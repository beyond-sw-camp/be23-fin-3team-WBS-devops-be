package com.beyond.wbs.inventory.service;

import com.beyond.wbs.common.SystemUser;
import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.*;
import com.beyond.wbs.inventory.domain.*;
import com.beyond.wbs.inventory.dtos.*;
import com.beyond.wbs.inventory.exception.LocationCapacityExceededException;
import com.beyond.wbs.inventory.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.beyond.wbs.outbounds.domain.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 재고 서비스 (Inventory Service)
 *
 * 재고 조회 + 재고 변동 처리 + 이력 기록을 담당한다.
 *
 * ─────────────────────────────────────────────
 *  [재고 변동 패턴]
 *  1. 비관적 락(PESSIMISTIC_WRITE)으로 Inventory row 조회
 *     → 동시성 이슈(중복 차감/예약) 방지
 *  2. 도메인 메서드 호출 (reserve / unreserve / addAvailable ...)
 *  3. InventoryTransaction을 "한 status당 1 row" 원칙으로 기록
 *     → 상태 간 이동(available → reserved)은 2 row 생성 (같은 refId 공유)
 *
 *  [Kafka 연동]
 *  - InventoryEventConsumer 가 출고/입고/재고실사 이벤트를 수신하여
 *    아래 메서드들을 호출한다. 직접 호출도 가능.
 * ─────────────────────────────────────────────
 */
@Slf4j
@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository outboundOrderItemRepository;
    private final com.beyond.wbs.alert.service.AlertService alertService;

    @Autowired
    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryTransactionRepository transactionRepository,
                            InventorySnapshotRepository snapshotRepository,
                            MasterServiceClient masterServiceClient,
                            AccountServiceClient accountServiceClient,
                            com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository outboundOrderItemRepository,
                            com.beyond.wbs.alert.service.AlertService alertService) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.masterServiceClient = masterServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.alertService = alertService;
    }

    // ============================================================
    // Feign 조회 헬퍼 (실패 시 null 반환 — 이름 없어도 서비스는 동작)
    // ============================================================

    private String fetchWarehouseName(UUID warehouseId, UUID clientId) {
        if (warehouseId == null || clientId == null) return null;
        try {
            WarehouseResDto w = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
            return w != null ? w.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] warehouse 조회 실패: {} - {}", warehouseId, e.getMessage());
            return null;
        }
    }

    private String fetchProductName(UUID productId, UUID clientId) {
        ProductResDto p = fetchProduct(productId, clientId);
        return p != null ? p.getName() : null;
    }

    /** 상품 전체 조회 — name + sku 둘 다 필요할 때 사용. 실패 시 null 반환. */
    private ProductResDto fetchProduct(UUID productId, UUID clientId) {
        if (productId == null) return null;
        try {
            return masterServiceClient.getProduct(productId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] product 조회 실패: {} - {}", productId, e.getMessage());
            return null;
        }
    }

    private LocationResDto fetchLocation(UUID locationId, UUID clientId) {
        if (locationId == null) return null;
        try {
            return masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] location 조회 실패: {} - {}", locationId, e.getMessage());
            return null;
        }
    }

    private String fetchUserName(UUID userId, UUID requesterId) {
        if (userId == null || requesterId == null) return null;
        if (SystemUser.ID.equals(userId)) return "시스템";
        try {
            UserResDto u = accountServiceClient.getUser(userId, requesterId.toString());
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] user 조회 실패: {} - {}", userId, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 조회 (Read)
    // ============================================================

    /**
     * 창고 내 랙(Rack) 단위로 그룹핑된 재고·로케이션 요약.
     *
     * 흐름:
     *  1) Master 에서 창고의 활성 Location 전체 조회 (zone/rack 정보 포함) — Feign 1회
     *  2) Stock 의 Inventory 행 전체 조회 (locationId null 인 staging 행 제외)
     *  3) productId 집합을 뽑아 Master 배치 상품 조회 — Feign 1회
     *  4) 랙 단위로 그룹핑 (zone.code → rack.code 정렬, 랙 내 floorNo ASC)
     *  5) 랙별 집계 (locationCount / occupiedCount / totalAvailableQty) 계산
     *
     * N+1 없음: Feign 은 "locations 벌크" + "products 배치" 두 번만 호출.
     */
    @Transactional(readOnly = true)
    public InventoryByRackResDto getInventoriesByRack(UUID warehouseId, UUID clientId) {
        // 1) 창고 활성 로케이션 전체 (master)
        WarehouseLocationsResDto master =
                masterServiceClient.getLocationsByWarehouseId(warehouseId, clientId);
        if (master == null || master.getItems() == null) {
            return InventoryByRackResDto.builder()
                    .warehouseId(warehouseId)
                    .warehouseName(master != null ? master.getWarehouseName() : null)
                    .racks(new ArrayList<>())
                    .build();
        }

        // 2) stock inventories — locationId 별 인덱싱 (null-location staging 행은 제외)
        List<Inventory> inventories = inventoryRepository.findByWarehouseId(warehouseId);
        Map<UUID, Inventory> invByLocation = new HashMap<>();
        for (Inventory inv : inventories) {
            if (inv.getLocationId() == null) continue;
            invByLocation.put(inv.getLocationId(), inv);
        }

        // 3) 상품 배치 조회 (재고가 있는 것만)
        Map<UUID, ProductResDto> productMap = new HashMap<>();
        List<UUID> productIds = new ArrayList<>();
        for (Inventory inv : invByLocation.values()) {
            if (inv.getProductId() != null && !productMap.containsKey(inv.getProductId())) {
                productIds.add(inv.getProductId());
                productMap.put(inv.getProductId(), null); // 중복 방지용 자리
            }
        }
        if (!productIds.isEmpty()) {
            try {
                List<ProductResDto> products = masterServiceClient.getProducts(productIds, clientId.toString());
                if (products != null) {
                    for (ProductResDto p : products) {
                        if (p != null && p.getId() != null) productMap.put(p.getId(), p);
                    }
                }
            } catch (Exception e) {
                log.warn("[Feign] product 배치 조회 실패: {}", e.getMessage());
            }
        }

        // 4) 랙 단위 그룹핑
        // Key: rackId, Value: RackGroupAcc (헤더 + locations 누적)
        Map<UUID, RackGroupAcc> rackAcc = new HashMap<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : master.getItems()) {
            RackGroupAcc acc = rackAcc.computeIfAbsent(item.getRackId(), k -> {
                RackGroupAcc a = new RackGroupAcc();
                a.zoneId = item.getZoneId();
                a.zoneCode = item.getZoneCode();
                a.zoneName = item.getZoneName();
                a.rackId = item.getRackId();
                a.rackCode = item.getRackCode();
                a.rackName = item.getRackName();
                return a;
            });

            Inventory inv = invByLocation.get(item.getLocationId());
            ProductResDto product = inv != null ? productMap.get(inv.getProductId()) : null;

            InventoryByRackResDto.LocationInventory loc =
                    InventoryByRackResDto.LocationInventory.builder()
                            .locationId(item.getLocationId())
                            .locationCode(item.getLocationCode())
                            .floorNo(item.getFloorNo())
                            .maxCapacity(item.getMaxCapacity())
                            .inventoryId(inv != null ? inv.getId() : null)
                            .productId(inv != null ? inv.getProductId() : null)
                            .productSku(product != null ? product.getSku() : null)
                            .productName(product != null ? product.getName() : null)
                            .availableQty(inv != null ? inv.getAvailableQty() : 0)
                            .reservedQty(inv != null ? inv.getReservedQty() : 0)
                            .pendingQty(inv != null ? inv.getPendingQty() : 0)
                            .defectQty(inv != null ? inv.getDefectQty() : 0)
                            .totalQty(inv != null ? inv.getTotalQty() : 0)
                            .updatedAt(inv != null ? inv.getUpdatedAt() : null)
                            .build();
            acc.locations.add(loc);
            if (inv != null && inv.getAvailableQty() != null) {
                acc.totalAvailableQty += inv.getAvailableQty();
                if (inv.getAvailableQty() > 0) acc.occupiedCount++;
            }
        }

        // 5) 랙별 정렬 + 집계 → DTO 변환
        List<RackGroupAcc> sortedRacks = new ArrayList<>(rackAcc.values());
        sortedRacks.sort((a, b) -> {
            int c = safeCompare(a.zoneCode, b.zoneCode);
            if (c != 0) return c;
            return safeCompare(a.rackCode, b.rackCode);
        });

        List<InventoryByRackResDto.RackGroup> rackGroups = new ArrayList<>();
        for (RackGroupAcc a : sortedRacks) {
            a.locations.sort((l1, l2) -> {
                Integer f1 = l1.getFloorNo();
                Integer f2 = l2.getFloorNo();
                if (f1 == null && f2 == null) return 0;
                if (f1 == null) return 1;
                if (f2 == null) return -1;
                return Integer.compare(f1, f2);
            });
            rackGroups.add(InventoryByRackResDto.RackGroup.builder()
                    .rackId(a.rackId)
                    .rackCode(a.rackCode)
                    .rackName(a.rackName)
                    .zoneId(a.zoneId)
                    .zoneCode(a.zoneCode)
                    .zoneName(a.zoneName)
                    .locationCount(a.locations.size())
                    .occupiedCount(a.occupiedCount)
                    .totalAvailableQty(a.totalAvailableQty)
                    .locations(a.locations)
                    .build());
        }

        return InventoryByRackResDto.builder()
                .warehouseId(warehouseId)
                .warehouseName(master.getWarehouseName())
                .racks(rackGroups)
                .build();
    }

    private static int safeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    /**
     * getInventoriesByRack 내부 집계용 가변 누적자.
     */
    private static class RackGroupAcc {
        UUID zoneId;
        String zoneCode;
        String zoneName;
        UUID rackId;
        String rackCode;
        String rackName;
        final List<InventoryByRackResDto.LocationInventory> locations = new ArrayList<>();
        int occupiedCount = 0;
        int totalAvailableQty = 0;
    }

    /**
     * 창고 단위 재고 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryResDto> getInventoriesByWarehouse(UUID warehouseId, UUID clientId) {
        List<Inventory> list = inventoryRepository.findByWarehouseId(warehouseId);
        return toResDtos(list, clientId);
    }

    /**
     * 회사 전체 재고 조회 (모든 창고 통합).
     * 재고 현황 페이지에서 창고 필터를 "전체" 로 둘 때 사용.
     */
    @Transactional(readOnly = true)
    public List<InventoryResDto> getInventoriesByClient(UUID clientId) {
        List<Inventory> list = inventoryRepository.findByClientId(clientId);
        return toResDtos(list, clientId);
    }

    /**
     * 조회일자 기준 재고 현황 (날짜 시점 역산).
     *
     * 동작:
     *  1. 현재 Inventory 의 status별 수량을 시작점으로 둔다.
     *  2. (date 다음날 00:00:00 ~ now) 사이의 모든 트랜잭션을
     *     (productId, warehouseId, locationId, statusTo) 별로 합산해 변동합을 구한다.
     *  3. 현재값 - 변동합 = 그 날 시점의 status별 수량.
     *
     * 예) 오늘이 5/6 이고 date=4/15 이면
     *     fromTs = 4/16 00:00:00, toTs = now → 4/15 자정까지의 변동을 모두 빼서 4/15 24:00 시점 값.
     *
     * 미래 날짜를 입력하면 현재값 그대로.
     */
    @Transactional(readOnly = true)
    public List<InventoryResDto> getInventoriesByDate(UUID clientId, UUID warehouseId, LocalDate date) {
        // Step 1. 베이스 Inventory 조회 (clientId 필터 + staging 행 제외)
        List<Inventory> source = warehouseId != null
                ? inventoryRepository.findByWarehouseId(warehouseId)
                : inventoryRepository.findByClientId(clientId);
        List<Inventory> baseInventories = new ArrayList<>();
        for (Inventory inv : source) {
            if (!clientId.equals(inv.getClientId())) continue;
            if (inv.getLocationId() == null) continue; // null-location staging 행 제외
            baseInventories.add(inv);
        }

        // Step 2. (date 다음날 00:00:00 ~ now) 트랜잭션 → status별 변동합 집계
        LocalDateTime fromTs = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Integer> deltaMap = new HashMap<>();
        if (fromTs.isBefore(now)) {
            List<InventoryTransaction> txList = transactionRepository
                    .findAllStatusTransactionsInRange(clientId, warehouseId, fromTs, now);
            for (InventoryTransaction tx : txList) {
                if (tx.getStatusTo() == null) continue;
                String key = makeStatusKey(tx.getProductId(), tx.getWarehouseId(),
                        tx.getLocationId(), tx.getStatusTo());
                deltaMap.merge(key, tx.getQty(), Integer::sum);
            }
        }

        // Step 3. 현재값 - 변동합 = 과거값. Feign 캐시는 toResDtos 와 동일하게 운용.
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();

        List<InventoryResDto> result = new ArrayList<>();
        for (Inventory inv : baseInventories) {
            int pastAvailable = (inv.getAvailableQty() != null ? inv.getAvailableQty() : 0)
                    - getDelta(deltaMap, inv, InventoryStatus.available);
            int pastReserved  = (inv.getReservedQty()  != null ? inv.getReservedQty()  : 0)
                    - getDelta(deltaMap, inv, InventoryStatus.reserved);
            int pastDefect    = (inv.getDefectQty()    != null ? inv.getDefectQty()    : 0)
                    - getDelta(deltaMap, inv, InventoryStatus.defect);
            int pastPending   = (inv.getPendingQty()   != null ? inv.getPendingQty()   : 0)
                    - getDelta(deltaMap, inv, InventoryStatus.pending);
            int pastIncoming  = (inv.getIncomingQty()  != null ? inv.getIncomingQty()  : 0)
                    - getDelta(deltaMap, inv, InventoryStatus.incoming);

            // 음수 방어 (트랜잭션 누락/이상치) — 0 으로 클램프
            pastAvailable = Math.max(0, pastAvailable);
            pastReserved  = Math.max(0, pastReserved);
            pastDefect    = Math.max(0, pastDefect);
            pastPending   = Math.max(0, pastPending);
            pastIncoming  = Math.max(0, pastIncoming);
            int pastTotal = pastAvailable + pastReserved + pastDefect + pastPending;

            // 그 시점 재고가 전부 0 이면 결과에서 제외
            if (pastTotal == 0 && pastIncoming == 0) continue;

            ProductResDto product = productCache.computeIfAbsent(inv.getProductId(),
                    pid -> fetchProduct(pid, clientId));
            String productName = product != null ? product.getName() : null;
            String productSku  = product != null ? product.getSku()  : null;
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    inv.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(
                    inv.getLocationId(), lid -> fetchLocation(lid, clientId));

            // 영속 객체를 변형하지 않기 위해 임시 Inventory 인스턴스 생성
            Inventory snap = Inventory.builder()
                    .id(inv.getId())
                    .clientId(inv.getClientId())
                    .productId(inv.getProductId())
                    .warehouseId(inv.getWarehouseId())
                    .locationId(inv.getLocationId())
                    .availableQty(pastAvailable)
                    .reservedQty(pastReserved)
                    .defectQty(pastDefect)
                    .incomingQty(pastIncoming)
                    .pendingQty(pastPending)
                    .totalQty(pastTotal)
                    .updatedAt(inv.getUpdatedAt())
                    .build();
            result.add(InventoryResDto.fromEntity(snap, productName, productSku, warehouseName, location));
        }
        return result;
    }

    private int getDelta(Map<String, Integer> deltaMap, Inventory inv, InventoryStatus status) {
        return deltaMap.getOrDefault(
                makeStatusKey(inv.getProductId(), inv.getWarehouseId(), inv.getLocationId(), status),
                0);
    }

    private String makeStatusKey(UUID productId, UUID warehouseId, UUID locationId, InventoryStatus status) {
        return productId + "|" + warehouseId + "|" + locationId + "|" + status;
    }

    /**
     * 주어진 location 집합 중 재고가 한 row 라도 남아있는지 조회.
     * Master 의 랙 비활성화 가드에서 호출.
     */
    @Transactional(readOnly = true)
    public boolean hasStockInLocations(UUID clientId, List<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return false;
        return inventoryRepository.existsStockInLocations(clientId, locationIds);
    }

    /**
     * Location 별 totalQty 합계 조회.
     * Master 의 location maxCapacity 변경 시 "현재 보관 중인 양 > 새 maxCapacity" 검증에 사용.
     * 재고 row 가 없는 location 은 0 으로 채워서 반환 — 호출자가 모든 입력 id 를 안전하게 lookup.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> getTotalQtyByLocations(UUID clientId, List<UUID> locationIds) {
        Map<UUID, Integer> result = new HashMap<>();
        if (locationIds == null || locationIds.isEmpty()) return result;
        for (UUID id : locationIds) result.put(id, 0);
        for (Object[] row : inventoryRepository.sumTotalQtyByLocations(clientId, locationIds)) {
            UUID locationId = (UUID) row[0];
            Number sum = (Number) row[1];
            result.put(locationId, sum != null ? sum.intValue() : 0);
        }
        return result;
    }

    /** Inventory 엔티티 리스트 → DTO 변환. 상품/창고/로케이션 Feign 호출은 캐싱해 N+1 방지. */
    private List<InventoryResDto> toResDtos(List<Inventory> list, UUID clientId) {
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();

        List<InventoryResDto> result = new ArrayList<>();
        for (Inventory i : list) {
            ProductResDto product = productCache.computeIfAbsent(i.getProductId(), pid -> fetchProduct(pid, clientId));
            String productName = product != null ? product.getName() : null;
            String productSku  = product != null ? product.getSku() : null;
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    i.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(i.getLocationId(), lid -> fetchLocation(lid, clientId));
            result.add(InventoryResDto.fromEntity(i, productName, productSku, warehouseName, location));
        }
        return result;
    }

    /**
     * 상품 단위 재고 조회 (여러 창고/위치에 분산된 재고 전체)
     */
    @Transactional(readOnly = true)
    public List<InventoryResDto> getInventoriesByProduct(UUID productId, UUID clientId) {
        List<Inventory> list = inventoryRepository.findByProductId(productId);

        // 상품은 고정이므로 한 번만 조회
        ProductResDto product = fetchProduct(productId, clientId);
        String productName = product != null ? product.getName() : null;
        String productSku  = product != null ? product.getSku() : null;
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();

        List<InventoryResDto> result = new ArrayList<>();
        for (Inventory i : list) {
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    i.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(i.getLocationId(), lid -> fetchLocation(lid, clientId));
            result.add(InventoryResDto.fromEntity(i, productName, productSku, warehouseName, location));
        }
        return result;
    }

    /**
     * 재고 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryResDto getInventory(UUID inventoryId, UUID clientId) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        ProductResDto product = fetchProduct(inventory.getProductId(), clientId);
        String productName = product != null ? product.getName() : null;
        String productSku  = product != null ? product.getSku() : null;
        String warehouseName = fetchWarehouseName(inventory.getWarehouseId(), clientId);
        LocationResDto location = fetchLocation(inventory.getLocationId(), clientId);

        return InventoryResDto.fromEntity(inventory, productName, productSku, warehouseName, location);
    }

    /**
     * 상품 재고 위치 조회 — 레이아웃 뷰어의 하이라이트 기능용.
     *
     * by-rack API 의 master 로케이션 데이터를 재활용하여
     * 해당 상품이 보관된 zone/rack/location 을 한 번에 반환.
     */
    @Transactional(readOnly = true)
    public List<ProductLocationResDto> getProductLocations(UUID productId, UUID warehouseId, UUID clientId) {
        // 1) 해당 상품+창고의 재고 row (null-location 제외)
        List<Inventory> inventories = inventoryRepository.findByProductId(productId);

        // 2) master 에서 창고의 전체 로케이션 정보 (zone/rack 포함)
        WarehouseLocationsResDto master =
                masterServiceClient.getLocationsByWarehouseId(warehouseId, clientId);
        if (master == null || master.getItems() == null) {
            return new ArrayList<>();
        }

        // locationId → master item 매핑
        Map<UUID, WarehouseLocationsResDto.LocationSummaryItem> locationMap = new HashMap<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : master.getItems()) {
            locationMap.put(item.getLocationId(), item);
        }

        // 3) 재고 + 위치 정보 조합
        List<ProductLocationResDto> result = new ArrayList<>();
        for (Inventory inv : inventories) {
            if (!warehouseId.equals(inv.getWarehouseId())) continue;
            if (inv.getLocationId() == null) continue;
            if (inv.getTotalQty() == null || inv.getTotalQty() <= 0) continue;

            WarehouseLocationsResDto.LocationSummaryItem loc = locationMap.get(inv.getLocationId());
            if (loc == null) continue;

            result.add(ProductLocationResDto.builder()
                    .warehouseId(warehouseId)
                    .warehouseName(master.getWarehouseName())
                    .zoneId(loc.getZoneId())
                    .zoneCode(loc.getZoneCode())
                    .zoneName(loc.getZoneName())
                    .rackId(loc.getRackId())
                    .rackCode(loc.getRackCode())
                    .rackName(loc.getRackName())
                    .locationId(loc.getLocationId())
                    .locationCode(loc.getLocationCode())
                    .floorNo(loc.getFloorNo())
                    .availableQty(inv.getAvailableQty())
                    .reservedQty(inv.getReservedQty())
                    .pendingQty(inv.getPendingQty())
                    .defectQty(inv.getDefectQty())
                    .totalQty(inv.getTotalQty())
                    .build());
        }

        return result;
    }

    /**
     * 특정 재고의 이력 조회 (오래된 순)
     */
    @Transactional(readOnly = true)
    public List<InventoryTransactionResDto> getTransactionsByInventory(UUID inventoryId, UUID requesterId, UUID clientId) {
        List<InventoryTransaction> list = transactionRepository.findByInventoryIdOrderByCreatedAtAsc(inventoryId);

        Map<UUID, String> productNameCache = new HashMap<>();
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        List<InventoryTransactionResDto> result = new ArrayList<>();
        for (InventoryTransaction tx : list) {
            String productName = productNameCache.computeIfAbsent(tx.getProductId(), pid -> fetchProductName(pid, clientId));
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    tx.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(tx.getLocationId(), lid -> fetchLocation(lid, clientId));
            String createdByName = userNameCache.computeIfAbsent(
                    tx.getCreatedBy(), uid -> fetchUserName(uid, requesterId));
            result.add(InventoryTransactionResDto.fromEntity(tx, productName, warehouseName, location, createdByName));
        }
        return result;
    }

    /**
     * 상품 단위 이력 조회 (최신순)
     */
    @Transactional(readOnly = true)
    public List<InventoryTransactionResDto> getTransactionsByProduct(UUID productId, UUID requesterId, UUID clientId) {
        List<InventoryTransaction> list = transactionRepository.findByProductIdOrderByCreatedAtDesc(productId);

        // 상품은 고정
        String productName = fetchProductName(productId, clientId);
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        List<InventoryTransactionResDto> result = new ArrayList<>();
        for (InventoryTransaction tx : list) {
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    tx.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(tx.getLocationId(), lid -> fetchLocation(lid, clientId));
            String createdByName = userNameCache.computeIfAbsent(
                    tx.getCreatedBy(), uid -> fetchUserName(uid, requesterId));
            result.add(InventoryTransactionResDto.fromEntity(tx, productName, warehouseName, location, createdByName));
        }
        return result;
    }

    /**
     * 지시서/원천 이벤트 단위 이력 조회 (오래된 순).
     */
    @Transactional(readOnly = true)
    public List<InventoryTransactionResDto> getTransactionsByRef(UUID refId, RefType refType, UUID requesterId, UUID clientId) {
        List<InventoryTransaction> list = transactionRepository
                .findByClientIdAndRefIdAndRefTypeOrderByCreatedAtAsc(clientId, refId, refType);

        Map<UUID, String> productNameCache = new HashMap<>();
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        List<InventoryTransactionResDto> result = new ArrayList<>();
        for (InventoryTransaction tx : list) {
            String productName = productNameCache.computeIfAbsent(
                    tx.getProductId(), pid -> fetchProductName(pid, clientId));
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    tx.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            LocationResDto location = locationCache.computeIfAbsent(
                    tx.getLocationId(), lid -> fetchLocation(lid, clientId));
            String createdByName = userNameCache.computeIfAbsent(
                    tx.getCreatedBy(), uid -> fetchUserName(uid, requesterId));
            result.add(InventoryTransactionResDto.fromEntity(tx, productName, warehouseName, location, createdByName));
        }
        return result;
    }

    // ============================================================
    // 재고 변동 - 출고 플로우
    // ============================================================

    /**
     * 출고지시서 승인 시 재고 예약
     *
     * 가용재고 → 예약재고로 이동
     * (available -qty, reserved +qty)
     *
     * 2 row 기록:
     *  Row 1) available 차감
     *  Row 2) reserved 증가
     *
     * Kafka: InventoryEventConsumer 의 outbound.approved 리스너에서 호출됨
     */
    public void reserve(UUID clientId, UUID productId,
                        UUID warehouseId, UUID locationId,
                        int qty, UUID refId, UUID userId) {
        // ──────────────────────────────────────────
        // 출고 승인 시점에는 "어디서 꺼낼지"(locationId)가 아직 정해지지 않아서
        // Kafka 이벤트의 locationId 가 null 로 들어올 수 있다.
        //
        // locationId 가 있으면: 해당 위치의 재고에서 직접 예약 (단일 row)
        // locationId 가 null 이면: 해당 상품+창고의 가용재고가 있는 위치들을 찾아서
        //                         필요한 수량이 채워질 때까지 순회하며 분산 예약
        //                         (피킹 위치 분배와 같은 패턴)
        // ──────────────────────────────────────────

        if (locationId != null) {
            // 특정 위치 지정 → 기존 로직 그대로
            reserveSingleLocation(clientId, productId, warehouseId, locationId, qty, refId, userId);
        } else {
            // 위치 미정 → 가용재고 있는 위치들에서 분산 예약
            reserveDistributed(clientId, productId, warehouseId, qty, refId, userId);
        }
    }

    /**
     * 특정 로케이션에서 예약 (locationId 가 확정된 경우)
     */
    private void reserveSingleLocation(UUID clientId, UUID productId,
                                        UUID warehouseId, UUID locationId,
                                        int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository.findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int availableBefore = inventory.getAvailableQty();
        int reservedBefore = inventory.getReservedQty();

        inventory.reserve(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.reserve,
                -qty, availableBefore, availableBefore - qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.outbound_order, userId,
                "출고지시서 승인으로 예약");

        recordTransaction(clientId, inventory, TxType.reserve,
                +qty, reservedBefore, reservedBefore + qty,
                InventoryStatus.reserved, InventoryStatus.reserved,
                refId, RefType.outbound_order, userId,
                "출고지시서 승인으로 예약");

        // 가용재고 차감 후 임계 미달이면 WebSocket 으로 알림 push
        alertService.notifyLowStockIfNeeded(productId, warehouseId, clientId);
    }

    /**
     * 위치 미정 시 분산 예약 (ATP 기반)
     *
     * ──────────────────────────────────────────
     * ATP (Available To Promise) 검증:
     *   ATP = available + incoming + pending
     *   = "지금 꺼낼 수 있는 양 + 올 예정인 양 + 검수 중인 양"
     *
     *   ATP >= 필요 수량이면 출고 승인 허용.
     *   실제 reserve 는 available 에서 가능한 만큼만 부분 예약.
     *   나머지는 입고 완료(적치) 후 available 로 전환되면 그때 잡힘.
     *
     * 예시:
     *   available=10, incoming=30, pending=20 → ATP=60
     *   출고 25개 승인 → available 10개만 실제 reserve
     *   나머지 15개는 "아직 못 잡았지만 승인은 됨"
     *   → 입고 완료 후 available 이 올라가면 피킹 시점에 자연스럽게 잡힘
     *
     * 만약 입고가 취소/지연되면?
     *   → 관리자가 출고지시서를 취소하면 됨 (cancelOutboundOrder 이미 구현됨)
     * ──────────────────────────────────────────
     */
    private void reserveDistributed(UUID clientId, UUID productId,
                                     UUID warehouseId,
                                     int qty, UUID refId, UUID userId) {
        // 1) 해당 상품+창고의 모든 재고 row 조회 (null-location 포함)
        //    ATP 계산을 위해 incoming/pending 도 필요하므로 전체 조회
        List<Inventory> allInventories = inventoryRepository.findByProductId(productId);

        // 2) ATP 계산: available + incoming + pending (같은 창고만)
        int totalAvailable = 0;
        int totalIncoming = 0;
        int totalPending = 0;
        for (Inventory inv : allInventories) {
            if (!warehouseId.equals(inv.getWarehouseId())) continue;
            totalAvailable += inv.getAvailableQty();
            totalIncoming += inv.getIncomingQty();
            totalPending += inv.getPendingQty();
        }
        int atp = totalAvailable + totalIncoming + totalPending;

        // 3) ATP 선검증: ATP < 필요 수량이면 승인 불가
        if (atp < qty) {
            throw new IllegalArgumentException(
                    "출고 가능 수량(ATP) 부족: productId=" + productId
                            + ", 필요=" + qty
                            + ", ATP=" + atp
                            + " (가용=" + totalAvailable
                            + ", 입고예정=" + totalIncoming
                            + ", 검수중=" + totalPending + ")");
        }

        // 4) 실제 reserve 는 available 에서 가능한 만큼만 부분 예약
        //    available 이 부족하면 가능한 만큼만 잡고 나머지는 보류
        //    (입고 완료 후 available 로 전환되면 피킹 시점에 자연스럽게 처리됨)
        List<Inventory> availableList = inventoryRepository.findAvailableForPick(productId, warehouseId);

        int remaining = qty;
        for (Inventory inv : availableList) {
            if (remaining <= 0) break;

            // 비관적 락으로 다시 조회 (동시성 보호)
            Inventory locked = inventoryRepository
                    .findForUpdate(productId, warehouseId, inv.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

            // 이 위치에서 예약할 수량 = 필요한 양과 가용 중 작은 쪽
            int take = Math.min(remaining, locked.getAvailableQty());
            if (take <= 0) continue;

            int availableBefore = locked.getAvailableQty();
            int reservedBefore = locked.getReservedQty();

            locked.reserve(take);
            inventoryRepository.save(locked);

            recordTransaction(clientId, locked, TxType.reserve,
                    -take, availableBefore, availableBefore - take,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.outbound_order, userId,
                    "출고지시서 승인으로 예약 (분산)");

            recordTransaction(clientId, locked, TxType.reserve,
                    +take, reservedBefore, reservedBefore + take,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.outbound_order, userId,
                    "출고지시서 승인으로 예약 (분산)");

            remaining -= take;
        }

        // 실제 예약 성공한 수량
        int reserved = qty - remaining;

        // 출고지시서 품목의 reservedQty 업데이트
        // refId = 출고지시서 ID → 해당 지시서의 품목 중 이 상품에 해당하는 것을 찾아서 반영
        if (refId != null && reserved > 0) {
            List<OutboundOrderItems> orderItems =
                    outboundOrderItemRepository.findByOutboundOrdersId(refId);
            for (OutboundOrderItems oi : orderItems) {
                if (oi.getProductId().equals(productId)) {
                    oi.setReservedQty(oi.getReservedQty() + reserved);
                    outboundOrderItemRepository.save(oi);
                }
            }
        }

        // remaining = 아직 못 잡은 수량
        // remaining > 0 이면 available 부족으로 일부만 예약된 상태
        // → 입고 완료(적치) 시 autoReserveUnfilledOrders 가 자동으로 잡아줌
        // → 입고가 안 오면 관리자가 출고지시서 취소하면 됨
        if (remaining > 0) {
            log.info("[reserve] 부분 예약: productId={}, 총필요={}개, 예약성공={}개, 아직못잡음={}개",
                    productId, qty, reserved, remaining);
        }

        // 가용재고 차감 후 임계 미달이면 WebSocket 으로 알림 push
        alertService.notifyLowStockIfNeeded(productId, warehouseId, clientId);
    }

    /**
     * 출고지시서 취소 시 재고 예약 해제
     *
     * 예약재고 → 가용재고로 원복
     * (reserved -qty, available +qty)
     *
     * locationId 가 null 이면 해당 상품+창고의 reserved 가 있는 모든 위치에서 분산 해제.
     * (reserve 가 분산으로 여러 위치에 걸쳤을 수 있으므로)
     *
     * Kafka: InventoryEventConsumer 의 outbound.cancelled 리스너에서 호출됨
     */
    public void unreserve(UUID clientId, UUID productId,
                          UUID warehouseId, UUID locationId,
                          int qty, UUID refId, UUID userId) {
        if (locationId != null) {
            // 특정 위치 지정 → 단일 row 해제
            unreserveSingle(clientId, productId, warehouseId, locationId, qty, refId, userId);
        } else {
            // 위치 미정 → reserved 가 있는 위치들에서 분산 해제
            unreserveDistributed(clientId, productId, warehouseId, qty, refId, userId);
        }
    }

    private void unreserveSingle(UUID clientId, UUID productId,
                                  UUID warehouseId, UUID locationId,
                                  int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository.findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int reservedBefore = inventory.getReservedQty();
        int availableBefore = inventory.getAvailableQty();

        inventory.unreserve(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.unreserve,
                -qty, reservedBefore, reservedBefore - qty,
                InventoryStatus.reserved, InventoryStatus.reserved,
                refId, RefType.outbound_order, userId,
                "출고지시서 취소로 예약 해제");

        recordTransaction(clientId, inventory, TxType.unreserve,
                +qty, availableBefore, availableBefore + qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.outbound_order, userId,
                "출고지시서 취소로 예약 해제");
    }

    /**
     * 분산 unreserve: reserved 가 있는 위치들을 순회하며 해제
     */
    private void unreserveDistributed(UUID clientId, UUID productId,
                                       UUID warehouseId,
                                       int qty, UUID refId, UUID userId) {
        // reserved 가 있는 위치들 조회
        List<Inventory> reservedList = inventoryRepository.findLocationsWithStock(productId, warehouseId);

        int remaining = qty;
        for (Inventory inv : reservedList) {
            if (remaining <= 0) break;
            if (inv.getReservedQty() <= 0) continue;

            Inventory locked = inventoryRepository
                    .findForUpdate(productId, warehouseId, inv.getLocationId())
                    .orElse(null);
            if (locked == null || locked.getReservedQty() <= 0) continue;

            int take = Math.min(remaining, locked.getReservedQty());

            int reservedBefore = locked.getReservedQty();
            int availableBefore = locked.getAvailableQty();

            locked.unreserve(take);
            inventoryRepository.save(locked);

            recordTransaction(clientId, locked, TxType.unreserve,
                    -take, reservedBefore, reservedBefore - take,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.outbound_order, userId,
                    "출고지시서 취소로 예약 해제 (분산)");

            recordTransaction(clientId, locked, TxType.unreserve,
                    +take, availableBefore, availableBefore + take,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.outbound_order, userId,
                    "출고지시서 취소로 예약 해제 (분산)");

            remaining -= take;
        }
    }

    /**
     * 출고 확정 (출고전표 생성) 시 재고 차감
     *
     * 예약재고에서 최종 차감 (실물이 창고에서 빠짐)
     * (reserved -qty)
     *
     * locationId 가 null 이면 reserved 가 있는 위치들에서 분산 차감.
     *
     * Kafka: InventoryEventConsumer 의 outbound.completed 리스너에서 호출됨
     */
    public void releaseOnDispatch(UUID clientId, UUID productId,
                                  UUID warehouseId, UUID locationId,
                                  int qty, UUID refId, UUID userId) {
        if (locationId != null) {
            // 특정 위치 지정 → 단일 row 차감
            releaseSingle(clientId, productId, warehouseId, locationId, qty, refId, userId);
        } else {
            // 위치 미정 → reserved 가 있는 위치들에서 분산 차감
            releaseDistributed(clientId, productId, warehouseId, qty, refId, userId);
        }
    }

    private void releaseSingle(UUID clientId, UUID productId,
                                UUID warehouseId, UUID locationId,
                                int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository.findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int reservedBefore = inventory.getReservedQty();

        inventory.releaseReserved(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.outbound,
                -qty, reservedBefore, reservedBefore - qty,
                InventoryStatus.reserved, InventoryStatus.reserved,
                refId, RefType.outbound_order, userId,
                "출고 확정 (실물 출고)");
    }

    /**
     * 분산 release: reserved 가 있는 위치들을 순회하며 최종 차감
     */
    private void releaseDistributed(UUID clientId, UUID productId,
                                     UUID warehouseId,
                                     int qty, UUID refId, UUID userId) {
        List<Inventory> reservedList = inventoryRepository.findLocationsWithStock(productId, warehouseId);

        int remaining = qty;
        for (Inventory inv : reservedList) {
            if (remaining <= 0) break;
            if (inv.getReservedQty() <= 0) continue;

            Inventory locked = inventoryRepository
                    .findForUpdate(productId, warehouseId, inv.getLocationId())
                    .orElse(null);
            if (locked == null || locked.getReservedQty() <= 0) continue;

            int take = Math.min(remaining, locked.getReservedQty());

            int reservedBefore = locked.getReservedQty();

            locked.releaseReserved(take);
            inventoryRepository.save(locked);

            recordTransaction(clientId, locked, TxType.outbound,
                    -take, reservedBefore, reservedBefore - take,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.outbound_order, userId,
                    "출고 확정 (실물 출고, 분산)");

            remaining -= take;
        }
    }

    // ============================================================
    // 재고 변동 - 입고 플로우
    // ============================================================

    /**
     * 입고지시서 승인 시 입고예정 추가
     * incomingQty 증가
     *
     * Kafka: InventoryEventConsumer 의 inbound.approved 리스너에서 호출됨
     */
    public void addIncoming(UUID clientId, UUID productId,
                            UUID warehouseId, UUID locationId,
                            int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        int incomingBefore = inventory.getIncomingQty();

        inventory.addIncoming(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.inbound,
                +qty, incomingBefore, incomingBefore + qty,
                InventoryStatus.incoming, InventoryStatus.incoming,
                refId, RefType.inbound_order, userId,
                "입고지시서 승인 (입고예정)");
    }

    /**
     * 입고지시서 취소 시 입고예정 원복
     * incomingQty 차감
     *
     * Kafka: InventoryEventConsumer 의 inbound.cancelled 리스너에서 호출됨
     */
    public void removeIncoming(UUID clientId, UUID productId,
                               UUID warehouseId, UUID locationId,
                               int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "입고예정 재고가 없습니다: productId=" + productId));

        int incomingBefore = inventory.getIncomingQty();
        inventory.removeIncoming(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.inbound,
                -qty, incomingBefore, incomingBefore - qty,
                InventoryStatus.incoming, InventoryStatus.incoming,
                refId, RefType.inbound_order, userId,
                "입고지시서 취소 (입고예정 원복)");
    }

    /**
     * 입고 검수 시작 (입고예정 → 검수중)
     * incomingQty → pendingQty
     *
     * 2 row 기록
     *
     * 처음 입고되는 상품/위치 조합이면 inventory row가 없을 수 있어
     * createEmptyInventory()로 빈 row를 먼저 만든 뒤 pendingQty를 올린다.
     *
     * Kafka: InventoryEventConsumer 의 inbound.inspected 리스너에서 호출됨
     */
    public void addPending(UUID clientId, UUID productId,
                           UUID warehouseId, UUID locationId,
                           int qty, UUID refId, UUID userId) {
        // inspected 시점엔 적치 위치가 미정(locationId=null)이라 같은 null-row 안에서
        // incoming → pending 으로만 전환한다. 실제 로케이션으로의 분산은 placed 시점에서.
        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        int incomingBefore = inventory.getIncomingQty();
        int pendingBefore = inventory.getPendingQty();

        inventory.removeIncoming(qty);
        inventory.addPending(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.inbound,
                -qty, incomingBefore, incomingBefore - qty,
                InventoryStatus.incoming, InventoryStatus.incoming,
                refId, RefType.inbound_order, userId,
                "검수 시작 (입고예정 해제)");

        recordTransaction(clientId, inventory, TxType.inbound,
                +qty, pendingBefore, pendingBefore + qty,
                InventoryStatus.pending, InventoryStatus.pending,
                refId, RefType.inbound_order, userId,
                "검수 시작 (검수중 전환)");
    }

    /**
     * 입고 적치 완료 (검수중 → 가용)
     * pendingQty → availableQty
     * 2 row 기록
     * Kafka: InventoryEventConsumer 의 inbound.placed 리스너에서 호출됨
     */
    public void confirmPlacement(UUID clientId, UUID productId,
                                 UUID warehouseId, UUID locationId,
                                 int qty, UUID refId, UUID userId) {
        // pending 은 inspected 시점에 null-location 행에 쌓여 있음.
        // placed 시점엔 실제 적치 위치가 정해지므로 두 개 행을 각각 업데이트한다:
        //   1) pending 은 null-location 행에서 차감
        //   2) available 은 실제 location 행(없으면 생성)에 증가
        Inventory pendingInventory = inventoryRepository
                .findForUpdate(productId, warehouseId, null)
                .orElseThrow(() -> new IllegalArgumentException("검수중 재고가 없습니다 (null-location row)."));

        int pendingBefore = pendingInventory.getPendingQty();
        pendingInventory.removePending(qty);
        inventoryRepository.save(pendingInventory);

        recordTransaction(clientId, pendingInventory, TxType.inbound,
                -qty, pendingBefore, pendingBefore - qty,
                InventoryStatus.pending, InventoryStatus.pending,
                refId, RefType.inbound_order, userId,
                "적치 완료 (검수중 해제)");

        // one-SKU-per-location 안전망 — Kafka 비동기 처리에서 race condition 방어.
        // completePlacementItem 의 동기 검증을 우회한 케이스(직접 이벤트 발행, 재시도 등) 차단.
        // 거부 시 InventoryEventConsumer 의 try-catch 가 잡아 inventory 변경은 일어나지 않음.
        List<Inventory> conflictRows = inventoryRepository
                .findByWarehouseIdAndLocationId(warehouseId, locationId)
                .stream()
                .filter(inv -> inv.getProductId() != null && !inv.getProductId().equals(productId))
                .filter(inv -> inv.getTotalQty() != null && inv.getTotalQty() > 0)
                .toList();
        if (!conflictRows.isEmpty()) {
            Inventory other = conflictRows.get(0);
            log.error("[유령재고 차단] confirmPlacement 거부 — locationId={}, 신규 productId={}, 점유 productId={}, qty={}",
                    locationId, productId, other.getProductId(), other.getTotalQty());
            throw new IllegalStateException(
                    "이 위치에 이미 다른 상품(productId=" + other.getProductId()
                            + ")이 보관 중이어서 적치 완료를 반영할 수 없습니다.");
        }

        // 실제 적치 위치 row — 없으면 생성 후 available 증가
        Inventory availableInventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        // 로케이션 수용량 검증: 현재 보관량 + 적치량 > 최대 수용량이면 거부
        validateLocationCapacity(locationId, warehouseId, qty, clientId, false);

        int availableBefore = availableInventory.getAvailableQty();
        availableInventory.addAvailable(qty);
        inventoryRepository.save(availableInventory);

        recordTransaction(clientId, availableInventory, TxType.inbound,
                +qty, availableBefore, availableBefore + qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.inbound_order, userId,
                "적치 완료 (가용재고 확정)");

        // ============================================================
        // 자동 예약: 미예약 출고지시서가 있으면 방금 올라간 available 로 자동 reserve
        // ============================================================
        // 적치 완료로 available 이 올라갔으니, 이 상품에 대해
        // "승인은 됐지만 available 부족으로 예약 못 한" 출고품목이 있는지 확인.
        // 있으면 출고예정일 빠른 순서대로 자동으로 reserve 해줌.
        //
        // 예) 콜라 30개 적치 완료 → available +30
        //     미예약 출고품목: SO-001 콜라 20개(미예약), SO-002 콜라 15개(미예약)
        //     → SO-001에 20개 자동 reserve (available 30→10)
        //     → SO-002에 10개 자동 reserve (available 10→0, 5개는 여전히 미예약)
        autoReserveUnfilledOrders(clientId, productId, warehouseId, locationId);
    }

    /**
     * 적치 완료 후 미예약 출고품목 자동 reserve
     *
     * 흐름:
     *  1) 이 상품+창고에 대해 "reservedQty < orderedQty" 인 출고품목 조회 (출고예정일 빠른 순)
     *  2) 방금 적치된 위치의 available 에서 잡을 수 있는 만큼 순서대로 reserve
     *  3) 각 품목의 reservedQty 업데이트
     */
    private void autoReserveUnfilledOrders(UUID clientId, UUID productId,
                                            UUID warehouseId, UUID locationId) {
        // 미예약 출고품목 조회 (출고예정일 빠른 순)
        List<OutboundOrderItems> unreserved =
                outboundOrderItemRepository.findUnreservedByProductAndWarehouse(productId, warehouseId);

        if (unreserved.isEmpty()) return;

        // 방금 적치된 위치의 현재 available 확인 (위에서 이미 save 했으므로 최신)
        Inventory inv = inventoryRepository.findForUpdate(productId, warehouseId, locationId)
                .orElse(null);
        if (inv == null || inv.getAvailableQty() <= 0) return;

        int remainingAvailable = inv.getAvailableQty();

        for (OutboundOrderItems orderItem : unreserved) {
            if (remainingAvailable <= 0) break;

            // 이 품목에서 아직 못 잡은 양
            int shortage = orderItem.getOrderedQty() - orderItem.getReservedQty();
            if (shortage <= 0) continue;

            // 잡을 수 있는 만큼만
            int take = Math.min(shortage, remainingAvailable);

            // 재고 reserve 처리 (available → reserved)
            int availableBefore = inv.getAvailableQty();
            int reservedBefore = inv.getReservedQty();

            inv.reserve(take);
            inventoryRepository.save(inv);

            recordTransaction(clientId, inv, TxType.reserve,
                    -take, availableBefore, availableBefore - take,
                    InventoryStatus.available, InventoryStatus.available,
                    orderItem.getOutboundOrdersId(), RefType.outbound_order, SystemUser.ID,
                    "적치 후 자동 예약");

            recordTransaction(clientId, inv, TxType.reserve,
                    +take, reservedBefore, reservedBefore + take,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    orderItem.getOutboundOrdersId(), RefType.outbound_order, SystemUser.ID,
                    "적치 후 자동 예약");

            // 출고품목의 reservedQty 업데이트
            orderItem.setReservedQty(orderItem.getReservedQty() + take);
            outboundOrderItemRepository.save(orderItem);

            remainingAvailable -= take;

            log.info("[자동예약] 적치 후 미예약분 자동 reserve: outboundOrderItemId={}, 잡은수량={}개, 남은미예약={}개",
                    orderItem.getId(), take, orderItem.getOrderedQty() - orderItem.getReservedQty());
        }
    }

    /**
     * 로케이션 수용량 검증.
     *
     * 적치 / 이동 / 조정 / 기타입출고 등 재고 증가 시 호출.
     *
     * 합산 규칙:
     *  - 일반 zone (STORAGE 등): Σ(available + reserved + pending)  ← defect 제외
     *  - 불량존 (DEFECT)      : Σ defect                              ← defect 만
     *
     * 예외 케이스:
     *  - locationId == null: 검증 생략 (staging null-location 허용)
     *  - location.maxCapacity null/0: 무제한으로 간주
     *  - Master Feign 실패: 검증 생략 (장애 시 적치 막히지 않게)
     *
     * 초과 시 {@link LocationCapacityExceededException} 던짐 (HTTP 422).
     *
     * @param locationId      목적지 로케이션
     * @param warehouseId     소속 창고 (inventory 행 조회용)
     * @param addQty          이번 작업으로 들어가는 양
     * @param clientId        Master Feign 호출용
     * @param targetIsDefect  true 면 defect 합산 기준 검증 (불량존 적재 시)
     */
    /**
     * 적치 capacity 사전 검증 (동기) — completePlacementItem 에서 Kafka publish 전에 호출.
     *
     * 흐름상 문제: completePlacementItem(@Transactional) 이 끝나면서 placement_item.is_placed=true 가
     * 먼저 커밋되고, 그 다음에 Kafka 가 발행돼 비동기 confirmPlacement 가 실행됨.
     * 비동기 단계에서 capacity 초과로 throw 하면 inventory 는 롤백되지만 이미 커밋된 is_placed=true 는
     * 되돌릴 수 없어 phantom (적치 완료 표시인데 실재고 없음) 발생.
     *
     * 동기 단계에서 미리 capacity 검사 → 초과 시 4xx (HTTP 422) 즉시 반환하여 phantom 자체를 방지.
     */
    public void checkPlacementCapacity(UUID locationId, UUID warehouseId,
                                        int addQty, UUID clientId) {
        validateLocationCapacity(locationId, warehouseId, addQty, clientId, false);
    }

    private void validateLocationCapacity(UUID locationId, UUID warehouseId,
                                          int addQty, UUID clientId,
                                          boolean targetIsDefect) {
        if (locationId == null) return;

        LocationResDto location;
        try {
            location = masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[수용량 검증] Master 조회 실패, 검증 생략: locationId={}, err={}", locationId, e.getMessage());
            return;
        }
        if (location == null || location.getMaxCapacity() == null || location.getMaxCapacity() <= 0) return;

        int maxCapacity = location.getMaxCapacity();

        // 해당 location 의 inventory 행 조회.
        // [one-SKU-per-location] 정책상 보통 0~1 행. 방어적으로 List 합산.
        List<Inventory> rows = inventoryRepository.findByWarehouseIdAndLocationId(warehouseId, locationId);
        int existingQty = 0;
        for (Inventory inv : rows) {
            if (targetIsDefect) {
                existingQty += nullSafe(inv.getDefectQty());
            } else {
                existingQty += nullSafe(inv.getAvailableQty())
                             + nullSafe(inv.getReservedQty())
                             + nullSafe(inv.getPendingQty());
            }
        }

        if (existingQty + addQty > maxCapacity) {
            throw LocationCapacityExceededException.of(existingQty, maxCapacity, addQty);
        }
    }

    private static int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 검수 불량 처리 (검수중 → 불량)
     * pendingQty → defectQty
     *
     * 2 row 기록
     *
     * Kafka: InventoryEventConsumer 의 inbound.defect 리스너에서 호출됨
     */
    public void markDefect(UUID clientId, UUID productId,
                           UUID warehouseId, UUID locationId,
                           int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository.findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int pendingBefore = inventory.getPendingQty();
        int defectBefore = inventory.getDefectQty();

        inventory.markDefect(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.dispose,
                -qty, pendingBefore, pendingBefore - qty,
                InventoryStatus.pending, InventoryStatus.pending,
                refId, RefType.inbound_order, userId,
                "검수 불량 판정");

        recordTransaction(clientId, inventory, TxType.dispose,
                +qty, defectBefore, defectBefore + qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.inbound_order, userId,
                "검수 불량 판정");
    }

    /**
     * 불량 재고 위치 이동 (defect@null → defect@실위치)
     * 불량품 적치 완료 시 호출. 정상품의 confirmPlacement 와 짝을 이루지만
     * pending→available 이 아니라 defect@null → defect@실위치 로 이동한다.
     */
    public void relocateDefect(UUID clientId, UUID productId,
                               UUID warehouseId, UUID locationId,
                               int qty, UUID refId, UUID userId) {
        // 1) defect@null 에서 차감 (receiveInbound 가 null-location 에 쌓아놓음)
        Inventory defectAtNull = inventoryRepository
                .findForUpdate(productId, warehouseId, null)
                .orElseThrow(() -> new IllegalArgumentException("불량 재고가 없습니다."));

        int defectBefore = defectAtNull.getDefectQty();
        if (defectBefore < qty) {
            throw new IllegalArgumentException(
                    "불량 재고 부족: 요청=" + qty + ", 보유=" + defectBefore);
        }
        defectAtNull.setDefectQty(defectAtNull.getDefectQty() - qty);
        inventoryRepository.save(defectAtNull);

        recordTransaction(clientId, defectAtNull, TxType.dispose,
                -qty, defectBefore, defectBefore - qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.inbound_order, userId,
                "불량품 적치 (격리존 이동 — 출발)");

        // 2) defect@실위치 에 증가
        Inventory defectAtLocation = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        int defectAtLocBefore = defectAtLocation.getDefectQty();
        defectAtLocation.setDefectQty(defectAtLocation.getDefectQty() + qty);
        inventoryRepository.save(defectAtLocation);

        recordTransaction(clientId, defectAtLocation, TxType.dispose,
                +qty, defectAtLocBefore, defectAtLocBefore + qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.inbound_order, userId,
                "불량품 적치 (격리존 이동 — 도착)");
    }

    // ============================================================
    // 재고 조정 (재고실사 / 수동 보정)
    // ============================================================

    /**
     * 재고 수동 조정
     * - 재고실사 차이 반영
     * - 분실/파손 등 수동 보정
     *
     * diffQty 양수: 증가, 음수: 감소
     *
     * (재고실사 완료 이벤트와의 연동은 별도 설계 예정)
     */

    /**
     * 이동 지시서 — 가용재고 위치 이동
     *
     * 출발 위치의 available 차감 → 도착 위치의 available 증가.
     */
    public void transferAvailable(UUID clientId, UUID productId,
                                  UUID fromWarehouseId, UUID fromLocationId,
                                  UUID toWarehouseId, UUID toLocationId,
                                  int qty, UUID refId, UUID userId) {
        Inventory fromInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, fromLocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "출발 위치에 재고가 없습니다: productId=" + productId + ", locationId=" + fromLocationId));

        int fromBefore = fromInv.getAvailableQty();
        if (fromBefore < qty) {
            throw new IllegalArgumentException(
                    "출발 위치 가용재고 부족: 요청=" + qty + ", 보유=" + fromBefore);
        }

        fromInv.setAvailableQty(fromBefore - qty);
        inventoryRepository.save(fromInv);

        recordTransaction(clientId, fromInv, TxType.adjust,
                -qty, fromBefore, fromBefore - qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 출발지 차감");

        // 도착지 수용량 검증 (일반 zone — defect 제외)
        validateLocationCapacity(toLocationId, toWarehouseId, qty, clientId, false);

        Inventory toInv = inventoryRepository.findForUpdate(productId, toWarehouseId, toLocationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, toWarehouseId, toLocationId));

        int toBefore = toInv.getAvailableQty();
        toInv.setAvailableQty(toBefore + qty);
        inventoryRepository.save(toInv);

        recordTransaction(clientId, toInv, TxType.adjust,
                +qty, toBefore, toBefore + qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 도착지 증가");
    }

    /**
     * 이동 지시서 모바일 — 픽업
     *
     * 창고내 이동: 출발 위치 available 차감 → in-transit (location=null) 행에 보관.
     * 창고간 이동: 출발 위치 available → reserved (출고 reserve 패턴 재사용).
     *               이동 중에도 출발 창고에 위치한 채 "예약된 재고"로 표시.
     */
    public void transferPickFromSource(UUID clientId, UUID productId,
                                       UUID fromWarehouseId, UUID fromLocationId,
                                       UUID toWarehouseId,
                                       int qty, UUID refId, UUID userId) {
        Inventory fromInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, fromLocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "출발 위치에 재고가 없습니다: productId=" + productId + ", locationId=" + fromLocationId));

        if (fromWarehouseId.equals(toWarehouseId)) {
            // 창고내 이동 — NULL 위치 방식
            int fromBefore = fromInv.getAvailableQty();
            if (fromBefore < qty) {
                throw new IllegalArgumentException(
                        "출발 위치 가용재고 부족: 요청=" + qty + ", 보유=" + fromBefore);
            }

            fromInv.setAvailableQty(fromBefore - qty);
            inventoryRepository.save(fromInv);
            recordTransaction(clientId, fromInv, TxType.adjust,
                    -qty, fromBefore, fromBefore - qty,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 — 픽업 (출발지 차감)");

            Inventory transitInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, null)
                    .orElseGet(() -> createEmptyInventory(clientId, productId, fromWarehouseId, null));
            int transitBefore = transitInv.getAvailableQty();
            transitInv.setAvailableQty(transitBefore + qty);
            inventoryRepository.save(transitInv);
            recordTransaction(clientId, transitInv, TxType.adjust,
                    +qty, transitBefore, transitBefore + qty,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 — 픽업 (이동 중 보관)");
        } else {
            // 창고간 이동 — reserved 방식 (출고 패턴 재사용)
            int availableBefore = fromInv.getAvailableQty();
            int reservedBefore = fromInv.getReservedQty();
            if (availableBefore < qty) {
                throw new IllegalArgumentException(
                        "출발 위치 가용재고 부족: 요청=" + qty + ", 보유=" + availableBefore);
            }

            fromInv.reserve(qty);
            inventoryRepository.save(fromInv);

            recordTransaction(clientId, fromInv, TxType.reserve,
                    -qty, availableBefore, availableBefore - qty,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 PICK — 창고간 예약 (가용 차감)");

            recordTransaction(clientId, fromInv, TxType.reserve,
                    +qty, reservedBefore, reservedBefore + qty,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 PICK — 창고간 예약 (예약 증가)");
        }
    }

    /**
     * 이동 지시서 모바일 — 적치 정상
     *
     * 창고내 이동: in-transit (location=null) 행에서 차감 → 도착지 available 증가.
     * 창고간 이동: 출발 위치 reserved 차감 (출고 확정 패턴) → 도착지 available 증가.
     */
    public void transferPlaceAvailable(UUID clientId, UUID productId,
                                       UUID fromWarehouseId, UUID fromLocationId,
                                       UUID toWarehouseId, UUID toLocationId,
                                       int qty, UUID refId, UUID userId) {
        if (fromWarehouseId.equals(toWarehouseId)) {
            // 창고내 이동 — NULL 위치에서 차감
            Inventory transitInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, null)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "이동 중 재고가 없습니다 (PICK 이력 누락): productId=" + productId));

            int transitBefore = transitInv.getAvailableQty();
            if (transitBefore < qty) {
                throw new IllegalArgumentException(
                        "이동 중 재고 부족: 요청=" + qty + ", 보유=" + transitBefore);
            }

            transitInv.setAvailableQty(transitBefore - qty);
            inventoryRepository.save(transitInv);
            recordTransaction(clientId, transitInv, TxType.adjust,
                    -qty, transitBefore, transitBefore - qty,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 — 적치 (이동 중 차감)");
        } else {
            // 창고간 이동 — 출발 위치 reserved 차감
            Inventory fromInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, fromLocationId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "출발 위치에 재고가 없습니다: productId=" + productId + ", locationId=" + fromLocationId));

            int reservedBefore = fromInv.getReservedQty();
            if (reservedBefore < qty) {
                throw new IllegalArgumentException(
                        "예약 재고 부족 (PICK 이력 누락 가능): 요청=" + qty + ", 보유=" + reservedBefore);
            }

            fromInv.releaseReserved(qty);
            inventoryRepository.save(fromInv);
            recordTransaction(clientId, fromInv, TxType.transfer,
                    -qty, reservedBefore, reservedBefore - qty,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 PLACE — 창고간 출발지 예약 차감");
        }

        // 도착지 가용 증가 (창고내·창고간 공통)
        Inventory toInv = inventoryRepository.findForUpdate(productId, toWarehouseId, toLocationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, toWarehouseId, toLocationId));
        int toBefore = toInv.getAvailableQty();
        toInv.addAvailable(qty);
        inventoryRepository.save(toInv);
        recordTransaction(clientId, toInv,
                fromWarehouseId.equals(toWarehouseId) ? TxType.adjust : TxType.transfer,
                +qty, toBefore, toBefore + qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 적치 (도착지 증가)");
    }

    /**
     * 이동 지시서 모바일 — 적치 시 불량 등록
     *
     * 창고내 이동: in-transit (location=null) 행에서 차감 → 도착지 defect 증가.
     * 창고간 이동: 출발 위치 reserved 차감 → 도착지 defect 증가.
     */
    public void transferPlaceDefect(UUID clientId, UUID productId,
                                    UUID fromWarehouseId, UUID fromLocationId,
                                    UUID toWarehouseId, UUID toLocationId,
                                    int qty, UUID refId, UUID userId) {
        if (fromWarehouseId.equals(toWarehouseId)) {
            // 창고내 이동 — NULL 위치에서 차감
            Inventory transitInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, null)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "이동 중 재고가 없습니다 (PICK 이력 누락): productId=" + productId));

            int transitBefore = transitInv.getAvailableQty();
            if (transitBefore < qty) {
                throw new IllegalArgumentException(
                        "이동 중 재고 부족: 요청=" + qty + ", 보유=" + transitBefore);
            }

            transitInv.setAvailableQty(transitBefore - qty);
            inventoryRepository.save(transitInv);
            recordTransaction(clientId, transitInv, TxType.adjust,
                    -qty, transitBefore, transitBefore - qty,
                    InventoryStatus.available, InventoryStatus.available,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 — 적치 (이동 중 차감, 파손분)");
        } else {
            // 창고간 이동 — 출발 위치 reserved 차감
            Inventory fromInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, fromLocationId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "출발 위치에 재고가 없습니다: productId=" + productId + ", locationId=" + fromLocationId));

            int reservedBefore = fromInv.getReservedQty();
            if (reservedBefore < qty) {
                throw new IllegalArgumentException(
                        "예약 재고 부족 (PICK 이력 누락 가능): 요청=" + qty + ", 보유=" + reservedBefore);
            }

            fromInv.releaseReserved(qty);
            inventoryRepository.save(fromInv);
            recordTransaction(clientId, fromInv, TxType.transfer,
                    -qty, reservedBefore, reservedBefore - qty,
                    InventoryStatus.reserved, InventoryStatus.reserved,
                    refId, RefType.transfer_order, userId,
                    "이동 지시서 PLACE — 창고간 출발지 예약 차감 (파손분)");
        }

        // 도착지 불량 증가 (창고내·창고간 공통)
        Inventory toInv = inventoryRepository.findForUpdate(productId, toWarehouseId, toLocationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, toWarehouseId, toLocationId));
        int defectBefore = toInv.getDefectQty();
        toInv.setDefectQty(defectBefore + qty);
        inventoryRepository.save(toInv);
        recordTransaction(clientId, toInv, TxType.dispose,
                +qty, defectBefore, defectBefore + qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 이동 중 파손 (도착지 불량 등록)");
    }

    /**
     * 이동 지시서 — 이동 중 파손(불량) 처리
     *
     * 출발 위치의 available 차감 → 도착 위치의 defect 증가.
     */
    public void transferToDefect(UUID clientId, UUID productId,
                                 UUID fromWarehouseId, UUID fromLocationId,
                                 UUID toWarehouseId, UUID toLocationId,
                                 int qty, UUID refId, UUID userId) {
        Inventory fromInv = inventoryRepository.findForUpdate(productId, fromWarehouseId, fromLocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "출발 위치에 재고가 없습니다: productId=" + productId + ", locationId=" + fromLocationId));

        int fromBefore = fromInv.getAvailableQty();
        if (fromBefore < qty) {
            throw new IllegalArgumentException(
                    "출발 위치 가용재고 부족: 요청=" + qty + ", 보유=" + fromBefore);
        }

        fromInv.setAvailableQty(fromBefore - qty);
        inventoryRepository.save(fromInv);

        recordTransaction(clientId, fromInv, TxType.dispose,
                -qty, fromBefore, fromBefore - qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 이동 중 파손 (출발지 차감)");

        // 도착지(불량존) 수용량 검증 — defect 합산 기준
        validateLocationCapacity(toLocationId, toWarehouseId, qty, clientId, true);

        Inventory toInv = inventoryRepository.findForUpdate(productId, toWarehouseId, toLocationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, toWarehouseId, toLocationId));

        int defectBefore = toInv.getDefectQty();
        toInv.setDefectQty(defectBefore + qty);
        inventoryRepository.save(toInv);

        recordTransaction(clientId, toInv, TxType.dispose,
                +qty, defectBefore, defectBefore + qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.transfer_order, userId,
                "이동 지시서 — 이동 중 파손 (도착지 불량 등록)");
    }

    public void adjust(UUID clientId, StockAdjustReqDto dto, UUID userId) {
        Inventory inventory = inventoryRepository
                .findForUpdate(dto.getProductId(), dto.getWarehouseId(), dto.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        // 재고 조정(adjust)은 "현실 동기화" — 재고실사 결과 반영이 주 용도.
        // 실측이 capacity 를 초과해도 그게 사실이면 그대로 반영해야 하므로 capacity 검증 우회.

        int availableBefore = inventory.getAvailableQty();

        inventory.adjust(dto.getDiffQty());
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.adjust,
                dto.getDiffQty(), availableBefore, availableBefore + dto.getDiffQty(),
                InventoryStatus.available, InventoryStatus.available,
                null, RefType.manual, userId,
                dto.getNote() != null ? dto.getNote() : "수동 조정");
    }

    // ============================================================
    // 내부 헬퍼
    // ============================================================

    /**
     * 재고 row가 없을 때 빈 row 생성
     * - 처음 입고되는 상품/위치 조합일 때 사용
     * - 모든 수량 0으로 초기화 후 저장
     */
    private Inventory createEmptyInventory(UUID clientId, UUID productId, UUID warehouseId, UUID locationId) {
        Inventory inventory = Inventory.builder()
                .clientId(clientId)
                .productId(productId)
                .warehouseId(warehouseId)
                .locationId(locationId)
                .availableQty(0)
                .reservedQty(0)
                .defectQty(0)
                .pendingQty(0)
                .totalQty(0)
                .updatedAt(LocalDateTime.now())
                .build();
        return inventoryRepository.save(inventory);
    }

    /**
     * 재고 이력 기록 (공통)
     *
     * @param clientId     고객사 ID
     * @param inventory    변경 대상 재고 row
     * @param txType       변동 유형
     * @param qty          변동량 (양수/음수)
     * @param qtyBefore    변동 전 해당 status 잔액
     * @param qtyAfter     변동 후 해당 status 잔액
     * @param statusFrom   변동 status (from == to)
     * @param statusTo     변동 status
     * @param refId        원천 이벤트 ID (출고지시서 ID 등)
     * @param refType      원천 이벤트 유형
     * @param userId       생성자 (Kafka 리스너에서 받은 값)
     * @param note         비고
     */
    private void recordTransaction(UUID clientId, Inventory inventory, TxType txType,
                                   int qty, int qtyBefore, int qtyAfter,
                                   InventoryStatus statusFrom, InventoryStatus statusTo,
                                   UUID refId, RefType refType, UUID userId, String note) {
        InventoryTransaction tx = InventoryTransaction.builder()
                .clientId(clientId)
                .productId(inventory.getProductId())
                .inventoryId(inventory.getId())
                .warehouseId(inventory.getWarehouseId())
                .locationId(inventory.getLocationId())
                .txType(txType)
                .qty(qty)
                .qtyBefore(qtyBefore)
                .qtyAfter(qtyAfter)
                .statusFrom(statusFrom)
                .statusTo(statusTo)
                .refId(refId)
                .refType(refType)
                .note(note)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);
    }

    /**
     * 기타입고 완료 시 가용재고 증가
     * (반품입고, 샘플입고, 조정입고, 기타입고)
     */
    public void addAvailableByEtcInout(UUID clientId, UUID productId,
                                       UUID warehouseId, UUID locationId,
                                       int qty, UUID refId, UUID userId) {
        // 도착지 수용량 검증 (일반 zone — defect 제외)
        validateLocationCapacity(locationId, warehouseId, qty, clientId, false);

        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        int availableBefore = inventory.getAvailableQty();

        inventory.addAvailable(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.returned,
                +qty, availableBefore, availableBefore + qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.etc_inout_order, userId,
                "기타입고 완료 (가용재고 증가)");
    }

    /**
     * 기타입고 시 불량재고 증가 (현장 검수에서 불량 판정된 수량)
     * 가용재고 staging(pending)을 거치지 않고 바로 defectQty 로 적재.
     */
    public void addDefectByEtcInout(UUID clientId, UUID productId,
                                    UUID warehouseId, UUID locationId,
                                    int qty, UUID refId, UUID userId) {
        if (qty <= 0) return;

        // DEFECT zone 적재 — 일반 zone 수용량 검증은 isDefect=true 로 우회
        validateLocationCapacity(locationId, warehouseId, qty, clientId, true);

        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseGet(() -> createEmptyInventory(clientId, productId, warehouseId, locationId));

        int defectBefore = inventory.getDefectQty();

        inventory.addDefect(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.defect_in,
                +qty, defectBefore, defectBefore + qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.etc_inout_order, userId,
                "기타입고 완료 (불량재고 증가)");
    }

    /**
     * 기타출고 완료 시 가용재고 감소
     * (샘플출고, 조정출고, 기타출고)
     */
    public void reduceAvailableByEtcInout(UUID clientId, UUID productId,
                                          UUID warehouseId, UUID locationId,
                                          int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int availableBefore = inventory.getAvailableQty();

        // 가용재고 부족 체크
        if (availableBefore < qty) {
            throw new IllegalArgumentException(
                    "가용재고가 부족합니다. 요청: " + qty + ", 보유: " + availableBefore);
        }

        inventory.adjust(-qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.adjust,
                -qty, availableBefore, availableBefore - qty,
                InventoryStatus.available, InventoryStatus.available,
                refId, RefType.etc_inout_order, userId,
                "기타출고 완료 (가용재고 감소)");
    }

    /**
     * 폐기 처리 시 불량재고 감소
     * (불량재고에서 물리적으로 제거)
     */
    public void disposeByEtcInout(UUID clientId, UUID productId,
                                  UUID warehouseId, UUID locationId,
                                  int qty, UUID refId, UUID userId) {
        Inventory inventory = inventoryRepository
                .findForUpdate(productId, warehouseId, locationId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));

        int defectBefore = inventory.getDefectQty();

        // 도메인 메서드로 defectQty 차감 (내부에서 부족 시 예외 발생)
        inventory.disposeDefect(qty);
        inventoryRepository.save(inventory);

        recordTransaction(clientId, inventory, TxType.dispose,
                -qty, defectBefore, defectBefore - qty,
                InventoryStatus.defect, InventoryStatus.defect,
                refId, RefType.etc_inout_order, userId,
                "폐기 처리 완료");
    }
}

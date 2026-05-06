package com.beyond.wbs.inventory.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.*;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.dtos.SuggestLocationResDto;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 적치 위치 추천 서비스
 *
 * 입고 검수 시 "이 상품을 어디에 적치할지" 시스템이 추천해준다.
 *
 * ─────────────────────────────────────────────
 *  [추천 로직 — 위에서 아래로 좁혀감]
 *
 *  1단계: 상품 정보 조회 (Feign → master)
 *    - supplierId (협력사)
 *    - categoryId (상품 대분류)
 *
 *  2단계: 카테고리 → 구역(Zone) 찾기
 *    - 전자기기 카테고리 → 전자기기존
 *
 *  3단계: 협력사 → 랙(Rack) 찾기
 *    - LogiX → ELEC-LogiX-01, ELEC-LogiX-02
 *
 *  4단계: 2번과 3번의 교집합
 *    - "전자기기존 안에 있는 LogiX 랙"만 필터
 *
 *  5단계: 해당 랙의 로케이션(Location) 조회
 *    - 각 로케이션의 현재 재고 확인
 *    - ① 같은 상품 있는 위치 (우선)
 *    - ② 빈 위치 (차선)
 * ─────────────────────────────────────────────
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class PlacementSuggestionService {

    private final MasterServiceClient masterServiceClient;
    private final InventoryRepository inventoryRepository;

    @Autowired
    public PlacementSuggestionService(MasterServiceClient masterServiceClient,
                                      InventoryRepository inventoryRepository) {
        this.masterServiceClient = masterServiceClient;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * 적치 위치 추천 (기본: 정상 입고)
     */
    public List<SuggestLocationResDto> suggest(UUID productId, UUID warehouseId, Integer qty, UUID clientId) {
        return suggest(productId, warehouseId, qty, clientId, PlacementPurpose.NORMAL);
    }

    /**
     * 적치 위치 추천
     *
     * @param productId   적치할 상품 ID
     * @param warehouseId 적치할 창고 ID
     * @param qty         적치 예정 수량 (nullable)
     * @param purpose     적치 목적 (정상/불량/반품/폐기)
     * @return 추천 위치 목록 (우선순위 높은 순)
     */
    public List<SuggestLocationResDto> suggest(UUID productId, UUID warehouseId, Integer qty, UUID clientId,
                                                PlacementPurpose purpose) {

        String clientIdStr = clientId.toString();

        // ──────────────────────────────────────
        // [DEFECT/RETURN/DISPOSAL] 경로: 같은 창고의 DEFECT/STORAGE zone 내 빈 위치 우선
        // - DEFECT: 정상 창고에 불량품 임시 보관 → DEFECT zone 고정
        // - RETURN/DISPOSAL: 창고 자체가 해당 목적이라 STORAGE zone 그대로 사용
        // ──────────────────────────────────────
        if (purpose != PlacementPurpose.NORMAL) {
            String zoneType = purpose == PlacementPurpose.DEFECT ? "DEFECT" : "STORAGE";
            List<ZoneResDto> targetZones = masterServiceClient
                    .getZonesByWarehouseIdAndType(warehouseId, zoneType, clientIdStr);
            if (targetZones.isEmpty()) {
                log.warn("[적치 추천-{}] 대상 zone 없음: warehouseId={}", purpose, warehouseId);
                return new ArrayList<>();
            }
            List<RackResDto> targetRacks = new ArrayList<>();
            Set<UUID> added = new HashSet<>();
            for (ZoneResDto zone : targetZones) {
                for (RackResDto rack : masterServiceClient.getRacksByZoneId(zone.getId(), clientIdStr)) {
                    if (added.add(rack.getId())) targetRacks.add(rack);
                }
            }
            return buildSuggestions(targetRacks, productId, warehouseId, qty, clientIdStr, null);
        }

        // ──────────────────────────────────────
        // 1단계: 상품 정보 조회 → 협력사, 카테고리 확인
        // ──────────────────────────────────────
        ProductResDto product = masterServiceClient.getProduct(productId, clientIdStr);
        UUID supplierId = product.getSupplierId();
        UUID categoryId = product.getCategoryId();
        Integer productHeightMm = toPositiveInt(product.getHeight());

        log.info("[적치 추천] 상품={}, 협력사={}, 카테고리={}, 높이={}mm",
                product.getName(), supplierId, categoryId, productHeightMm);

        // ──────────────────────────────────────
        // 2단계: 카테고리 → 해당 카테고리의 구역(Zone) 찾기
        // ──────────────────────────────────────
        List<ZoneResDto> categoryZones = new ArrayList<>();
        if (categoryId != null) {
            List<ZoneResDto> allCategoryZones = masterServiceClient.getZonesByCategoryId(categoryId, clientIdStr);
            // 입고 창고 소속 구역만 필터링
            for (ZoneResDto zone : allCategoryZones) {
                if (warehouseId.equals(zone.getWarehouseId())) {
                    categoryZones.add(zone);
                }
            }
            log.info("[적치 추천] 카테고리 구역 {}개 중 해당 창고 소속 {}개",
                    allCategoryZones.size(), categoryZones.size());
        }

        // ──────────────────────────────────────
        // 3~4단계: Zone별 랙 조회 → 협력사가 일치하는 랙만 필터 (진짜 교집합)
        //
        // 흐름:
        //  a) 카테고리의 각 Zone에 속한 랙들을 모두 가져오기
        //  b) 그 중 상품의 협력사(supplierId)와 일치하는 랙만 남기기
        //
        // 예) USB-C 케이블 (전자기기 카테고리, LogiX 협력사)
        //  → 전자기기존의 랙 [ELEC-LogiX-01, ELEC-LogiX-02, ELEC-TechSupply-01]
        //  → supplierId 필터 후 [ELEC-LogiX-01, ELEC-LogiX-02]만 남음
        // ──────────────────────────────────────
        List<RackResDto> targetRacks = new ArrayList<>();
        Set<UUID> addedRackIds = new HashSet<>();  // 중복 방지

        if (supplierId != null && !categoryZones.isEmpty()) {
            // [정상 경로] 카테고리 Zone ∩ 협력사 교집합
            for (ZoneResDto zone : categoryZones) {
                List<RackResDto> zoneRacks = masterServiceClient.getRacksByZoneId(zone.getId(), clientIdStr);
                for (RackResDto rack : zoneRacks) {
                    if (supplierId.equals(rack.getSupplierId())
                            && addedRackIds.add(rack.getId())) {
                        targetRacks.add(rack);
                    }
                }
            }
            log.info("[적치 추천] 교집합 결과: {}개 랙 (카테고리 Zone ∩ 협력사)", targetRacks.size());
        } else if (supplierId != null) {
            // [fallback 1] 카테고리 없음 → 협력사 랙 중 해당 창고 소속만
            List<RackResDto> allSupplierRacks = masterServiceClient.getRacksBySupplierId(supplierId, clientIdStr);
            // 해당 창고의 Zone ID 셋 구성
            WarehouseLocationsResDto whLocations = masterServiceClient.getLocationsByWarehouseId(warehouseId, clientId);
            Set<UUID> warehouseZoneIds = new HashSet<>();
            if (whLocations != null && whLocations.getItems() != null) {
                for (WarehouseLocationsResDto.LocationSummaryItem item : whLocations.getItems()) {
                    warehouseZoneIds.add(item.getZoneId());
                }
            }
            for (RackResDto rack : allSupplierRacks) {
                if (warehouseZoneIds.contains(rack.getZoneId())) {
                    targetRacks.add(rack);
                }
            }
            log.info("[적치 추천] 카테고리 없음, 협력사 랙 {}개 중 해당 창고 소속 {}개",
                    allSupplierRacks.size(), targetRacks.size());
        } else if (!categoryZones.isEmpty()) {
            // [fallback 2] 협력사 없음 → 카테고리 Zone의 랙 전체
            for (ZoneResDto zone : categoryZones) {
                List<RackResDto> zoneRacks = masterServiceClient.getRacksByZoneId(zone.getId(), clientIdStr);
                for (RackResDto rack : zoneRacks) {
                    if (addedRackIds.add(rack.getId())) {
                        targetRacks.add(rack);
                    }
                }
            }
            log.info("[적치 추천] 협력사 없음, 카테고리 Zone 랙 전체 사용: {}개", targetRacks.size());
        }
        // supplierId도 없고 categoryZones도 없으면 targetRacks는 빈 리스트
        // → 5단계에서 빈 위치 추천 자체가 안 됨 → 작업자가 수동 입력 필요

        if (targetRacks.isEmpty()) {
            log.warn("[적치 추천] 추천 가능한 랙이 없습니다. 상품={} (supplierId/categoryId 확인 필요)",
                    product.getName());
            return new ArrayList<>();
        }

        return buildSuggestions(targetRacks, productId, warehouseId, qty, clientIdStr, productHeightMm);
    }

    /**
     * 공통 5단계 — 주어진 랙 리스트 내에서 위치를 찾아 추천 목록을 구성한다.
     *
     * NORMAL / DEFECT / RETURN / DISPOSAL 모든 purpose 에서 공용.
     *
     * [정책]
     *  - 한 Location 에는 한 SKU 만 보관 가능 → 다른 상품 보유 위치는 후보에서 제외 (one-SKU-per-location)
     *  - max_capacity 가 null/0 → "무제한" 처리 (응답에 maxCapacity/availableCapacity = null)
     *  - currentUsed = available + reserved + pending  (defect/incoming 제외)
     *  - availableCapacity = max - used (음수는 0)
     *
     * [정렬 — qty 인지(aware), 약한 필터(B안)]
     *  ① 동일 상품 + 수용 충분            (1순위)
     *  ② 동일 상품이지만 수용 부족        (2순위, 분할 적치 가능성 안내)
     *  ③ 빈 위치 + 수용 충분              (3순위)
     *  ④ 빈 위치지만 수용 부족            (4순위)
     *  같은 그룹 내: availableCapacity DESC → locationCode ASC
     *  무제한(null) 은 항상 "충분" 그룹 + 정렬 시 최상위로 취급.
     */
    private List<SuggestLocationResDto> buildSuggestions(
            List<RackResDto> targetRacks, UUID productId, UUID warehouseId,
            Integer qty, String clientIdStr, Integer productHeightMm) {

        // 4-그룹으로 분리해서 담은 뒤 ①→②→③→④ 순으로 합쳐 반환한다.
        List<SuggestLocationResDto> sameProductFit = new ArrayList<>();
        List<SuggestLocationResDto> sameProductInsufficient = new ArrayList<>();
        List<SuggestLocationResDto> emptyFit = new ArrayList<>();
        List<SuggestLocationResDto> emptyInsufficient = new ArrayList<>();

        for (RackResDto rack : targetRacks) {
            // 비활성 랙은 신규 적치 후보에서 제외
            if (Boolean.FALSE.equals(rack.getIsActive())) {
                continue;
            }
            Integer floorHeightMm = computeFloorHeightMm(rack);

            // 랙의 모든 로케이션 조회 (Feign → master)
            LocationPageResDto locationPage = masterServiceClient.getLocationsByRackId(rack.getId(), clientIdStr);
            if (locationPage == null || locationPage.getContent() == null) continue;

            for (LocationResDto location : locationPage.getContent()) {
                // 비활성 위치는 제외
                if (Boolean.FALSE.equals(location.getIsActive())) {
                    continue;
                }
                if (productHeightMm != null && floorHeightMm != null && productHeightMm > floorHeightMm) {
                    continue;
                }

                Integer rawMaxCap = location.getMaxCapacity();
                boolean unlimited = (rawMaxCap == null || rawMaxCap <= 0);
                Integer maxCapacity = unlimited ? null : rawMaxCap;

                // 해당 위치의 현재 재고 확인 (stock DB)
                Optional<Inventory> existingInventory = inventoryRepository
                        .findByProductIdAndWarehouseIdAndLocationId(
                                productId, warehouseId, location.getId());

                if (existingInventory.isPresent()) {
                    // ① 같은 상품이 이미 있는 위치 → 1·2 그룹 후보
                    Inventory inv = existingInventory.get();
                    int currentQty = inv.getTotalQty();
                    int currentUsed = inv.getAvailableQty() + inv.getReservedQty() + inv.getPendingQty();
                    Integer availableCapacity = unlimited ? null : Math.max(0, rawMaxCap - currentUsed);

                    SuggestLocationResDto dto = SuggestLocationResDto.builder()
                            .locationId(location.getId())
                            .rackId(rack.getId())
                            .rackCode(rack.getCode())
                            .locationCode(location.getCode())
                            .floorNo(location.getFloorNo())
                            .reason(buildReason("동일 상품 보관중", maxCapacity, availableCapacity))
                            .currentQty(currentQty)
                            .maxCapacity(maxCapacity)
                            .currentUsed(currentUsed)
                            .availableCapacity(availableCapacity)
                            .remainCapacity(availableCapacity)
                            .build();

                    if (fits(availableCapacity, qty)) sameProductFit.add(dto);
                    else sameProductInsufficient.add(dto);
                } else {
                    // 다른 상품이 있는지 확인
                    List<Inventory> otherInventory = inventoryRepository
                            .findByWarehouseIdAndLocationId(warehouseId, location.getId());
                    // 다른 상품이 이미 있는 위치는 one-SKU-per-location 정책상 추천 안 함
                    if (!otherInventory.isEmpty()) continue;

                    // ② 완전히 빈 위치 → 3·4 그룹 후보 (잔여 = 최대)
                    Integer availableCapacity = maxCapacity;

                    SuggestLocationResDto dto = SuggestLocationResDto.builder()
                            .locationId(location.getId())
                            .rackId(rack.getId())
                            .rackCode(rack.getCode())
                            .locationCode(location.getCode())
                            .floorNo(location.getFloorNo())
                            .reason(buildReason("빈 위치", maxCapacity, availableCapacity))
                            .currentQty(0)
                            .maxCapacity(maxCapacity)
                            .currentUsed(0)
                            .availableCapacity(availableCapacity)
                            .remainCapacity(availableCapacity)
                            .build();

                    if (fits(availableCapacity, qty)) emptyFit.add(dto);
                    else emptyInsufficient.add(dto);
                }
            }
        }

        // 그룹 내 정렬: availableCapacity DESC (null = 무제한 = 최상위), locationCode ASC
        Comparator<SuggestLocationResDto> withinGroup = Comparator
                .<SuggestLocationResDto, Integer>comparing(
                        s -> s.getAvailableCapacity() == null ? Integer.MAX_VALUE : s.getAvailableCapacity(),
                        Comparator.reverseOrder())
                .thenComparing(SuggestLocationResDto::getLocationCode,
                        Comparator.nullsLast(String::compareTo));

        sameProductFit.sort(withinGroup);
        sameProductInsufficient.sort(withinGroup);
        emptyFit.sort(withinGroup);
        emptyInsufficient.sort(withinGroup);

        // 1~4순위 합쳐서 반환
        List<SuggestLocationResDto> result = new ArrayList<>();
        result.addAll(sameProductFit);
        result.addAll(sameProductInsufficient);
        result.addAll(emptyFit);
        result.addAll(emptyInsufficient);

        log.info("[적치 추천] qty={}, 동일상품(충분/부족)={}/{}, 빈위치(충분/부족)={}/{} → 총 {}건",
                qty, sameProductFit.size(), sameProductInsufficient.size(),
                emptyFit.size(), emptyInsufficient.size(), result.size());

        return result;
    }

    /**
     * qty 가 null 이거나 availableCapacity 가 null(무제한)이면 항상 충분으로 본다.
     * 그 외에는 availableCapacity >= qty 여부.
     */
    private static boolean fits(Integer availableCapacity, Integer qty) {
        if (qty == null || availableCapacity == null) return true;
        return availableCapacity >= qty;
    }

    /**
     * 추천 사유 텍스트에 수용량 정보를 붙인다.
     *  - 무제한(maxCapacity=null) → 기본 사유만
     *  - 그 외 → "{사유} · 여유 {avail}/{max}"
     */
    private static String buildReason(String base, Integer maxCapacity, Integer availableCapacity) {
        if (maxCapacity == null) return base;
        int avail = availableCapacity == null ? maxCapacity : availableCapacity;
        return base + " · 여유 " + avail + "/" + maxCapacity;
    }

    private static Integer computeFloorHeightMm(RackResDto rack) {
        if (rack == null || rack.getHeightMm() == null || rack.getLevelNo() == null || rack.getLevelNo() <= 0) {
            return null;
        }
        int floorHeight = rack.getHeightMm() / rack.getLevelNo();
        return floorHeight > 0 ? floorHeight : null;
    }

    private static Integer toPositiveInt(BigDecimal value) {
        if (value == null) return null;
        int intValue = value.setScale(0, RoundingMode.CEILING).intValue();
        return intValue > 0 ? intValue : null;
    }
}

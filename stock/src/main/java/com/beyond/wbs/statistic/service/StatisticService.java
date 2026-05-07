package com.beyond.wbs.statistic.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.WarehouseLocationsResDto;
import com.beyond.wbs.etcinout.domain.Direction;
import com.beyond.wbs.etcinout.domain.EtcInoutOrder;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderRepository;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inventory.domain.*;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.inventory.repository.InventorySnapshotRepository;
import com.beyond.wbs.inventory.repository.InventoryTransactionRepository;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.statistic.dto.*;
import com.beyond.wbs.transfer.domain.TransferOrder;
import com.beyond.wbs.transfer.repository.TransferOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 통계 서비스
 *
 * 재고 관련 통계 지표를 계산한다.
 * 모든 데이터는 stock 모듈 내부의 테이블에서 직접 조회한다.
 *  - InventorySnapshot: 월별 입출고 추이, 재고 회전율
 *  - InventoryTransaction: 일별 입출고 추이, 품번별 출고 순위
 *  - Inventory: 재고 부족 알림 현황
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticService {

    private final InventorySnapshotRepository snapshotRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryRepository inventoryRepository;
    private final MasterServiceClient masterServiceClient;
    private final InboundOrderRepository inboundOrderRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final TransferOrderRepository transferOrderRepository;
    private final EtcInoutOrderRepository etcInoutOrderRepository;

    // ============================================================
    // 1) 월별 입출고 추이
    // ============================================================

    /**
     * 월별 입출고 추이를 조회한다.
     *
     * InventoryTransaction 테이블에서 월별 재고 흐름을 집계한다.
     *
     * @param clientId    고객사 ID
     * @param from        시작월 (yyyy-MM-01)
     * @param to          종료월 (yyyy-MM-01)
     * @param warehouseId 창고 필터 (nullable — null 이면 회사 전체 합계)
     */
    public List<MonthlyInoutDto> getMonthlyInout(UUID clientId, LocalDate from, LocalDate to,
                                                  UUID warehouseId) {
        LocalDate firstMonth = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);
        LocalDateTime fromStart = firstMonth.atStartOfDay();
        LocalDateTime toEnd = lastMonth.plusMonths(1).atStartOfDay();

        List<InventoryTransaction> txList = transactionRepository
                .findByDateRangeAndStatus(clientId, warehouseId, null,
                        InventoryStatus.available, fromStart, toEnd);

        ReferenceLookup lookup = buildReferenceLookup(clientId, txList);
        Map<LocalDate, StatisticFlowAccumulator> monthlyMap = new LinkedHashMap<>();

        LocalDate current = firstMonth;
        while (!current.isAfter(lastMonth)) {
            monthlyMap.put(current, new StatisticFlowAccumulator());
            current = current.plusMonths(1);
        }

        for (InventoryTransaction tx : txList) {
            LocalDate monthKey = tx.getCreatedAt().toLocalDate().withDayOfMonth(1);
            StatisticFlowAccumulator bucket = monthlyMap.get(monthKey);
            if (bucket == null) continue;
            accumulateFlow(bucket, tx, lookup);
        }

        List<MonthlyInoutDto> result = new ArrayList<>();
        for (Map.Entry<LocalDate, StatisticFlowAccumulator> entry : monthlyMap.entrySet()) {
            result.add(toMonthlyDto(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // ============================================================
    // 2) 일별 입출고 추이
    // ============================================================

    /**
     * 일별 입출고 추이를 조회한다.
     *
     * InventoryTransaction을 날짜별로 집계하여
     * 정상입고/반품입고/정상출고/반품출고/이동/기타입출고 흐름을 반환한다.
     * available 상태 기준으로 집계 (실제 재고에 반영된 변동만).
     *
     * @param clientId    고객사 ID
     * @param from        시작일
     * @param to          종료일
     * @param warehouseId 창고 필터 (nullable)
     */
    public List<DailyInoutDto> getDailyInout(UUID clientId, LocalDate from, LocalDate to,
                                              UUID warehouseId) {
        LocalDateTime fromStart = from.atStartOfDay();
        LocalDateTime toEnd = to.plusDays(1).atStartOfDay();

        List<InventoryTransaction> txList = transactionRepository
                .findByDateRangeAndStatus(clientId, warehouseId, null,
                        InventoryStatus.available, fromStart, toEnd);

        ReferenceLookup lookup = buildReferenceLookup(clientId, txList);
        Map<LocalDate, StatisticFlowAccumulator> dailyMap = new LinkedHashMap<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            dailyMap.put(current, new StatisticFlowAccumulator());
            current = current.plusDays(1);
        }

        for (InventoryTransaction tx : txList) {
            LocalDate txDate = tx.getCreatedAt().toLocalDate();
            if (!dailyMap.containsKey(txDate)) continue;
            accumulateFlow(dailyMap.get(txDate), tx, lookup);
        }

        List<DailyInoutDto> result = new ArrayList<>();
        for (Map.Entry<LocalDate, StatisticFlowAccumulator> entry : dailyMap.entrySet()) {
            result.add(toDailyDto(entry.getKey(), entry.getValue(), lookup.products()));
        }

        return result;
    }

    private ReferenceLookup buildReferenceLookup(UUID clientId, List<InventoryTransaction> txList) {
        Set<UUID> inboundIds = new LinkedHashSet<>();
        Set<UUID> outboundIds = new LinkedHashSet<>();
        Set<UUID> transferIds = new LinkedHashSet<>();
        Set<UUID> etcIds = new LinkedHashSet<>();
        Set<UUID> productIds = new LinkedHashSet<>();

        for (InventoryTransaction tx : txList) {
            if (tx.getProductId() != null) {
                productIds.add(tx.getProductId());
            }
            if (tx.getRefId() == null || tx.getRefType() == null) continue;
            switch (tx.getRefType()) {
                case inbound_order -> inboundIds.add(tx.getRefId());
                case outbound_order -> outboundIds.add(tx.getRefId());
                case transfer_order -> transferIds.add(tx.getRefId());
                case etc_inout_order -> etcIds.add(tx.getRefId());
                default -> {
                }
            }
        }

        Map<UUID, InboundOrders> inboundOrders = inboundOrderRepository.findAllById(inboundIds).stream()
                .collect(Collectors.toMap(InboundOrders::getId, o -> o));
        Map<UUID, OutboundOrders> outboundOrders = outboundOrderRepository.findAllById(outboundIds).stream()
                .collect(Collectors.toMap(OutboundOrders::getId, o -> o));
        Map<UUID, TransferOrder> transferOrders = transferOrderRepository.findAllById(transferIds).stream()
                .collect(Collectors.toMap(TransferOrder::getId, o -> o));
        Map<UUID, EtcInoutOrder> etcOrders = etcInoutOrderRepository.findAllById(etcIds).stream()
                .collect(Collectors.toMap(EtcInoutOrder::getId, o -> o));

        Map<UUID, ProductResDto> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            try {
                masterServiceClient.getProducts(new ArrayList<>(productIds), clientId.toString())
                        .forEach(product -> products.put(product.getId(), product));
            } catch (Exception ignored) {
                // 상품 정보 조회 실패 시 sku 요약만 비운다.
            }
        }

        return new ReferenceLookup(inboundOrders, outboundOrders, transferOrders, etcOrders, products);
    }

    private void accumulateFlow(StatisticFlowAccumulator bucket, InventoryTransaction tx, ReferenceLookup lookup) {
        if (tx.getRefType() == null) {
            return;
        }

        switch (tx.getRefType()) {
            case inbound_order -> accumulateInbound(bucket, tx, lookup.inboundOrders());
            case outbound_order -> accumulateOutbound(bucket, tx, lookup.outboundOrders());
            case transfer_order -> accumulateTransfer(bucket, tx, lookup.transferOrders());
            case etc_inout_order -> accumulateEtc(bucket, tx, lookup.etcOrders());
            case manual, stock_count -> accumulateAdjustment(bucket, tx);
        }
    }

    private void accumulateInbound(StatisticFlowAccumulator bucket, InventoryTransaction tx,
                                   Map<UUID, InboundOrders> inboundOrders) {
        if (tx.getQty() <= 0) return;
        InboundOrders order = inboundOrders.get(tx.getRefId());
        if (order != null && "return".equalsIgnoreCase(order.getOriginType())) {
            bucket.returnInboundQty += tx.getQty();
        } else {
            bucket.normalInboundQty += tx.getQty();
        }
        bucket.addSku(tx.getProductId());
        if (order != null) {
            bucket.inboundOrders.put(order.getId(), toOrderLink(order.getId(), order.getOrderNo(), "inbound"));
        }
    }

    private void accumulateOutbound(StatisticFlowAccumulator bucket, InventoryTransaction tx,
                                    Map<UUID, OutboundOrders> outboundOrders) {
        if (tx.getQty() >= 0) return;
        int qty = Math.abs(tx.getQty());
        OutboundOrders order = outboundOrders.get(tx.getRefId());
        if (order != null && "return".equalsIgnoreCase(order.getOriginType())) {
            bucket.returnOutboundQty += qty;
        } else {
            bucket.normalOutboundQty += qty;
        }
        bucket.addSku(tx.getProductId());
        if (order != null) {
            bucket.outboundOrders.put(order.getId(), toOrderLink(order.getId(), order.getOrderNo(), "outbound"));
        }
    }

    private void accumulateTransfer(StatisticFlowAccumulator bucket, InventoryTransaction tx,
                                    Map<UUID, TransferOrder> transferOrders) {
        // 이동은 출발지 차감/중간보관/도착지 증가가 함께 찍히므로
        // 실제 완료 물동량은 "도착 로케이션으로 들어온 +available" 만 집계한다.
        if (tx.getQty() <= 0 || tx.getLocationId() == null) return;
        bucket.transferQty += tx.getQty();
        bucket.addSku(tx.getProductId());
        TransferOrder order = transferOrders.get(tx.getRefId());
        if (order != null) {
            bucket.transferOrders.put(order.getId(), toOrderLink(order.getId(), order.getOrderNo(), "transfer"));
        }
    }

    private void accumulateEtc(StatisticFlowAccumulator bucket, InventoryTransaction tx,
                               Map<UUID, EtcInoutOrder> etcOrders) {
        EtcInoutOrder order = etcOrders.get(tx.getRefId());
        int qty = Math.abs(tx.getQty());

        if (order != null) {
            if (order.getDirection() == Direction.in && tx.getQty() > 0) {
                bucket.etcInboundQty += tx.getQty();
            } else if (order.getDirection() == Direction.out && tx.getQty() < 0) {
                bucket.etcOutboundQty += qty;
            } else {
                return;
            }
            bucket.etcOrders.put(order.getId(),
                    toOrderLink(order.getId(), order.getOrderNo(), order.getDirection() == Direction.in ? "etc_in" : "etc_out"));
        } else {
            return;
        }

        bucket.addSku(tx.getProductId());
    }

    private void accumulateAdjustment(StatisticFlowAccumulator bucket, InventoryTransaction tx) {
        if (tx.getQty() > 0) {
            bucket.adjustmentInboundQty += tx.getQty();
        } else if (tx.getQty() < 0) {
            bucket.adjustmentOutboundQty += Math.abs(tx.getQty());
        }
        bucket.addSku(tx.getProductId());
    }

    private DailyInoutDto toDailyDto(LocalDate date, StatisticFlowAccumulator bucket,
                                     Map<UUID, ProductResDto> products) {
        return DailyInoutDto.builder()
                .date(date)
                .inboundQty(bucket.totalInbound())
                .outboundQty(bucket.totalOutbound())
                .normalInboundQty(bucket.normalInboundQty)
                .returnInboundQty(bucket.returnInboundQty)
                .normalOutboundQty(bucket.normalOutboundQty)
                .returnOutboundQty(bucket.returnOutboundQty)
                .transferQty(bucket.transferQty)
                .etcInboundQty(bucket.etcInboundQty)
                .etcOutboundQty(bucket.etcOutboundQty)
                .adjustmentInboundQty(bucket.adjustmentInboundQty)
                .adjustmentOutboundQty(bucket.adjustmentOutboundQty)
                .inboundOrders(new ArrayList<>(bucket.inboundOrders.values()))
                .outboundOrders(new ArrayList<>(bucket.outboundOrders.values()))
                .transferOrders(new ArrayList<>(bucket.transferOrders.values()))
                .etcOrders(new ArrayList<>(bucket.etcOrders.values()))
                .skuList(bucket.productIds.stream()
                        .map(products::get)
                        .filter(Objects::nonNull)
                        .map(ProductResDto::getSku)
                        .filter(Objects::nonNull)
                        .toList())
                .build();
    }

    private MonthlyInoutDto toMonthlyDto(LocalDate month, StatisticFlowAccumulator bucket) {
        return MonthlyInoutDto.builder()
                .month(month)
                .inboundQty(bucket.totalInbound())
                .outboundQty(bucket.totalOutbound())
                .normalInboundQty(bucket.normalInboundQty)
                .returnInboundQty(bucket.returnInboundQty)
                .normalOutboundQty(bucket.normalOutboundQty)
                .returnOutboundQty(bucket.returnOutboundQty)
                .transferQty(bucket.transferQty)
                .etcInboundQty(bucket.etcInboundQty)
                .etcOutboundQty(bucket.etcOutboundQty)
                .adjustmentInboundQty(bucket.adjustmentInboundQty)
                .adjustmentOutboundQty(bucket.adjustmentOutboundQty)
                .build();
    }

    private OrderLinkDto toOrderLink(UUID id, String orderNo, String type) {
        return OrderLinkDto.builder()
                .id(id)
                .orderNo(orderNo)
                .type(type)
                .build();
    }

    private record ReferenceLookup(
            Map<UUID, InboundOrders> inboundOrders,
            Map<UUID, OutboundOrders> outboundOrders,
            Map<UUID, TransferOrder> transferOrders,
            Map<UUID, EtcInoutOrder> etcOrders,
            Map<UUID, ProductResDto> products
    ) {
    }

    private static class StatisticFlowAccumulator {
        private int normalInboundQty;
        private int returnInboundQty;
        private int normalOutboundQty;
        private int returnOutboundQty;
        private int transferQty;
        private int etcInboundQty;
        private int etcOutboundQty;
        private int adjustmentInboundQty;
        private int adjustmentOutboundQty;
        private final LinkedHashMap<UUID, OrderLinkDto> inboundOrders = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, OrderLinkDto> outboundOrders = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, OrderLinkDto> transferOrders = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, OrderLinkDto> etcOrders = new LinkedHashMap<>();
        private final LinkedHashSet<UUID> productIds = new LinkedHashSet<>();

        private void addSku(UUID productId) {
            if (productId != null) {
                productIds.add(productId);
            }
        }

        private int totalInbound() {
            return normalInboundQty + returnInboundQty + etcInboundQty + adjustmentInboundQty;
        }

        private int totalOutbound() {
            return normalOutboundQty + returnOutboundQty + etcOutboundQty + adjustmentOutboundQty;
        }
    }

    // ============================================================
    // 3) 재고 회전율
    // ============================================================

    /**
     * 월별 재고 회전율을 계산한다.
     *
     * 재고 회전율 = 출고량 / 평균재고
     *  - 평균재고 = (월초재고 + 월말재고) / 2
     *  - InventorySnapshot의 openQty, closeQty, outboundQty 사용
     *
     * @param clientId 고객사 ID
     * @param from     시작월
     * @param to       종료월
     */
    public List<InventoryTurnoverDto> getInventoryTurnover(UUID clientId, LocalDate from, LocalDate to) {
        List<InventoryTurnoverDto> result = new ArrayList<>();

        LocalDate current = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);

        while (!current.isAfter(end)) {
            List<InventorySnapshot> snapshots = snapshotRepository
                    .findByClientIdAndSnapshotMonth(clientId, current);

            int totalOpen = 0;
            int totalClose = 0;
            int totalOutbound = 0;

            for (InventorySnapshot s : snapshots) {
                totalOpen += s.getOpenQty();
                totalClose += s.getCloseQty();
                totalOutbound += s.getOutboundQty();
            }

            double avgInventory = (totalOpen + totalClose) / 2.0;
            double turnoverRate = avgInventory > 0 ? totalOutbound / avgInventory : 0.0;

            result.add(InventoryTurnoverDto.builder()
                    .month(current)
                    .outboundQty(totalOutbound)
                    .averageInventory(Math.round(avgInventory * 100) / 100.0)
                    .turnoverRate(Math.round(turnoverRate * 100) / 100.0)
                    .build());

            current = current.plusMonths(1);
        }

        return result;
    }

    // ============================================================
    // 4) 품번별 출고 순위
    // ============================================================

    /**
     * 기간 내 품번별 출고량 순위를 계산한다.
     *
     * InventoryTransaction에서 txType=outbound인 트랜잭션을
     * productId별로 집계하여 출고량 내림차순으로 정렬한다.
     *
     * @param clientId 고객사 ID
     * @param from     시작일
     * @param to       종료일
     * @param limit    상위 N개 (기본 10)
     */
    public List<ProductOutboundRankDto> getProductOutboundRank(UUID clientId, LocalDate from,
                                                                LocalDate to, int limit) {
        LocalDateTime fromStart = from.atStartOfDay();
        LocalDateTime toEnd = to.plusDays(1).atStartOfDay();

        // available 상태에서 qty가 음수(출고 차감)인 트랜잭션을 조회
        List<InventoryTransaction> txList = transactionRepository
                .findByDateRangeAndStatus(clientId, null, null,
                        InventoryStatus.available, fromStart, toEnd);

        // productId별 출고량 집계 (음수 qty만 합산)
        Map<UUID, Integer> outboundByProduct = new HashMap<>();
        for (InventoryTransaction tx : txList) {
            if (tx.getQty() < 0) {
                outboundByProduct.merge(tx.getProductId(), Math.abs(tx.getQty()), Integer::sum);
            }
        }

        // 출고량 내림차순 정렬
        List<Map.Entry<UUID, Integer>> sorted = outboundByProduct.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());

        // 상품명 배치 조회
        List<UUID> productIds = sorted.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<UUID, ProductResDto> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            try {
                List<ProductResDto> products = masterServiceClient
                        .getProducts(productIds, clientId.toString());
                for (ProductResDto p : products) {
                    productMap.put(p.getId(), p);
                }
            } catch (Exception e) {
                // Master 서비스 호출 실패 시 상품명 없이 반환
            }
        }

        List<ProductOutboundRankDto> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            ProductResDto product = productMap.get(entry.getKey());
            result.add(ProductOutboundRankDto.builder()
                    .rank(rank++)
                    .productId(entry.getKey())
                    .productName(product != null ? product.getName() : null)
                    .sku(product != null ? product.getSku() : null)
                    .outboundQty(entry.getValue())
                    .build());
        }

        return result;
    }

    // ============================================================
    // 5) 창고별 적재율 요약
    // ============================================================

    /**
     * 창고별 적재율을 계산한다.
     *
     * @param clientId    고객사 ID
     * @param warehouseId 창고 ID (null이면 전체 창고 합산)
     */
    public RackUsageSummaryDto getRackUsage(UUID clientId, UUID warehouseId) {
        // 창고 미선택(초기 상태) → 0으로 채운 기본 DTO 반환
        if (warehouseId == null) {
            return RackUsageSummaryDto.builder()
                    .totalLocations(0).occupiedLocations(0).emptyLocations(0).occupancyRate(0.0)
                    .totalRacks(0).usedRacks(0).emptyRacks(0).rackOccupancyRate(0.0)
                    .build();
        }

        // Master에서 해당 창고의 전체 로케이션 정보 조회
        WarehouseLocationsResDto whLocations = masterServiceClient
                .getLocationsByWarehouseId(warehouseId, clientId);

        if (whLocations == null || whLocations.getItems() == null) {
            return RackUsageSummaryDto.builder()
                    .totalLocations(0).occupiedLocations(0).emptyLocations(0).occupancyRate(0.0)
                    .totalRacks(0).usedRacks(0).emptyRacks(0).rackOccupancyRate(0.0)
                    .build();
        }

        List<WarehouseLocationsResDto.LocationSummaryItem> items = whLocations.getItems();
        int totalLocations = items.size();
        Map<UUID, WarehouseLocationsResDto.LocationSummaryItem> itemByLocationId = new HashMap<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            itemByLocationId.put(item.getLocationId(), item);
        }

        // 전체 랙 수
        Set<UUID> allRackIds = new HashSet<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            allRackIds.add(item.getRackId());
        }
        int totalRacks = allRackIds.size();

        // 점유된 로케이션: inventory에서 해당 창고의 totalQty > 0인 locationId 셋
        List<Inventory> inventories = inventoryRepository.findByWarehouseId(warehouseId);
        Set<UUID> occupiedLocationIds = new HashSet<>();
        Set<UUID> usedRackIds = new HashSet<>();

        for (Inventory inv : inventories) {
            if (inv.getLocationId() == null) continue;
            if (!clientId.equals(inv.getClientId())) continue;
            if (inv.getTotalQty() > 0) {
                occupiedLocationIds.add(inv.getLocationId());
            }
        }

        // 점유된 로케이션이 속한 랙 확인
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            if (occupiedLocationIds.contains(item.getLocationId())) {
                usedRackIds.add(item.getRackId());
            }
        }

        int occupiedLocations = occupiedLocationIds.size();
        int emptyLocations = totalLocations - occupiedLocations;
        int usedRacks = usedRackIds.size();
        int emptyRacks = totalRacks - usedRacks;
        int usedQty = 0;
        int totalCapacity = 0;
        for (Inventory inv : inventories) {
            if (inv.getLocationId() == null) continue;
            if (!clientId.equals(inv.getClientId())) continue;
            WarehouseLocationsResDto.LocationSummaryItem item = itemByLocationId.get(inv.getLocationId());
            if (item == null || item.getMaxCapacity() == null || item.getMaxCapacity() <= 0) continue;
            usedQty += inv.getTotalQty();
        }
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            if (item.getMaxCapacity() != null && item.getMaxCapacity() > 0) {
                totalCapacity += item.getMaxCapacity();
            }
        }
        double occupancyRate = totalCapacity > 0
                ? Math.round((double) usedQty / totalCapacity * 1000) / 10.0
                : (totalLocations > 0
                    ? Math.round((double) occupiedLocations / totalLocations * 1000) / 10.0
                    : 0.0);
        double rackOccupancyRate = totalRacks > 0
                ? Math.round((double) usedRacks / totalRacks * 1000) / 10.0 : 0.0;

        return RackUsageSummaryDto.builder()
                .totalLocations(totalLocations)
                .occupiedLocations(occupiedLocations)
                .emptyLocations(emptyLocations)
                .occupancyRate(occupancyRate)
                .totalRacks(totalRacks)
                .usedRacks(usedRacks)
                .emptyRacks(emptyRacks)
                .rackOccupancyRate(rackOccupancyRate)
                .build();
    }

    // ============================================================
    // 6) 구역별 적재율
    // ============================================================

    /**
     * 구역별 적재율을 계산한다.
     *
     * @param clientId    고객사 ID
     * @param warehouseId 창고 ID
     */
    public List<ZoneRackUsageDto> getRackUsageByZone(UUID clientId, UUID warehouseId) {
        // 창고 미선택(초기 상태) → 빈 리스트 반환
        if (warehouseId == null) {
            return new ArrayList<>();
        }

        WarehouseLocationsResDto whLocations = masterServiceClient
                .getLocationsByWarehouseId(warehouseId, clientId);

        if (whLocations == null || whLocations.getItems() == null) {
            return new ArrayList<>();
        }

        List<WarehouseLocationsResDto.LocationSummaryItem> items = whLocations.getItems();
        Map<UUID, WarehouseLocationsResDto.LocationSummaryItem> itemByLocationId = new HashMap<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            itemByLocationId.put(item.getLocationId(), item);
        }

        // 점유된 로케이션 셋
        List<Inventory> inventories = inventoryRepository.findByWarehouseId(warehouseId);
        Set<UUID> occupiedLocationIds = new HashSet<>();
        for (Inventory inv : inventories) {
            if (inv.getLocationId() == null) continue;
            if (!clientId.equals(inv.getClientId())) continue;
            if (inv.getTotalQty() > 0) {
                occupiedLocationIds.add(inv.getLocationId());
            }
        }

        // Zone별 그룹핑
        Map<UUID, List<WarehouseLocationsResDto.LocationSummaryItem>> byZone = new LinkedHashMap<>();
        for (WarehouseLocationsResDto.LocationSummaryItem item : items) {
            byZone.computeIfAbsent(item.getZoneId(), k -> new ArrayList<>()).add(item);
        }

        List<ZoneRackUsageDto> result = new ArrayList<>();
        for (Map.Entry<UUID, List<WarehouseLocationsResDto.LocationSummaryItem>> entry : byZone.entrySet()) {
            List<WarehouseLocationsResDto.LocationSummaryItem> zoneItems = entry.getValue();
            if (zoneItems.isEmpty()) continue;

            String zoneName = zoneItems.get(0).getZoneName();
            String zoneCode = zoneItems.get(0).getZoneCode();

            int totalLocs = zoneItems.size();
            Set<UUID> zoneRacks = new HashSet<>();
            Set<UUID> zoneUsedRacks = new HashSet<>();
            int occupiedLocs = 0;
            int zoneCapacity = 0;
            Set<UUID> zoneLocationIds = new HashSet<>();

            for (WarehouseLocationsResDto.LocationSummaryItem item : zoneItems) {
                zoneRacks.add(item.getRackId());
                zoneLocationIds.add(item.getLocationId());
                if (item.getMaxCapacity() != null && item.getMaxCapacity() > 0) {
                    zoneCapacity += item.getMaxCapacity();
                }
                if (occupiedLocationIds.contains(item.getLocationId())) {
                    occupiedLocs++;
                    zoneUsedRacks.add(item.getRackId());
                }
            }

            int zoneUsedQty = 0;
            for (Inventory inv : inventories) {
                if (inv.getLocationId() == null) continue;
                if (!clientId.equals(inv.getClientId())) continue;
                if (!zoneLocationIds.contains(inv.getLocationId())) continue;
                WarehouseLocationsResDto.LocationSummaryItem item = itemByLocationId.get(inv.getLocationId());
                if (item == null || item.getMaxCapacity() == null || item.getMaxCapacity() <= 0) continue;
                zoneUsedQty += inv.getTotalQty();
            }

            double rate = zoneCapacity > 0
                    ? Math.round((double) zoneUsedQty / zoneCapacity * 1000) / 10.0
                    : (totalLocs > 0
                        ? Math.round((double) occupiedLocs / totalLocs * 1000) / 10.0
                        : 0.0);

            result.add(ZoneRackUsageDto.builder()
                    .zoneId(entry.getKey())
                    .zoneName(zoneName)
                    .zoneCode(zoneCode)
                    .totalLocations(totalLocs)
                    .occupiedLocations(occupiedLocs)
                    .occupancyRate(rate)
                    .totalRacks(zoneRacks.size())
                    .usedRacks(zoneUsedRacks.size())
                    .build());
        }

        return result;
    }
}

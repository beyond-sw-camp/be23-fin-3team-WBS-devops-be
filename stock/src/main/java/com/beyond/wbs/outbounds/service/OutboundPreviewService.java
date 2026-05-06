package com.beyond.wbs.outbounds.service;

import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.WarehousePageResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.outbounds.domain.ErpSalesOrderItems;
import com.beyond.wbs.outbounds.domain.ErpSalesOrders;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.domain.OutboundOrderItems;
import com.beyond.wbs.outbounds.domain.OutboundSalesOrderLinks;
import com.beyond.wbs.outbounds.dtos.CreateOutboundFromSalesOrdersReqDto;
import com.beyond.wbs.outbounds.dtos.CreateOutboundFromSalesOrdersReqDto.WarehouseAllocation;
import com.beyond.wbs.outbounds.dtos.CreateOutboundFromSalesOrdersReqDto.ProductAllocation;
import com.beyond.wbs.outbounds.dtos.CreateOutboundResDto;
import com.beyond.wbs.outbounds.dtos.ManualPreviewReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewResDto;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewResDto.ProductRequirement;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewResDto.StockStatus;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewResDto.StoreGroup;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewResDto.WarehouseStock;
import com.beyond.wbs.outbounds.dtos.SalesOrderProgressResDto;
import com.beyond.wbs.outbounds.dtos.SalesOrderProgressResDto.ItemProgress;
import com.beyond.wbs.outbounds.dtos.SalesOrderProgressResDto.LinkedItem;
import com.beyond.wbs.outbounds.dtos.SalesOrderProgressResDto.LinkedOutbound;
import com.beyond.wbs.outbounds.dtos.SplitRecommendationReqDto;
import com.beyond.wbs.outbounds.dtos.SplitRecommendationResDto;
import com.beyond.wbs.outbounds.dtos.SplitRecommendationResDto.ProductShortage;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderItemRepository;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.outbounds.repository.OutboundSalesOrderLinksRepository;
import com.beyond.wbs.websocket.WebSocketPublisher;
import com.beyond.wbs.websocket.WorkEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 출고지시서 생성 전 창고별 가용재고 미리보기 서비스.
 *
 * 핵심 로직: ship_date 시점의 (창고 × 품목) 가용재고를 한 화면에서 비교.
 *   projected = current_available + incoming_by_shipDate - other_draft_outbounds_by_shipDate
 *
 * 성능: 배치 쿼리 3개 (재고 합계 / 입고예정 / 다른 draft 출고) 로 (warehouse N × product M) 매트릭스
 * 한 번에 가져온다. N+1 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboundPreviewService {

    private final ErpSalesOrderRepository salesOrderRepo;
    private final ErpSalesOrderItemRepository salesOrderItemRepo;
    private final InventoryRepository inventoryRepo;
    private final InboundOrderRepository inboundOrderRepo;
    private final OutboundOrderRepository outboundOrderRepo;
    private final OutboundOrderItemRepository outboundOrderItemRepo;
    private final OutboundSalesOrderLinksRepository linksRepo;
    private final MasterServiceClient masterClient;
    private final NumberingUtil numberingUtil;
    private final WebSocketPublisher webSocketPublisher;

    /** 미리보기에 포함할 입고 상태 — 협력사 약속이 확정된 시점부터 (draft 제외, completed 는 이미 availableQty 반영) */
    private static final List<InboundOrderStatus> INBOUND_OPEN_FOR_PREVIEW = List.of(
            InboundOrderStatus.approved,
            InboundOrderStatus.received,
            InboundOrderStatus.placing
    );

    public OutboundPreviewResDto getPreview(UUID clientId, OutboundPreviewReqDto req) {
        if (req.getSalesOrderIds() == null || req.getSalesOrderIds().isEmpty()) {
            throw new IllegalArgumentException("선택된 수주서가 없습니다.");
        }

        // 1) 수주서 fetch + 검증 (같은 클라이언트, 같은 출고예정일)
        List<ErpSalesOrders> salesOrders = salesOrderRepo.findAllById(req.getSalesOrderIds());
        if (salesOrders.size() != req.getSalesOrderIds().size()) {
            throw new IllegalArgumentException("일부 수주서가 존재하지 않습니다.");
        }
        for (ErpSalesOrders so : salesOrders) {
            if (!so.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("권한 없는 수주서가 포함되어 있습니다.");
            }
        }
        Set<LocalDate> distinctDates = salesOrders.stream()
                .map(ErpSalesOrders::getScheduledDate)
                .collect(Collectors.toSet());
        if (distinctDates.size() > 1) {
            throw new IllegalArgumentException("출고예정일이 다른 수주서는 한 번에 묶을 수 없습니다: " + distinctDates);
        }
        LocalDate shipDate = distinctDates.iterator().next();

        // 2) 출고처별 그룹핑 — 출고처가 다르면 출고지시서 분리
        Map<UUID, List<ErpSalesOrders>> byStore = salesOrders.stream()
                .collect(Collectors.groupingBy(ErpSalesOrders::getStoreId));

        // 3) 모든 SO 라인을 한번에 fetch (N+1 방지)
        List<ErpSalesOrderItems> allItems = salesOrderItemRepo.findBySalesOrderIdIn(req.getSalesOrderIds());
        Map<UUID, List<ErpSalesOrderItems>> itemsBySalesOrderId = allItems.stream()
                .collect(Collectors.groupingBy(ErpSalesOrderItems::getSalesOrderId));

        // 4) 클라이언트의 활성 창고 목록
        WarehousePageResDto whPage = masterClient.getWarehouses(1000, clientId);
        List<WarehouseResDto> warehouses = whPage.getContent();
        List<UUID> warehouseIds = warehouses.stream().map(WarehouseResDto::getId).toList();
        Map<UUID, String> warehouseNameById = warehouses.stream()
                .collect(Collectors.toMap(WarehouseResDto::getId, WarehouseResDto::getName));

        if (warehouseIds.isEmpty()) {
            // 창고가 없으면 빈 응답
            return OutboundPreviewResDto.builder()
                    .shipDate(shipDate)
                    .storeGroups(List.of())
                    .build();
        }

        // 5) 모든 productId 수집 (배치 쿼리용)
        List<UUID> allProductIds = allItems.stream()
                .map(ErpSalesOrderItems::getProductId)
                .distinct()
                .toList();

        // 6) 배치 쿼리 3종 — (warehouse × product) 매트릭스 한 번에 채우기
        Map<MatrixKey, Integer> currentMap = toMatrix(
                inventoryRepo.sumAvailableByWarehouseAndProduct(clientId, warehouseIds, allProductIds));
        Map<MatrixKey, Integer> incomingMap = toMatrix(
                inboundOrderRepo.sumIncomingByWarehouseAndProduct(
                        clientId, warehouseIds, allProductIds, shipDate, INBOUND_OPEN_FOR_PREVIEW));
        Map<MatrixKey, Integer> draftReservedMap = toMatrix(
                outboundOrderRepo.sumDraftReservedByWarehouseAndProduct(
                        clientId, warehouseIds, allProductIds, shipDate, req.getExcludeOutboundOrderId()));

        // 7) 상품 정보 일괄 조회
        List<ProductResDto> products = allProductIds.isEmpty()
                ? List.of()
                : masterClient.getProducts(allProductIds, clientId.toString());
        Map<UUID, ProductResDto> productById = products.stream()
                .collect(Collectors.toMap(ProductResDto::getId, p -> p));

        // 8) 출고처별 응답 조립
        List<StoreGroup> storeGroups = new ArrayList<>();
        for (Map.Entry<UUID, List<ErpSalesOrders>> entry : byStore.entrySet()) {
            UUID storeId = entry.getKey();
            List<ErpSalesOrders> sosInStore = entry.getValue();

            // 출고처 정보
            String storeName = "-";
            try {
                StoreResDto store = masterClient.getStore(storeId, clientId.toString());
                if (store != null && store.getName() != null) storeName = store.getName();
            } catch (Exception e) {
                log.warn("Failed to fetch store {}: {}", storeId, e.getMessage());
            }

            // 이 출고처에서 필요한 (productId → 합산 qty)
            Map<UUID, Integer> requiredByProduct = new LinkedHashMap<>();
            for (ErpSalesOrders so : sosInStore) {
                List<ErpSalesOrderItems> lines = itemsBySalesOrderId.getOrDefault(so.getId(), List.of());
                for (ErpSalesOrderItems line : lines) {
                    requiredByProduct.merge(line.getProductId(), line.getQty(), Integer::sum);
                }
            }

            // 품목별 창고 매트릭스 빌드
            List<ProductRequirement> requirements = new ArrayList<>();
            for (Map.Entry<UUID, Integer> reqEntry : requiredByProduct.entrySet()) {
                UUID productId = reqEntry.getKey();
                int requiredQty = reqEntry.getValue();
                ProductResDto p = productById.get(productId);

                List<WarehouseStock> whStocks = new ArrayList<>();
                for (UUID whId : warehouseIds) {
                    MatrixKey key = new MatrixKey(whId, productId);
                    int current = currentMap.getOrDefault(key, 0);
                    int incoming = incomingMap.getOrDefault(key, 0);
                    int draftReserved = draftReservedMap.getOrDefault(key, 0);
                    int projected = current + incoming - draftReserved;

                    StockStatus status;
                    if (projected <= 0 && current <= 0 && incoming <= 0) status = StockStatus.NONE;
                    else if (projected >= requiredQty) status = StockStatus.SUFFICIENT;
                    else status = StockStatus.SHORTAGE;

                    whStocks.add(WarehouseStock.builder()
                            .warehouseId(whId)
                            .warehouseName(warehouseNameById.getOrDefault(whId, "-"))
                            .currentAvailableQty(current)
                            .incomingQty(incoming)
                            .draftReservedQty(draftReserved)
                            .projectedQty(projected)
                            .status(status)
                            .build());
                }

                requirements.add(ProductRequirement.builder()
                        .productId(productId)
                        .productName(p != null ? p.getName() : "-")
                        .sku(p != null ? p.getSku() : "-")
                        .requiredQty(requiredQty)
                        .warehouses(whStocks)
                        .build());
            }

            // 추천 창고 — 모든 품목이 SUFFICIENT 인 창고 (가용재고 합 많은 순)
            UUID recommended = pickRecommendedWarehouse(requirements, warehouseIds);

            storeGroups.add(StoreGroup.builder()
                    .storeId(storeId)
                    .storeName(storeName)
                    .salesOrderIds(sosInStore.stream().map(ErpSalesOrders::getId).toList())
                    .requirements(requirements)
                    .recommendedWarehouseId(recommended)
                    .build());
        }

        return OutboundPreviewResDto.builder()
                .shipDate(shipDate)
                .storeGroups(storeGroups)
                .build();
    }

    /**
     * 수동 출고지시서 생성용 미리보기.
     *
     * 수주서(SO) 가 없는 케이스. 사용자가 직접 출고처/출고예정일/품목을 입력하면
     * 수주서 흐름과 동일한 (창고 × 품목) 매트릭스 + 추천 창고를 반환한다.
     *
     * 응답 구조는 {@link OutboundPreviewResDto} 와 동일 — storeGroups 는 항상 1건.
     * excludeOutboundOrderId 는 신규 생성이므로 null (제외할 OB 없음).
     */
    public OutboundPreviewResDto getManualPreview(UUID clientId, ManualPreviewReqDto req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("미리보기에 포함할 품목이 없습니다.");
        }
        LocalDate shipDate = req.getScheduledDate();
        UUID storeId = req.getStoreId();

        // 1) 활성 창고 목록
        WarehousePageResDto whPage = masterClient.getWarehouses(1000, clientId);
        List<WarehouseResDto> warehouses = whPage.getContent();
        List<UUID> warehouseIds = warehouses.stream().map(WarehouseResDto::getId).toList();
        Map<UUID, String> warehouseNameById = warehouses.stream()
                .collect(Collectors.toMap(WarehouseResDto::getId, WarehouseResDto::getName));

        if (warehouseIds.isEmpty()) {
            return OutboundPreviewResDto.builder()
                    .shipDate(shipDate)
                    .storeGroups(List.of())
                    .build();
        }

        // 2) (productId → 합산 qty). 같은 상품이 여러 행에 있으면 합산.
        Map<UUID, Integer> requiredByProduct = new LinkedHashMap<>();
        for (ManualPreviewReqDto.Item item : req.getItems()) {
            requiredByProduct.merge(item.getProductId(), item.getQty(), Integer::sum);
        }
        List<UUID> allProductIds = new ArrayList<>(requiredByProduct.keySet());

        // 3) 배치 쿼리 3종 — (warehouse × product) 매트릭스 한 번에
        Map<MatrixKey, Integer> currentMap = toMatrix(
                inventoryRepo.sumAvailableByWarehouseAndProduct(clientId, warehouseIds, allProductIds));
        Map<MatrixKey, Integer> incomingMap = toMatrix(
                inboundOrderRepo.sumIncomingByWarehouseAndProduct(
                        clientId, warehouseIds, allProductIds, shipDate, INBOUND_OPEN_FOR_PREVIEW));
        Map<MatrixKey, Integer> draftReservedMap = toMatrix(
                outboundOrderRepo.sumDraftReservedByWarehouseAndProduct(
                        clientId, warehouseIds, allProductIds, shipDate, null));

        // 4) 상품 상세 일괄 조회
        List<ProductResDto> products = masterClient.getProducts(allProductIds, clientId.toString());
        Map<UUID, ProductResDto> productById = products.stream()
                .collect(Collectors.toMap(ProductResDto::getId, p -> p));

        // 5) 출고처 이름
        String storeName = "-";
        try {
            StoreResDto store = masterClient.getStore(storeId, clientId.toString());
            if (store != null && store.getName() != null) storeName = store.getName();
        } catch (Exception e) {
            log.warn("Failed to fetch store {}: {}", storeId, e.getMessage());
        }

        // 6) 품목별 창고 매트릭스 빌드 (getPreview 와 동일 로직)
        List<ProductRequirement> requirements = new ArrayList<>();
        for (Map.Entry<UUID, Integer> reqEntry : requiredByProduct.entrySet()) {
            UUID productId = reqEntry.getKey();
            int requiredQty = reqEntry.getValue();
            ProductResDto p = productById.get(productId);

            List<WarehouseStock> whStocks = new ArrayList<>();
            for (UUID whId : warehouseIds) {
                MatrixKey key = new MatrixKey(whId, productId);
                int current = currentMap.getOrDefault(key, 0);
                int incoming = incomingMap.getOrDefault(key, 0);
                int draftReserved = draftReservedMap.getOrDefault(key, 0);
                int projected = current + incoming - draftReserved;

                StockStatus status;
                if (projected <= 0 && current <= 0 && incoming <= 0) status = StockStatus.NONE;
                else if (projected >= requiredQty) status = StockStatus.SUFFICIENT;
                else status = StockStatus.SHORTAGE;

                whStocks.add(WarehouseStock.builder()
                        .warehouseId(whId)
                        .warehouseName(warehouseNameById.getOrDefault(whId, "-"))
                        .currentAvailableQty(current)
                        .incomingQty(incoming)
                        .draftReservedQty(draftReserved)
                        .projectedQty(projected)
                        .status(status)
                        .build());
            }

            requirements.add(ProductRequirement.builder()
                    .productId(productId)
                    .productName(p != null ? p.getName() : "-")
                    .sku(p != null ? p.getSku() : "-")
                    .requiredQty(requiredQty)
                    .warehouses(whStocks)
                    .build());
        }

        UUID recommended = pickRecommendedWarehouse(requirements, warehouseIds);

        StoreGroup storeGroup = StoreGroup.builder()
                .storeId(storeId)
                .storeName(storeName)
                .salesOrderIds(List.of())  // 수동이라 SO 없음
                .requirements(requirements)
                .recommendedWarehouseId(recommended)
                .build();

        return OutboundPreviewResDto.builder()
                .shipDate(shipDate)
                .storeGroups(List.of(storeGroup))
                .build();
    }

    /**
     * 모든 품목을 한 창고에서 충당 가능한 창고를 고른다 — 분할 모달이 필요한지 판정용.
     * 후보가 여러 개면 가용재고 합이 많은 창고 우선.
     * 한 군데도 없으면 null (= 분할 필요).
     */
    private UUID pickRecommendedWarehouse(List<ProductRequirement> requirements, List<UUID> warehouseIds) {
        UUID best = null;
        long bestScore = -1;
        for (UUID whId : warehouseIds) {
            boolean allSufficient = true;
            long score = 0;
            for (ProductRequirement r : requirements) {
                WarehouseStock ws = r.getWarehouses().stream()
                        .filter(w -> w.getWarehouseId().equals(whId))
                        .findFirst()
                        .orElse(null);
                if (ws == null || ws.getStatus() != StockStatus.SUFFICIENT) {
                    allSufficient = false;
                    break;
                }
                score += ws.getProjectedQty();
            }
            if (allSufficient && score > bestScore) {
                best = whId;
                bestScore = score;
            }
        }
        return best;
    }

    /** Object[] {warehouseId, productId, qtySum} 결과를 (warehouseId+productId) -> qty Map 으로 변환 */
    private Map<MatrixKey, Integer> toMatrix(List<Object[]> rows) {
        Map<MatrixKey, Integer> m = new HashMap<>();
        for (Object[] row : rows) {
            UUID whId = (UUID) row[0];
            UUID pid = (UUID) row[1];
            Number qty = (Number) row[2];
            m.put(new MatrixKey(whId, pid), qty.intValue());
        }
        return m;
    }

    // ============================================================
    // 분할 출고 자동 추천
    // 알고리즘: "가용재고 많은 창고부터 그리디하게 채워나가기"
    //   - 품목별로 필요량(requiredQty) 만큼 채울 때까지
    //   - 창고를 가용량(projectedQty) 큰 순서로 훑으며 가져갈 만큼 가져감
    //   - 모든 창고를 합쳐도 모자라면 부족분(shortageQty) 으로 보고
    // ============================================================

    public SplitRecommendationResDto recommendSplit(UUID clientId, SplitRecommendationReqDto req) {
        // 미리보기 결과를 그대로 재사용 — 매트릭스(창고×품목 가용량) 다시 계산하지 않음
        OutboundPreviewReqDto previewReq = OutboundPreviewReqDto.builder()
                .salesOrderIds(req.getSalesOrderIds())
                .excludeOutboundOrderId(req.getExcludeOutboundOrderId())
                .build();
        OutboundPreviewResDto preview = getPreview(clientId, previewReq);

        // 요청 출고처에 해당하는 묶음만 찾기 (한 출고처 단위로 추천)
        StoreGroup target = preview.getStoreGroups().stream()
                .filter(g -> g.getStoreId().equals(req.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("요청된 출고처가 미리보기에 없습니다: " + req.getStoreId()));

        // 결과 누적 자료구조
        // allocByWh: 창고 → (품목 → 그 창고에서 가져갈 수량)
        Map<UUID, Map<UUID, Integer>> allocByWh = new LinkedHashMap<>();
        // shortages: 모든 창고를 합쳐도 못 채운 부족분 — UI에서 빨간 경고로 노출
        List<ProductShortage> shortages = new ArrayList<>();
        int totalUnallocated = 0; // 부족분 총합 (진단용)

        for (ProductRequirement r : target.getRequirements()) {
            int remaining = r.getRequiredQty(); // 이 품목에서 아직 채워야 할 양

            // 이 품목 기준으로 가용량(projectedQty) 많은 창고부터 정렬
            // projectedQty = 현재 가용 + 입고예정 - 다른 draft 출고 (미리보기 단계에서 이미 계산됨)
            List<WarehouseStock> sortedByAvail = r.getWarehouses().stream()
                    .filter(w -> w.getProjectedQty() > 0) // 가용량 있는 창고만 후보
                    .sorted(Comparator.comparingInt(WarehouseStock::getProjectedQty).reversed())
                    .toList();

            // 그리디 채우기
            for (WarehouseStock w : sortedByAvail) {
                if (remaining <= 0) break; // 다 채웠으면 중단
                int take = Math.min(remaining, w.getProjectedQty()); // 이 창고에서 가져갈 양
                if (take <= 0) continue;
                allocByWh
                        .computeIfAbsent(w.getWarehouseId(), k -> new LinkedHashMap<>())
                        .merge(r.getProductId(), take, Integer::sum);
                remaining -= take;
            }

            // 모든 창고 다 훑었는데도 부족하면 → 부족분으로 기록
            if (remaining > 0) {
                shortages.add(ProductShortage.builder()
                        .productId(r.getProductId())
                        .productName(r.getProductName())
                        .requiredQty(r.getRequiredQty())
                        .allocatedQty(r.getRequiredQty() - remaining) // 채워진 양
                        .shortageQty(remaining)                        // 모자란 양
                        .build());
                totalUnallocated += remaining;
            }
        }

        // 응답 형식 변환 — CreateOutboundFromSalesOrdersReqDto 와 동일 구조라 FE 가 그대로 생성 API 에 제출 가능
        List<WarehouseAllocation> recommendations = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> e : allocByWh.entrySet()) {
            List<ProductAllocation> productAllocs = e.getValue().entrySet().stream()
                    .map(pe -> ProductAllocation.builder()
                            .productId(pe.getKey())
                            .qty(pe.getValue())
                            .build())
                    .toList();
            recommendations.add(WarehouseAllocation.builder()
                    .warehouseId(e.getKey())
                    .productAllocations(productAllocs)
                    .build());
        }

        return SplitRecommendationResDto.builder()
                .recommendations(recommendations)
                .unallocatedQty(totalUnallocated)
                .shortages(shortages)
                .build();
    }

    // ============================================================
    // 출고지시서 생성 (단일/분할 통합)
    //
    // 용어 정리:
    //   - SO (Sales Order) = ERP 수주서 (ErpSalesOrders)
    //   - SO 라인 = 수주서의 품목별 행 (ErpSalesOrderItems)
    //   - OB (Outbound Order) = 우리가 만드는 출고지시서 (OutboundOrders)
    //   - OB 라인 = 출고지시서의 품목별 행 (OutboundOrderItems)
    //   - 링크 = SO 라인 ↔ OB 다대다 연결 (OutboundSalesOrderLinks)
    //           "이 OB 에 이 SO 라인에서 N개 들어감" 을 한 행으로 기록
    //   - allocatedQty = SO 라인 1줄에 대해 "지금까지 만들어진 OB 들에 분배된 누적 수량"
    //                    쉽게 = "이 SO 라인에서 이미 OB 로 빠져나간 양"
    //   - remainingToAllocate = qty(주문량) - allocatedQty(빠져나간 양)
    //                            = "아직 이 SO 라인에서 추가 OB 로 더 보낼 수 있는 양"
    //   - dispatchedQty = 실제 출고(dispatch)된 누적 (이 메서드에서는 안 건드림 — dispatch 이벤트가 갱신)
    //
    // 호출 단위: 한 출고처(store) — FE 가 출고처별로 분리해서 호출.
    // warehouseAllocations 의 창고 수만큼 OB 가 생성된다 (단일이면 1개, 분할이면 N개).
    // ============================================================

    /**
     * 출고지시서 생성 흐름:
     *   1) 같은 클라이언트 / 같은 ship_date / 같은 store 검증
     *   2) 분배 요청 합계가 SO 잔여(아직 OB 로 안 보낸 양) 이내인지 검증
     *   3) 각 (warehouse) 별로 OB 1개 생성 (orderNo 자동 채번)
     *   4) OB 라인 생성 (productAllocations 만큼)
     *   5) productAllocation.qty 를 SO 라인에 그리디 분배 (잔여 큰 라인부터)
     *   6) OutboundSalesOrderLinks 행 생성 — "이 OB 에 이 SO 라인에서 take 개 들어감"
     *   7) SO 라인의 allocatedQty(분배된 양) 누적 갱신 — JPA dirty checking 으로 commit 시 반영
     */
    @Transactional
    public CreateOutboundResDto createFromSalesOrders(UUID clientId, UUID createdBy,
                                                       CreateOutboundFromSalesOrdersReqDto req) {
        // ── 1) SO + 라인 fetch 및 검증 ──
        List<ErpSalesOrders> salesOrders = salesOrderRepo.findAllById(req.getSalesOrderIds());
        if (salesOrders.size() != req.getSalesOrderIds().size()) {
            throw new IllegalArgumentException("일부 수주서가 존재하지 않습니다.");
        }

        // 한 호출에 출고처가 둘 이상이면 거부 (FE 가 출고처별로 분리 호출해야 함)
        Set<UUID> distinctStores = salesOrders.stream().map(ErpSalesOrders::getStoreId).collect(Collectors.toSet());
        if (distinctStores.size() > 1) {
            throw new IllegalArgumentException("한 호출에 여러 출고처를 묶을 수 없습니다. 출고처별로 분리해서 호출하세요.");
        }
        // 출고예정일이 다른 SO 끼리도 거부 (UX 정책상 같은 날짜만 묶기 가능)
        Set<LocalDate> distinctDates = salesOrders.stream().map(ErpSalesOrders::getScheduledDate).collect(Collectors.toSet());
        if (distinctDates.size() > 1) {
            throw new IllegalArgumentException("출고예정일이 다른 수주서는 한 번에 묶을 수 없습니다.");
        }
        // 멀티테넌시 방어 — 다른 클라이언트의 SO 끼면 차단
        for (ErpSalesOrders so : salesOrders) {
            if (!so.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("권한 없는 수주서가 포함되어 있습니다.");
            }
        }
        UUID storeId = distinctStores.iterator().next();
        LocalDate shipDate = distinctDates.iterator().next();

        // 모든 SO 라인 한번에 fetch (N+1 방지)
        List<ErpSalesOrderItems> allLines = salesOrderItemRepo.findBySalesOrderIdIn(req.getSalesOrderIds());

        // 품목별로 묶어두기 (분배할 때 같은 productId 라인을 빠르게 찾기 위함)
        Map<UUID, List<ErpSalesOrderItems>> linesByProduct = allLines.stream()
                .collect(Collectors.groupingBy(ErpSalesOrderItems::getProductId));

        // ── 2) 분배 요청 합계 검증 ──
        // "이번에 OB 로 보내려는 수량" 이 "현재 SO 들에 아직 남은 분배 가능량" 을 넘지 않는지 확인
        // 예: SO 라인 콜라 100개 중 이미 60개 다른 OB 에 보냈으면 잔여 40개. 이번에 50개 요청하면 거부.
        Map<UUID, Integer> totalRequestedByProduct = new HashMap<>();
        for (WarehouseAllocation wa : req.getWarehouseAllocations()) {
            for (ProductAllocation pa : wa.getProductAllocations()) {
                totalRequestedByProduct.merge(pa.getProductId(), pa.getQty(), Integer::sum);
            }
        }
        // 품목별로 "이 품목을 아직 OB 로 더 보낼 수 있는 양" 합산해서 비교
        for (Map.Entry<UUID, Integer> e : totalRequestedByProduct.entrySet()) {
            UUID pid = e.getKey();
            int requested = e.getValue(); // 이번에 OB 로 보내려는 양
            int remaining = linesByProduct.getOrDefault(pid, List.of()).stream()
                    .mapToInt(ErpSalesOrderItems::remainingToAllocate) // qty - allocatedQty (= 아직 OB 로 안 보낸 양)
                    .sum();
            if (requested > remaining) {
                throw new IllegalArgumentException(
                        "상품 " + pid + " 분배 요청량(" + requested + ") 이 SO 잔여 가능 수량(" + remaining + ") 을 초과합니다.");
            }
        }

        // 첫 번째 SO 의 배송지/비고를 그대로 사용 (다중 SO 때 합치기 어려움)
        // 또한 origin_id 도 첫 SO 로 — 기존 코드 호환성 + "대표 SO" 표시
        // 전체 SO 목록은 OutboundSalesOrderLinks 로 따로 추적
        ErpSalesOrders firstSo = salesOrders.get(0);

        // ── 3~7) 창고별로 OB 생성 + OB 라인 + 링크 + allocatedQty 갱신 ──
        List<UUID> createdOutboundIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (WarehouseAllocation wa : req.getWarehouseAllocations()) {
            // ── 3) OB 본체 생성 ──
            OutboundOrders ob = OutboundOrders.builder()
                    .orderNo(numberingUtil.generateOrderNo()) // OB-YYYYMMDD-NNNNN 자동 채번
                    .clientId(clientId)
                    .warehouseId(wa.getWarehouseId())
                    .storeId(storeId)
                    .createdBy(createdBy)
                    .status(OutboundOrderStatus.draft) // 초안 상태로 생성 (승인 시점에 재고 reserve)
                    // 어떤 수주서에서 만들어졌는지 표시 — 기존 컨벤션 따라 첫 SO 를 대표로 기록.
                    // 다중 SO 묶음의 경우 전체 관계는 OutboundSalesOrderLinks 테이블로 추적.
                    .originType("sales_order")
                    .originId(firstSo.getId())
                    .scheduledDate(shipDate)
                    .shippingAddress(firstSo.getShippingAddress())
                    .note(firstSo.getNote())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            outboundOrderRepo.save(ob);

            // ── 4) OB 라인 + 5~7) 링크 / allocatedQty 누적 ──
            List<OutboundOrderItems> obItems = new ArrayList<>();
            List<OutboundSalesOrderLinks> linksToSave = new ArrayList<>();

            for (ProductAllocation pa : wa.getProductAllocations()) {
                // OB 라인 — 이 창고에서 출고할 품목·수량
                obItems.add(OutboundOrderItems.builder()
                        .outboundOrdersId(ob.getId())
                        .productId(pa.getProductId())
                        .orderedQty(pa.getQty())
                        .reservedQty(0)    // 승인 시점에 재고 서비스가 reserve
                        .pickedQty(0)
                        .dispatchedQty(0)
                        .build());

                // ── 5) productAllocation.qty 를 SO 라인들에 그리디 분배 ──
                // 잔여(remainingToAllocate, = 아직 OB 로 안 보낸 양) 큰 SO 라인부터 채워나감
                // → 한 SO 가 여러 OB 로 쪼개지는 빈도 최소화
                List<ErpSalesOrderItems> candidateLines = linesByProduct.getOrDefault(pa.getProductId(), List.of()).stream()
                        .filter(l -> l.remainingToAllocate() > 0)
                        .sorted(Comparator.comparingInt(ErpSalesOrderItems::remainingToAllocate).reversed())
                        .toList();

                int remaining = pa.getQty(); // 이번 OB 라인에 대해 아직 SO 에서 가져와야 할 양
                for (ErpSalesOrderItems line : candidateLines) {
                    if (remaining <= 0) break;
                    int take = Math.min(remaining, line.remainingToAllocate()); // 이 SO 라인에서 가져갈 양
                    if (take <= 0) continue;

                    // ── 6) 링크 생성 ── "이 OB 에 이 SO 라인에서 take 개 들어감" 기록
                    linksToSave.add(OutboundSalesOrderLinks.builder()
                            .outboundOrderId(ob.getId())
                            .salesOrderId(line.getSalesOrderId())
                            .salesOrderItemId(line.getId())
                            .qty(take)
                            .build());

                    // ── 7) SO 라인의 분배된 양(allocatedQty) 누적 갱신 ──
                    // JPA 영속 컨텍스트에서 dirty checking → 트랜잭션 commit 시 UPDATE 발생
                    line.setAllocatedQty(line.getAllocatedQty() + take);
                    remaining -= take;
                }
                if (remaining > 0) {
                    // 위쪽 검증을 통과했는데 분배 못 했다면 알고리즘 버그 — 방어
                    throw new IllegalStateException(
                            "분배 실패: 상품 " + pa.getProductId() + " 잔여 " + remaining + " (검증 누락)");
                }
            }
            outboundOrderItemRepo.saveAll(obItems);
            linksRepo.saveAll(linksToSave);

            createdOutboundIds.add(ob.getId());

            // 같은 회사 관리자에게 생성 알림 push (목록만)
            webSocketPublisher.send("/topic/admin/outbound/" + clientId,
                    WorkEventMessage.builder()
                            .module("outbound")
                            .type("CREATED")
                            .clientId(clientId)
                            .orderId(ob.getId())
                            .orderNo(ob.getOrderNo())
                            .userId(createdBy)
                            .occurredAt(LocalDateTime.now())
                            .build());
        }

        // SO 라인 변경분은 dirty checking 으로 자동 flush 되지만, 명시적으로 한 번 더 save 해도 무해
        // (id 가 이미 있어 update 로 수렴)
        salesOrderItemRepo.saveAll(allLines);

        return CreateOutboundResDto.builder()
                .outboundOrderIds(createdOutboundIds)
                .unallocatedQty(0) // 검증 통과한 호출은 항상 전량 분배
                .build();
    }

    // ============================================================
    // OB 취소 시 SO 라인 동기화
    //
    // 목적: OB 가 취소되면 그 OB 가 끌어다 쓴 분배량을 SO 라인에 돌려줘야
    //       해당 SO 라인이 다시 다른 OB 로 분배 가능해진다.
    //
    // 동작:
    //   1) 이 OB 의 활성 링크들을 fetch ("이 OB 가 어느 SO 라인에서 얼마씩 끌어다 썼나" 기록)
    //   2) 각 링크가 가리키는 SO 라인의 allocatedQty(분배된 양) 에서 link.qty 만큼 차감
    //      → SO 라인 잔여(remainingToAllocate = qty - allocatedQty) 가 늘어남
    //   3) 링크들 일괄 soft delete (cancelled_at 채움) — 진행률 페이지 이력으로 보존
    //
    // 호출 위치: OutboundService.cancelOutboundOrder() 의 order.cancel() 직후
    // ============================================================
    @Transactional
    public void onOutboundCancelled(UUID outboundOrderId) {
        List<OutboundSalesOrderLinks> links = linksRepo.findByOutboundOrderIdAndCancelledAtIsNull(outboundOrderId);
        if (links.isEmpty()) {
            // 링크 없는 OB (구식 단일 SO 흐름이나 이미 동기화된 경우) — 스킵
            return;
        }

        // 영향받는 SO 라인 일괄 fetch (N+1 방지)
        List<UUID> soItemIds = links.stream()
                .map(OutboundSalesOrderLinks::getSalesOrderItemId)
                .distinct()
                .toList();
        Map<UUID, ErpSalesOrderItems> itemsById = salesOrderItemRepo.findAllById(soItemIds).stream()
                .collect(Collectors.toMap(ErpSalesOrderItems::getId, i -> i));

        // 각 링크의 qty 만큼 SO 라인의 분배된 양(allocatedQty) 에서 차감
        for (OutboundSalesOrderLinks link : links) {
            ErpSalesOrderItems soItem = itemsById.get(link.getSalesOrderItemId());
            if (soItem == null) continue;
            int newAllocated = soItem.getAllocatedQty() - link.getQty();
            // 음수 가드 — 정합성 깨졌어도 0 미만으로는 안 떨어지게
            soItem.setAllocatedQty(Math.max(0, newAllocated));
        }
        salesOrderItemRepo.saveAll(itemsById.values());

        // 링크 일괄 soft delete (한 번의 UPDATE 쿼리)
        linksRepo.softDeleteByOutboundOrderId(outboundOrderId, LocalDateTime.now());
    }

    // ============================================================
    // OB 출고 확정(dispatch) 시 SO 라인 동기화
    //
    // 목적: OB 라인 1개가 여러 SO 라인에서 끌어다 만들어졌을 수 있음.
    //       OB 가 실제로 출고한 양(= dispatchedQty) 을 그 비율대로 SO 들에 나눠줘서
    //       각 SO 의 dispatchedQty(실제 출고 누적, 진행률 페이지 "처리량") 에 더한다.
    //
    // 분배 방식 (예시):
    //   SO1 콜라 60개 주문 + SO2 콜라 40개 주문 → 묶어서 OB 라인 콜라 100개 생성
    //   링크: (SO1 라인: 60), (SO2 라인: 40)  ← 합 100 = orderedQty
    //   OB 가 실제로 80개 출고됨 (dispatchedQty=80) →
    //     SO1 dispatchedQty += 60 × (80/100) = 48
    //     SO2 dispatchedQty += 40 × (80/100) = 32
    //     합 80 ✓
    //   결과: SO1 진행률 48/60 = 80%, SO2 진행률 32/40 = 80% (OB 80% 출고와 일치)
    //
    //   반올림 오차는 "마지막 링크"가 흡수해서 합계가 정확히 dispatchedQty 와 일치하게.
    //
    // 호출 위치: OutboundService.createDispatch() 에서 OB 라인 dispatchedQty 갱신 직후
    // ============================================================
    @Transactional
    public void onOutboundDispatched(UUID outboundOrderId) {
        // ① 이 OB 의 활성 링크 — "어느 SO 라인에서 얼마씩 끌어다 썼나"
        List<OutboundSalesOrderLinks> links = linksRepo.findByOutboundOrderIdAndCancelledAtIsNull(outboundOrderId);
        if (links.isEmpty()) return;

        // ② 이 OB 의 품목별 라인 — "각 품목 실제 출고된 양(dispatchedQty)"
        List<OutboundOrderItems> obItems = outboundOrderItemRepo.findByOutboundOrdersId(outboundOrderId);
        if (obItems.isEmpty()) return;

        // ③ 영향받는 SO 라인 일괄 fetch — "dispatchedQty 갱신할 영속 객체"
        List<UUID> soItemIds = links.stream()
                .map(OutboundSalesOrderLinks::getSalesOrderItemId)
                .distinct()
                .toList();
        Map<UUID, ErpSalesOrderItems> soItemsById = salesOrderItemRepo.findAllById(soItemIds).stream()
                .collect(Collectors.toMap(ErpSalesOrderItems::getId, i -> i));

        // 링크를 productId 별로 그룹 — OB 라인은 productId 단위라 같은 productId 의 링크들과 매칭
        Map<UUID, List<OutboundSalesOrderLinks>> linksByProduct = new HashMap<>();
        for (OutboundSalesOrderLinks link : links) {
            ErpSalesOrderItems soItem = soItemsById.get(link.getSalesOrderItemId());
            if (soItem == null) continue;
            linksByProduct.computeIfAbsent(soItem.getProductId(), k -> new ArrayList<>()).add(link);
        }

        // 각 OB 라인 → 같은 productId 의 링크들에 prorate (비율) 분배
        for (OutboundOrderItems obItem : obItems) {
            int orderedQty = obItem.getOrderedQty() != null ? obItem.getOrderedQty() : 0;
            // 실제 출고 확정량 — createDispatch 에서 pickedQty 로 채워짐 (이번에 버그 픽스)
            int dispatchedQty = obItem.getDispatchedQty() != null ? obItem.getDispatchedQty() : 0;
            if (orderedQty <= 0 || dispatchedQty <= 0) continue;

            List<OutboundSalesOrderLinks> productLinks = linksByProduct.get(obItem.getProductId());
            if (productLinks == null || productLinks.isEmpty()) continue;

            // 비율 계산 + 마지막 링크가 반올림 오차 흡수 → 합계 = dispatchedQty 보장
            int distributed = 0;
            for (int i = 0; i < productLinks.size(); i++) {
                OutboundSalesOrderLinks link = productLinks.get(i);
                int amount;
                if (i == productLinks.size() - 1) {
                    amount = dispatchedQty - distributed; // 마지막 = 잔여 전부
                } else {
                    amount = (int) Math.round((double) link.getQty() * dispatchedQty / orderedQty);
                }
                if (amount < 0) amount = 0;

                ErpSalesOrderItems soItem = soItemsById.get(link.getSalesOrderItemId());
                if (soItem != null) {
                    soItem.setDispatchedQty(soItem.getDispatchedQty() + amount);
                    distributed += amount;
                }
            }
        }
        salesOrderItemRepo.saveAll(soItemsById.values());
    }

    // ============================================================
    // 수주서 진행률 조회 (진행률 페이지 메인 API)
    //
    // 조회 내용:
    //   ① SO 헤더 (거래처/날짜/상태)
    //   ② 품목별 진행률 (qty / allocatedQty / dispatchedQty / 진행률 %)
    //   ③ 이 SO 에서 만들어진 OB 목록 (취소된 것도 이력으로 포함, 회색 처리)
    //
    // 쿼리 카운트:
    //   1) SO fetch
    //   2) SO 라인 fetch
    //   3) 이 SO 의 모든 링크 fetch (활성 + 취소)
    //   4) 연결된 OB 목록 fetch (배치)
    //   5) 상품/창고/거래처 정보 (Master 서비스 Feign)
    //   N+1 없음.
    // ============================================================
    public SalesOrderProgressResDto getSalesOrderProgress(UUID clientId, UUID salesOrderId) {
        // ① SO 헤더
        ErpSalesOrders so = salesOrderRepo.findById(salesOrderId)
                .orElseThrow(() -> new IllegalArgumentException("수주서를 찾을 수 없습니다: " + salesOrderId));
        if (!so.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("권한이 없는 수주서입니다.");
        }

        // ② SO 라인 일괄 fetch
        List<ErpSalesOrderItems> lines = salesOrderItemRepo.findBySalesOrderId(salesOrderId);

        // 상품 정보 일괄 조회 (제품명/SKU)
        List<UUID> productIds = lines.stream().map(ErpSalesOrderItems::getProductId).distinct().toList();
        Map<UUID, ProductResDto> productById = productIds.isEmpty()
                ? Map.of()
                : masterClient.getProducts(productIds, clientId.toString()).stream()
                        .collect(Collectors.toMap(ProductResDto::getId, p -> p));

        // 거래처 이름
        String storeName = "-";
        try {
            StoreResDto store = masterClient.getStore(so.getStoreId(), clientId.toString());
            if (store != null && store.getName() != null) storeName = store.getName();
        } catch (Exception e) {
            log.warn("Failed to fetch store {}: {}", so.getStoreId(), e.getMessage());
        }

        // ③ 이 SO 의 모든 링크 (활성 + 취소) — 가중 진행률 계산 + 이력 표시용
        //   가중치는 활성 링크만 사용. 취소 링크는 진행률 0 으로 자연 제외.
        List<OutboundSalesOrderLinks> allLinks = linksRepo.findBySalesOrderId(salesOrderId);

        // 연결된 OB 묶기 + status 일괄 fetch (가중치 계산 + 응답 빌드 둘 다 사용)
        Map<UUID, List<OutboundSalesOrderLinks>> linksByOb = allLinks.stream()
                .collect(Collectors.groupingBy(OutboundSalesOrderLinks::getOutboundOrderId));
        Map<UUID, OutboundOrders> obById = linksByOb.isEmpty()
                ? Map.of()
                : outboundOrderRepo.findAllById(linksByOb.keySet()).stream()
                    .collect(Collectors.toMap(OutboundOrders::getId, o -> o));

        // SO 라인별 가중 진행 수량 = sum(link.qty × weight(ob.status))  — 활성 링크만
        Map<UUID, Double> weightedQtyByLineId = new HashMap<>();
        for (OutboundSalesOrderLinks link : allLinks) {
            if (link.getCancelledAt() != null) continue;
            OutboundOrders ob = obById.get(link.getOutboundOrderId());
            OutboundOrderStatus obStatus = ob != null ? ob.getStatus() : null;
            double weight = OutboundService.progressWeightForOutboundStatus(obStatus);
            int qty = link.getQty() == null ? 0 : link.getQty();
            weightedQtyByLineId.merge(link.getSalesOrderItemId(), qty * weight, Double::sum);
        }

        // 품목별 진행률 빌드 — 가중치 적용
        List<ItemProgress> itemProgresses = new ArrayList<>();
        int totalOrdered = 0, totalAllocated = 0, totalDispatched = 0;
        double totalWeightedQty = 0.0;
        for (ErpSalesOrderItems line : lines) {
            int orderedQty = line.getQty() != null ? line.getQty() : 0;
            int allocatedQty = line.getAllocatedQty() != null ? line.getAllocatedQty() : 0;
            int dispatchedQty = line.getDispatchedQty() != null ? line.getDispatchedQty() : 0;
            ProductResDto p = productById.get(line.getProductId());

            double lineWeighted = weightedQtyByLineId.getOrDefault(line.getId(), 0.0);
            int linePct = orderedQty == 0
                    ? 0
                    : Math.min(100, (int) Math.round((lineWeighted * 100.0) / orderedQty));

            itemProgresses.add(ItemProgress.builder()
                    .id(line.getId())
                    .productId(line.getProductId())
                    .productName(p != null ? p.getName() : "-")
                    .sku(p != null ? p.getSku() : "-")
                    .orderedQty(orderedQty)
                    .allocatedQty(allocatedQty)
                    .dispatchedQty(dispatchedQty)
                    .remainingToDispatch(Math.max(0, orderedQty - dispatchedQty))
                    .remainingToAllocate(line.remainingToAllocate())
                    .dispatchProgressPercent(linePct)
                    .build());

            totalOrdered += orderedQty;
            totalAllocated += allocatedQty;
            totalDispatched += dispatchedQty;
            totalWeightedQty += lineWeighted;
        }

        int totalProgressPct = totalOrdered == 0
                ? 0
                : Math.min(100, (int) Math.round((totalWeightedQty * 100.0) / totalOrdered));

        List<LinkedOutbound> linkedOutbounds = new ArrayList<>();
        if (!linksByOb.isEmpty()) {
            // OB 는 위쪽 가중치 계산 단계에서 이미 fetch 됨 (obById 재사용)
            // 창고 이름 일괄 조회
            List<UUID> warehouseIds = obById.values().stream()
                    .map(OutboundOrders::getWarehouseId).distinct().toList();
            Map<UUID, String> warehouseNameById = new HashMap<>();
            try {
                masterClient.getWarehouses(1000, clientId).getContent().stream()
                        .filter(w -> warehouseIds.contains(w.getId()))
                        .forEach(w -> warehouseNameById.put(w.getId(), w.getName()));
            } catch (Exception e) {
                log.warn("Failed to fetch warehouses: {}", e.getMessage());
            }

            for (Map.Entry<UUID, List<OutboundSalesOrderLinks>> e : linksByOb.entrySet()) {
                UUID obId = e.getKey();
                List<OutboundSalesOrderLinks> obLinks = e.getValue();
                OutboundOrders ob = obById.get(obId);
                if (ob == null) continue; // OB 가 사라진 경우 (있을 수 없지만 방어)

                // 이 OB 안에서 SO 의 어느 품목이 얼마씩 들어갔는지 (link.qty 기준)
                // 같은 productId 의 링크가 여러 개 있을 수 있어서 (다른 SO 라인이 같은 product) 합산
                Map<UUID, Integer> qtyByProduct = new LinkedHashMap<>();
                for (OutboundSalesOrderLinks lk : obLinks) {
                    // 이 SO 의 라인을 매칭해서 productId 도출
                    ErpSalesOrderItems matchLine = lines.stream()
                            .filter(l -> l.getId().equals(lk.getSalesOrderItemId()))
                            .findFirst().orElse(null);
                    if (matchLine == null) continue;
                    qtyByProduct.merge(matchLine.getProductId(), lk.getQty(), Integer::sum);
                }
                List<LinkedItem> linkedItems = qtyByProduct.entrySet().stream()
                        .map(en -> {
                            ProductResDto p = productById.get(en.getKey());
                            return LinkedItem.builder()
                                    .productId(en.getKey())
                                    .productName(p != null ? p.getName() : "-")
                                    .qty(en.getValue())
                                    .build();
                        })
                        .toList();

                // 모든 링크가 cancelled 면 이력 = 취소
                boolean allCancelled = obLinks.stream().allMatch(l -> l.getCancelledAt() != null);
                LocalDateTime cancelledAt = obLinks.stream()
                        .map(OutboundSalesOrderLinks::getCancelledAt)
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null);

                linkedOutbounds.add(LinkedOutbound.builder()
                        .outboundOrderId(obId)
                        .outboundOrderNo(ob.getOrderNo())
                        .warehouseId(ob.getWarehouseId())
                        .warehouseName(warehouseNameById.getOrDefault(ob.getWarehouseId(), "-"))
                        .status(ob.getStatus().name())
                        .scheduledDate(ob.getScheduledDate())
                        .items(linkedItems)
                        .cancelled(allCancelled)
                        .cancelledAt(cancelledAt)
                        .build());
            }

            // 정렬: 활성 우선 → 취소 이력 (생성일 역순)
            linkedOutbounds.sort(Comparator
                    .comparing(LinkedOutbound::isCancelled)
                    .thenComparing(LinkedOutbound::getOutboundOrderNo, Comparator.reverseOrder()));
        }

        return SalesOrderProgressResDto.builder()
                .id(so.getId())
                .salesOrderNumber(so.getSalesOrderNumber())
                .storeId(so.getStoreId())
                .storeName(storeName)
                .orderDate(so.getOrderDate())
                .scheduledDate(so.getScheduledDate())
                .status(so.getStatus() != null ? so.getStatus().name() : null)
                .totalOrderedQty(totalOrdered)
                .totalAllocatedQty(totalAllocated)
                .totalDispatchedQty(totalDispatched)
                .dispatchProgressPercent(totalProgressPct)
                .items(itemProgresses)
                .linkedOutbounds(linkedOutbounds)
                .build();
    }

    // 매트릭스 키 — (창고, 품목) 쌍을 Map key 로 쓰기 위한 record
    private record MatrixKey(UUID warehouseId, UUID productId) {}
}

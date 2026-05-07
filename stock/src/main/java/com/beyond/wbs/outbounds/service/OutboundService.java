package com.beyond.wbs.outbounds.service;

import com.beyond.wbs.assignment.WorkAssignmentService;
import com.beyond.wbs.assignment.WorkTaskType;
import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.*;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inbounds.repository.InboundOrderItemRepository;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.outbounds.domain.*;
import com.beyond.wbs.outbounds.dtos.*;
import com.beyond.wbs.outbounds.repository.*;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.outbounds.kafka.OutboundEventPublisher;
import com.beyond.wbs.kafka.event.OutboundStockEvent;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.websocket.WorkEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.beyond.wbs.websocket.WebSocketPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class OutboundService {
    private final OutboundOrderRepository outboundOrderRepository;
    private final ErpSalesOrderRepository erpSalesOrdersRepository;
    private final ErpSalesOrderItemRepository erpSalesOrderItemRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final NumberingUtil numberingUtil;
    private final OutboundDispatchRepository outboundDispatchRepository;
    private final OutboundDispatchItemRepository outboundDispatchItemRepository;
    private final OutboundPickinglistRepository outboundPickinglistRepository;
    private final PickingListItemRepository pickingListItemRepository;
    private final OutboundSalesOrderLinksRepository outboundSalesOrderLinksRepository;
    private final OutboundEventPublisher outboundEventPublisher;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WebSocketPublisher webSocketPublisher;
    private final OutboundPreviewService outboundPreviewService;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final WorkAssignmentService workAssignmentService;

    @Autowired
    public OutboundService(OutboundOrderRepository outboundOrderRepository,
                           ErpSalesOrderRepository erpSalesOrdersRepository,
                           ErpSalesOrderItemRepository erpSalesOrderItemRepository,
                           OutboundOrderItemRepository outboundOrderItemRepository,
                           NumberingUtil numberingUtil,
                           OutboundDispatchRepository outboundDispatchRepository,
                           OutboundDispatchItemRepository outboundDispatchItemRepository,
                           OutboundPickinglistRepository outboundPickinglistRepository,
                           PickingListItemRepository pickingListItemRepository,
                           OutboundSalesOrderLinksRepository outboundSalesOrderLinksRepository,
                           OutboundEventPublisher outboundEventPublisher,
                           MasterServiceClient masterServiceClient,
                           AccountServiceClient accountServiceClient,
                           InventoryRepository inventoryRepository,
                           ApplicationEventPublisher applicationEventPublisher,
                           OutboundPreviewService outboundPreviewService,
                           WebSocketPublisher webSocketPublisher,
                           InboundOrderRepository inboundOrderRepository,
                           InboundOrderItemRepository inboundOrderItemRepository,
                           WorkAssignmentService workAssignmentService) {
        this.outboundOrderRepository = outboundOrderRepository;
        this.erpSalesOrdersRepository = erpSalesOrdersRepository;
        this.erpSalesOrderItemRepository = erpSalesOrderItemRepository;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.numberingUtil = numberingUtil;
        this.outboundDispatchRepository = outboundDispatchRepository;
        this.outboundDispatchItemRepository = outboundDispatchItemRepository;
        this.outboundPickinglistRepository = outboundPickinglistRepository;
        this.pickingListItemRepository = pickingListItemRepository;
        this.outboundSalesOrderLinksRepository = outboundSalesOrderLinksRepository;
        this.outboundEventPublisher = outboundEventPublisher;
        this.masterServiceClient = masterServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.inventoryRepository = inventoryRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.outboundPreviewService = outboundPreviewService;
        this.webSocketPublisher = webSocketPublisher;
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
        this.workAssignmentService = workAssignmentService;
    }

    // ============================================================
    // Feign 조회 헬퍼 (실패 시 null 반환 — 이름 없어도 서비스는 동작)
    // ============================================================

    /**
     * 협력사 이름 조회 — 반품 출고 응답에서 supplier 표시용. 실패 시 null.
     */
    private String fetchSupplierName(UUID supplierId, UUID clientId) {
        if (supplierId == null) return null;
        try {
            SupplierResDto s = masterServiceClient.getSupplier(supplierId, clientId.toString());
            return s != null ? s.getName() : null;
        } catch (Exception e) {
            log.warn("supplier 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private String fetchWarehouseName(UUID warehouseId, UUID clientId) {
        WarehouseResDto w = fetchWarehouseSafe(warehouseId, clientId);
        return w != null ? w.getName() : null;
    }

    private WarehouseResDto fetchWarehouseSafe(UUID warehouseId, UUID clientId) {
        if (warehouseId == null || clientId == null) return null;
        try {
            return masterServiceClient.getWarehouse(warehouseId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] warehouse 조회 실패: {} - {}", warehouseId, e.getMessage());
            return null;
        }
    }

    private String fetchStoreName(UUID storeId, UUID clientId) {
        if (storeId == null) return null;
        try {
            StoreResDto s = masterServiceClient.getStore(storeId, clientId.toString());
            return s != null ? s.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] store 조회 실패: {} - {}", storeId, e.getMessage());
            return null;
        }
    }

    private String fetchProductName(UUID productId, UUID clientId) {
        if (productId == null) return null;
        try {
            ProductResDto p = masterServiceClient.getProduct(productId, clientId.toString());
            return p != null ? p.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] product 조회 실패: {} - {}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * 상품 상세(name, sku 등) 를 통째로 조회. 실패 시 null.
     */
    private ProductResDto fetchProduct(UUID productId, UUID clientId) {
        if (productId == null) return null;
        try {
            return masterServiceClient.getProduct(productId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] product 조회 실패: {} - {}", productId, e.getMessage());
            return null;
        }
    }

    private String fetchUserName(UUID userId, UUID adminId) {
        if (userId == null || adminId == null) return null;
        try {
            UserResDto u = accountServiceClient.getUser(userId, adminId.toString());
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] user 조회 실패: {} - {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * ERP 수주서 목록조회 (FE 선택 화면용 — 필터 + 진행률 + 품목요약 포함).
     *
     * 모든 필터는 null 허용:
     *   - status / storeId / dateFrom / dateTo / soNoKeyword
     *   - hideCompleted: true 면 processStatus == COMPLETED 제외
     *
     * 응답에 추가 정보 포함:
     *   - processStatus (NOT_STARTED / PARTIAL / COMPLETED) — allocatedQty 기준
     *   - dispatchProgressPercent — dispatchedQty 기준
     *   - itemCount / totalOrderedQty — 합계
     *   - itemPreview (상위 3개) — 행 요약 표시
     */
    public List<ErpSalesOrderResDto> getErpSalesOrders(UUID clientId,
                                                        ErpSalesOrderStatus status,
                                                        UUID storeId,
                                                        java.time.LocalDate dateFrom,
                                                        java.time.LocalDate dateTo,
                                                        String soNoKeyword,
                                                        boolean hideCompleted){
        List<ErpSalesOrders> orders = erpSalesOrdersRepository.findByFilters(
                clientId, status, storeId, dateFrom, dateTo, soNoKeyword);
        if (orders.isEmpty()) return new ArrayList<>();

        List<UUID> orderIds = orders.stream().map(ErpSalesOrders::getId).toList();

        // ── 배치 fetch (N+1 방지) ──
        // 1) 모든 SO 라인 한번에 fetch — 진행률/품목요약 계산용
        List<ErpSalesOrderItems> allLines = erpSalesOrderItemRepository.findBySalesOrderIdIn(orderIds);
        Map<UUID, List<ErpSalesOrderItems>> linesBySoId = allLines.stream()
                .collect(Collectors.groupingBy(ErpSalesOrderItems::getSalesOrderId));

        // 2) 상품 정보 일괄 조회 (품목 미리보기 productName 채움용)
        List<UUID> productIds = allLines.stream()
                .map(ErpSalesOrderItems::getProductId).distinct().toList();
        Map<UUID, ProductResDto> productByIdTmp;
        if (productIds.isEmpty()) {
            productByIdTmp = Map.of();
        } else {
            try {
                productByIdTmp = masterServiceClient.getProducts(productIds, clientId.toString()).stream()
                        .collect(Collectors.toMap(ProductResDto::getId, p -> p));
            } catch (Exception e) {
                log.warn("[Feign] products 일괄 조회 실패: {}", e.getMessage());
                productByIdTmp = Map.of();
            }
        }
        // 람다 캡처용 final 참조
        final Map<UUID, ProductResDto> productById = productByIdTmp;

        // 3) 같은 store 가 반복되므로 캐싱
        Map<UUID, String> storeNameCache = new HashMap<>();

        // 4) 구식 단일 SO → OB 호환용 — 이미 전환된 SO id (legacy)
        Set<UUID> convertedIds = new HashSet<>(
                outboundOrderRepository.findConvertedOriginIds(orderIds));

        // 5) 가중 진행률 계산용 — 모든 SO 의 활성 링크 + 연결된 OB status 일괄 조회
        List<OutboundSalesOrderLinks> allLinks = outboundSalesOrderLinksRepository
                .findBySalesOrderIdInAndCancelledAtIsNull(orderIds);
        Map<UUID, OutboundOrderStatus> obStatusById = Map.of();
        if (!allLinks.isEmpty()) {
            List<UUID> obIds = allLinks.stream()
                    .map(OutboundSalesOrderLinks::getOutboundOrderId)
                    .distinct().toList();
            obStatusById = outboundOrderRepository.findAllById(obIds).stream()
                    .collect(Collectors.toMap(OutboundOrders::getId, OutboundOrders::getStatus));
        }
        // SO 별 가중치 적용된 진행 수량 합계 = sum(link.qty × weight(ob.status))
        Map<UUID, Double> weightedQtyBySoId = new HashMap<>();
        for (OutboundSalesOrderLinks link : allLinks) {
            OutboundOrderStatus obStatus = obStatusById.get(link.getOutboundOrderId());
            double weight = progressWeightForOutboundStatus(obStatus);
            int qty = link.getQty() == null ? 0 : link.getQty();
            weightedQtyBySoId.merge(link.getSalesOrderId(), qty * weight, Double::sum);
        }

        // ── 응답 빌드 ──
        List<ErpSalesOrderResDto> result = new ArrayList<>();
        for(ErpSalesOrders order : orders){
            String storeName = storeNameCache.computeIfAbsent(
                    order.getStoreId(), id -> fetchStoreName(id, clientId));

            List<ErpSalesOrderItems> lines = linesBySoId.getOrDefault(order.getId(), List.of());

            // 합계 계산
            int totalOrdered = 0, totalAllocated = 0, totalDispatched = 0;
            for (ErpSalesOrderItems l : lines) {
                totalOrdered += l.getQty() != null ? l.getQty() : 0;
                totalAllocated += l.getAllocatedQty() != null ? l.getAllocatedQty() : 0;
                totalDispatched += l.getDispatchedQty() != null ? l.getDispatchedQty() : 0;
            }

            // 처리 상태 — allocatedQty 기반
            com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus processStatus;
            if (totalAllocated <= 0) {
                processStatus = com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus.NOT_STARTED;
            } else if (totalAllocated >= totalOrdered) {
                processStatus = com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus.COMPLETED;
            } else {
                processStatus = com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus.PARTIAL;
            }

            // hideCompleted 필터 — COMPLETED 면 응답에서 제외
            if (hideCompleted && processStatus == com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus.COMPLETED) {
                continue;
            }

            // 출고 진행률 % — 단계 가중치 (B안)
            //  link.qty × weight(ob.status) 누적값을 SO 전체 주문수량으로 나눔.
            //  분배 안 된 잔여 qty 는 0% 로 자연 가산. dispatched 만 보던 옛 로직 대비
            //  피킹중·승인됨 단계도 진행률에 반영됨.
            double weightedQty = weightedQtyBySoId.getOrDefault(order.getId(), 0.0);
            int progressPct = totalOrdered == 0
                    ? 0
                    : Math.min(100, (int) Math.round((weightedQty * 100.0) / totalOrdered));

            // 품목 미리보기 — 상위 3개 (productName + qty)
            List<ErpSalesOrderResDto.ItemPreview> itemPreview = lines.stream()
                    .limit(3)
                    .map(l -> {
                        ProductResDto p = productById.get(l.getProductId());
                        return ErpSalesOrderResDto.ItemPreview.builder()
                                .productId(l.getProductId())
                                .productName(p != null ? p.getName() : "-")
                                .qty(l.getQty())
                                .build();
                    })
                    .toList();

            ErpSalesOrderResDto dto = ErpSalesOrderResDto.builder()
                    .id(order.getId())
                    .clientId(order.getClientId())
                    .storeId(order.getStoreId())
                    .storeName(storeName)
                    .soNo(order.getSalesOrderNumber())
                    .status(order.getStatus())
                    .orderDate(order.getOrderDate())
                    .scheduledDate(order.getScheduledDate())
                    .shippingAddress(order.getShippingAddress())
                    .note(order.getNote())
                    .createdAt(order.getCreatedAt())
                    .alreadyConverted(convertedIds.contains(order.getId()))
                    // 신규 보강 필드
                    .processStatus(processStatus)
                    .dispatchProgressPercent(progressPct)
                    .itemCount(lines.size())
                    .totalOrderedQty(totalOrdered)
                    .itemPreview(itemPreview)
                    .build();
            result.add(dto);
        }
        return result;
    }

    // 출고지시서 목록조회
    // assignedTo 가 null 이면 전체 조회, 값이 있으면 해당 담당자 배당건만 반환 (모바일 "내 작업" 용)
    @Transactional(readOnly = true)
    public Page<OutboundOrderResDto> getOutboundOrders(OutboundOrderStatus status, UUID warehouseId,
                                                       UUID storeId , UUID clientId,
                                                       UUID assignedTo, Pageable pageable){
        return getOutboundOrders(status, warehouseId, storeId, clientId, assignedTo, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OutboundOrderResDto> getOutboundOrders(OutboundOrderStatus status, UUID warehouseId,
                                                       UUID storeId, UUID clientId,
                                                       UUID assignedTo, List<UUID> productIds,
                                                       Pageable pageable) {
        return getOutboundOrders(status, warehouseId, storeId, clientId, assignedTo,
                productIds, null, null, pageable);
    }

    /**
     * 출고지시서 멀티필터 검색 — productIds + originType 까지.
     *
     * - productIds: EXISTS 서브쿼리
     * - originType: 정확매칭 (예: "return" / "sales_order" / "manual")
     * - excludeOriginType: 그 값을 가진 row 제외 (originType 이 null 인 row 통과)
     *
     * originType / excludeOriginType / productIds 중 하나라도 들어오면 통합 동적 쿼리,
     * 그 외에는 기존 검증된 findByConditions 유지 (회귀 안전).
     */
    @Transactional(readOnly = true)
    public Page<OutboundOrderResDto> getOutboundOrders(OutboundOrderStatus status, UUID warehouseId,
                                                       UUID storeId, UUID clientId,
                                                       UUID assignedTo, List<UUID> productIds,
                                                       String originType, String excludeOriginType,
                                                       Pageable pageable) {
        boolean hasProducts = productIds != null && !productIds.isEmpty();
        boolean hasOriginFilter = (originType != null && !originType.isBlank())
                || (excludeOriginType != null && !excludeOriginType.isBlank());

        Page<OutboundOrders> outboundOrders;
        if (hasOriginFilter || hasProducts) {
            outboundOrders = outboundOrderRepository.searchByConditions(
                    clientId, warehouseId, storeId, status, assignedTo,
                    (originType != null && !originType.isBlank()) ? originType : null,
                    (excludeOriginType != null && !excludeOriginType.isBlank()) ? excludeOriginType : null,
                    hasProducts ? productIds : null,
                    pageable);
        } else {
            outboundOrders = outboundOrderRepository.findByConditions(
                    clientId, warehouseId, storeId, status, assignedTo, pageable);
        }

        // 같은 창고/출고처가 반복되므로 Feign 결과 캐싱 (N+1 방지)
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, String> storeNameCache = new HashMap<>();

        return outboundOrders.map(order -> {
            // 출고 지시서 품목 조회해서 총 수량 계산
            List<OutboundOrderItems> items = outboundOrderItemRepository.findByOutboundOrdersId(order.getId());

            int totalQty = 0;
            for(OutboundOrderItems item : items){
                totalQty += item.getOrderedQty();
            }

            String warehouseName = warehouseNameCache.computeIfAbsent(
                    order.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            String storeName = storeNameCache.computeIfAbsent(
                    order.getStoreId(), id -> fetchStoreName(id, clientId));

            return OutboundOrderResDto.builder()
                    .id(order.getId())
                    .orderNo(order.getOrderNo())
                    .warehouseName(warehouseName)
                    .storeName(storeName)
                    .scheduledDate(order.getScheduledDate())
                    .status(order.getStatus())
                    .totalQty(totalQty)
                    .createdAt(order.getCreatedAt())
                    .build();
        });
    }

    // 출고지시서 상세조회
    /**
     * 출고지시서 내 상품 라인 — productIds 로 필터링.
     *
     * 지시서 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    @Transactional(readOnly = true)
    public List<OutboundOrderItemResDto> getOutboundItemsByProductIds(UUID orderId,
                                                                       List<UUID> productIds,
                                                                       UUID clientId) {
        // 회사 소속 검증
        OutboundOrders order = outboundOrderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("출고지시서를 찾을 수 없거나 접근 권한이 없습니다."));

        // 라인 조회 — productIds 있으면 IN 매칭, 없으면 전체
        List<OutboundOrderItems> items = (productIds == null || productIds.isEmpty())
                ? outboundOrderItemRepository.findByOutboundOrdersId(order.getId())
                : outboundOrderItemRepository.findByOutboundOrdersIdAndProductIdIn(order.getId(), productIds);

        // 상품 정보 캐시 후 DTO 변환
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        List<OutboundOrderItemResDto> result = new ArrayList<>();
        for (OutboundOrderItems item : items) {
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            result.add(OutboundOrderItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(product != null ? product.getSku() : null)
                    .productName(product != null ? product.getName() : null)
                    .orderedQty(item.getOrderedQty())
                    .pickedQty(item.getPickedQty())
                    .dispatchedQty(item.getDispatchedQty())
                    .unitPrice(item.getUnitPrice())
                    .status(item.getStatus())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public OutboundOrderDetailResDto getOutboundOrder(UUID id, UUID requesterId, UUID clientId){
        // id + clientId로 조회 (내 회사 지시서만 접근 가능)
        OutboundOrders order = outboundOrderRepository.findByIdAndClientId(id,clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고지시서를 찾을 수 없거나 접근 권한이 없습니다."));

        // 출고지시서 품목 목록조회
        // 같은 상품이 반복될 수 있으므로 상품 정보 조회 결과 캐싱 (name, sku 한 번에)
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        List<OutboundOrderItems> items = outboundOrderItemRepository.findByOutboundOrdersId(id);
        List<OutboundOrderItemResDto> itemResDtos = new ArrayList<>();
        for(OutboundOrderItems item : items){
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            String sku = product != null ? product.getSku() : null;
            String productName = product != null ? product.getName() : null;

            OutboundOrderItemResDto dto = OutboundOrderItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(sku)
                    .productName(productName)
                    .orderedQty(item.getOrderedQty())
                    .pickedQty(item.getPickedQty())
                    .dispatchedQty(item.getDispatchedQty())
                    .unitPrice(item.getUnitPrice())
                    .status(item.getStatus())
                    .build();
            itemResDtos.add(dto);
        }

        // Feign 호출로 이름 정보 채우기
        String warehouseName = fetchWarehouseName(order.getWarehouseId(), clientId);
        String storeName = fetchStoreName(order.getStoreId(), clientId);
        String createdByName = fetchUserName(order.getCreatedBy(), requesterId);
        String approvedByName = fetchUserName(order.getApprovedBy(), requesterId);

        // 이 출고지시서에 연결된 피킹리스트 ID 조회 (중복 제거)
        List<UUID> pickingListIds = outboundPickinglistRepository.findByOutboundOrderId(order.getId())
                .stream()
                .map(OutboundPickinglist::getPickingListId)
                .distinct()
                .toList();

        // 이 출고지시서가 만들어진 원본 ERP 수주서 ID 목록 (활성 링크만, 중복 제거)
        List<UUID> sourceSalesOrderIds = outboundSalesOrderLinksRepository
                .findByOutboundOrderIdAndCancelledAtIsNull(order.getId())
                .stream()
                .map(OutboundSalesOrderLinks::getSalesOrderId)
                .distinct()
                .toList();
        // 같은 순서로 SO 번호 매핑 (id 없는 건은 빈 문자열로)
        Map<UUID, String> soNoById = sourceSalesOrderIds.isEmpty()
                ? Map.of()
                : erpSalesOrdersRepository.findAllById(sourceSalesOrderIds).stream()
                        .collect(Collectors.toMap(ErpSalesOrders::getId, ErpSalesOrders::getSalesOrderNumber));
        List<String> sourceSalesOrderNos = sourceSalesOrderIds.stream()
                .map(soId -> soNoById.getOrDefault(soId, ""))
                .toList();

        return OutboundOrderDetailResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .storeId(order.getStoreId())
                .warehouseId(order.getWarehouseId())
                .warehouseName(warehouseName)
                .storeName(storeName)
                .createdByName(createdByName)
                .approvedByName(approvedByName)
                .scheduledDate(order.getScheduledDate())
                .shippingAddress(order.getShippingAddress())
                .status(order.getStatus())
                .note(order.getNote())
                .approvedAt(order.getApprovedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResDtos)
                .pickingListIds(pickingListIds)
                .sourceSalesOrderIds(sourceSalesOrderIds)
                .sourceSalesOrderNos(sourceSalesOrderNos)
                .build();
    }

    // 출고지시서 승인
    public void approveOutboundOrder(UUID id, UUID userId, UUID clientId){
        OutboundOrders outboundOrders = outboundOrderRepository.findByIdAndClientId(id,clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고지시서를 찾을 수 없거나 접근권한이 없습니다."));

        // draft 상태만 승인가능
        if (outboundOrders.getStatus() != OutboundOrderStatus.draft){
            throw new IllegalArgumentException("초안 상태의 출고지시서만 승인할 수 있습니다.");
        }

        // 창고 타입 검증 — NORMAL 창고에서만 출고 가능
        WarehouseResDto warehouse = fetchWarehouseSafe(outboundOrders.getWarehouseId(), clientId);
        if (warehouse != null && warehouse.getWarehouseType() != null
                && !"NORMAL".equals(warehouse.getWarehouseType())) {
            throw new IllegalArgumentException(
                    "반품·불량 창고에서는 출고할 수 없습니다. 일반 창고로 재고 이동 후 출고하세요.");
        }

        // ─────────────────────────────────────────────────────
        // ATP 동기 사전 검증 — 승인 = 재고 약속(reserve) 커밋 지점
        // 생성은 자유롭게(수요 기록), 승인에서 막는다. (2단계 검증: 업계 표준)
        // Kafka 컨슈머의 reserveDistributed 도 ATP 체크를 하지만 비동기라 사용자에게 전달되지 않으므로
        //   승인 응답을 차단하려면 여기서 동기 검증 필요.
        // 공식은 InventoryService.reserveDistributed 와 동일: available + incoming + pending
        // ─────────────────────────────────────────────────────
        UUID warehouseId = outboundOrders.getWarehouseId();
        List<OutboundOrderItems> itemsForAtp = outboundOrderItemRepository.findByOutboundOrdersId(id);
        List<String> shortageMessages = new ArrayList<>();
        for (OutboundOrderItems item : itemsForAtp) {
            UUID productId = item.getProductId();
            int needed = item.getOrderedQty();

            int totalAvailable = 0;
            int totalIncoming = 0;
            int totalPending = 0;
            for (Inventory inv : inventoryRepository.findByProductId(productId)) {
                if (!warehouseId.equals(inv.getWarehouseId())) continue;
                totalAvailable += inv.getAvailableQty();
                totalIncoming += inv.getIncomingQty();
                totalPending += inv.getPendingQty();
            }
            int atp = totalAvailable + totalIncoming + totalPending;

            if (atp < needed) {
                ProductResDto product = fetchProduct(productId, clientId);
                String label = product != null && product.getName() != null
                        ? product.getName()
                        : (product != null ? product.getSku() : productId.toString().substring(0, 8));
                shortageMessages.add(String.format(
                        "%s (요청 %d, ATP %d = 가용 %d + 입고예정 %d + 검수중 %d)",
                        label, needed, atp, totalAvailable, totalIncoming, totalPending));
            }
        }
        if (!shortageMessages.isEmpty()) {
            throw new IllegalStateException(
                    "출고 가능 수량(ATP) 이 부족한 상품이 있어 승인할 수 없습니다.\n - "
                            + String.join("\n - ", shortageMessages));
        }

        UUID assignedTo = workAssignmentService.assign(WorkTaskType.OUTBOUND_DISPATCH, clientId, userId);
        outboundOrders.approve(userId, assignedTo);
        outboundOrderRepository.save(outboundOrders);

        // Kafka 이벤트 발행 (가용재고 감소·예약재고 증가)
        List<OutboundOrderItems> items = outboundOrderItemRepository.findByOutboundOrdersId(id);
        List<OutboundStockEvent.Item> eventItems = new ArrayList<>();
        for (OutboundOrderItems item : items) {
            eventItems.add(OutboundStockEvent.Item.builder()
                    .productId(item.getProductId())
                    .locationId(null)  // TODO: 피킹 위치 확정 후 채워넣기
                    .qty(item.getOrderedQty())
                    .build());
        }
        outboundEventPublisher.publishApproved(OutboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(outboundOrders.getWarehouseId())
                .refId(outboundOrders.getId())
                .userId(userId)
                .items(eventItems)
                .build());

        // 지시서 PDF 발행 요청 — Spring 이벤트로 게시.
        // 같은 트랜잭션 안에서 게시되지만, AFTER_COMMIT 리스너(InstructionIssueEventBridge)가
        // 커밋 후에만 Kafka로 publish하므로, 이 트랜잭션이 롤백되면 PDF는 만들어지지 않는다.
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.OUTBOUND_ORDER,
                outboundOrders.getId(),
                outboundOrders.getOrderNo(),
                clientId,
                userId
        ));

        // 같은 회사 관리자에게 승인 알림 push (목록 + 상세)
        WorkEventMessage approvedMsg = WorkEventMessage.builder()
                .module("outbound")
                .type("APPROVED")
                .clientId(clientId)
                .orderId(outboundOrders.getId())
                .orderNo(outboundOrders.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/outbound/" + clientId, approvedMsg);
        webSocketPublisher.send("/topic/admin/outbound/" + clientId + "/" + outboundOrders.getId(), approvedMsg);
    }

    // 출고지시서 취소
    // - draft 상태 취소: 아직 재고 예약 안 했으므로 DB 상태만 변경 (Kafka 발행 불필요)
    // - approved 상태 취소: 승인 시 예약된 재고를 원복해야 하므로 Kafka 이벤트 발행
    public void cancelOutboundOrder(UUID id, UUID userId, UUID clientId){
        OutboundOrders order = outboundOrderRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고 지시서를 찾을 수 없거나 접근 권한이 없습니다."));

        // draft 또는 approved 상태만 취소 가능
        if(order.getStatus()!= OutboundOrderStatus.draft && order.getStatus()!= OutboundOrderStatus.approved){
            throw new IllegalArgumentException("처리중 이후 상태는 취소할 수 없습니다.");
        }

        // [Kafka] approved 상태 취소 시에만 재고 예약 해제 이벤트 발행
        // - 승인 시 reserve()로 잠근 재고를 unreserve()로 원복
        // - draft 상태는 재고 변동이 없었으므로 이벤트 발행 안 함
        if(order.getStatus() == OutboundOrderStatus.approved){
            List<OutboundOrderItems> items = outboundOrderItemRepository.findByOutboundOrdersId(id);
            List<OutboundStockEvent.Item> eventItems = new ArrayList<>();
            for (OutboundOrderItems item : items) {
                eventItems.add(OutboundStockEvent.Item.builder()
                        .productId(item.getProductId())
                        .locationId(null)  // TODO: 피킹 위치 확정 후 채워넣기
                        .qty(item.getOrderedQty())
                        .build());
            }
            outboundEventPublisher.publishCancelled(OutboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(order.getWarehouseId())
                    .refId(order.getId())
                    .userId(userId)
                    .items(eventItems)
                    .build());
        }

        order.cancel();
        outboundOrderRepository.save(order);

        // SO 라인 동기화 — 이 OB 가 끌어다 쓴 분배량 원복 + 링크 soft delete
        outboundPreviewService.onOutboundCancelled(order.getId());

        // 같은 회사 관리자에게 취소 알림 push (목록 + 상세)
        WorkEventMessage cancelledMsg = WorkEventMessage.builder()
                .module("outbound")
                .type("CANCELLED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/outbound/" + clientId, cancelledMsg);
        webSocketPublisher.send("/topic/admin/outbound/" + clientId + "/" + order.getId(), cancelledMsg);
    }

    /**
     * 수동 출고지시서 생성 — ERP 수주서 없이 관리자가 직접 입력.
     * draft 상태로 저장. 이후 흐름(승인 → reserve → wave) 은 ERP 수주서 흐름과 동일.
     * SO 연결이 없으므로 OutboundSalesOrderLinks 는 만들지 않는다.
     */
    public OutboundOrderResDto createManual(CreateOutboundReqDto dto, UUID userId, UUID clientId) {
        // 1. 창고 타입 검증 — 일반(NORMAL) 창고에서만 출고 가능 (반품/폐기 창고 제외)
        WarehouseResDto warehouse = fetchWarehouseSafe(dto.getWarehouseId(), clientId);
        if (warehouse != null && warehouse.getWarehouseType() != null
                && !"NORMAL".equals(warehouse.getWarehouseType())) {
            throw new IllegalArgumentException(
                    "반품·불량 창고에서는 출고할 수 없습니다. 일반 창고를 선택해 주세요.");
        }

        // 2. 출고지시서 생성 (draft, originType="manual", originId=null)
        OutboundOrders outboundOrder = OutboundOrders.builder()
                .orderNo(numberingUtil.generateOrderNo())
                .clientId(clientId)
                .warehouseId(dto.getWarehouseId())
                .storeId(dto.getStoreId())
                .createdBy(userId)
                .status(OutboundOrderStatus.draft)
                .originType("manual")
                .originId(null)
                .scheduledDate(dto.getScheduledDate())
                .shippingAddress(dto.getShippingAddress())
                .note(dto.getNote())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        outboundOrderRepository.save(outboundOrder);

        // 3. 품목 생성 + 총 수량 계산
        int totalQty = 0;
        for (CreateOutboundReqDto.Item item : dto.getItems()) {
            OutboundOrderItems orderItem = OutboundOrderItems.builder()
                    .outboundOrdersId(outboundOrder.getId())
                    .productId(item.getProductId())
                    .orderedQty(item.getQty())
                    .pickedQty(0)
                    .dispatchedQty(0)
                    .unitPrice(item.getUnitPrice() != null
                            ? item.getUnitPrice() : java.math.BigDecimal.ZERO)
                    .status(OutboundOrderItemsStatus.pending)
                    .build();
            outboundOrderItemRepository.save(orderItem);
            totalQty += item.getQty();
        }

        // 4. 통계/대시보드 카운트용 — 생성 이벤트 발행 (재고 영향 없음)
        outboundEventPublisher.publishCreated(OutboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(outboundOrder.getWarehouseId())
                .refId(outboundOrder.getId())
                .userId(userId)
                .items(List.of())
                .build());

        // 같은 회사 관리자에게 생성 알림 push (목록만)
        WorkEventMessage createdMsg = WorkEventMessage.builder()
                .module("outbound")
                .type("CREATED")
                .clientId(clientId)
                .orderId(outboundOrder.getId())
                .orderNo(outboundOrder.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/outbound/" + clientId, createdMsg);

        // 5. 응답 DTO 조립
        return OutboundOrderResDto.builder()
                .id(outboundOrder.getId())
                .orderNo(outboundOrder.getOrderNo())
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .storeName(fetchStoreName(outboundOrder.getStoreId(), clientId))
                .scheduledDate(outboundOrder.getScheduledDate())
                .status(outboundOrder.getStatus())
                .totalQty(totalQty)
                .createdAt(outboundOrder.getCreatedAt())
                .build();
    }

    /**
     * 반품 출고 생성 — 원본 입고지시서 매칭형.
     *
     * 흐름:
     *   1. 원본 입고지시서 조회 + 회사 격리 검증
     *   2. supplierId 매칭 검증 (Body == 원본 입고처)
     *   3. 누적 반품량 검증 — receivedQty 초과 방지
     *   4. OutboundOrder 생성: originType="return", originId=inboundOrderId,
     *      destinationType="supplier", supplierId=원본 입고처, storeId=null
     *   5. OutboundOrderItems 일괄 생성
     *   6. 일반 출고와 동일한 워크플로우 진입 가능 (draft → approve → reserve → wave → dispatch)
     */
    public OutboundOrderResDto createReturnOutbound(ReturnOutboundCreateReqDto dto,
                                                    UUID userId, UUID clientId) {
        // 1. 원본 입고지시서 조회 + 회사 격리
        InboundOrders inbound = inboundOrderRepository.findById(dto.getInboundOrderId())
                .orElseThrow(() -> new NoSuchElementException("원본 입고지시서를 찾을 수 없습니다."));
        if (!inbound.getClientId().equals(clientId)) {
            throw new SecurityException("같은 회사의 입고지시서만 반품할 수 있습니다.");
        }

        // 2. supplierId 매칭 검증
        if (inbound.getSupplierId() == null
                || !inbound.getSupplierId().equals(dto.getSupplierId())) {
            throw new IllegalArgumentException(
                    "반품 대상 입고처가 원본 입고지시서의 입고처와 일치하지 않습니다.");
        }

        // 3. 원본 라인의 receivedQty 매핑
        List<InboundOrderItems> originalItems = inboundOrderItemRepository
                .findByInboundOrderId(inbound.getId());
        Map<UUID, Integer> receivedByProduct = new HashMap<>();
        for (InboundOrderItems oi : originalItems) {
            receivedByProduct.merge(oi.getProductId(), oi.getReceivedQty(), Integer::sum);
        }

        // 4. 기존 반품 출고 누적 합산
        Map<UUID, Integer> alreadyReturned = new HashMap<>();
        for (Object[] row : outboundOrderRepository.sumReturnedByProductForInbound(inbound.getId())) {
            alreadyReturned.put((UUID) row[0], ((Number) row[1]).intValue());
        }

        // 5. 누적 + 새 요청 ≤ receivedQty 검증
        for (ReturnOutboundCreateReqDto.Item item : dto.getItems()) {
            int received = receivedByProduct.getOrDefault(item.getProductId(), 0);
            int already = alreadyReturned.getOrDefault(item.getProductId(), 0);
            if (received <= 0) {
                throw new IllegalArgumentException(
                        "원본 입고에 검수된 수량이 없는 상품은 반품할 수 없습니다 (productId="
                                + item.getProductId() + ").");
            }
            if (already + item.getQty() > received) {
                throw new IllegalArgumentException(
                        "반품 수량 초과 (productId=" + item.getProductId()
                                + "): 검수수량 " + received
                                + ", 기 반품 " + already
                                + ", 요청 " + item.getQty());
            }
        }

        // 6. OutboundOrder 생성 (draft, originType=return, destination=supplier)
        OutboundOrders outboundOrder = OutboundOrders.builder()
                .orderNo(numberingUtil.generateOrderNo())
                .clientId(clientId)
                .warehouseId(dto.getWarehouseId())
                .storeId(null)                              // 반품 출고는 store 없음
                .supplierId(inbound.getSupplierId())
                .destinationType("supplier")
                .createdBy(userId)
                .status(OutboundOrderStatus.draft)
                .originType("return")
                .originId(inbound.getId())
                .returnReason(dto.getReason())
                .scheduledDate(dto.getScheduledDate() != null
                        ? dto.getScheduledDate() : LocalDate.now())
                .note(dto.getNote())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        outboundOrderRepository.save(outboundOrder);

        // 7. 품목 생성 + 총 수량 계산
        int totalQty = 0;
        for (ReturnOutboundCreateReqDto.Item item : dto.getItems()) {
            OutboundOrderItems orderItem = OutboundOrderItems.builder()
                    .outboundOrdersId(outboundOrder.getId())
                    .productId(item.getProductId())
                    .orderedQty(item.getQty())
                    .pickedQty(0)
                    .dispatchedQty(0)
                    .unitPrice(java.math.BigDecimal.ZERO)
                    .status(OutboundOrderItemsStatus.pending)
                    .build();
            outboundOrderItemRepository.save(orderItem);
            totalQty += item.getQty();
        }

        // 8. 통계/대시보드 카운트용 — 생성 이벤트 발행 (재고 영향은 approve 시점에 reserve 로 발생)
        outboundEventPublisher.publishCreated(OutboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(outboundOrder.getWarehouseId())
                .refId(outboundOrder.getId())
                .userId(userId)
                .items(List.of())
                .build());

        // 같은 회사 관리자에게 생성 알림 push (목록만, type 으로 반품 구분)
        WorkEventMessage createdMsg = WorkEventMessage.builder()
                .module("outbound")
                .type("CREATED_RETURN")
                .clientId(clientId)
                .orderId(outboundOrder.getId())
                .orderNo(outboundOrder.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/outbound/" + clientId, createdMsg);

        // 9. 응답 DTO — 반품 컨텍스트 메타 포함
        WarehouseResDto warehouse = fetchWarehouseSafe(outboundOrder.getWarehouseId(), clientId);
        return OutboundOrderResDto.builder()
                .id(outboundOrder.getId())
                .orderNo(outboundOrder.getOrderNo())
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .storeName(null)
                .scheduledDate(outboundOrder.getScheduledDate())
                .status(outboundOrder.getStatus())
                .totalQty(totalQty)
                .createdAt(outboundOrder.getCreatedAt())
                .originType("return")
                .originId(inbound.getId())
                .returnFromOrderNo(inbound.getOrderNo())
                .destinationType("supplier")
                .supplierId(inbound.getSupplierId())
                .supplierName(fetchSupplierName(inbound.getSupplierId(), clientId))
                .returnReason(dto.getReason())
                .build();
    }

    // 출고 전표 생성(출고확정)
    public UUID createDispatch(DispatchCreateReqDto dto, UUID userId, UUID clientId){
        OutboundOrders order = outboundOrderRepository.findByIdAndClientId(dto.getOutboundOrderId(), clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고 지시서를 찾을 수 없거나 접근 권한이 없습니다."));

        // in_progress (처리중)상태만 출고 확정 가능
        if(order.getStatus() != OutboundOrderStatus.in_progress){
            throw new IllegalArgumentException("처리중 상태의 출고지시서만 출고 확정할 수 있습니다.");
        }

        // 피킹 완료 여부 검증
        // pending 품목이 하나라도 있으면 출고 확정 불가
        List<OutboundOrderItems> orderItemsForCheck = outboundOrderItemRepository.findByOutboundOrdersId(order.getId());
        for(OutboundOrderItems item : orderItemsForCheck){
            if(item.getStatus() == OutboundOrderItemsStatus.pending){
                throw new IllegalArgumentException("아직 피킹되지 않은 품목이 있어 출고 확정할 수 없습니다.");
            }
        }

        // 출고 전표 생성
        OutboundDispatch outboundDispatch = OutboundDispatch.builder()
                .clientId(clientId)
                .warehouseId(order.getWarehouseId())
                .outboundOrdersId(order.getId())
                .dispatchedBy(userId)
                .dispatchedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .dispatchNo(numberingUtil.generateDispatchNo())
                .build();

        outboundDispatchRepository.save(outboundDispatch);

        // 출고지시서 품목 기준으로 출고 전표 품목 생성
        // 동시에 OB 라인의 dispatchedQty(실제 출고 확정량) 를 pickedQty 로 갱신
        //   — 픽킹된 양이 곧 출고 확정량 (DispatchItem.qty 와 동일 기준)
        //   — 진행률 페이지/링크 동기화 등에서 OB.dispatchedQty 를 신뢰값으로 사용 가능하도록
        List<OutboundOrderItems> orderItems = outboundOrderItemRepository.findByOutboundOrdersId(order.getId());
        for(OutboundOrderItems item : orderItems){
            int pickedQty = item.getPickedQty() != null ? item.getPickedQty() : 0;

            OutboundDispatchItems dispatchItems = OutboundDispatchItems.builder()
                    .dispatchId(outboundDispatch.getId())
                    .orderItemId(item.getId())
                    .productId(item.getProductId())
                    .qty(pickedQty)
                    .build();

            outboundDispatchItemRepository.save(dispatchItems);

            // OB 라인의 출고확정량 갱신 (기존 코드 누락 — 항상 0이던 것 정정)
            item.setDispatchedQty(pickedQty);
        }
        outboundOrderItemRepository.saveAll(orderItems);

        // 출고 지시서 상태 업데이트
        // 전체 품목 완료면 completed, 일부 shortage면 partial
        boolean allCompleted = true;
        for(OutboundOrderItems item : orderItems){
            if(item.getStatus() != OutboundOrderItemsStatus.completed){
                allCompleted = false;
                break;
            }
        }
        OutboundOrderStatus newStatus = allCompleted? OutboundOrderStatus.completed : OutboundOrderStatus.partial;

        order.changeStatus(newStatus);
        outboundOrderRepository.save(order);

        // [Kafka] 출고 확정 이벤트 발행
        // - 예약재고에서 최종 차감 (실물이 창고에서 빠짐)
        // - pickedQty 기준으로 발행 (실제 피킹된 수량만큼만 차감)
        List<OutboundStockEvent.Item> eventItems = new ArrayList<>();
        for (OutboundOrderItems item : orderItems) {
            eventItems.add(OutboundStockEvent.Item.builder()
                    .productId(item.getProductId())
                    .locationId(null)  // TODO: 피킹 위치 확정 후 채워넣기
                    .qty(item.getPickedQty())
                    .build());
        }
        outboundEventPublisher.publishCompleted(OutboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(order.getWarehouseId())
                .refId(order.getId())
                .userId(userId)
                .items(eventItems)
                .build());

        // SO 라인 동기화 — OB.dispatchedQty 를 비율대로 SO 라인의 dispatchedQty 에 분배
        // (진행률 페이지의 "처리량" 갱신)
        outboundPreviewService.onOutboundDispatched(order.getId());

        // 출고전표 PDF 발행 요청
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.OUTBOUND_DISPATCH,
                outboundDispatch.getId(),
                outboundDispatch.getDispatchNo(),
                clientId,
                userId
        ));

        // 웹 관리자에게 출고 확정 알림 push — 목록 + 상세 둘 다
        WorkEventMessage closeMsg = WorkEventMessage.builder()
                .module("outbound")
                .type(allCompleted ? "COMPLETED" : "PARTIAL")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/outbound/" + clientId, closeMsg);
        webSocketPublisher.send("/topic/admin/outbound/" + clientId + "/" + order.getId(), closeMsg);

        return outboundDispatch.getId();
    }

    // ============================================================
    // 잔여 출고 처리 (force release residual reserve)
    // ============================================================
    //
    // 코드 버그/비정상 종료 등으로 출고확정 시 일부 위치만 release 되고 reserved 가
    // 시스템에 좀비처럼 남는 케이스를 운영자가 화면에서 정리하는 액션.
    //
    // 운영자 판단 전제: 실재고가 비어있음을 확인했고, 시스템상 남은 reserved 도
    // 사실은 이미 출고된 양으로 판단한 경우.
    //
    // 흐름:
    //   1) 이 출고지시서가 사용한 picking_list_items 의 (productId, locationId) 수집
    //   2) 각 위치의 inventory.reservedQty > 0 이면 "잔여" 로 판단
    //   3) 잔여만큼 outbound.completed 보상 이벤트 발행
    //      → InventoryEventConsumer 가 받아서 release 처리 (기존 흐름 재사용)
    //   4) 결과: reserved 0, 출고 트랜잭션 추가 기록 → 정합성 회복
    public List<UUID> forceReleaseResidual(UUID outboundOrderId, UUID userId, UUID clientId) {
        OutboundOrders order = outboundOrderRepository.findByIdAndClientId(outboundOrderId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없거나 접근 권한이 없습니다."));

        if (order.getStatus() != OutboundOrderStatus.completed
                && order.getStatus() != OutboundOrderStatus.partial) {
            throw new IllegalArgumentException("완료/부분완료 상태의 출고지시서만 잔여 출고 처리 가능합니다.");
        }

        // 1) 이 출고지시서가 사용한 picking_list_items 수집
        List<UUID> pickingListIds = outboundPickinglistRepository
                .findByOutboundOrderId(outboundOrderId).stream()
                .map(OutboundPickinglist::getPickingListId)
                .distinct()
                .toList();

        Map<UUID, UUID> productByLocation = new HashMap<>();
        for (UUID plId : pickingListIds) {
            for (PickingListItems pli : pickingListItemRepository.findByPickingListId(plId)) {
                if (pli.getLocationId() != null) {
                    productByLocation.put(pli.getLocationId(), pli.getProductId());
                }
            }
        }

        if (productByLocation.isEmpty()) {
            throw new IllegalArgumentException("이 출고지시서에 연결된 피킹 위치가 없습니다.");
        }

        // 2) 각 위치의 잔여 reserved 확인
        UUID warehouseId = order.getWarehouseId();
        List<OutboundStockEvent.Item> eventItems = new ArrayList<>();
        List<UUID> residualLocations = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : productByLocation.entrySet()) {
            UUID locationId = entry.getKey();
            UUID productId = entry.getValue();

            Inventory inv = inventoryRepository
                    .findByProductIdAndWarehouseIdAndLocationId(productId, warehouseId, locationId)
                    .orElse(null);
            if (inv == null || inv.getReservedQty() <= 0) continue;

            eventItems.add(OutboundStockEvent.Item.builder()
                    .productId(productId)
                    .locationId(locationId)
                    .qty(inv.getReservedQty())
                    .build());
            residualLocations.add(locationId);
        }

        if (eventItems.isEmpty()) {
            throw new IllegalArgumentException("잔여 reserve 가 없습니다 — 정합성이 이미 맞춰져 있습니다.");
        }

        // 3) 보상 이벤트 발행 — InventoryEventConsumer 가 받아서 release 처리
        outboundEventPublisher.publishCompleted(OutboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .refId(outboundOrderId)
                .userId(userId)
                .items(eventItems)
                .build());

        // 4) outbound_order_items.pickedQty 보정 — UI 화면 정합성 회복
        // (productId 별로 이번 release 양을 합산해서 기존 pickedQty 에 누적)
        Map<UUID, Integer> releasedByProduct = new HashMap<>();
        for (OutboundStockEvent.Item it : eventItems) {
            releasedByProduct.merge(it.getProductId(), it.getQty(), Integer::sum);
        }

        List<OutboundOrderItems> orderItems = outboundOrderItemRepository.findByOutboundOrdersId(outboundOrderId);
        for (OutboundOrderItems oi : orderItems) {
            Integer extra = releasedByProduct.get(oi.getProductId());
            if (extra == null || extra <= 0) continue;

            int newPicked = (oi.getPickedQty() == null ? 0 : oi.getPickedQty()) + extra;
            OutboundOrderItemsStatus newStatus = newPicked >= oi.getOrderedQty()
                    ? OutboundOrderItemsStatus.completed
                    : OutboundOrderItemsStatus.shortage;
            oi.applyPickedResult(newPicked, newStatus);
            outboundOrderItemRepository.save(oi);
        }

        log.info("[forceReleaseResidual] outboundOrderId={}, residualLocations={}, pickedQtyAdjusted={}",
                outboundOrderId, residualLocations.size(), releasedByProduct.size());

        return residualLocations;
    }

    // 출고 전표 상세 조회 (전표 ID로)
    /**
     * 출고전표 내 상품 라인 — productIds 로 필터링.
     *
     * 출고전표 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    @Transactional(readOnly = true)
    public List<OutboundDispatchItemResDto> getDispatchItemsByProductIds(UUID dispatchId,
                                                                          List<UUID> productIds,
                                                                          UUID clientId) {
        OutboundDispatch dispatch = outboundDispatchRepository.findByIdAndClientId(dispatchId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("출고 전표를 찾을 수 없거나 접근 권한이 없습니다."));

        // 라인 조회 — productIds 있으면 IN 매칭, 없으면 전체
        List<OutboundDispatchItems> items = (productIds == null || productIds.isEmpty())
                ? outboundDispatchItemRepository.findByDispatchId(dispatchId)
                : outboundDispatchItemRepository.findByDispatchIdAndProductIdIn(dispatchId, productIds);

        // 단가는 출고지시서 라인에서 가져옴
        Map<UUID, OutboundOrderItems> orderItemMap = outboundOrderItemRepository
                .findByOutboundOrdersId(dispatch.getOutboundOrdersId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(OutboundOrderItems::getId, oi -> oi));

        Map<UUID, ProductResDto> productCache = new HashMap<>();
        List<OutboundDispatchItemResDto> result = new ArrayList<>();
        for (OutboundDispatchItems item : items) {
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            OutboundOrderItems originalItem = orderItemMap.get(item.getOrderItemId());
            java.math.BigDecimal unitPrice = originalItem != null ? originalItem.getUnitPrice() : null;

            result.add(OutboundDispatchItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(product != null ? product.getSku() : null)
                    .productName(product != null ? product.getName() : null)
                    .qty(item.getQty())
                    .unitPrice(unitPrice)
                    .lotNo(item.getLotNo())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public OutboundDispatchResDto getDispatch(UUID id, UUID requesterId, UUID clientId){
        OutboundDispatch outboundDispatch = outboundDispatchRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고 전표를 찾을 수 없거나 접근 권한이 없습니다."));
        return buildDispatchResDto(outboundDispatch, requesterId, clientId);
    }

    // 출고 전표 상세 조회 (출고지시서 ID로) — FE 출고 상세 화면이 orderId로 진입
    @Transactional(readOnly = true)
    public OutboundDispatchResDto getDispatchByOrderId(UUID orderId, UUID requesterId, UUID clientId){
        OutboundDispatch outboundDispatch = outboundDispatchRepository
                .findByOutboundOrdersIdAndClientId(orderId, clientId)
                .orElseThrow(()-> new IllegalArgumentException("출고 전표를 찾을 수 없거나 접근 권한이 없습니다."));
        return buildDispatchResDto(outboundDispatch, requesterId, clientId);
    }

    // 출고전표 → ResDto 변환 (id 진입·orderId 진입 공통 사용)
    private OutboundDispatchResDto buildDispatchResDto(OutboundDispatch outboundDispatch,
                                                       UUID requesterId, UUID clientId){
        // 출고처(storeId)는 전표가 아닌 출고지시서가 보유 → 출고지시서에서 꺼냄
        OutboundOrders outboundOrder = outboundOrderRepository.findById(outboundDispatch.getOutboundOrdersId())
                .orElseThrow(()-> new IllegalArgumentException("출고지시서를 찾을 수 없습니다."));

        // 인쇄용 SKU/단가는 출고지시서 품목에서 가져옴 (item_id 매칭)
        Map<UUID, OutboundOrderItems> orderItemMap = outboundOrderItemRepository
                .findByOutboundOrdersId(outboundOrder.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(OutboundOrderItems::getId, oi -> oi));

        // 같은 상품이 반복될 수 있으므로 ProductResDto 통째로 캐싱 (name+sku 한 번에)
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        List<OutboundDispatchItems> items = outboundDispatchItemRepository.findByDispatchId(outboundDispatch.getId());

        List<OutboundDispatchItemResDto> itemResDtos = new ArrayList<>();
        for(OutboundDispatchItems item : items){
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            String productName = product != null ? product.getName() : null;
            String sku = product != null ? product.getSku() : null;

            OutboundOrderItems originalItem = orderItemMap.get(item.getOrderItemId());
            java.math.BigDecimal unitPrice = originalItem != null ? originalItem.getUnitPrice() : null;

            OutboundDispatchItemResDto dto = OutboundDispatchItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(sku)
                    .productName(productName)
                    .qty(item.getQty())
                    .unitPrice(unitPrice)
                    .lotNo(item.getLotNo())
                    .build();
            itemResDtos.add(dto);
        }

        // Feign 호출로 이름 정보 채우기
        String warehouseName = fetchWarehouseName(outboundDispatch.getWarehouseId(), clientId);
        String storeName = fetchStoreName(outboundOrder.getStoreId(), clientId);
        String dispatchedByName = fetchUserName(outboundDispatch.getDispatchedBy(), requesterId);

        // 출처 수주서 정보 채우기 — sales_order 일 때만 링크 조회, 아니면 빈 리스트
        String originType = outboundOrder.getOriginType();
        List<OutboundDispatchListItemResDto.OriginRef> originRefs = new ArrayList<>();
        if ("sales_order".equalsIgnoreCase(originType)) {
            List<UUID> soIds = outboundSalesOrderLinksRepository
                    .findByOutboundOrderIdAndCancelledAtIsNull(outboundOrder.getId())
                    .stream()
                    .map(OutboundSalesOrderLinks::getSalesOrderId)
                    .distinct()
                    .toList();
            if (!soIds.isEmpty()) {
                Map<UUID, String> soNoById = erpSalesOrdersRepository.findAllById(soIds).stream()
                        .collect(Collectors.toMap(ErpSalesOrders::getId, ErpSalesOrders::getSalesOrderNumber));
                for (UUID soId : soIds) {
                    String soNo = soNoById.get(soId);
                    if (soNo != null) {
                        originRefs.add(OutboundDispatchListItemResDto.OriginRef.builder()
                                .id(soId)
                                .no(soNo)
                                .build());
                    }
                }
            }
        }

        return OutboundDispatchResDto.builder()
                .id(outboundDispatch.getId())
                .orderNo(outboundOrder.getOrderNo())
                .dispatchNo(outboundDispatch.getDispatchNo())
                .originType(originType)
                .originRefs(originRefs)
                .warehouseName(warehouseName)
                .storeName(storeName)
                .dispatchedBy(outboundDispatch.getDispatchedBy())
                .dispatchedByName(dispatchedByName)
                .dispatchedAt(outboundDispatch.getDispatchedAt())
                .createdAt(outboundDispatch.getCreatedAt())
                .items(itemResDtos)
                .build();
    }

    /**
     * 출고전표 목록 조회 — 페이지/필터 지원.
     *
     * 모든 필터 null 허용:
     *   - dateFrom/dateTo: 출고 일시(dispatchedAt) 범위 [from, to)
     *   - warehouseId, originType ("sales_order"/"manual"), dispatchNoKeyword (전표번호), orderNoKeyword (지시서번호)
     *
     * 응답 각 행에 부모 출고지시서 + 출처 SO 들 (분할로 N개 가능) + 창고/출고처/담당자 이름 포함.
     * N+1 방지를 위해 ID 모은 뒤 batch 조회.
     */
    @Transactional(readOnly = true)
    public Page<OutboundDispatchListItemResDto> getDispatchList(
            UUID clientId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            UUID warehouseId,
            String originType,
            String dispatchNoKeyword,
            String orderNoKeyword,
            UUID requesterId,
            Pageable pageable) {
        return getDispatchList(clientId, dateFrom, dateTo, warehouseId, originType,
                dispatchNoKeyword, orderNoKeyword, null, requesterId, pageable);
    }

    /**
     * 출고전표 목록 — productIds 멀티필터 추가 오버로드.
     * productIds 가 있으면 EXISTS 서브쿼리로 매칭, 없으면 기존 쿼리 그대로.
     */
    @Transactional(readOnly = true)
    public Page<OutboundDispatchListItemResDto> getDispatchList(
            UUID clientId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            UUID warehouseId,
            String originType,
            String dispatchNoKeyword,
            String orderNoKeyword,
            List<UUID> productIds,
            UUID requesterId,
            Pageable pageable) {

        Page<OutboundDispatch> page = (productIds != null && !productIds.isEmpty())
                ? outboundDispatchRepository.findByFiltersAndProductIds(
                        clientId, warehouseId, dateFrom, dateTo, originType,
                        dispatchNoKeyword, orderNoKeyword, productIds, pageable)
                : outboundDispatchRepository.findByFilters(
                        clientId, warehouseId, dateFrom, dateTo, originType,
                        dispatchNoKeyword, orderNoKeyword, pageable);

        if (page.isEmpty()) return page.map(d -> null);

        // ── ID 수집 ──
        Set<UUID> orderIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (OutboundDispatch d : page.getContent()) {
            orderIds.add(d.getOutboundOrdersId());
            if (d.getWarehouseId() != null) warehouseIds.add(d.getWarehouseId());
            if (d.getDispatchedBy() != null) userIds.add(d.getDispatchedBy());
        }

        // ── 부모 출고지시서 일괄 조회 ──
        Map<UUID, OutboundOrders> orderById = new HashMap<>();
        Set<UUID> storeIds = new HashSet<>();
        for (OutboundOrders o : outboundOrderRepository.findAllById(orderIds)) {
            orderById.put(o.getId(), o);
            if (o.getStoreId() != null) storeIds.add(o.getStoreId());
        }

        // ── 활성 SO 링크 일괄 조회 → orderId 별 SO 리스트 ──
        Map<UUID, List<UUID>> salesOrderIdsByOrderId = new HashMap<>();
        Set<UUID> allSoIds = new HashSet<>();
        if (!orderIds.isEmpty()) {
            List<OutboundSalesOrderLinks> links =
                    outboundSalesOrderLinksRepository.findByOutboundOrderIdInAndCancelledAtIsNull(orderIds);
            for (OutboundSalesOrderLinks l : links) {
                salesOrderIdsByOrderId.computeIfAbsent(l.getOutboundOrderId(), k -> new ArrayList<>())
                        .add(l.getSalesOrderId());
                allSoIds.add(l.getSalesOrderId());
            }
        }

        // ── 출처 SO 일괄 조회 (so_no 채움) ──
        Map<UUID, String> soNoById = new HashMap<>();
        if (!allSoIds.isEmpty()) {
            for (ErpSalesOrders so : erpSalesOrdersRepository.findAllById(allSoIds)) {
                soNoById.put(so.getId(), so.getSalesOrderNumber());
            }
        }

        // ── 창고/출고처/담당자 이름 캐싱 (Master/Account 호출, 단건 API 만 있어 loop) ──
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        for (UUID wid : warehouseIds) warehouseNameCache.put(wid, fetchWarehouseName(wid, clientId));

        Map<UUID, String> storeNameCache = new HashMap<>();
        for (UUID sid : storeIds) storeNameCache.put(sid, fetchStoreName(sid, clientId));

        Map<UUID, String> userNameCache = new HashMap<>();
        for (UUID uid : userIds) userNameCache.put(uid, fetchUserName(uid, requesterId));

        // ── DTO 빌드 ──
        return page.map(d -> {
            OutboundOrders o = orderById.get(d.getOutboundOrdersId());
            String orderNo = o != null ? o.getOrderNo() : null;
            String oType = o != null ? o.getOriginType() : null;
            UUID storeId = o != null ? o.getStoreId() : null;

            // 분할 출고로 같은 SO 가 한 OB 안에 여러 라인으로 묶일 수 있으니 dedup
            List<UUID> soIds = salesOrderIdsByOrderId.getOrDefault(d.getOutboundOrdersId(), List.of());
            List<OutboundDispatchListItemResDto.OriginRef> originRefs = soIds.stream()
                    .distinct()
                    .map(sid -> OutboundDispatchListItemResDto.OriginRef.builder()
                            .id(sid)
                            .no(soNoById.get(sid))
                            .build())
                    .toList();

            return OutboundDispatchListItemResDto.builder()
                    .id(d.getId())
                    .dispatchNo(d.getDispatchNo())
                    .dispatchedAt(d.getDispatchedAt())
                    .createdAt(d.getCreatedAt())
                    .outboundOrderId(d.getOutboundOrdersId())
                    .orderNo(orderNo)
                    .originType(oType)
                    .originRefs(originRefs)
                    .warehouseId(d.getWarehouseId())
                    .warehouseName(warehouseNameCache.get(d.getWarehouseId()))
                    .storeId(storeId)
                    .storeName(storeId != null ? storeNameCache.get(storeId) : null)
                    .dispatchedBy(d.getDispatchedBy())
                    .dispatchedByName(userNameCache.get(d.getDispatchedBy()))
                    .build();
        });
    }

    /**
     * 수주서 진행률 단계 가중치 (B안 — 초기값, 운영 데이터로 추후 튜닝).
     *
     *   DRAFT / APPROVED   → 0.30  (출고지시서 생성·승인 — 전산 단계)
     *   IN_PROGRESS        → 0.70  (피킹 단계 — 실작업)
     *   COMPLETED / PARTIAL → 1.00 (출고확정 — 최종)
     *   CANCELLED / null   → 0.00  (제외)
     *
     * 진행률 = sum(link.qty × weight(ob.status)) / totalOrderedQty × 100
     */
    static double progressWeightForOutboundStatus(OutboundOrderStatus status) {
        if (status == null) return 0.0;
        return switch (status) {
            case draft, approved -> 0.30;
            case in_progress -> 0.70;
            case completed, partial -> 1.00;
            case cancelled -> 0.00;
        };
    }
}

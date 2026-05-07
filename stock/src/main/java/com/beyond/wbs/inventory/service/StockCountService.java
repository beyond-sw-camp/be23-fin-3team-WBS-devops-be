package com.beyond.wbs.inventory.service;

import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.LocationPageResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.inventory.domain.*;
import com.beyond.wbs.inventory.dtos.*;
import com.beyond.wbs.inventory.repository.*;
import com.beyond.wbs.websocket.WebSocketPublisher;
import com.beyond.wbs.websocket.WorkEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountOrderRepository countOrderRepository;
    private final StockCountItemRepository countItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final NumberingUtil numberingUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WebSocketPublisher webSocketPublisher;
    private final MasterServiceClient masterServiceClient;

    // WS 알림: 모듈 공통 형식 (clientId 기반 multi-tenant 분리)
    private void notifyStockCount(String type, StockCountOrder order, UUID actorId) {
        UUID clientId = order.getClientId();
        WorkEventMessage msg = WorkEventMessage.builder()
                .module("stock-count")
                .type(type)
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(actorId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId, msg);
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId + "/" + order.getId(), msg);
    }

    // 모바일 — master batch 조회로 productSku/Name + location/rack/zone enrichment
    private List<StockCountItemResDto> enrichItems(List<StockCountItemResDto> items, UUID clientId) {
        if (items == null || items.isEmpty() || clientId == null) return items;

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        for (StockCountItemResDto it : items) {
            if (it.getProductId() != null) productIds.add(it.getProductId());
            if (it.getLocationId() != null) locationIds.add(it.getLocationId());
        }

        Map<UUID, ProductResDto> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            try {
                List<ProductResDto> products = masterServiceClient.getProducts(
                        new ArrayList<>(productIds), clientId.toString());
                for (ProductResDto p : products) {
                    if (p != null && p.getId() != null) productMap.put(p.getId(), p);
                }
            } catch (Exception e) {
                log.warn("[StockCount] product batch 조회 실패: {}", e.getMessage());
            }
        }

        Map<UUID, LocationResDto> locationMap = new HashMap<>();
        if (!locationIds.isEmpty()) {
            try {
                List<LocationResDto> locations = masterServiceClient.getLocations(
                        new ArrayList<>(locationIds), clientId.toString());
                for (LocationResDto l : locations) {
                    if (l != null && l.getId() != null) locationMap.put(l.getId(), l);
                }
            } catch (Exception e) {
                log.warn("[StockCount] location batch 조회 실패: {}", e.getMessage());
            }
        }

        List<StockCountItemResDto> enriched = new ArrayList<>(items.size());
        for (StockCountItemResDto it : items) {
            ProductResDto p = it.getProductId() != null ? productMap.get(it.getProductId()) : null;
            LocationResDto l = it.getLocationId() != null ? locationMap.get(it.getLocationId()) : null;
            enriched.add(StockCountItemResDto.builder()
                    .id(it.getId())
                    .productId(it.getProductId())
                    .locationId(it.getLocationId())
                    .systemQty(it.getSystemQty())
                    .countQty(it.getCountQty())
                    .diffQty(it.getDiffQty())
                    .status(it.getStatus())
                    .countedBy(it.getCountedBy())
                    .countedAt(it.getCountedAt())
                    .note(it.getNote())
                    .productSku(p != null ? p.getSku() : null)
                    .productName(p != null ? p.getName() : null)
                    .locationCode(l != null ? l.getCode() : null)
                    .floorNo(l != null ? l.getFloorNo() : null)
                    .rackId(l != null ? l.getRackId() : null)
                    .rackCode(l != null ? l.getRackCode() : null)
                    .zoneId(l != null ? l.getZoneId() : null)
                    .zoneCode(l != null ? l.getZoneCode() : null)
                    .zoneName(l != null ? l.getZoneName() : null)
                    .build());
        }
        return enriched;
    }

    /**
     * 실사지시서 생성
     * 생성 시점에 시스템 재고 수량 스냅샷 저장
     */
    public UUID create(StockCountCreateReqDto dto, UUID clientId, UUID userId) {

        StockCountOrder order = StockCountOrder.builder()
                .clientId(clientId)
                .warehouseId(dto.getWarehouseId())
                .orderNo(numberingUtil.generateStockCountNo())
                .createdBy(userId)
                .note(dto.getNote())
                .build();

        countOrderRepository.save(order);

        // 품목별 현재 시스템 재고 스냅샷
        for (StockCountCreateReqDto.StockCountItemReqDto itemDto : dto.getItems()) {

            int systemQty = inventoryRepository
                    .findByProductIdAndWarehouseIdAndLocationId(
                            itemDto.getProductId(),
                            dto.getWarehouseId(),
                            itemDto.getLocationId())
                    .map(Inventory::getAvailableQty)
                    .orElse(0);

            StockCountItem item = StockCountItem.builder()
                    .countOrderId(order.getId())
                    .productId(itemDto.getProductId())
                    .locationId(itemDto.getLocationId())
                    .systemQty(systemQty)
                    .build();

            countItemRepository.save(item);
        }

        notifyStockCount("CREATED", order, userId);
        return order.getId();
    }

    /**
     * 실사 시작 (draft → in_progress)
     */
    public void start(UUID orderId, UUID clientId, UUID userId) {
        StockCountOrder order = getOrderWithCheck(orderId, clientId);

        if (order.getStatus() != StockCountStatus.draft) {
            throw new IllegalStateException("초안 상태에서만 실사를 시작할 수 있습니다.");
        }

        order.start(userId);
        countOrderRepository.save(order);

        // 실사지시서 PDF 발행 요청 — 시작 시점 = 발행
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.STOCK_COUNT_ORDER,
                order.getId(),
                order.getOrderNo(),
                clientId,
                userId
        ));

        // 같은 회사 관리자에게 시작 알림 push (목록 + 상세)
        WorkEventMessage startedMsg = WorkEventMessage.builder()
                .module("stock-count")
                .type("STARTED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId, startedMsg);
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId + "/" + order.getId(), startedMsg);
    }

    /**
     * 품목별 실사 수량 입력
     */
    public StockCountItemResDto countItem(UUID orderId, UUID itemId,
                                          StockCountItemInputDto dto,
                                          UUID clientId, UUID userId) {
        StockCountOrder order = getOrderWithCheck(orderId, clientId);

        if (order.getStatus() != StockCountStatus.in_progress) {
            throw new IllegalStateException("실사 중 상태에서만 수량을 입력할 수 있습니다.");
        }

        StockCountItem item = countItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("실사 품목을 찾을 수 없습니다."));

        if (!item.getCountOrderId().equals(orderId)) {
            throw new IllegalArgumentException("해당 실사지시서의 품목이 아닙니다.");
        }

        item.count(dto.getCountQty(), dto.getNote(), userId);
        countItemRepository.save(item);

        // 같은 회사 관리자에게 품목 실사 알림 push (상세만 — 진행률 갱신용)
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId + "/" + orderId,
                WorkEventMessage.builder()
                        .module("stock-count")
                        .type("COUNTING")
                        .clientId(clientId)
                        .orderId(orderId)
                        .orderNo(order.getOrderNo())
                        .userId(userId)
                        .occurredAt(LocalDateTime.now())
                        .build());

        return StockCountItemResDto.from(item);
    }

    /**
     * 실사 완료 (in_progress → completed)
     * 차이 발생 품목 자동 재고 조정
     */
    public void complete(UUID orderId, UUID clientId, UUID userId) {
        StockCountOrder order = getOrderWithCheck(orderId, clientId);

        if (order.getStatus() != StockCountStatus.in_progress) {
            throw new IllegalStateException("실사 중 상태에서만 완료 처리할 수 있습니다.");
        }

        List<StockCountItem> items = countItemRepository.findByCountOrderId(orderId);

        // 미입력 품목 체크
        boolean hasUncounted = items.stream()
                .anyMatch(i -> i.getStatus() == StockCountItemStatus.pending);
        if (hasUncounted) {
            throw new IllegalStateException("아직 실사 수량이 입력되지 않은 품목이 있습니다.");
        }

        // 차이 발생 품목 자동 재고 조정
        for (StockCountItem item : items) {
            if (item.getDiffQty() != null && item.getDiffQty() != 0) {

                StockAdjustReqDto adjustDto = StockAdjustReqDto.builder()
                        .productId(item.getProductId())
                        .warehouseId(order.getWarehouseId())
                        .locationId(item.getLocationId())
                        .diffQty(item.getDiffQty())
                        .note("실사 결과 반영 (지시서: " + order.getOrderNo() + ")")
                        .build();

                inventoryService.adjust(clientId, adjustDto, userId);
                item.markAdjusted();
                countItemRepository.save(item);
            }
        }

        order.complete();
        countOrderRepository.save(order);

        // 같은 회사 관리자에게 마감 알림 push (목록 + 상세)
        WorkEventMessage closeMsg = WorkEventMessage.builder()
                .module("stock-count")
                .type("COMPLETED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId, closeMsg);
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId + "/" + order.getId(), closeMsg);
    }

    /**
     * 실사 취소 (draft 상태만)
     */
    public void cancel(UUID orderId, UUID clientId) {
        StockCountOrder order = getOrderWithCheck(orderId, clientId);

        if (order.getStatus() != StockCountStatus.draft) {
            throw new IllegalStateException("초안 상태에서만 취소할 수 있습니다.");
        }

        order.cancel();
        countOrderRepository.save(order);

        // 같은 회사 관리자에게 취소 알림 push (목록 + 상세)
        WorkEventMessage cancelledMsg = WorkEventMessage.builder()
                .module("stock-count")
                .type("CANCELLED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId, cancelledMsg);
        webSocketPublisher.send("/topic/admin/stock-count/" + clientId + "/" + order.getId(), cancelledMsg);
    }

    /**
     * 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<StockCountResDto> findAll(UUID clientId,
                                          StockCountStatus status,
                                          Pageable pageable) {
        return findAll(clientId, status, null, pageable);
    }

    /**
     * 목록 조회 — productIds 멀티필터 추가 오버로드.
     */
    @Transactional(readOnly = true)
    public Page<StockCountResDto> findAll(UUID clientId,
                                          StockCountStatus status,
                                          List<UUID> productIds,
                                          Pageable pageable) {
        Page<StockCountOrder> orders;
        if (productIds != null && !productIds.isEmpty()) {
            orders = countOrderRepository.findByClientIdAndProductIds(clientId, status, productIds, pageable);
        } else if (status != null) {
            orders = countOrderRepository.findByClientIdAndStatus(clientId, status, pageable);
        } else {
            orders = countOrderRepository.findByClientId(clientId, pageable);
        }
        return orders.map(StockCountResDto::from);
    }

    /**
     * 실사지시서 내 상품 라인 — productIds 로 필터링.
     *
     * 실사지시서 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    @Transactional(readOnly = true)
    public List<StockCountItemResDto> findItemsByProductIds(UUID orderId,
                                                             List<UUID> productIds,
                                                             UUID clientId) {
        getOrderWithCheck(orderId, clientId); // 회사 소속 검증

        List<StockCountItem> items = (productIds == null || productIds.isEmpty())
                ? countItemRepository.findByCountOrderId(orderId)
                : countItemRepository.findByCountOrderIdAndProductIdIn(orderId, productIds);

        List<StockCountItemResDto> dtos = items.stream()
                .map(StockCountItemResDto::from)
                .collect(Collectors.toList());
        return enrichItems(dtos, clientId);
    }

    /**
     * 모바일 작업자용 — 본인 작업 이력 + 진행 중 실사 모두 반환.
     * - in_progress: 누구나 진입 가능 (작업자가 새 실사 시작용)
     * - completed/cancelled: 본인이 countedBy 로 입력한 적 있는 것만 (작업 이력 회상용)
     */
    @Transactional(readOnly = true)
    public List<StockCountResDto> findMyListForMobile(UUID clientId, UUID userId, UUID warehouseId) {
        return countOrderRepository.findMyListForMobile(clientId, userId, warehouseId).stream()
                .map(StockCountResDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 모바일 작업자가 위치 QR 스캔 시 — 해당 위치 품목들.
     */
    @Transactional(readOnly = true)
    public List<StockCountItemResDto> findItemsByLocation(UUID orderId, UUID locationId, UUID clientId) {
        getOrderWithCheck(orderId, clientId); // 회사 소속 검증
        List<StockCountItemResDto> dtos = countItemRepository.findByCountOrderIdAndLocationId(orderId, locationId).stream()
                .map(StockCountItemResDto::from)
                .collect(Collectors.toList());
        return enrichItems(dtos, clientId);
    }

    /**
     * 모바일 작업자가 랙 QR 스캔 시 — 해당 랙(여러 floor 포함) 의 품목들.
     * master 에서 rackId → locationIds 조회 후 그 locations 로 필터링.
     */
    @Transactional(readOnly = true)
    public List<StockCountItemResDto> findItemsByRack(UUID orderId, UUID rackId, UUID clientId) {
        getOrderWithCheck(orderId, clientId);

        List<UUID> locationIds;
        try {
            LocationPageResDto page = masterServiceClient.getLocationsByRackId(rackId, clientId.toString());
            locationIds = (page == null || page.getContent() == null) ? List.of()
                    : page.getContent().stream()
                        .map(LocationResDto::getId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[StockCount] rack 조회 실패 rackId={} - {}", rackId, e.getMessage());
            return List.of();
        }
        if (locationIds.isEmpty()) return List.of();

        List<StockCountItem> items = countItemRepository.findByCountOrderIdAndLocationIdIn(orderId, locationIds);
        List<StockCountItemResDto> dtos = items.stream()
                .map(StockCountItemResDto::from)
                .collect(Collectors.toList());
        return enrichItems(dtos, clientId);
    }

    /**
     * 상세 조회 (품목 포함)
     */
    @Transactional(readOnly = true)
    public StockCountResDto findById(UUID orderId, UUID clientId) {
        StockCountOrder order = getOrderWithCheck(orderId, clientId);
        List<StockCountItem> items = countItemRepository.findByCountOrderId(orderId);

        List<StockCountItemResDto> itemDtos = items.stream()
                .map(StockCountItemResDto::from)
                .collect(Collectors.toList());

        return StockCountResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .createdBy(order.getCreatedBy())
                .approvedBy(order.getApprovedBy())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .approvedAt(order.getApprovedAt())
                .completedAt(order.getCompletedAt())
                .items(itemDtos)
                .build();
    }

    // ── 헬퍼 ──────────────────────────────────────────────────

    private StockCountOrder getOrderWithCheck(UUID orderId, UUID clientId) {
        StockCountOrder order = countOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("실사지시서를 찾을 수 없습니다."));
        if (!order.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        return order;
    }

}
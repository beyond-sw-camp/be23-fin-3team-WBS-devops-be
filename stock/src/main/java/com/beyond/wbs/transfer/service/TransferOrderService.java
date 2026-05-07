package com.beyond.wbs.transfer.service;

import com.beyond.wbs.assignment.WorkAssignmentService;
import com.beyond.wbs.assignment.WorkTaskType;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.transfer.domain.TransferExecution;
import com.beyond.wbs.transfer.domain.TransferOrder;
import com.beyond.wbs.transfer.domain.TransferOrderItem;
import com.beyond.wbs.transfer.domain.TransferOrderItemStatus;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import com.beyond.wbs.transfer.dto.TransferOrderCreateReqDto;
import com.beyond.wbs.transfer.dto.TransferOrderItemReqDto;
import com.beyond.wbs.transfer.dto.TransferOrderItemResDto;
import com.beyond.wbs.transfer.dto.TransferOrderResDto;
import com.beyond.wbs.transfer.dto.TransferPickItemReqDto;
import com.beyond.wbs.transfer.dto.TransferPlaceItemReqDto;
import com.beyond.wbs.transfer.dto.TransferProcessItemReqDto;
import com.beyond.wbs.transfer.dto.TransferTaskListResDto;
import com.beyond.wbs.transfer.dto.TransferTaskResDto;
import com.beyond.wbs.transfer.repository.TransferExecutionRepository;
import com.beyond.wbs.transfer.repository.TransferOrderItemRepository;
import com.beyond.wbs.transfer.repository.TransferOrderRepository;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.websocket.WorkEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.beyond.wbs.websocket.WebSocketPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * 이동지시서 서비스 — 1:1 단순 구조
 */
@Slf4j
@Service
public class TransferOrderService {

    private final TransferOrderRepository transferOrderRepository;
    private final TransferOrderItemRepository transferOrderItemRepository;
    private final TransferExecutionRepository transferExecutionRepository;
    private final TransferLocationValidator locationValidator;
    private final NumberingUtil numberingUtil;
    private final InventoryService inventoryService;
    private final MasterServiceClient masterServiceClient;
    private final WebSocketPublisher webSocketPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransferEventPublisher transferEventPublisher;
    private final WorkAssignmentService workAssignmentService;

    public TransferOrderService(TransferOrderRepository transferOrderRepository,
                                 TransferOrderItemRepository transferOrderItemRepository,
                                 TransferExecutionRepository transferExecutionRepository,
                                 TransferLocationValidator locationValidator,
                                 NumberingUtil numberingUtil,
                                 InventoryService inventoryService,
                                 MasterServiceClient masterServiceClient,
                                 WebSocketPublisher webSocketPublisher,
                                 ApplicationEventPublisher applicationEventPublisher,
                                 TransferEventPublisher transferEventPublisher,
                                 WorkAssignmentService workAssignmentService) {
        this.transferOrderRepository = transferOrderRepository;
        this.transferOrderItemRepository = transferOrderItemRepository;
        this.transferExecutionRepository = transferExecutionRepository;
        this.locationValidator = locationValidator;
        this.numberingUtil = numberingUtil;
        this.inventoryService = inventoryService;
        this.masterServiceClient = masterServiceClient;
        this.webSocketPublisher = webSocketPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transferEventPublisher = transferEventPublisher;
        this.workAssignmentService = workAssignmentService;
    }

    /** 창고 이름 조회 실패 시 null 반환 (전체 응답은 살려둠). */
    private String fetchWarehouseName(UUID warehouseId, UUID clientId) {
        if (warehouseId == null) return null;
        try {
            WarehouseResDto wh = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
            return wh != null ? wh.getName() : null;
        } catch (Exception e) {
            log.warn("Master 창고 조회 실패 warehouseId={}, err={}", warehouseId, e.getMessage());
            return null;
        }
    }

    /** 품목 productId 집합 → Master 상품 정보 일괄 조회 (N+1 방지). */
    private Map<UUID, ProductResDto> fetchProducts(Set<UUID> productIds, UUID clientId) {
        Map<UUID, ProductResDto> out = new HashMap<>();
        for (UUID pid : productIds) {
            if (pid == null) continue;
            try {
                out.put(pid, masterServiceClient.getProduct(pid, clientId.toString()));
            } catch (Exception e) {
                log.warn("Master 상품 조회 실패 productId={}, err={}", pid, e.getMessage());
            }
        }
        return out;
    }

    // =========================================================================
    // 관리자 API
    // =========================================================================

    /**
     * 이동지시서 생성
     */
    @Transactional
    public TransferOrderResDto createTransferOrder(TransferOrderCreateReqDto dto,
                                                    UUID clientId, UUID userId) {
        // 1. 품목 레벨 검증
        for (TransferOrderItemReqDto itemDto : dto.getItems()) {
            if (itemDto.getFromLocationId().equals(itemDto.getToLocationId())) {
                throw new IllegalArgumentException("품목의 출발지와 도착지 로케이션이 동일합니다.");
            }
        }
        locationValidator.validateAll(
                dto.getItems(), dto.getFromWarehouseId(), dto.getToWarehouseId(), clientId);

        // 2. 번호 채번
        String orderNo = numberingUtil.generate("transfer", "TR");

        // 3. 지시서 생성
        LocalDateTime now = LocalDateTime.now();
        TransferOrder order = TransferOrder.builder()
                .clientId(clientId)
                .orderNo(orderNo)
                .fromWarehouseId(dto.getFromWarehouseId())
                .toWarehouseId(dto.getToWarehouseId())
                .status(TransferOrderStatus.draft)
                .expectedDate(dto.getExpectedDate())
                .createdBy(userId)
                .note(dto.getNote())
                .createdAt(now)
                .updatedAt(now)
                .build();
        transferOrderRepository.save(order);

        // 4. 품목 생성
        int totalQty = 0;
        List<TransferOrderItemResDto> itemResList = new ArrayList<>();
        for (TransferOrderItemReqDto itemDto : dto.getItems()) {
            TransferOrderItem item = TransferOrderItem.builder()
                    .transferOrderId(order.getId())
                    .productId(itemDto.getProductId())
                    .fromLocationId(itemDto.getFromLocationId())
                    .toLocationId(itemDto.getToLocationId())
                    .orderedQty(itemDto.getOrderedQty())
                    .processedQty(0)
                    .defectQty(0)
                    .lotNo(itemDto.getLotNo())
                    .status(TransferOrderItemStatus.pending)
                    .build();
            transferOrderItemRepository.save(item);
            totalQty += itemDto.getOrderedQty();
            itemResList.add(TransferOrderItemResDto.fromEntity(item, ""));
        }

        // 같은 회사 관리자에게 생성 알림 push (목록만)
        webSocketPublisher.send("/topic/admin/transfer/" + clientId,
                WorkEventMessage.builder()
                        .module("transfer")
                        .type("CREATED")
                        .clientId(clientId)
                        .orderId(order.getId())
                        .orderNo(order.getOrderNo())
                        .userId(userId)
                        .occurredAt(now)
                        .build());

        return TransferOrderResDto.fromEntity(order, "", "", dto.getItems().size(), totalQty, itemResList);
    }

    /**
     * 이동지시서 단건 조회 (품목 포함)
     */
    @Transactional(readOnly = true)
    public TransferOrderResDto getTransferOrder(UUID orderId, UUID clientId) {
        TransferOrder order = findAndCheckOrder(orderId, clientId);

        List<TransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(orderId);
        Set<UUID> productIds = new HashSet<>();
        for (TransferOrderItem item : items) productIds.add(item.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);

        int totalQty = 0;
        List<TransferOrderItemResDto> itemResList = new ArrayList<>();
        for (TransferOrderItem item : items) {
            totalQty += item.getOrderedQty();
            ProductResDto p = products.get(item.getProductId());
            String productName = p != null ? p.getName() : "";
            itemResList.add(TransferOrderItemResDto.fromEntity(item, productName));
        }

        String fromName = fetchWarehouseName(order.getFromWarehouseId(), clientId);
        String toName = fetchWarehouseName(order.getToWarehouseId(), clientId);
        return TransferOrderResDto.fromEntity(order, fromName, toName, items.size(), totalQty, itemResList);
    }

    /**
     * 이동지시서 품목 목록만 조회
     */
    @Transactional(readOnly = true)
    public List<TransferOrderItemResDto> getTransferOrderItems(UUID orderId, UUID clientId) {
        return getTransferOrderItemsByProductIds(orderId, null, clientId);
    }

    /**
     * 이동지시서 내 상품 라인 — productIds 로 필터링.
     *
     * 이동지시서 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    @Transactional(readOnly = true)
    public List<TransferOrderItemResDto> getTransferOrderItemsByProductIds(UUID orderId,
                                                                            List<UUID> productIds,
                                                                            UUID clientId) {
        findAndCheckOrder(orderId, clientId);

        List<TransferOrderItem> items = (productIds == null || productIds.isEmpty())
                ? transferOrderItemRepository.findByTransferOrderId(orderId)
                : transferOrderItemRepository.findByTransferOrderIdAndProductIdIn(orderId, productIds);

        Set<UUID> ids = new HashSet<>();
        for (TransferOrderItem item : items) ids.add(item.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(ids, clientId);

        List<TransferOrderItemResDto> result = new ArrayList<>();
        for (TransferOrderItem item : items) {
            ProductResDto p = products.get(item.getProductId());
            String productName = p != null ? p.getName() : "";
            result.add(TransferOrderItemResDto.fromEntity(item, productName));
        }
        return result;
    }

    /**
     * 이동지시서 목록 조회 (페이징 + 필터)
     */
    @Transactional(readOnly = true)
    public Page<TransferOrderResDto> getTransferOrders(UUID clientId,
                                                        TransferOrderStatus status,
                                                        UUID fromWarehouseId,
                                                        UUID toWarehouseId,
                                                        Pageable pageable) {
        return getTransferOrders(clientId, status, fromWarehouseId, toWarehouseId, null, pageable);
    }

    /**
     * 이동지시서 목록 조회 + productIds 멀티필터.
     * productIds 가 있으면 EXISTS 서브쿼리로 매칭, 없으면 기존 쿼리 그대로.
     */
    @Transactional(readOnly = true)
    public Page<TransferOrderResDto> getTransferOrders(UUID clientId,
                                                        TransferOrderStatus status,
                                                        UUID fromWarehouseId,
                                                        UUID toWarehouseId,
                                                        List<UUID> productIds,
                                                        Pageable pageable) {
        Page<TransferOrder> orders = (productIds != null && !productIds.isEmpty())
                ? transferOrderRepository.searchTransferOrdersWithProducts(
                        clientId, status, fromWarehouseId, toWarehouseId, productIds, pageable)
                : transferOrderRepository.searchTransferOrders(
                        clientId, status, fromWarehouseId, toWarehouseId, pageable);

        // 페이지 내 창고 ID 캐시 — 같은 창고 중복 조회 방지
        Map<UUID, String> warehouseNameCache = new HashMap<>();

        return orders.map(order -> {
            List<TransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(order.getId());
            int totalQty = items.stream().mapToInt(TransferOrderItem::getOrderedQty).sum();
            String fromName = warehouseNameCache.computeIfAbsent(order.getFromWarehouseId(), id -> fetchWarehouseName(id, clientId));
            String toName = warehouseNameCache.computeIfAbsent(order.getToWarehouseId(), id -> fetchWarehouseName(id, clientId));
            return TransferOrderResDto.fromEntity(order, fromName, toName, items.size(), totalQty, null);
        });
    }

    /**
     * 이동지시서 승인 (draft → approved)
     */
    @Transactional
    public void approveTransferOrder(UUID orderId, UUID clientId, UUID approverId) {
        TransferOrder order = findAndCheckOrder(orderId, clientId);

        if (order.getStatus() != TransferOrderStatus.draft) {
            throw new IllegalStateException("임시저장(draft) 상태의 지시서만 승인할 수 있습니다. 현재: " + order.getStatus());
        }
        UUID assignedTo = workAssignmentService.assign(WorkTaskType.TRANSFER, clientId, approverId);
        order.approve(approverId, assignedTo);

        // 이동지시서 PDF 발행 요청
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.TRANSFER_ORDER,
                order.getId(),
                order.getOrderNo(),
                clientId,
                approverId
        ));

        // 같은 회사 관리자에게 승인 알림 push (목록 + 상세)
        WorkEventMessage approvedMsg = WorkEventMessage.builder()
                .module("transfer")
                .type("APPROVED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(approverId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/transfer/" + clientId, approvedMsg);
        webSocketPublisher.send("/topic/admin/transfer/" + clientId + "/" + order.getId(), approvedMsg);
    }

    /**
     * 이동지시서 취소
     */
    @Transactional
    public void cancelTransferOrder(UUID orderId, UUID clientId) {
        TransferOrder order = findAndCheckOrder(orderId, clientId);
        order.cancel();

        // 같은 회사 관리자에게 취소 알림 push (목록 + 상세)
        WorkEventMessage cancelledMsg = WorkEventMessage.builder()
                .module("transfer")
                .type("CANCELLED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/transfer/" + clientId, cancelledMsg);
        webSocketPublisher.send("/topic/admin/transfer/" + clientId + "/" + order.getId(), cancelledMsg);
    }

    // =========================================================================
    // 모바일 API — 작업자용
    // =========================================================================

    /**
     * 작업 완료 (모든 품목 처리 확인 후 최종 상태 결정)
     */
    @Transactional
    public void completeWork(UUID orderId, UUID clientId) {
        TransferOrder order = findAndCheckOrder(orderId, clientId);

        if (order.getStatus() != TransferOrderStatus.in_progress) {
            throw new IllegalStateException("진행 중(in_progress) 상태의 지시서만 완료할 수 있습니다. 현재: " + order.getStatus());
        }

        List<TransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(orderId);
        long notProcessed = items.stream()
                .filter(i -> i.getProcessedQty() + i.getDefectQty() < i.getOrderedQty())
                .count();
        if (notProcessed > 0) {
            throw new IllegalStateException("아직 처리되지 않은 품목이 있습니다: " + notProcessed + "건");
        }

        boolean hasDefect = items.stream().anyMatch(i -> i.getDefectQty() > 0);
        if (hasDefect) {
            order.partial();
        } else {
            order.complete();
        }

        // 같은 회사 관리자에게 마감 알림 push (목록 + 상세)
        WorkEventMessage closeMsg = WorkEventMessage.builder()
                .module("transfer")
                .type(hasDefect ? "PARTIAL" : "COMPLETED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/transfer/" + clientId, closeMsg);
        webSocketPublisher.send("/topic/admin/transfer/" + clientId + "/" + order.getId(), closeMsg);
    }

    /**
     * 품목 처리 (수량 반영 + 실행 이력 기록)
     * - approved 상태에서 첫 처리 시 자동으로 in_progress 전이
     */
    @Transactional
    public void processItem(UUID itemId, TransferProcessItemReqDto dto,
                             UUID clientId, UUID userId) {
        TransferOrderItem item = transferOrderItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("이동지시서 품목을 찾을 수 없습니다."));

        TransferOrder order = findAndCheckOrder(item.getTransferOrderId(), clientId);

        if (order.getStatus() != TransferOrderStatus.approved
                && order.getStatus() != TransferOrderStatus.in_progress) {
            throw new IllegalStateException("approved 또는 in_progress 상태에서만 품목을 처리할 수 있습니다. 현재: " + order.getStatus());
        }

        if (order.getStatus() == TransferOrderStatus.approved) {
            order.changeStatus(TransferOrderStatus.in_progress);
        }

        int goodQty = dto.getGoodQty();
        int defectQty = dto.getDefectQty();

        // 수량 초과 입력 차단
        int alreadyHandled = item.getProcessedQty() + item.getDefectQty();
        int totalAfter = alreadyHandled + goodQty + defectQty;
        if (totalAfter > item.getOrderedQty()) {
            throw new IllegalArgumentException(
                    "지시 수량(" + item.getOrderedQty() + "개)을 초과할 수 없습니다. " +
                    "이미 처리: " + alreadyHandled + "개, " +
                    "이번 요청: " + (goodQty + defectQty) + "개, " +
                    "초과: " + (totalAfter - item.getOrderedQty()) + "개");
        }

        item.process(goodQty, defectQty);
        saveExecution(order, item, goodQty, defectQty, userId);

        // ── 재고 변동 ──
        // 정상 이동분: 출발지 available ↓ → 도착지 available ↑
        if (goodQty > 0) {
            inventoryService.transferAvailable(
                    clientId, item.getProductId(),
                    order.getFromWarehouseId(), item.getFromLocationId(),
                    order.getToWarehouseId(), item.getToLocationId(),
                    goodQty, order.getId(), userId);
        }
        // 이동 중 파손분: 출발지 available ↓ → 도착지 defect ↑
        if (defectQty > 0) {
            inventoryService.transferToDefect(
                    clientId, item.getProductId(),
                    order.getFromWarehouseId(), item.getFromLocationId(),
                    order.getToWarehouseId(), item.getToLocationId(),
                    defectQty, order.getId(), userId);
        }
    }

    // =========================================================================
    // 모바일 API — 작업자용 (PICK / PLACE 분리)
    // =========================================================================

    /**
     * 모바일 — 픽업 (출발지에서 꺼냄)
     * 출발지 available 차감만, 도착지엔 손대지 않음.
     */
    @Transactional
    public void pickItemMobile(UUID itemId, TransferPickItemReqDto dto,
                                UUID clientId, UUID userId) {
        TransferOrderItem item = transferOrderItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("이동지시서 품목을 찾을 수 없습니다."));

        TransferOrder order = findAndCheckOrder(item.getTransferOrderId(), clientId);

        if (order.getStatus() != TransferOrderStatus.approved
                && order.getStatus() != TransferOrderStatus.in_progress) {
            throw new IllegalStateException(
                    "approved 또는 in_progress 상태에서만 픽업할 수 있습니다. 현재: " + order.getStatus());
        }

        int qty = dto.getQty();
        int alreadyPicked = item.getPickedQty();
        if (alreadyPicked + qty > item.getOrderedQty()) {
            throw new IllegalArgumentException(
                    "지시 수량(" + item.getOrderedQty() + "개) 초과 픽업 불가. " +
                            "이미 픽업: " + alreadyPicked + "개, 이번 요청: " + qty + "개");
        }

        if (order.getStatus() == TransferOrderStatus.approved) {
            order.changeStatus(TransferOrderStatus.in_progress);
        }

        item.pick(qty);

        inventoryService.transferPickFromSource(
                clientId, item.getProductId(),
                order.getFromWarehouseId(), item.getFromLocationId(),
                order.getToWarehouseId(),
                qty, order.getId(), userId);
    }

    /**
     * 모바일 — 적치 (도착지에 놓음)
     * 도착지 available/defect 증가만, 출발지엔 이미 PICK에서 차감 완료.
     */
    @Transactional
    public void placeItemMobile(UUID itemId, TransferPlaceItemReqDto dto,
                                 UUID clientId, UUID userId) {
        TransferOrderItem item = transferOrderItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("이동지시서 품목을 찾을 수 없습니다."));

        TransferOrder order = findAndCheckOrder(item.getTransferOrderId(), clientId);

        if (order.getStatus() != TransferOrderStatus.in_progress) {
            throw new IllegalStateException(
                    "in_progress 상태에서만 적치할 수 있습니다. 현재: " + order.getStatus());
        }

        int goodQty = dto.getGoodQty();
        int defectQty = dto.getDefectQty();
        int placeQty = goodQty + defectQty;

        // 픽업한 만큼만 적치 가능
        int alreadyPlaced = item.getProcessedQty() + item.getDefectQty();
        if (alreadyPlaced + placeQty > item.getPickedQty()) {
            throw new IllegalArgumentException(
                    "픽업 수량(" + item.getPickedQty() + "개)을 초과해 적치할 수 없습니다. " +
                            "이미 적치: " + alreadyPlaced + "개, 이번 요청: " + placeQty + "개");
        }

        item.place(goodQty, defectQty);
        saveExecution(order, item, goodQty, defectQty, userId);

        if (goodQty > 0) {
            inventoryService.transferPlaceAvailable(
                    clientId, item.getProductId(),
                    order.getFromWarehouseId(), item.getFromLocationId(),
                    order.getToWarehouseId(), item.getToLocationId(),
                    goodQty, order.getId(), userId);
        }
        if (defectQty > 0) {
            inventoryService.transferPlaceDefect(
                    clientId, item.getProductId(),
                    order.getFromWarehouseId(), item.getFromLocationId(),
                    order.getToWarehouseId(), item.getToLocationId(),
                    defectQty, order.getId(), userId);
        }

        // 자동 마감 — 모든 품목이 처리 완료되면 지시서 상태 전환
        List<TransferOrderItem> allItems = transferOrderItemRepository.findByTransferOrderId(order.getId());
        boolean allDone = allItems.stream()
                .allMatch(i -> i.getProcessedQty() + i.getDefectQty() >= i.getOrderedQty());
        if (allDone) {
            boolean hasDefect = allItems.stream().anyMatch(i -> i.getDefectQty() > 0);
            if (hasDefect) {
                order.partial();
            } else {
                order.complete();
            }

            // 같은 회사 관리자에게 마감 알림 push (목록 + 상세)
            WorkEventMessage closeMsg = WorkEventMessage.builder()
                    .module("transfer")
                    .type(hasDefect ? "PARTIAL" : "COMPLETED")
                    .clientId(clientId)
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .userId(userId)
                    .occurredAt(LocalDateTime.now())
                    .build();
            webSocketPublisher.send("/topic/admin/transfer/" + clientId, closeMsg);
            webSocketPublisher.send("/topic/admin/transfer/" + clientId + "/" + order.getId(), closeMsg);
        }
    }

    /**
     * 모바일 — 작업 목록 (PICK 단계 + PLACE 단계).
     * 각 단계는 rackCode → locationCode 정렬.
     */
    @Transactional(readOnly = true)
    public TransferTaskListResDto getMobileTasks(UUID orderId, UUID clientId) {
        TransferOrder order = findAndCheckOrder(orderId, clientId);
        List<TransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(orderId);

        // master 배치 조회 — from + to 로케이션 모두
        Set<UUID> locationIds = new HashSet<>();
        for (TransferOrderItem it : items) {
            locationIds.add(it.getFromLocationId());
            locationIds.add(it.getToLocationId());
        }
        Map<UUID, LocationResDto> locMap = new HashMap<>();
        if (!locationIds.isEmpty()) {
            try {
                List<LocationResDto> locs =
                        masterServiceClient.getLocations(new ArrayList<>(locationIds), clientId.toString());
                for (LocationResDto l : locs) {
                    if (l != null && l.getId() != null) locMap.put(l.getId(), l);
                }
            } catch (Exception e) {
                log.warn("Master 로케이션 배치 조회 실패: {}", e.getMessage());
            }
        }

        // 상품명 조회
        Set<UUID> productIds = new HashSet<>();
        for (TransferOrderItem it : items) productIds.add(it.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);

        List<TransferTaskResDto> pickTasks = new ArrayList<>();
        List<TransferTaskResDto> placeTasks = new ArrayList<>();

        for (TransferOrderItem it : items) {
            ProductResDto p = products.get(it.getProductId());
            String productName = p != null ? p.getName() : "";

            int pickRemaining = it.getOrderedQty() - it.getPickedQty();
            if (pickRemaining > 0) {
                pickTasks.add(buildTask(it, productName, it.getFromLocationId(), pickRemaining, locMap));
            }

            int placedTotal = it.getProcessedQty() + it.getDefectQty();
            int placeRemaining = it.getPickedQty() - placedTotal;
            if (placeRemaining > 0) {
                placeTasks.add(buildTask(it, productName, it.getToLocationId(), placeRemaining, locMap));
            }
        }

        //규칙
        Comparator<TransferTaskResDto> byRackThenLocation = Comparator
                .comparing(TransferTaskResDto::getRackCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(TransferTaskResDto::getLocationCode, Comparator.nullsLast(String::compareTo));
        pickTasks.sort(byRackThenLocation);
        placeTasks.sort(byRackThenLocation);

        String fromName = fetchWarehouseName(order.getFromWarehouseId(), clientId);
        String toName = fetchWarehouseName(order.getToWarehouseId(), clientId);

        return TransferTaskListResDto.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .fromWarehouseName(fromName)
                .toWarehouseName(toName)
                .status(order.getStatus().name())
                .pickTasks(pickTasks)
                .placeTasks(placeTasks)
                .build();
    }

    private TransferTaskResDto buildTask(TransferOrderItem it, String productName,
                                          UUID locationId, int qty,
                                          Map<UUID, LocationResDto> locMap) {
        LocationResDto loc = locMap.get(locationId);
        return TransferTaskResDto.builder()
                .itemId(it.getId())
                .locationId(locationId)
                .rackId(loc != null ? loc.getRackId() : null)
                .rackCode(loc != null ? loc.getRackCode() : null)
                .locationCode(loc != null ? loc.getCode() : null)
                .productId(it.getProductId())
                .productName(productName)
                .qty(qty)
                .status(it.getStatus().name())
                .build();
    }

    // =========================================================================
    // 접근 제한
    // =========================================================================

    private TransferOrder findAndCheckOrder(UUID orderId, UUID clientId) {
        TransferOrder order = transferOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("이동지시서를 찾을 수 없습니다."));
        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 접근할 수 있습니다.");
        }
        return order;
    }
    //감사로그 저장
    private void saveExecution(TransferOrder order, TransferOrderItem item,
                                int goodQty, int defectQty, UUID userId) {
        TransferExecution execution = TransferExecution.builder()
                .transferOrderId(order.getId())
                .orderItemId(item.getId())
                .productId(item.getProductId())
                .fromLocationId(item.getFromLocationId())
                .toLocationId(item.getToLocationId())
                .qty(goodQty)
                .defectQty(defectQty)
                .lotNo(item.getLotNo())
                .executedBy(userId)
                .build();
        transferExecutionRepository.save(execution);
    }
}

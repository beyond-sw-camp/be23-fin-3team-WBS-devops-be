package com.beyond.wbs.etcinout.service;

import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.SupplierResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.etcinout.assignment.EtcInoutAssignmentStrategy;
import com.beyond.wbs.etcinout.domain.*;
import com.beyond.wbs.etcinout.dto.CancellationCandidateResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutCompletePickReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutCompleteWorkReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemProcessReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutCreateReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemDto;
import com.beyond.wbs.etcinout.dto.EtcInoutDetailResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutListResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutUpdateReqDto;
import com.beyond.wbs.etcinout.dto.InboundRequestEmailHistoryResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestPreviewResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendReqDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendResDto;
import com.beyond.wbs.etcinout.dto.StockShortageItemDto;
import com.beyond.wbs.etcinout.exception.EtcInoutStockShortageException;
import com.beyond.wbs.etcinout.mail.EtcInoutMailService;
import com.beyond.wbs.etcinout.repository.EtcInoutCancellationLinkRepository;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderItemRepository;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderRepository;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.outbounds.service.OutboundService;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.websocket.WebSocketPublisher;
import com.beyond.wbs.websocket.WorkEventMessage;
import jakarta.persistence.criteria.Predicate;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EtcInoutService {

    private final EtcInoutOrderRepository orderRepository;
    private final EtcInoutOrderItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final MasterServiceClient masterServiceClient;
    private final NumberingUtil numberingUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EtcInoutAssignmentStrategy etcInoutAssignmentStrategy;
    private final WebSocketPublisher webSocketPublisher;
    private final InventoryRepository inventoryRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundService outboundService;
    private final EtcInoutCancellationLinkRepository cancellationLinkRepository;
    private final AccountServiceClient accountServiceClient;
    private final EtcInoutMailService etcInoutMailService;

    // ============================================================
    // Feign 조회 헬퍼 (실패 시 null — 이름 없어도 서비스는 동작)
    // ============================================================

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

    private String fetchSupplierName(UUID supplierId, UUID clientId) {
        if (supplierId == null) return null;
        try {
            SupplierResDto s = masterServiceClient.getSupplier(supplierId, clientId.toString());
            return s != null ? s.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] supplier 조회 실패: {} - {}", supplierId, e.getMessage());
            return null;
        }
    }

    // WS 알림: 모듈 공통 형식 (admin 목록/상세 + 배정 작업자 모바일)
    private void notifyEtcInout(String type, EtcInoutOrder order, UUID actorId, boolean notifyWorker) {
        UUID clientId = order.getClientId();
        WorkEventMessage msg = WorkEventMessage.builder()
                .module("etc-inout")
                .type(type)
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(actorId)
                .occurredAt(LocalDateTime.now())
                .build();

        webSocketPublisher.send("/topic/admin/etc-inout/" + clientId, msg);
        webSocketPublisher.send("/topic/admin/etc-inout/" + clientId + "/" + order.getId(), msg);

        if (notifyWorker && order.getAssignedTo() != null) {
            webSocketPublisher.send("/topic/worker/" + order.getAssignedTo() + "/etc-inout", msg);
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

    // 작업자 이름 조회 — 호출자(requesterId)의 권한으로 account-service 조회
    private String fetchUserName(UUID userId, UUID requesterId) {
        if (userId == null || requesterId == null) return null;
        try {
            UserResDto u = accountServiceClient.getUser(userId, requesterId.toString());
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] user 조회 실패: {} - {}", userId, e.getMessage());
            return null;
        }
    }
    // 기타입출고 기록 + 품목 생성
    public UUID create(EtcInoutCreateReqDto dto, UUID userId, UUID clientId) {

        // 폐기 사유는 폐기창고에만 처리 가능
        if (dto.getIoType() == IoType.dispose_in || dto.getIoType() == IoType.dispose_out) {
            WarehouseResDto wh = fetchWarehouseSafe(dto.getWarehouseId(), clientId);
            String type = wh != null ? wh.getWarehouseType() : null;
            if (!"DISPOSAL".equals(type)) {
                throw new IllegalArgumentException("폐기 사유는 폐기창고에만 처리 가능합니다.");
            }
        }

        // 출고처(Store) 필수 — sample_out / dispose_out / etc_out
        if (dto.getIoType() == IoType.sample_out
                || dto.getIoType() == IoType.dispose_out
                || dto.getIoType() == IoType.etc_out) {
            if (dto.getStoreId() == null) {
                throw new IllegalArgumentException("출고처(storeId)는 필수입니다.");
            }
        }

        // 재고 조정 출고는 사유 비고(note) 필수
        if (dto.getIoType() == IoType.adjust_out) {
            if (dto.getNote() == null || dto.getNote().isBlank()) {
                throw new IllegalArgumentException("재고 조정 출고는 사유(비고) 입력이 필수입니다.");
            }
        }

        // 관리번호 자동 생성 (DB 시퀀스 + 비관적 락으로 중복 방지)
        String orderNo = numberingUtil.generateEtcInoutNo();

        // 기타입출고 기록 생성 (작업자 배정은 approve 시점)
        LocalDateTime now = LocalDateTime.now();
        EtcInoutOrder order = EtcInoutOrder.builder()
                .createdBy(userId)
                .clientId(clientId)
                .warehouseId(dto.getWarehouseId())
                .orderNo(orderNo)
                .ioType(dto.getIoType())
                .note(dto.getNote())
                .supplierId(dto.getSupplierId())
                .storeId(dto.getStoreId())
                .refId(dto.getRefId())
                .refType(dto.getRefType())
                .createdAt(now)
                .updatedAt(now)
                .build();

        EtcInoutOrder savedOrder = orderRepository.save(order);

        // 품목 저장
        if (dto.getItems() != null) {
            for (EtcInoutItemDto itemDto : dto.getItems()) {
                EtcInoutOrderItem item = EtcInoutOrderItem.builder()
                        .etcOrderId(savedOrder.getId())
                        .productId(itemDto.getProductId())
                        .locationId(itemDto.getLocationId())
                        .qty(itemDto.getQty())
                        .lotNo(itemDto.getLotNo())
                        .condition(itemDto.getCondition())
                        .note(itemDto.getNote())
                        .build();

                itemRepository.save(item);
            }
        }

        // 출고는 초안 작성 시점부터 누적 가용재고 검증 — 부족이면 트랜잭션 롤백
        if (savedOrder.getDirection() == Direction.out) {
            List<StockShortageItemDto> shortages = checkOutboundStock(savedOrder);
            if (!shortages.isEmpty()) {
                throw new EtcInoutStockShortageException(shortages);
            }
        }

        // 입고요청 메일 본문이 cancellation_link 를 따라 그려지므로,
        // 프론트가 cancel 한 outboundId 들을 함께 받았으면 link 자동 저장.
        if (dto.getCancelledOutboundIds() != null && !dto.getCancelledOutboundIds().isEmpty()) {
            for (UUID outboundId : dto.getCancelledOutboundIds()) {
                cancellationLinkRepository.save(EtcInoutCancellationLink.builder()
                        .etcOrderId(savedOrder.getId())
                        .cancelledOutboundId(outboundId)
                        .cancelledBy(userId)
                        .cancelledAt(now)
                        .build());
            }
        }

        // 기타입출고지시서 PDF 발행 요청
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.ETC_INOUT_ORDER,
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                clientId,
                userId
        ));

        // WS: 운영자 생성 알림 → admin
        notifyEtcInout("CREATED", savedOrder, userId, false);

        return savedOrder.getId();
    }

    // 기타입출고 목록 조회 (회사별 + 필터: 방향, 상태, 유형)
    @Transactional(readOnly = true)
    public Page<EtcInoutListResDto> getList(UUID clientId, UUID requesterId,
                                             Direction direction,
                                             EtcInoutStatus status, IoType ioType,
                                             Pageable pageable) {

        Page<EtcInoutOrder> orders = orderRepository.findAll(
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();

                    // 자기 회사 기록만 조회
                    predicates.add(cb.equal(root.get("clientId"), clientId));

                    if (direction != null) {
                        predicates.add(cb.equal(root.get("direction"), direction));
                    }
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    if (ioType != null) {
                        predicates.add(cb.equal(root.get("ioType"), ioType));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                }, pageable);

        // 같은 창고/입고처/출고처/작업자가 반복되므로 Feign 결과 캐싱 (N+1 방지)
        Map<UUID, String> warehouseCache = new HashMap<>();
        Map<UUID, String> supplierCache = new HashMap<>();
        Map<UUID, String> storeCache = new HashMap<>();
        Map<UUID, String> userCache = new HashMap<>();

        return orders.map(order -> {
            String warehouseName = warehouseCache.computeIfAbsent(
                    order.getWarehouseId(), id -> fetchWarehouseName(id, clientId));
            String supplierName = order.getSupplierId() != null
                    ? supplierCache.computeIfAbsent(order.getSupplierId(), id -> fetchSupplierName(id, clientId))
                    : null;
            String storeName = order.getStoreId() != null
                    ? storeCache.computeIfAbsent(order.getStoreId(), id -> fetchStoreName(id, clientId))
                    : null;
            String assignedToName = order.getAssignedTo() != null
                    ? userCache.computeIfAbsent(order.getAssignedTo(), uid -> fetchUserName(uid, requesterId))
                    : null;
            return EtcInoutListResDto.fromEntity(order, warehouseName, supplierName, storeName, assignedToName);
        });
    }

    // 기타입출고 상세 조회 (기록만, 회사 확인)
    @Transactional(readOnly = true)
    public EtcInoutDetailResDto getDetail(UUID clientId, UUID requesterId, UUID id) {

        // 기록 조회
        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        // 자기 회사 기록인지 확인
        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        String warehouseName = fetchWarehouseName(order.getWarehouseId(), clientId);
        String supplierName = fetchSupplierName(order.getSupplierId(), clientId);
        String storeName = fetchStoreName(order.getStoreId(), clientId);
        String assignedToName = fetchUserName(order.getAssignedTo(), requesterId);

        return EtcInoutDetailResDto.fromEntity(order, warehouseName, supplierName, storeName, assignedToName);
    }

    // 기타입출고 수정 (draft 상태만 가능)
    public EtcInoutDetailResDto update(UUID clientId, UUID id, EtcInoutUpdateReqDto dto) {

        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("임시저장(draft) 상태에서만 수정할 수 있습니다.");
        }

        order.update(
                dto.getIoType(),
                dto.getNote(),
                dto.getWarehouseId(),
                dto.getSupplierId(),
                dto.getStoreId(),
                dto.getRefId(),
                dto.getRefType()
        );

        String warehouseName = fetchWarehouseName(order.getWarehouseId(), clientId);
        String supplierName = fetchSupplierName(order.getSupplierId(), clientId);
        String storeName = fetchStoreName(order.getStoreId(), clientId);
        // update 응답엔 작업자가 아직 없으므로 (draft만 수정 가능) null
        return EtcInoutDetailResDto.fromEntity(order, warehouseName, supplierName, storeName, null);
    }

    // ============================================================
    // 모바일 흐름 (1:1 — 한 작업자가 하나의 기록을 검수→적치까지 처리)
    // ============================================================

    // 모바일: 본인에게 배정된 기타입출고 기록 목록
    @Transactional(readOnly = true)
    public Page<EtcInoutListResDto> getMyList(UUID clientId, UUID userId,
                                              List<EtcInoutStatus> statuses,
                                              Pageable pageable) {

        Page<EtcInoutOrder> orders = (statuses == null || statuses.isEmpty())
                ? orderRepository.findByClientIdAndAssignedToOrderByCreatedAtDesc(
                        clientId, userId, pageable)
                : orderRepository.findByClientIdAndAssignedToAndStatusInOrderByCreatedAtDesc(
                        clientId, userId, statuses, pageable);

        Map<UUID, String> warehouseCache = new HashMap<>();
        Map<UUID, String> supplierCache = new HashMap<>();
        Map<UUID, String> storeCache = new HashMap<>();

        // 모바일은 본인 작업이라 배정자=본인. requesterId 도 본인.
        String myName = fetchUserName(userId, userId);

        return orders.map(order -> {
            String warehouseName = warehouseCache.computeIfAbsent(
                    order.getWarehouseId(), id -> fetchWarehouseName(id, clientId));
            String supplierName = order.getSupplierId() != null
                    ? supplierCache.computeIfAbsent(order.getSupplierId(),
                            id -> fetchSupplierName(id, clientId))
                    : null;
            String storeName = order.getStoreId() != null
                    ? storeCache.computeIfAbsent(order.getStoreId(),
                            id -> fetchStoreName(id, clientId))
                    : null;
            return EtcInoutListResDto.fromEntity(order, warehouseName, supplierName, storeName, myName);
        });
    }

    // 웹 운영자: 승인 (draft → approved) + 작업자 자동 배정
    // 출고는 가용재고/불량재고 체크 → 부족 시 EtcInoutStockShortageException 발생
    public void approve(UUID clientId, UUID id, UUID userId) {

        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("작성중(draft) 상태에서만 승인할 수 있습니다.");
        }

        // 출고는 재고 부족 검증
        if (order.getDirection() == Direction.out) {
            List<StockShortageItemDto> shortages = checkOutboundStock(order);
            if (!shortages.isEmpty()) {
                throw new EtcInoutStockShortageException(shortages);
            }
        }

        UUID assignedWorker = etcInoutAssignmentStrategy.assign(clientId, userId);
        order.approve(userId, assignedWorker);

        notifyEtcInout("APPROVED", order, userId, true);
    }

    // 외부(EtcInoutItemService)에서도 호출 가능한 검증 진입점.
    // 품목 추가/수정 후 호출하여 누적 가용재고 초과를 막는다.
    public void validateOutboundStock(EtcInoutOrder order) {
        if (order.getDirection() != Direction.out) return;
        List<StockShortageItemDto> shortages = checkOutboundStock(order);
        if (!shortages.isEmpty()) {
            throw new EtcInoutStockShortageException(shortages);
        }
    }

    // 출고 누적 재고 부족 검사 — 창고 단위 합산.
    // 한 (productId + warehouseId) 안에서:
    //   유효가용 = inventory 합계 availableQty (이미 approved 출고 reserve는 제외된 값)
    //              − draft 출고지시서 수량 합계
    //              − 다른 approved 기타출고 수량 합계 (자기 제외)
    //   요청 = 이 기타출고의 같은 product+warehouse 에 대한 모든 item qty 합
    // 부족분 발생 시 product+warehouse 단위로 1줄 shortage 반환.
    private List<StockShortageItemDto> checkOutboundStock(EtcInoutOrder order) {
        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(order.getId());
        UUID warehouseId = order.getWarehouseId();
        UUID clientId = order.getClientId();
        boolean isDispose = order.getIoType() == IoType.dispose_out;

        // 같은 productId 의 요청 수량 합산
        Map<UUID, Integer> requestedByProduct = new HashMap<>();
        for (EtcInoutOrderItem item : items) {
            requestedByProduct.merge(item.getProductId(), item.getQty(), Integer::sum);
        }

        List<StockShortageItemDto> shortages = new ArrayList<>();

        for (Map.Entry<UUID, Integer> e : requestedByProduct.entrySet()) {
            UUID productId = e.getKey();
            int requested = e.getValue();

            // 1. 이 (product, warehouse) 의 가용/불량 재고 합계
            int rawAvailable = isDispose
                    ? inventoryRepository.sumDefectQtyByProductAndWarehouse(productId, warehouseId)
                    : inventoryRepository.sumAvailableQtyByProductAndWarehouse(productId, warehouseId);

            // 2. 누적 demand — 출고는 draft 출고지시서 + 다른 approved 기타출고
            //    폐기 출고는 별도 demand 풀이 없어 0 처리
            int draftOutbound = isDispose
                    ? 0
                    : outboundOrderRepository.sumDraftOrderedByProductAndWarehouse(
                            clientId, productId, warehouseId);
            int otherApprovedEtcOut = itemRepository
                    .sumApprovedOutboundByProductAndWarehouse(
                            clientId, productId, warehouseId, order.getId());

            int effectiveAvailable = rawAvailable - draftOutbound - otherApprovedEtcOut;
            if (effectiveAvailable < 0) effectiveAvailable = 0;

            if (effectiveAvailable < requested) {
                shortages.add(StockShortageItemDto.builder()
                        .productId(productId)
                        .warehouseId(warehouseId)
                        .requested(requested)
                        .available(effectiveAvailable)
                        .shortage(requested - effectiveAvailable)
                        .build());
            }
        }
        return shortages;
    }

    // 가용재고 부족 시 — 취소 후보 출고지시서 조회
    @Transactional(readOnly = true)
    public List<CancellationCandidateResDto> getCancellationCandidates(UUID clientId, UUID etcOrderId) {

        EtcInoutOrder order = orderRepository.findById(etcOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        if (order.getDirection() != Direction.out) {
            throw new IllegalStateException("출고 기록만 후보 조회 가능합니다.");
        }

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(etcOrderId);
        Map<UUID, CancellationCandidateResDto> dedup = new HashMap<>();
        Map<UUID, String> storeCache = new HashMap<>();

        for (EtcInoutOrderItem item : items) {
            // approved 출고지시서 (reserve 트랜잭션 기반)
            List<Object[]> approvedRows = outboundOrderRepository.findCancellationCandidates(
                    clientId, item.getProductId(), order.getWarehouseId(), item.getLocationId());
            collectCandidates(approvedRows, "approved", dedup, storeCache, clientId);

            // draft 출고지시서 (위치 미지정이라 창고 단위로)
            List<Object[]> draftRows = outboundOrderRepository.findDraftCancellationCandidates(
                    clientId, item.getProductId(), order.getWarehouseId());
            collectCandidates(draftRows, "draft", dedup, storeCache, clientId);
        }
        return new ArrayList<>(dedup.values());
    }

    private void collectCandidates(List<Object[]> rows, String status,
                                   Map<UUID, CancellationCandidateResDto> dedup,
                                   Map<UUID, String> storeCache, UUID clientId) {
        for (Object[] r : rows) {
            UUID outboundId = (UUID) r[0];
            if (dedup.containsKey(outboundId)) continue;

            UUID storeId = (UUID) r[3];
            String storeName = storeId == null ? null
                    : storeCache.computeIfAbsent(storeId, sid -> fetchStoreName(sid, clientId));

            dedup.put(outboundId, CancellationCandidateResDto.builder()
                    .outboundOrderId(outboundId)
                    .orderNo((String) r[1])
                    .scheduledDate((java.time.LocalDate) r[2])
                    .storeId(storeId)
                    .storeName(storeName)
                    .reservedQty(((Number) r[4]).intValue())
                    .status(status)
                    .build());
        }
    }

    // 운영자가 선택한 출고지시서들을 취소하고 기타출고와 링크 기록
    public void cancelOutboundsForEtc(UUID clientId, UUID etcOrderId,
                                      List<UUID> outboundIds, UUID userId) {

        EtcInoutOrder order = orderRepository.findById(etcOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("작성중(draft) 상태에서만 취소를 트리거할 수 있습니다.");
        }
        if (outboundIds == null || outboundIds.isEmpty()) {
            throw new IllegalArgumentException("취소할 출고지시서를 선택하세요.");
        }

        for (UUID outboundId : outboundIds) {
            // 기존 출고 cancel 흐름 그대로 — Kafka로 unreserve 이벤트 발행됨
            outboundService.cancelOutboundOrder(outboundId, userId, clientId);

            cancellationLinkRepository.save(EtcInoutCancellationLink.builder()
                    .etcOrderId(etcOrderId)
                    .cancelledOutboundId(outboundId)
                    .cancelledBy(userId)
                    .cancelledAt(LocalDateTime.now())
                    .build());
        }
    }

    // OMS 입고 요청 메일 미리보기 — 폼 초기값
    @Transactional(readOnly = true)
    public InboundRequestPreviewResDto previewInboundRequestMail(UUID clientId,
                                                                  UUID etcOrderId,
                                                                  UUID userId) {
        EtcInoutOrder order = orderRepository.findById(etcOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(etcOrderId);

        String requesterName = null;
        String requesterEmail = null;
        try {
            com.beyond.wbs.common.client.dto.UserResDto u =
                    accountServiceClient.getUser(userId, userId.toString());
            if (u != null) {
                requesterName = u.getName();
                requesterEmail = u.getEmail();
            }
        } catch (Exception e) {
            log.warn("[OMS] 운영자 정보 조회 실패: userId={} - {}", userId, e.getMessage());
        }

        return etcInoutMailService.buildPreview(order, items, requesterName, requesterEmail);
    }

    // OMS 입고 요청 메일 SMTP 발송 + 이력 저장
    public InboundRequestSendResDto sendInboundRequestMail(UUID clientId, UUID etcOrderId,
                                                            UUID userId,
                                                            InboundRequestSendReqDto dto) {
        EtcInoutOrder order = orderRepository.findById(etcOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(etcOrderId);

        String requesterName = null;
        String requesterEmail = null;
        try {
            com.beyond.wbs.common.client.dto.UserResDto u =
                    accountServiceClient.getUser(userId, userId.toString());
            if (u != null) {
                requesterName = u.getName();
                requesterEmail = u.getEmail();
            }
        } catch (Exception e) {
            log.warn("[OMS] 운영자 정보 조회 실패: userId={} - {}", userId, e.getMessage());
        }

        log.info("[OMS] 입고 요청 메일 발송 시도: etcOrderId={}, orderNo={}, requestedBy={}",
                etcOrderId, order.getOrderNo(), userId);

        return etcInoutMailService.sendInboundRequest(
                order, items, dto, userId, requesterName, requesterEmail);
    }

    // 메일 발송 이력 조회
    @Transactional(readOnly = true)
    public List<InboundRequestEmailHistoryResDto> getInboundRequestEmailHistory(
            UUID clientId, UUID etcOrderId) {
        EtcInoutOrder order = orderRepository.findById(etcOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        return etcInoutMailService.getHistory(etcOrderId);
    }

    // 모바일 작업자: 출고 픽업 완료 (approved → completed)
    // pickedQty < qty 면 해당 품목만 shortage 표기, 기록 자체는 completed
    public void completePick(UUID clientId, UUID id, UUID userId,
                              EtcInoutCompletePickReqDto dto) {

        EtcInoutOrder order = loadAssigned(clientId, id, userId);

        if (order.getStatus() != EtcInoutStatus.approved) {
            throw new IllegalStateException("승인(approved) 상태에서만 출고 완료할 수 있습니다.");
        }
        if (order.getDirection() != Direction.out) {
            throw new IllegalStateException("출고 기록만 픽업 완료가 가능합니다.");
        }

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(id);
        Map<UUID, EtcInoutOrderItem> itemMap = new HashMap<>();
        for (EtcInoutOrderItem it : items) {
            itemMap.put(it.getId(), it);
        }

        for (EtcInoutCompletePickReqDto.Item req : dto.getItems()) {
            EtcInoutOrderItem item = itemMap.get(req.getItemId());
            if (item == null) {
                throw new IllegalArgumentException("품목이 기록에 속하지 않습니다: " + req.getItemId());
            }

            int picked = req.getPickedQty() != null ? req.getPickedQty() : 0;
            item.recordPickResult(picked, userId);

            // 실제 픽업한 수량만 재고 차감
            if (picked > 0) {
                applyOutboundInventory(clientId, order, item, picked, userId);
            }
        }

        order.complete(userId);
        notifyEtcInout("COMPLETED", order, userId, false);
    }

    // 출고 IoType 별 재고 차감 (작업자가 입력한 pickedQty 만큼만)
    private void applyOutboundInventory(UUID clientId, EtcInoutOrder order,
                                        EtcInoutOrderItem item, int pickedQty, UUID actorId) {
        switch (order.getIoType()) {
            case dispose_out:
                inventoryService.disposeByEtcInout(
                        clientId, item.getProductId(), order.getWarehouseId(),
                        item.getLocationId(), pickedQty, order.getId(), actorId);
                break;
            case sample_out:
            case adjust_out:
            case etc_out:
                inventoryService.reduceAvailableByEtcInout(
                        clientId, item.getProductId(), order.getWarehouseId(),
                        item.getLocationId(), pickedQty, order.getId(), actorId);
                break;
            default:
                throw new IllegalStateException("출고가 아닌 IoType: " + order.getIoType());
        }
    }

    // 모바일 작업자: 검수 + 적치 한 번에 완료 (approved → completed)
    public void completeWork(UUID clientId, UUID id, UUID userId,
                              EtcInoutCompleteWorkReqDto dto) {

        EtcInoutOrder order = loadAssigned(clientId, id, userId);

        if (order.getStatus() != EtcInoutStatus.approved) {
            throw new IllegalStateException("승인(approved) 상태에서만 완료 처리할 수 있습니다.");
        }

        // 품목 매핑
        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(id);
        Map<UUID, EtcInoutOrderItem> itemMap = new HashMap<>();
        for (EtcInoutOrderItem it : items) {
            itemMap.put(it.getId(), it);
        }

        // 요청 검증 + 결과 기록
        for (EtcInoutCompleteWorkReqDto.Item req : dto.getItems()) {
            EtcInoutOrderItem item = itemMap.get(req.getItemId());
            if (item == null) {
                throw new IllegalArgumentException("품목이 기록에 속하지 않습니다: " + req.getItemId());
            }
            if (req.getDefectQty() != null && req.getDefectQty() > 0
                    && req.getDefectLocationId() == null) {
                throw new IllegalArgumentException("불량 수량이 있을 경우 불량 적치 위치(defectLocationId)는 필수입니다.");
            }

            int processed = req.getProcessedQty() != null ? req.getProcessedQty() : 0;
            int defect = req.getDefectQty() != null ? req.getDefectQty() : 0;

            item.recordWorkResult(
                    processed,
                    defect,
                    req.getLocationId(),
                    req.getDefectReason(),
                    userId
            );

            // 재고 반영 (정상/불량 분기)
            applyInventory(clientId, order, item, processed, defect, req.getDefectLocationId(), userId);
        }

        order.complete(userId);
        notifyEtcInout("COMPLETED", order, userId, false);
    }

    /**
     * 모바일 — 품목 1건 처리 완료 (입고/출고 통합).
     *
     * <p>입고: 정상/불량/위치 입력 → 재고 반영 → item.status=completed
     * <p>출고: 픽업 수량 입력 → 재고 차감 → item.status=completed/shortage
     * <p>모든 품목 처리되면 order.status=completed 자동 전환.
     */
    public void processItem(UUID clientId, UUID orderId, UUID itemId, UUID userId,
                             EtcInoutItemProcessReqDto dto) {

        EtcInoutOrder order = loadAssigned(clientId, orderId, userId);

        if (order.getStatus() != EtcInoutStatus.approved) {
            throw new IllegalStateException("승인(approved) 상태에서만 품목 처리가 가능합니다.");
        }

        EtcInoutOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("품목을 찾을 수 없습니다."));
        if (!item.getEtcOrderId().equals(orderId)) {
            throw new IllegalArgumentException("품목이 기록에 속하지 않습니다.");
        }
        if (item.getStatus() == EtcInoutItemStatus.completed
                || item.getStatus() == EtcInoutItemStatus.shortage) {
            throw new IllegalStateException("이미 처리된 품목입니다.");
        }

        if (order.getDirection() == Direction.in) {
            int processed = dto.getProcessedQty() != null ? dto.getProcessedQty() : 0;
            int defect = dto.getDefectQty() != null ? dto.getDefectQty() : 0;
            if (defect > 0 && dto.getDefectLocationId() == null) {
                throw new IllegalArgumentException("불량 수량이 있을 경우 불량 적치 위치(defectLocationId)는 필수입니다.");
            }
            item.recordWorkResult(processed, defect, dto.getLocationId(), dto.getDefectReason(), userId);
            applyInventory(clientId, order, item, processed, defect, dto.getDefectLocationId(), userId);
        } else {
            int picked = dto.getPickedQty() != null ? dto.getPickedQty() : 0;
            item.recordPickResult(picked, userId);
            if (picked > 0) {
                applyOutboundInventory(clientId, order, item, picked, userId);
            }
        }

        notifyEtcInout("PROCESSED", order, userId, false);

        // 모든 품목 처리 완료 시 order 자동 complete
        List<EtcInoutOrderItem> all = itemRepository.findByEtcOrderId(orderId);
        boolean allDone = all.stream().allMatch(it ->
                it.getStatus() == EtcInoutItemStatus.completed
                || it.getStatus() == EtcInoutItemStatus.shortage);
        if (allDone) {
            order.complete(userId);
            notifyEtcInout("COMPLETED", order, userId, false);
        }
    }

    // 본인에게 배정된 기록만 로드 (회사 일치 + assignedTo 일치)
    private EtcInoutOrder loadAssigned(UUID clientId, UUID id, UUID userId) {
        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        if (order.getAssignedTo() == null || !order.getAssignedTo().equals(userId)) {
            throw new IllegalArgumentException("본인에게 배정된 기록이 아닙니다.");
        }
        return order;
    }

    // 웹 운영자 직접 완료 — 모바일 우회 경로 (긴급/오프라인 대비)
    // draft 또는 approved 에서 즉시 completed 로. 전량 정상 처리(불량 분기 없음).
    public void complete(UUID clientId, UUID id, UUID userId) {

        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (order.getStatus() != EtcInoutStatus.draft
                && order.getStatus() != EtcInoutStatus.approved) {
            throw new IllegalStateException("작성중(draft) 또는 승인(approved) 상태에서만 완료 처리할 수 있습니다.");
        }

        // 출고는 가용재고/불량재고 체크 — 부족 시 후보 조회 흐름으로
        if (order.getDirection() == Direction.out) {
            List<StockShortageItemDto> shortages = checkOutboundStock(order);
            if (!shortages.isEmpty()) {
                throw new EtcInoutStockShortageException(shortages);
            }
        }

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(id);

        // 품목별 재고 변동 처리 — 입고는 전량 정상, 출고는 전량 픽업
        for (EtcInoutOrderItem item : items) {
            if (order.getDirection() == Direction.in) {
                applyInventory(clientId, order, item, item.getQty(), 0, null, userId);
                item.recordWorkResult(item.getQty(), 0, null, null, userId);
            } else {
                applyOutboundInventory(clientId, order, item, item.getQty(), userId);
                item.recordPickResult(item.getQty(), userId);
            }
        }

        order.complete(userId);
        notifyEtcInout("COMPLETED", order, userId, false);
    }

    // IoType + 정상/불량 분기 재고 반영
    private void applyInventory(UUID clientId, EtcInoutOrder order, EtcInoutOrderItem item,
                                int processedQty, int defectQty,
                                UUID defectLocationId, UUID actorId) {
        switch (order.getIoType()) {

            // ── 입고 방향 ──────────────────────────────────────
            case sample_in:
            case adjust_in:
            case etc_in:
                if (processedQty > 0) {
                    inventoryService.addAvailableByEtcInout(
                            clientId, item.getProductId(), order.getWarehouseId(),
                            item.getLocationId(), processedQty, order.getId(), actorId);
                }
                if (defectQty > 0) {
                    UUID defectLoc = defectLocationId != null
                            ? defectLocationId : item.getLocationId();
                    inventoryService.addDefectByEtcInout(
                            clientId, item.getProductId(), order.getWarehouseId(),
                            defectLoc, defectQty, order.getId(), actorId);
                }
                break;

            // ── 폐기 입고 — 폐기창고 defectQty 증가 ──
            // 폐기 대기 재고를 defectQty 로 격리.
            // 가용재고/안전재고 알림 영향 없음. 출고는 dispose_out 이 차감.
            case dispose_in: {
                int total = (processedQty > 0 ? processedQty : 0)
                        + (defectQty > 0 ? defectQty : 0);
                if (total > 0) {
                    inventoryService.addDefectByEtcInout(
                            clientId, item.getProductId(), order.getWarehouseId(),
                            item.getLocationId(), total, order.getId(), actorId);
                }
                break;
            }

            // ── 출고 방향 ──────────────────────────────────────
            case dispose_out:       // 폐기 출고 → defect 차감
                inventoryService.disposeByEtcInout(
                        clientId, item.getProductId(), order.getWarehouseId(),
                        item.getLocationId(), item.getQty(), order.getId(), actorId);
                break;

            case sample_out:
            case adjust_out:
            case etc_out:
                inventoryService.reduceAvailableByEtcInout(
                        clientId, item.getProductId(), order.getWarehouseId(),
                        item.getLocationId(), item.getQty(), order.getId(), actorId);
                break;
        }
    }

    // 기타입출고 취소 (draft / approved 상태에서만)
    public void cancel(UUID clientId, UUID id) {

        EtcInoutOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (order.getStatus() != EtcInoutStatus.draft
                && order.getStatus() != EtcInoutStatus.approved) {
            throw new IllegalStateException("완료/취소된 기록은 취소할 수 없습니다.");
        }

        order.updateStatus(EtcInoutStatus.cancelled);

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(id);
        for (EtcInoutOrderItem item : items) {
            item.updateStatus(EtcInoutItemStatus.cancelled);
        }
    }

}

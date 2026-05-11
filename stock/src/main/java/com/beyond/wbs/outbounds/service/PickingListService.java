package com.beyond.wbs.outbounds.service;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.*;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.outbounds.assignment.PickingAssignmentService;
import com.beyond.wbs.outbounds.domain.*;
import com.beyond.wbs.outbounds.dtos.*;
import com.beyond.wbs.outbounds.repository.*;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.websocket.WorkEventMessage;
import com.beyond.wbs.websocket.WorkerAssignmentRefreshPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.beyond.wbs.websocket.WebSocketPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class PickingListService {
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final PickinglistRepository pickinglistRepository;
    private final PickingListItemRepository pickingListItemRepository;
    private final OutboundPickinglistRepository outboundPickinglistRepository;
    private final InventoryRepository inventoryRepository;
    private final NumberingUtil numberingUtil;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final WebSocketPublisher webSocketPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PickingAssignmentService pickingAssignmentService;
    private final WorkerAssignmentRefreshPublisher workerAssignmentRefreshPublisher;

    @Autowired
    public PickingListService(OutboundOrderRepository outboundOrderRepository,
                              OutboundOrderItemRepository outboundOrderItemRepository,
                              PickinglistRepository pickinglistRepository,
                              PickingListItemRepository pickingListItemRepository,
                              OutboundPickinglistRepository outboundPickinglistRepository,
                              InventoryRepository inventoryRepository,
                              NumberingUtil numberingUtil,
                              MasterServiceClient masterServiceClient,
                              AccountServiceClient accountServiceClient,
                              WebSocketPublisher webSocketPublisher,
                              ApplicationEventPublisher applicationEventPublisher,
                              PickingAssignmentService pickingAssignmentService,
                              WorkerAssignmentRefreshPublisher workerAssignmentRefreshPublisher) {
        this.outboundOrderRepository = outboundOrderRepository;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.pickinglistRepository = pickinglistRepository;
        this.pickingListItemRepository = pickingListItemRepository;
        this.outboundPickinglistRepository = outboundPickinglistRepository;
        this.inventoryRepository = inventoryRepository;
        this.numberingUtil = numberingUtil;
        this.masterServiceClient = masterServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.webSocketPublisher = webSocketPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.pickingAssignmentService = pickingAssignmentService;
        this.workerAssignmentRefreshPublisher = workerAssignmentRefreshPublisher;
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

    /**
     * 상품 상세(name, sku 등) 를 통째로 조회. 실패 시 null.
     */
    private ProductResDto fetchProduct(UUID productId, UUID clientId) {
        if (productId == null || clientId == null) return null;
        try {
            return masterServiceClient.getProduct(productId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] product 조회 실패: {} - {}", productId, e.getMessage());
            return null;
        }
    }

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

    // 로케이션 ID → 위치 상세(전체 code, floorNo, rack 정보) 조회
    // PickingListItems에는 locationId만 저장돼 있고, 화면에서는 같은 랙 내 층까지 구분해야 하므로
    // LocationResDto 자체를 반환해 floorNo/code를 함께 사용한다.
    private LocationResDto fetchLocationDetail(UUID locationId, UUID clientId) {
        if (locationId == null) return null;
        try {
            return masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] location 조회 실패: locationId={} - {}", locationId, e.getMessage());
            return null;
        }
    }

    // 웨이브 생성 (피킹리스트 자동생성)
    public List<UUID> createWave(WaveCreateReqDto dto, UUID userId, UUID clientId){

        // 선택된 출고 지시서 조회
        List<OutboundOrders> orders = new ArrayList<>();
        for(UUID id : dto.getOutboundOrderIds()){
            // id랑 clientId 둘 다 조건으로 조회
            // 없으면 권한 없거나 존재하지 않는 지시서
            OutboundOrders order = outboundOrderRepository.findByIdAndClientId(id, clientId)
                    .orElseThrow(()-> new IllegalArgumentException("출고 지시서를 찾을 수 없거나 접근 권한이 없습니다: " + id));
            orders.add(order);
        }

        // 승인완료 상태 체크
        for (OutboundOrders order : orders){
            if(order.getStatus()!= OutboundOrderStatus.approved){
                throw new IllegalArgumentException("승인완료 상태의 출고지시서만 선택할 수 있습니다.");
            }
        }

        // 첫번째 출고지시서 창고 ID 사용
        // 프론트에서 같은 창고만 선택 가능하도록 보장하는게 성능적으로 좋음
        UUID warehouseId = orders.get(0).getWarehouseId();

        // 전체 품목 조회 - 출고지시서 ID로 그 지시서에 속한 품목들을 찾는 것
        // flatMap -> 리스트를 하나로 합치는 것
        List<OutboundOrderItems> allItems = orders.stream()
                .flatMap(order -> outboundOrderItemRepository.findByOutboundOrdersId(order.getId())
                        .stream()).collect(Collectors.toList());

        // SKU별 수량 합산 (product_id 기준으로 그룹핑)
        Map<UUID, List<OutboundOrderItems>> groupedItems = allItems.stream()
                .collect(Collectors.groupingBy(OutboundOrderItems::getProductId));

        // 생성된 피킹리스트 ID 목록
        List<UUID> pickingListIds = new ArrayList<>();

        // ============================================================
        // 단건 vs 다건 분기
        // ============================================================
        // 단건 (출고지시서 1개): 피킹리스트 1개에 모든 상품 품목을 담음
        //   → 한 사람이 한 번에 다 꺼내므로 분리할 이유 없음
        // 다건 (출고지시서 여러 개): 상품(SKU)별로 피킹리스트 각각 생성
        //   → 상품마다 담당자가 다를 수 있고, 동선 최적화를 위해 분리
        // ============================================================

        boolean isSingleOrder = (orders.size() == 1);
        Map<UUID, UUID> autoAssignments = isSingleOrder
                ? Map.of()
                : buildAutoAssignmentsIfNeeded(dto, groupedItems.keySet().stream().toList(), clientId, userId, warehouseId);

        if (isSingleOrder) {
            // ── 단건: 피킹리스트 1개에 모든 상품 ──
            UUID assignedTo = resolveSingleOrderAssignee(
                    dto,
                    clientId,
                    userId,
                    warehouseId,
                    groupedItems.keySet().stream().sorted().toList());

            PickingList pickingList = PickingList.builder()
                    .clientId(clientId)
                    .pickingNo(numberingUtil.generatePickingNo())
                    .warehouseId(warehouseId)
                    .assignedTo(assignedTo)
                    .status(PickingListStatus.pending)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            pickinglistRepository.save(pickingList);
            pickingListIds.add(pickingList.getId());

            // 모든 SKU 를 순회하며 이 하나의 피킹리스트에 품목 배정
            for (UUID productId : groupedItems.keySet()) {
                List<OutboundOrderItems> items = groupedItems.get(productId);
                distributeItems(pickingList.getId(), productId, warehouseId, items);
            }

            // 출고지시서-피킹리스트 연결
            for (OutboundOrderItems item : allItems) {
                outboundPickinglistRepository.save(OutboundPickinglist.builder()
                        .outboundOrderId(item.getOutboundOrdersId())
                        .pickingListId(pickingList.getId())
                        .build());
            }

        } else {
            // ── 다건: 상품(SKU)별로 피킹리스트 각각 생성 ──
            for (UUID productId : groupedItems.keySet()) {
                List<OutboundOrderItems> items = groupedItems.get(productId);

                UUID assignedTo = resolveProductAssignee(dto, productId, autoAssignments, userId);

                PickingList pickingList = PickingList.builder()
                        .clientId(clientId)
                        .pickingNo(numberingUtil.generatePickingNo())
                        .warehouseId(warehouseId)
                        .assignedTo(assignedTo)
                        .status(PickingListStatus.pending)
                        .createdBy(userId)
                        .createdAt(LocalDateTime.now())
                        .build();
                pickinglistRepository.save(pickingList);
                pickingListIds.add(pickingList.getId());

                // 이 SKU 의 품목들을 위치별로 분배
                distributeItems(pickingList.getId(), productId, warehouseId, items);

                // 출고지시서-피킹리스트 연결
                for (OutboundOrderItems item : items) {
                    outboundPickinglistRepository.save(OutboundPickinglist.builder()
                            .outboundOrderId(item.getOutboundOrdersId())
                            .pickingListId(pickingList.getId())
                            .build());
                }
            }
        }

        // 출고 지시서 상태 in_progress(처리중)로 변경
        for(OutboundOrders order : orders){
            order.changeStatus(OutboundOrderStatus.in_progress);
            outboundOrderRepository.save(order);
        }

        // 피킹리스트 PDF 발행 요청 — 생성된 모든 리스트에 대해 1건씩 발행
        // 같은 회사 관리자에게 피킹리스트 생성 알림 + 출고지시서 IN_PROGRESS 전환 알림 push
        for (UUID pickingListId : pickingListIds) {
            PickingList pl = pickinglistRepository.findById(pickingListId).orElse(null);
            if (pl != null) {
                applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                        InstructionDocumentType.PICKING_LIST,
                        pl.getId(),
                        pl.getPickingNo(),
                        clientId,
                        userId
                ));

                webSocketPublisher.send("/topic/admin/picking/" + clientId,
                        WorkEventMessage.builder()
                                .module("picking")
                                .type("CREATED")
                                .clientId(clientId)
                                .orderId(pl.getId())
                                .orderNo(pl.getPickingNo())
                                .userId(userId)
                                .occurredAt(LocalDateTime.now())
                                .build());
                workerAssignmentRefreshPublisher.publishRefresh(
                        "picking",
                        clientId,
                        pl.getAssignedTo(),
                        pl.getId(),
                        pl.getPickingNo());
            }
        }

        // 출고지시서 IN_PROGRESS 전환 알림 (목록 + 상세)
        for (OutboundOrders order : orders) {
            WorkEventMessage inProgressMsg = WorkEventMessage.builder()
                    .module("outbound")
                    .type("IN_PROGRESS")
                    .clientId(clientId)
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .userId(userId)
                    .occurredAt(LocalDateTime.now())
                    .build();
            webSocketPublisher.send("/topic/admin/outbound/" + clientId, inProgressMsg);
            webSocketPublisher.send("/topic/admin/outbound/" + clientId + "/" + order.getId(), inProgressMsg);
        }

        return pickingListIds;
    }

    /**
     * 피킹 위치 분배 (공통 헬퍼)
     *
     * 특정 상품(productId)의 출고품목들을 물리적 보관 위치 기준으로
     * PickingListItems 로 분배하여 저장한다.
     *
     * - 상품이 여러 로케이션에 분산돼 있으면 PickingListItems 도 여러 row 로 쪼갬
     * - 출고 승인 시 reserve 가 끝난 상태이므로 totalQty(물리 존재) 기준으로 조회
     *
     * @param pickingListId 이 품목들이 속할 피킹리스트 ID
     * @param productId     상품 ID
     * @param warehouseId   창고 ID
     * @param items         이 상품에 해당하는 출고지시서 품목 목록
     */
    private void distributeItems(UUID pickingListId, UUID productId,
                                  UUID warehouseId, List<OutboundOrderItems> items) {
        // 출고 승인 시점에 reserve 가 끝났으므로 분배는 reservedQty > 0 위치만 대상이어야 한다.
        // (reserve 안된 위치는 가용재고/다른 출고 후보지이므로 손대면 안 됨)
        // findLocationsWithStock 은 totalQty DESC 라 동률일 때 reserve 안된 위치가 먼저 뽑힐 수 있어 명시적으로 필터링.
        List<Inventory> reservedLocations = inventoryRepository
                .findLocationsWithStock(productId, warehouseId).stream()
                .filter(i -> i.getReservedQty() != null && i.getReservedQty() > 0)
                .toList();

        // ───────────────────────────────────────────────────────
        // [C안] 사전 검증 — 이 상품의 총 reserved 가 wave 의 총 ordered 를 커버하는지.
        //  - ATP 는 liberal(available + incoming + pending) 통과 → 승인은 가능
        //  - 그러나 실제 reserve 는 available 만 잡으므로, 가용 부족 시 wave 생성을 차단해 사용자 안내
        //  - 입고예정/검수중 재고는 적치 완료 시점에 autoReserveUnfilledOrders 가 자동 예약
        // ───────────────────────────────────────────────────────
        int totalOrderedQty = items.stream().mapToInt(OutboundOrderItems::getOrderedQty).sum();
        int totalReservedQty = reservedLocations.stream().mapToInt(Inventory::getReservedQty).sum();

        if (reservedLocations.isEmpty() || totalReservedQty < totalOrderedQty) {
            String label = productId.toString().substring(0, 8);
            throw new IllegalStateException(String.format(
                    "[%s] 출고 작업(wave) 생성 불가 — 예약된 가용재고 부족.%n" +
                            "  요청: %d개 / 현재 예약: %d개%n" +
                            "  입고예정/검수중 재고는 적치 완료 시점에 자동으로 예약됩니다. " +
                            "진행 중인 적치를 완료한 뒤 다시 시도해 주세요.",
                    label, totalOrderedQty, totalReservedQty));
        }

        // Iterator 기반 분배 — 한 위치에서 reservedQty 만큼만 빼고 다음 위치로 이동
        Iterator<Inventory> invIter = reservedLocations.iterator();
        Inventory currentInv = invIter.hasNext() ? invIter.next() : null;
        int currentRemaining = currentInv != null ? currentInv.getReservedQty() : 0;

        for (OutboundOrderItems orderItem : items) {
            int needed = orderItem.getOrderedQty();

            while (needed > 0) {
                if (currentInv == null) {
                    // 사전 검증 통과 후 도달 시 코드 버그.
                    throw new IllegalStateException(
                            "내부 분배 오류 — 사전 검증 후 예약 위치 부족: productId=" + productId);
                }

                int take = Math.min(needed, currentRemaining);

                pickingListItemRepository.save(PickingListItems.builder()
                        .pickingListId(pickingListId)
                        .outboundOrderItemId(orderItem.getId())
                        .productId(productId)
                        .qty(take)
                        .pickedQty(0)
                        .status(PickingListItemStatus.pending)
                        .locationId(currentInv.getLocationId())
                        .build());

                needed -= take;
                currentRemaining -= take;

                if (currentRemaining == 0) {
                    currentInv = invIter.hasNext() ? invIter.next() : null;
                    currentRemaining = currentInv != null ? currentInv.getReservedQty() : 0;
                }
            }
        }
    }

    // 피킹리스트 목록조회 (페이징 처리)
    @Transactional(readOnly = true)
    public Page<PickingListResDto> getPickingLists(UUID clientId,
                                                  UUID requesterId,
                                                  UUID warehouseId,
                                                  PickingListStatus status,
                                                  UUID assignedTo,
                                                  Pageable pageable){
        return getPickingLists(clientId, requesterId, warehouseId, status, assignedTo, null, pageable);
    }

    /**
     * 피킹리스트 멀티필터 검색 — productIds 가 있으면 EXISTS 서브쿼리로 매칭.
     * productIds 가 null/빈 리스트면 기존 단순 목록과 동일.
     */
    @Transactional(readOnly = true)
    public Page<PickingListResDto> getPickingLists(UUID clientId,
                                                  UUID requesterId,
                                                  UUID warehouseId,
                                                  PickingListStatus status,
                                                  UUID assignedTo,
                                                  List<UUID> productIds,
                                                  Pageable pageable) {
        Page<PickingList> pickingLists = (productIds != null && !productIds.isEmpty())
                ? pickinglistRepository.findByConditionsAndProductIds(
                        clientId, warehouseId, status, assignedTo, productIds, pageable)
                : pickinglistRepository.findByConditions(
                        clientId, warehouseId, status, assignedTo, pageable);

        // 같은 창고/담당자/생성자가 반복되므로 Feign 결과 캐싱 (N+1 방지)
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        Map<UUID, String> userNameCache = new HashMap<>();

        return pickingLists.map(p1 -> {
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    p1.getWarehouseId(), wid -> fetchWarehouseName(wid, clientId));
            String assignedToName = userNameCache.computeIfAbsent(
                    p1.getAssignedTo(), uid -> fetchUserName(uid, requesterId));
            String createdByName = userNameCache.computeIfAbsent(
                    p1.getCreatedBy(), uid -> fetchUserName(uid, requesterId));

            return PickingListResDto.builder()
                    .id(p1.getId())
                    .pickingNo(p1.getPickingNo())
                    .warehouseName(warehouseName)
                    .assignedTo(p1.getAssignedTo())
                    .assignedToName(assignedToName)
                    .createdByName(createdByName)
                    .status(p1.getStatus())
                    .startedAt(p1.getStartedAt())
                    .completedAt(p1.getCompletedAt())
                    .createdAt(p1.getCreatedAt())
                    .build();
        });
    }

    /**
     * 피킹리스트 내 상품 라인 — productIds 로 필터링.
     *
     * 피킹리스트 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    @Transactional(readOnly = true)
    public List<PickingListItemResDto> getPickingItemsByProductIds(UUID pickingListId,
                                                                    List<UUID> productIds,
                                                                    UUID clientId) {
        // 회사 소속 검증
        PickingList pickingList = pickinglistRepository.findById(pickingListId)
                .orElseThrow(() -> new IllegalArgumentException("피킹리스트를 찾을 수 없습니다."));
        if (!pickingList.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 피킹리스트만 조회할 수 있습니다.");
        }

        // 라인 조회 — productIds 있으면 IN 매칭, 없으면 전체
        List<PickingListItems> items = (productIds == null || productIds.isEmpty())
                ? pickingListItemRepository.findByPickingListId(pickingListId)
                : pickingListItemRepository.findByPickingListIdAndProductIdIn(pickingListId, productIds);

        // 상품/로케이션 정보 캐시 후 DTO 변환
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();
        List<PickingListItemResDto> result = new ArrayList<>();
        for (PickingListItems item : items) {
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            LocationResDto location = item.getLocationId() == null ? null
                    : locationCache.computeIfAbsent(item.getLocationId(),
                                                    lid -> fetchLocationDetail(lid, clientId));

            result.add(PickingListItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(product != null ? product.getSku() : null)
                    .productName(product != null ? product.getName() : null)
                    .rackId(location != null ? location.getRackId() : null)
                    .rackCode(location != null ? location.getRackCode() : null)
                    .zoneName(location != null ? location.getZoneName() : null)
                    .locationId(item.getLocationId())
                    .locationCode(location != null ? location.getCode() : null)
                    .floorNo(location != null ? location.getFloorNo() : null)
                    .qty(item.getQty())
                    .pickedQty(item.getPickedQty())
                    .lotNo(item.getLotNo())
                    .status(item.getStatus())
                    .pickedAt(item.getPickedAt())
                    .build());
        }
        return result;
    }

    // 피킹리스트 상세조회
    @Transactional(readOnly = true)
    public PickingListDetailResDto getPickingList(UUID id, UUID requesterId, UUID clientId){
        PickingList pickingList = pickinglistRepository.findById(id)
                .orElseThrow(()-> new IllegalArgumentException("피킹리스트를 찾을 수 없습니다."));

        // 피킹리스트 품목 목록조회
        List<PickingListItems> pickingListItems = pickingListItemRepository.findByPickingListId(id);

        // 같은 상품/로케이션이 반복될 수 있으므로 Feign 결과 캐싱 (N+1 방지)
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();

        // 품목 dto변환
        List<PickingListItemResDto> itemDto = new ArrayList<>();
        for(PickingListItems item : pickingListItems){
            ProductResDto product = productCache.computeIfAbsent(
                    item.getProductId(), pid -> fetchProduct(pid, clientId));
            String sku = product != null ? product.getSku() : null;
            String productName = product != null ? product.getName() : null;

            LocationResDto location = item.getLocationId() == null ? null
                    : locationCache.computeIfAbsent(item.getLocationId(), lid -> fetchLocationDetail(lid, clientId));
            UUID rackId = location != null ? location.getRackId() : null;
            String rackCode = location != null ? location.getRackCode() : null;
            String zoneName = location != null ? location.getZoneName() : null;
            String locationCode = location != null ? location.getCode() : null;
            Integer floorNo = location != null ? location.getFloorNo() : null;

            PickingListItemResDto pickingListItemResDto = PickingListItemResDto.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .sku(sku)
                    .productName(productName)
                    .rackId(rackId)
                    .rackCode(rackCode)
                    .zoneName(zoneName)
                    .locationId(item.getLocationId())
                    .locationCode(locationCode)
                    .floorNo(floorNo)
                    .qty(item.getQty())
                    .pickedQty(item.getPickedQty())
                    .lotNo(item.getLotNo())
                    .status(item.getStatus())
                    .pickedAt(item.getPickedAt())
                    .build();
            itemDto.add(pickingListItemResDto);
        }

        // 피킹리스트 헤더 Feign 호출로 이름 정보 채우기
        String warehouseName = fetchWarehouseName(pickingList.getWarehouseId(), clientId);
        String assignedToName = fetchUserName(pickingList.getAssignedTo(), requesterId);
        String createdByName = fetchUserName(pickingList.getCreatedBy(), requesterId);

        return PickingListDetailResDto.builder()
                .id(pickingList.getId())
                .pickingNo(pickingList.getPickingNo())
                .warehouseName(warehouseName)
                .assignedTo(pickingList.getAssignedTo())
                .assignedToName(assignedToName)
                .createdByName(createdByName)
                .status(pickingList.getStatus())
                .startedAt(pickingList.getStartedAt())
                .completedAt(pickingList.getCompletedAt())
                .createdAt(pickingList.getCreatedAt())
                .items(itemDto)
                .build();
    }

    // 피킹리스트 QR생성
    // QR 코드 데이터 = 피킹리스트 ID
    // 작업자가 모바일 앱에서 QR 스캔 시 해당 피킹리스트 조회 가능
    public String getPickingListQr(UUID id){
        //피킹리스트 존재여부 확인
        pickinglistRepository.findById(id).orElseThrow(()-> new IllegalArgumentException("피킹리스트를 찾을 수 없습니다."));

        // 피킹리스트 ID를 QR 코드 데이터로 반환
        // 프론트에서 이 값으로 QR 이미지 생성
        // QR 코드는 문자열 데이터를 담음
        // UUID → String으로 변환해서 QR에 담아야 함

        return id.toString();
    }

    // 품목별 피킹 수량 수동입력
    public void pickItem(UUID id, UUID itemId, PickItemReqDto dto, UUID userId){

        // 피킹 품목 조회
        PickingListItems item = pickingListItemRepository.findById(itemId)
                .orElseThrow(()-> new IllegalArgumentException("피킹품목을 찾을 수 없습니다."));

        // 수량 초과 입력 차단
        // 지시서 수량보다 많이 입력하면 에러
        if(dto.getPickedQty()>item.getQty()){
            throw new IllegalArgumentException(
                    "지시서 수량(" + item.getQty() + "개)을 초과할 수 없습니다. " +
                    "초과 수량: " + (dto.getPickedQty() - item.getQty()) + "개");
        }

        // 피킹품목들의 상태 결정
        // 입력 수량 == 목표 수량 → 완료
        // 입력 수량 < 목표 수량 → 재고부족
        PickingListItemStatus newStatus;
        if(dto.getPickedQty().equals(item.getQty())){
            newStatus = PickingListItemStatus.completed;
        }else{
            newStatus = PickingListItemStatus.shortage;
        }

        // 품목 업데이트 (도메인 메서드로 pickedQty, status, pickedBy, pickedAt 변경)
        item.pick(dto.getPickedQty(), newStatus, userId);
        pickingListItemRepository.save(item);

        PickingList pickingListForAssignment = pickinglistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("피킹리스트를 찾을 수 없습니다."));
        pickingAssignmentService.recordLastLocation(
                pickingListForAssignment.getClientId(),
                userId,
                pickingListForAssignment.getWarehouseId(),
                item.getLocationId());

        // 부모 피킹리스트 상태 재계산 — 첫 품목 피킹 시 pending → in_progress 전환되면서 startedAt 세팅됨
        updatePickingListStatus(id);

        // 웹 관리자에게 품목 피킹 알림 push — 상세 채널만
        // (a) 피킹리스트 상세 채널 — 그 피킹리스트 상세 보고 있는 관리자
        PickingList pickingListForMsg = pickingListForAssignment;
        String pickingNo = pickingListForMsg != null ? pickingListForMsg.getPickingNo() : null;
        UUID pickingClientId = pickingListForMsg != null ? pickingListForMsg.getClientId() : null;
        webSocketPublisher.send("/topic/admin/picking/" + pickingClientId + "/" + id,
                WorkEventMessage.builder()
                        .module("picking")
                        .type("PICKED")
                        .clientId(pickingClientId)
                        .orderId(id)
                        .orderNo(pickingNo)
                        .userId(userId)
                        .occurredAt(LocalDateTime.now())
                        .build());

        // (b) 출고 지시서 상세 채널들 — 연결된 출고들 상세 보고 있는 관리자
        List<UUID> connectedOrderIds = outboundPickinglistRepository.findByPickingListId(id).stream()
                .map(OutboundPickinglist::getOutboundOrderId)
                .distinct()
                .toList();
        for (UUID orderId : connectedOrderIds) {
            OutboundOrders order = outboundOrderRepository.findById(orderId).orElse(null);
            if (order == null) continue;
            webSocketPublisher.send("/topic/admin/outbound/" + order.getClientId() + "/" + order.getId(),
                    WorkEventMessage.builder()
                            .module("outbound")
                            .type("PICKED")
                            .clientId(order.getClientId())
                            .orderId(order.getId())
                            .orderNo(order.getOrderNo())
                            .userId(userId)
                            .occurredAt(LocalDateTime.now())
                            .build());
        }
    }

    // 피킹리스트 전체 상태 자동 업데이트
    // 품목별 피킹완료 시 피킹리스트 전체 상태를 자동으로 업데이트
    private void updatePickingListStatus(UUID pickingListId){
        List<PickingListItems> items = pickingListItemRepository.findByPickingListId(pickingListId);
        boolean allCompleted = true; // 처음엔 전부 완료됐다고 가정
        boolean anyPending = false;  // 처음엔 대기중인 것 없다고 가정

        for(PickingListItems item : items){
            if(item.getStatus()!= PickingListItemStatus.completed){
                allCompleted = false;   // 완료 안 된 게 하나라도 있으면 false
            }
            if(item.getStatus() == PickingListItemStatus.pending){
                anyPending = true;      // 대기중인 게 하나라도 있으면 true
            }
        }

        // 전체 완료 → completed
        // 일부 대기 → in_progress
        // 일부 재고부족 → partial (shortage일때 partial이라는 뜻)
        PickingListStatus newStatus;
        if (allCompleted){
            newStatus = PickingListStatus.completed;
        }else if(anyPending){
            newStatus = PickingListStatus.in_progress;
        }else{
            newStatus = PickingListStatus.partial;
        }

        PickingList pickingList = pickinglistRepository.findById(pickingListId)
                .orElseThrow(() -> new IllegalArgumentException("피킹리스트를 찾을 수 없습니다."));

        pickingList.changeStatus(newStatus);
        pickinglistRepository.save(pickingList);
    }

    // 피킹리스트 완료 처리
    public void completePickingList(UUID id, UUID userId){
        // 피킹리스트 조회
        PickingList pickingList = pickinglistRepository.findById(id)
                .orElseThrow(()-> new IllegalArgumentException("피킹리스트 찾을 수 없습니다."));

        // 피킹리스트 품목 전체 조회
        List<PickingListItems> items = pickingListItemRepository.findByPickingListId(id);

        // 아직 피킹 안 한 품목 체크
        // pending 상태인 품목이 있으면 완료처리 불가
        for(PickingListItems item : items){
            if(item.getStatus() == PickingListItemStatus.pending){
                throw new IllegalArgumentException("아직 피킹되지 않은 품목이 있습니다.");
            }
        }
        // 전체 완료 여부 체크
        // 전부 completed면 completed, 일부 shortage면 partial
        boolean allCompleted = true;
        for(PickingListItems item : items){
            if(item.getStatus()!= PickingListItemStatus.completed){
                allCompleted = false;
                break;
            }
        }

        pickingList.changeStatus(allCompleted ? PickingListStatus.completed : PickingListStatus.partial);
        pickinglistRepository.save(pickingList);

        // 연결된 출고 지시서 품목 피킹수량 업데이트
        // 한 outbound_order_item 이 여러 위치로 분할 피킹된 경우 picking_list_items 가 여러 row 로 존재하므로
        // outboundOrderItemId 기준으로 합산해 한 번만 적용해야 한다 (개별 호출 시 applyPickedResult 가 덮어쓰기라 마지막 값만 남는 버그)
        Map<UUID, List<PickingListItems>> itemsByOrderItemId = items.stream()
                .collect(Collectors.groupingBy(PickingListItems::getOutboundOrderItemId));

        for (Map.Entry<UUID, List<PickingListItems>> entry : itemsByOrderItemId.entrySet()) {
            UUID orderItemId = entry.getKey();
            List<PickingListItems> picksForOrderItem = entry.getValue();

            int totalPicked = picksForOrderItem.stream()
                    .mapToInt(PickingListItems::getPickedQty)
                    .sum();
            boolean allItemsCompleted = picksForOrderItem.stream()
                    .allMatch(p -> p.getStatus() == PickingListItemStatus.completed);

            OutboundOrderItemsStatus orderItemsStatus = allItemsCompleted
                    ? OutboundOrderItemsStatus.completed
                    : OutboundOrderItemsStatus.shortage;

            OutboundOrderItems orderItem = outboundOrderItemRepository.findById(orderItemId)
                    .orElseThrow(()-> new IllegalArgumentException("출고지시서 품목을 찾을 수 없습니다."));

            orderItem.applyPickedResult(totalPicked, orderItemsStatus);
            outboundOrderItemRepository.save(orderItem);
        }

        // 피킹을 마감한 사용자를 출고 담당자로 기록한다.
        // 방어 가드: 상태가 in_progress 아니거나 이미 담당자가 있으면 skip
        List<UUID> connectedOrderIds = outboundPickinglistRepository.findByPickingListId(id).stream()
                .map(OutboundPickinglist::getOutboundOrderId)
                .distinct()
                .toList();

        for (UUID orderId : connectedOrderIds) {
            OutboundOrders order = outboundOrderRepository.findById(orderId).orElse(null);
            if (order == null) continue;
            if (order.getAssignedTo() != null) continue;
            if (order.getStatus() != OutboundOrderStatus.in_progress) continue;

            boolean allDone = outboundOrderItemRepository.findByOutboundOrdersId(orderId).stream()
                    .noneMatch(i -> i.getStatus() == OutboundOrderItemsStatus.pending
                                 || i.getStatus() == OutboundOrderItemsStatus.picking);

            if (allDone) {
                order.assignDispatcher(userId);
                outboundOrderRepository.save(order);
            }
        }

        // 웹 관리자에게 피킹리스트 마감 알림 push — 4개 채널 (피킹/출고 × 목록/상세)
        // type: 전부 정상이면 COMPLETED, shortage 섞이면 PARTIAL
        boolean hasShortage = items.stream()
                .anyMatch(i -> i.getStatus() == PickingListItemStatus.shortage);
        String closeType = hasShortage ? "PARTIAL" : "COMPLETED";

        UUID pickingClientId = pickingList.getClientId();

        // (a) 피킹리스트 채널 (목록 + 상세)
        WorkEventMessage pickingCloseMsg = WorkEventMessage.builder()
                .module("picking")
                .type(closeType)
                .clientId(pickingClientId)
                .orderId(id)
                .orderNo(pickingList.getPickingNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/picking/" + pickingClientId, pickingCloseMsg);
        webSocketPublisher.send("/topic/admin/picking/" + pickingClientId + "/" + id, pickingCloseMsg);

        // (b) 출고 지시서 채널 (각 연결된 출고마다 목록 + 상세)
        for (UUID orderId : connectedOrderIds) {
            OutboundOrders order = outboundOrderRepository.findById(orderId).orElse(null);
            if (order == null) continue;
            WorkEventMessage closeMsg = WorkEventMessage.builder()
                    .module("outbound")
                    .type(closeType)
                    .clientId(order.getClientId())
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .userId(userId)
                    .occurredAt(LocalDateTime.now())
                    .build();
            webSocketPublisher.send("/topic/admin/outbound/" + order.getClientId(), closeMsg);
            webSocketPublisher.send("/topic/admin/outbound/" + order.getClientId() + "/" + order.getId(), closeMsg);
        }
    }

    private UUID resolveSingleOrderAssignee(
            WaveCreateReqDto dto,
            UUID clientId,
            UUID requesterId,
            UUID warehouseId,
            List<UUID> productIds) {
        UUID manualAssignee = dto.getAssignments() == null ? null : dto.getAssignments().values().stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (manualAssignee != null) {
            return manualAssignee;
        }
        return pickingAssignmentService.assignForWave(clientId, requesterId, warehouseId, productIds);
    }

    private UUID resolveProductAssignee(
            WaveCreateReqDto dto,
            UUID productId,
            Map<UUID, UUID> autoAssignments,
            UUID fallbackUserId) {
        if (dto.getAssignments() != null && dto.getAssignments().get(productId) != null) {
            return dto.getAssignments().get(productId);
        }
        return autoAssignments.getOrDefault(productId, fallbackUserId);
    }

    private Map<UUID, UUID> buildAutoAssignmentsIfNeeded(
            WaveCreateReqDto dto,
            List<UUID> productIds,
            UUID clientId,
            UUID requesterId,
            UUID warehouseId) {
        List<UUID> missingProductIds = productIds.stream()
                .filter(productId -> dto.getAssignments() == null || dto.getAssignments().get(productId) == null)
                .sorted()
                .toList();
        if (missingProductIds.isEmpty()) {
            return Map.of();
        }
        return pickingAssignmentService.assignByProducts(clientId, requesterId, warehouseId, missingProductIds);
    }

}

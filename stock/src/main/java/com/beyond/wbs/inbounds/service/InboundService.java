package com.beyond.wbs.inbounds.service;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.RackResDto;
import com.beyond.wbs.common.client.dto.SupplierResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.assignment.WorkAssignmentService;
import com.beyond.wbs.assignment.WorkTaskType;
import com.beyond.wbs.inbounds.domain.*;
import com.beyond.wbs.inbounds.dto.*;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.inbounds.kafka.InboundEventPublisher;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.kafka.event.InboundStockEvent;
import com.beyond.wbs.search.inbound.InboundSearchQuery;
import com.beyond.wbs.inbounds.repository.*;
import com.beyond.wbs.inventory.dtos.SuggestLocationResDto;
import com.beyond.wbs.inventory.exception.LocationCapacityExceededException;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.inventory.service.PlacementPurpose;
import com.beyond.wbs.inventory.service.PlacementSuggestionService;
import com.beyond.wbs.outbounds.domain.OutboundOrderItems;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.search.inbound.InboundOrderSearchService;
import com.beyond.wbs.websocket.WorkEventMessage;
import com.beyond.wbs.websocket.WorkerAssignmentRefreshPublisher;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.beyond.wbs.websocket.WebSocketPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

@Slf4j
@Service
@Transactional(readOnly = true)
public class InboundService {
    private static final String NORMAL_WAREHOUSE_TYPE = "NORMAL";


    private final ErpPurchaseOrderRepository erpPurchaseOrderRepository;
    private final ErpPurchaseOrderItemRepository erpPurchaseOrderItemRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptItemRepository inboundReceiptItemRepository;
    private final PlacementOrderRepository placementOrderRepository;
    private final PlacementItemRepository placementItemRepository;
    private final InboundOrderSearchService inboundOrderSearchService;
    private final InboundEventPublisher inboundEventPublisher;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final InventoryRepository inventoryRepository;
    private final NumberingUtil numberingUtil;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final InventoryService inventoryService;
    private final PlacementSuggestionService placementSuggestionService;
    private final WebSocketPublisher webSocketPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkAssignmentService workAssignmentService;
    private final WorkerAssignmentRefreshPublisher workerAssignmentRefreshPublisher;

    public InboundService(ErpPurchaseOrderRepository erpPurchaseOrderRepository,
                          ErpPurchaseOrderItemRepository erpPurchaseOrderItemRepository,
                          InboundOrderRepository inboundOrderRepository,
                          InboundOrderItemRepository inboundOrderItemRepository,
                          InboundReceiptRepository inboundReceiptRepository,
                          InboundReceiptItemRepository inboundReceiptItemRepository,
                          PlacementOrderRepository placementOrderRepository,
                          PlacementItemRepository placementItemRepository,
                          InboundOrderSearchService inboundOrderSearchService,
                          InboundEventPublisher inboundEventPublisher,
                          MasterServiceClient masterServiceClient,
                          AccountServiceClient accountServiceClient,
                          InventoryRepository inventoryRepository,
                          NumberingUtil numberingUtil,
                          OutboundOrderRepository outboundOrderRepository,
                          OutboundOrderItemRepository outboundOrderItemRepository,
                          InventoryService inventoryService,
                          PlacementSuggestionService placementSuggestionService,
                          ApplicationEventPublisher applicationEventPublisher,
                          WebSocketPublisher webSocketPublisher,
                          WorkAssignmentService workAssignmentService,
                          WorkerAssignmentRefreshPublisher workerAssignmentRefreshPublisher) {
        this.erpPurchaseOrderRepository = erpPurchaseOrderRepository;
        this.erpPurchaseOrderItemRepository = erpPurchaseOrderItemRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.inboundReceiptItemRepository = inboundReceiptItemRepository;
        this.placementOrderRepository = placementOrderRepository;
        this.placementItemRepository = placementItemRepository;
        this.inboundOrderSearchService = inboundOrderSearchService;
        this.inboundEventPublisher = inboundEventPublisher;
        this.masterServiceClient = masterServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.inventoryRepository = inventoryRepository;
        this.numberingUtil = numberingUtil;
        this.outboundOrderRepository = outboundOrderRepository;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.inventoryService = inventoryService;
        this.placementSuggestionService = placementSuggestionService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.webSocketPublisher = webSocketPublisher;
        this.workAssignmentService = workAssignmentService;
        this.workerAssignmentRefreshPublisher = workerAssignmentRefreshPublisher;
    }

    /**
     * 여러 productId 의 Master Service 상품 정보를 한 번에 조회해 Map 으로 반환한다.
     * - Master 호출 실패 시 해당 상품은 null 로 남겨두고 전체 응답을 실패시키지 않는다.
     * - DTO fromEntity(..., ProductResDto) 에서 null 이면 빈 문자열로 표시.
     */
    private Map<UUID, ProductResDto> fetchProducts(Set<UUID> productIds, UUID clientId) {
        Map<UUID, ProductResDto> map = new HashMap<>();
        for (UUID pid : productIds) {
            if (pid == null || map.containsKey(pid)) continue;
            try {
                map.put(pid, masterServiceClient.getProduct(pid, clientId.toString()));
            } catch (Exception e) {
                log.warn("Master product 조회 실패 productId={}, err={}", pid, e.getMessage());
                map.put(pid, null);
            }
        }
        return map;
    }

    private String diagnoseUnassignedReason(PlacementItems item, PlacementOrders po, ProductResDto product, UUID clientId) {
        if (item == null || item.getLocationId() != null || po == null) return null;
        if (product == null) {
            return "상품 정보를 확인할 수 없어 자동 추천에 실패했습니다.";
        }

        PlacementPurpose purpose = item.isDefect() ? PlacementPurpose.DEFECT : PlacementPurpose.NORMAL;
        List<SuggestLocationResDto> suggestions = placementSuggestionService.suggest(
                item.getProductId(), po.getWarehouseId(), item.getQty(), clientId, purpose);

        if (suggestions.isEmpty()) {
            if (purpose != PlacementPurpose.NORMAL) {
                return "추천 가능한 불량 보관 위치가 없습니다.";
            }
            boolean hasSupplier = product.getSupplierId() != null;
            boolean hasCategory = product.getCategoryId() != null;
            if (!hasSupplier && !hasCategory) {
                return "상품의 협력사/카테고리 정보가 없어 추천 위치를 찾지 못했습니다.";
            }
            if (!hasCategory) {
                return "상품 카테고리에 연결된 보관 구역이 없습니다.";
            }
            if (!hasSupplier) {
                return "상품에 연결된 협력사 랙이 없어 추천 위치를 찾지 못했습니다.";
            }
            return "추천 가능한 랙 또는 로케이션이 없습니다.";
        }

        int totalRemain = 0;
        for (SuggestLocationResDto suggestion : suggestions) {
            Integer remain = suggestion.getRemainCapacity();
            totalRemain += Math.max(0, remain != null ? remain : item.getQty());
        }

        if (totalRemain < item.getQty()) {
            return "자동 추천 위치의 잔여 용량이 부족했습니다.";
        }

        return "자동 추천 위치는 있었지만 같은 배치에서 먼저 선점되었거나 위치 정책에 맞지 않았습니다.";
    }

    /**
     * 여러 supplierId 의 Master Service 협력사 정보를 한 번에 조회해 Map 으로 반환한다.
     * - 실패 시 해당 entry 는 null 로 남겨두고 전체 응답은 정상 반환
     */
    private Map<UUID, SupplierResDto> fetchSuppliers(Set<UUID> supplierIds, UUID clientId) {
        Map<UUID, SupplierResDto> map = new HashMap<>();
        for (UUID sid : supplierIds) {
            if (sid == null || map.containsKey(sid)) continue;
            try {
                map.put(sid, masterServiceClient.getSupplier(sid, clientId.toString()));
            } catch (Exception e) {
                log.warn("Master supplier 조회 실패 supplierId={}, err={}", sid, e.getMessage());
                map.put(sid, null);
            }
        }
        return map;
    }

    /**
     * 여러 warehouseId 의 Master Service 창고 정보를 한 번에 조회해 Map 으로 반환한다.
     * - master 쪽에서 X-Client-Id 헤더로 멀티테넌시 검증하므로 clientId 필수
     * - 실패 시 해당 entry 는 null 로 남겨두고 전체 응답은 정상 반환
     */
    private Map<UUID, WarehouseResDto> fetchWarehouses(Set<UUID> warehouseIds, UUID clientId) {
        Map<UUID, WarehouseResDto> map = new HashMap<>();
        for (UUID wid : warehouseIds) {
            if (wid == null || map.containsKey(wid)) continue;
            try {
                map.put(wid, masterServiceClient.getWarehouse(wid, clientId.toString()));
            } catch (Exception e) {
                log.warn("Master warehouse 조회 실패 warehouseId={}, err={}", wid, e.getMessage());
                map.put(wid, null);
            }
        }
        return map;
    }

    /**
     * 일반 입고(발주서 기반 / 수동 생성)는 NORMAL 창고에서만 허용한다.
     * 반품/폐기 전용 창고는 별도 흐름에서만 사용한다.
     */
    private void validateNormalInboundWarehouse(UUID warehouseId, UUID clientId) {
        WarehouseResDto warehouse = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
        if (warehouse == null) {
            throw new NoSuchElementException("창고를 찾을 수 없습니다.");
        }
        if (Boolean.FALSE.equals(warehouse.getIsActive())) {
            throw new IllegalStateException("비활성 창고에는 입고지시서를 생성할 수 없습니다.");
        }
        if (!NORMAL_WAREHOUSE_TYPE.equals(warehouse.getWarehouseType())) {
            throw new IllegalArgumentException("일반 입고는 정상창고에서만 생성할 수 있습니다.");
        }
    }

    /**
     * 로케이션 수용량 검증 (적치 지시서 생성 시)
     *
     * 해당 로케이션의 현재 재고 + 넣으려는 수량이 최대 수용량을 초과하면 에러.
     * maxCapacity 가 null 이면 무제한으로 간주.
     */
    private void validateLocationCapacity(UUID locationId, UUID warehouseId, int addQty, UUID clientId) {
        if (locationId == null) return;

        LocationResDto location;
        try {
            location = masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[수용량 검증] Master 조회 실패, 검증 생략: locationId={}", locationId);
            return;
        }

        if (location == null || location.getMaxCapacity() == null) return;

        // 해당 로케이션의 현재 재고량 조회
        List<Inventory> existing = inventoryRepository
                .findByWarehouseIdAndLocationId(warehouseId, locationId);
        int currentQty = existing.stream().mapToInt(Inventory::getTotalQty).sum();

        int afterQty = currentQty + addQty;
        if (afterQty > location.getMaxCapacity()) {
            throw new IllegalArgumentException(
                    "로케이션 수용량 초과: locationId=" + locationId
                            + ", 최대수용량=" + location.getMaxCapacity() + "개"
                            + ", 현재보관량=" + currentQty + "개"
                            + ", 추가하려는량=" + addQty + "개"
                            + ", 합계=" + afterQty + "개");
        }
    }

    /**
     * 신규 적치 작업이 들어가는 위치가 실제로 사용 가능한지 검증한다.
     * - 위치 비활성 여부
     */
    private void validatePlacementLocationUsable(UUID locationId, UUID clientId) {
        LocationResDto location = masterServiceClient.getLocation(locationId, clientId.toString());
        if (location == null) {
            throw new NoSuchElementException("로케이션을 찾을 수 없습니다.");
        }
        if (Boolean.FALSE.equals(location.getIsActive())) {
            throw new IllegalStateException("비활성 로케이션에는 적치할 수 없습니다.");
        }
    }

    /**
     * locationId → Rack 정보 (rackCode / zoneName 표시용)
     * - 2단계 조회: getLocation → getRack
     * - 같은 rackId 는 한 번만 조회하도록 내부 캐시 사용 (N+1 방지)
     * - 실패 시 해당 entry 는 null 로 두고 전체 응답은 살려둠
     *
     * @param locationCodesOut (nullable) 넘기면 각 locationId 의 locationCode 가 채워짐
     */
    private Map<UUID, RackResDto> fetchRacksByLocationIds(Set<UUID> locationIds, UUID clientId,
                                                           Map<UUID, String> locationCodesOut) {
        Map<UUID, RackResDto> rackByLocation = new HashMap<>();
        Map<UUID, RackResDto> rackByRackId = new HashMap<>();
        for (UUID locId : locationIds) {
            if (locId == null || rackByLocation.containsKey(locId)) continue;
            try {
                LocationResDto loc = masterServiceClient.getLocation(locId, clientId.toString());
                if (loc == null) {
                    rackByLocation.put(locId, null);
                    if (locationCodesOut != null) locationCodesOut.put(locId, null);
                    continue;
                }
                if (locationCodesOut != null) locationCodesOut.put(locId, loc.getCode());
                if (loc.getRackId() == null) {
                    rackByLocation.put(locId, null);
                    continue;
                }
                RackResDto rack = rackByRackId.get(loc.getRackId());
                if (rack == null) {
                    rack = masterServiceClient.getRack(loc.getRackId(), clientId.toString());
                    rackByRackId.put(loc.getRackId(), rack);
                }
                rackByLocation.put(locId, rack);
            } catch (Exception e) {
                log.warn("Master rack 조회 실패 locationId={}, err={}", locId, e.getMessage());
                rackByLocation.put(locId, null);
                if (locationCodesOut != null) locationCodesOut.put(locId, null);
            }
        }
        return rackByLocation;
    }

    private Map<UUID, RackResDto> fetchRacksByLocationIds(Set<UUID> locationIds, UUID clientId) {
        return fetchRacksByLocationIds(locationIds, clientId, null);
    }

    /**
     * 1. ASN(ERP 발주서) 목록 조회
     * approved 상태인 발주서만 조회 (승인된 것만 입고 가능)
     * supplierName / productName / sku 는 master-service Feign 호출로 채움 (N+1 방지 캐싱)
     */
    public List<AsnOrderResDto> getAsnOrders(UUID clientId) {
        // 승인된 발주서 목록 조회
        List<ErpPurchaseOrders> orders = erpPurchaseOrderRepository
                .findByClientIdAndStatus(clientId, ErpPurchaseOrderStatus.approved);

        // 모든 발주서 품목을 미리 한 번에 조회하고 발주서별로 그룹핑 (쿼리 루프 내부 호출을 피하기 위함)
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> supplierIds = new HashSet<>();
        Map<UUID, List<ErpPurchaseOrderItems>> itemsByOrder = new HashMap<>();
        for (ErpPurchaseOrders order : orders) {
            List<ErpPurchaseOrderItems> items = erpPurchaseOrderItemRepository
                    .findByPurchaseOrderId(order.getId());
            itemsByOrder.put(order.getId(), items);
            for (ErpPurchaseOrderItems item : items) productIds.add(item.getProductId());
            if (order.getSupplierId() != null) supplierIds.add(order.getSupplierId());
        }

        // Feign 일괄 조회 (중복 제거된 ID 셋 기준)
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);
        Map<UUID, SupplierResDto> suppliers = fetchSuppliers(supplierIds, clientId);

        // 각 발주서를 응답 DTO로 변환
        List<AsnOrderResDto> result = new ArrayList<>();
        for (ErpPurchaseOrders order : orders) {
            List<ErpPurchaseOrderItems> items = itemsByOrder.get(order.getId());

            List<AsnItemResDto> itemDtos = new ArrayList<>();
            for (ErpPurchaseOrderItems item : items) {
                itemDtos.add(AsnItemResDto.fromEntity(item, products.get(item.getProductId())));
            }

            SupplierResDto supplier = suppliers.get(order.getSupplierId());
            String supplierName = supplier != null ? supplier.getName() : null;

            result.add(AsnOrderResDto.fromEntity(order, supplierName, itemDtos));
        }
        return result;
    }

    /**
     * 1-1. "발주서 목록" 화면용 — PO 전체 + 진행률 보강 응답 (Spring Page).
     *
     * PO 1건 ↔ 입고지시서 1건 (1:1) 정책. 데이터 누적 대비 페이징 처리.
     *
     *  - statusFilter: ALL | NOT_STARTED | IN_PROGRESS | COMPLETED (FE 라벨 그대로)
     *  - poNoKeyword: poNo 또는 supplierName 부분 일치
     *  - dateFrom/dateTo: scheduledDate 범위
     *  - hideCompleted: COMPLETED 처리상태 숨김
     *
     * 구현: status/keyword 필터는 in-memory 처리 (processStatus 가 inbound join 후에야 결정되므로),
     *   필터 후 결과에 대해 페이지 슬라이싱. 데이터 양 늘어나면 native query + 인덱스로 최적화 검토.
     */
    public Page<PurchaseOrderListItemResDto> getPurchaseOrderList(
            UUID clientId,
            String statusFilter,
            String poNoKeyword,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean hideCompleted,
            Pageable pageable) {

        // 1. 모든 PO (approved + closed)
        List<ErpPurchaseOrders> allPos = erpPurchaseOrderRepository.findByClientId(clientId);

        // 2. 날짜 1차 필터 (in-memory)
        List<ErpPurchaseOrders> dateFiltered = new ArrayList<>();
        for (ErpPurchaseOrders po : allPos) {
            if (dateFrom != null && po.getScheduledDate().isBefore(dateFrom)) continue;
            if (dateTo != null && po.getScheduledDate().isAfter(dateTo)) continue;
            dateFiltered.add(po);
        }

        if (dateFiltered.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }

        // 3. 일괄 fetch — supplier
        Set<UUID> supplierIds = new HashSet<>();
        for (ErpPurchaseOrders po : dateFiltered) {
            if (po.getSupplierId() != null) supplierIds.add(po.getSupplierId());
        }
        Map<UUID, SupplierResDto> suppliers = fetchSuppliers(supplierIds, clientId);

        // 4. PO 별 보강 + 키워드/상태 필터
        String kw = poNoKeyword == null ? null : poNoKeyword.trim().toLowerCase();
        List<PurchaseOrderListItemResDto> result = new ArrayList<>();
        for (ErpPurchaseOrders po : dateFiltered) {
            SupplierResDto supplier = suppliers.get(po.getSupplierId());
            String supplierName = supplier != null ? supplier.getName() : null;

            // 키워드 필터 (poNo OR supplierName)
            if (kw != null && !kw.isEmpty()) {
                String poNoLower = po.getPoNo() == null ? "" : po.getPoNo().toLowerCase();
                String supLower = supplierName == null ? "" : supplierName.toLowerCase();
                if (!poNoLower.contains(kw) && !supLower.contains(kw)) continue;
            }

            // PO 품목 집계
            List<ErpPurchaseOrderItems> poItems = erpPurchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
            int itemCount = poItems.size();
            int totalOrderedQty = poItems.stream().mapToInt(ErpPurchaseOrderItems::getQty).sum();

            // 연결 입고지시서 (cancelled 제외) — 1:1 정책이라 최대 1건이지만 방어적 처리
            List<InboundOrders> linked = inboundOrderRepository
                    .findByOriginIdAndOriginType(po.getId(), "purchase_order");
            InboundOrders activeInbound = linked.stream()
                    .filter(io -> io.getStatus() != InboundOrderStatus.cancelled)
                    .max(Comparator.comparing(InboundOrders::getCreatedAt))
                    .orElse(null);

            int receiveProgressPercent = 0;
            PurchaseOrderListItemResDto.ProcessStatus processStatus =
                    PurchaseOrderListItemResDto.ProcessStatus.NOT_STARTED;
            UUID inboundOrderId = null;
            String inboundOrderNo = null;
            InboundOrderStatus inboundStatus = null;

            if (activeInbound != null) {
                inboundOrderId = activeInbound.getId();
                inboundOrderNo = activeInbound.getOrderNo();
                inboundStatus = activeInbound.getStatus();

                // 진행률 = 단계 가중치 × 100  (A안: draft/approved 30, received 60, placing 80, completed/partial 100)
                //   PO ↔ 입고지시서가 1:1 이라 link.qty 합산 없이 그 입고지시서의 status 가중치만 보면 됨.
                //   "검수 직전까지 0%" 옛 버그 해결 — 입고지시서 만들면 30% 부터 시작.
                double weight = progressWeightForInboundStatus(inboundStatus);
                receiveProgressPercent = (int) Math.round(weight * 100);

                if (weight >= 1.0) {
                    processStatus = PurchaseOrderListItemResDto.ProcessStatus.COMPLETED;
                } else {
                    processStatus = PurchaseOrderListItemResDto.ProcessStatus.IN_PROGRESS;
                }
            }

            if (statusFilter != null && !"ALL".equalsIgnoreCase(statusFilter)) {
                if (!processStatus.name().equalsIgnoreCase(statusFilter)) continue;
            }
            if (hideCompleted && processStatus == PurchaseOrderListItemResDto.ProcessStatus.COMPLETED) continue;

            result.add(PurchaseOrderListItemResDto.builder()
                    .id(po.getId())
                    .poNo(po.getPoNo())
                    .supplierId(po.getSupplierId())
                    .supplierName(supplierName)
                    .orderDate(po.getOrderDate())
                    .scheduledDate(po.getScheduledDate())
                    .poStatus(po.getStatus())
                    .processStatus(processStatus)
                    .inboundOrderId(inboundOrderId)
                    .inboundOrderNo(inboundOrderNo)
                    .inboundStatus(inboundStatus)
                    .receiveProgressPercent(receiveProgressPercent)
                    .itemCount(itemCount)
                    .totalOrderedQty(totalOrderedQty)
                    .build());
        }

        // 5. 정렬 — pageable.sort 우선 적용, 없거나 unknown 필드면 scheduledDate ASC + poNo ASC fallback
        result.sort(buildPurchaseOrderComparator(pageable));

        // 6. 페이지 슬라이싱
        int totalElements = result.size();
        int from = Math.min((int) pageable.getOffset(), totalElements);
        int to = Math.min(from + pageable.getPageSize(), totalElements);
        List<PurchaseOrderListItemResDto> pageContent = result.subList(from, to);

        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, totalElements);
    }

    /** pageable.sort 를 PurchaseOrderListItemResDto 비교자로 변환 — 알려진 필드만 처리, 나머지는 기본 정렬 */
    private Comparator<PurchaseOrderListItemResDto> buildPurchaseOrderComparator(Pageable pageable) {
        Comparator<PurchaseOrderListItemResDto> defaultCmp = Comparator
                .comparing(PurchaseOrderListItemResDto::getScheduledDate,
                        Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(PurchaseOrderListItemResDto::getPoNo,
                        Comparator.nullsLast(String::compareTo));

        if (pageable == null || pageable.getSort().isUnsorted()) return defaultCmp;

        Comparator<PurchaseOrderListItemResDto> cmp = null;
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            Comparator<PurchaseOrderListItemResDto> next = switch (order.getProperty()) {
                case "scheduledDate" -> Comparator.comparing(
                        PurchaseOrderListItemResDto::getScheduledDate,
                        Comparator.nullsLast(LocalDate::compareTo));
                case "orderDate" -> Comparator.comparing(
                        PurchaseOrderListItemResDto::getOrderDate,
                        Comparator.nullsLast(LocalDate::compareTo));
                case "poNo" -> Comparator.comparing(
                        PurchaseOrderListItemResDto::getPoNo,
                        Comparator.nullsLast(String::compareTo));
                case "receiveProgressPercent" -> Comparator.comparingInt(
                        PurchaseOrderListItemResDto::getReceiveProgressPercent);
                default -> null;
            };
            if (next == null) continue;
            if (order.isDescending()) next = next.reversed();
            cmp = (cmp == null) ? next : cmp.thenComparing(next);
        }
        return cmp == null ? defaultCmp : cmp;
    }

    /**
     * 1-2. 단일 ASN 발주서 미리보기.
     *
     * 프론트 "발주서 불러오기" 모달에서 PO 선택 시 호출.
     * 품목별 SKU/상품명/수량 + 상품 등록 여부(matched) 를 반환하여,
     * 관리자가 입고지시서 생성 전에 미등록 상품을 파악하고 상품등록까지 유도할 수 있게 한다.
     *
     * 단일 건 조회이지만 N+1 방지를 위해 products 는 batch 로 조회.
     */
    public AsnOrderResDto getAsnOrderPreview(UUID asnId, UUID clientId) {
        ErpPurchaseOrders order = erpPurchaseOrderRepository.findById(asnId)
                .orElseThrow(() -> new NoSuchElementException("발주서를 찾을 수 없습니다."));

        // 소유권 검증
        if (!order.getClientId().equals(clientId)) {
            throw new SecurityException("해당 발주서에 대한 접근 권한이 없습니다.");
        }

        List<ErpPurchaseOrderItems> items = erpPurchaseOrderItemRepository
                .findByPurchaseOrderId(asnId);

        Set<UUID> productIds = new HashSet<>();
        for (ErpPurchaseOrderItems item : items) productIds.add(item.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);

        List<AsnItemResDto> itemDtos = new ArrayList<>();
        for (ErpPurchaseOrderItems item : items) {
            itemDtos.add(AsnItemResDto.fromEntity(item, products.get(item.getProductId())));
        }

        String supplierName = resolveSupplierName(order.getSupplierId(), clientId);
        return AsnOrderResDto.fromEntity(order, supplierName, itemDtos);
    }

    /**
     * 2. ASN → 입고 지시서 생성
     * ASN(발주서)을 기반으로 입고 지시서와 품목을 생성하고, ASN 상태를 closed로 변경
     */
    @Transactional
    public InboundOrderResDto createFromAsn(UUID asnId, UUID warehouseId, UUID clientId, UUID userId) {
        validateNormalInboundWarehouse(warehouseId, clientId);

        // 1. ASN(발주서) 조회
        ErpPurchaseOrders asn = erpPurchaseOrderRepository.findById(asnId)
                .orElseThrow(() -> new NoSuchElementException("ASN을 찾을 수 없습니다."));

        // 2. 지시서 번호 자동 채번 (IN-2026-001 형식)
        String orderNo = numberingUtil.generateInboundOrderNo();

        // 2-1. ASN 품목 미리 조회 — supplier 검증에 productId 필요
        List<ErpPurchaseOrderItems> asnItems = erpPurchaseOrderItemRepository
                .findByPurchaseOrderId(asnId);

        // 2-2. 입고처(ASN supplier) ↔ 상품 supplier 일치 검증
        validateInboundSupplierMatch(asn.getSupplierId(),
                asnItems.stream().map(ErpPurchaseOrderItems::getProductId).toList(),
                clientId);

        // 3. 입고 지시서 생성 (draft 상태)
        InboundOrders inboundOrder = InboundOrders.fromAsn(asn, warehouseId, clientId, userId, orderNo);
        inboundOrderRepository.save(inboundOrder);

        // 4. ASN 품목 → 입고 지시서 품목 생성

        int totalQty = 0;
        for (ErpPurchaseOrderItems asnItem : asnItems) {
            InboundOrderItems orderItem = InboundOrderItems.fromAsnItem(asnItem, inboundOrder.getId());
            inboundOrderItemRepository.save(orderItem);
            totalQty += asnItem.getQty();
        }

        // 5. ASN 상태를 closed로 변경 (중복 생성 방지)
        asn.setStatus(ErpPurchaseOrderStatus.closed);
        erpPurchaseOrderRepository.save(asn);

        // 6. Elasticsearch 색인
        inboundOrderSearchService.index(inboundOrder.getId());

        // 6-1. 통계/대시보드 카운트용 — 생성 이벤트 발행
        inboundEventPublisher.publishCreated(InboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .refId(inboundOrder.getId())
                .userId(userId)
                .originType(inboundOrder.getOriginType())
                .items(List.of())
                .build());

        // 같은 회사 관리자에게 생성 알림 push (목록만)
        WorkEventMessage createdMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("CREATED")
                .clientId(clientId)
                .orderId(inboundOrder.getId())
                .orderNo(inboundOrder.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, createdMsg);

        // 7. 응답 DTO 반환 (Master Service 이름 조회)
        String supplierName = resolveSupplierName(inboundOrder.getSupplierId(), clientId);
        String warehouseName = resolveWarehouseName(inboundOrder.getWarehouseId(), clientId);
        return InboundOrderResDto.fromEntity(inboundOrder, supplierName, warehouseName, "", asnItems.size(), totalQty);
    }

    /**
     * 3. 수동 입고 지시서 생성
     * 관리자가 직접 협력사/창고/품목 정보를 입력하여 생성
     */
    @Transactional
    public InboundOrderResDto createManual(CreateInboundReqDto dto, UUID clientId, UUID userId) {
        // 1. 지시서 번호 자동 채번
        String orderNo = numberingUtil.generateInboundOrderNo();

        // 2. 입고 지시서 생성 (draft 상태)
        UUID supplierId = dto.getSupplierId();
        UUID warehouseId = dto.getWarehouseId();
        validateNormalInboundWarehouse(warehouseId, clientId);

        // 2-0. 입고처 ↔ 상품 supplier 일치 검증 (생성 시점에 데이터 정합성 차단)
        // 자사(공급자 null) 상품을 외부 협력사로 받거나, 외부 상품을 자사로 받는 케이스 모두 차단.
        validateInboundSupplierMatch(supplierId,
                dto.getItems().stream().map(CreateInboundItemDto::getProductId).toList(),
                clientId);

        InboundOrders inboundOrder = InboundOrders.createManual(
                clientId, warehouseId, supplierId, userId, orderNo,
                LocalDate.parse(dto.getExpectedDate()), dto.getSource());
        inboundOrderRepository.save(inboundOrder);

        // 3. 품목 생성
        int totalQty = 0;
        for (CreateInboundItemDto itemDto : dto.getItems()) {
            UUID productId = itemDto.getProductId();

            InboundOrderItems orderItem = itemDto.toEntity(inboundOrder.getId(), productId);
            inboundOrderItemRepository.save(orderItem);
            totalQty += itemDto.getQty();
        }

        // 4. Elasticsearch 색인
        inboundOrderSearchService.index(inboundOrder.getId());

        // 4-1. 통계/대시보드 카운트용 — 생성 이벤트 발행 (재고 영향 없음)
        inboundEventPublisher.publishCreated(InboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .refId(inboundOrder.getId())
                .userId(userId)
                .originType(inboundOrder.getOriginType())
                .items(List.of())
                .build());

        // 같은 회사 관리자에게 생성 알림 push (목록만)
        WorkEventMessage createdMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("CREATED")
                .clientId(clientId)
                .orderId(inboundOrder.getId())
                .orderNo(inboundOrder.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, createdMsg);

        // 5. 응답 DTO 반환 (Master Service 이름 조회)
        String supplierName = resolveSupplierName(inboundOrder.getSupplierId(), clientId);
        String warehouseName = resolveWarehouseName(inboundOrder.getWarehouseId(), clientId);
        return InboundOrderResDto.fromEntity(inboundOrder, supplierName, warehouseName, "", dto.getItems().size(), totalQty);
    }

    /**
     * 3-1. 반품 입고지시서 생성
     * 출고지시서를 기준으로 반품 수량을 입력받아 입고지시서를 자동 생성한다.
     * originType = "return", originId = 출고지시서 ID
     *
     * 이후 흐름(승인→검수→적치→재고반영)은 일반 입고와 100% 동일.
     */
    @Transactional
    public InboundOrderResDto createFromReturn(CreateFromReturnReqDto dto, UUID clientId, UUID userId) {
        // 1. 출고지시서 조회 + 소유권 검증
        OutboundOrders outbound = outboundOrderRepository.findByIdAndClientId(dto.getOutboundOrderId(), clientId)
                .orElseThrow(() -> new NoSuchElementException("출고지시서를 찾을 수 없습니다."));

        // 2. 반품 수량 검증 — 출고 수량 초과 방지 (기존 반품 합산 포함)
        List<OutboundOrderItems> outboundItems = outboundOrderItemRepository
                .findByOutboundOrdersId(outbound.getId());

        // 출고 품목을 productId → 출고 수량으로 매핑
        Map<UUID, Integer> outboundQtyByProduct = new HashMap<>();
        for (OutboundOrderItems oi : outboundItems) {
            outboundQtyByProduct.merge(oi.getProductId(),
                    oi.getPickedQty() != null ? oi.getPickedQty() : oi.getOrderedQty(),
                    Integer::sum);
        }

        // 기존 반품 입고지시서의 품목 수량 합산 (중복 반품 방지)
        Map<UUID, Integer> alreadyReturnedByProduct = new HashMap<>();
        List<InboundOrders> existingReturns = inboundOrderRepository
                .findByOriginIdAndOriginType(outbound.getId(), "return");
        for (InboundOrders existing : existingReturns) {
            List<InboundOrderItems> existingItems = inboundOrderItemRepository
                    .findByInboundOrderId(existing.getId());
            for (InboundOrderItems ei : existingItems) {
                alreadyReturnedByProduct.merge(ei.getProductId(), ei.getOrderedQty(), Integer::sum);
            }
        }

        for (CreateFromReturnReqDto.ReturnItem item : dto.getItems()) {
            Integer outboundQty = outboundQtyByProduct.get(item.getProductId());
            if (outboundQty == null) {
                throw new IllegalArgumentException(
                        "출고지시서에 없는 상품입니다: " + item.getProductId());
            }
            int alreadyReturned = alreadyReturnedByProduct.getOrDefault(item.getProductId(), 0);
            int remaining = outboundQty - alreadyReturned;
            if (item.getQty() > remaining) {
                throw new IllegalArgumentException(
                        "반품 가능 수량을 초과합니다. 출고: " + outboundQty
                                + ", 기반품: " + alreadyReturned
                                + ", 잔여: " + remaining
                                + ", 요청: " + item.getQty());
            }
        }

        // 3. 입고지시서 생성 (originType: "return") — 공통 채번 사용
        String orderNo = numberingUtil.generateInboundOrderNo();
        InboundOrders inboundOrder = InboundOrders.createReturn(
                clientId, dto.getWarehouseId(), userId, orderNo, outbound.getId());
        inboundOrderRepository.save(inboundOrder);

        // 4. 입고 품목 생성
        int totalQty = 0;
        for (CreateFromReturnReqDto.ReturnItem item : dto.getItems()) {
            InboundOrderItems orderItem = InboundOrderItems.builder()
                    .inboundOrderId(inboundOrder.getId())
                    .productId(item.getProductId())
                    .orderedQty(item.getQty())
                    .receivedQty(0)
                    .defectQty(0)
                    .status(InboundOrderItemStatus.pending)
                    .build();
            inboundOrderItemRepository.save(orderItem);
            totalQty += item.getQty();
        }

        // 5. Elasticsearch 색인
        inboundOrderSearchService.index(inboundOrder.getId());

        // 5-1. 통계/대시보드 카운트용 — 생성 이벤트 발행
        inboundEventPublisher.publishCreated(InboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(dto.getWarehouseId())
                .refId(inboundOrder.getId())
                .userId(userId)
                .originType(inboundOrder.getOriginType())
                .items(List.of())
                .build());

        // 같은 회사 관리자에게 반품 입고지시서 생성 알림 push (목록만)
        WorkEventMessage createdMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("CREATED_RETURN")
                .clientId(clientId)
                .orderId(inboundOrder.getId())
                .orderNo(inboundOrder.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, createdMsg);

        // 6. 응답 — 반품 출고처명 조회
        String warehouseName = resolveWarehouseName(dto.getWarehouseId(), clientId);
        String returnFrom = resolveStoreName(outbound.getStoreId(), clientId);
        return InboundOrderResDto.fromEntity(inboundOrder, "", warehouseName, "",
                dto.getItems().size(), totalQty, returnFrom);
    }

    // (비페이징 목록은 페이징 버전으로 통합됨 — 삭제)

    /**
     * 입고 지시서 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<InboundOrderResDto> getInboundOrders(UUID clientId, List<String> statuses, Pageable pageable, UUID requesterId) {
        return getInboundOrders(clientId, statuses, null, pageable, requesterId);
    }

    @Transactional(readOnly = true)
    public Page<InboundOrderResDto> getInboundOrders(UUID clientId, List<String> statuses,
                                                      List<UUID> productIds, Pageable pageable,
                                                      UUID requesterId) {
        return getInboundOrders(clientId, statuses, productIds, null, null, pageable, requesterId);
    }

    /**
     * 입고지시서 목록 — status / productIds / originType / excludeOriginType 멀티필터.
     *
     * - productIds: EXISTS 서브쿼리
     * - originType: 정확매칭 (예: "return" / "purchase_order" / "manual")
     * - excludeOriginType: 그 값을 가진 row 제외 (originType 이 null 인 row 는 통과)
     * 모두 null/빈 리스트면 기존 단순 목록과 동일.
     *
     * originType 이나 excludeOriginType 이 들어오면 통합 동적 @Query 사용,
     * 그 외 경우는 기존 검증된 4-분기 메서드 유지 (회귀 안전).
     */
    @Transactional(readOnly = true)
    public Page<InboundOrderResDto> getInboundOrders(UUID clientId, List<String> statuses,
                                                      List<UUID> productIds,
                                                      String originType, String excludeOriginType,
                                                      Pageable pageable,
                                                      UUID requesterId) {
        Page<InboundOrders> orderPage;
        List<InboundOrderStatus> statusEnums = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusEnums = new ArrayList<>();
            for (String s : statuses) {
                statusEnums.add(InboundOrderStatus.valueOf(s));
            }
        }

        boolean hasStatus = statusEnums != null && !statusEnums.isEmpty();
        boolean hasProducts = productIds != null && !productIds.isEmpty();
        boolean hasOriginFilter = (originType != null && !originType.isBlank())
                || (excludeOriginType != null && !excludeOriginType.isBlank());

        if (hasOriginFilter) {
            // 통합 동적 쿼리 — 모든 옵셔널 필드 + originType 필터
            orderPage = inboundOrderRepository.searchByConditions(
                    clientId,
                    hasStatus ? statusEnums : null,
                    (originType != null && !originType.isBlank()) ? originType : null,
                    (excludeOriginType != null && !excludeOriginType.isBlank()) ? excludeOriginType : null,
                    hasProducts ? productIds : null,
                    pageable);
        } else if (hasProducts && hasStatus) {
            orderPage = inboundOrderRepository.findByClientIdAndStatusInAndProductIds(
                    clientId, statusEnums, productIds, pageable);
        } else if (hasProducts) {
            orderPage = inboundOrderRepository.findByClientIdAndProductIds(
                    clientId, productIds, pageable);
        } else if (hasStatus) {
            orderPage = inboundOrderRepository.findByClientIdAndStatusIn(clientId, statusEnums, pageable);
        } else {
            orderPage = inboundOrderRepository.findByClientId(clientId, pageable);
        }

        // N+1 방지: 품목 집계를 DB 1회로 처리
        List<InboundOrders> orders = orderPage.getContent();
        List<UUID> orderIds = new ArrayList<>();
        Set<UUID> supplierIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>();
        Set<UUID> outboundIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();

        for (InboundOrders order : orders) {
            orderIds.add(order.getId());
            if (order.getSupplierId() != null) supplierIds.add(order.getSupplierId());
            if (order.getWarehouseId() != null) warehouseIds.add(order.getWarehouseId());
            if (order.getCreatedBy() != null) userIds.add(order.getCreatedBy());
            if (order.getAssignedTo() != null) userIds.add(order.getAssignedTo());
            if ("return".equals(order.getOriginType()) && order.getOriginId() != null) {
                outboundIds.add(order.getOriginId());
            }
        }
        Map<UUID, long[]> summaryMap = buildSummaryMap(orderIds);
        Map<UUID, SupplierResDto> suppliers = fetchSuppliers(supplierIds, clientId);
        Map<UUID, WarehouseResDto> warehouses = fetchWarehouses(warehouseIds, clientId);
        Map<UUID, String> storeNameByOutboundId = resolveStoreNames(outboundIds, clientId);
        Map<UUID, String> userNameMap = new HashMap<>();
        for (UUID uid : userIds) {
            userNameMap.put(uid, resolveUserName(uid, requesterId));
        }

        return orderPage.map(order -> {
            long[] summary = summaryMap.getOrDefault(order.getId(), new long[]{0, 0});
            SupplierResDto supplier = suppliers.get(order.getSupplierId());
            WarehouseResDto warehouse = warehouses.get(order.getWarehouseId());
            String supplierName = supplier != null && supplier.getName() != null ? supplier.getName() : "";
            String warehouseName = warehouse != null && warehouse.getName() != null ? warehouse.getName() : "";
            String returnFrom = storeNameByOutboundId.get(order.getOriginId());
            String createdByName = userNameMap.get(order.getCreatedBy());
            String assignedToName = userNameMap.get(order.getAssignedTo());
            return InboundOrderResDto.fromEntity(order, supplierName, warehouseName, createdByName,
                    (int) summary[0], (int) summary[1], returnFrom, assignedToName);
        });
    }

    /**
     * Elasticsearch 기반 입고 지시서 검색
     */
    public List<InboundSearchResDto> searchInboundOrders(UUID clientId, String keyword, List<String> statuses,
                                                         Integer page, Integer size) {
        InboundSearchQuery query = InboundSearchQuery.builder()
                .clientId(clientId)
                .keyword(keyword)
                .statuses(statuses)
                .page(page != null ? page : 0)
                .size(size != null ? size : 20)
                .build();

        return inboundOrderSearchService.search(query).stream()
                .map(InboundSearchResDto::fromDocument)
                .toList();
    }

    @Transactional
    public InboundReindexResDto reindexInboundOrders(UUID clientId) {
        int indexedCount = inboundOrderSearchService.reindexByClient(clientId);
        return InboundReindexResDto.builder()
                .clientId(clientId)
                .indexedCount(indexedCount)
                .build();
    }

    /**
     * 5. 입고 지시서 상세 조회
     */
    public InboundOrderResDto getInboundOrder(UUID orderId, UUID clientId, UUID requesterId) {
        // 1. 지시서 조회
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        // 2. 같은 회사인지 검증
        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 조회할 수 있습니다.");
        }

        // 3. 품목 조회 → 품목 수, 총 수량 계산
        List<InboundOrderItems> items = inboundOrderItemRepository
                .findByInboundOrderId(orderId);

        int totalQty = 0;
        for (InboundOrderItems item : items) {
            totalQty += item.getOrderedQty();
        }

        // 4. Master/Account Service 이름 조회
        String supplierName = resolveSupplierName(order.getSupplierId(), clientId);
        String warehouseName = resolveWarehouseName(order.getWarehouseId(), clientId);
        String createdByName = resolveUserName(order.getCreatedBy(), requesterId);
        String assignedToName = resolveUserName(order.getAssignedTo(), requesterId);

        // 반품 입고면 출고처명 + 원본 출고지시서 번호 조회
        String returnFrom = null;
        String originNo = null;
        if ("return".equals(order.getOriginType()) && order.getOriginId() != null) {
            OutboundOrders outbound = outboundOrderRepository.findById(order.getOriginId()).orElse(null);
            if (outbound != null) {
                originNo = outbound.getOrderNo();
                if (outbound.getStoreId() != null) {
                    returnFrom = resolveStoreName(outbound.getStoreId(), clientId);
                }
            }
        } else if ("purchase_order".equals(order.getOriginType()) && order.getOriginId() != null) {
            // 일반 발주서로 만들어진 입고지시서 — 원본 발주서 번호(PO-XXX) 채워 상세 화면/인쇄에서 노출
            originNo = erpPurchaseOrderRepository.findById(order.getOriginId())
                    .map(ErpPurchaseOrders::getPoNo)
                    .orElse(null);
        }

        InboundOrderResDto dto = InboundOrderResDto.fromEntity(order, supplierName, warehouseName, createdByName,
                items.size(), totalQty, returnFrom, assignedToName);
        dto.setOriginNo(originNo);
        return dto;
    }

    /**
     * 단건 supplier 이름 조회 (실패 시 빈 문자열 반환)
     */
    private String resolveSupplierName(UUID supplierId, UUID clientId) {
        if (supplierId == null || clientId == null) return "";
        try {
            SupplierResDto supplier = masterServiceClient.getSupplier(supplierId, clientId.toString());
            return supplier != null && supplier.getName() != null ? supplier.getName() : "";
        } catch (Exception e) {
            log.warn("Master supplier 조회 실패 supplierId={}, err={}", supplierId, e.getMessage());
            return "";
        }
    }

    /**
     * 입고지시서 생성 시 입고처(supplier) ↔ 상품 supplier 일치 검증.
     *
     * 정책:
     *  - 둘 다 null   → 자사 ↔ 자사 OK
     *  - 입고처만 null, 상품 supplier 존재 → 차단 (외부 협력사 상품을 자사로 받을 수 없음)
     *  - 상품 supplier 만 null, 입고처 존재 → 차단 (자사 상품을 외부 협력사로 받을 수 없음)
     *  - 둘 다 존재하고 다름 → 차단
     *  - 둘 다 존재하고 같음 → OK
     *
     * 반품(createReturn) 처럼 supplierId 자체가 null 인 경우엔 호출하지 않음.
     */
    private void validateInboundSupplierMatch(UUID inboundSupplierId,
                                              List<UUID> productIds,
                                              UUID clientId) {
        if (productIds == null || productIds.isEmpty()) return;

        for (UUID productId : productIds) {
            if (productId == null) continue;

            ProductResDto product;
            try {
                product = masterServiceClient.getProduct(productId, clientId.toString());
            } catch (Exception e) {
                log.warn("[입고처 검증] 상품 조회 실패 — 검증 생략: productId={}, err={}",
                        productId, e.getMessage());
                continue;
            }
            if (product == null) continue;

            UUID productSupplierId = product.getSupplierId();

            // 두 supplier 가 동일하거나 둘 다 null 이면 통과
            boolean bothNull = inboundSupplierId == null && productSupplierId == null;
            boolean equal = inboundSupplierId != null && inboundSupplierId.equals(productSupplierId);
            if (bothNull || equal) continue;

            // 불일치 — 차단. 메시지에 양쪽 이름 노출 (실패 시에만 추가 Feign).
            String inboundSupplierName = inboundSupplierId == null
                    ? "자사" : resolveSupplierName(inboundSupplierId, clientId);
            String productSupplierName = productSupplierId == null
                    ? "자사" : resolveSupplierName(productSupplierId, clientId);
            String productLabel = product.getName() != null
                    ? product.getName()
                    : (product.getSku() != null ? product.getSku() : productId.toString());

            throw new IllegalArgumentException(String.format(
                    "입고처와 상품의 공급사가 일치하지 않습니다.%n" +
                            "  상품: %s%n" +
                            "  입고처: %s%n" +
                            "  상품 공급사: %s%n" +
                            "공급사가 일치하는 입고처로 다시 선택해 주세요.",
                    productLabel, inboundSupplierName, productSupplierName));
        }
    }

    /**
     * 단건 warehouse 이름 조회 (실패 시 빈 문자열 반환)
     * master 쪽에서 X-Client-Id 검증하므로 clientId 필수
     */
    private String resolveWarehouseName(UUID warehouseId, UUID clientId) {
        if (warehouseId == null || clientId == null) return "";
        try {
            WarehouseResDto warehouse = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
            return warehouse != null && warehouse.getName() != null ? warehouse.getName() : "";
        } catch (Exception e) {
            log.warn("Master warehouse 조회 실패 warehouseId={}, err={}", warehouseId, e.getMessage());
            return "";
        }
    }

    /**
     * 품목 집계 결과를 Map 으로 변환 — orderId → [itemCount, totalQty]
     */
    private Map<UUID, long[]> buildSummaryMap(List<UUID> orderIds) {
        Map<UUID, long[]> map = new HashMap<>();
        if (orderIds.isEmpty()) return map;
        List<Object[]> summaries = inboundOrderRepository.findOrderSummaries(orderIds);
        for (Object[] row : summaries) {
            UUID orderId = (UUID) row[0];
            long itemCount = row[1] != null ? (Long) row[1] : 0;
            long totalQty = row[2] != null ? (Long) row[2] : 0;
            map.put(orderId, new long[]{itemCount, totalQty});
        }
        return map;
    }

    /**
     * 반품 출고처명 일괄 조회 — outboundId → storeName
     */
    private Map<UUID, String> resolveStoreNames(Set<UUID> outboundIds, UUID clientId) {
        Map<UUID, String> result = new HashMap<>();
        if (outboundIds == null || outboundIds.isEmpty()) return result;
        for (UUID outboundId : outboundIds) {
            try {
                OutboundOrders outbound = outboundOrderRepository.findById(outboundId).orElse(null);
                if (outbound != null && outbound.getStoreId() != null) {
                    result.put(outboundId, resolveStoreName(outbound.getStoreId(), clientId));
                }
            } catch (Exception e) {
                log.warn("반품 출고처 조회 실패 outboundId={}", outboundId);
            }
        }
        return result;
    }

    private String resolveUserName(UUID userId, UUID requesterId) {
        if (userId == null || requesterId == null) return null;
        try {
            com.beyond.wbs.common.client.dto.UserResDto u =
                    accountServiceClient.getUser(userId, requesterId.toString());
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("Account user 조회 실패 userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }

    private String resolveStoreName(UUID storeId, UUID clientId) {
        if (storeId == null || clientId == null) return null;
        try {
            com.beyond.wbs.common.client.dto.StoreResDto store =
                    masterServiceClient.getStore(storeId, clientId.toString());
            return store != null && store.getName() != null ? store.getName() : null;
        } catch (Exception e) {
            log.warn("Master store 조회 실패 storeId={}, err={}", storeId, e.getMessage());
            return null;
        }
    }

    /**
     * 6. 입고 지시서 품목 목록 조회
     */
    public List<InboundOrderItemResDto> getInboundItems(UUID orderId, UUID clientId) {
        return getInboundItemsByProductIds(orderId, null, clientId);
    }

    /**
     * 6-A. 입고 지시서 품목 목록 — 상품 ID 필터 적용.
     *
     * 지시서 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    public List<InboundOrderItemResDto> getInboundItemsByProductIds(UUID orderId,
                                                                    List<UUID> productIds,
                                                                    UUID clientId) {
        // 1. 지시서 조회 (회사 검증용)
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 조회할 수 있습니다.");
        }

        // 2. 품목 조회 — productIds 가 있으면 IN 매칭, 없으면 전체
        List<InboundOrderItems> items = (productIds == null || productIds.isEmpty())
                ? inboundOrderItemRepository.findByInboundOrderId(orderId)
                : inboundOrderItemRepository.findByInboundOrderIdAndProductIdIn(orderId, productIds);

        // 3. DTO 변환 (Master Service 에서 sku/productName 채움)
        Set<UUID> ids = new HashSet<>();
        for (InboundOrderItems item : items) ids.add(item.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(ids, clientId);

        List<InboundOrderItemResDto> result = new ArrayList<>();
        for (InboundOrderItems item : items) {
            result.add(InboundOrderItemResDto.fromEntity(item, products.get(item.getProductId())));
        }

        return result;
    }

    /**
     * 입고 전표 조회
     * - 현재 정책은 지시서별 단일 검수이므로 가장 최근 전표를 반환한다.
     */
    /**
     * 입고전표 내 상품 라인 — productIds 로 필터링.
     *
     * 입고전표 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    public List<InboundReceiptItemResDto> getInboundReceiptItemsByProductIds(UUID receiptId,
                                                                              List<UUID> productIds,
                                                                              UUID clientId) {
        InboundReceipts receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("입고전표를 찾을 수 없습니다."));

        InboundOrders order = inboundOrderRepository.findById(receipt.getInboundOrderId())
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));
        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 입고 전표만 조회할 수 있습니다.");
        }

        List<InboundReceiptItems> receiptItems = (productIds == null || productIds.isEmpty())
                ? inboundReceiptItemRepository.findByReceiptId(receiptId)
                : inboundReceiptItemRepository.findByReceiptIdAndProductIdIn(receiptId, productIds);

        // orderItems 매핑 (라인 변환에 필요)
        List<InboundOrderItems> orderItems = inboundOrderItemRepository.findByInboundOrderId(receipt.getInboundOrderId());
        Map<UUID, InboundOrderItems> orderItemMap = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (InboundOrderItems oi : orderItems) {
            orderItemMap.put(oi.getId(), oi);
            ids.add(oi.getProductId());
        }
        for (InboundReceiptItems ri : receiptItems) ids.add(ri.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(ids, clientId);

        List<InboundReceiptItemResDto> result = new ArrayList<>();
        for (InboundReceiptItems ri : receiptItems) {
            InboundOrderItems oi = orderItemMap.get(ri.getOrderItemId());
            result.add(InboundReceiptItemResDto.fromEntity(ri, oi, products.get(ri.getProductId())));
        }
        return result;
    }

    public InboundReceiptResDto getInboundReceiptByOrder(UUID orderId, UUID clientId, UUID requesterId) {
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 입고 전표만 조회할 수 있습니다.");
        }

        InboundReceipts receipt = inboundReceiptRepository.findByInboundOrderId(orderId).stream()
                .max(Comparator.comparing(InboundReceipts::getReceivedAt))
                .orElseThrow(() -> new NoSuchElementException("입고 전표가 아직 생성되지 않았습니다."));

        List<InboundReceiptItems> receiptItems = inboundReceiptItemRepository.findByReceiptId(receipt.getId());
        List<InboundOrderItems> orderItems = inboundOrderItemRepository.findByInboundOrderId(orderId);

        Map<UUID, InboundOrderItems> orderItemMap = new HashMap<>();
        Set<UUID> productIds = new HashSet<>();
        for (InboundOrderItems orderItem : orderItems) {
            orderItemMap.put(orderItem.getId(), orderItem);
            productIds.add(orderItem.getProductId());
        }
        for (InboundReceiptItems receiptItem : receiptItems) {
            productIds.add(receiptItem.getProductId());
        }
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);

        List<InboundReceiptItemResDto> itemDtos = new ArrayList<>();
        for (InboundReceiptItems receiptItem : receiptItems) {
            InboundOrderItems orderItem = orderItemMap.get(receiptItem.getOrderItemId());
            itemDtos.add(InboundReceiptItemResDto.fromEntity(
                    receiptItem,
                    orderItem,
                    products.get(receiptItem.getProductId())
            ));
        }

        String supplierName = resolveSupplierName(order.getSupplierId(), clientId);
        String warehouseName = resolveWarehouseName(receipt.getWarehouseId(), clientId);
        String receivedByName = resolveUserName(receipt.getReceivedBy(), requesterId);
        return InboundReceiptResDto.fromEntity(receipt, order, supplierName, warehouseName, receivedByName, itemDtos);
    }

    /**
     * 입고전표 목록 조회 — 페이지/필터 지원.
     *
     * 모든 필터 null 허용:
     *   - dateFrom/dateTo: 입고 일시(receivedAt) 범위. dateTo 는 [from, to) 형태로 사용 (= 그 날짜의 23:59:59 포함하려면 다음날 00:00 으로 전달).
     *   - warehouseId, originType ("purchase_order"/"manual"/"return"), receiptNoKeyword, orderNoKeyword
     *
     * 응답 각 행에 부모 입고지시서 정보 + 출처 (PO/반품 OB) + 창고/협력사/담당자 이름 포함.
     * N+1 방지를 위해 ID 모은 뒤 batch 조회.
     */
    public Page<InboundReceiptListItemResDto> getReceiptList(
            UUID clientId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            UUID warehouseId,
            String originType,
            String receiptNoKeyword,
            String orderNoKeyword,
            UUID requesterId,
            Pageable pageable) {
        return getReceiptList(clientId, dateFrom, dateTo, warehouseId, originType,
                receiptNoKeyword, orderNoKeyword, null, requesterId, pageable);
    }

    /**
     * 입고전표 목록 — productIds 멀티필터 추가 오버로드.
     * productIds 가 있으면 EXISTS 서브쿼리로 매칭, 없으면 기존 쿼리 그대로.
     */
    public Page<InboundReceiptListItemResDto> getReceiptList(
            UUID clientId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            UUID warehouseId,
            String originType,
            String receiptNoKeyword,
            String orderNoKeyword,
            List<UUID> productIds,
            UUID requesterId,
            Pageable pageable) {

        Page<InboundReceipts> page = (productIds != null && !productIds.isEmpty())
                ? inboundReceiptRepository.findByFiltersAndProductIds(
                        clientId, warehouseId, dateFrom, dateTo, originType,
                        receiptNoKeyword, orderNoKeyword, productIds, pageable)
                : inboundReceiptRepository.findByFilters(
                        clientId, warehouseId, dateFrom, dateTo, originType,
                        receiptNoKeyword, orderNoKeyword, pageable);

        if (page.isEmpty()) return page.map(r -> null);

        // ── ID 수집 ──
        Set<UUID> orderIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (InboundReceipts r : page.getContent()) {
            orderIds.add(r.getInboundOrderId());
            if (r.getWarehouseId() != null) warehouseIds.add(r.getWarehouseId());
            if (r.getReceivedBy() != null) userIds.add(r.getReceivedBy());
        }

        // ── 부모 입고지시서 일괄 조회 ──
        Map<UUID, InboundOrders> orderById = new HashMap<>();
        for (InboundOrders o : inboundOrderRepository.findAllById(orderIds)) {
            orderById.put(o.getId(), o);
        }

        // ── 출처별 ID 분리 ──
        Set<UUID> purchaseOriginIds = new HashSet<>();
        Set<UUID> returnOriginIds = new HashSet<>();
        Set<UUID> supplierIds = new HashSet<>();
        for (InboundOrders o : orderById.values()) {
            if (o.getOriginId() != null) {
                if ("purchase_order".equals(o.getOriginType())) purchaseOriginIds.add(o.getOriginId());
                else if ("return".equals(o.getOriginType())) returnOriginIds.add(o.getOriginId());
            }
            if (o.getSupplierId() != null) supplierIds.add(o.getSupplierId());
        }

        // ── 출처 PO / 반품 OB 일괄 조회 ──
        Map<UUID, String> originNoById = new HashMap<>();
        if (!purchaseOriginIds.isEmpty()) {
            for (ErpPurchaseOrders po : erpPurchaseOrderRepository.findAllById(purchaseOriginIds)) {
                originNoById.put(po.getId(), po.getPoNo());
            }
        }
        if (!returnOriginIds.isEmpty()) {
            for (OutboundOrders ob : outboundOrderRepository.findAllById(returnOriginIds)) {
                originNoById.put(ob.getId(), ob.getOrderNo());
            }
        }

        // ── 창고/협력사/담당자 이름 캐싱 (Master/Account 호출, 단건 API 만 있어 loop) ──
        Map<UUID, String> warehouseNameCache = new HashMap<>();
        for (UUID wid : warehouseIds) warehouseNameCache.put(wid, resolveWarehouseName(wid, clientId));

        Map<UUID, String> supplierNameCache = new HashMap<>();
        for (UUID sid : supplierIds) supplierNameCache.put(sid, resolveSupplierName(sid, clientId));

        Map<UUID, String> userNameCache = new HashMap<>();
        for (UUID uid : userIds) userNameCache.put(uid, resolveUserName(uid, requesterId));

        // ── DTO 빌드 ──
        return page.map(r -> {
            InboundOrders o = orderById.get(r.getInboundOrderId());
            String orderNo = o != null ? o.getOrderNo() : null;
            String oType = o != null ? o.getOriginType() : null;
            UUID oId = o != null ? o.getOriginId() : null;
            String oNo = oId != null ? originNoById.get(oId) : null;
            UUID supplierId = o != null ? o.getSupplierId() : null;

            return InboundReceiptListItemResDto.builder()
                    .id(r.getId())
                    .receiptNo(r.getReceiptNo())
                    .receivedAt(r.getReceivedAt())
                    .createdAt(r.getCreatedAt())
                    .inboundOrderId(r.getInboundOrderId())
                    .orderNo(orderNo)
                    .originType(oType)
                    .originId(oId)
                    .originNo(oNo)
                    .warehouseId(r.getWarehouseId())
                    .warehouseName(warehouseNameCache.get(r.getWarehouseId()))
                    .supplierId(supplierId)
                    .supplierName(supplierId != null ? supplierNameCache.get(supplierId) : null)
                    .receivedBy(r.getReceivedBy())
                    .receivedByName(userNameCache.get(r.getReceivedBy()))
                    .build();
        });
    }

    /**
     * 7. 입고 지시서 승인
     * draft → approved 상태 변경
     */
    @Transactional
    public void approveInboundOrder(UUID orderId, UUID clientId, UUID userId) {
        // 1. 지시서 조회
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        // 2. 같은 회사인지 검증
        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 승인할 수 있습니다.");
        }

        // 3. draft 상태인지 검증
        if (order.getStatus() != InboundOrderStatus.draft) {
            throw new IllegalArgumentException("초안(draft) 상태의 지시서만 승인할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        // 4. 승인 처리
        UUID assignedTo = workAssignmentService.assign(
                WorkTaskType.INBOUND_INSPECTION, clientId, userId, order.getWarehouseId(), null);
        order.approve(userId, assignedTo);

        // 5. Elasticsearch 색인 갱신
        inboundOrderSearchService.index(order.getId());

        // [Kafka] 입고지시서 승인 이벤트 발행
        // - 입고예정재고(incomingQty) 증가
        // - "곧 들어올 재고"를 확인할 수 있게 됨
        List<InboundOrderItems> approvedItems = inboundOrderItemRepository.findByInboundOrderId(orderId);
        List<InboundStockEvent.Item> eventItems = new ArrayList<>();
        for (InboundOrderItems item : approvedItems) {
            eventItems.add(InboundStockEvent.Item.builder()
                    .productId(item.getProductId())
                    .locationId(null)  // 승인 시점에는 적치 위치 미정
                    .qty(item.getOrderedQty())
                    .build());
        }
        inboundEventPublisher.publishApproved(InboundStockEvent.builder()
                .clientId(clientId)
                .warehouseId(order.getWarehouseId())
                .refId(orderId)
                .userId(userId)
                .originType(order.getOriginType())
                .items(eventItems)
                .build());

        // 같은 회사 관리자에게 승인 알림 push (목록 + 상세)
        WorkEventMessage approvedMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("APPROVED")
                .clientId(clientId)
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, approvedMsg);
        webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + orderId, approvedMsg);
        workerAssignmentRefreshPublisher.publishRefresh("inbound", clientId, assignedTo, orderId, order.getOrderNo());

        // 입고지시서 PDF 발행 요청
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.INBOUND_ORDER,
                order.getId(),
                order.getOrderNo(),
                clientId,
                userId
        ));
    }

    /**
     * 7-1. 입고지시서 취소 (출고 취소와 동일 정책)
     *  - draft   취소: DB 상태만 변경, 재고 영향 없음
     *  - approved 취소: incomingQty 원복 위해 Kafka 이벤트 발행 (inbound.cancelled)
     *  - received 이후: 검수/적치 시작했으므로 취소 불가
     */
    @Transactional
    public void cancelInboundOrder(UUID orderId, UUID userId, UUID clientId) {
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 취소할 수 있습니다.");
        }

        if (order.getStatus() != InboundOrderStatus.draft
                && order.getStatus() != InboundOrderStatus.approved) {
            throw new IllegalArgumentException(
                    "초안 또는 승인 상태의 지시서만 취소할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        // approved 취소 시에만 Kafka 발행 — InventoryEventConsumer 가 incomingQty 차감
        // draft 취소는 재고 변동 없으므로 발행 안 함
        if (order.getStatus() == InboundOrderStatus.approved) {
            List<InboundOrderItems> items = inboundOrderItemRepository.findByInboundOrderId(orderId);
            List<InboundStockEvent.Item> eventItems = new ArrayList<>();
            for (InboundOrderItems item : items) {
                eventItems.add(InboundStockEvent.Item.builder()
                        .productId(item.getProductId())
                        .locationId(null)
                        .qty(item.getOrderedQty())
                        .build());
            }
            inboundEventPublisher.publishCancelled(InboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(order.getWarehouseId())
                    .refId(orderId)
                    .userId(userId)
                    .originType(order.getOriginType())
                    .items(eventItems)
                    .build());
        }

        order.cancel();
        inboundOrderRepository.save(order);
        inboundOrderSearchService.index(order.getId());

        // 같은 회사 관리자에게 취소 알림 push (목록 + 상세)
        WorkEventMessage cancelledMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("CANCELLED")
                .clientId(clientId)
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, cancelledMsg);
        webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + orderId, cancelledMsg);
    }

    /**
     * 8-1. 입고 확정 (수량 검수)
     * approved → received 상태 변경
     * 수량/불량/LOT 처리 후 자동 적치 추천으로 적치 지시서까지 생성
     *
     * 재고 변동:
     *  - 정상품: incomingQty ↓ / pendingQty ↑  (inbound.inspected 이벤트)
     *  - 불량품: pendingQty ↓ / defectQty ↑    (inbound.defect   이벤트)
     *  검수 시점에는 적치 위치가 미정이므로 locationId=null 로 발행한다.
     */
    @Transactional
    public List<InboundOrderItemResDto> receiveInbound(UUID orderId, ReceiveReqDto dto,
                                                        UUID clientId, UUID userId) {
        // 1. 지시서 조회 및 검증
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 처리할 수 있습니다.");
        }

        // 단일 검수 정책: 승인 후 한 번에 종결. 부족/불량은 partial 로 마감.
        if (order.getStatus() != InboundOrderStatus.approved) {
            throw new IllegalArgumentException("승인된 지시서만 검수할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        // 2. 입고 전표(receipt) 생성
        String receiptNo = numberingUtil.generateReceiptNo();
        InboundReceipts receipt = InboundReceipts.create(order.getClientId(), orderId, order.getWarehouseId(), userId, receiptNo);
        inboundReceiptRepository.save(receipt);

        // 3. 각 품목별 수량 검수 + 전표 품목 생성 + 재고 이벤트 누적
        List<InboundStockEvent.Item> normalItems = new ArrayList<>();
        List<InboundStockEvent.Item> defectItems = new ArrayList<>();

        for (ReceiveRowDto row : dto.getRows()) {
            InboundOrderItems orderItem = inboundOrderItemRepository.findById(row.getItemId())
                    .orElseThrow(() -> new NoSuchElementException("입고 지시서 품목을 찾을 수 없습니다."));

            int defective = row.getDefective() != null ? row.getDefective() : 0;
            orderItem.inspect(row.getQty(), defective);

            // 정상품 검수 기록
            inboundReceiptItemRepository.save(InboundReceiptItems.createNormal(
                    receipt.getId(), orderItem.getId(), orderItem.getProductId(),
                    row.getQty(), row.getLotNo(), userId));

            // 불량품 검수 기록
            if (defective > 0) {
                inboundReceiptItemRepository.save(InboundReceiptItems.createDefect(
                        receipt.getId(), orderItem.getId(), orderItem.getProductId(),
                        defective, row.getLotNo(), userId));
            }

            // 재고 이벤트 — 적치 전이므로 null-location 행에 pending 으로 쌓는다.
            if (row.getQty() != null && row.getQty() > 0) {
                normalItems.add(InboundStockEvent.Item.builder()
                        .productId(orderItem.getProductId())
                        .locationId(null)
                        .qty(row.getQty())
                        .build());
            }
            if (defective > 0) {
                defectItems.add(InboundStockEvent.Item.builder()
                        .productId(orderItem.getProductId())
                        .locationId(null)
                        .qty(defective)
                        .build());
            }
        }

        // 4. 전체 품목 검수 완료 확인 (부분 검수 방지)
        List<InboundOrderItems> allItems = inboundOrderItemRepository.findByInboundOrderId(orderId);
        boolean allDone = allItems.stream()
                .allMatch(i -> i.getStatus() == InboundOrderItemStatus.completed
                            || i.getStatus() == InboundOrderItemStatus.shortage);
        if (!allDone) {
            long pending = allItems.stream()
                    .filter(i -> i.getStatus() == InboundOrderItemStatus.pending)
                    .count();
            throw new IllegalStateException("아직 검수되지 않은 품목이 있습니다: " + pending + "건");
        }

        // 5. 상태 변경: approved → received
        order.receive();

        // 5-1. 적치 지시서 자동 생성 (검수자 → assignedTo)
        //   - 위치: PlacementSuggestionService 자동 추천 (첫 번째 결과 사용)
        //   - 정상 수량 → NORMAL purpose (카테고리/협력사 매칭)
        //   - 불량 수량 → DEFECT purpose (같은 창고의 DEFECT zone, is_defect=true)
        //   - 같은 PlacementOrder 에 합쳐서 저장 (정상·불량 구분은 PlacementItems.isDefect 로)
        //   - 중복 생성 방지: 이미 해당 inbound 에 placement_order 가 있으면 재사용
        PlacementOrders placementOrder = placementOrderRepository
                .findByInboundOrderId(orderId)
                .stream().findFirst().orElse(null);
        boolean anyPlacement = (placementOrder != null);

        // [one-SKU-per-location] 같은 receiveInbound 배치 내에서 이미 배정된 location 의 소유 SKU/남은 용량 추적.
        // suggest 는 품목별로 독립 호출되므로 동일 카테고리·협력사 상품들은 모두 같은 빈 위치를 1순위로 받는다.
        // DB 에는 아직 반영 전이라 DB 스냅샷만 보는 검증으로는 막을 수 없다.
        Map<UUID, UUID> batchLocOwner = new HashMap<>();      // locationId → 최초 점유 productId
        Map<UUID, Integer> batchLocRemain = new HashMap<>();  // locationId → 남은 배정 가능 용량
        UUID placementAssignedTo = null;

        for (ReceiveRowDto row : dto.getRows()) {
            InboundOrderItems orderItem = inboundOrderItemRepository.findById(row.getItemId())
                    .orElseThrow(() -> new NoSuchElementException("입고 지시서 품목을 찾을 수 없습니다."));

            int normalQty = row.getQty() != null ? row.getQty() : 0;
            int defectQty = row.getDefective() != null ? row.getDefective() : 0;

            // 정상 수량 적치 — 추천 위치들에 순차 분할, 못 채운 잔여는 location=null("미배정")로 저장
            if (normalQty > 0) {
                if (placementOrder == null) {
                    placementOrder = PlacementOrders.create(
                            orderId, clientId, order.getWarehouseId(), numberingUtil.generatePlacementNo());
                    if (placementAssignedTo == null) {
                        placementAssignedTo = workAssignmentService.assign(
                                WorkTaskType.PLACEMENT, clientId, userId, order.getWarehouseId(), null);
                    }
                    placementOrder.setAssignedTo(placementAssignedTo);
                    placementOrderRepository.save(placementOrder);
                }
                List<SuggestLocationResDto> suggestions = placementSuggestionService.suggest(
                        orderItem.getProductId(), order.getWarehouseId(), normalQty, clientId,
                        com.beyond.wbs.inventory.service.PlacementPurpose.NORMAL);
                int remaining = normalQty;
                for (SuggestLocationResDto s : suggestions) {
                    if (remaining <= 0) break;
                    // 배치 내 다른 상품이 먼저 잡은 위치는 스킵 (one-SKU-per-location)
                    UUID locOwner = batchLocOwner.get(s.getLocationId());
                    if (locOwner != null && !locOwner.equals(orderItem.getProductId())) continue;
                    int cap = batchLocRemain.getOrDefault(s.getLocationId(),
                            s.getRemainCapacity() != null ? s.getRemainCapacity() : remaining);
                    int putQty = Math.min(remaining, cap);
                    if (putQty <= 0) continue;
                    placementItemRepository.save(PlacementItems.create(
                            placementOrder.getId(), orderItem.getId(), orderItem.getProductId(),
                            s.getLocationId(), putQty, row.getLotNo(), false));
                    batchLocOwner.putIfAbsent(s.getLocationId(), orderItem.getProductId());
                    batchLocRemain.put(s.getLocationId(), cap - putQty);
                    remaining -= putQty;
                }
                if (remaining > 0) {
                    // 배정 못한 잔여 → location=null 로 보관. 관리자가 나중에 위치 지정.
                    log.warn("[자동 적치-정상] 용량 부족 {}개 미배정 저장 — productId={}",
                            remaining, orderItem.getProductId());
                    placementItemRepository.save(PlacementItems.create(
                            placementOrder.getId(), orderItem.getId(), orderItem.getProductId(),
                            null, remaining, row.getLotNo(), false));
                }
                anyPlacement = true;
            }

            // 불량 수량 적치 — DEFECT zone 에서 분할, 못 채운 잔여는 location=null 로 저장
            // 검수 때 확정된 불량은 작업자가 DEFECT 존까지 옮겨놓도록 적치 지시서에 포함.
            // 적치 완료(completePlacementItem)에서 relocateDefect 가 defect@null → defect@DEFECT위치 로 이동시킴.
            if (defectQty > 0) {
                if (placementOrder == null) {
                    placementOrder = PlacementOrders.create(
                            orderId, clientId, order.getWarehouseId(), numberingUtil.generatePlacementNo());
                    if (placementAssignedTo == null) {
                        placementAssignedTo = workAssignmentService.assign(
                                WorkTaskType.PLACEMENT, clientId, userId, order.getWarehouseId(), null);
                    }
                    placementOrder.setAssignedTo(placementAssignedTo);
                    placementOrderRepository.save(placementOrder);
                }
                List<SuggestLocationResDto> defectSuggestions = placementSuggestionService.suggest(
                        orderItem.getProductId(), order.getWarehouseId(), defectQty, clientId,
                        com.beyond.wbs.inventory.service.PlacementPurpose.DEFECT);
                int remaining = defectQty;
                for (SuggestLocationResDto s : defectSuggestions) {
                    if (remaining <= 0) break;
                    UUID locOwner = batchLocOwner.get(s.getLocationId());
                    if (locOwner != null && !locOwner.equals(orderItem.getProductId())) continue;
                    int cap = batchLocRemain.getOrDefault(s.getLocationId(),
                            s.getRemainCapacity() != null ? s.getRemainCapacity() : remaining);
                    int putQty = Math.min(remaining, cap);
                    if (putQty <= 0) continue;
                    placementItemRepository.save(PlacementItems.create(
                            placementOrder.getId(), orderItem.getId(), orderItem.getProductId(),
                            s.getLocationId(), putQty, row.getLotNo(), true));
                    batchLocOwner.putIfAbsent(s.getLocationId(), orderItem.getProductId());
                    batchLocRemain.put(s.getLocationId(), cap - putQty);
                    remaining -= putQty;
                }
                if (remaining > 0) {
                    log.warn("[자동 적치-불량] 용량 부족 {}개 미배정 저장 — productId={}",
                            remaining, orderItem.getProductId());
                    placementItemRepository.save(PlacementItems.create(
                            placementOrder.getId(), orderItem.getId(), orderItem.getProductId(),
                            null, remaining, row.getLotNo(), true));
                }
                anyPlacement = true;
            }
        }

        if (anyPlacement) {
            order.startPlacing();
            log.info("[자동 적치] 생성 완료 — inboundOrderId={}, placementNo={}",
                    orderId, placementOrder.getPlacementNo());
        } else {
            log.warn("[자동 적치] 생성된 적치 지시서 없음 — inboundOrderId={}", orderId);
        }

        // 5. 재고 이벤트 발행 — 단일 토픽(inbound.inspected)으로 한 번만 발행.
        //   - items       : 정상+불량 총 수량 (pending 증가 대상)
        //   - defectItems : 그중 불량분 (consumer 가 pending→defect 로 이동)
        //   이렇게 하나의 이벤트에 담으면 consumer 가 같은 트랜잭션 안에서 순서대로 처리 가능.
        //   (기존엔 inspected/defect 두 토픽으로 분리 발행 → 토픽 간 순서 보장 없음 → race로 defect 유실 문제)
        if (!defectItems.isEmpty()) {
            // defect 수량만큼 먼저 incoming→pending 으로 끌어올린 뒤 defect 로 이동시킨다.
            for (InboundStockEvent.Item d : defectItems) {
                normalItems.add(InboundStockEvent.Item.builder()
                        .productId(d.getProductId())
                        .locationId(null)
                        .qty(d.getQty())
                        .build());
            }
        }

        if (!normalItems.isEmpty() || !defectItems.isEmpty()) {
            inboundEventPublisher.publishInspected(InboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(order.getWarehouseId())
                    .refId(orderId)
                    .userId(userId)
                    .originType(order.getOriginType())
                    .items(normalItems)
                    .defectItems(defectItems.isEmpty() ? null : defectItems)
                    .build());
        }

        // 입고전표 PDF 발행 요청 (검수 완료된 receipt 기준)
        applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                InstructionDocumentType.INBOUND_RECEIPT,
                receipt.getId(),
                receipt.getReceiptNo(),
                clientId,
                userId
        ));

        // 적치지시서 PDF 발행 요청 (자동 생성된 경우만)
        if (anyPlacement && placementOrder != null) {
            applicationEventPublisher.publishEvent(new InstructionIssueRequested(
                    InstructionDocumentType.PLACEMENT_ORDER,
                    placementOrder.getId(),
                    placementOrder.getPlacementNo(),
                    clientId,
                    userId
            ));
            workerAssignmentRefreshPublisher.publishRefresh(
                    "placement",
                    clientId,
                    placementOrder.getAssignedTo(),
                    placementOrder.getId(),
                    placementOrder.getPlacementNo());
        }

        // 6. 응답 (allItems 는 위에서 이미 조회됨)
        Set<UUID> productIdsForDto = new HashSet<>();
        for (InboundOrderItems item : allItems) productIdsForDto.add(item.getProductId());
        Map<UUID, ProductResDto> products = fetchProducts(productIdsForDto, clientId);

        List<InboundOrderItemResDto> result = new ArrayList<>();
        for (InboundOrderItems item : allItems) {
            result.add(InboundOrderItemResDto.fromEntity(item, products.get(item.getProductId())));
        }

        // 7. 웹 관리자에게 검수 완료 알림 push — 목록 + 상세 둘 다
        // (목록: status 갱신, 상세: 품목 status 갱신)
        WorkEventMessage receivedMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("RECEIVED")
                .clientId(clientId)
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId, receivedMsg);
        webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + order.getId(), receivedMsg);

        return result;
    }


    /**
     * 9. 특정 지시서의 적치 지시서 조회
     */
    public List<PlacementOrderResDto> getPlacementOrders(UUID orderId, UUID clientId) {
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 조회할 수 있습니다.");
        }

        List<PlacementOrders> placementOrders = placementOrderRepository.findByInboundOrderId(orderId);

        // 전 지시서의 productId / locationId 를 모아 Master 정보를 한 번에 조회
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        Map<UUID, List<PlacementItems>> itemsByPo = new HashMap<>();
        for (PlacementOrders po : placementOrders) {
            List<PlacementItems> items = placementItemRepository.findByPlacementOrderId(po.getId());
            itemsByPo.put(po.getId(), items);
            for (PlacementItems item : items) {
                productIds.add(item.getProductId());
                locationIds.add(item.getLocationId());
            }
        }
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);
        Map<UUID, String> locationCodes = new HashMap<>();
        Map<UUID, RackResDto> rackByLocation = fetchRacksByLocationIds(locationIds, clientId, locationCodes);

        List<PlacementOrderResDto> result = new ArrayList<>();
        for (PlacementOrders po : placementOrders) {
            List<PlacementItems> items = itemsByPo.get(po.getId());
            int placedCount = (int) items.stream().filter(PlacementItems::isPlaced).count();

            List<PlacementItemResDto> itemDtos = new ArrayList<>();
            int seq = 1;
            for (PlacementItems item : items) {
                RackResDto rack = rackByLocation.get(item.getLocationId());
                UUID rackId = rack != null ? rack.getId() : null;
                String rackCode = rack != null ? rack.getCode() : null;
                String zoneName = rack != null ? rack.getZoneName() : null;
                String locationCode = locationCodes.get(item.getLocationId());
                PlacementItemResDto dto = PlacementItemResDto.fromEntity(
                        item, seq++, zoneName, rackId, rackCode, locationCode,
                        orderId, order.getWarehouseId(), order.getOrderNo(), po.getPlacementNo(),
                        products.get(item.getProductId()));
                dto.setAssignedTo(po.getAssignedTo());
                dto.setUnassignedReason(diagnoseUnassignedReason(item, po, products.get(item.getProductId()), clientId));
                itemDtos.add(dto);
            }

            result.add(PlacementOrderResDto.fromEntity(po, order.getOrderNo(),
                    items.size(), placedCount, itemDtos));
        }

        return result;
    }

    /**
     * 9-A. 적치지시서 내 상품 라인 — productIds 로 필터링.
     *
     * 적치지시서 상세 화면에서 ProductSearchFilterModal 결과(productIds)로
     * 라인을 좁힐 때 사용. productIds 가 null/빈 리스트면 전체 라인 반환.
     */
    public List<PlacementItemResDto> getPlacementItemsByProductIds(UUID placementOrderId,
                                                                    List<UUID> productIds,
                                                                    UUID clientId) {
        PlacementOrders placementOrder = placementOrderRepository.findById(placementOrderId)
                .orElseThrow(() -> new NoSuchElementException("적치지시서를 찾을 수 없습니다."));
        if (!placementOrder.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 지시서만 조회할 수 있습니다.");
        }

        InboundOrders inboundOrder = inboundOrderRepository.findById(placementOrder.getInboundOrderId())
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        List<PlacementItems> items = (productIds == null || productIds.isEmpty())
                ? placementItemRepository.findByPlacementOrderId(placementOrderId)
                : placementItemRepository.findByPlacementOrderIdAndProductIdIn(placementOrderId, productIds);

        Set<UUID> ids = new HashSet<>();
        Set<UUID> locIds = new HashSet<>();
        for (PlacementItems item : items) {
            ids.add(item.getProductId());
            locIds.add(item.getLocationId());
        }
        Map<UUID, ProductResDto> products = fetchProducts(ids, clientId);
        Map<UUID, String> locationCodes = new HashMap<>();
        Map<UUID, RackResDto> rackByLocation = fetchRacksByLocationIds(locIds, clientId, locationCodes);

        List<PlacementItemResDto> result = new ArrayList<>();
        int seq = 1;
        for (PlacementItems item : items) {
            RackResDto rack = rackByLocation.get(item.getLocationId());
            UUID rackId = rack != null ? rack.getId() : null;
            String rackCode = rack != null ? rack.getCode() : null;
            String zoneName = rack != null ? rack.getZoneName() : null;
            String locationCode = locationCodes.get(item.getLocationId());
            PlacementItemResDto dto = PlacementItemResDto.fromEntity(
                    item, seq++, zoneName, rackId, rackCode, locationCode,
                    inboundOrder.getId(), inboundOrder.getWarehouseId(),
                    inboundOrder.getOrderNo(), placementOrder.getPlacementNo(),
                    products.get(item.getProductId()));
            dto.setAssignedTo(placementOrder.getAssignedTo());
            result.add(dto);
        }
        return result;
    }

    /**
     * 9-1. 모바일 적치 작업 목록 (작업자 기준 페이징)
     */
    public Page<PlacementOrderResDto> getPlacementOrderList(
            UUID clientId,
            UUID assignedTo,
            UUID warehouseId,
            PlacementOrderStatus status,
            Pageable pageable) {
        return getPlacementOrderList(clientId, assignedTo, warehouseId, status, null, pageable);
    }

    /**
     * 9-1-A. 적치지시서 목록 + productIds 멀티필터.
     * productIds 가 있으면 EXISTS 서브쿼리로 매칭, 없으면 기존 쿼리 그대로.
     */
    public Page<PlacementOrderResDto> getPlacementOrderList(
            UUID clientId,
            UUID assignedTo,
            UUID warehouseId,
            PlacementOrderStatus status,
            List<UUID> productIds,
            Pageable pageable) {
        Page<PlacementOrders> orders = (productIds != null && !productIds.isEmpty())
                ? placementOrderRepository.findByConditionsAndProductIds(
                        clientId, assignedTo, warehouseId, status, productIds, pageable)
                : placementOrderRepository.findByConditions(
                        clientId, assignedTo, warehouseId, status, pageable);

        Set<UUID> inboundOrderIds = new HashSet<>();
        for (PlacementOrders po : orders.getContent()) {
            inboundOrderIds.add(po.getInboundOrderId());
        }

        Map<UUID, String> orderNoMap = new HashMap<>();
        for (UUID iid : inboundOrderIds) {
            inboundOrderRepository.findById(iid)
                    .ifPresent(io -> orderNoMap.put(iid, io.getOrderNo()));
        }

        return orders.map(po -> {
            String orderNo = orderNoMap.getOrDefault(po.getInboundOrderId(), "");

            List<PlacementItems> items = placementItemRepository.findByPlacementOrderId(po.getId());
            int totalItems = items.size();
            int placedItems = (int) items.stream().filter(PlacementItems::isPlaced).count();

            return PlacementOrderResDto.fromEntity(po, orderNo, totalItems, placedItems, null);
        });
    }

    /**
     * 9-2. 적치 지시서 단건 조회 (적치 지시서 ID 기준)
     */
    public PlacementOrderResDto getPlacementOrder(UUID placementOrderId, UUID clientId) {
        PlacementOrders po = placementOrderRepository.findById(placementOrderId)
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));

        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 지시서만 조회할 수 있습니다.");
        }

        InboundOrders order = inboundOrderRepository.findById(po.getInboundOrderId())
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        List<PlacementItems> items = placementItemRepository.findByPlacementOrderId(po.getId());
        int placedCount = (int) items.stream().filter(PlacementItems::isPlaced).count();

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        for (PlacementItems item : items) {
            productIds.add(item.getProductId());
            locationIds.add(item.getLocationId());
        }
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);
        Map<UUID, String> locationCodes = new HashMap<>();
        Map<UUID, RackResDto> rackByLocation = fetchRacksByLocationIds(locationIds, clientId, locationCodes);

        List<PlacementItemResDto> itemDtos = new ArrayList<>();
        int seq = 1;
        for (PlacementItems item : items) {
            RackResDto rack = rackByLocation.get(item.getLocationId());
            UUID rackId = rack != null ? rack.getId() : null;
            String rackCode = rack != null ? rack.getCode() : null;
            String zoneName = rack != null ? rack.getZoneName() : null;
            String locationCode = locationCodes.get(item.getLocationId());
            PlacementItemResDto dto = PlacementItemResDto.fromEntity(
                    item, seq++, zoneName, rackId, rackCode, locationCode,
                    order.getId(), order.getWarehouseId(), order.getOrderNo(), po.getPlacementNo(),
                    products.get(item.getProductId()));
            dto.setAssignedTo(po.getAssignedTo());
            dto.setUnassignedReason(diagnoseUnassignedReason(item, po, products.get(item.getProductId()), clientId));
            itemDtos.add(dto);
        }

        return PlacementOrderResDto.fromEntity(po, order.getOrderNo(),
                items.size(), placedCount, itemDtos);
    }

    /**
     * 10. 전체 적치 대기 목록 조회 (현장 작업자용)
     */
    public List<PlacementItemResDto> getPendingPlacements(UUID clientId, String status) {
        List<PlacementItems> items;

        if ("pending".equals(status)) {
            items = placementItemRepository.findPendingByClientId(clientId);
        } else {
            // 전체 조회
            List<PlacementOrders> allOrders = placementOrderRepository.findByClientId(clientId);
            items = new ArrayList<>();
            for (PlacementOrders po : allOrders) {
                items.addAll(placementItemRepository.findByPlacementOrderId(po.getId()));
            }

            if ("completed".equals(status)) {
                items = items.stream().filter(PlacementItems::isPlaced).toList();
            }
        }

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        for (PlacementItems item : items) {
            productIds.add(item.getProductId());
            locationIds.add(item.getLocationId());
        }
        Map<UUID, ProductResDto> products = fetchProducts(productIds, clientId);
        Map<UUID, String> locationCodes = new HashMap<>();
        Map<UUID, RackResDto> rackByLocation = fetchRacksByLocationIds(locationIds, clientId, locationCodes);

        List<PlacementItemResDto> result = new ArrayList<>();
        int seq = 1;
        for (PlacementItems item : items) {
            // placementOrder → inboundOrder 정보 조회
            PlacementOrders po = placementOrderRepository.findById(item.getPlacementOrderId()).orElse(null);
            UUID orderId = po != null ? po.getInboundOrderId() : null;
            String orderNo = "";
            String placementNo = po != null ? po.getPlacementNo() : "";
            if (orderId != null) {
                InboundOrders order = inboundOrderRepository.findById(orderId).orElse(null);
                orderNo = order != null ? order.getOrderNo() : "";
            }

            RackResDto rack = rackByLocation.get(item.getLocationId());
            UUID rackId = rack != null ? rack.getId() : null;
            String rackCode = rack != null ? rack.getCode() : null;
            String zoneName = rack != null ? rack.getZoneName() : null;
            String locationCode = locationCodes.get(item.getLocationId());
            PlacementItemResDto dto = PlacementItemResDto.fromEntity(
                    item, seq++, zoneName, rackId, rackCode, locationCode,
                    orderId, po != null ? po.getWarehouseId() : null, orderNo, placementNo,
                    products.get(item.getProductId()));
            dto.setAssignedTo(po != null ? po.getAssignedTo() : null);
            dto.setUnassignedReason(diagnoseUnassignedReason(item, po, products.get(item.getProductId()), clientId));
            result.add(dto);
        }

        return result;
    }

    /**
     * 11-1. 적치 아이템에 위치 지정 / 재지정 (관리자 수동)
     *
     * 두 가지 시나리오 모두 지원:
     *  - 미배정(location_id=null) 항목에 처음 위치 지정
     *  - 이미 지정된 위치를 다른 위치로 변경 (race condition 으로 추천 위치가 점유당한 경우 등)
     *
     * 적치 완료(is_placed=true) 된 항목은 변경 불가 — 재고 이동은 별도 흐름.
     * is_placed 는 바꾸지 않음 (별도로 completePlacementItem 호출해야 실제 적치 반영).
     */
    @Transactional
    public void assignPlacementLocation(UUID itemId, UUID locationId, UUID clientId) {
        PlacementItems item = placementItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("적치 항목을 찾을 수 없습니다."));
        PlacementOrders po = placementOrderRepository.findById(item.getPlacementOrderId())
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));
        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 항목만 처리할 수 있습니다.");
        }
        if (item.isPlaced()) {
            throw new IllegalArgumentException("이미 적치 완료된 항목입니다.");
        }

        // zone_type 검증 — 입고/출고 대기장(staging) 은 적치 대상이 아님.
        // 불량 적치 시 DEFECT zone 만 허용. 정상 적치 시 STORAGE/DEFECT 허용 (DEFECT 는 케이스에 따라).
        LocationResDto location = masterServiceClient.getLocation(locationId, clientId.toString());
        if (location == null) {
            throw new IllegalArgumentException("로케이션을 찾을 수 없습니다.");
        }
        String zoneType = location.getZoneType();
        if ("INBOUND".equals(zoneType) || "OUTBOUND".equals(zoneType)) {
            throw new IllegalArgumentException("입고/출고 대기장에는 적치할 수 없습니다. 보관 구역을 선택하세요.");
        }
        if (item.isDefect() && !"DEFECT".equals(zoneType)) {
            throw new IllegalArgumentException("불량품은 불량존(DEFECT)에만 적치할 수 있습니다.");
        }

        // 협력사 매칭은 수동 지정에서는 검증하지 않음 — 미배정 풀이 협력사 불일치 케이스의
        // 안전망 역할을 해야 하므로, 자동 추천(PlacementSuggestionService)에서만 강제한다.
        // 정책 가이드는 추천 응답에 노출되며, 추천 외 위치 선택은 의도된 override 로 본다.

        // one-SKU-per-location: 이미 다른 상품이 차지/예약한 위치는 배정 거부.
        // (1) 실제 inventory 에 다른 productId 가 점유 중인지
        UUID warehouseId = po.getWarehouseId();
        List<Inventory> existingInv = inventoryRepository.findByWarehouseIdAndLocationId(warehouseId, locationId);
        for (Inventory inv : existingInv) {
            if (inv.getProductId() != null
                    && !inv.getProductId().equals(item.getProductId())
                    && inv.getTotalQty() != null && inv.getTotalQty() > 0) {
                throw new IllegalArgumentException(buildOccupiedMessage(
                        location.getFloorNo(), inv.getProductId(), inv.getTotalQty(), clientId.toString()));
            }
        }
        // (2) 다른 미적치 PlacementItem 이 이 위치를 이미 예약했는지
        List<PlacementItems> reserved = placementItemRepository.findByLocationIdAndIsPlacedFalse(locationId);
        for (PlacementItems other : reserved) {
            if (!other.getId().equals(item.getId())
                    && other.getProductId() != null
                    && !other.getProductId().equals(item.getProductId())) {
                throw new IllegalArgumentException(buildReservedMessage(
                        location.getFloorNo(), other.getProductId(), other.getQty(), clientId.toString()));
            }
        }

        item.setLocationId(locationId);
        placementItemRepository.save(item);
    }

    /**
     * 11-1-2. 미배정 적치 아이템을 여러 위치에 분할 지정 (관리자 수동)
     *
     * 자동 적치가 한 위치에 다 못 채워서 location=null 로 남은 큰 수량을
     * 관리자가 여러 위치에 나눠서 배정한다.
     *
     * 동작:
     *  - 분할 배정 목록(assignments) 의 수량 합계 = 원본 항목 수량 검증
     *  - 각 분할 배정마다 단건 지정과 동일 검증 (zone_type / one-SKU / capacity)
     *  - 수용량 초과 시 422 (LocationCapacityExceededException)
     *  - 첫 번째 배정 → 원본 PlacementItem 의 위치/수량을 갱신
     *  - 나머지 배정 → 같은 적치지시서/입고품목/상품/불량여부/LOT 로 신규 PlacementItem 생성
     *  - 모두 isPlaced=false (실제 적치 반영은 completePlacementItem 에서)
     */
    @Transactional
    public void splitAssignPlacementLocation(UUID itemId, SplitAssignReqDto body, UUID clientId) {
        // 분할 배정 목록 — "어디에 얼마씩 담을지" 한 줄씩 들어옴
        // 예: [{locA, 50}, {locB, 70}] = "A위치에 50, B위치에 70"
        if (body == null || body.getAssignments() == null || body.getAssignments().isEmpty()) {
            throw new IllegalArgumentException("분할 배정 목록(assignments)은 1건 이상이어야 합니다.");
        }
        List<SplitAssignReqDto.Assignment> assignments = body.getAssignments();

        // 1) 원본 미배정 항목 + 적치지시서 조회 / 권한·상태 검증
        PlacementItems item = placementItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("적치 항목을 찾을 수 없습니다."));
        PlacementOrders po = placementOrderRepository.findById(item.getPlacementOrderId())
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));
        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 항목만 처리할 수 있습니다.");
        }
        if (item.isPlaced()) {
            throw new IllegalArgumentException("이미 적치 완료된 항목입니다.");
        }
        if (item.getLocationId() != null) {
            throw new IllegalArgumentException("이미 위치가 지정된 항목입니다.");
        }

        // 2) 분할 합계가 원본 수량과 같은지 검증 (각 항목 자체 검증도 같이)
        int totalAssignQty = 0;  // 분할 배정 수량의 합계
        for (SplitAssignReqDto.Assignment a : assignments) {
            if (a.getLocationId() == null) {
                throw new IllegalArgumentException("각 분할 배정의 locationId(위치)는 필수입니다.");
            }
            if (a.getQty() == null || a.getQty() <= 0) {
                throw new IllegalArgumentException("각 분할 배정의 qty(수량)는 양수여야 합니다.");
            }
            totalAssignQty += a.getQty();
        }
        if (totalAssignQty != item.getQty()) {
            throw new IllegalArgumentException(
                    "분할 합계가 원본 수량과 일치하지 않습니다. 원본=" + item.getQty()
                            + ", 분할합계=" + totalAssignQty);
        }

        // 3) 각 분할 배정에 대해 단건 검증 동일 적용
        //    어느 하나라도 실패하면 @Transactional 로 전체 롤백 → 부분 저장 없음.
        UUID warehouseId = po.getWarehouseId();
        String clientStr = clientId.toString();
        for (SplitAssignReqDto.Assignment a : assignments) {
            validateAssignmentForItem(item, warehouseId, a.getLocationId(), a.getQty(), clientStr);
        }

        // 4) 첫 번째 분할 배정 → 원본 PlacementItem 의 위치/수량 갱신
        SplitAssignReqDto.Assignment first = assignments.get(0);
        item.setLocationId(first.getLocationId());
        item.setQty(first.getQty());
        placementItemRepository.save(item);

        // 5) 나머지 분할 배정 → 신규 PlacementItem 으로 추가 저장
        //    같은 적치지시서/입고품목/상품/불량여부/LOT 를 그대로 물려받는다.
        for (int i = 1; i < assignments.size(); i++) {
            SplitAssignReqDto.Assignment a = assignments.get(i);
            PlacementItems split = PlacementItems.create(
                    item.getPlacementOrderId(),
                    item.getOrderItemId(),
                    item.getProductId(),
                    a.getLocationId(),
                    a.getQty(),
                    item.getLotNo(),
                    item.isDefect()
            );
            placementItemRepository.save(split);
        }

        log.info("[수동 분할 적치] itemId={} → {}건 분할 (총 {}개)",
                itemId, assignments.size(), totalAssignQty);
    }

    /**
     * 단건/분할 공용 — 한 PlacementItem 을 특정 location 에 qty 만큼 배정해도 되는지 검증.
     *
     * - zone_type: INBOUND/OUTBOUND 거부, 불량이면 DEFECT zone 만 허용
     * - one-SKU-per-location: 다른 상품이 점유/예약 중인 위치 거부
     * - capacity: max_capacity 초과 시 422 (LocationCapacityExceededException)
     */
    private void validateAssignmentForItem(PlacementItems item, UUID warehouseId,
                                            UUID locationId, int qty, String clientStr) {
        LocationResDto location = masterServiceClient.getLocation(locationId, clientStr);
        if (location == null) {
            throw new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + locationId);
        }
        String zoneType = location.getZoneType();
        if ("INBOUND".equals(zoneType) || "OUTBOUND".equals(zoneType)) {
            throw new IllegalArgumentException("입고/출고 대기장에는 적치할 수 없습니다. 보관 구역을 선택하세요.");
        }
        if (item.isDefect() && !"DEFECT".equals(zoneType)) {
            throw new IllegalArgumentException("불량품은 불량존(DEFECT)에만 적치할 수 있습니다.");
        }

        // 협력사 매칭은 분할 지정에서도 검증하지 않음 — 미배정 풀의 안전망 역할을 위해
        // 자동 추천에서만 강제한다 (단건 지정과 동일 정책).

        // (1) 실제 inventory 에 다른 productId 가 점유 중인지
        List<Inventory> existingInv = inventoryRepository.findByWarehouseIdAndLocationId(warehouseId, locationId);
        for (Inventory inv : existingInv) {
            if (inv.getProductId() != null
                    && !inv.getProductId().equals(item.getProductId())
                    && inv.getTotalQty() != null && inv.getTotalQty() > 0) {
                throw new IllegalArgumentException(buildOccupiedMessage(
                        location.getFloorNo(), inv.getProductId(), inv.getTotalQty(), clientStr));
            }
        }
        // (2) 다른 미적치 PlacementItem 이 이 위치를 이미 예약했는지
        List<PlacementItems> reserved = placementItemRepository.findByLocationIdAndIsPlacedFalse(locationId);
        for (PlacementItems other : reserved) {
            if (!other.getId().equals(item.getId())
                    && other.getProductId() != null
                    && !other.getProductId().equals(item.getProductId())) {
                throw new IllegalArgumentException(buildReservedMessage(
                        location.getFloorNo(), other.getProductId(), other.getQty(), clientStr));
            }
        }

        // (3) capacity — max_capacity 초과 시 422
        Integer maxCap = location.getMaxCapacity();
        if (maxCap != null && maxCap > 0) {
            int currentTotal = existingInv.stream()
                    .mapToInt(inv -> inv.getTotalQty() != null ? inv.getTotalQty() : 0)
                    .sum();
            if (currentTotal + qty > maxCap) {
                throw LocationCapacityExceededException.of(currentTotal, maxCap, qty);
            }
        }
    }

    /**
     * 위치 점유 에러 메시지 — 층수 + 어떤 상품이 얼마나 들어있는지.
     * 예: "이 위치(4층)에는 다른 상품(무선 마우스 KB_MS-002, 50개)이 보관 중입니다"
     */
    private String buildOccupiedMessage(Integer floorNo, UUID existingProductId,
                                         Integer existingQty, String clientStr) {
        String productLabel = lookupProductLabel(existingProductId, clientStr);
        int qty = existingQty != null ? existingQty : 0;
        return "이 위치" + floorLabel(floorNo) + "에는 다른 상품("
                + productLabel + ", " + qty + "개)이 보관 중입니다";
    }

    /**
     * 위치 예약(미적치 PlacementItem) 에러 메시지 — 층수 + 어떤 상품이 얼마나 예약 중인지.
     */
    private String buildReservedMessage(Integer floorNo, UUID otherProductId,
                                         Integer otherQty, String clientStr) {
        String productLabel = lookupProductLabel(otherProductId, clientStr);
        int qty = otherQty != null ? otherQty : 0;
        return "이 위치" + floorLabel(floorNo) + "는 다른 적치 품목("
                + productLabel + ", " + qty + "개)이 사용 예정입니다";
    }

    private String floorLabel(Integer floorNo) {
        return (floorNo != null) ? "(" + floorNo + "층)" : "";
    }

    /**
     * 상품을 "이름 SKU" 형태로 라벨링. 조회 실패 시 UUID 그대로 노출.
     */
    private String lookupProductLabel(UUID productId, String clientStr) {
        if (productId == null) return "미지정";
        try {
            ProductResDto p = masterServiceClient.getProduct(productId, clientStr);
            if (p == null) return productId.toString();
            String name = p.getName() != null ? p.getName() : "";
            String sku = p.getSku() != null ? p.getSku() : "";
            if (!name.isEmpty() && !sku.isEmpty()) return name + " " + sku;
            if (!name.isEmpty()) return name;
            if (!sku.isEmpty()) return sku;
            return productId.toString();
        } catch (Exception e) {
            return productId.toString();
        }
    }

    /**
     * 미배정 적치 아이템 기준으로 로케이션을 다시 추천한다.
     * - 현재 placement item 의 productId / qty / isDefect / warehouseId 를 사용
     * - 자동 적치 생성과 동일한 정책을 재사용한다.
     */
    @Transactional(readOnly = true)
    public List<SuggestLocationResDto> suggestPlacementLocations(UUID itemId, UUID clientId) {
        PlacementItems item = placementItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("적치 항목을 찾을 수 없습니다."));
        PlacementOrders po = placementOrderRepository.findById(item.getPlacementOrderId())
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));

        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 항목만 조회할 수 있습니다.");
        }
        if (item.isPlaced()) {
            throw new IllegalArgumentException("이미 적치 완료된 항목입니다.");
        }
        if (item.getLocationId() != null) {
            throw new IllegalArgumentException("이미 위치가 지정된 항목입니다.");
        }

        PlacementPurpose purpose = item.isDefect() ? PlacementPurpose.DEFECT : PlacementPurpose.NORMAL;
        return placementSuggestionService.suggest(
                item.getProductId(),
                po.getWarehouseId(),
                item.getQty(),
                clientId,
                purpose
        );
    }

    /**
     * 11. 개별 적치 아이템 완료 처리
     * 완료 후 해당 적치 지시서 + 입고 지시서 자동 완료 판정
     *
     * 재고 변동: pendingQty ↓ / availableQty ↑  (inbound.placed 이벤트)
     */
    @Transactional
    public void completePlacementItem(UUID itemId, UUID clientId, UUID userId,
                                      Integer defectQty, String defectReason) {
        // 1. 적치 아이템 조회
        PlacementItems item = placementItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("적치 항목을 찾을 수 없습니다."));

        if (item.isPlaced()) {
            throw new IllegalArgumentException("이미 적치 완료된 항목입니다.");
        }

        // 1-1. 미배정(location_id=null) 아이템은 완료 불가 — 관리자가 먼저 위치 지정해야 함
        if (item.getLocationId() == null) {
            throw new IllegalArgumentException("위치가 지정되지 않은 항목입니다. 먼저 위치를 지정해 주세요.");
        }

        validatePlacementLocationUsable(item.getLocationId(), clientId);

        // 2. 회사 검증
        PlacementOrders po = placementOrderRepository.findById(item.getPlacementOrderId())
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));

        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 항목만 처리할 수 있습니다.");
        }

        // 2-0. one-SKU-per-location 재검증 — assignPlacementLocation 이후 다른 상품이
        // 같은 위치를 점유했을 수 있으므로 적치 완료 직전에 다시 본다 (race condition 방어).
        // 위반 시 PlacementItem.placed 는 false 유지되어 사용자가 위치 재지정 가능.
        List<Inventory> existingInv = inventoryRepository
                .findByWarehouseIdAndLocationId(po.getWarehouseId(), item.getLocationId());
        for (Inventory inv : existingInv) {
            if (inv.getProductId() != null
                    && !inv.getProductId().equals(item.getProductId())
                    && inv.getTotalQty() != null && inv.getTotalQty() > 0) {
                LocationResDto loc = masterServiceClient.getLocation(item.getLocationId(), clientId.toString());
                Integer floorNo = (loc != null) ? loc.getFloorNo() : null;
                throw new IllegalArgumentException(buildOccupiedMessage(
                        floorNo, inv.getProductId(), inv.getTotalQty(), clientId.toString())
                        + " 다른 위치로 재지정해 주세요.");
            }
        }

        // 2-1. 불량 수량 검증
        int defect = defectQty != null ? defectQty : 0;
        if (defect < 0) {
            throw new IllegalArgumentException("불량 수량은 0 이상이어야 합니다.");
        }
        if (defect > item.getQty()) {
            throw new IllegalArgumentException(
                    "불량 수량(" + defect + ")이 적치 수량(" + item.getQty() + ")을 초과할 수 없습니다.");
        }
        int normalQty = item.getQty() - defect;

        // 2-2. 로케이션 수용량 사전 검증 (동기) — Kafka 비동기 confirmPlacement 가 capacity 초과로 실패하면
        // is_placed=true 는 이미 커밋돼 phantom 발생. 동기 단계에서 미리 막아 placement_item 자체가 완료 표시되지 않게 함.
        // (불량 적치 흐름은 별도 zone 검증이라 제외)
        if (!item.isDefect() && normalQty > 0) {
            inventoryService.checkPlacementCapacity(
                    item.getLocationId(), po.getWarehouseId(), normalQty, clientId);
        }

        // 3. 적치 완료 처리 + 불량 수량 기록
        item.setDefectQty(defect);
        item.completePlacement(userId);
        workAssignmentService.recordLastLocation(clientId, userId, po.getWarehouseId(), item.getLocationId());

        // 3-1. 적치 중 불량 발견 시 — 같은 적치 지시서에 DEFECT 존 추가 PlacementItems 자동 생성
        //   검수 불량과 동일한 패턴: DEFECT 존 위치 자동 추천 → 작업자가 완료하면 relocateDefect
        if (defect > 0 && !item.isDefect()) {
            List<SuggestLocationResDto> defectSuggestions = placementSuggestionService.suggest(
                    item.getProductId(), po.getWarehouseId(), defect, clientId,
                    com.beyond.wbs.inventory.service.PlacementPurpose.DEFECT);
            int remaining = defect;
            for (SuggestLocationResDto s : defectSuggestions) {
                if (remaining <= 0) break;
                int cap = s.getRemainCapacity() != null ? s.getRemainCapacity() : remaining;
                int putQty = Math.min(remaining, cap);
                if (putQty <= 0) continue;
                placementItemRepository.save(PlacementItems.create(
                        po.getId(), item.getOrderItemId(), item.getProductId(),
                        s.getLocationId(), putQty, item.getLotNo(), true));
                remaining -= putQty;
            }
            if (remaining > 0) {
                log.warn("[적치 중 불량 자동 적치] DEFECT 존 용량 부족 {}개 미배정 저장 — productId={}",
                        remaining, item.getProductId());
                placementItemRepository.save(PlacementItems.create(
                        po.getId(), item.getOrderItemId(), item.getProductId(),
                        null, remaining, item.getLotNo(), true));
            }
            log.info("[적치 중 불량 자동 적치 생성] inboundOrderId={}, productId={}, qty={}",
                    po.getInboundOrderId(), item.getProductId(), defect);
        }

        // 4. 적치 지시서 상태 업데이트 (신규 defect PlacementItems 포함 계산)
        long pendingInPo = placementItemRepository.countByPlacementOrderIdAndIsPlacedFalse(po.getId());
        if (pendingInPo == 0) {
            po.complete();
        } else {
            po.startProgress();
        }

        // 5. 재고 반영 — originType 은 이벤트 페이로드에 같이 실어 통계 분기에 사용
        InboundOrders inboundForOrigin = inboundOrderRepository.findById(po.getInboundOrderId()).orElse(null);
        String inboundOriginType = inboundForOrigin != null ? inboundForOrigin.getOriginType() : null;

        if (item.isDefect()) {
            // 검수 때 이미 defect 로 분류됐던 품목: defect@null → defect@실위치 (직접 호출)
            inventoryService.relocateDefect(
                    clientId, item.getProductId(), po.getWarehouseId(),
                    item.getLocationId(), item.getQty(),
                    po.getInboundOrderId(), null);
        } else {
            // 정상 적치 — 정상 수량만 available 로,적치 중 발견 불량은 defect@null 에 임시 집계 (이후 자동 생성된 defect PlacementItems 완료 시 실위치로 이동)"


            if (normalQty > 0) {
                InboundStockEvent.Item normalEvent = InboundStockEvent.Item.builder()
                        .productId(item.getProductId())
                        .locationId(item.getLocationId())
                        .qty(normalQty)
                        .build();
                inboundEventPublisher.publishPlaced(InboundStockEvent.builder()
                        .clientId(clientId)
                        .warehouseId(po.getWarehouseId())
                        .refId(po.getInboundOrderId())
                        .userId(userId)
                        .originType(inboundOriginType)
                        .items(List.of(normalEvent))
                        .build());
            }

            if (defect > 0) {
                // 적치 중 발견한 불량 — pending → defect@null (검수 defect 와 동일 흐름)
                InboundStockEvent.Item defectEvent = InboundStockEvent.Item.builder()
                        .productId(item.getProductId())
                        .locationId(null)
                        .qty(defect)
                        .build();
                inboundEventPublisher.publishDefect(InboundStockEvent.builder()
                        .clientId(clientId)
                        .warehouseId(po.getWarehouseId())
                        .refId(po.getInboundOrderId())
                        .userId(userId)
                        .originType(inboundOriginType)
                        .items(List.of(defectEvent))
                        .build());
                log.info("[적치 불량] inboundOrderId={}, placementItemId={}, qty={}, reason={}",
                        po.getInboundOrderId(), item.getId(), defect, defectReason);
            }
        }

        // 6. 입고 지시서의 모든 적치 완료인지 자동 판정 + 웹 알림
        InboundOrders order = inboundForOrigin;
        InboundOrderStatus before = order != null ? order.getStatus() : null;

        long pendingInOrder = placementItemRepository.countPendingByInboundOrderId(po.getInboundOrderId());
        if (pendingInOrder == 0 && order != null) {
            order.completePlacing(hasDefect(po.getInboundOrderId()));
        }

        InboundOrderStatus after = order != null ? order.getStatus() : null;
        String orderNo = order != null ? order.getOrderNo() : null;
        UUID orderId = po.getInboundOrderId();

        // 7. 웹 관리자에게 알림 push
        // (a) 품목 적치 완료 — 상세 채널만 (해당 입고 지시서 상세를 보고 있는 관리자만)
        WorkEventMessage placedMsg = WorkEventMessage.builder()
                .module("inbound")
                .type("PLACED")
                .clientId(clientId)
                .orderId(orderId)
                .orderNo(orderNo)
                .userId(userId)
                .occurredAt(LocalDateTime.now())
                .build();
        webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + orderId, placedMsg);

        // (b) 입고 지시서 마감 transition 발생 시 — 목록 + 상세 둘 다 push
        if (before != after && (after == InboundOrderStatus.completed
                || after == InboundOrderStatus.partial)) {
            WorkEventMessage closeMsg = WorkEventMessage.builder()
                    .module("inbound")
                    .type(after == InboundOrderStatus.partial ? "PARTIAL" : "COMPLETED")
                    .clientId(clientId)
                    .orderId(orderId)
                    .orderNo(orderNo)
                    .userId(userId)
                    .occurredAt(LocalDateTime.now())
                    .build();
            webSocketPublisher.send("/topic/admin/inbound/" + clientId, closeMsg);
            webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + orderId, closeMsg);

            // 통계/대시보드 카운트용 — 마감 이벤트 발행 (진행 중 카운트 -1)
            inboundEventPublisher.publishOrderCompleted(InboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(po.getWarehouseId())
                    .refId(orderId)
                    .userId(userId)
                    .originType(inboundOriginType)
                    .items(List.of())
                    .build());
        }
    }

    /**
     * 정상 완료가 아닌 케이스 — partial 마감 사유 통합 판정.
     *  (a) 검수 불량 (InboundOrderItems.defectQty > 0)
     *  (b) 적치 중 발견된 불량 (PlacementItems.defectQty > 0)
     *  (c) 수량 부족 (InboundOrderItems.status == shortage, 즉 received_qty < ordered_qty)
     * 위 셋 중 하나라도 해당되면 true.
     */
    private boolean hasDefect(UUID inboundOrderId) {
        List<InboundOrderItems> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);
        boolean receiveDefect = items.stream()
                .anyMatch(i -> i.getDefectQty() != null && i.getDefectQty() > 0);
        if (receiveDefect) return true;
        boolean shortage = items.stream()
                .anyMatch(i -> i.getStatus() == InboundOrderItemStatus.shortage);
        if (shortage) return true;
        return placementItemRepository.findByInboundOrderId(inboundOrderId).stream()
                .anyMatch(p -> p.getDefectQty() != null && p.getDefectQty() > 0);
    }

    /**
     * 12. 적치 지시서 전체 완료 처리
     */
    @Transactional
    public void completePlacementOrder(UUID placementOrderId, UUID userId, UUID clientId) {
        PlacementOrders po = placementOrderRepository.findById(placementOrderId)
                .orElseThrow(() -> new NoSuchElementException("적치 지시서를 찾을 수 없습니다."));

        if (!po.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("같은 회사의 적치 지시서만 처리할 수 있습니다.");
        }

        if (po.getStatus() == PlacementOrderStatus.completed) {
            throw new IllegalStateException("이미 완료된 적치 지시서입니다.");
        }

        // 미적치 품목 존재 시 완료 불가 (개별 완료 강제)
        List<PlacementItems> items = placementItemRepository.findByPlacementOrderId(po.getId());
        long pending = items.stream().filter(i -> !i.isPlaced()).count();
        if (pending > 0) {
            throw new IllegalStateException(
                    "아직 적치되지 않은 품목이 있습니다. 미적치 " + pending + "건");
        }

        po.complete();

        // 입고 지시서 정보 조회 (Kafka 이벤트 payload용)
        UUID inboundOrderId = po.getInboundOrderId();
        InboundOrders inboundOrder = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));

        // 입고 지시서의 모든 적치 완료 시 지시서 종결 (불량 있으면 partial, 없으면 completed)
        InboundOrderStatus before = inboundOrder.getStatus();
        long pendingInOrder = placementItemRepository.countPendingByInboundOrderId(inboundOrderId);
        if (pendingInOrder == 0) {
            inboundOrder.completePlacing(hasDefect(inboundOrderId));
        }
        InboundOrderStatus after = inboundOrder.getStatus();

        // 입고 지시서 마감 transition 발생 시 웹 관리자에게 알림 push (목록 + 상세 둘 다)
        if (before != after && (after == InboundOrderStatus.completed
                || after == InboundOrderStatus.partial)) {
            WorkEventMessage closeMsg = WorkEventMessage.builder()
                    .module("inbound")
                    .type(after == InboundOrderStatus.partial ? "PARTIAL" : "COMPLETED")
                    .clientId(clientId)
                    .orderId(inboundOrderId)
                    .orderNo(inboundOrder.getOrderNo())
                    .userId(userId)
                    .occurredAt(LocalDateTime.now())
                    .build();
            webSocketPublisher.send("/topic/admin/inbound/" + clientId, closeMsg);
            webSocketPublisher.send("/topic/admin/inbound/" + clientId + "/" + inboundOrderId, closeMsg);

            // 통계/대시보드 카운트용 — 마감 이벤트 발행 (진행 중 카운트 -1)
            inboundEventPublisher.publishOrderCompleted(InboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(inboundOrder.getWarehouseId())
                    .refId(inboundOrderId)
                    .userId(userId)
                    .originType(inboundOrder.getOriginType())
                    .items(List.of())
                    .build());
        }

        // [Kafka] 적치 완료 이벤트 발행
        // - 검수중(pending) → 가용(available) 전환
        // - 이 시점부터 해당 재고로 출고 가능해짐
        // - PlacementItems 기반으로 productId/locationId/qty 매핑
        List<InboundStockEvent.Item> eventItems = new ArrayList<>();
        for (PlacementItems item : items) {
            eventItems.add(InboundStockEvent.Item.builder()
                    .productId(item.getProductId())
                    .locationId(item.getLocationId())
                    .qty(item.getQty())
                    .build());
        }

        if (!eventItems.isEmpty()) {
            inboundEventPublisher.publishPlaced(InboundStockEvent.builder()
                    .clientId(clientId)
                    .warehouseId(inboundOrder.getWarehouseId())
                    .refId(inboundOrderId)
                    .userId(userId)
                    .originType(inboundOrder.getOriginType())
                    .items(eventItems)
                    .build());
        }
    }

    /**
     * 발주서 진행률 단계 가중치 (A안 — 초기값, 운영 데이터로 추후 튜닝).
     *
     *   DRAFT / APPROVED → 0.30   (입고지시서 생성·승인 — 전산 단계)
     *   RECEIVED         → 0.60   (검수 완료 — 가재고 발생)
     *   PLACING          → 0.80   (적치 진행 중)
     *   COMPLETED / PARTIAL → 1.00 (적치 완료 — 최종)
     *   CANCELLED / null → 0.00   (제외)
     *
     * 사용처: 발주서 목록 화면의 receiveProgressPercent 계산.
     * 출고 쪽 OutboundService.progressWeightForOutboundStatus 와 대칭이지만 단계가 한 단계 더 많음
     *  — 입고는 검수와 적치가 별개 작업이라 분리 유지.
     */
    static double progressWeightForInboundStatus(InboundOrderStatus status) {
        if (status == null) return 0.0;
        return switch (status) {
            case draft, approved -> 0.30;
            case received -> 0.60;
            case placing -> 0.80;
            case completed, partial -> 1.00;
            case cancelled -> 0.00;
        };
    }
}

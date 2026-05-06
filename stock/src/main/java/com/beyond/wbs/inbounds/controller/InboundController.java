package com.beyond.wbs.inbounds.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.inbounds.dto.*;
import com.beyond.wbs.inventory.dtos.SuggestLocationResDto;
import com.beyond.wbs.inventory.service.WarehouseRecommendationService;
import com.beyond.wbs.inbounds.service.InboundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/inbound")
public class InboundController {

    private final InboundService inboundService;
    private final WarehouseRecommendationService warehouseRecommendationService;

    public InboundController(InboundService inboundService,
                             WarehouseRecommendationService warehouseRecommendationService) {
        this.inboundService = inboundService;
        this.warehouseRecommendationService = warehouseRecommendationService;
    }

    // 1. ASN(ERP 발주서) 목록 조회
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @GetMapping("/asn-orders")
    public ResponseEntity<List<AsnOrderResDto>> getAsnOrders(
            @RequestHeader("X-Client-Id") String clientId) {
        List<AsnOrderResDto> orders = inboundService.getAsnOrders(UUID.fromString(clientId));
        return ResponseEntity.ok(orders);
    }

    // 1-2. 단일 ASN 발주서 미리보기 (품목 + 상품 등록여부)
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @GetMapping("/asn-orders/{asnId}/preview")
    public ResponseEntity<AsnOrderResDto> getAsnOrderPreview(
            @PathVariable UUID asnId,
            @RequestHeader("X-Client-Id") String clientId) {
        AsnOrderResDto preview = inboundService.getAsnOrderPreview(asnId, UUID.fromString(clientId));
        return ResponseEntity.ok(preview);
    }

    // 1-2-2. "발주서 목록" 화면용 — PO 전체 + 진행률 보강 (Spring Page).
    //   수주서 진행률 페이지(/erp-sales-orders) 와 대칭이지만 데이터 누적 대비 페이징 적용.
    //   PO 1건 ↔ 입고지시서 1건 정책이라 응답에 inboundOrderId/inboundOrderNo/inboundStatus + receiveProgressPercent 보강.
    //   필터 모두 선택적: status (NOT_STARTED/IN_PROGRESS/COMPLETED), poNoKeyword, dateFrom/dateTo, hideCompleted.
    //   기본 정렬: scheduledDate ASC (FE 가 sort 파라미터로 변경 가능).
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/purchase-orders")
    public ResponseEntity<?> getPurchaseOrderList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String poNoKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "false") boolean hideCompleted,
            @PageableDefault(size = 20, sort = "scheduledDate", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(inboundService.getPurchaseOrderList(
                UUID.fromString(clientId), status, poNoKeyword, dateFrom, dateTo, hideCompleted, pageable));
    }

    // 1-3. 발주서 다건 → 창고 추천 (입고지시서 생성 전 미리보기)
    //   FE 가 같은 입고예정일(PO.scheduledDate) 에 묶인 PO 들을 한 번에 보내면,
    //   각 PO 별 추천 창고 + 후보 목록을 돌려줌.
    //   추천 기준: 협력사 전용 랙 보유(1차) + 카테고리 매칭 zone(2차).
    //   자동 선택 X — FE 강조 표시 후 사용자 선택.
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/recommend-warehouses")
    public ResponseEntity<RecommendWarehousesResDto> recommendWarehouses(
            @RequestBody @Valid RecommendWarehousesReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        RecommendWarehousesResDto result = warehouseRecommendationService
                .recommendForPos(dto.getPurchaseOrderIds(), UUID.fromString(clientId));
        return ResponseEntity.ok(result);
    }

    // 2. ASN → 입고 지시서 생성
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/inbound-orders/from-asn")
    public ResponseEntity<InboundOrderResDto> createFromAsn(
            @RequestBody @Valid CreateFromAsnReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        InboundOrderResDto result = inboundService.createFromAsn(
                dto.getAsnId(),
                UUID.fromString(dto.getWarehouse()),
                UUID.fromString(clientId),
                UUID.fromString(userId)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 3. 수동 입고 지시서 생성
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/inbound-orders")
    public ResponseEntity<InboundOrderResDto> createManual(
            @RequestBody @Valid CreateInboundReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        InboundOrderResDto result = inboundService.createManual(
                dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 3-1. 반품 입고지시서 생성 (출고지시서 기반)
    @CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/inbound-orders/from-return")
    public ResponseEntity<InboundOrderResDto> createFromReturn(
            @RequestBody @Valid CreateFromReturnReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        InboundOrderResDto result = inboundService.createFromReturn(
                dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * 4-A. 입고지시서 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① 프론트가 ProductSearchFilterModal → /master-service/product/search-advanced 로
     *      productIds 수집
     *   ② 그 productIds 와 기존 status 조건을 본 엔드포인트 body 로 전달
     *   ③ 백엔드는 EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 입고지시서"만 반환
     *
     * 기존 GET /inbound-orders 는 그대로 유지 (단순 status 필터용).
     * Body 의 모든 필드 옵셔널 — 비어있으면 GET 와 동일한 결과.
     *
     * Body 예) { "status": ["approved"], "productIds": ["uuid1", "uuid2"] }
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/inbound-orders/search")
    public ResponseEntity<?> searchInboundOrders(
            @RequestBody InboundOrderSearchReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        List<String> status = dto != null ? dto.getStatus() : null;
        List<UUID> productIds = dto != null ? dto.getProductIds() : null;
        String originType = dto != null ? dto.getOriginType() : null;
        String excludeOriginType = dto != null ? dto.getExcludeOriginType() : null;
        return ResponseEntity.ok(
                inboundService.getInboundOrders(UUID.fromString(clientId),
                        status, productIds, originType, excludeOriginType, pageable, requesterId));
    }

    // 4. 입고 지시서 목록 조회 (페이징)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/inbound-orders")
    public ResponseEntity<?> getInboundOrders(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String originType,
            @RequestParam(required = false) String excludeOriginType,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        // 빈 문자열 필터 제거 (프론트에서 status= 또는 status=null 로 보내는 경우 방어)
        List<String> cleanStatus = null;
        if (status != null) {
            cleanStatus = status.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            if (cleanStatus.isEmpty()) cleanStatus = null;
        }
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(
                inboundService.getInboundOrders(UUID.fromString(clientId),
                        cleanStatus, null, originType, excludeOriginType, pageable, requesterId));
    }

    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/search")
    public ResponseEntity<List<InboundSearchResDto>> searchInboundOrders(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "status") List<String> statuses,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<InboundSearchResDto> orders = inboundService.searchInboundOrders(
                UUID.fromString(clientId), keyword, statuses, page, size);
        return ResponseEntity.ok(orders);
    }

    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog
    @PostMapping("/search/reindex")
    public ResponseEntity<InboundReindexResDto> reindexInboundOrders(
            @RequestHeader("X-Client-Id") String clientId) {
        InboundReindexResDto result = inboundService.reindexInboundOrders(UUID.fromString(clientId));
        return ResponseEntity.ok(result);
    }

    // 5. 입고 지시서 상세 조회
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/inbound-orders/{id}")
    public ResponseEntity<InboundOrderResDto> getInboundOrder(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        InboundOrderResDto order = inboundService.getInboundOrder(id, UUID.fromString(clientId), requesterId);
        return ResponseEntity.ok(order);
    }

    // 6. 입고 지시서 품목 목록 조회
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/inbound-orders/{orderId}/items")
    public ResponseEntity<List<InboundOrderItemResDto>> getInboundItems(
            @PathVariable UUID orderId,
            @RequestHeader("X-Client-Id") String clientId) {
        List<InboundOrderItemResDto> items = inboundService.getInboundItems(orderId, UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    /**
     * 6-A. 입고 지시서 품목 목록 — 상품 ID 필터 적용 (지시서 상세 내 검색).
     *
     * 흐름:
     *  ① 프론트가 ProductSearchFilterModal 의 condition 으로
     *     /master-service/product/search-advanced 호출 → productIds 수집
     *  ② 그 productIds 를 본 엔드포인트에 body 로 전달 → 라인 좁힘
     *
     * Body 예) { "productIds": ["uuid1", "uuid2", "uuid3"] }
     * productIds 가 비어있으면 전체 라인 반환 (필터 미적용).
     *
     * GET 변형이 아닌 POST 인 이유: 매칭할 productIds 수가 많아질 수 있어
     * URL 길이 제한을 회피.
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/inbound-orders/{orderId}/items/search")
    public ResponseEntity<List<InboundOrderItemResDto>> searchInboundItems(
            @PathVariable UUID orderId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<InboundOrderItemResDto> items = inboundService.getInboundItemsByProductIds(
                orderId,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 6-1. 입고 전표 조회 (입고 확정 이후 생성)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/inbound-orders/{orderId}/receipt")
    public ResponseEntity<InboundReceiptResDto> getInboundReceipt(
            @PathVariable UUID orderId,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        InboundReceiptResDto receipt = inboundService.getInboundReceiptByOrder(
                orderId, UUID.fromString(clientId), requesterId);
        return ResponseEntity.ok(receipt);
    }

    // 6-2. 입고 전표 목록 조회 — 평면 리스트, 페이지/필터 지원
    // 모든 필터 선택적: dateFrom/dateTo (receivedAt 범위), warehouseId, originType,
    //                  receiptNoKeyword (전표번호), orderNoKeyword (지시서번호)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/receipts")
    public ResponseEntity<?> getInboundReceiptList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String originType,
            @RequestParam(required = false) String receiptNoKeyword,
            @RequestParam(required = false) String orderNoKeyword,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        // 날짜 → 시각 변환: from 은 그 날 00:00, to 는 다음 날 00:00 (= 그 날 포함)
        LocalDateTime fromAt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toAt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(inboundService.getReceiptList(
                UUID.fromString(clientId), fromAt, toAt, warehouseId,
                originType, receiptNoKeyword, orderNoKeyword, requesterId, pageable));
    }

    // 7. 입고 지시서 승인
    @CheckPermission(resource = Resource.INBOUND, action = Action.APPROVE)
    @AuditLog(action = "승인")
    @PostMapping("/inbound-orders/{id}/approve")
    public ResponseEntity<Void> approveInboundOrder(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        inboundService.approveInboundOrder(id, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 7-1. 입고지시서 취소 (draft / approved 만 가능)
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog(action = "취소")
    @PatchMapping("/inbound-orders/{id}/cancel")
    public ResponseEntity<Void> cancelInboundOrder(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        inboundService.cancelInboundOrder(id, UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

    // 8-1. 입고 확정 (수량 검수) - approved → received
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog
    @PostMapping("/inbound-orders/{orderId}/receive")
    public ResponseEntity<List<InboundOrderItemResDto>> receiveInbound(
            @PathVariable UUID orderId,
            @RequestBody @Valid ReceiveReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        List<InboundOrderItemResDto> result = inboundService.receiveInbound(
                orderId, dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok(result);
    }

    // 9. 특정 지시서의 적치 지시서 목록 조회
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/inbound-orders/{orderId}/placements")
    public ResponseEntity<List<PlacementOrderResDto>> getPlacementOrders(
            @PathVariable UUID orderId,
            @RequestHeader("X-Client-Id") String clientId) {
        List<PlacementOrderResDto> orders = inboundService.getPlacementOrders(
                orderId, UUID.fromString(clientId));
        return ResponseEntity.ok(orders);
    }

    // 10. 전체 적치 대기 목록 조회 (현장 작업자용)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/placements")
    public ResponseEntity<List<PlacementItemResDto>> getPendingPlacements(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) String status) {
        List<PlacementItemResDto> items = inboundService.getPendingPlacements(
                UUID.fromString(clientId), status);
        return ResponseEntity.ok(items);
    }

    // 11. 개별 적치 아이템 완료 처리
    // 바디의 defectQty/defectReason 은 옵션 — 없으면 전량 정상 적치로 처리
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog(action = "완료")
    @PatchMapping("/placements/{itemId}/complete")
    public ResponseEntity<Void> completePlacementItem(
            @PathVariable UUID itemId,
            @RequestBody(required = false) CompletePlacementItemReqDto body,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Integer defectQty = body != null ? body.getDefectQty() : null;
        String defectReason = body != null ? body.getDefectReason() : null;
        inboundService.completePlacementItem(itemId, UUID.fromString(clientId),
                userId != null ? UUID.fromString(userId) : null,
                defectQty, defectReason);
        return ResponseEntity.ok().build();
    }

    // 11-1. 미배정 적치 아이템에 위치 지정 (관리자가 수동 지정)
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog(action = "위치지정")
    @PatchMapping("/placements/{itemId}/assign-location")
    public ResponseEntity<Void> assignPlacementLocation(
            @PathVariable UUID itemId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Client-Id") String clientId) {
        String locationIdStr = body.get("locationId");
        if (locationIdStr == null) {
            throw new IllegalArgumentException("locationId 는 필수입니다.");
        }
        inboundService.assignPlacementLocation(
                itemId, UUID.fromString(locationIdStr), UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

    // 11-1-2. 미배정 적치 아이템을 여러 위치에 분할 지정 (수량이 한 위치에 다 안 들어갈 때)
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog(action = "위치분할지정")
    @PostMapping("/placements/{itemId}/split-assign")
    public ResponseEntity<Void> splitAssignPlacementLocation(
            @PathVariable UUID itemId,
            @RequestBody SplitAssignReqDto body,
            @RequestHeader("X-Client-Id") String clientId) {
        inboundService.splitAssignPlacementLocation(
                itemId, body, UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

    // 11-2. 미배정 적치 아이템 기준 로케이션 재추천
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog
    @GetMapping("/placements/{itemId}/suggest-locations")
    public ResponseEntity<List<SuggestLocationResDto>> suggestPlacementLocations(
            @PathVariable UUID itemId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inboundService.suggestPlacementLocations(itemId, UUID.fromString(clientId))
        );
    }

    /**
     * 6-A. 입고전표 목록 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → /master-service/product/search-advanced 로 productIds 수집
     *   ② 그 productIds 와 기존 dateFrom/dateTo/warehouseId 등을 본 엔드포인트 body 로 전달
     *   ③ EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 입고전표"만 반환
     *
     * 기존 GET /receipts 는 그대로 유지 (단순 필터용).
     * Body 의 모든 필드 옵셔널.
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/receipts/search")
    public ResponseEntity<?> searchInboundReceipts(
            @RequestBody InboundReceiptSearchReqDto dto,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        LocalDate dateFrom = dto != null ? dto.getDateFrom() : null;
        LocalDate dateTo = dto != null ? dto.getDateTo() : null;
        LocalDateTime fromAt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toAt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(inboundService.getReceiptList(
                UUID.fromString(clientId),
                fromAt, toAt,
                dto != null ? dto.getWarehouseId() : null,
                dto != null ? dto.getOriginType() : null,
                dto != null ? dto.getReceiptNoKeyword() : null,
                dto != null ? dto.getOrderNoKeyword() : null,
                dto != null ? dto.getProductIds() : null,
                requesterId, pageable));
    }

    /**
     * 6-B. 입고전표 내 상품 라인 검색 — productIds 필터.
     *
     * Body: { "productIds": ["uuid1", "uuid2"] } — 비어있으면 전체 반환
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/receipts/{receiptId}/items/search")
    public ResponseEntity<List<InboundReceiptItemResDto>> searchReceiptItems(
            @PathVariable UUID receiptId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<InboundReceiptItemResDto> items = inboundService.getInboundReceiptItemsByProductIds(
                receiptId,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    /**
     * 11-2. 적치지시서 목록 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → productIds 수집
     *   ② body 로 전달 → EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 적치지시서"만 반환
     *
     * Body 의 모든 필드 옵셔널.
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/placement-orders/search")
    public ResponseEntity<?> searchPlacementOrders(
            @RequestBody PlacementOrderSearchReqDto dto,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(inboundService.getPlacementOrderList(
                UUID.fromString(clientId),
                dto != null ? dto.getAssignedTo() : null,
                dto != null ? dto.getWarehouseId() : null,
                dto != null ? dto.getStatus() : null,
                dto != null ? dto.getProductIds() : null,
                pageable));
    }

    /**
     * 11-3. 적치지시서 내 상품 라인 검색 — productIds 필터.
     *
     * Body: { "productIds": ["uuid1", "uuid2"] } — 비어있으면 전체 반환
     */
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/placement-orders/{placementOrderId}/items/search")
    public ResponseEntity<List<PlacementItemResDto>> searchPlacementItems(
            @PathVariable UUID placementOrderId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<PlacementItemResDto> items = inboundService.getPlacementItemsByProductIds(
                placementOrderId,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 12. 적치 지시서 전체 완료 처리
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @AuditLog(action = "완료")
    @PostMapping("/placement-orders/{placementOrderId}/complete")
    public ResponseEntity<Void> completePlacementOrder(
            @PathVariable UUID placementOrderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        inboundService.completePlacementOrder(placementOrderId,
                UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

}

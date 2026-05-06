package com.beyond.wbs.outbounds.controller;


import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.outbounds.domain.ErpSalesOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.dtos.CreateOutboundFromSalesOrdersReqDto;
import com.beyond.wbs.outbounds.dtos.CreateOutboundReqDto;
import com.beyond.wbs.outbounds.dtos.DispatchCreateReqDto;
import com.beyond.wbs.outbounds.dtos.ManualPreviewReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundDispatchItemResDto;
import com.beyond.wbs.outbounds.dtos.OutboundDispatchSearchReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundOrderItemResDto;
import com.beyond.wbs.outbounds.dtos.OutboundOrderSearchReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundPreviewReqDto;
import com.beyond.wbs.outbounds.dtos.ProductIdsReqDto;
import com.beyond.wbs.outbounds.dtos.ReturnOutboundCreateReqDto;
import com.beyond.wbs.outbounds.dtos.SplitRecommendationReqDto;
import com.beyond.wbs.outbounds.service.OutboundPreviewService;
import com.beyond.wbs.outbounds.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/outbound")
public class OutboundController {
    private final OutboundService outboundService;
    private final OutboundPreviewService outboundPreviewService;

    @Autowired
    public OutboundController(OutboundService outboundService,
                              OutboundPreviewService outboundPreviewService) {
        this.outboundService = outboundService;
        this.outboundPreviewService = outboundPreviewService;
    }

    // 출고지시서 생성 전 — 선택한 수주서 기준 (창고 × 품목) 가용재고 미리보기
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/preview")
    public ResponseEntity<?> previewWarehouseStock(@RequestBody @Valid OutboundPreviewReqDto req,
                                                    @RequestHeader("X-Client-Id") String clientId) {
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundPreviewService.getPreview(clientUUID, req));
    }

    // 수동 생성 전 — 출고처/예정일/품목 입력 기반 미리보기 (수주서 흐름과 동일한 응답 구조)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/preview/manual")
    public ResponseEntity<?> previewManualOutbound(@RequestBody @Valid ManualPreviewReqDto req,
                                                    @RequestHeader("X-Client-Id") String clientId) {
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundPreviewService.getManualPreview(clientUUID, req));
    }

    // 분할 출고 자동 추천 — 한 출고처 단위로 가용재고 많은 창고부터 그리디 분배
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/preview/split-recommendation")
    public ResponseEntity<?> recommendSplit(@RequestBody @Valid SplitRecommendationReqDto req,
                                             @RequestHeader("X-Client-Id") String clientId) {
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundPreviewService.recommendSplit(clientUUID, req));
    }

    // 수주서(다수) → 출고지시서 생성 (단일/분할 통합)
    // 한 출고처 단위로 호출 — warehouseAllocations 의 창고 수만큼 OB 생성
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/from-sales-orders")
    public ResponseEntity<?> createFromSalesOrders(@RequestBody @Valid CreateOutboundFromSalesOrdersReqDto req,
                                                    @RequestHeader("X-User-Id") String userId,
                                                    @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundPreviewService.createFromSalesOrders(clientUUID, userUUID, req));
    }

    // 수동 출고지시서 생성 (ERP 수주서 없이 관리자가 직접 입력)
    // draft 상태로 저장 → 이후 흐름(승인 → reserve → wave) 은 수주서 흐름과 동일
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.CREATE)
    @AuditLog(action = "수동생성")
    @PostMapping("/manual")
    public ResponseEntity<?> createManual(@RequestBody @Valid CreateOutboundReqDto dto,
                                          @RequestHeader("X-User-Id") String userId,
                                          @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundService.createManual(dto, userUUID, clientUUID));
    }

    // 수주서 진행률 조회 — 진행률 페이지 메인 API
    // 응답: SO 헤더 + 품목별 진행률 + 연결된 OB 목록 (취소 이력 포함)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/sales-orders/{salesOrderId}/progress")
    public ResponseEntity<?> getSalesOrderProgress(@PathVariable UUID salesOrderId,
                                                    @RequestHeader("X-Client-Id") String clientId) {
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundPreviewService.getSalesOrderProgress(clientUUID, salesOrderId));
    }

    // ERP 수주서 목록조회 — FE 선택 화면용 (필터 + 진행률 + 품목요약 포함)
    // 모든 필터 파라미터는 선택적: status, storeId, dateFrom, dateTo, soNoKeyword, hideCompleted
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/erp-sales-orders")
    public ResponseEntity<?> getErpSalesOrders(
            @RequestParam(required = false) ErpSalesOrderStatus status,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String soNoKeyword,
            @RequestParam(required = false, defaultValue = "false") boolean hideCompleted,
            @RequestHeader("X-Client-Id") String clientId){
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundService.getErpSalesOrders(
                clientUUID, status, storeId, dateFrom, dateTo, soNoKeyword, hideCompleted));
    }

    // 출고지시서 목록조회
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/findAll")
    public ResponseEntity<?> getOutboundOrders(@RequestParam(required = false) OutboundOrderStatus status,
                                               @RequestParam(required = false) UUID warehouseId,
                                               @RequestParam(required = false) UUID storeId,
                                               @RequestParam(required = false) String originType,
                                               @RequestParam(required = false) String excludeOriginType,
                                               @PageableDefault(size = 20, sort = "createdAt",
                                                       direction = Sort.Direction.DESC)Pageable pageable,
                                               @RequestHeader("X-Client-Id") String clientId){
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundService.getOutboundOrders(
                status, warehouseId, storeId, clientUUID, null, null,
                originType, excludeOriginType, pageable));

    }

    /**
     * 출고지시서 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → /master-service/product/search-advanced 로 productIds 수집
     *   ② 그 productIds 와 기존 status/warehouseId/storeId 를 본 엔드포인트 body 로 전달
     *   ③ EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 출고지시서"만 반환
     *
     * 기존 GET /findAll 은 그대로 유지 (단순 status/warehouse/store 필터용).
     * Body 의 모든 필드 옵셔널.
     *
     * Body 예) { "status": "approved", "warehouseId": "uuid", "productIds": ["uuid1"] }
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/orders/search")
    public ResponseEntity<?> searchOutboundOrders(
            @RequestBody OutboundOrderSearchReqDto dto,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        UUID clientUUID = UUID.fromString(clientId);
        OutboundOrderStatus status = dto != null ? dto.getStatus() : null;
        UUID warehouseId = dto != null ? dto.getWarehouseId() : null;
        UUID storeId = dto != null ? dto.getStoreId() : null;
        List<UUID> productIds = dto != null ? dto.getProductIds() : null;
        String originType = dto != null ? dto.getOriginType() : null;
        String excludeOriginType = dto != null ? dto.getExcludeOriginType() : null;
        return ResponseEntity.ok(outboundService.getOutboundOrders(
                status, warehouseId, storeId, clientUUID, null, productIds,
                originType, excludeOriginType, pageable));
    }

    /**
     * 반품 출고 생성 — 입고처(공급사)로 돌려보내는 역방향 출고.
     *
     * 비즈니스: 입고처에서 받은 상품 중 불량/오배송이 발견되어 반품 동의 후 다시 보냄.
     * 흐름:
     *   ① 원본 입고지시서(IB-XXXX) 매칭
     *   ② 누적 반품량 + 새 요청 ≤ 검수수량(receivedQty) 검증
     *   ③ OutboundOrder draft 생성 (originType=return, destinationType=supplier)
     *   ④ 일반 출고와 동일한 워크플로우 진입 가능 (approve → reserve → wave → dispatch)
     *
     * 응답: OutboundOrderResDto (반품 컨텍스트 메타 포함)
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.CREATE)
    @AuditLog(action = "반품출고 생성")
    @PostMapping("/return")
    public ResponseEntity<?> createReturnOutbound(
            @RequestBody @Valid ReturnOutboundCreateReqDto dto,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.status(HttpStatus.OK).body(
                outboundService.createReturnOutbound(dto, userUUID, clientUUID));
    }

    // 출고지시서 상세조회
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> getOutboundOrder(@PathVariable UUID id,
                                              @RequestHeader("X-User-Id") String userId,
                                              @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundService.getOutboundOrder(id, userUUID, clientUUID));
    }

    /**
     * 출고지시서 내 상품 라인 검색 — 상품 ID 필터 적용 (지시서 상세 내 검색).
     *
     * 흐름: 프론트가 ProductSearchFilterModal 의 condition 으로
     *       /master-service/product/search-advanced 호출 → productIds 수집 →
     *       본 엔드포인트에 body 로 전달.
     *
     * Body 예) { "productIds": ["uuid1", "uuid2"] }
     * productIds 가 비어있으면 전체 라인 반환.
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/orders/{orderId}/items/search")
    public ResponseEntity<List<OutboundOrderItemResDto>> searchOutboundItems(
            @PathVariable UUID orderId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<OutboundOrderItemResDto> items = outboundService.getOutboundItemsByProductIds(
                orderId,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 출고지시서 승인
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.APPROVE)
    @AuditLog(action = "승인")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approveOutboundOrder(@PathVariable UUID id,
                                                  @RequestHeader("X-User-Id") String userId,
                                                  @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        outboundService.approveOutboundOrder(id, userUUID, clientUUID);
        return ResponseEntity.ok().build();
    }

    //출고지시서 취소
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @AuditLog(action = "취소")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOutboundOrder(@PathVariable UUID id,
                                                 @RequestHeader("X-User-Id") String userId,
                                                 @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        outboundService.cancelOutboundOrder(id, userUUID, clientUUID);
        return ResponseEntity.ok().build();
    }

    // 출고 전표 생성 (출고확정)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.CREATE)
    @AuditLog
    @PostMapping("/dispatches")
    public ResponseEntity<?> createDispatch(@RequestBody @Valid DispatchCreateReqDto dto,
                                            @RequestHeader("X-User-Id")String userId,
                                            @RequestHeader("X-Client-Id")String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        UUID id = outboundService.createDispatch(dto, userUUID, clientUUID);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // 잔여 출고 처리 (force release residual reserve)
    // 출고확정 후 좀비처럼 남은 reserved 재고를 운영자가 화면에서 정리
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @AuditLog(action = "잔여 출고 처리")
    @PostMapping("/{id}/force-release-residual")
    public ResponseEntity<?> forceReleaseResidual(@PathVariable UUID id,
                                                  @RequestHeader("X-User-Id") String userId,
                                                  @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        List<UUID> residualLocations = outboundService.forceReleaseResidual(id, userUUID, clientUUID);
        return ResponseEntity.ok(Map.of("residualLocations", residualLocations.size()));
    }

    // 출고 전표 상세조회
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/dispatches/{id}")
    public ResponseEntity<?> getDispatch(@PathVariable UUID id,
                                         @RequestHeader("X-User-Id") String userId,
                                         @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundService.getDispatch(id, userUUID, clientUUID));
    }

    /**
     * 출고전표 목록 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → productIds 수집
     *   ② body 로 전달 → EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 출고전표"만 반환
     *
     * 기존 GET /dispatches 그대로 유지. Body 의 모든 필드 옵셔널.
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/dispatches/search")
    public ResponseEntity<?> searchOutboundDispatches(
            @RequestBody OutboundDispatchSearchReqDto dto,
            @PageableDefault(size = 20, sort = "dispatchedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        LocalDate dateFrom = dto != null ? dto.getDateFrom() : null;
        LocalDate dateTo = dto != null ? dto.getDateTo() : null;
        LocalDateTime fromAt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toAt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(outboundService.getDispatchList(
                clientUUID, fromAt, toAt,
                dto != null ? dto.getWarehouseId() : null,
                dto != null ? dto.getOriginType() : null,
                dto != null ? dto.getDispatchNoKeyword() : null,
                dto != null ? dto.getOrderNoKeyword() : null,
                dto != null ? dto.getProductIds() : null,
                userUUID, pageable));
    }

    /**
     * 출고전표 내 상품 라인 검색 — productIds 필터.
     *
     * Body: { "productIds": ["uuid1", "uuid2"] } — 비어있으면 전체 반환
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/dispatches/{dispatchId}/items/search")
    public ResponseEntity<List<OutboundDispatchItemResDto>> searchDispatchItems(
            @PathVariable UUID dispatchId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<OutboundDispatchItemResDto> items = outboundService.getDispatchItemsByProductIds(
                dispatchId,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 출고 전표 목록 조회 — 평면 리스트, 페이지/필터 지원
    // 모든 필터 선택적: dateFrom/dateTo (dispatchedAt 범위), warehouseId, originType,
    //                  dispatchNoKeyword (전표번호), orderNoKeyword (지시서번호)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/dispatches")
    public ResponseEntity<?> getDispatchList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String originType,
            @RequestParam(required = false) String dispatchNoKeyword,
            @RequestParam(required = false) String orderNoKeyword,
            @PageableDefault(size = 20, sort = "dispatchedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        // 날짜 → 시각 변환: from 은 그 날 00:00, to 는 다음 날 00:00 (= 그 날 포함)
        LocalDateTime fromAt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toAt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(outboundService.getDispatchList(
                clientUUID, fromAt, toAt, warehouseId, originType,
                dispatchNoKeyword, orderNoKeyword, userUUID, pageable));
    }

    // 출고지시서 ID로 출고전표 조회 — 상세 화면이 orderId만 갖고 진입할 때 사용
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/orders/{orderId}/dispatch")
    public ResponseEntity<?> getDispatchByOrderId(@PathVariable UUID orderId,
                                                  @RequestHeader("X-User-Id") String userId,
                                                  @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(outboundService.getDispatchByOrderId(orderId, userUUID, clientUUID));
    }

//    // 송장번호 입력 (출고전표에 송장번호 입력) (택배사 관리는 안하기로 해서 일단 주석처리)
//    @PatchMapping("/dispatches/{id}/invoice")
//    public ResponseEntity<?> updateInvoiceNo(@PathVariable UUID id,
//                                             @RequestBody @Valid InvoiceUpdateReqDto dto){
//        outboundService.updateInvoiceNo(id, dto);
//        return ResponseEntity.ok().build();
//    }

}

package com.beyond.wbs.transfer.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import com.beyond.wbs.transfer.dto.ProductIdsReqDto;
import com.beyond.wbs.transfer.dto.TransferOrderCreateReqDto;
import com.beyond.wbs.transfer.dto.TransferOrderItemResDto;
import com.beyond.wbs.transfer.dto.TransferOrderResDto;
import com.beyond.wbs.transfer.dto.TransferOrderSearchReqDto;
import com.beyond.wbs.transfer.dto.TransferProcessItemReqDto;
import com.beyond.wbs.transfer.service.TransferOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/transfer")
public class TransferOrderController {

    private final TransferOrderService transferOrderService;

    public TransferOrderController(TransferOrderService transferOrderService) {
        this.transferOrderService = transferOrderService;
    }

    // 1. 이동지시서 생성
    @CheckPermission(resource = Resource.TRANSFER, action = Action.CREATE)
    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<TransferOrderResDto> create(
            @RequestBody @Valid TransferOrderCreateReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        TransferOrderResDto result = transferOrderService.createTransferOrder(
                dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 2. 이동지시서 단건 조회 (품목 포함)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @GetMapping("/{id}")
    public ResponseEntity<TransferOrderResDto> get(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(transferOrderService.getTransferOrder(id, UUID.fromString(clientId)));
    }

    // 3. 이동지시서 품목 목록만 조회
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @GetMapping("/{orderId}/items")
    public ResponseEntity<List<TransferOrderItemResDto>> getItems(
            @PathVariable UUID orderId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                transferOrderService.getTransferOrderItems(orderId, UUID.fromString(clientId)));
    }

    /**
     * 3-A. 이동지시서 내 상품 라인 검색 — productIds 필터.
     *
     * Body: { "productIds": ["uuid1", "uuid2"] } — 비어있으면 전체 반환
     */
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @PostMapping("/{orderId}/items/search")
    public ResponseEntity<List<TransferOrderItemResDto>> searchItems(
            @PathVariable UUID orderId,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                transferOrderService.getTransferOrderItemsByProductIds(
                        orderId,
                        dto != null ? dto.getProductIds() : null,
                        UUID.fromString(clientId)));
    }

    // 4. 이동지시서 목록 조회 (페이징 + 필터)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<Page<TransferOrderResDto>> list(
            @RequestParam(required = false) TransferOrderStatus status,
            @RequestParam(required = false) UUID fromWarehouseId,
            @RequestParam(required = false) UUID toWarehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        Page<TransferOrderResDto> result = transferOrderService.getTransferOrders(
                UUID.fromString(clientId), status, fromWarehouseId, toWarehouseId, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 4-A. 이동지시서 목록 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → productIds 수집
     *   ② body 로 전달 → EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 이동지시서"만 반환
     *
     * Body 의 모든 필드 옵셔널.
     */
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @PostMapping("/search")
    public ResponseEntity<Page<TransferOrderResDto>> searchList(
            @RequestBody TransferOrderSearchReqDto dto,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(transferOrderService.getTransferOrders(
                UUID.fromString(clientId),
                dto != null ? dto.getStatus() : null,
                dto != null ? dto.getFromWarehouseId() : null,
                dto != null ? dto.getToWarehouseId() : null,
                dto != null ? dto.getProductIds() : null,
                pageable));
    }

    // 5. 이동지시서 승인 (draft → approved)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.APPROVE)
    @AuditLog(action = "승인")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        transferOrderService.approveTransferOrder(
                id, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 6. 이동지시서 취소
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @AuditLog(action = "취소")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        transferOrderService.cancelTransferOrder(id, UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

    // 7. 품목 처리 (QR 스캔 후 수량 입력)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @AuditLog
    @PostMapping("/items/{itemId}/process")
    public ResponseEntity<Void> processItem(
            @PathVariable UUID itemId,
            @RequestBody @Valid TransferProcessItemReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        transferOrderService.processItem(
                itemId, dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 8. 작업 마감 (in_progress → completed/partial)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @AuditLog(action = "완료")
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        transferOrderService.completeWork(id, UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }
}

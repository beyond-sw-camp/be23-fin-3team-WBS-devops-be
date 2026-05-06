package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.dtos.DispatchCreateReqDto;
import com.beyond.wbs.outbounds.dtos.OutboundOrderDetailResDto;
import com.beyond.wbs.outbounds.dtos.OutboundOrderResDto;
import com.beyond.wbs.outbounds.service.OutboundService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 모바일 앱 - 출고 완료
 * 작업자가 출고 지시서 QR 스캔 시 사용
 */
@RestController
@RequestMapping("/mobile/outbound")
public class MobileOutboundController {

    private final OutboundService outboundService;

    public MobileOutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    // 출고 지시서 단건 조회 (QR 스캔 후 호출) — 조회는 모든 상태 허용
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<OutboundOrderDetailResDto> getOutbound(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        OutboundOrderDetailResDto order = outboundService.getOutboundOrder(
                id, UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.ok(order);
    }

    // 출고 지시서 목록 (작업 목록) — 모바일: 본인에게 배당된 출고지시서만 노출
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @GetMapping("/list")
    public ResponseEntity<Page<OutboundOrderResDto>> getOutboundList(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) OutboundOrderStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID storeId,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userUUID = UUID.fromString(userId);
        Page<OutboundOrderResDto> result = outboundService.getOutboundOrders(
                status, warehouseId, storeId, UUID.fromString(clientId), userUUID, pageable);
        return ResponseEntity.ok(result);
    }

    // 출고 완료 처리 (발송 생성)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @PostMapping("/{id}/dispatch")
    public ResponseEntity<UUID> dispatch(
            @PathVariable UUID id,
            @RequestBody DispatchCreateReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        UUID dispatchId = outboundService.createDispatch(dto, UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.status(HttpStatus.CREATED).body(dispatchId);
    }
}
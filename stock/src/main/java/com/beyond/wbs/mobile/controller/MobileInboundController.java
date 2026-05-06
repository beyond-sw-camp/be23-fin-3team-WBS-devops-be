package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.inbounds.dto.InboundOrderItemResDto;
import com.beyond.wbs.inbounds.dto.InboundOrderResDto;
import com.beyond.wbs.inbounds.dto.ReceiveReqDto;
import com.beyond.wbs.inbounds.service.InboundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 앱 - 입고 검수
 * 작업자가 입고 지시서 QR 스캔 시 사용
 */
@RestController
@RequestMapping("/mobile/inbound")
public class MobileInboundController {

    private final InboundService inboundService;

    public MobileInboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    // 입고 지시서 단건 조회 (QR 스캔 후 호출)
    // 조회는 모든 상태 허용 — 처리(POST)는 서비스 레이어에서 상태 검증
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<InboundOrderResDto> getInbound(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        InboundOrderResDto order = inboundService.getInboundOrder(id, UUID.fromString(clientId), requesterId);
        return ResponseEntity.ok(order);
    }

    // 입고 지시서 품목 조회 (검수 화면에서 사용)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @GetMapping("/{id}/items")
    public ResponseEntity<List<InboundOrderItemResDto>> getInboundItems(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        List<InboundOrderItemResDto> items = inboundService.getInboundItems(id, UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 작업자 본인 작업 목록 (대기/진행/완료 필터)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @GetMapping("/list")
    public ResponseEntity<?> getInboundList(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) List<String> status,
            @PageableDefault(size = 50, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(
                inboundService.getInboundOrders(UUID.fromString(clientId), status, pageable, requesterId));
    }

    // 입고 검수 완료 (approved → received, 적치 지시서 미생성)
    // 모든 품목의 수량 입력 완료 후 호출. 부분 검수 불가.
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @PostMapping("/{id}/receive")
    public ResponseEntity<List<InboundOrderItemResDto>> receive(
            @PathVariable UUID id,
            @RequestBody @Valid ReceiveReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        List<InboundOrderItemResDto> result = inboundService.receiveInbound(
                id, dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok(result);
    }
}
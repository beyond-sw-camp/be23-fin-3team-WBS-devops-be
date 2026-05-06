package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.transfer.dto.TransferOrderResDto;
import com.beyond.wbs.transfer.dto.TransferPickItemReqDto;
import com.beyond.wbs.transfer.dto.TransferPlaceItemReqDto;
import com.beyond.wbs.transfer.dto.TransferTaskListResDto;
import com.beyond.wbs.transfer.service.TransferOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 모바일 앱 — 이동지시서 작업 (QR 기반, 두 단계 흐름)
 *
 * <p>QR 스캔 → 작업 목록 조회 → PICK 단계 (출발 랙별 픽업) → PLACE 단계 (도착 랙별 적치) → 마감.
 * 재고는 PICK 시점에 출발지에서 차감, PLACE 시점에 도착지에 추가 (이동 중 유령 재고 회피).
 */
@RestController
@RequestMapping("/mobile/transfer")
public class MobileTransferController {

    private final TransferOrderService transferOrderService;

    public MobileTransferController(TransferOrderService transferOrderService) {
        this.transferOrderService = transferOrderService;
    }

    // 지시서 단건 조회 (QR 스캔 후) — 헤더/품목 전체
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<TransferOrderResDto> get(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                transferOrderService.getTransferOrder(id, UUID.fromString(clientId)));
    }

    // 작업 목록 (PICK 단계 + PLACE 단계, 각각 rack/location 정렬)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @GetMapping("/{id}/tasks")
    public ResponseEntity<TransferTaskListResDto> tasks(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                transferOrderService.getMobileTasks(id, UUID.fromString(clientId)));
    }

    // 픽업 (출발지에서 꺼냄) — 출발지 재고 차감
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @PostMapping("/items/{itemId}/pick")
    public ResponseEntity<Void> pick(
            @PathVariable UUID itemId,
            @RequestBody @Valid TransferPickItemReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        transferOrderService.pickItemMobile(
                itemId, dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 적치 (도착지에 놓음) — 도착지 재고 증가
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @PostMapping("/items/{itemId}/place")
    public ResponseEntity<Void> place(
            @PathVariable UUID itemId,
            @RequestBody @Valid TransferPlaceItemReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        transferOrderService.placeItemMobile(
                itemId, dto, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 작업 마감 (in_progress → completed/partial)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.UPDATE)
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        transferOrderService.completeWork(id, UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }
}

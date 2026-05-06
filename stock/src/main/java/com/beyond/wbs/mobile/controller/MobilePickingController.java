package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.outbounds.domain.PickingListStatus;
import com.beyond.wbs.outbounds.dtos.PickItemReqDto;
import com.beyond.wbs.outbounds.dtos.PickingListDetailResDto;
import com.beyond.wbs.outbounds.dtos.PickingListResDto;
import com.beyond.wbs.outbounds.service.PickingListService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 모바일 앱 - 출고 검수 (피킹)
 * 작업자가 피킹리스트 QR 스캔 시 사용
 */
@RestController
@RequestMapping("/mobile/picking")
public class MobilePickingController {

    private final PickingListService pickingListService;

    public MobilePickingController(PickingListService pickingListService) {
        this.pickingListService = pickingListService;
    }

    // 피킹리스트 단건 조회 (QR 스캔 후 호출) — 조회는 모든 상태 허용
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<PickingListDetailResDto> getPicking(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        PickingListDetailResDto picking = pickingListService.getPickingList(
                id, UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.ok(picking);
    }

    // 피킹리스트 목록 (작업 목록)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @GetMapping("/list")
    public ResponseEntity<Page<PickingListResDto>> getPickingList(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PickingListStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userUUID = UUID.fromString(userId);
        // 모바일: 본인에게 배당된 피킹리스트만 노출
        Page<PickingListResDto> result = pickingListService.getPickingLists(
                UUID.fromString(clientId), userUUID, warehouseId, status, userUUID, pageable);
        return ResponseEntity.ok(result);
    }

    // 품목 피킹 처리 (수량 입력)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @PostMapping("/{id}/items/{itemId}/pick")
    public ResponseEntity<Void> pickItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody PickItemReqDto dto,
            @RequestHeader("X-User-Id") String userId) {
        pickingListService.pickItem(id, itemId, dto, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    // 피킹리스트 완료 처리
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completePicking(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId) {
        pickingListService.completePickingList(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
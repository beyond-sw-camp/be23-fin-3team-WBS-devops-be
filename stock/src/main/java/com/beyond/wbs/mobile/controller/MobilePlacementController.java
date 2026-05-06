package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.inbounds.domain.PlacementOrderStatus;
import com.beyond.wbs.inbounds.dto.CompletePlacementItemReqDto;
import com.beyond.wbs.inbounds.dto.PlacementOrderResDto;
import com.beyond.wbs.inbounds.service.InboundService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 모바일 앱 - 적치
 * 작업자가 적치 지시서 QR 스캔 시 사용
 */
@RestController
@RequestMapping("/mobile/placement")
public class MobilePlacementController {

    private final InboundService inboundService;

    public MobilePlacementController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    // 작업자 본인 작업 목록 (자동 배분된 적치 지시서 조회)
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @GetMapping("/list")
    public ResponseEntity<Page<PlacementOrderResDto>> getPlacementOrderList(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PlacementOrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PlacementOrderResDto> result = inboundService.getPlacementOrderList(
                UUID.fromString(clientId), UUID.fromString(userId),
                warehouseId, status, pageable);
        return ResponseEntity.ok(result);
    }

    // 적치 지시서 단건 조회 (QR 스캔 후 호출) — 조회는 모든 상태 허용
    @CheckPermission(resource = Resource.INBOUND, action = Action.READ)
    @GetMapping("/order/{placementOrderId}")
    public ResponseEntity<PlacementOrderResDto> getPlacementOrder(
            @PathVariable UUID placementOrderId,
            @RequestHeader("X-Client-Id") String clientId) {
        PlacementOrderResDto result = inboundService.getPlacementOrder(
                placementOrderId, UUID.fromString(clientId));
        return ResponseEntity.ok(result);
    }

    // 적치 품목 개별 완료 (랙에 물건 하나씩 놓을 때마다 호출)
    // 바디의 defectQty/defectReason 있으면 적치 중 불량으로 기록 + 재고 이벤트 분기
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @PostMapping("/{placementOrderId}/items/{itemId}/complete")
    public ResponseEntity<Void> completePlacementItem(
            @PathVariable UUID placementOrderId,
            @PathVariable UUID itemId,
            @RequestBody(required = false) CompletePlacementItemReqDto body,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Integer defectQty = body != null ? body.getDefectQty() : null;
        String defectReason = body != null ? body.getDefectReason() : null;
        inboundService.completePlacementItem(itemId, UUID.fromString(clientId),
                userId != null ? UUID.fromString(userId) : null,
                defectQty, defectReason);
        return ResponseEntity.noContent().build();
    }

    // 적치 지시서 전체 완료 처리 → 가용재고 전환 이벤트 발행
    @CheckPermission(resource = Resource.INBOUND, action = Action.UPDATE)
    @PostMapping("/{placementOrderId}/complete")
    public ResponseEntity<Void> completePlacementOrder(
            @PathVariable UUID placementOrderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        inboundService.completePlacementOrder(placementOrderId,
                UUID.fromString(userId), UUID.fromString(clientId));
        return ResponseEntity.noContent().build();
    }
}

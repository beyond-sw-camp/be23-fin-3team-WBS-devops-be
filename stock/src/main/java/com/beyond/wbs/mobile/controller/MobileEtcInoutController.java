package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.etcinout.domain.EtcInoutStatus;
import com.beyond.wbs.etcinout.dto.EtcInoutCompletePickReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutCompleteWorkReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemProcessReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutDetailResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutListResDto;
import com.beyond.wbs.etcinout.service.EtcInoutItemService;
import com.beyond.wbs.etcinout.service.EtcInoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 앱 — 기타입고 (1:1 한 카드 완료 구조)
 *
 * <p>운영자 승인 시점에 작업자가 배정되며, 작업자는 본인 목록에서 카드 하나를 골라
 * 정상/불량 + 위치를 한 번에 입력하고 완료한다. 검수/적치 단계 분리 없음.
 */
@RestController
@RequestMapping("/mobile/etc-inout")
@RequiredArgsConstructor
public class MobileEtcInoutController {

    private final EtcInoutService etcInoutService;
    private final EtcInoutItemService etcInoutItemService;

    // 본인에게 배정된 기록 목록 (기본: approved)
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/my-list")
    public ResponseEntity<Page<EtcInoutListResDto>> myList(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) List<EtcInoutStatus> status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<EtcInoutListResDto> result = etcInoutService.getMyList(
                UUID.fromString(clientId), UUID.fromString(userId), status, pageable);
        return ResponseEntity.ok(result);
    }

    // 기록 상세 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<EtcInoutDetailResDto> detail(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(etcInoutService.getDetail(
                UUID.fromString(clientId), UUID.fromString(userId), id));
    }

    // 기록 품목 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/{id}/items")
    public ResponseEntity<List<EtcInoutItemResDto>> items(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(etcInoutItemService.getItems(UUID.fromString(clientId), id));
    }

    // 입고 완료 (정상/불량/위치 한 번에) — approved → completed
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @PostMapping("/{id}/complete-work")
    public ResponseEntity<Void> completeWork(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @RequestBody @Valid EtcInoutCompleteWorkReqDto dto) {

        etcInoutService.completeWork(
                UUID.fromString(clientId), id, UUID.fromString(userId), dto);
        return ResponseEntity.noContent().build();
    }

    // 출고 픽업 완료 (실 픽업수량만 입력) — approved → completed
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @PostMapping("/{id}/complete-pick")
    public ResponseEntity<Void> completePick(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @RequestBody @Valid EtcInoutCompletePickReqDto dto) {

        etcInoutService.completePick(
                UUID.fromString(clientId), id, UUID.fromString(userId), dto);
        return ResponseEntity.noContent().build();
    }

    /**
     * 품목 1건 처리 완료 — 입고/출고 통합.
     *
     * <p>입고: body 에 processedQty/defectQty/locationId/defectLocationId/defectReason
     * <p>출고: body 에 pickedQty
     *
     * <p>모든 품목 처리되면 order 자동 completed. WS PROCESSED + COMPLETED 발송.
     */
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @PostMapping("/{id}/items/{itemId}/process")
    public ResponseEntity<Void> processItem(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid EtcInoutItemProcessReqDto dto) {

        etcInoutService.processItem(
                UUID.fromString(clientId), id, itemId, UUID.fromString(userId), dto);
        return ResponseEntity.noContent().build();
    }
}

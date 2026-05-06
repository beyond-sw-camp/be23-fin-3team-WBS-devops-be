package com.beyond.wbs.etcinout.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.etcinout.domain.Direction;
import com.beyond.wbs.etcinout.domain.EtcInoutStatus;
import com.beyond.wbs.etcinout.domain.IoType;
import com.beyond.wbs.etcinout.dto.CancellationCandidateResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutCreateReqDto;
import com.beyond.wbs.etcinout.dto.EtcInoutDetailResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutListResDto;
import com.beyond.wbs.etcinout.dto.EtcInoutUpdateReqDto;
import com.beyond.wbs.etcinout.dto.InboundRequestEmailHistoryResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestPreviewResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendReqDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendResDto;
import com.beyond.wbs.etcinout.exception.EtcInoutStockShortageException;
import com.beyond.wbs.etcinout.service.EtcInoutItemService;
import com.beyond.wbs.etcinout.service.EtcInoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/etc-inout")
@RequiredArgsConstructor
public class EtcInoutController {

    private final EtcInoutService etcInoutService;
    private final EtcInoutItemService etcInoutItemService;

    // 기타입출고 생성
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.CREATE)
    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestBody @Valid EtcInoutCreateReqDto dto) {

        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);

        UUID orderId = etcInoutService.create(dto, userUUID, clientUUID);

        return ResponseEntity.status(HttpStatus.CREATED).body(orderId);
    }

    // 기타입출고 목록 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<Page<EtcInoutListResDto>> list(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) EtcInoutStatus status,
            @RequestParam(required = false) IoType ioType,
            @PageableDefault(size = 10) Pageable pageable) {

        UUID clientUUID = UUID.fromString(clientId);
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        Page<EtcInoutListResDto> result = etcInoutService.getList(
                clientUUID, requesterId, direction, status, ioType, pageable);

        return ResponseEntity.ok(result);
    }

    // 기타입출고 상세 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<EtcInoutDetailResDto> detail(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id) {

        UUID clientUUID = UUID.fromString(clientId);
        UUID requesterId = userId != null ? UUID.fromString(userId) : null;
        EtcInoutDetailResDto result = etcInoutService.getDetail(clientUUID, requesterId, id);

        return ResponseEntity.ok(result);
    }

    // 기타입출고 수정
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog
    @PutMapping("/update/{id}")
    public ResponseEntity<EtcInoutDetailResDto> update(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id,
            @RequestBody @Valid EtcInoutUpdateReqDto dto) {

        UUID clientUUID = UUID.fromString(clientId);
        EtcInoutDetailResDto result = etcInoutService.update(clientUUID, id, dto);

        return ResponseEntity.ok(result);
    }

    // 기타입출고 품목 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @AuditLog
    @GetMapping("/{id}/items")
    public ResponseEntity<java.util.List<EtcInoutItemResDto>> items(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        UUID clientUUID = UUID.fromString(clientId);
        java.util.List<EtcInoutItemResDto> result = etcInoutItemService.getItems(clientUUID, id);

        return ResponseEntity.ok(result);
    }

    // 품목 추가
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog
    @PostMapping("/{id}/items")
    public ResponseEntity<EtcInoutItemResDto> addItem(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id,
            @RequestBody @Valid EtcInoutItemDto dto) {

        UUID clientUUID = UUID.fromString(clientId);
        EtcInoutItemResDto result = etcInoutItemService.addItem(clientUUID, id, dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 품목 수정
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog
    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<EtcInoutItemResDto> updateItem(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid EtcInoutItemDto dto) {

        UUID clientUUID = UUID.fromString(clientId);
        EtcInoutItemResDto result = etcInoutItemService.updateItem(clientUUID, id, itemId, dto);

        return ResponseEntity.ok(result);
    }

    // 품목 삭제
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.DELETE)
    @AuditLog
    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id,
            @PathVariable UUID itemId) {

        UUID clientUUID = UUID.fromString(clientId);
        etcInoutItemService.deleteItem(clientUUID, id, itemId);

        return ResponseEntity.noContent().build();
    }

    // 기타입출고 승인 (draft → approved + 작업자 자동 배정)
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog(action = "승인")
    @PutMapping("/approve/{id}")
    public ResponseEntity<Void> approve(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id) {

        etcInoutService.approve(
                UUID.fromString(clientId), id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    // 기타입출고 완료 처리 (웹 직접 완료 — 모바일 우회 경로)
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog(action = "완료")
    @PutMapping("/complete/{id}")
    public ResponseEntity<Void> complete(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id) {

        UUID clientUUID = UUID.fromString(clientId);
        UUID userUUID = userId != null ? UUID.fromString(userId) : null;
        etcInoutService.complete(clientUUID, id, userUUID);

        return ResponseEntity.noContent().build();
    }

    // 기타입출고 취소
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.DELETE)
    @AuditLog(action = "취소")
    @PutMapping("/cancel/{id}")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        UUID clientUUID = UUID.fromString(clientId);
        etcInoutService.cancel(clientUUID, id);

        return ResponseEntity.noContent().build();
    }

    // 출고 승인 시 가용재고 부족 — 취소할 수 있는 정식 출고지시서 후보 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/{id}/cancellation-candidates")
    public ResponseEntity<java.util.List<CancellationCandidateResDto>> cancellationCandidates(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(etcInoutService.getCancellationCandidates(
                UUID.fromString(clientId), id));
    }

    // 운영자가 선택한 출고지시서들을 취소 + 기타출고와 링크 기록
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog(action = "출고지시서 취소")
    @PostMapping("/{id}/cancel-outbounds")
    public ResponseEntity<Void> cancelOutbounds(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @RequestBody java.util.List<UUID> outboundIds) {

        etcInoutService.cancelOutboundsForEtc(
                UUID.fromString(clientId), id, outboundIds, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    // 입고 요청 메일 미리보기 — 폼 초기값
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/{id}/inbound-request-preview")
    public ResponseEntity<InboundRequestPreviewResDto> previewInboundRequest(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id) {

        InboundRequestPreviewResDto result = etcInoutService.previewInboundRequestMail(
                UUID.fromString(clientId), id, UUID.fromString(userId));
        return ResponseEntity.ok(result);
    }

    // 입고 요청 메일 SMTP 발송
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.UPDATE)
    @AuditLog(action = "입고 요청 메일 발송")
    @PostMapping("/{id}/send-inbound-request")
    public ResponseEntity<InboundRequestSendResDto> sendInboundRequest(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @RequestBody(required = false) InboundRequestSendReqDto dto) {

        InboundRequestSendResDto result = etcInoutService.sendInboundRequestMail(
                UUID.fromString(clientId), id, UUID.fromString(userId), dto);
        return ResponseEntity.ok(result);
    }

    // 메일 발송 이력 조회
    @CheckPermission(resource = Resource.ETC_INOUT, action = Action.READ)
    @GetMapping("/{id}/inbound-request-history")
    public ResponseEntity<java.util.List<InboundRequestEmailHistoryResDto>> inboundRequestHistory(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(etcInoutService.getInboundRequestEmailHistory(
                UUID.fromString(clientId), id));
    }

    // 가용재고 부족 응답 핸들러 — 409 + 부족 목록 페이로드
    @ExceptionHandler(EtcInoutStockShortageException.class)
    public ResponseEntity<?> handleStockShortage(EtcInoutStockShortageException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(java.util.Map.of(
                        "error", "STOCK_SHORTAGE",
                        "message", ex.getMessage(),
                        "shortages", ex.getShortages()));
    }
}

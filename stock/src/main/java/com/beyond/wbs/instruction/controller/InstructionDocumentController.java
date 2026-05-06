package com.beyond.wbs.instruction.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentStatus;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.dto.InstructionDocumentResDto;
import com.beyond.wbs.document.instruction.dto.InstructionDocumentSummaryDto;
import com.beyond.wbs.instruction.service.InstructionDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 지시서 문서 API.
 *
 * Gateway가 JWT를 파싱해 X-Client-Id / X-User-Id 헤더로 전파하는 기존 패턴을 그대로 사용.
 * 다른 client_id의 문서는 service 레이어의 findByIdAndClientId가 차단한다.
 *
 * 두 가지 진입 동선 모두 지원:
 *  - 거래 단위: ?docType=...&sourceId=... → 발행 이력 (version DESC)
 *  - 사이드바("공식 문서함"): 모든 파라미터 옵셔널 → 회사 전체 (issuedAt DESC)
 */
@RestController
@RequestMapping("/instruction-documents")
@RequiredArgsConstructor
public class InstructionDocumentController {

    private final InstructionDocumentService instructionDocumentService;

    /**
     * presigned 다운로드 URL 발급.
     * 응답: {"url": "...", "expiresAt": "...", "doc": {...}}
     */
    @AuditLog(action = "지시서 다운로드 발급")
    @GetMapping("/{id}/download")
    public Map<String, Object> issueDownloadUrl(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Client-Id") String clientId,
        @RequestHeader(value = "X-Presign-Ttl-Seconds", required = false) Long ttlSeconds
    ) {
        UUID clientUuid = UUID.fromString(clientId);
        InstructionDocumentResDto doc = instructionDocumentService.getDownloadable(id, clientUuid);
        String url = instructionDocumentService.presignDownloadUrl(id, clientUuid);
        long ttl = ttlSeconds != null ? ttlSeconds : 300L;
        return Map.of(
            "url", url,
            "expiresAt", Instant.now().plusSeconds(ttl).toString(),
            "doc", doc
        );
    }

    @AuditLog
    @GetMapping("/{id}")
    public InstructionDocumentResDto get(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        return instructionDocumentService.getDownloadable(id, UUID.fromString(clientId));
    }

    /**
     * 지시서 문서 목록 (다조건 검색).
     *
     * 모든 필터 파라미터는 옵셔널. 미지정 시 회사 단위 전체 조회 (사이드바 진입).
     * 정렬:
     *  - sort 파라미터 명시 시 → 그쪽 우선
     *  - sourceId 있을 때     → version DESC
     *  - 그 외                 → issuedAt DESC
     */
    @AuditLog
    @GetMapping
    public Page<InstructionDocumentResDto> search(
        @RequestParam(value = "docType",  required = false) InstructionDocumentType docType,
        @RequestParam(value = "sourceId", required = false) UUID sourceId,
        @RequestParam(value = "sourceNo", required = false) String sourceNo,
        @RequestParam(value = "status",   required = false) InstructionDocumentStatus status,
        @RequestParam(value = "issuedFrom", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime issuedFrom,
        @RequestParam(value = "issuedTo",   required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime issuedTo,
        @RequestHeader("X-Client-Id") String clientId,
        Pageable pageable
    ) {
        return instructionDocumentService.search(
            UUID.fromString(clientId),
            docType, sourceId, sourceNo, status, issuedFrom, issuedTo,
            pageable);
    }

    /**
     * "공식 문서함" 사이드바 진입 시 보여줄 요약 카드용 통계.
     */
    @AuditLog
    @GetMapping("/summary")
    public InstructionDocumentSummaryDto summary(
        @RequestHeader("X-Client-Id") String clientId
    ) {
        return instructionDocumentService.summary(UUID.fromString(clientId));
    }
}

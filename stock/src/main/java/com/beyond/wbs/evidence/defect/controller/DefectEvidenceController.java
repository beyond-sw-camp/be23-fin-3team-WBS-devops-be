package com.beyond.wbs.evidence.defect.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import com.beyond.wbs.evidence.defect.domain.DefectReasonCode;
import com.beyond.wbs.evidence.defect.dto.DefectEvidenceResDto;
import com.beyond.wbs.evidence.defect.service.DefectEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 불량 사진 증거 API (서버 경유 업로드).
 *
 * 흐름:
 *  1) POST /defect-evidence (multipart) → 서버가 S3에 직접 업로드 + DB row 저장
 *  2) GET  /defect-evidence?sourceType=&sourceId=  → 목록
 *  3) GET  /defect-evidence/{id}/download  → presigned GET URL (브라우저 직접 다운로드)
 *  4) DELETE /defect-evidence/{id}  → S3 객체 + DB row 삭제
 */
@RestController
@RequestMapping("/defect-evidence")
@RequiredArgsConstructor
public class DefectEvidenceController {

    private final DefectEvidenceService service;

    /**
     * multipart 업로드 — 사진 1장 + 메타.
     *
     * Form fields:
     *  file       (required) — 사진 파일
     *  sourceType (required) — 예: INBOUND_RECEIPT_ITEM
     *  sourceId   (required) — UUID
     *  reasonCode (옵션)     — 정형 사유 코드
     *  reasonText (옵션)     — 자유 사유 메모
     */
    @AuditLog(action = "불량사진 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DefectEvidenceResDto upload(
        @RequestPart("file") MultipartFile file,
        @RequestParam("sourceType") DefectEvidenceSourceType sourceType,
        @RequestParam("sourceId") UUID sourceId,
        @RequestParam(value = "reasonCode", required = false) DefectReasonCode reasonCode,
        @RequestParam(value = "reasonText", required = false) String reasonText,
        @RequestHeader("X-Client-Id") String clientId,
        @RequestHeader("X-User-Id")   String userId
    ) {
        return service.upload(
            file, sourceType, sourceId, reasonCode, reasonText,
            UUID.fromString(clientId), UUID.fromString(userId));
    }

    @AuditLog
    @GetMapping
    public List<DefectEvidenceResDto> list(
        @RequestParam("sourceType") DefectEvidenceSourceType sourceType,
        @RequestParam("sourceId")   UUID sourceId,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        return service.list(UUID.fromString(clientId), sourceType, sourceId);
    }

    @AuditLog
    @GetMapping("/{id}")
    public DefectEvidenceResDto get(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        return service.get(id, UUID.fromString(clientId));
    }

    /**
     * 다운로드용 presigned GET URL 발급.
     * 브라우저가 S3에 직접 GET — 서버 대역폭 0.
     */
    @AuditLog(action = "불량사진 다운로드 발급")
    @GetMapping("/{id}/download")
    public Map<String, Object> issueDownloadUrl(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        UUID clientUuid = UUID.fromString(clientId);
        DefectEvidenceResDto doc = service.get(id, clientUuid);
        String url = service.presignDownload(id, clientUuid);
        return Map.of(
            "url", url,
            "expiresAt", Instant.now().plusSeconds(300).toString(),
            "doc", doc
        );
    }

    @AuditLog(action = "불량사진 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        service.delete(id, UUID.fromString(clientId));
        return ResponseEntity.noContent().build();
    }
}

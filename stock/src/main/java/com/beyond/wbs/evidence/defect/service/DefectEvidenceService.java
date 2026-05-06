package com.beyond.wbs.evidence.defect.service;

import com.beyond.wbs.evidence.defect.domain.DefectEvidence;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceStatus;
import com.beyond.wbs.evidence.defect.domain.DefectReasonCode;
import com.beyond.wbs.evidence.defect.dto.DefectEvidenceResDto;
import com.beyond.wbs.evidence.defect.repository.DefectEvidenceRepository;
import com.beyond.wbs.evidence.defect.s3.DefectEvidenceS3Uploader;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 불량 사진 증거 오케스트레이션 (서버 경유 업로드).
 *
 * 흐름:
 *  1) 클라이언트 multipart POST → 서버 수신
 *  2) mime/size/장수 검증
 *  3) S3에 PUT (서버가 직접)
 *  4) DefectEvidence row 저장 (status=READY)
 *  5) 응답
 *
 * 트랜잭션 경계:
 *  - mime/size/장수 검증 실패 → 400 (S3 호출 안 됨)
 *  - S3 PUT 실패  → 예외 → 트랜잭션 롤백 → DB row 미생성 (정합)
 *  - S3 PUT 성공 후 DB save 실패 → S3 객체는 남음(orphan). 드물게 발생, 별도 정리 cron 검토.
 */
@Service
@Slf4j
public class DefectEvidenceService {

    private final DefectEvidenceRepository repository;
    private final DefectEvidenceS3Uploader s3Uploader;
    private final long maxFileSizeBytes;
    private final int maxPerSource;
    private final Set<String> allowedMimeTypes;

    public DefectEvidenceService(
        DefectEvidenceRepository repository,
        DefectEvidenceS3Uploader s3Uploader,
        @Value("${defect-evidence.max-file-size-bytes:5242880}") long maxFileSizeBytes,
        @Value("${defect-evidence.max-per-source:5}") int maxPerSource,
        @Value("${defect-evidence.allowed-mime-types:image/jpeg,image/png,image/webp,image/heic,image/heif}")
        String allowedMimeCsv
    ) {
        this.repository = repository;
        this.s3Uploader = s3Uploader;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxPerSource = maxPerSource;
        this.allowedMimeTypes = Arrays.stream(allowedMimeCsv.split(","))
            .map(String::trim).map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    /**
     * 사진 1장 업로드 (서버 경유).
     */
    @Transactional
    public DefectEvidenceResDto upload(
        MultipartFile file,
        DefectEvidenceSourceType sourceType,
        UUID sourceId,
        DefectReasonCode reasonCode,
        String reasonText,
        UUID clientId,
        UUID userId
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }
        String mimeType = normalizeMime(file.getContentType());
        validateMime(mimeType);
        validateSize(file.getSize());
        validateCountLimit(clientId, sourceType, sourceId);

        UUID evidenceId = UuidCreator.getTimeOrderedEpoch();
        String s3Key = s3Uploader.buildKey(clientId, sourceType, sourceId, evidenceId, mimeType);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽을 수 없습니다.", e);
        }

        // S3 업로드 (실패 시 예외 → 트랜잭션 롤백 → DB row 미생성)
        // TODO: 운영 진입 시 이미지 압축(JPEG quality·resize)·EXIF 제거 추가
        DefectEvidenceS3Uploader.UploadResult uploaded =
            s3Uploader.uploadBytes(s3Key, mimeType, bytes);

        DefectEvidence row = DefectEvidence.builder()
            .id(evidenceId)
            .clientId(clientId)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .s3Key(uploaded.s3Key())
            .mimeType(mimeType)
            .fileSize(uploaded.fileSize())
            .sha256(uploaded.sha256())
            .reasonCode(reasonCode)
            .reasonText(reasonText)
            .status(DefectEvidenceStatus.READY)
            .uploadedBy(userId)
            .build();
        repository.save(row);

        log.info("[DefectEvidence] uploaded clientId={} sourceType={} sourceId={} sizeKB={} sha256={}",
            clientId, sourceType, sourceId, uploaded.fileSize() / 1024,
            uploaded.sha256().substring(0, 12));

        return DefectEvidenceResDto.from(row);
    }

    @Transactional(readOnly = true)
    public List<DefectEvidenceResDto> list(UUID clientId,
                                           DefectEvidenceSourceType sourceType,
                                           UUID sourceId) {
        return repository
            .findByClientIdAndSourceTypeAndSourceIdOrderByUploadedAtAsc(clientId, sourceType, sourceId)
            .stream().map(DefectEvidenceResDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DefectEvidenceResDto get(UUID id, UUID clientId) {
        DefectEvidence row = repository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("불량 증거 파일을 찾을 수 없습니다."));
        return DefectEvidenceResDto.from(row);
    }

    public String presignDownload(UUID id, UUID clientId) {
        DefectEvidence row = repository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("불량 증거 파일을 찾을 수 없습니다."));
        return s3Uploader.presignDownload(row.getS3Key());
    }

    @Transactional
    public void delete(UUID id, UUID clientId) {
        DefectEvidence row = repository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("불량 증거 파일을 찾을 수 없습니다."));
        try {
            s3Uploader.delete(row.getS3Key());
        } catch (Exception e) {
            log.warn("[DefectEvidence] S3 삭제 실패 (DB row 는 삭제 진행) key={} : {}",
                row.getS3Key(), e.getMessage());
        }
        repository.delete(row);
    }

    /**
     * 입고전표 PDF 등에서 사용하는 카운트 헬퍼.
     */
    @Transactional(readOnly = true)
    public long countReady(UUID clientId, DefectEvidenceSourceType sourceType, List<UUID> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return 0;
        return repository.countReadyBySourceIds(clientId, sourceType, sourceIds);
    }

    // ────────────── 검증 ──────────────

    private String normalizeMime(String contentType) {
        if (contentType == null) return null;
        // 일부 브라우저가 "; charset=..." 등 부속 정보를 붙이는 경우 정리
        int sep = contentType.indexOf(';');
        return (sep > 0 ? contentType.substring(0, sep) : contentType).trim().toLowerCase();
    }

    private void validateMime(String mimeType) {
        if (mimeType == null || !allowedMimeTypes.contains(mimeType)) {
            throw new IllegalArgumentException(
                "허용되지 않은 mime 타입입니다: " + mimeType + " (허용: " + allowedMimeTypes + ")");
        }
    }

    private void validateSize(long fileSize) {
        if (fileSize > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                "파일 크기 한도(" + maxFileSizeBytes + " bytes) 초과: " + fileSize);
        }
    }

    private void validateCountLimit(UUID clientId, DefectEvidenceSourceType sourceType, UUID sourceId) {
        long current = repository.countByClientIdAndSourceTypeAndSourceId(clientId, sourceType, sourceId);
        if (current >= maxPerSource) {
            throw new IllegalStateException(
                "최대 첨부 장수(" + maxPerSource + ")를 초과했습니다. 기존 사진을 삭제 후 다시 시도하세요.");
        }
    }
}

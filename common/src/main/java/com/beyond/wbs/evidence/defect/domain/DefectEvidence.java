package com.beyond.wbs.evidence.defect.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 불량 발생 시점에 작업자가 첨부하는 사진 1장의 메타.
 *
 * 바이너리는 S3에 저장(브라우저 → S3 직접 PUT). 백엔드는 메타만 다룸.
 * 회사 격리는 client_id로, 도메인 무관 polymorphic 참조는 (source_type, source_id)로.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(
    name = "defect_evidence",
    indexes = {
        @Index(name = "idx_def_evi_lookup",
               columnList = "client_id, source_type, source_id"),
        @Index(name = "idx_def_evi_status",
               columnList = "client_id, status"),
        @Index(name = "idx_def_evi_uploader",
               columnList = "client_id, uploaded_by")
    }
)
public class DefectEvidence {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private DefectEvidenceSourceType sourceType;

    @Column(name = "source_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sourceId;

    // S3 키 — `{clientId}/{sourceType}/{yyyy-MM}/{sourceId}/{evidenceId}.{ext}`
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    // 업로드 시 클라이언트가 알린 mime (서버에서 검증은 안 함, 1차 PoC)
    @Column(name = "mime_type", length = 50)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    // READY 단계에서 백엔드가 S3 HeadObject로 받아 저장 (선택)
    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 40)
    private DefectReasonCode reasonCode;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefectEvidenceStatus status;

    @Column(name = "uploaded_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = DefectEvidenceStatus.PENDING;
        }
    }

    public void markReady(Long fileSize, String sha256) {
        this.status = DefectEvidenceStatus.READY;
        if (fileSize != null) this.fileSize = fileSize;
        if (sha256 != null)   this.sha256   = sha256;
    }

    public void markOrphan() {
        this.status = DefectEvidenceStatus.ORPHAN;
    }

    public void updateReason(DefectReasonCode reasonCode, String reasonText) {
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
    }
}

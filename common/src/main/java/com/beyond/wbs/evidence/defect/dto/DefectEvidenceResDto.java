package com.beyond.wbs.evidence.defect.dto;

import com.beyond.wbs.evidence.defect.domain.DefectEvidence;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceStatus;
import com.beyond.wbs.evidence.defect.domain.DefectReasonCode;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record DefectEvidenceResDto(
    UUID id,
    UUID clientId,
    DefectEvidenceSourceType sourceType,
    UUID sourceId,
    String mimeType,
    Long fileSize,
    String sha256,
    DefectReasonCode reasonCode,
    String reasonCodeName,
    String reasonText,
    DefectEvidenceStatus status,
    UUID uploadedBy,
    LocalDateTime uploadedAt
) {
    public static DefectEvidenceResDto from(DefectEvidence e) {
        return DefectEvidenceResDto.builder()
            .id(e.getId())
            .clientId(e.getClientId())
            .sourceType(e.getSourceType())
            .sourceId(e.getSourceId())
            .mimeType(e.getMimeType())
            .fileSize(e.getFileSize())
            .sha256(e.getSha256())
            .reasonCode(e.getReasonCode())
            .reasonCodeName(e.getReasonCode() != null ? e.getReasonCode().getDisplayName() : null)
            .reasonText(e.getReasonText())
            .status(e.getStatus())
            .uploadedBy(e.getUploadedBy())
            .uploadedAt(e.getUploadedAt())
            .build();
    }
}

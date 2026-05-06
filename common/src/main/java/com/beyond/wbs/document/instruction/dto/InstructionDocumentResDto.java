package com.beyond.wbs.document.instruction.dto;

import com.beyond.wbs.document.instruction.domain.InstructionDocument;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentStatus;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InstructionDocumentResDto(
    UUID id,
    UUID clientId,
    InstructionDocumentType docType,
    String docTypeName,
    UUID sourceId,
    String sourceNo,
    int version,
    InstructionDocumentStatus status,
    Long fileSize,
    String sha256,
    UUID issuedBy,
    LocalDateTime issuedAt,
    UUID reissuedFromId,
    String errorMessage
) {
    public static InstructionDocumentResDto from(InstructionDocument d) {
        return InstructionDocumentResDto.builder()
            .id(d.getId())
            .clientId(d.getClientId())
            .docType(d.getDocType())
            .docTypeName(d.getDocType().getDisplayName())
            .sourceId(d.getSourceId())
            .sourceNo(d.getSourceNo())
            .version(d.getVersion())
            .status(d.getStatus())
            .fileSize(d.getFileSize())
            .sha256(d.getSha256())
            .issuedBy(d.getIssuedBy())
            .issuedAt(d.getIssuedAt())
            .reissuedFromId(d.getReissuedFromId())
            .errorMessage(d.getErrorMessage())
            .build();
    }
}

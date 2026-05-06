package com.beyond.wbs.document.instruction.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(
    name = "instruction_document",
    indexes = {
        @Index(name = "idx_instr_doc_lookup",
               columnList = "client_id, doc_type, source_id, version DESC"),
        @Index(name = "idx_instr_doc_status",
               columnList = "client_id, status")
    }
)
public class InstructionDocument {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private InstructionDocumentType docType;

    // 원본 도메인 엔티티 PK (예: outbound_orders.id)
    @Column(name = "source_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sourceId;

    // 원본 도메인 엔티티 비즈니스 번호 (예: SO-00001)
    @Column(name = "source_no", nullable = false, length = 30)
    private String sourceNo;

    // 동일 sourceId의 발행 회차. 첫 발행=1, 재발행=2,3...
    @Column(nullable = false)
    private int version;

    // S3 키 — `{clientId}/{docType.code}/{yyyy-MM}/{sourceId}_v{version}.pdf`
    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "file_size")
    private Long fileSize;

    // 본문 byte[]의 SHA-256 (hex). 멱등성·중복 발행 방지에 사용.
    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstructionDocumentStatus status;

    @Column(name = "issued_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    // 재발행 추적: 직전 발행본의 InstructionDocument.id
    @Column(name = "reissued_from_id", columnDefinition = "BINARY(16)")
    private UUID reissuedFromId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // 동시 발행 방어 (낙관적 락)
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = InstructionDocumentStatus.GENERATING;
        }
    }

    public void markReady(String s3Key, long fileSize, String sha256) {
        this.s3Key = s3Key;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.status = InstructionDocumentStatus.READY;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = InstructionDocumentStatus.FAILED;
        this.errorMessage = truncate(errorMessage, 1000);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

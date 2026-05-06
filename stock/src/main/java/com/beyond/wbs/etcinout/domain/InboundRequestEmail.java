package com.beyond.wbs.etcinout.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기타출고 [입고 요청] 메일 발송 이력.
 *
 * <p>운영자가 가용재고 부족으로 OMS 팀에 입고 요청 메일을 보낸 기록.
 * 발송 성공/실패 모두 저장하여 추적 가능.
 */
@Entity
@Table(name = "inbound_request_emails",
        indexes = {
                @Index(name = "idx_ire_etc_order", columnList = "etc_order_id"),
                @Index(name = "idx_ire_sent_at", columnList = "sent_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InboundRequestEmail {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    @Column(name = "etc_order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID etcOrderId;

    // 발송 트리거한 운영자
    @Column(name = "sent_by", columnDefinition = "BINARY(16)", nullable = false)
    private UUID sentBy;

    @Column(name = "sent_by_name", length = 100)
    private String sentByName;

    @Column(name = "sent_by_email", length = 200)
    private String sentByEmail;

    // 실제 SMTP From 주소 (예: tjssudmsrud@gmail.com)
    @Column(name = "sender_address", length = 200, nullable = false)
    private String senderAddress;

    // 받는 사람
    @Column(name = "recipient", length = 200, nullable = false)
    private String recipient;

    @Column(name = "subject", length = 300, nullable = false)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    // SMTP 응답 message-id (디버그/추적용)
    @Column(name = "smtp_message_id", length = 300)
    private String smtpMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InboundRequestEmailStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

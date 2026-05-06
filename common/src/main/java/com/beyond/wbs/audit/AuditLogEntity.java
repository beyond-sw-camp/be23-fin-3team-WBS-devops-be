package com.beyond.wbs.audit;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 감사 로그 엔티티.
 * 각 모듈의 DB에 audit_logs 테이블로 자동 생성된다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "user_name", length = 50)
    private String userName;

    // 행위: 생성, 조회, 수정, 삭제, 승인, 취소 등
    @Column(length = 30, nullable = false)
    private String action;

    // HTTP Method: GET, POST, PATCH, PUT, DELETE
    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    // 요청 URI: /inbound-orders/create
    @Column(name = "request_uri", length = 255, nullable = false)
    private String requestUri;

    // 대상 엔티티명: InboundController → inbound
    @Column(name = "entity_name", length = 50)
    private String entityName;

    // 요청 body (JSON)
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    // HTTP 응답 코드
    @Column(name = "response_status")
    private Integer responseStatus;

    // 요청 IP
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // 처리 시간 (ms)
    @Column(name = "duration_ms")
    private Long durationMs;

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

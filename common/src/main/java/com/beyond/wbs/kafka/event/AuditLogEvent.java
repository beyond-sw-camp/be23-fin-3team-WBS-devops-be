package com.beyond.wbs.kafka.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 감사 로그 Kafka 이벤트 DTO.
 *
 * AuditLogAspect → Kafka "audit.created" 토픽으로 발행.
 * search-service 의 AuditLogEventConsumer 가 수신하여 ES 색인.
 *
 * requestBody 는 알림 이벤트(AlertAuditLogger) 의 payload(JSON) 운반에 필요.
 * 일반 audit 의 requestBody 는 검색 가치가 낮지만 필드만 있고 비워두면 무해.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class AuditLogEvent {
    private UUID id;
    private UUID clientId;
    private UUID userId;
    private String serviceName;
    private String userName;
    private String action;
    private String httpMethod;
    private String requestUri;
    private String entityName;
    private Integer responseStatus;
    private String ipAddress;
    private Long durationMs;
    private LocalDateTime createdAt;
    /** 알림 이벤트의 payload(JSON 직렬화). 일반 audit 에선 null/비어있을 수 있음. */
    private String requestBody;
}

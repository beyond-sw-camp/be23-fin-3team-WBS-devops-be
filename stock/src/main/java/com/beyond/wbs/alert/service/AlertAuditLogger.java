package com.beyond.wbs.alert.service;

import com.beyond.wbs.audit.AuditLogEntity;
import com.beyond.wbs.audit.AuditLogEventPublisher;
import com.beyond.wbs.audit.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 알림 이벤트 → audit_log 저장 + ES 인덱싱 publish.
 *
 * Why: WebSocket 발행 시점에 이벤트 이력을 audit_log 에도 1행 남겨 NotificationPage 가
 *      시간순 조회할 수 있게 한다 (Redis 는 현재 상태 스냅샷만, 이력은 audit/ES 가 담당).
 *
 * AuditLogAspect 가 다루는 일반 컨트롤러 호출과 달리 이 흐름은 시스템 트리거라
 * userId/userName/HTTP 컨텍스트가 없다. action + requestBody(payload JSON) 로만 의미를 보존한다.
 *
 * 실패 시 throw 하지 않음 — 알림 push 실패가 진행 중인 비즈니스 트랜잭션을 막으면 안 됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertAuditLogger {

    /** action 라벨 — 다른 audit action(생성/승인/취소...) 과 한국어 톤 통일 */
    public static final String ACTION_LOW_STOCK_ADDED = "재고부족발생";
    public static final String ACTION_LOW_STOCK_RESOLVED = "재고부족해소";
    public static final String ACTION_SO_SHORTAGE_ADDED = "출고불가발생";
    public static final String ACTION_SO_SHORTAGE_RESOLVED = "출고불가해소";

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEventPublisher auditLogEventPublisher;
    private final ObjectMapper objectMapper;

    public void log(String action, UUID clientId, Object payload) {
        try {
            String body;
            try {
                body = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                body = String.valueOf(payload);
            }
            AuditLogEntity saved = auditLogRepository.save(AuditLogEntity.builder()
                    .clientId(clientId)
                    .action(action)
                    .httpMethod("EVENT")
                    .requestUri("/alert/event")
                    .entityName("alert")
                    .requestBody(body)
                    .responseStatus(200)
                    .build());
            auditLogEventPublisher.publish(saved);
        } catch (Exception e) {
            log.warn("[Alert audit] 저장 실패 action={} clientId={} - {}",
                    action, clientId, e.getMessage());
        }
    }
}

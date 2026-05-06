package com.beyond.wbs.audit;

import com.beyond.wbs.kafka.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 Kafka Consumer.
 *
 * master/stock/account 서비스에서 발행된 "audit.created" 이벤트를 수신하여
 * Elasticsearch에 색인한다.
 *
 * CQRS 패턴:
 *   Command 측 (각 서비스) → RDB 저장 + Kafka 발행
 *   Query 측 (이 서비스) → Kafka 수신 + ES 색인 + 검색 API 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventConsumer {

    private final AuditLogSearchService searchService;

    @KafkaListener(topics = "audit.created", groupId = "search-group")
    public void handleAuditCreated(AuditLogEvent event) {
        try {
            searchService.index(event);
            log.debug("[ES 색인] audit.created | action={}, user={}, uri={}",
                    event.getAction(), event.getUserName(), event.getRequestUri());
        } catch (Exception e) {
            log.error("[ES 색인 실패] audit.created | error={}", e.getMessage(), e);
            // ES 색인 실패해도 RDB에 원본 있으므로 데이터 유실 없음
        }
    }
}

package com.beyond.wbs.audit;

import com.beyond.wbs.kafka.event.AuditLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditLogEventPublisher {

    private static final String TOPIC = "audit.created";

    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final Environment environment;

    public AuditLogEventPublisher(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
                                  Environment environment) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.environment = environment;
    }

    public void publish(AuditLogEntity savedLog) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null || savedLog == null) return;

        try {
            AuditLogEvent event = AuditLogEvent.builder()
                    .id(savedLog.getId())
                    .clientId(savedLog.getClientId())
                    .userId(savedLog.getUserId())
                    .serviceName(environment.getProperty("spring.application.name", "unknown-service"))
                    .userName(savedLog.getUserName())
                    .action(savedLog.getAction())
                    .httpMethod(savedLog.getHttpMethod())
                    .requestUri(savedLog.getRequestUri())
                    .entityName(savedLog.getEntityName())
                    .responseStatus(savedLog.getResponseStatus())
                    .ipAddress(savedLog.getIpAddress())
                    .durationMs(savedLog.getDurationMs())
                    .createdAt(savedLog.getCreatedAt())
                    .requestBody(savedLog.getRequestBody())
                    .build();

            String messageKey = savedLog.getId() != null ? savedLog.getId().toString() : null;
            kafkaTemplate.send(TOPIC, messageKey, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[감사로그] Kafka 발행 실패 - RDB에는 정상 저장됨: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("[감사로그] Kafka 발행 실패 - RDB에는 정상 저장됨: {}", e.getMessage());
        }
    }
}

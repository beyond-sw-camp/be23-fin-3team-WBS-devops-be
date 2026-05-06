package com.beyond.wbs.instruction.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 지시서 발행용 Kafka 토픽 선언.
 *
 * 부팅 시 Spring Boot의 KafkaAdmin이 이 NewTopic 빈들을 발견하고
 * 브로커에 토픽이 없으면 자동 생성한다 (이미 있으면 그대로 둠).
 *
 * 토픽 4개:
 *  - instruction.issued            : 메인. 도메인 approve 후 발행되는 메시지 수신.
 *  - instruction.issued-retry-0    : @RetryableTopic 1차 재시도 큐 (500ms 백오프).
 *  - instruction.issued-retry-1    : @RetryableTopic 2차 재시도 큐 (1000ms 백오프).
 *  - instruction.issued.dlq        : 최종 실패 메시지가 모이는 DLQ.
 *
 * 파티션 / 복제:
 *  - 메인+재시도: 3 partitions — sourceId 키 기반 분산. PDF 렌더가 상대적으로 무거우므로 병렬 처리.
 *  - DLQ:        1 partition  — 운영자 수동 처리용이라 분산 불필요.
 *  - replicas:   1            — 시연/개발 환경(application.yml 의 streams.replication.factor 와 일치).
 *                              운영 클러스터에서는 3으로 상향.
 */
@Configuration
public class InstructionDocumentTopicConfig {

    @Value("${instruction-document.kafka.topic}")
    private String topic;

    @Value("${instruction-document.kafka.dlq-topic}")
    private String dlqTopic;

    @Bean
    public NewTopic instructionIssuedTopic() {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic instructionIssuedRetry0() {
        return TopicBuilder.name(topic + "-retry-0").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic instructionIssuedRetry1() {
        return TopicBuilder.name(topic + "-retry-1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic instructionIssuedDlq() {
        return TopicBuilder.name(dlqTopic).partitions(1).replicas(1).build();
    }
}

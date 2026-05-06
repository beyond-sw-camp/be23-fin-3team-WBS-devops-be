package com.beyond.wbs.streams;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 통계/대시보드 이벤트 토픽 선언.
 *
 * 부팅 시 Spring Boot 의 KafkaAdmin 이 이 NewTopic 빈들을 발견해서
 * 브로커에 토픽이 없으면 자동 생성한다 (이미 있으면 그대로 둠).
 *
 * Streams 토폴로지가 시작될 때 이 토픽들이 모두 존재해야 정상 구동.
 * (없으면 MissingSourceTopicException 으로 Streams 클라이언트 SHUTDOWN_CLIENT)
 *
 * partitions 3 / replicas 1 — 시연/개발 환경.
 * - partitions 3: 안전 마진 + 운영 표준 패턴 (늘리는 건 쉽지만 줄이는 건 불가)
 * - replicas 1: 단일 broker 환경에서 강제됨. 운영 클러스터 가면 3 으로 상향.
 */
@Configuration
public class StatsTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    // ── 입고 ──
    @Bean public NewTopic inboundCreatedTopic()        { return topic("inbound.created"); }
    @Bean public NewTopic inboundApprovedTopic()       { return topic("inbound.approved"); }
    @Bean public NewTopic inboundCancelledTopic()      { return topic("inbound.cancelled"); }
    @Bean public NewTopic inboundInspectedTopic()      { return topic("inbound.inspected"); }
    @Bean public NewTopic inboundDefectTopic()         { return topic("inbound.defect"); }
    @Bean public NewTopic inboundPlacedTopic()         { return topic("inbound.placed"); }
    @Bean public NewTopic inboundOrderCompletedTopic() { return topic("inbound.order-completed"); }

    // ── 출고 ──
    @Bean public NewTopic outboundCreatedTopic()   { return topic("outbound.created"); }
    @Bean public NewTopic outboundApprovedTopic()  { return topic("outbound.approved"); }
    @Bean public NewTopic outboundCancelledTopic() { return topic("outbound.cancelled"); }
    @Bean public NewTopic outboundCompletedTopic() { return topic("outbound.completed"); }

    // ── 이동 ──
    @Bean public NewTopic transferCreatedTopic()   { return topic("transfer.created"); }
    @Bean public NewTopic transferApprovedTopic()  { return topic("transfer.approved"); }
    @Bean public NewTopic transferCancelledTopic() { return topic("transfer.cancelled"); }
    @Bean public NewTopic transferOutTopic()       { return topic("transfer.out"); }
    @Bean public NewTopic transferInTopic()        { return topic("transfer.in"); }
    @Bean public NewTopic transferCompletedTopic() { return topic("transfer.completed"); }

    // ── 기타입출고 ──
    @Bean public NewTopic etcinoutCreatedTopic()   { return topic("etcinout.created"); }
    @Bean public NewTopic etcinoutApprovedTopic()  { return topic("etcinout.approved"); }
    @Bean public NewTopic etcinoutCancelledTopic() { return topic("etcinout.cancelled"); }
    @Bean public NewTopic etcinoutCompletedTopic() { return topic("etcinout.completed"); }
}

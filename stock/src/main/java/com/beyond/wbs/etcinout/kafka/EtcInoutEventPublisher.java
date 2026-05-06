package com.beyond.wbs.etcinout.kafka;

import com.beyond.wbs.kafka.event.EtcInoutStockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 기타 입출고 이벤트 발행자
 *
 * EtcInoutService 에서 호출. 통계/대시보드 카운트 전용 — 재고 변동에는 영향 없음.
 * 멀티테넌시: 페이로드의 clientId 로 회사 분리.
 */
@Slf4j
@Component
public class EtcInoutEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public EtcInoutEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(EtcInoutStockEvent event) {
        log.info("[Kafka 발행] etcinout.created | refId={}", event.getRefId());
        kafkaTemplate.send("etcinout.created", event.getRefId().toString(), event);
    }

    public void publishApproved(EtcInoutStockEvent event) {
        log.info("[Kafka 발행] etcinout.approved | refId={}", event.getRefId());
        kafkaTemplate.send("etcinout.approved", event.getRefId().toString(), event);
    }

    public void publishCancelled(EtcInoutStockEvent event) {
        log.info("[Kafka 발행] etcinout.cancelled | refId={}", event.getRefId());
        kafkaTemplate.send("etcinout.cancelled", event.getRefId().toString(), event);
    }

    public void publishCompleted(EtcInoutStockEvent event) {
        log.info("[Kafka 발행] etcinout.completed | refId={}", event.getRefId());
        kafkaTemplate.send("etcinout.completed", event.getRefId().toString(), event);
    }
}

package com.beyond.wbs.outbounds.kafka;

import com.beyond.wbs.kafka.event.OutboundStockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 출고 이벤트 발행자
 *
 * OutboundService에서 호출하여 Kafka 토픽에 이벤트를 보낸다.
 * InventoryEventConsumer가 이 이벤트를 수신하여 재고 변동 처리.
 */
@Slf4j
@Component
public class OutboundEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public OutboundEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 출고지시서 생성 이벤트 발행 (재고 영향 없음 — 통계/대시보드 카운트용)
     */
    public void publishCreated(OutboundStockEvent event) {
        log.info("[Kafka 발행] outbound.created | refId={}", event.getRefId());
        kafkaTemplate.send("outbound.created", event.getRefId().toString(), event);
    }

    /**
     * 출고지시서 승인 이벤트 발행
     * → InventoryService.reserve() 호출됨
     */
    public void publishApproved(OutboundStockEvent event) {
        log.info("[Kafka 발행] outbound.approved | refId={}", event.getRefId());
        kafkaTemplate.send("outbound.approved", event.getRefId().toString(), event);
    }

    /**
     * 출고지시서 취소 이벤트 발행
     * → InventoryService.unreserve() 호출됨
     */
    public void publishCancelled(OutboundStockEvent event) {
        log.info("[Kafka 발행] outbound.cancelled | refId={}", event.getRefId());
        kafkaTemplate.send("outbound.cancelled", event.getRefId().toString(), event);
    }

    /**
     * 출고 확정 (출고전표 생성) 이벤트 발행
     * → InventoryService.releaseOnDispatch() 호출됨
     */
    public void publishCompleted(OutboundStockEvent event) {
        log.info("[Kafka 발행] outbound.completed | refId={}", event.getRefId());
        kafkaTemplate.send("outbound.completed", event.getRefId().toString(), event);
    }
}

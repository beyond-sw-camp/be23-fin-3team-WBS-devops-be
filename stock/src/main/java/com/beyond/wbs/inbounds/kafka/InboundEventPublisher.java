package com.beyond.wbs.inbounds.kafka;

import com.beyond.wbs.kafka.event.InboundStockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 입고 이벤트 발행자
 *
 * InboundService에서 호출하여 Kafka 토픽에 이벤트를 보낸다.
 * InventoryEventConsumer가 이 이벤트를 수신하여 재고 변동 처리.
 */
@Slf4j
@Component
public class InboundEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public InboundEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 입고지시서 생성 이벤트 발행 (재고 영향 없음 — 통계/대시보드 카운트용)
     */
    public void publishCreated(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.created | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.created", event.getRefId().toString(), event);
    }

    /**
     * 입고지시서 승인 이벤트 발행
     * → InventoryService.addIncoming() 호출됨 (입고예정 ↑)
     */
    public void publishApproved(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.approved | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.approved", event.getRefId().toString(), event);
    }

    /**
     * 입고지시서 취소 이벤트 발행
     * → approved 단계 취소 시 InventoryService.removeIncoming() 호출됨 (입고예정 ↓)
     * → draft 단계 취소는 재고 영향 없음 (통계/대시보드 카운트용)
     */
    public void publishCancelled(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.cancelled | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.cancelled", event.getRefId().toString(), event);
    }

    /**
     * 검수 완료 (정상품) 이벤트 발행
     * → InventoryService.addPending() 호출됨 (입고예정 ↓ / 검수중 ↑)
     */
    public void publishInspected(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.inspected | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.inspected", event.getRefId().toString(), event);
    }

    /**
     * 검수 불량 이벤트 발행
     * → InventoryService.markDefect() 호출됨 (검수중 ↓ / 불량 ↑)
     */
    public void publishDefect(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.defect | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.defect", event.getRefId().toString(), event);
    }

    /**
     * 적치 완료 이벤트 발행
     * → InventoryService.confirmPlacement() 호출됨 (검수중 ↓ / 가용 ↑)
     */
    public void publishPlaced(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.placed | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.placed", event.getRefId().toString(), event);
    }

    /**
     * 입고지시서 마감 이벤트 발행 — 통계/대시보드 전용 (재고 영향 없음).
     * 입고지시서 status 가 completed/partial 로 transition 하는 시점에 1번만 호출.
     */
    public void publishOrderCompleted(InboundStockEvent event) {
        log.info("[Kafka 발행] inbound.order-completed | refId={}", event.getRefId());
        kafkaTemplate.send("inbound.order-completed", event.getRefId().toString(), event);
    }
}

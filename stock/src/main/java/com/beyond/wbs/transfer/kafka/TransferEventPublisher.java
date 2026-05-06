package com.beyond.wbs.transfer.kafka;

import com.beyond.wbs.kafka.event.TransferStockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 이동 이벤트 발행자
 *
 * TransferOrderService 에서 호출.
 * - 라이프사이클 이벤트(created/approved/cancelled/completed): 통계 대시보드 카운트용
 * - 물량 흐름 이벤트(out/in): 창고별 처리량 집계용 (입고/출고와 동일 패턴)
 *
 * 주의: 이동의 실제 재고 차감/증가 로직은 InventoryService 직접 호출로 이미 처리되고 있음.
 *       여기서 발행하는 이벤트는 통계/대시보드 전용이며, 재고 변동에는 영향 없음.
 *
 * 멀티테넌시: 페이로드의 clientId 로 회사 분리 (Streams key/조회 API 필터에서 사용).
 */
@Slf4j
@Component
public class TransferEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public TransferEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.created | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.created", event.getRefId().toString(), event);
    }

    public void publishApproved(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.approved | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.approved", event.getRefId().toString(), event);
    }

    public void publishCancelled(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.cancelled | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.cancelled", event.getRefId().toString(), event);
    }

    /** 출발지에서 빠짐 (PICK) — warehouseId = 출발창고 */
    public void publishOut(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.out | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.out", event.getRefId().toString(), event);
    }

    /** 도착지에 놓임 (PLACE) — warehouseId = 도착창고 */
    public void publishIn(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.in | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.in", event.getRefId().toString(), event);
    }

    public void publishCompleted(TransferStockEvent event) {
        log.info("[Kafka 발행] transfer.completed | refId={}", event.getRefId());
        kafkaTemplate.send("transfer.completed", event.getRefId().toString(), event);
    }
}

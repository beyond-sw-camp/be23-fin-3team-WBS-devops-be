package com.beyond.wbs.instruction.consumer;

import com.beyond.wbs.instruction.service.InstructionDocumentService;
import com.beyond.wbs.kafka.event.InstructionDocumentIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 지시서 발행 Kafka 컨슈머.
 *
 * 토픽: instruction.issued
 * 그룹: instruction-doc-group (기존 stock-group과 분리해 메시지 누락·재시도 격리)
 *
 * 동기 재시도 3회/지수백오프 → 모두 실패 시 DLQ(instruction.issued.dlq)로 보낸다.
 * Service가 status=FAILED 행을 남겼으므로 admin 화면에서 수동 재발행 가능.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InstructionDocumentListener {

    private final InstructionDocumentService instructionDocumentService;

    @RetryableTopic(
        attempts = "${instruction-document.retry.max-attempts:3}",
        backoff = @Backoff(
            delayExpression = "${instruction-document.retry.initial-interval-ms:500}",
            multiplierExpression = "${instruction-document.retry.multiplier:2.0}"
        ),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        dltTopicSuffix = ".dlq"
    )
    @KafkaListener(
        topics = "${instruction-document.kafka.topic}",
        groupId = "${instruction-document.kafka.consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(InstructionDocumentIssuedEvent event) {
        log.info("[InstructionDoc] received docType={} sourceNo={} clientId={}",
            event.getDocType(), event.getSourceNo(), event.getClientId());
        instructionDocumentService.issue(event);
    }
}

package com.beyond.wbs.instruction.event;

import com.beyond.wbs.document.instruction.event.InstructionIssueRequested;
import com.beyond.wbs.kafka.event.InstructionDocumentIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 → 인프라 경계 브리지.
 *
 * 어떤 도메인 서비스든 {@link InstructionIssueRequested} Spring 이벤트를 발행하면,
 * 이 클래스가 트랜잭션 커밋 후(AFTER_COMMIT)에 Kafka {@code instruction.issued} 토픽으로 publish한다.
 *
 * 이렇게 두 단계로 나누는 이유:
 *  - 트랜잭션 롤백 시 PDF 발행이 일어나지 않게 함 (정합성)
 *  - 9종 docType 모두 한 클래스로 처리 → 도메인별 publisher 보일러플레이트 제거
 *
 * 카프카 키는 sourceId.toString()로 보내 같은 source의 메시지가 같은 파티션으로 가도록 한다(순서 보장).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InstructionIssueEventBridge {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${instruction-document.kafka.topic}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueRequested(InstructionIssueRequested e) {
        InstructionDocumentIssuedEvent message = InstructionDocumentIssuedEvent.builder()
            .docType(e.docType())
            .sourceId(e.sourceId())
            .sourceNo(e.sourceNo())
            .clientId(e.clientId())
            .issuedBy(e.issuedBy())
            .build();
        try {
            kafkaTemplate.send(topic, e.sourceId().toString(), message);
            log.info("[InstructionIssue] published topic={} docType={} sourceNo={} clientId={}",
                topic, e.docType(), e.sourceNo(), e.clientId());
        } catch (Exception ex) {
            // KafkaTemplate.send는 동기 큐잉만 하므로 즉시 실패는 거의 없으나
            // 직렬화 실패 등 가능성에 대비한다. 실패해도 도메인 트랜잭션은 이미 커밋됨 — 별도 보강 큐 고려 대상.
            log.error("[InstructionIssue] publish 실패 docType={} sourceNo={} clientId={}",
                e.docType(), e.sourceNo(), e.clientId(), ex);
        }
    }
}

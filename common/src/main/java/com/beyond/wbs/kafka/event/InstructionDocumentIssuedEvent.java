package com.beyond.wbs.kafka.event;

import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

/**
 * 지시서 발행 Kafka 메시지.
 *
 * 토픽: instruction.issued (단일)
 * 키:   sourceId.toString() — 같은 source는 같은 파티션으로 가서 순서 보장
 *
 * 컨슈머는 docType으로 어느 Renderer에 dispatch할지 결정한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InstructionDocumentIssuedEvent {

    private InstructionDocumentType docType;
    private UUID sourceId;
    private String sourceNo;
    private UUID clientId;
    private UUID issuedBy;
}

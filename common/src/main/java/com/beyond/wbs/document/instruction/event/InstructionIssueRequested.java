package com.beyond.wbs.document.instruction.event;

import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;

import java.util.UUID;

/**
 * 도메인 → 인프라 경계의 Spring 애플리케이션 이벤트.
 *
 * 각 도메인 서비스(예: OutboundService.approve)가 트랜잭션 내부에서 이 이벤트를 발행하면,
 * 공통 브리지({@code InstructionIssueEventBridge})가 AFTER_COMMIT에서 받아
 * Kafka {@code instruction.issued} 토픽으로 publish한다.
 *
 * 트랜잭션이 롤백되면 발행이 일어나지 않는다 — 이게 이 두 단계 분리의 핵심.
 */
public record InstructionIssueRequested(
    InstructionDocumentType docType,
    UUID sourceId,
    String sourceNo,
    UUID clientId,
    UUID issuedBy
) {}

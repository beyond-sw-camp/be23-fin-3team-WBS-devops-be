package com.beyond.wbs.document.instruction.render;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Renderer가 PDF 헤더/푸터에 찍어야 하는 발행 메타.
 * loadData()가 도메인 데이터를 가져온다면, 이 컨텍스트는 발행 회차·행위자 등
 * 이벤트/엔티티 단계에서만 알 수 있는 값들을 전달한다.
 */
public record InstructionDocumentRenderContext(
    UUID clientId,
    UUID sourceId,
    String sourceNo,
    int version,
    UUID issuedBy,
    LocalDateTime issuedAt
) {}

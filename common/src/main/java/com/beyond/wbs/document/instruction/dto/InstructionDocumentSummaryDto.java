package com.beyond.wbs.document.instruction.dto;

import com.beyond.wbs.document.instruction.domain.InstructionDocumentStatus;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import lombok.Builder;

import java.util.Map;

/**
 * "공식 문서함" 사이드바 진입 시 보여줄 요약 통계.
 *
 * - total          : 회사의 모든 발행본 수 (모든 상태 포함)
 * - byDocType      : 9종 docType별 건수 (없는 type은 미포함, 프론트에서 0으로 처리)
 * - byStatus       : READY/GENERATING/FAILED별 건수
 * - last7DaysCount : 발행일 기준 최근 7일 건수
 */
@Builder
public record InstructionDocumentSummaryDto(
    long total,
    Map<InstructionDocumentType, Long> byDocType,
    Map<InstructionDocumentStatus, Long> byStatus,
    long last7DaysCount
) {}

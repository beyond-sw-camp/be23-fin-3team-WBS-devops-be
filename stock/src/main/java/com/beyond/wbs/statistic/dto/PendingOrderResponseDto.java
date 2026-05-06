package com.beyond.wbs.statistic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 처리 필요 지시서 패널 응답 — 미리보기용.
 *
 * items: limit 만큼 잘려서 정렬된 리스트 (DELAYED → TODAY 순, 같은 카테고리는 마감일 빠른 순)
 * summary: 전체 카운트 (필터 토글용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOrderResponseDto {
    private List<PendingOrderItemDto> items;
    private Summary summary;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long total;            // 전체 미처리 (입고+출고+이동)
        private long delayed;          // 지연
        private long today;            // 오늘 마감
        private long upcoming;         // 미래 예정 (현재 응답에서 제외 — 0)
        private long inProgress;       // 현재 작업중 (in_progress/received/placing)
        private long pendingApproval;  // 승인 대기 (draft)
    }
}

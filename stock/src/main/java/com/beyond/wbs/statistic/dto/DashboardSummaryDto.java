package com.beyond.wbs.statistic.dto;

import lombok.*;

/**
 * 대시보드 상단 KPI 집계 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DashboardSummaryDto {
    // 카드용 (모두 "할 일 / 처리 필요" 기준으로 통일)
    private long todayInboundCount;     // scheduled(expected) <= today AND 미완료 (오늘 마감 + 지연분 포함)
    private long todayOutboundCount;    // scheduled <= today AND 미완료 (오늘 마감 + 지연분 포함)
    private long stockShortageCount;    // 안전재고 미달 품번 수
    private long integratedOrderCount;  // 모든 미처리 입고+출고 (날짜 무관) — "지시서 통합" 카드
    private long pendingApprovalCount;  // 초안(draft) 상태 지시서 수
    private long newSalesOrderCount;    // "신규 주문" 카드 — 출고지시서 미생성된 ERP 수주서 수
    private long newPurchaseOrderCount; // "신규 발주" 카드 — 입고지시서 미생성된 ERP 발주서 수 (PO.status=approved)

    // 진행 현황 패널용 (한 일 기준 — 입고/출고/이동 모두 표시)
    // 입고 완료 = originType != "return" 인 적치 완료 / 반품 완료 = originType = "return" 인 적치 완료
    private long todayPlacedCount;      // 오늘 실제 입고 완료(반품 제외, 적치 완료, placement.completedAt) 건수
    private long todayDispatchedCount;  // 오늘 실제 출고된(dispatch.dispatchedAt) 건수
    private long todayTransferredCount; // 오늘 실제 이동 완료된(transfer.updatedAt + status completed/partial) 건수
    private long todayReturnedCount;    // 오늘 실제 반품 완료된(originType=return 인 placement.completedAt) 건수

    // 오늘 처리 현황 패널 — type별 진행/대기 (날짜·상태 기준 분리)
    //   진행 = status ∈ ACTIVE (in_progress/received/placing/partial 등 — 도메인별 차이), 날짜 무관
    //   대기 = expected_date <= today AND status ∈ NOT_STARTED(draft/approved) — 처리필요지시서 스코프
    private long inboundActiveCount;
    private long outboundActiveCount;
    private long transferActiveCount;
    private long returnActiveCount;
    private long inboundPendingCount;
    private long outboundPendingCount;
    private long transferPendingCount;
    private long returnPendingCount;

    // 별도 카운트 — 오늘의 이슈 패널 등에서 활용
    private long delayedOrderCount;     // scheduled < today AND 미완료 (지연만)
}

package com.beyond.wbs.statistic.service;

import com.beyond.wbs.alert.service.AlertService;
import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderStatus;
import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import com.beyond.wbs.inbounds.repository.ErpPurchaseOrderRepository;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inbounds.repository.PlacementOrderRepository;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderRepository;
import com.beyond.wbs.outbounds.repository.OutboundDispatchRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.statistic.dto.DashboardSummaryDto;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import com.beyond.wbs.transfer.repository.TransferOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 대시보드 상단 KPI 집계 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundDispatchRepository outboundDispatchRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final PlacementOrderRepository placementOrderRepository;
    private final TransferOrderRepository transferOrderRepository;
    private final ErpSalesOrderRepository erpSalesOrderRepository;
    private final ErpPurchaseOrderRepository erpPurchaseOrderRepository;
    private final AlertService alertService;

    /**
     * 미완료(open) 상태 — 카드 카운트 + 통합 카운트 + 지연 카운트 모두 사용.
     * partial 은 "부분 완료" = 완료 상태로 보므로 제외, completed/cancelled 도 당연히 제외.
     * "할 일" 의미라 draft 도 포함.
     */
    private static final List<InboundOrderStatus> INBOUND_OPEN = List.of(
            InboundOrderStatus.draft,
            InboundOrderStatus.approved,
            InboundOrderStatus.received,
            InboundOrderStatus.placing
    );
    private static final List<OutboundOrderStatus> OUTBOUND_OPEN = List.of(
            OutboundOrderStatus.draft,
            OutboundOrderStatus.approved,
            OutboundOrderStatus.in_progress
    );
    private static final List<TransferOrderStatus> TRANSFER_OPEN = List.of(
            TransferOrderStatus.draft,
            TransferOrderStatus.approved,
            TransferOrderStatus.in_progress
    );

    /** 지연 패널 전용 — 위 OPEN 에서 draft 만 추가로 제외 (승인 안 한 건 "지연" 으로 안 봄) */
    private static final List<InboundOrderStatus> INBOUND_OPEN_NO_DRAFT = List.of(
            InboundOrderStatus.approved,
            InboundOrderStatus.received,
            InboundOrderStatus.placing
    );
    private static final List<OutboundOrderStatus> OUTBOUND_OPEN_NO_DRAFT = List.of(
            OutboundOrderStatus.approved,
            OutboundOrderStatus.in_progress
    );
    private static final List<TransferOrderStatus> TRANSFER_OPEN_NO_DRAFT = List.of(
            TransferOrderStatus.approved,
            TransferOrderStatus.in_progress
    );

    /** 이동 완료 판정 — completed/partial 둘 다 그 날 마감된 것으로 본다 (transfer.updatedAt 기준). */
    private static final List<TransferOrderStatus> TRANSFER_DONE = List.of(
            TransferOrderStatus.completed,
            TransferOrderStatus.partial
    );

    /** 진행중 (현재 작업중) — 오늘 처리 현황 패널 "진행" 카운트용. 날짜 무관. */
    private static final List<InboundOrderStatus> INBOUND_ACTIVE = List.of(
            InboundOrderStatus.received,
            InboundOrderStatus.placing
    );
    private static final List<OutboundOrderStatus> OUTBOUND_ACTIVE = List.of(
            OutboundOrderStatus.in_progress
    );
    private static final List<TransferOrderStatus> TRANSFER_ACTIVE = List.of(
            TransferOrderStatus.in_progress
    );

    /** 미시작 (대기) — 오늘 처리 현황 패널 "대기" 카운트용. expected_date <= today 와 함께 사용. */
    private static final List<InboundOrderStatus> INBOUND_NOT_STARTED = List.of(
            InboundOrderStatus.draft,
            InboundOrderStatus.approved
    );
    private static final List<OutboundOrderStatus> OUTBOUND_NOT_STARTED = List.of(
            OutboundOrderStatus.draft,
            OutboundOrderStatus.approved
    );
    private static final List<TransferOrderStatus> TRANSFER_NOT_STARTED = List.of(
            TransferOrderStatus.draft,
            TransferOrderStatus.approved
    );

    private static final String ORIGIN_RETURN = "return";

    /** 승인대기(draft) 노출 임계값 — 마감 7일 이내 draft 만 KPI/패널에 노출. PendingOrderService 와 동일. */
    private static final long PENDING_APPROVAL_WITHIN_DAYS = 7;

    private static final List<InboundOrderStatus> INBOUND_DRAFT_ONLY = List.of(InboundOrderStatus.draft);
    private static final List<OutboundOrderStatus> OUTBOUND_DRAFT_ONLY = List.of(OutboundOrderStatus.draft);
    private static final List<TransferOrderStatus> TRANSFER_DRAFT_ONLY = List.of(TransferOrderStatus.draft);

    public DashboardSummaryDto getSummary(UUID clientId) {
        LocalDate today = LocalDate.now();
        LocalDate within7Days = today.plusDays(PENDING_APPROVAL_WITHIN_DAYS);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        // ── 카드 1 — 오늘 입고 ──
        // 협력사가 오늘(expected_date = today) 입고 약속한 건수, 미완료만 (이미 적치 끝난 건 제외)
        long todayInboundCount = inboundOrderRepository
                .countByClientIdAndExpectedDateAndStatusIn(clientId, today, INBOUND_OPEN);

        // ── 카드 2 — 오늘 출고 ──
        // 오늘(scheduled_date = today) 출고처로 보낼 건수, 미완료만 (이미 dispatch 끝난 건 제외)
        long todayOutboundCount = outboundOrderRepository
                .countByClientIdAndScheduledDateAndStatusIn(clientId, today, OUTBOUND_OPEN);

        // 카드 3 — 재고 부족 품번 수 (AlertService 재활용)
        long stockShortageCount = alertService.getLowStockAlerts(clientId).size();

        // 카드 4 — 지시서 통합 (입고 + 출고 + 이동, 모든 미완료, 날짜 무관)
        long integratedInbound = inboundOrderRepository.countByClientIdAndStatusIn(clientId, INBOUND_OPEN);
        long integratedOutbound = outboundOrderRepository.countByClientIdAndStatusIn(clientId, OUTBOUND_OPEN);
        long integratedTransfer = transferOrderRepository.countByClientIdAndStatusIn(clientId, TRANSFER_OPEN);
        long integratedOrderCount = integratedInbound + integratedOutbound + integratedTransfer;

        // 카드 — "신규 주문" / "신규 발주" (출고지시서/입고지시서 미생성된 ERP 수주서/발주서 수)
        //   SO: 분할 출고로 1:N 매핑 → OutboundSalesOrderLinks 활성 링크 없는 SO 카운트
        //   PO: 1:1 매핑 → 입고지시서 생성 시 status closed 로 자동 변경 → approved 카운트
        long newSalesOrderCount = erpSalesOrderRepository.countNotConverted(clientId);
        long newPurchaseOrderCount = erpPurchaseOrderRepository
                .countByClientIdAndStatus(clientId, ErpPurchaseOrderStatus.approved);

        // 카드 5 — 승인 대기 (draft + 마감 7일 이내) — 처리 필요 지시서 패널의 승인대기 탭과 동일
        long pendingApprovalInbound = inboundOrderRepository
                .countByClientIdAndExpectedDateLessThanEqualAndStatusIn(clientId, within7Days, INBOUND_DRAFT_ONLY);
        long pendingApprovalOutbound = outboundOrderRepository
                .countByClientIdAndScheduledDateLessThanEqualAndStatusIn(clientId, within7Days, OUTBOUND_DRAFT_ONLY);
        long pendingApprovalTransfer = transferOrderRepository
                .countByClientIdAndExpectedDateLessThanEqualAndStatusIn(clientId, within7Days, TRANSFER_DRAFT_ONLY);

        // 진행 현황 패널 — 오늘 실제 완료된 입고/출고/이동/반품 건수
        //   입고 완료 = originType != "return" 인 적치 / 반품 완료 = originType = "return" 인 적치
        long todayPlacedCount = placementOrderRepository
                .countCompletedByOriginTypeNot(clientId, ORIGIN_RETURN, todayStart, tomorrowStart);
        long todayReturnedCount = placementOrderRepository
                .countCompletedByOriginType(clientId, ORIGIN_RETURN, todayStart, tomorrowStart);
        long todayDispatchedCount = outboundDispatchRepository
                .countByClientIdAndDispatchedAtBetween(clientId, todayStart, tomorrowStart);
        long todayTransferredCount = transferOrderRepository
                .countByClientIdAndStatusInAndUpdatedAtBetween(clientId, TRANSFER_DONE, todayStart, tomorrowStart);

        // 오늘 처리 현황 — 진행중 카운트 (날짜 무관, status ∈ ACTIVE)
        long inboundActiveCount = inboundOrderRepository
                .countByClientIdAndOriginTypeNotAndStatusIn(clientId, ORIGIN_RETURN, INBOUND_ACTIVE);
        long returnActiveCount = inboundOrderRepository
                .countByClientIdAndOriginTypeAndStatusIn(clientId, ORIGIN_RETURN, INBOUND_ACTIVE);
        long outboundActiveCount = outboundOrderRepository.countByClientIdAndStatusIn(clientId, OUTBOUND_ACTIVE);
        long transferActiveCount = transferOrderRepository.countByClientIdAndStatusIn(clientId, TRANSFER_ACTIVE);

        // 오늘 처리 현황 — 대기 카운트 (expected_date <= today AND status ∈ NOT_STARTED)
        long inboundPendingCount = inboundOrderRepository
                .countByClientIdAndOriginTypeNotAndExpectedDateLessThanEqualAndStatusIn(
                        clientId, ORIGIN_RETURN, today, INBOUND_NOT_STARTED);
        long returnPendingCount = inboundOrderRepository
                .countByClientIdAndOriginTypeAndExpectedDateLessThanEqualAndStatusIn(
                        clientId, ORIGIN_RETURN, today, INBOUND_NOT_STARTED);
        long outboundPendingCount = outboundOrderRepository
                .countByClientIdAndScheduledDateLessThanEqualAndStatusIn(clientId, today, OUTBOUND_NOT_STARTED);
        long transferPendingCount = transferOrderRepository
                .countByClientIdAndExpectedDateLessThanEqualAndStatusIn(clientId, today, TRANSFER_NOT_STARTED);

        // 지연 패널 — scheduled < today AND 미완료 (draft 제외)
        long delayedOutbound = outboundOrderRepository.countByClientIdAndScheduledDateBeforeAndStatusIn(clientId, today, OUTBOUND_OPEN_NO_DRAFT);
        long delayedInbound = inboundOrderRepository.countByClientIdAndExpectedDateBeforeAndStatusIn(clientId, today, INBOUND_OPEN_NO_DRAFT);
        long delayedTransfer = transferOrderRepository.countByClientIdAndExpectedDateBeforeAndStatusIn(clientId, today, TRANSFER_OPEN_NO_DRAFT);

        return DashboardSummaryDto.builder()
                .todayInboundCount(todayInboundCount)
                .todayOutboundCount(todayOutboundCount)
                .stockShortageCount(stockShortageCount)
                .integratedOrderCount(integratedOrderCount)
                .pendingApprovalCount(pendingApprovalOutbound + pendingApprovalInbound + pendingApprovalTransfer)
                .newSalesOrderCount(newSalesOrderCount)
                .newPurchaseOrderCount(newPurchaseOrderCount)
                .todayPlacedCount(todayPlacedCount)
                .todayDispatchedCount(todayDispatchedCount)
                .todayTransferredCount(todayTransferredCount)
                .todayReturnedCount(todayReturnedCount)
                .inboundActiveCount(inboundActiveCount)
                .outboundActiveCount(outboundActiveCount)
                .transferActiveCount(transferActiveCount)
                .returnActiveCount(returnActiveCount)
                .inboundPendingCount(inboundPendingCount)
                .outboundPendingCount(outboundPendingCount)
                .transferPendingCount(transferPendingCount)
                .returnPendingCount(returnPendingCount)
                .delayedOrderCount(delayedOutbound + delayedInbound + delayedTransfer)
                .build();
    }
}

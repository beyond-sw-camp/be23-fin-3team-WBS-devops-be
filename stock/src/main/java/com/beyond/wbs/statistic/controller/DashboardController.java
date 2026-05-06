package com.beyond.wbs.statistic.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.statistic.dto.DashboardSummaryDto;
import com.beyond.wbs.statistic.dto.PendingOrderResponseDto;
import com.beyond.wbs.statistic.service.DashboardService;
import com.beyond.wbs.statistic.service.PendingOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 대시보드 컨트롤러
 *
 *  GET /dashboard/summary         — 5개 KPI 카드 카운트 + 진행 현황 + 지연
 *  GET /dashboard/pending-orders  — "처리 필요 지시서" 패널 (limit 미리보기)
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PendingOrderService pendingOrderService;

    @AuditLog
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getSummary(@RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(dashboardService.getSummary(UUID.fromString(clientId)));
    }

    /**
     * 처리 필요 지시서 미리보기 (입고+출고+이동 통합).
     * 카테고리 우선순위(DELAYED → TODAY → UPCOMING) 정렬, limit 만큼 잘라서 반환.
     */
    @AuditLog
    @GetMapping("/pending-orders")
    public ResponseEntity<PendingOrderResponseDto> getPendingOrders(
            @RequestParam(defaultValue = "5") int limit,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                pendingOrderService.getPendingOrders(UUID.fromString(clientId), limit));
    }
}

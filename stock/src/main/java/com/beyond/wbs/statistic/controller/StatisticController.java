package com.beyond.wbs.statistic.controller;

import com.beyond.wbs.statistic.service.StatisticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 통계 컨트롤러
 *
 * 프론트 대시보드에 표시할 통계 지표 API를 제공한다.
 *
 * 엔드포인트:
 *  1. GET /statistic/monthly-inout    — 월별 입출고 추이
 *  2. GET /statistic/daily-inout      — 일별 입출고 추이
 *  3. GET /statistic/turnover         — 재고 회전율
 *  4. GET /statistic/outbound-rank    — 품번별 출고 순위
 */
@RestController
@RequestMapping("/statistic")
public class StatisticController {

    private final StatisticService statisticService;

    @Autowired
    public StatisticController(StatisticService statisticService) {
        this.statisticService = statisticService;
    }

    /**
     * 월별 입출고 추이
     *
     * 예) GET /statistic/monthly-inout?from=2026-01&to=2026-04
     *     GET /statistic/monthly-inout?from=2026-01&to=2026-04&warehouseId=...
     */
    @AuditLog
    @GetMapping("/monthly-inout")
    public ResponseEntity<?> getMonthlyInout(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestHeader("X-Client-Id") String clientId) {
        LocalDate fromDate = parseYearMonth(from);
        LocalDate toDate = parseYearMonth(to);
        return ResponseEntity.ok(
                statisticService.getMonthlyInout(UUID.fromString(clientId), fromDate, toDate, warehouseId));
    }

    /**
     * 일별 입출고 추이
     *
     * 예) GET /statistic/daily-inout?from=2026-04-01&to=2026-04-20&warehouseId=...
     */
    @AuditLog
    @GetMapping("/daily-inout")
    public ResponseEntity<?> getDailyInout(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                statisticService.getDailyInout(UUID.fromString(clientId), from, to, warehouseId));
    }

    /**
     * 재고 회전율
     *
     * 예) GET /statistic/turnover?from=2026-01&to=2026-04
     */
    @AuditLog
    @GetMapping("/turnover")
    public ResponseEntity<?> getInventoryTurnover(
            @RequestParam String from,
            @RequestParam String to,
            @RequestHeader("X-Client-Id") String clientId) {
        LocalDate fromDate = parseYearMonth(from);
        LocalDate toDate = parseYearMonth(to);
        return ResponseEntity.ok(
                statisticService.getInventoryTurnover(UUID.fromString(clientId), fromDate, toDate));
    }

    /**
     * 품번별 출고 순위
     *
     * 예) GET /statistic/outbound-rank?from=2026-04-01&to=2026-04-20&limit=10
     */
    @AuditLog
    @GetMapping("/outbound-rank")
    public ResponseEntity<?> getProductOutboundRank(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                statisticService.getProductOutboundRank(UUID.fromString(clientId), from, to, limit));
    }

    /**
     * "yyyy-MM" 문자열을 LocalDate (해당 월 1일)로 변환한다.
     * 예) "2026-01" → 2026-01-01
     */
    private LocalDate parseYearMonth(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        return ym.atDay(1);
    }
}

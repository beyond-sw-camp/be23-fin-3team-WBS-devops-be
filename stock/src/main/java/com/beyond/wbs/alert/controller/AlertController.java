package com.beyond.wbs.alert.controller;

import com.beyond.wbs.alert.service.AlertService;
import com.beyond.wbs.alert.service.SalesOrderShortageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 알림 컨트롤러
 *
 * 재고 부족 등 운영 알림 API를 제공한다.
 *
 * 엔드포인트:
 *  GET /alert/low-stock    — 안전재고 미달 품목 현황
 *  GET /alert/so-shortage  — 현재 부족 상태인 SO 목록 (WebSocket baseline 용)
 */
@RestController
@RequestMapping("/alert")
public class AlertController {

    private final AlertService alertService;
    private final SalesOrderShortageService salesOrderShortageService;

    @Autowired
    public AlertController(AlertService alertService,
                            SalesOrderShortageService salesOrderShortageService) {
        this.alertService = alertService;
        this.salesOrderShortageService = salesOrderShortageService;
    }

    /**
     * 재고 부족 알림 현황
     *
     * 상품별 안전재고(minStockQty) 이하인 품목을 반환한다.
     * minStockQty가 0인 상품은 안전재고 미설정으로 간주하여 제외.
     *
     * 예) GET /alert/low-stock
     */
    @AuditLog
    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStockAlerts(
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                alertService.getLowStockAlerts(UUID.fromString(clientId)));
    }

    /**
     * SO 재고 부족 현황 — WebSocket 알림의 baseline 조회.
     *
     * Redis 에 저장된 현재 부족 상태 SO 목록 반환. FE 가 페이지 진입 시 한 번 호출해
     * 패널/카운트 초기화. 이후 갱신은 WebSocket /topic/admin/alerts/{clientId} 로.
     */
    @AuditLog
    @GetMapping("/so-shortage")
    public ResponseEntity<?> getSalesOrderShortages(
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                salesOrderShortageService.getCurrentShortages(UUID.fromString(clientId)));
    }
}

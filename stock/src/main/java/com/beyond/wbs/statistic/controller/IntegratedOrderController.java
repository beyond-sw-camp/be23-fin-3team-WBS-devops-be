package com.beyond.wbs.statistic.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.statistic.dto.PendingOrderItemDto;
import com.beyond.wbs.statistic.service.PendingOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 지시서 통합 페이지 — 입고/출고/이동 합쳐서 페이징/필터/정렬.
 *
 *  GET /orders/integrated
 *      ?type=ALL|INBOUND|OUTBOUND|TRANSFER
 *      &category=ALL|DELAYED|TODAY|UPCOMING
 *      &status=ALL|draft|approved|...
 *      &page=0&size=20
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class IntegratedOrderController {

    private final PendingOrderService pendingOrderService;

    @AuditLog
    @GetMapping("/integrated")
    public ResponseEntity<Page<PendingOrderItemDto>> getIntegratedOrders(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-Client-Id") String clientId) {

        List<PendingOrderItemDto> all = pendingOrderService
                .getIntegratedOrders(UUID.fromString(clientId), type, category, status);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        List<PendingOrderItemDto> sliced = all.subList(from, to);

        return ResponseEntity.ok(new PageImpl<>(sliced, pageable, all.size()));
    }
}

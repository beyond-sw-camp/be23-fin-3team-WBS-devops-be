package com.beyond.wbs.inventory.controller;

import com.beyond.wbs.inventory.domain.StockCountStatus;
import com.beyond.wbs.inventory.dtos.*;
import com.beyond.wbs.inventory.service.StockCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/stock-count")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService stockCountService;

    // 실사지시서 생성
    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestBody @Valid StockCountCreateReqDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        UUID id = stockCountService.create(dto,
                UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // 실사 시작
    @AuditLog
    @PatchMapping("/{id}/start")
    public ResponseEntity<?> start(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        stockCountService.start(id,
                UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 품목별 실사 수량 입력
    @AuditLog
    @PatchMapping("/{id}/items/{itemId}/count")
    public ResponseEntity<?> countItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid StockCountItemInputDto dto,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        StockCountItemResDto result = stockCountService.countItem(
                id, itemId, dto,
                UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok(result);
    }

    // 실사 완료 (재고 자동 조정)
    @AuditLog(action = "완료")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId) {
        stockCountService.complete(id,
                UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    // 실사 취소
    @AuditLog(action = "취소")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        stockCountService.cancel(id, UUID.fromString(clientId));
        return ResponseEntity.ok().build();
    }

    // 목록 조회
    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) StockCountStatus status,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockCountResDto> list = stockCountService.findAll(
                UUID.fromString(clientId), status, pageable);
        return ResponseEntity.ok(list);
    }

    // 상세 조회
    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                stockCountService.findById(id, UUID.fromString(clientId)));
    }

    /**
     * 실사지시서 목록 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → productIds 수집
     *   ② body 로 전달 → EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 실사지시서"만 반환
     *
     * Body 의 모든 필드 옵셔널.
     */
    @AuditLog
    @PostMapping("/search")
    public ResponseEntity<?> searchStockCounts(
            @RequestBody StockCountSearchReqDto dto,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(stockCountService.findAll(
                UUID.fromString(clientId),
                dto != null ? dto.getStatus() : null,
                dto != null ? dto.getProductIds() : null,
                pageable));
    }

    /**
     * 실사지시서 내 상품 라인 검색 — 상품 ID 필터 적용.
     *
     * 흐름: 프론트가 ProductSearchFilterModal 의 condition 으로
     *       /master-service/product/search-advanced 호출 → productIds 수집 →
     *       본 엔드포인트에 body 로 전달.
     *
     * Body: { "productIds": ["uuid1", "uuid2"] } — 비어있으면 전체 반환
     */
    @AuditLog
    @PostMapping("/{id}/items/search")
    public ResponseEntity<List<StockCountItemResDto>> searchItems(
            @PathVariable UUID id,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<StockCountItemResDto> items = stockCountService.findItemsByProductIds(
                id,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }
}
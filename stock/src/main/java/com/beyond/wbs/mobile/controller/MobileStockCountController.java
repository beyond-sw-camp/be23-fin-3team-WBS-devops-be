package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.inventory.dtos.StockCountItemInputDto;
import com.beyond.wbs.inventory.dtos.StockCountItemResDto;
import com.beyond.wbs.inventory.dtos.StockCountResDto;
import com.beyond.wbs.inventory.service.StockCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 앱 — 재고실사 (작업자용).
 *
 * <p>작업자가 폰에서:
 * 1) 본인 창고에 진행 중인 실사지시서 list 조회
 * 2) 실사지시서 상세 + 품목 list
 * 3) 위치 QR 스캔으로 그 위치 품목 빠른 접근
 * 4) 품목별 실사 수량 입력
 *
 * <p>완료 처리(in_progress → completed)는 운영자가 웹에서. 모바일은 수량 입력까지.
 */
@RestController
@RequestMapping("/mobile/stock-count")
@RequiredArgsConstructor
public class MobileStockCountController {

    private final StockCountService stockCountService;

    // 본인 회사의 진행 중 실사지시서 목록 (창고 필터 옵셔널)
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.READ)
    @GetMapping("/my-list")
    public ResponseEntity<List<StockCountResDto>> myList(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) UUID warehouseId) {

        List<StockCountResDto> result = stockCountService.findInProgressForMobile(
                UUID.fromString(clientId), warehouseId);
        return ResponseEntity.ok(result);
    }

    // 실사지시서 상세
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.READ)
    @GetMapping("/{id}")
    public ResponseEntity<StockCountResDto> detail(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(stockCountService.findById(id, UUID.fromString(clientId)));
    }

    // 품목 list (productIds null 이면 전체)
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.READ)
    @GetMapping("/{id}/items")
    public ResponseEntity<List<StockCountItemResDto>> items(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id) {

        List<StockCountItemResDto> result = stockCountService.findItemsByProductIds(
                id, null, UUID.fromString(clientId));
        return ResponseEntity.ok(result);
    }

    // 위치 QR 스캔 시 — 그 위치의 품목들
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.READ)
    @GetMapping("/{id}/items/by-location/{locationId}")
    public ResponseEntity<List<StockCountItemResDto>> itemsByLocation(
            @RequestHeader("X-Client-Id") String clientId,
            @PathVariable UUID id,
            @PathVariable UUID locationId) {

        List<StockCountItemResDto> result = stockCountService.findItemsByLocation(
                id, locationId, UUID.fromString(clientId));
        return ResponseEntity.ok(result);
    }

    // 품목별 실사 수량 입력 (웹 endpoint 와 동일 service 호출)
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.UPDATE)
    @PatchMapping("/{id}/items/{itemId}/count")
    public ResponseEntity<StockCountItemResDto> countItem(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid StockCountItemInputDto dto) {

        StockCountItemResDto result = stockCountService.countItem(
                id, itemId, dto,
                UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.ok(result);
    }

    // 실사 완료 — 작업자가 모든 위치 입력 끝나고 호출.
    // 차이 발생 품목 자동 재고 조정. 미입력 품목 있으면 거부.
    @CheckPermission(resource = Resource.STOCK_COUNT, action = Action.UPDATE)
    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID id) {

        stockCountService.complete(id, UUID.fromString(clientId), UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}

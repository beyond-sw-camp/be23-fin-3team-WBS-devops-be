package com.beyond.wbs.inventory.controller;

import com.beyond.wbs.inventory.dtos.StockAdjustReqDto;
import com.beyond.wbs.inventory.domain.RefType;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.inventory.service.InventoryRebuildService;
import com.beyond.wbs.inventory.service.InventorySnapshotService;
import com.beyond.wbs.inventory.service.PlacementPurpose;
import com.beyond.wbs.inventory.service.PlacementSuggestionService;
import com.beyond.wbs.statistic.service.StatisticService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 재고 컨트롤러 (Inventory Controller)
 *
 * 재고 조회 API와 수동 조정 API만 제공한다.
 *
 * 재고 변동(예약/적치/출고확정 등)은 직접 호출하지 않고
 * Kafka 이벤트로 들어오므로 별도 컨트롤러 엔드포인트는 없다.
 *  → InventoryService의 reserve / unreserve / addPending 등은
 *    @KafkaListener에서 호출될 예정이다.
 *
 * 모든 요청은 X-Client-Id, X-User-Id 헤더를 통해 멀티테넌트 검증.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRebuildService inventoryRebuildService;
    private final InventorySnapshotService snapshotService;
    private final PlacementSuggestionService placementSuggestionService;
    private final StatisticService statisticService;

    @Autowired
    public InventoryController(InventoryService inventoryService,
                               InventoryRebuildService inventoryRebuildService,
                               InventorySnapshotService snapshotService,
                               PlacementSuggestionService placementSuggestionService,
                               StatisticService statisticService) {
        this.inventoryService = inventoryService;
        this.inventoryRebuildService = inventoryRebuildService;
        this.snapshotService = snapshotService;
        this.placementSuggestionService = placementSuggestionService;
        this.statisticService = statisticService;
    }

    // ============================================================
    // 재고 조회
    // ============================================================

    /**
     * 창고 단위 재고 조회
     * 예) GET /inventory/warehouse/{warehouseId}
     */
    @AuditLog
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<?> getInventoriesByWarehouse(@PathVariable UUID warehouseId,
                                                       @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getInventoriesByWarehouse(warehouseId, UUID.fromString(clientId)));
    }

    /**
     * 회사 전체 재고 조회 (모든 창고 통합)
     * 재고 현황 페이지에서 창고 필터 "전체" 시 사용.
     * 예) GET /inventory/findAll
     */
    @AuditLog
    @GetMapping("/findAll")
    public ResponseEntity<?> getAllInventories(@RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getInventoriesByClient(UUID.fromString(clientId)));
    }

    /**
     * 조회일자 기준 재고 현황 (해당 날짜 시점 역산).
     *
     * 응답 포맷은 /findAll 과 동일한 InventoryResDto 리스트.
     * 수량 필드만 해당 날짜 시점의 값으로 채워진다.
     *
     * 예) GET /inventory/by-date?date=2026-04-15
     *     GET /inventory/by-date?date=2026-04-15&warehouseId=...
     */
    @AuditLog
    @GetMapping("/by-date")
    public ResponseEntity<?> getInventoriesByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID warehouseId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getInventoriesByDate(UUID.fromString(clientId), warehouseId, date));
    }

    /**
     * 주어진 location 집합 중 재고 보유 행이 하나라도 있는지 검사.
     * Master Service 의 랙 비활성화 가드에서 호출.
     */
    @PostMapping("/has-stock")
    public ResponseEntity<Boolean> hasStockInLocations(
            @RequestBody List<UUID> locationIds,
            @RequestHeader("X-Client-Id") String clientId) {
        boolean exists = inventoryService.hasStockInLocations(
                UUID.fromString(clientId), locationIds);
        return ResponseEntity.ok(exists);
    }

    /**
     * Location 별 totalQty 합계 조회.
     * Master Service 의 location maxCapacity 변경 시 데이터 무결성 검증용.
     * 응답: { locationId: totalQty } — 입력에 포함된 모든 id 가 키로 들어감 (재고 없는 곳은 0).
     */
    @PostMapping("/total-qty-by-locations")
    public ResponseEntity<Map<UUID, Integer>> getTotalQtyByLocations(
            @RequestBody List<UUID> locationIds,
            @RequestHeader("X-Client-Id") String clientId) {
        Map<UUID, Integer> totals = inventoryService.getTotalQtyByLocations(
                UUID.fromString(clientId), locationIds);
        return ResponseEntity.ok(totals);
    }

    /**
     * 창고의 랙(rack) 단위로 그룹핑된 재고·로케이션 요약 조회.
     * 프론트 "랙별 재고 조회" 탭에서 한 번의 호출로 전체 구조를 받기 위한 엔드포인트.
     *
     *  - 권한: X-Client-Id 로 Master 측 검증. 다른 회사 창고면 403.
     *  - 응답: 랙별로 그룹핑된 로케이션 + 재고 (빈 로케이션도 포함, 랙 내 floorNo 오름차순).
     *
     * 예) GET /inventory/warehouse/{warehouseId}/by-rack
     */
    @AuditLog
    @GetMapping("/warehouse/{warehouseId}/by-rack")
    public ResponseEntity<?> getInventoriesByRack(@PathVariable UUID warehouseId,
                                                  @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(inventoryService.getInventoriesByRack(warehouseId, clientId));
    }

    /**
     * 상품 단위 재고 조회
     * 같은 상품이 여러 창고/위치에 분산된 경우 모두 반환
     * 예) GET /inventory/product/{productId}
     */
    @AuditLog
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getInventoriesByProduct(@PathVariable UUID productId,
                                                     @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getInventoriesByProduct(productId, UUID.fromString(clientId)));
    }

    /**
     * 상품 재고 위치 조회 — 레이아웃 뷰어에서 상품 검색 시 사용.
     * 해당 상품이 보관된 zone/rack/location 정보를 한 번에 반환.
     *
     * 예) GET /inventory/product/{productId}/locations?warehouseId=...
     */
    @GetMapping("/product/{productId}/locations")
    public ResponseEntity<?> getProductLocations(@PathVariable UUID productId,
                                                  @RequestParam UUID warehouseId,
                                                  @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(
                inventoryService.getProductLocations(productId, warehouseId, clientId));
    }

    /**
     * 재고 row 상세 조회
     * 예) GET /inventory/detail/{inventoryId}
     */
    @AuditLog
    @GetMapping("/detail/{inventoryId}")
    public ResponseEntity<?> getInventory(@PathVariable UUID inventoryId,
                                          @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getInventory(inventoryId, UUID.fromString(clientId)));
    }

    // ============================================================
    // 재고 이력 조회
    // ============================================================

    /**
     * 지시서/원천 이벤트 단위 이력 조회 (오래된 순)
     * 예) GET /inventory/transactions/by-ref?refId=...&refType=outbound_order
     */
    @AuditLog
    @GetMapping("/transactions/by-ref")
    public ResponseEntity<?> getTransactionsByRef(@RequestParam UUID refId,
                                                  @RequestParam RefType refType,
                                                  @RequestHeader("X-User-Id") String userId,
                                                  @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getTransactionsByRef(refId, refType, UUID.fromString(userId), UUID.fromString(clientId)));
    }

    /**
     * 특정 재고의 이력 조회 (오래된 순)
     * 예) GET /inventory/{inventoryId}/transactions
     */
    @AuditLog
    @GetMapping("/{inventoryId}/transactions")
    public ResponseEntity<?> getTransactionsByInventory(@PathVariable UUID inventoryId,
                                                        @RequestHeader("X-User-Id") String userId,
                                                        @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getTransactionsByInventory(inventoryId, UUID.fromString(userId), UUID.fromString(clientId)));
    }

    /**
     * 상품 단위 이력 조회 (최신순)
     * 예) GET /inventory/product/{productId}/transactions
     */
    @AuditLog
    @GetMapping("/product/{productId}/transactions")
    public ResponseEntity<?> getTransactionsByProduct(@PathVariable UUID productId,
                                                      @RequestHeader("X-User-Id") String userId,
                                                      @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                inventoryService.getTransactionsByProduct(productId, UUID.fromString(userId), UUID.fromString(clientId)));
    }

    // ============================================================
    // 재고 수동 조정
    // ============================================================

    /**
     * 재고 수동 조정 (재고실사 결과 반영 / 분실/파손 등)
     *
     * Body 예시:
     * {
     *   "productId": "...",
     *   "warehouseId": "...",
     *   "locationId": "...",
     *   "diffQty": -5,
     *   "note": "재고실사 차이 반영"
     * }
     *
     * 예) POST /inventory/adjust
     */
    @AuditLog
    @PostMapping("/adjust")
    public ResponseEntity<?> adjust(@RequestBody @Valid StockAdjustReqDto dto,
                                    @RequestHeader("X-Client-Id") String clientId,
                                    @RequestHeader("X-User-Id") String userId) {
        UUID clientUUID = UUID.fromString(clientId);
        UUID userUUID = UUID.fromString(userId);
        inventoryService.adjust(clientUUID, dto, userUUID);
        return ResponseEntity.ok().build();
    }

    /**
     * 재고 재계산 (admin) — inventory_transactions 합계로 inventory 테이블을 다시 맞춤.
     * 코드 버그/비정상 종료 후 재고 정합성을 회복할 때 사용.
     *
     * 쿼리 파라미터:
     *   warehouseId (선택) — 특정 창고만 재계산, 없으면 전체
     *
     * 예) POST /inventory/rebuild
     *     POST /inventory/rebuild?warehouseId=...
     */
    @AuditLog(action = "재고 재계산")
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild(@RequestParam(required = false) UUID warehouseId) {
        InventoryRebuildService.RebuildResult result = inventoryRebuildService.rebuild(warehouseId);
        return ResponseEntity.ok(Map.of(
                "totalChecked", result.totalChecked(),
                "corrected", result.corrected()
        ));
    }

    // ============================================================
    // 재고 스냅샷 + 날짜별 현황
    // ============================================================

    /**
     * 수동 스냅샷 생성 (시연/테스트용)
     * 현재 시점의 재고를 이번 달 스냅샷으로 즉시 박제.
     * 예) POST /inventory/snapshots/create
     */
    @AuditLog
    @PostMapping("/snapshots/create")
    public ResponseEntity<?> createSnapshot(@RequestHeader("X-Client-Id") String clientId) {
        int created = snapshotService.createSnapshotNow(UUID.fromString(clientId));
        return ResponseEntity.ok(Map.of("created", created));
    }

    /**
     * 월별 스냅샷 조회
     * 예) GET /inventory/snapshots?month=2026-04
     */
    @AuditLog
    @GetMapping("/snapshots")
    public ResponseEntity<?> getSnapshots(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") LocalDate month,
                                          @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                snapshotService.getSnapshots(UUID.fromString(clientId), month));
    }

    /**
     * 날짜별 재고 현황 조회
     * 현재 재고에서 역산하여 과거 날짜별 가용재고 추이를 반환.
     * 프론트의 "재고 추이 그래프" 화면용.
     *
     * 예) GET /inventory/daily-status?from=2026-04-01&to=2026-04-15&warehouseId=...&productId=...
     */
    @AuditLog
    @GetMapping("/daily-status")
    public ResponseEntity<?> getDailyStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                snapshotService.getDailyStatus(UUID.fromString(clientId), warehouseId, productId, from, to));
    }

    // ============================================================
    // 적치 위치 추천
    // ============================================================

    /**
     * 적치 위치 추천
     * 입고 검수 시 "이 상품을 어디에 적치할지" 시스템이 추천해준다.
     *
     * 추천 로직:
     *  - NORMAL: 카테고리 → 구역(Zone), 협력사 → 랙(Rack) 교집합 → 동일 상품 위치 우선, 빈 위치 차선
     *  - DEFECT/RETURN/DISPOSAL: 해당 zone_type 의 zone 안에서 동일 상품 위치 우선, 빈 위치 차선
     *
     * 예) GET /inventory/suggest-location?productId=...&warehouseId=...
     *     GET /inventory/suggest-location?productId=...&warehouseId=...&purpose=DISPOSAL
     */
    @AuditLog
    @GetMapping("/suggest-location")
    public ResponseEntity<?> suggestLocation(@RequestParam UUID productId,
                                             @RequestParam UUID warehouseId,
                                             @RequestParam(required = false) Integer qty,
                                             @RequestParam(required = false) PlacementPurpose purpose,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        PlacementPurpose target = purpose != null ? purpose : PlacementPurpose.NORMAL;
        return ResponseEntity.ok(
                placementSuggestionService.suggest(productId, warehouseId, qty, clientId, target));
    }

    // ============================================================
    // 적재율 조회
    // ============================================================

    /**
     * 창고별 적재율 요약
     * 예) GET /inventory/rack-usage?warehouseId=...
     */
    @AuditLog
    @GetMapping("/rack-usage")
    public ResponseEntity<?> getRackUsage(
            @RequestParam(required = false) UUID warehouseId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                statisticService.getRackUsage(UUID.fromString(clientId), warehouseId));
    }

    /**
     * 구역별 적재율 목록
     * 예) GET /inventory/rack-usage/by-zone?warehouseId=...
     */
    @AuditLog
    @GetMapping("/rack-usage/by-zone")
    public ResponseEntity<?> getRackUsageByZone(
            @RequestParam(required = false) UUID warehouseId,
            @RequestHeader("X-Client-Id") String clientId) {
        return ResponseEntity.ok(
                statisticService.getRackUsageByZone(UUID.fromString(clientId), warehouseId));
    }
}

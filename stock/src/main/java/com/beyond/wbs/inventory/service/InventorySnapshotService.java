package com.beyond.wbs.inventory.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inventory.domain.*;
import com.beyond.wbs.inventory.dtos.DailyInventoryStatusDto;
import com.beyond.wbs.inventory.dtos.SnapshotResDto;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.inventory.repository.InventorySnapshotRepository;
import com.beyond.wbs.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 재고 스냅샷 서비스
 *
 * 세 가지 기능:
 *  1) 월별 마감 스냅샷 자동 생성 (@Scheduled 배치)
 *  2) 월별 스냅샷 조회
 *  3) 날짜별 재고 현황 조회 (현재 재고 역산 + 트랜잭션 델타)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventorySnapshotService {

    private final InventoryRepository inventoryRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MasterServiceClient masterServiceClient;

    // ============================================================
    // 1) 월별 마감 스냅샷 자동 생성 (매월 1일 새벽 2시)
    // ============================================================

    /**
     * 전월 마감 스냅샷을 자동 생성한다.
     *
     * 예) 5월 1일 새벽에 실행되면 → "4월 마감" 스냅샷 생성
     *
     * 각 Inventory 행마다:
     *  - openQty     = 전월 closeQty (없으면 0 — 첫 달이거나 신규 상품)
     *  - closeQty    = 현재(월말) 시점의 가용재고
     *  - inboundQty  = 해당 월의 순수 입고 합계 (트랜잭션 qty > 0 합산)
     *  - outboundQty = 해당 월의 순수 출고 합계 (트랜잭션 qty < 0 절대값 합산)
     */
    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void createMonthlySnapshot() {
        // 스냅샷 기준월 = 전월 1일
        // 예) 오늘이 5/1이면 → snapshotMonth = 4/1 (4월 마감)
        LocalDate snapshotMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        log.info("[스냅샷 배치] 시작: {}월 마감", snapshotMonth);

        // 해당 월의 트랜잭션을 한 번에 조회해서 상품+창고+위치별 입출고 집계
        // monthStart = 4/1 00:00:00, monthEnd = 5/1 00:00:00 → 4월 한 달 전체
        LocalDateTime monthStart = snapshotMonth.atStartOfDay();
        LocalDateTime monthEnd = snapshotMonth.plusMonths(1).atStartOfDay();
        List<InventoryTransaction> monthTxList = transactionRepository
                .findAvailableTransactionsInRange(null, monthStart, monthEnd);

        // (상품+창고+위치) 조합별로 [입고합계, 출고합계] 집계
        Map<String, int[]> txSummary = aggregateInboundOutbound(monthTxList);

        List<Inventory> allInventories = inventoryRepository.findAll();
        int created = 0;
        int skipped = 0;

        for (Inventory inv : allInventories) {
            // null-location(입고 대기 staging 행)은 스냅샷에서 제외
            if (inv.getLocationId() == null) continue;

            // 중복 체크 — 이미 해당 월 스냅샷이 있으면 스킵
            boolean exists = snapshotRepository
                    .findByClientIdAndProductIdAndWarehouseIdAndLocationIdAndSnapshotMonth(
                            inv.getClientId(), inv.getProductId(), inv.getWarehouseId(),
                            inv.getLocationId(), snapshotMonth)
                    .isPresent();
            if (exists) { skipped++; continue; }

            // 전월 closeQty → 이번 달 openQty (없으면 0 — 첫 달이거나 신규 상품)
            LocalDate prevMonth = snapshotMonth.minusMonths(1).withDayOfMonth(1);
            int openQty = snapshotRepository
                    .findByClientIdAndProductIdAndWarehouseIdAndLocationIdAndSnapshotMonth(
                            inv.getClientId(), inv.getProductId(), inv.getWarehouseId(),
                            inv.getLocationId(), prevMonth)
                    .map(InventorySnapshot::getCloseQty)
                    .orElse(0);

            // 해당 월의 입출고 합계 조회
            String key = makeKey(inv.getProductId(), inv.getWarehouseId(), inv.getLocationId());
            int[] io = txSummary.getOrDefault(key, new int[]{0, 0});

            snapshotRepository.save(InventorySnapshot.builder()
                    .clientId(inv.getClientId())
                    .productId(inv.getProductId())
                    .warehouseId(inv.getWarehouseId())
                    .locationId(inv.getLocationId())
                    .snapshotMonth(snapshotMonth)
                    .openQty(openQty)
                    .closeQty(inv.getAvailableQty())
                    .inboundQty(io[0])
                    .outboundQty(io[1])
                    .build());
            created++;
        }

        log.info("[스냅샷 배치] 완료: 생성 {}건, 스킵(중복) {}건", created, skipped);
    }

    /**
     * 수동 스냅샷 생성 (테스트·시연용)
     * 현재 시점 기준으로 이번 달 스냅샷을 즉시 생성한다.
     */
    @Transactional
    public int createSnapshotNow(UUID clientId) {
        LocalDate snapshotMonth = LocalDate.now().withDayOfMonth(1);

        // 이번 달 1일 ~ 지금까지의 트랜잭션 입출고 집계
        LocalDateTime monthStart = snapshotMonth.atStartOfDay();
        LocalDateTime monthEnd = LocalDateTime.now();
        List<InventoryTransaction> monthTxList = transactionRepository
                .findAvailableTransactionsInRange(clientId, monthStart, monthEnd);
        Map<String, int[]> txSummary = aggregateInboundOutbound(monthTxList);

        List<Inventory> allInventories = inventoryRepository.findAll();
        int created = 0;

        for (Inventory inv : allInventories) {
            if (inv.getLocationId() == null) continue;
            // 내 회사 재고만
            if (!clientId.equals(inv.getClientId())) continue;

            // 중복 스킵
            boolean exists = snapshotRepository
                    .findByClientIdAndProductIdAndWarehouseIdAndLocationIdAndSnapshotMonth(
                            clientId, inv.getProductId(), inv.getWarehouseId(),
                            inv.getLocationId(), snapshotMonth)
                    .isPresent();
            if (exists) continue;

            // 전월 closeQty → 이번 달 openQty
            LocalDate prevMonth = snapshotMonth.minusMonths(1).withDayOfMonth(1);
            int openQty = snapshotRepository
                    .findByClientIdAndProductIdAndWarehouseIdAndLocationIdAndSnapshotMonth(
                            clientId, inv.getProductId(), inv.getWarehouseId(),
                            inv.getLocationId(), prevMonth)
                    .map(InventorySnapshot::getCloseQty)
                    .orElse(0);

            // 이번 달 입출고 합계
            String key = makeKey(inv.getProductId(), inv.getWarehouseId(), inv.getLocationId());
            int[] io = txSummary.getOrDefault(key, new int[]{0, 0});

            snapshotRepository.save(InventorySnapshot.builder()
                    .clientId(clientId)
                    .productId(inv.getProductId())
                    .warehouseId(inv.getWarehouseId())
                    .locationId(inv.getLocationId())
                    .snapshotMonth(snapshotMonth)
                    .openQty(openQty)
                    .closeQty(inv.getAvailableQty())
                    .inboundQty(io[0])
                    .outboundQty(io[1])
                    .build());
            created++;
        }

        log.info("[수동 스냅샷] {}월 스냅샷 {}건 생성 (clientId={})", snapshotMonth, created, clientId);
        return created;
    }

    /**
     * 트랜잭션 목록에서 (상품+창고+위치)별 입고/출고 합계를 집계한다.
     *
     * key   = "productId|warehouseId|locationId" 문자열
     * value = int[2] → [0]=입고합계(양수 qty 합), [1]=출고합계(음수 qty 절대값 합)
     */
    private Map<String, int[]> aggregateInboundOutbound(List<InventoryTransaction> txList) {
        Map<String, int[]> summary = new HashMap<>();
        for (InventoryTransaction tx : txList) {
            String key = makeKey(tx.getProductId(), tx.getWarehouseId(), tx.getLocationId());
            int[] io = summary.computeIfAbsent(key, k -> new int[]{0, 0});
            if (tx.getQty() > 0) {
                io[0] += tx.getQty();           // 입고 (양수)
            } else {
                io[1] += Math.abs(tx.getQty()); // 출고 (음수 → 절대값)
            }
        }
        return summary;
    }

    /**
     * 집계 Map 의 키 생성 (UUID 3개를 문자열로 연결)
     */
    private String makeKey(UUID productId, UUID warehouseId, UUID locationId) {
        return productId + "|" + warehouseId + "|" + locationId;
    }

    // ============================================================
    // 2) 월별 스냅샷 조회
    // ============================================================

    /**
     * 특정 월의 마감 스냅샷 목록을 조회한다.
     * 프론트의 "월별 재고 리포트" 화면용.
     */
    @Transactional(readOnly = true)
    public List<SnapshotResDto> getSnapshots(UUID clientId, LocalDate month) {
        LocalDate snapshotMonth = month.withDayOfMonth(1);
        List<InventorySnapshot> snapshots = snapshotRepository
                .findByClientIdAndSnapshotMonth(clientId, snapshotMonth);

        // 상품명/창고명 캐싱 (N+1 방지)
        Map<UUID, String> productNameCache = new HashMap<>();
        Map<UUID, String> warehouseNameCache = new HashMap<>();

        List<SnapshotResDto> result = new ArrayList<>();
        for (InventorySnapshot s : snapshots) {
            String productName = productNameCache.computeIfAbsent(s.getProductId(), pid -> {
                try {
                    ProductResDto p = masterServiceClient.getProduct(pid, clientId.toString());
                    return p != null ? p.getName() : null;
                } catch (Exception e) { return null; }
            });
            String warehouseName = warehouseNameCache.computeIfAbsent(s.getWarehouseId(), wid -> {
                try {
                    WarehouseResDto w = masterServiceClient.getWarehouse(wid, clientId.toString());
                    return w != null ? w.getName() : null;
                } catch (Exception e) { return null; }
            });
            result.add(SnapshotResDto.fromEntity(s, productName, warehouseName));
        }
        return result;
    }

    // ============================================================
    // 3) 날짜별 재고 현황 조회
    // ============================================================

    /**
     * 날짜별 가용재고 현황을 계산한다.
     *
     * ──────────────────────────────────────────
     * 핵심: "현재 재고에서 거꾸로 되돌려서 과거 시점 재고를 구한다"
     *
     * 비유: 통장 잔고(현재 300만원)와 거래 내역(입출금 기록)이 있을 때
     *       "4월 1일에 얼마 있었지?" → 잔고 - (4/1 이후 입출금 합계) = 4/1 잔고
     *
     * 예시:
     *   현재(4/17) 가용재고 = 300개
     *   4/1~4/17 변동: +100, -30, -20, +50 → 합계 +100
     *   4/1 시점 재고 = 300 - 100 = 200개
     *   이후 날짜별 변동을 더해가면:
     *     4/1: 200 → 4/2: 300 → 4/5: 270 → 4/10: 250 → 4/15: 300
     * ──────────────────────────────────────────
     */
    @Transactional(readOnly = true)
    public List<DailyInventoryStatusDto> getDailyStatus(UUID clientId,
                                                         UUID warehouseId,
                                                         UUID productId,
                                                         LocalDate from,
                                                         LocalDate to) {
        // Step 1. 현재 가용재고 합계 조회 (DB 의 Inventory 테이블)
        int currentAvailable = getCurrentAvailableSum(clientId, warehouseId, productId);

        // Step 2. "조회 시작일 ~ 현재"까지의 재고 변동 합계
        //   fromStart = 조회 시작일의 자정 (예: 4/1 00:00:00)
        //   now = 지금 시각
        LocalDateTime fromStart = from.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<InventoryTransaction> txFromToNow = transactionRepository
                .findByDateRangeAndStatus(clientId, warehouseId, productId,
                        InventoryStatus.available, fromStart, now);

        // 이 기간의 모든 변동을 합산
        // 예: +100 + (-30) + (-20) + (+50) = +100
        int deltaFromToNow = 0;
        for (InventoryTransaction tx : txFromToNow) {
            deltaFromToNow += tx.getQty();
        }

        // Step 3. 조회 시작일 시점의 재고 역산
        // 예: 300(현재) - 100(변동합계) = 200 ← 4/1에는 200개였다
        int baseQty = currentAvailable - deltaFromToNow;

        // Step 4. 조회 범위의 트랜잭션을 날짜별로 입고/출고 분리 집계
        //   toEnd = 종료일 다음날 자정 (종료일 포함하기 위해)
        //   예: to=4/15 → toEnd=4/16 00:00:00 → "4/15 23:59:59" 까지 포함
        LocalDateTime toEnd = to.plusDays(1).atStartOfDay();

        List<InventoryTransaction> txInRange = transactionRepository
                .findByDateRangeAndStatus(clientId, warehouseId, productId,
                        InventoryStatus.available, fromStart, toEnd);

        // 날짜별 [입고합계, 출고합계] (LinkedHashMap → 날짜 순서 보장)
        Map<LocalDate, int[]> dailyMap = new LinkedHashMap<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            dailyMap.put(current, new int[]{0, 0}); // [0]=입고, [1]=출고
            current = current.plusDays(1);
        }

        for (InventoryTransaction tx : txInRange) {
            LocalDate txDate = tx.getCreatedAt().toLocalDate();
            if (!dailyMap.containsKey(txDate)) continue;

            int[] dayData = dailyMap.get(txDate);
            if (tx.getQty() > 0) {
                dayData[0] += tx.getQty();           // 입고 (양수)
            } else {
                dayData[1] += Math.abs(tx.getQty()); // 출고 (음수 → 절대값)
            }
        }

        // Step 5. 날짜별 누적 합산 → 일별 가용재고
        List<DailyInventoryStatusDto> result = new ArrayList<>();
        int runningQty = baseQty;

        for (Map.Entry<LocalDate, int[]> entry : dailyMap.entrySet()) {
            int inbound = entry.getValue()[0];
            int outbound = entry.getValue()[1];
            runningQty += (inbound - outbound);

            result.add(DailyInventoryStatusDto.builder()
                    .date(entry.getKey())
                    .availableQty(runningQty)
                    .inboundQty(inbound)
                    .outboundQty(outbound)
                    .build());
        }

        return result;
    }

    /**
     * 현재 Inventory 의 가용재고 합계 (회사 + 필터 조건)
     * null-location(staging 행)은 제외.
     */
    private int getCurrentAvailableSum(UUID clientId, UUID warehouseId, UUID productId) {
        List<Inventory> inventories;
        if (warehouseId != null) {
            inventories = inventoryRepository.findByWarehouseId(warehouseId);
        } else if (productId != null) {
            inventories = inventoryRepository.findByProductId(productId);
        } else {
            inventories = inventoryRepository.findAll();
        }

        int sum = 0;
        for (Inventory inv : inventories) {
            if (inv.getLocationId() == null) continue;
            if (!clientId.equals(inv.getClientId())) continue;
            if (productId != null && !productId.equals(inv.getProductId())) continue;
            if (warehouseId != null && !warehouseId.equals(inv.getWarehouseId())) continue;
            sum += inv.getAvailableQty();
        }
        return sum;
    }
}

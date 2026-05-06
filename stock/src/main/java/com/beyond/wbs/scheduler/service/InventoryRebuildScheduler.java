package com.beyond.wbs.scheduler.service;

import com.beyond.wbs.inventory.service.InventoryRebuildService;
import com.beyond.wbs.scheduler.domain.SchedulerHistory;
import com.beyond.wbs.scheduler.repository.SchedulerHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory Rebuild 자동 실행 스케줄러.
 *
 * 실행 시점: 매일 새벽 03:00 (운영 트래픽 적은 시간대)
 * 처리: inventory_transactions 합계 → inventory 테이블 재계산 (전체)
 * 목적: 코드 버그/비정상 종료로 inventory 가 트랜잭션 합계와 어긋난 경우 자동 보정
 *
 * 한계: inventory_transactions 자체가 누락된 케이스는 못 잡음.
 *       그런 경우는 도메인별 보상 액션 (예: 출고지시서 잔여 출고 처리) 으로 별도 처리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRebuildScheduler {

    private static final String JOB_NAME = "DAILY_INVENTORY_REBUILD";

    private final InventoryRebuildService inventoryRebuildService;
    private final SchedulerHistoryRepository schedulerHistoryRepository;

    /** cron 자동 실행 진입점 — 매일 03:00 */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        run(false);
    }

    /** 수동 트리거 진입점 (admin 버튼/테스트용) */
    public SchedulerHistory runManually() {
        return run(true);
    }

    @Transactional
    public SchedulerHistory run(boolean manual) {
        SchedulerHistory history = schedulerHistoryRepository.save(
                SchedulerHistory.start(JOB_NAME, manual));

        log.info("[InventoryRebuildScheduler] 시작 manual={}", manual);
        try {
            InventoryRebuildService.RebuildResult result = inventoryRebuildService.rebuild(null);
            log.info("[InventoryRebuildScheduler] 완료 totalChecked={} corrected={}",
                    result.totalChecked(), result.corrected());

            // targetCount = 검사한 inventory row 수, successCount = 보정된 row 수
            history.markSuccess(result.totalChecked(), result.corrected());
        } catch (Exception e) {
            log.error("[InventoryRebuildScheduler] 실패", e);
            history.markFailed(e.getMessage());
        }

        return schedulerHistoryRepository.save(history);
    }
}

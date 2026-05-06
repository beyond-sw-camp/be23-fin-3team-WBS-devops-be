package com.beyond.wbs.scheduler.domain;

/**
 * 스케줄러 실행 결과 상태.
 *
 * - RUNNING: 시작됐지만 아직 종료되지 않음 (비정상 종료 시 이 상태로 남음)
 * - SUCCESS: 모든 대상 성공 처리
 * - PARTIAL: 일부 실패 (예: 4/5 성공)
 * - FAILED : 전체 실패 또는 시작 단계에서 예외
 * - SKIPPED: 대상 없음 (정상)
 */
public enum SchedulerStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    SKIPPED
}

package com.beyond.wbs.scheduler.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스케줄러 실행 이력.
 *
 * Spring Batch 를 도입하는 대신 @Scheduled 를 그대로 쓰면서
 * 실행 이력·처리 건수·실패 사유를 추적하기 위한 경량 테이블.
 *
 * 향후 Spring Batch 로 이전 시 이 테이블은 제거하고 batch_job_execution 으로 대체 가능.
 */
@Entity
@Table(name = "scheduler_history", indexes = {
        @Index(name = "idx_scheduler_history_job_started", columnList = "job_name, started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerHistory {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    /** 잡 식별자 — 예: "DAILY_WAVE_GENERATION" */
    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SchedulerStatus status;

    /** 수동 실행 여부 (true = API 트리거, false = @Scheduled cron) */
    @Column(name = "triggered_manually", nullable = false)
    private boolean triggeredManually;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 처리 대상 건수 (예: 대상 출고지시서 수) */
    @Column(name = "target_count")
    private Integer targetCount;

    /** 성공 처리 건수 (예: 생성된 웨이브 수) */
    @Column(name = "success_count")
    private Integer successCount;

    /** 실패 건수 */
    @Column(name = "failed_count")
    private Integer failedCount;

    /** 실패 시 에러 요약 (최대 500자) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    public static SchedulerHistory start(String jobName, boolean manual) {
        SchedulerHistory h = new SchedulerHistory();
        h.jobName = jobName;
        h.status = SchedulerStatus.RUNNING;
        h.triggeredManually = manual;
        h.startedAt = LocalDateTime.now();
        return h;
    }

    public void markSuccess(int targetCount, int successCount) {
        this.status = SchedulerStatus.SUCCESS;
        this.targetCount = targetCount;    // 배치가 처리하려고 한 대상 수
        this.successCount = successCount;  // 성공적으로 만들어진 결과물 수
        this.failedCount = 0;
        this.finishedAt = LocalDateTime.now();
    }

    public void markPartial(int targetCount, int successCount, int failedCount, String errorSummary) {
        this.status = SchedulerStatus.PARTIAL;
        this.targetCount = targetCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.errorMessage = truncate(errorSummary);
        this.finishedAt = LocalDateTime.now();
    }

    public void markSkipped() {
        this.status = SchedulerStatus.SKIPPED;
        this.targetCount = 0;
        this.successCount = 0;
        this.failedCount = 0;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = SchedulerStatus.FAILED;
        this.errorMessage = truncate(error);
        this.finishedAt = LocalDateTime.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}

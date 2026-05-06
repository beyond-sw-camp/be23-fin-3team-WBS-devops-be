package com.beyond.wbs.scheduler.controller;

import com.beyond.wbs.scheduler.domain.SchedulerHistory;
import com.beyond.wbs.scheduler.domain.SchedulerHistoryDetail;
import com.beyond.wbs.scheduler.repository.SchedulerHistoryDetailRepository;
import com.beyond.wbs.scheduler.repository.SchedulerHistoryRepository;
import com.beyond.wbs.scheduler.service.WaveScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 스케줄러 수동 트리거 + 실행 이력 조회.
 *
 * - POST /scheduler/wave/run         : 웨이브 자동생성 배치 즉시 실행 (시연/긴급용)
 * - GET  /scheduler/history          : 전체 배치 실행 이력 (최신순 페이징)
 * - GET  /scheduler/history/{id}/detail : 한 실행의 처리 상세 (OB × SO)
 */
@RestController
@RequestMapping("/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final WaveScheduler waveScheduler;
    private final SchedulerHistoryRepository schedulerHistoryRepository;
    private final SchedulerHistoryDetailRepository schedulerHistoryDetailRepository;

    @PostMapping("/wave/run")
    public ResponseEntity<SchedulerHistory> runWaveNow() {
        return ResponseEntity.ok(waveScheduler.runManually());
    }

    @GetMapping("/history")
    public ResponseEntity<Page<SchedulerHistory>> getHistory(
            @RequestParam(required = false) String jobName,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SchedulerHistory> page = (jobName == null || jobName.isBlank())
                ? schedulerHistoryRepository.findAllByOrderByStartedAtDesc(pageable)
                : schedulerHistoryRepository.findByJobNameOrderByStartedAtDesc(jobName, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * 한 스케줄 실행의 처리 상세 — 행 펼치기 UI 용.
     * 한 OB 가 여러 SO 와 묶이면 SO 수만큼 행이 들어있음 (FE 가 OB 단위로 그룹핑).
     */
    @GetMapping("/history/{id}/detail")
    public ResponseEntity<List<SchedulerHistoryDetail>> getHistoryDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(schedulerHistoryDetailRepository.findByHistoryId(id));
    }
}

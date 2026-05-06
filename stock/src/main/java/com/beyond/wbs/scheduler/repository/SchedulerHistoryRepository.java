package com.beyond.wbs.scheduler.repository;

import com.beyond.wbs.scheduler.domain.SchedulerHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchedulerHistoryRepository extends JpaRepository<SchedulerHistory, UUID> {

    /** 잡별 이력 조회 (최신순) */
    Page<SchedulerHistory> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

    /** 전체 이력 (최신순) */
    Page<SchedulerHistory> findAllByOrderByStartedAtDesc(Pageable pageable);
}

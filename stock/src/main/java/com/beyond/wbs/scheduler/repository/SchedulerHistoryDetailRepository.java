package com.beyond.wbs.scheduler.repository;

import com.beyond.wbs.scheduler.domain.SchedulerHistoryDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SchedulerHistoryDetailRepository extends JpaRepository<SchedulerHistoryDetail, UUID> {

    /** 한 스케줄러 실행의 상세 목록 — FE 행 펼치기 용 */
    List<SchedulerHistoryDetail> findByHistoryId(UUID historyId);

    /** OB 가 어떤 스케줄러 실행에서 처리됐는지 추적 */
    List<SchedulerHistoryDetail> findByOutboundOrderId(UUID outboundOrderId);

    /** SO 가 어떤 스케줄러 실행에서 처리됐는지 추적 */
    List<SchedulerHistoryDetail> findBySalesOrderId(UUID salesOrderId);
}

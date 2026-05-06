package com.beyond.wbs.transfer.repository;

import com.beyond.wbs.transfer.domain.TransferExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransferExecutionRepository extends JpaRepository<TransferExecution, UUID> {

    // 감사·KPI용 필터 조회
    @Query("""
            SELECT e FROM TransferExecution e
            WHERE (:transferOrderId IS NULL OR e.transferOrderId = :transferOrderId)
              AND (:executedBy IS NULL OR e.executedBy = :executedBy)
              AND (:fromDate IS NULL OR e.executedAt >= :fromDate)
              AND (:toDate IS NULL OR e.executedAt <= :toDate)
            ORDER BY e.executedAt DESC
            """)
    Page<TransferExecution> searchExecutions(@Param("transferOrderId") UUID transferOrderId,
                                               @Param("executedBy") UUID executedBy,
                                               @Param("fromDate") LocalDateTime fromDate,
                                               @Param("toDate") LocalDateTime toDate,
                                               Pageable pageable);
}

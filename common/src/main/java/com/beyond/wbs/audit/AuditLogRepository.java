package com.beyond.wbs.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    Page<AuditLogEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.clientId = :clientId " +
            "AND (:action IS NULL OR a.action = :action) " +
            "AND (:from IS NULL OR a.createdAt >= :from) " +
            "AND (:to IS NULL OR a.createdAt < :to) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findByFilters(
            @Param("clientId") UUID clientId,
            @Param("action") String action,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}

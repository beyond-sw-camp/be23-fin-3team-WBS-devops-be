package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.ErpSalesOrderStatus;
import com.beyond.wbs.outbounds.domain.ErpSalesOrders;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ErpSalesOrderRepository extends JpaRepository<ErpSalesOrders, UUID> {

    // clientId 기준 수주서 조회
    // status가 null이면 상태 조건 무시하고 전체 조회
    @Query("SELECT e FROM ErpSalesOrders e " +
            "WHERE e.clientId = :clientId " +
            "AND (:status IS NULL OR e.status = :status)")
    List<ErpSalesOrders> findByConditions(
            @Param("clientId") UUID clientId,
            @Param("status") ErpSalesOrderStatus status);

    /**
     * FE 선택 화면용 — 다양한 필터 적용한 수주서 조회.
     * 모든 파라미터는 null 허용 (= 해당 조건 무시).
     *
     * 정렬: scheduledDate ASC (출고예정일 빠른 순) — FE 그룹핑 전제
     */
    @Query("SELECT e FROM ErpSalesOrders e " +
            "WHERE e.clientId = :clientId " +
            "AND (:status IS NULL OR e.status = :status) " +
            "AND (:storeId IS NULL OR e.storeId = :storeId) " +
            "AND (:dateFrom IS NULL OR e.scheduledDate >= :dateFrom) " +
            "AND (:dateTo IS NULL OR e.scheduledDate <= :dateTo) " +
            "AND (:soNoKeyword IS NULL OR LOWER(e.salesOrderNumber) LIKE LOWER(CONCAT('%', :soNoKeyword, '%'))) " +
            "ORDER BY e.scheduledDate ASC, e.orderDate ASC")
    List<ErpSalesOrders> findByFilters(
            @Param("clientId") UUID clientId,
            @Param("status") ErpSalesOrderStatus status,
            @Param("storeId") UUID storeId,
            @Param("dateFrom") java.time.LocalDate dateFrom,
            @Param("dateTo") java.time.LocalDate dateTo,
            @Param("soNoKeyword") String soNoKeyword);

    // 대시보드 "신규 주문" 카드용 — 출고지시서가 아직 만들어지지 않은 ERP 수주서 수.
    // SO ↔ OB 는 분할 출고로 1:N 매핑 (OutboundSalesOrderLinks).
    // 활성 링크 (cancelledAt IS NULL) 가 하나도 없는 SO = 미전환.
    @Query("SELECT COUNT(e) FROM ErpSalesOrders e " +
            "WHERE e.clientId = :clientId " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM OutboundSalesOrderLinks l " +
            "  WHERE l.salesOrderId = e.id AND l.cancelledAt IS NULL" +
            ")")
    long countNotConverted(@Param("clientId") UUID clientId);
}

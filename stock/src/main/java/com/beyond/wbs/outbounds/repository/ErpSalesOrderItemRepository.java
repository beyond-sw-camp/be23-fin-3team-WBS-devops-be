package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.ErpSalesOrderItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ErpSalesOrderItemRepository extends JpaRepository<ErpSalesOrderItems, UUID> {

    // 수주서 ID 기준 품목 목록 조회
    List<ErpSalesOrderItems> findBySalesOrderId(UUID salesOrderId);

    // 여러 수주서의 품목을 한번에 조회 — N+1 방지
    List<ErpSalesOrderItems> findBySalesOrderIdIn(List<UUID> salesOrderIds);

    /**
     * SO 부족 알림 계산용 — 미할당 잔량이 남은 라인 (allocatedQty < qty) 만, client 별로 조회.
     * 부모 SO 의 scheduledDate ASC 정렬 (출고예정 빠른 SO 부터 가용재고 우선 배분).
     * client 검증을 위해 ErpSalesOrders 와 join.
     */
    @Query("SELECT i FROM ErpSalesOrderItems i " +
            "JOIN ErpSalesOrders s ON s.id = i.salesOrderId " +
            "WHERE s.clientId = :clientId " +
            "AND i.allocatedQty < i.qty " +
            "ORDER BY s.scheduledDate ASC, s.orderDate ASC")
    List<ErpSalesOrderItems> findOpenByClientId(@Param("clientId") UUID clientId);
}

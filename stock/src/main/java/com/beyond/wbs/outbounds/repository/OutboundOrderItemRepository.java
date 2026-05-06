package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundOrderItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboundOrderItemRepository extends JpaRepository<OutboundOrderItems, UUID> {
    List<OutboundOrderItems> findByOutboundOrdersId(UUID outboundOrdersId);

    /**
     * 출고지시서 내 상품 라인 필터링 — 상품 멀티필터(/product/search-advanced) 결과로
     * 받은 productIds 와 매칭되는 라인만 반환.
     */
    List<OutboundOrderItems> findByOutboundOrdersIdAndProductIdIn(UUID outboundOrdersId,
                                                                  Collection<UUID> productIds);

    /**
     * 미예약 출고품목 조회:
     * 특정 상품에 대해 "승인됐지만 아직 전량 예약이 안 된" 품목들을 찾는다.
     * (reservedQty < orderedQty 인 품목)
     *
     * 적치 완료 시 available 이 올라가면 이 목록을 순회하며 자동 reserve 실행.
     * 출고지시서 상태가 approved 또는 in_progress 인 것만 대상.
     */
    @Query("SELECT oi FROM OutboundOrderItems oi " +
            "JOIN OutboundOrders o ON oi.outboundOrdersId = o.id " +
            "WHERE oi.productId = :productId " +
            "AND o.warehouseId = :warehouseId " +
            "AND o.status IN ('approved', 'in_progress') " +
            "AND oi.reservedQty < oi.orderedQty " +
            "ORDER BY o.scheduledDate ASC")
    List<OutboundOrderItems> findUnreservedByProductAndWarehouse(
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId);

    /**
     * 주문별 품목 수 + 총 수량 — 대시보드 통합 응답에서 N+1 방지용.
     * 반환: [orderId(UUID), itemCount(Long), totalQty(Long)]
     */
    @Query("SELECT oi.outboundOrdersId, COUNT(oi), COALESCE(SUM(oi.orderedQty), 0) " +
            "FROM OutboundOrderItems oi " +
            "WHERE oi.outboundOrdersId IN :orderIds " +
            "GROUP BY oi.outboundOrdersId")
    List<Object[]> findOrderSummaries(@Param("orderIds") List<UUID> orderIds);
}

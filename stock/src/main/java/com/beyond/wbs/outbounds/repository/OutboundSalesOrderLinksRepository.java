package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundSalesOrderLinks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboundSalesOrderLinksRepository extends JpaRepository<OutboundSalesOrderLinks, UUID> {

    /** 출고지시서에 연결된 활성 링크 모두 */
    List<OutboundSalesOrderLinks> findByOutboundOrderIdAndCancelledAtIsNull(UUID outboundOrderId);

    /** 출고지시서 N개의 활성 링크 일괄 — 전표 목록처럼 batch 조회 시 사용 */
    List<OutboundSalesOrderLinks> findByOutboundOrderIdInAndCancelledAtIsNull(Collection<UUID> outboundOrderIds);

    /** 출고지시서의 모든 링크 (취소 포함) */
    List<OutboundSalesOrderLinks> findByOutboundOrderId(UUID outboundOrderId);

    /** 수주서에 연결된 출고지시서 링크 (활성만) — 진행률 페이지 "연결된 출고지시서 목록" */
    List<OutboundSalesOrderLinks> findBySalesOrderIdAndCancelledAtIsNull(UUID salesOrderId);

    /** 수주서 N개의 활성 링크 일괄 — SO 목록 화면 가중 진행률 계산용 (N+1 방지) */
    List<OutboundSalesOrderLinks> findBySalesOrderIdInAndCancelledAtIsNull(Collection<UUID> salesOrderIds);

    /** 수주서의 모든 링크 (취소 포함) — 진행률 페이지 이력 */
    List<OutboundSalesOrderLinks> findBySalesOrderId(UUID salesOrderId);

    /** 수주서 라인의 활성 할당 합계 — allocatedQty 재계산용 */
    @Query("SELECT COALESCE(SUM(l.qty), 0) FROM OutboundSalesOrderLinks l " +
            "WHERE l.salesOrderItemId = :salesOrderItemId AND l.cancelledAt IS NULL")
    int sumActiveQtyBySalesOrderItem(@Param("salesOrderItemId") UUID salesOrderItemId);

    /** 출고지시서 취소 시 — 모든 링크 일괄 soft delete */
    @Modifying
    @Query("UPDATE OutboundSalesOrderLinks l SET l.cancelledAt = :cancelledAt " +
            "WHERE l.outboundOrderId = :outboundOrderId AND l.cancelledAt IS NULL")
    int softDeleteByOutboundOrderId(@Param("outboundOrderId") UUID outboundOrderId,
                                     @Param("cancelledAt") LocalDateTime cancelledAt);
}

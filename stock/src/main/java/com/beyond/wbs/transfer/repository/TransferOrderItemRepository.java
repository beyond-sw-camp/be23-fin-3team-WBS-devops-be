package com.beyond.wbs.transfer.repository;

import com.beyond.wbs.transfer.domain.TransferOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransferOrderItemRepository extends JpaRepository<TransferOrderItem, UUID> {
    List<TransferOrderItem> findByTransferOrderId(UUID transferOrderId);

    /**
     * 이동지시서 내 상품 라인 필터링 — 상품 멀티필터 결과 productIds 와 매칭되는 라인만 반환.
     */
    List<TransferOrderItem> findByTransferOrderIdAndProductIdIn(UUID transferOrderId,
                                                                 Collection<UUID> productIds);

    /**
     * 주문별 품목 수 + 총 수량 — 대시보드 통합 응답에서 N+1 방지용.
     * 반환: [transferOrderId(UUID), itemCount(Long), totalQty(Long)]
     */
    @Query("SELECT t.transferOrderId, COUNT(t), COALESCE(SUM(t.orderedQty), 0) " +
            "FROM TransferOrderItem t " +
            "WHERE t.transferOrderId IN :orderIds " +
            "GROUP BY t.transferOrderId")
    List<Object[]> findOrderSummaries(@Param("orderIds") List<UUID> orderIds);
}

package com.beyond.wbs.inventory.repository;

import com.beyond.wbs.inventory.domain.StockCountOrder;
import com.beyond.wbs.inventory.domain.StockCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockCountOrderRepository extends JpaRepository<StockCountOrder, UUID> {
    Page<StockCountOrder> findByClientId(UUID clientId, Pageable pageable);
    Page<StockCountOrder> findByClientIdAndStatus(
            UUID clientId, StockCountStatus status, Pageable pageable);

    /**
     * productIds EXISTS — 그 상품 라인이 1개 이상 포함된 실사지시서만.
     * status 옵셔널.
     */
    @Query("SELECT DISTINCT o FROM StockCountOrder o " +
            "WHERE o.clientId = :clientId " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND EXISTS (SELECT 1 FROM StockCountItem i " +
            "    WHERE i.countOrderId = o.id AND i.productId IN :productIds)")
    Page<StockCountOrder> findByClientIdAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("status") StockCountStatus status,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);

    /**
     * 모바일 작업자용 — 본인이 카운트한 이력이 있는 실사지시서 모두 (status 무관) +
     * 진행 중인 실사 (누구나 진입 가능). warehouseId 옵셔널 필터.
     *
     * <p>본인 작업 이력 (completed/cancelled 포함) + 새 진행 중 실사 모두 노출.
     * 작업자가 과거 작업 다시 보거나 새 실사 진입 둘 다 가능.
     */
    @Query("SELECT DISTINCT o FROM StockCountOrder o " +
            "WHERE o.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId) " +
            "AND ( o.status = com.beyond.wbs.inventory.domain.StockCountStatus.in_progress " +
            "      OR EXISTS (SELECT 1 FROM StockCountItem i " +
            "                 WHERE i.countOrderId = o.id AND i.countedBy = :userId) ) " +
            "ORDER BY o.createdAt DESC")
    List<StockCountOrder> findMyListForMobile(@Param("clientId") UUID clientId,
                                               @Param("userId") UUID userId,
                                               @Param("warehouseId") UUID warehouseId);
}

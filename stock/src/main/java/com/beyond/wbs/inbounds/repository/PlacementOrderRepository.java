package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.PlacementOrderStatus;
import com.beyond.wbs.inbounds.domain.PlacementOrders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PlacementOrderRepository extends JpaRepository<PlacementOrders, UUID> {
    List<PlacementOrders> findByInboundOrderId(UUID inboundOrderId);
    List<PlacementOrders> findByClientId(UUID clientId);
    long countByClientId(UUID clientId);

    // 모바일 작업자 목록 조회용 동적 쿼리
    @Query("SELECT p FROM PlacementOrders p " +
            "WHERE p.clientId = :clientId " +
            "AND (:assignedTo IS NULL OR p.assignedTo = :assignedTo) " +
            "AND (:warehouseId IS NULL OR p.warehouseId = :warehouseId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<PlacementOrders> findByConditions(UUID clientId,
                                           UUID assignedTo,
                                           UUID warehouseId,
                                           PlacementOrderStatus status,
                                           Pageable pageable);

    /**
     * 멀티필터 + productIds EXISTS — 그 상품 라인이 1개 이상 포함된 적치지시서만.
     */
    @Query("SELECT DISTINCT p FROM PlacementOrders p " +
            "WHERE p.clientId = :clientId " +
            "AND (:assignedTo IS NULL OR p.assignedTo = :assignedTo) " +
            "AND (:warehouseId IS NULL OR p.warehouseId = :warehouseId) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND EXISTS (SELECT 1 FROM PlacementItems i " +
            "    WHERE i.placementOrderId = p.id AND i.productId IN :productIds)")
    Page<PlacementOrders> findByConditionsAndProductIds(UUID clientId,
                                                        UUID assignedTo,
                                                        UUID warehouseId,
                                                        PlacementOrderStatus status,
                                                        List<UUID> productIds,
                                                        Pageable pageable);

    // 대시보드 — 오늘 적치 완료된 건수 (실제 입고 완료 = 적치까지 끝난 시점)
    long countByClientIdAndCompletedAtBetween(UUID clientId, LocalDateTime start, LocalDateTime end);

    // 대시보드 — 오늘 적치 완료된 건수 (originType 기준 분리)
    //   originType = "return" 이면 반품 완료, 그 외(purchase_order/manual 등)는 일반 입고 완료
    @Query("SELECT COUNT(p) FROM PlacementOrders p " +
            "WHERE p.clientId = :clientId " +
            "AND p.completedAt BETWEEN :start AND :end " +
            "AND EXISTS (SELECT 1 FROM InboundOrders i WHERE i.id = p.inboundOrderId AND i.originType = :originType)")
    long countCompletedByOriginType(UUID clientId, String originType, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(p) FROM PlacementOrders p " +
            "WHERE p.clientId = :clientId " +
            "AND p.completedAt BETWEEN :start AND :end " +
            "AND EXISTS (SELECT 1 FROM InboundOrders i WHERE i.id = p.inboundOrderId AND i.originType <> :originType)")
    long countCompletedByOriginTypeNot(UUID clientId, String originType, LocalDateTime start, LocalDateTime end);
}

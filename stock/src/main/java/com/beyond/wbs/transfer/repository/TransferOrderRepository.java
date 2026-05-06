package com.beyond.wbs.transfer.repository;

import com.beyond.wbs.transfer.domain.TransferOrder;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, UUID> {

    @Query("""
            SELECT t FROM TransferOrder t
            WHERE t.clientId = :clientId
              AND (:status IS NULL OR t.status = :status)
              AND (:fromWarehouseId IS NULL OR t.fromWarehouseId = :fromWarehouseId)
              AND (:toWarehouseId IS NULL OR t.toWarehouseId = :toWarehouseId)
            """)
    Page<TransferOrder> searchTransferOrders(@Param("clientId") UUID clientId,
                                              @Param("status") TransferOrderStatus status,
                                              @Param("fromWarehouseId") UUID fromWarehouseId,
                                              @Param("toWarehouseId") UUID toWarehouseId,
                                              Pageable pageable);

    /**
     * productIds EXISTS — 그 상품 라인이 1개 이상 포함된 이동지시서만.
     */
    @Query("""
            SELECT DISTINCT t FROM TransferOrder t
            WHERE t.clientId = :clientId
              AND (:status IS NULL OR t.status = :status)
              AND (:fromWarehouseId IS NULL OR t.fromWarehouseId = :fromWarehouseId)
              AND (:toWarehouseId IS NULL OR t.toWarehouseId = :toWarehouseId)
              AND EXISTS (SELECT 1 FROM TransferOrderItem i
                  WHERE i.transferOrderId = t.id AND i.productId IN :productIds)
            """)
    Page<TransferOrder> searchTransferOrdersWithProducts(@Param("clientId") UUID clientId,
                                                         @Param("status") TransferOrderStatus status,
                                                         @Param("fromWarehouseId") UUID fromWarehouseId,
                                                         @Param("toWarehouseId") UUID toWarehouseId,
                                                         @Param("productIds") List<UUID> productIds,
                                                         Pageable pageable);

    long countByClientIdAndStatus(UUID clientId, TransferOrderStatus status);

    long countByClientIdAndExpectedDateBeforeAndStatusIn(UUID clientId, LocalDate date, List<TransferOrderStatus> statuses);

    // 오늘마감+지연 미완료 이동 — "오늘 처리 현황" 패널 대기 카운트용
    long countByClientIdAndExpectedDateLessThanEqualAndStatusIn(UUID clientId, LocalDate date, List<TransferOrderStatus> statuses);

    // 모든 미완료 이동 (날짜 무관) — "지시서 통합" 카드용
    long countByClientIdAndStatusIn(UUID clientId, List<TransferOrderStatus> statuses);

    // 대시보드 — 오늘 완료된(updated_at) 이동 건수 (실제 이동 완료 시점)
    long countByClientIdAndStatusInAndUpdatedAtBetween(UUID clientId,
                                                       List<TransferOrderStatus> statuses,
                                                       LocalDateTime start,
                                                       LocalDateTime end);

    // ── 통합 페이지 / 처리 필요 패널용 ──
    List<TransferOrder> findByClientIdAndStatusInOrderByExpectedDateAsc(UUID clientId, List<TransferOrderStatus> statuses);
    Page<TransferOrder> findByClientIdAndStatusInOrderByExpectedDateAsc(UUID clientId, List<TransferOrderStatus> statuses, Pageable pageable);
}

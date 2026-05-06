package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InboundOrderRepository extends JpaRepository<InboundOrders, UUID> {
    long countByClientId(UUID clientId);
    List<InboundOrders> findByClientId(UUID clientId);
    List<InboundOrders> findByClientIdAndStatusIn(UUID clientId, List<InboundOrderStatus> statuses);

    // Pageable 지원
    Page<InboundOrders> findByClientId(UUID clientId, Pageable pageable);
    Page<InboundOrders> findByClientIdAndStatusIn(UUID clientId, List<InboundOrderStatus> statuses, Pageable pageable);

    // 특정 출고지시서에 대한 반품 입고지시서 조회 (중복 반품 방지용)
    List<InboundOrders> findByOriginIdAndOriginType(UUID originId, String originType);

    /**
     * 주문별 품목 수 + 총 수량 집계 — N+1 제거용.
     * 반환: [orderId(UUID), itemCount(Long), totalQty(Long)]
     */
    @Query("SELECT i.inboundOrderId, COUNT(i), COALESCE(SUM(i.orderedQty), 0) " +
            "FROM InboundOrderItems i " +
            "WHERE i.inboundOrderId IN :orderIds " +
            "GROUP BY i.inboundOrderId")
    List<Object[]> findOrderSummaries(@Param("orderIds") List<UUID> orderIds);

    // ── 대시보드 KPI 집계용 ──
    long countByClientIdAndExpectedDate(UUID clientId, LocalDate expectedDate);
    long countByClientIdAndStatus(UUID clientId, InboundOrderStatus status);
    long countByClientIdAndExpectedDateBeforeAndStatusIn(UUID clientId, LocalDate date, List<InboundOrderStatus> statuses);

    // 오늘까지 마감(expected_date <= today) 인데 아직 미완료인 입고
    long countByClientIdAndExpectedDateLessThanEqualAndStatusIn(UUID clientId, LocalDate date, List<InboundOrderStatus> statuses);

    // 협력사가 오늘(expected_date = today) 입고 약속한 건수 (아직 처리 안 끝난 것만) — "오늘 입고" 카드용
    long countByClientIdAndExpectedDateAndStatusIn(UUID clientId, LocalDate date, List<InboundOrderStatus> statuses);

    // 모든 미완료 입고 (날짜 무관) — "지시서 통합" 카드용
    long countByClientIdAndStatusIn(UUID clientId, List<InboundOrderStatus> statuses);

    // ── 오늘 처리 현황 패널 — 반품(originType="return") 분리용 ──
    long countByClientIdAndOriginTypeAndStatusIn(UUID clientId, String originType, List<InboundOrderStatus> statuses);
    long countByClientIdAndOriginTypeNotAndStatusIn(UUID clientId, String originType, List<InboundOrderStatus> statuses);
    long countByClientIdAndOriginTypeAndExpectedDateLessThanEqualAndStatusIn(
            UUID clientId, String originType, LocalDate date, List<InboundOrderStatus> statuses);
    long countByClientIdAndOriginTypeNotAndExpectedDateLessThanEqualAndStatusIn(
            UUID clientId, String originType, LocalDate date, List<InboundOrderStatus> statuses);

    /**
     * 출고 미리보기용 — 입고예정 합산.
     * (warehouse × product) 단위로 shipDate 까지 도착 예정인 수량.
     * 결과 row: [warehouseId, productId, qtySum]
     *
     * 포함 상태: approved / received / placing
     *   - draft 는 미승인 제안 단계라 입고 확정 아님 (제외)
     *   - completed 는 이미 availableQty 에 반영되어 중복 방지 (제외)
     *   - cancelled 는 당연히 제외
     */
    @Query("SELECT o.warehouseId, it.productId, COALESCE(SUM(it.orderedQty), 0) " +
            "FROM InboundOrders o " +
            "JOIN InboundOrderItems it ON it.inboundOrderId = o.id " +
            "WHERE o.clientId = :clientId " +
            "AND o.warehouseId IN :warehouseIds " +
            "AND it.productId IN :productIds " +
            "AND o.expectedDate <= :shipDate " +
            "AND o.status IN :openStatuses " +
            "GROUP BY o.warehouseId, it.productId")
    List<Object[]> sumIncomingByWarehouseAndProduct(@Param("clientId") UUID clientId,
                                                     @Param("warehouseIds") List<UUID> warehouseIds,
                                                     @Param("productIds") List<UUID> productIds,
                                                     @Param("shipDate") LocalDate shipDate,
                                                     @Param("openStatuses") List<InboundOrderStatus> openStatuses);

    // ── 통합 페이지 / 처리 필요 패널용 ──
    List<InboundOrders> findByClientIdAndStatusInOrderByExpectedDateAsc(UUID clientId, List<InboundOrderStatus> statuses);
    Page<InboundOrders> findByClientIdAndStatusInOrderByExpectedDateAsc(UUID clientId, List<InboundOrderStatus> statuses, Pageable pageable);

    /**
     * 상품 멀티필터 검색 — productIds 가 1개 이상 매칭되는 라인이 있는 입고지시서만.
     * 상태 필터는 옵셔널 (null/empty 면 미적용).
     */
    @Query("SELECT DISTINCT o FROM InboundOrders o " +
           "WHERE o.clientId = :clientId " +
           "AND EXISTS (SELECT 1 FROM InboundOrderItems i " +
           "    WHERE i.inboundOrderId = o.id AND i.productId IN :productIds)")
    Page<InboundOrders> findByClientIdAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);

    @Query("SELECT DISTINCT o FROM InboundOrders o " +
           "WHERE o.clientId = :clientId " +
           "AND o.status IN :statuses " +
           "AND EXISTS (SELECT 1 FROM InboundOrderItems i " +
           "    WHERE i.inboundOrderId = o.id AND i.productId IN :productIds)")
    Page<InboundOrders> findByClientIdAndStatusInAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("statuses") List<InboundOrderStatus> statuses,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);

    /**
     * 통합 동적 검색 — originType / excludeOriginType 도 옵셔널 필터로 합친 쿼리.
     *
     * 모든 List 는 비어있을 때 null 로 전달 (Service 책임).
     * excludeOriginType: 그 originType 이 아닌 row 만 매칭. originType 이 null 인 row 도 통과.
     */
    @Query("SELECT DISTINCT o FROM InboundOrders o " +
           "WHERE o.clientId = :clientId " +
           "AND (:statuses IS NULL OR o.status IN :statuses) " +
           "AND (:originType IS NULL OR o.originType = :originType) " +
           "AND (:excludeOriginType IS NULL OR o.originType IS NULL OR o.originType <> :excludeOriginType) " +
           "AND (:productIds IS NULL OR EXISTS (SELECT 1 FROM InboundOrderItems i " +
           "    WHERE i.inboundOrderId = o.id AND i.productId IN :productIds))")
    Page<InboundOrders> searchByConditions(
            @Param("clientId") UUID clientId,
            @Param("statuses") List<InboundOrderStatus> statuses,
            @Param("originType") String originType,
            @Param("excludeOriginType") String excludeOriginType,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);
}

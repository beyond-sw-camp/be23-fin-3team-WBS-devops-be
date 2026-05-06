package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrders, UUID> {

    // clientId 필수, warehouseId/storeId/status/assignedTo 는 null 이면 조건 무시
    @Query("SELECT o FROM OutboundOrders o " +
            "WHERE o.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId) " +
            "AND (:storeId IS NULL OR o.storeId = :storeId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:assignedTo IS NULL OR o.assignedTo = :assignedTo)")
    Page<OutboundOrders> findByConditions(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("storeId") UUID storeId,
            @Param("status") OutboundOrderStatus status,
            @Param("assignedTo") UUID assignedTo,
            Pageable pageable);
    // id + clientId 기준 조회 (권한 검증용)
    Optional<OutboundOrders> findByIdAndClientId(UUID id, UUID clientId);

    // ERP 수주서 기반 중복 생성 방지
    boolean existsByOriginId(UUID originId);

    // 이미 출고지시서로 전환된 수주서 ID 목록 조회 (alreadyConverted 판정용)
    @Query("SELECT o.originId FROM OutboundOrders o WHERE o.originType = 'sales_order' AND o.originId IN :originIds")
    List<UUID> findConvertedOriginIds(@Param("originIds") java.util.List<UUID> originIds);

    // ── 대시보드 KPI 집계용 ──
    long countByClientIdAndScheduledDate(UUID clientId, LocalDate scheduledDate);
    long countByClientIdAndStatus(UUID clientId, OutboundOrderStatus status);
    long countByClientIdAndScheduledDateBeforeAndStatusIn(UUID clientId, LocalDate date, List<OutboundOrderStatus> statuses);

    // 오늘까지 마감(scheduled_date <= today)인데 아직 미완료인 출고 — 대시보드 "잔여" 카드용
    long countByClientIdAndScheduledDateLessThanEqualAndStatusIn(UUID clientId, LocalDate date, List<OutboundOrderStatus> statuses);

    // 오늘(scheduled_date = today) 출고처로 출고 예정인 건수 (아직 처리 안 끝난 것만) — "오늘 출고" 카드용
    long countByClientIdAndScheduledDateAndStatusIn(UUID clientId, LocalDate date, List<OutboundOrderStatus> statuses);

    // 모든 미완료 출고 (날짜 무관) — "지시서 통합" 카드용
    long countByClientIdAndStatusIn(UUID clientId, List<OutboundOrderStatus> statuses);

    /**
     * 출고 미리보기용 — 다른 draft 출고가 차지할 수량 합산.
     * (warehouse × product) 단위로 shipDate 까지 출고 예정인 draft 수량.
     * 결과 row: [warehouseId, productId, qtySum]
     *
     * draft 만 카운트:
     *   - approved/in_progress 는 이미 inventory.reservedQty 로 availableQty 에서 차감되어 중복 방지
     *   - completed/cancelled 는 당연히 제외
     *
     * excludeOutboundOrderId: 자기 자신(현재 편집/생성 중인 OB)을 미리보기에서 제외할 때 사용. null 이면 전체 포함.
     */
    @Query("SELECT o.warehouseId, it.productId, COALESCE(SUM(it.orderedQty), 0) " +
            "FROM OutboundOrders o " +
            "JOIN OutboundOrderItems it ON it.outboundOrdersId = o.id " +
            "WHERE o.clientId = :clientId " +
            "AND o.warehouseId IN :warehouseIds " +
            "AND it.productId IN :productIds " +
            "AND o.scheduledDate <= :shipDate " +
            "AND o.status = com.beyond.wbs.outbounds.domain.OutboundOrderStatus.draft " +
            "AND (:excludeOutboundOrderId IS NULL OR o.id <> :excludeOutboundOrderId) " +
            "GROUP BY o.warehouseId, it.productId")
    List<Object[]> sumDraftReservedByWarehouseAndProduct(@Param("clientId") UUID clientId,
                                                          @Param("warehouseIds") List<UUID> warehouseIds,
                                                          @Param("productIds") List<UUID> productIds,
                                                          @Param("shipDate") java.time.LocalDate shipDate,
                                                          @Param("excludeOutboundOrderId") UUID excludeOutboundOrderId);

    // ── 통합 페이지 / 처리 필요 패널용 ──
    List<OutboundOrders> findByClientIdAndStatusInOrderByScheduledDateAsc(UUID clientId, List<OutboundOrderStatus> statuses);
    Page<OutboundOrders> findByClientIdAndStatusInOrderByScheduledDateAsc(UUID clientId, List<OutboundOrderStatus> statuses, Pageable pageable);

    // ── 배치(웨이브 자동생성) 대상 조회 ──
    List<OutboundOrders> findByStatusAndScheduledDate(OutboundOrderStatus status, LocalDate scheduledDate);

    /**
     * 기타출고 누적 검증용 — 단일 (productId + warehouseId) 의 draft 출고지시서 수량 합계.
     * approved 이후는 inventory.reservedQty 로 이미 가용에서 빠졌으므로 별도 계산 불필요.
     * cancelled / completed 는 자연 제외.
     */
    @Query("SELECT COALESCE(SUM(it.orderedQty), 0) " +
            "FROM OutboundOrders o " +
            "JOIN OutboundOrderItems it ON it.outboundOrdersId = o.id " +
            "WHERE o.clientId = :clientId " +
            "AND o.warehouseId = :warehouseId " +
            "AND it.productId = :productId " +
            "AND o.status = com.beyond.wbs.outbounds.domain.OutboundOrderStatus.draft")
    int sumDraftOrderedByProductAndWarehouse(@Param("clientId") UUID clientId,
                                              @Param("productId") UUID productId,
                                              @Param("warehouseId") UUID warehouseId);

    /**
     * 기타출고용 — 가용재고 부족 시 취소 후보가 될 수 있는 정식 출고지시서 목록.
     *
     * 조건:
     *  - clientId 일치
     *  - 해당 productId/warehouseId/locationId 에 reserve 트랜잭션 존재
     *  - 상태 = approved (피킹 시작 전)
     *  - OutboundPickinglist 매핑이 없음 (이중 안전장치 — 피킹 단 한 줄도 만들어지지 않은 것)
     *
     * 정렬: scheduledDate DESC (출고일 먼 순 — 입고로 채울 시간 있는 것부터)
     *
     * 결과 row: [outboundOrderId, orderNo, scheduledDate, storeId, reservedQty]
     *  reservedQty 는 해당 (product+warehouse+location) 에 이 출고지시서가 reserve 한 합계
     */
    @Query("""
            SELECT o.id, o.orderNo, o.scheduledDate, o.storeId,
                   COALESCE(SUM(t.qty), 0)
              FROM OutboundOrders o
              JOIN InventoryTransaction t
                ON t.refId = o.id
               AND t.refType = com.beyond.wbs.inventory.domain.RefType.outbound_order
               AND t.txType = com.beyond.wbs.inventory.domain.TxType.reserve
               AND t.statusTo = com.beyond.wbs.inventory.domain.InventoryStatus.reserved
             WHERE o.clientId = :clientId
               AND o.status = com.beyond.wbs.outbounds.domain.OutboundOrderStatus.approved
               AND t.productId = :productId
               AND t.warehouseId = :warehouseId
               AND (:locationId IS NULL OR t.locationId = :locationId)
               AND NOT EXISTS (
                    SELECT 1 FROM OutboundPickinglist op
                     WHERE op.outboundOrderId = o.id
               )
             GROUP BY o.id, o.orderNo, o.scheduledDate, o.storeId
             ORDER BY o.scheduledDate DESC
            """)
    List<Object[]> findCancellationCandidates(
            @Param("clientId") UUID clientId,
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId,
            @Param("locationId") UUID locationId);

    /**
     * draft 출고지시서 후보 — reserve 트랜잭션이 없으므로 OutboundOrderItems 직접 조인.
     * draft 는 위치를 미지정이라 locationId 필터는 무시 (창고 단위로 검색).
     *
     * 결과 row: [outboundOrderId, orderNo, scheduledDate, storeId, orderedQty]
     */
    @Query("""
            SELECT o.id, o.orderNo, o.scheduledDate, o.storeId,
                   COALESCE(SUM(it.orderedQty), 0)
              FROM OutboundOrders o
              JOIN OutboundOrderItems it ON it.outboundOrdersId = o.id
             WHERE o.clientId = :clientId
               AND o.status = com.beyond.wbs.outbounds.domain.OutboundOrderStatus.draft
               AND o.warehouseId = :warehouseId
               AND it.productId = :productId
             GROUP BY o.id, o.orderNo, o.scheduledDate, o.storeId
             ORDER BY o.scheduledDate DESC
            """)
    List<Object[]> findDraftCancellationCandidates(
            @Param("clientId") UUID clientId,
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId);

    /**
     * 반품 출고 누적 합산 — 같은 입고지시서(inboundOrderId)를 origin 으로 하는
     * 반품 출고들의 productId 별 orderedQty 합. 취소된 건은 제외.
     *
     * 결과 row: [productId(UUID), summedQty(Number)]
     * 호출처: 반품 출고 생성 시 누적 검증용.
     */
    @Query("SELECT it.productId, COALESCE(SUM(it.orderedQty), 0) " +
            "FROM OutboundOrders o " +
            "JOIN OutboundOrderItems it ON it.outboundOrdersId = o.id " +
            "WHERE o.originId = :inboundOrderId " +
            "AND o.originType = 'return' " +
            "AND o.status <> com.beyond.wbs.outbounds.domain.OutboundOrderStatus.cancelled " +
            "GROUP BY it.productId")
    List<Object[]> sumReturnedByProductForInbound(@Param("inboundOrderId") UUID inboundOrderId);

    /**
     * 통합 동적 검색 — originType / excludeOriginType 도 옵셔널 필터로 합친 쿼리.
     *
     * 모든 옵셔널 — null/빈 리스트면 무시.
     * excludeOriginType: 그 originType 이 아닌 row 만 매칭. originType 이 null 인 row 도 통과.
     */
    @Query("SELECT DISTINCT o FROM OutboundOrders o " +
            "WHERE o.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId) " +
            "AND (:storeId IS NULL OR o.storeId = :storeId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:assignedTo IS NULL OR o.assignedTo = :assignedTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:excludeOriginType IS NULL OR o.originType IS NULL OR o.originType <> :excludeOriginType) " +
            "AND (:productIds IS NULL OR EXISTS (SELECT 1 FROM OutboundOrderItems i " +
            "    WHERE i.outboundOrdersId = o.id AND i.productId IN :productIds))")
    Page<OutboundOrders> searchByConditions(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("storeId") UUID storeId,
            @Param("status") OutboundOrderStatus status,
            @Param("assignedTo") UUID assignedTo,
            @Param("originType") String originType,
            @Param("excludeOriginType") String excludeOriginType,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);

    /**
     * 멀티필터 검색 + productIds EXISTS — 그 상품 라인이 1개 이상 포함된 출고지시서만.
     * 기타 조건(warehouse/store/status/assignedTo)은 옵셔널, null 이면 무시.
     */
    @Query("SELECT DISTINCT o FROM OutboundOrders o " +
            "WHERE o.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId) " +
            "AND (:storeId IS NULL OR o.storeId = :storeId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:assignedTo IS NULL OR o.assignedTo = :assignedTo) " +
            "AND EXISTS (SELECT 1 FROM OutboundOrderItems i " +
            "    WHERE i.outboundOrdersId = o.id AND i.productId IN :productIds)")
    Page<OutboundOrders> findByConditionsAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("storeId") UUID storeId,
            @Param("status") OutboundOrderStatus status,
            @Param("assignedTo") UUID assignedTo,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);
}

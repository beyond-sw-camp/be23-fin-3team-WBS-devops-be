package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundDispatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundDispatchRepository extends JpaRepository<OutboundDispatch, UUID> {

    // id + clientId 조회 (권한 검증용)
    Optional<OutboundDispatch> findByIdAndClientId(UUID id, UUID clientId);

    // outbound_order_id + clientId 조회 — FE 출고 전표 화면이 order id로 진입
    Optional<OutboundDispatch> findByOutboundOrdersIdAndClientId(UUID outboundOrdersId, UUID clientId);

    // 일자 범위 내 출고확정(dispatch) 건수 — 대시보드 "오늘 출고 완료" 카드용
    long countByClientIdAndDispatchedAtBetween(UUID clientId, LocalDateTime from, LocalDateTime to);

    /**
     * 출고전표 목록 — 모든 필터 null 허용.
     *
     * originType / orderNoKeyword 는 OutboundOrders 와 join 해서 적용.
     * 그 외 (warehouse / 날짜 / 전표번호) 는 OutboundDispatch 자체 컬럼.
     */
    @Query(value =
            "SELECT d FROM OutboundDispatch d " +
            "JOIN OutboundOrders o ON o.id = d.outboundOrdersId " +
            "WHERE d.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR d.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR d.dispatchedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR d.dispatchedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:dispatchNoKeyword IS NULL OR LOWER(d.dispatchNo) LIKE LOWER(CONCAT('%', :dispatchNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "ORDER BY d.dispatchedAt DESC",
        countQuery =
            "SELECT COUNT(d) FROM OutboundDispatch d " +
            "JOIN OutboundOrders o ON o.id = d.outboundOrdersId " +
            "WHERE d.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR d.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR d.dispatchedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR d.dispatchedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:dispatchNoKeyword IS NULL OR LOWER(d.dispatchNo) LIKE LOWER(CONCAT('%', :dispatchNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%')))")
    Page<OutboundDispatch> findByFilters(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("originType") String originType,
            @Param("dispatchNoKeyword") String dispatchNoKeyword,
            @Param("orderNoKeyword") String orderNoKeyword,
            Pageable pageable);

    /**
     * 멀티필터 + productIds EXISTS — 그 상품 라인이 1개 이상 포함된 출고전표만.
     */
    @Query(value =
            "SELECT DISTINCT d FROM OutboundDispatch d " +
            "JOIN OutboundOrders o ON o.id = d.outboundOrdersId " +
            "WHERE d.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR d.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR d.dispatchedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR d.dispatchedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:dispatchNoKeyword IS NULL OR LOWER(d.dispatchNo) LIKE LOWER(CONCAT('%', :dispatchNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "AND EXISTS (SELECT 1 FROM OutboundDispatchItems i " +
            "    WHERE i.dispatchId = d.id AND i.productId IN :productIds)",
        countQuery =
            "SELECT COUNT(DISTINCT d) FROM OutboundDispatch d " +
            "JOIN OutboundOrders o ON o.id = d.outboundOrdersId " +
            "WHERE d.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR d.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR d.dispatchedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR d.dispatchedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:dispatchNoKeyword IS NULL OR LOWER(d.dispatchNo) LIKE LOWER(CONCAT('%', :dispatchNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "AND EXISTS (SELECT 1 FROM OutboundDispatchItems i " +
            "    WHERE i.dispatchId = d.id AND i.productId IN :productIds)")
    Page<OutboundDispatch> findByFiltersAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("originType") String originType,
            @Param("dispatchNoKeyword") String dispatchNoKeyword,
            @Param("orderNoKeyword") String orderNoKeyword,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);
}

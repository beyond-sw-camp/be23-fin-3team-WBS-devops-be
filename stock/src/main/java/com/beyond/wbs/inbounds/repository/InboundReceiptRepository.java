package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.InboundReceipts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipts, UUID> {
    List<InboundReceipts> findByInboundOrderId(UUID inboundOrderId);

    /**
     * 입고전표 목록 — 모든 필터 null 허용.
     *
     * originType / orderNoKeyword 는 InboundOrders 와 join 해서 적용.
     * 그 외 (warehouse / 날짜 / 전표번호) 는 InboundReceipts 자체 컬럼.
     */
    @Query(value =
            "SELECT r FROM InboundReceipts r " +
            "JOIN InboundOrders o ON o.id = r.inboundOrderId " +
            "WHERE r.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR r.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR r.receivedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR r.receivedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:receiptNoKeyword IS NULL OR LOWER(r.receiptNo) LIKE LOWER(CONCAT('%', :receiptNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "ORDER BY r.receivedAt DESC",
        countQuery =
            "SELECT COUNT(r) FROM InboundReceipts r " +
            "JOIN InboundOrders o ON o.id = r.inboundOrderId " +
            "WHERE r.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR r.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR r.receivedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR r.receivedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:receiptNoKeyword IS NULL OR LOWER(r.receiptNo) LIKE LOWER(CONCAT('%', :receiptNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%')))")
    Page<InboundReceipts> findByFilters(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("originType") String originType,
            @Param("receiptNoKeyword") String receiptNoKeyword,
            @Param("orderNoKeyword") String orderNoKeyword,
            Pageable pageable);

    /**
     * 멀티필터 + productIds EXISTS — 그 상품 라인이 1개 이상 포함된 입고전표만.
     */
    @Query(value =
            "SELECT DISTINCT r FROM InboundReceipts r " +
            "JOIN InboundOrders o ON o.id = r.inboundOrderId " +
            "WHERE r.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR r.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR r.receivedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR r.receivedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:receiptNoKeyword IS NULL OR LOWER(r.receiptNo) LIKE LOWER(CONCAT('%', :receiptNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "AND EXISTS (SELECT 1 FROM InboundReceiptItems i " +
            "    WHERE i.receiptId = r.id AND i.productId IN :productIds)",
        countQuery =
            "SELECT COUNT(DISTINCT r) FROM InboundReceipts r " +
            "JOIN InboundOrders o ON o.id = r.inboundOrderId " +
            "WHERE r.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR r.warehouseId = :warehouseId) " +
            "AND (:dateFrom IS NULL OR r.receivedAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR r.receivedAt < :dateTo) " +
            "AND (:originType IS NULL OR o.originType = :originType) " +
            "AND (:receiptNoKeyword IS NULL OR LOWER(r.receiptNo) LIKE LOWER(CONCAT('%', :receiptNoKeyword, '%'))) " +
            "AND (:orderNoKeyword IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :orderNoKeyword, '%'))) " +
            "AND EXISTS (SELECT 1 FROM InboundReceiptItems i " +
            "    WHERE i.receiptId = r.id AND i.productId IN :productIds)")
    Page<InboundReceipts> findByFiltersAndProductIds(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("originType") String originType,
            @Param("receiptNoKeyword") String receiptNoKeyword,
            @Param("orderNoKeyword") String orderNoKeyword,
            @Param("productIds") List<UUID> productIds,
            Pageable pageable);
}

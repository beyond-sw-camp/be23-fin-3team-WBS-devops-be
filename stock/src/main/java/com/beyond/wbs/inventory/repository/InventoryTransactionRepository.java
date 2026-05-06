package com.beyond.wbs.inventory.repository;

import com.beyond.wbs.inventory.domain.InventoryTransaction;
import com.beyond.wbs.inventory.domain.InventoryStatus;
import com.beyond.wbs.inventory.domain.RefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    /**
     * 특정 재고 row의 이력 조회 (시간 순)
     * - inventoryId 자체가 특정 회사의 재고 row라서 회사 필터가 간접적으로 걸림
     * - "이 재고가 어떻게 변해왔나" 추적
     */
    List<InventoryTransaction> findByInventoryIdOrderByCreatedAtAsc(UUID inventoryId);

    /**
     * 같은 이벤트(refId)로 발생한 이력들 조회
     * - 예: 출고지시서 승인으로 생긴 2개 row (available 차감 + reserved 증가)
     * - refId는 출고지시서/입고지시서 UUID라 다른 회사와 절대 충돌하지 않음
     */
    List<InventoryTransaction> findByRefIdAndRefType(UUID refId, RefType refType);

    List<InventoryTransaction> findByClientIdAndRefIdAndRefTypeOrderByCreatedAtAsc(UUID clientId, UUID refId, RefType refType);

    /**
     * 회사 단위 이력 조회 (최신순)
     * - 재고이력 화면에서 사용
     */
    List<InventoryTransaction> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    /**
     * 상품 단위 이력 조회
     * - 고객사별로 상품이 따로 등록되므로 productId 자체가 회사별로 고유
     *   (삼성물산 케이블 UUID ≠ LG전자 케이블 UUID)
     * - 따라서 clientId 필터 없이 productId만으로 조회해도 다른 회사 데이터가 섞이지 않음
     */
    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(UUID productId);

    /**
     * 날짜 범위 내 available 상태의 트랜잭션만 조회 (시간순)
     * - 날짜별 재고 현황 계산용
     * - statusTo = available 인 row 만 뽑아야 가용재고 변동만 집계됨
     */
    @Query("SELECT t FROM InventoryTransaction t " +
            "WHERE t.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR t.warehouseId = :warehouseId) " +
            "AND (:productId IS NULL OR t.productId = :productId) " +
            "AND t.statusTo = :status " +
            "AND t.createdAt >= :from AND t.createdAt < :to " +
            "ORDER BY t.createdAt ASC")
    List<InventoryTransaction> findByDateRangeAndStatus(
            @Param("clientId") UUID clientId,
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId,
            @Param("status") InventoryStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 특정 월의 txType 별 수량 합계 (스냅샷 배치에서 입고/출고 합산용)
     */
    @Query("SELECT t FROM InventoryTransaction t " +
            "WHERE t.clientId = :clientId " +
            "AND t.statusTo = InventoryStatus.available " +
            "AND t.createdAt >= :from AND t.createdAt < :to " +
            "ORDER BY t.createdAt ASC")
    List<InventoryTransaction> findAvailableTransactionsInRange(
            @Param("clientId") UUID clientId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}

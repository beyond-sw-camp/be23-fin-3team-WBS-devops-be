package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.PickingListStatus;
import com.beyond.wbs.outbounds.domain.PickingList;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PickinglistRepository extends JpaRepository<com.beyond.wbs.outbounds.domain.PickingList, UUID> {

    @Query("SELECT p FROM PickingList p " +
            "WHERE p.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR p.warehouseId = :warehouseId) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:assignedTo IS NULL OR p.assignedTo = :assignedTo)")
    Page<PickingList> findByConditions(@Param("clientId") UUID clientId,
                                       @Param("warehouseId") UUID warehouseId,
                                       @Param("status") PickingListStatus status,
                                       @Param("assignedTo") UUID assignedTo,
                                       Pageable pageable);

    @Query("SELECT COUNT(p) FROM PickingList p " +
            "WHERE p.clientId = :clientId " +
            "AND p.assignedTo = :assignedTo " +
            "AND p.status IN :statuses")
    long countActiveWorkload(@Param("clientId") UUID clientId,
                             @Param("assignedTo") UUID assignedTo,
                             @Param("statuses") Collection<PickingListStatus> statuses);

    /**
     * 멀티필터 검색 + productIds EXISTS — 그 상품 라인이 1개 이상 포함된 피킹리스트만.
     */
    @Query("SELECT DISTINCT p FROM PickingList p " +
            "WHERE p.clientId = :clientId " +
            "AND (:warehouseId IS NULL OR p.warehouseId = :warehouseId) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:assignedTo IS NULL OR p.assignedTo = :assignedTo) " +
            "AND EXISTS (SELECT 1 FROM PickingListItems i " +
            "    WHERE i.pickingListId = p.id AND i.productId IN :productIds)")
    Page<PickingList> findByConditionsAndProductIds(@Param("clientId") UUID clientId,
                                                     @Param("warehouseId") UUID warehouseId,
                                                     @Param("status") PickingListStatus status,
                                                     @Param("assignedTo") UUID assignedTo,
                                                     @Param("productIds") List<UUID> productIds,
                                                     Pageable pageable);
}

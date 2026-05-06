package com.beyond.wbs.etcinout.repository;

import com.beyond.wbs.etcinout.domain.EtcInoutOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EtcInoutOrderItemRepository extends JpaRepository<EtcInoutOrderItem, UUID> {

    List<EtcInoutOrderItem> findByEtcOrderId(UUID etcOrderId);

    /**
     * 기타출고 누적 검증용 — 동일 (productId + warehouseId) 에 대해
     * 자기 자신을 제외한 다른 approved 기타출고들의 미완료 수량 합계.
     *
     * approve 시점에 reserve를 잡지 않으므로, 가용재고에서 빠지지 않은 채 떠 있는 demand.
     * 검증할 때 이 양을 함께 빼야 누적 초과를 막을 수 있음.
     */
    @Query("SELECT COALESCE(SUM(item.qty), 0) " +
            "FROM EtcInoutOrderItem item " +
            "JOIN EtcInoutOrder o ON o.id = item.etcOrderId " +
            "WHERE o.clientId = :clientId " +
            "AND o.warehouseId = :warehouseId " +
            "AND item.productId = :productId " +
            "AND o.direction = com.beyond.wbs.etcinout.domain.Direction.out " +
            "AND o.status = com.beyond.wbs.etcinout.domain.EtcInoutStatus.approved " +
            "AND (:excludeOrderId IS NULL OR o.id <> :excludeOrderId)")
    int sumApprovedOutboundByProductAndWarehouse(@Param("clientId") UUID clientId,
                                                  @Param("productId") UUID productId,
                                                  @Param("warehouseId") UUID warehouseId,
                                                  @Param("excludeOrderId") UUID excludeOrderId);
}

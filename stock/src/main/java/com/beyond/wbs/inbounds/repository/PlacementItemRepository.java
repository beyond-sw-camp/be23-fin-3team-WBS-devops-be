package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.PlacementItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlacementItemRepository extends JpaRepository<PlacementItems, UUID> {
    List<PlacementItems> findByPlacementOrderId(UUID placementOrderId);

    /**
     * 적치지시서 내 상품 라인 필터링 — 상품 멀티필터 결과 productIds 와 매칭되는 라인만 반환.
     */
    List<PlacementItems> findByPlacementOrderIdAndProductIdIn(UUID placementOrderId,
                                                               Collection<UUID> productIds);

    // 특정 적치 지시서의 미적치 개수
    long countByPlacementOrderIdAndIsPlacedFalse(UUID placementOrderId);

    // 전체 적치 대기 목록 (회사 기준, 미적치만)
    @Query("SELECT pi FROM PlacementItems pi " +
           "JOIN PlacementOrders po ON pi.placementOrderId = po.id " +
           "WHERE po.clientId = :clientId AND pi.isPlaced = false")
    List<PlacementItems> findPendingByClientId(@Param("clientId") UUID clientId);

    // 특정 입고 지시서의 미적치 개수
    @Query("SELECT COUNT(pi) FROM PlacementItems pi " +
           "JOIN PlacementOrders po ON pi.placementOrderId = po.id " +
           "WHERE po.inboundOrderId = :orderId AND pi.isPlaced = false")
    long countPendingByInboundOrderId(@Param("orderId") UUID orderId);

    // 특정 입고 지시서의 전체 적치 품목 (hasDefect 판정 시 사용)
    @Query("SELECT pi FROM PlacementItems pi " +
           "JOIN PlacementOrders po ON pi.placementOrderId = po.id " +
           "WHERE po.inboundOrderId = :orderId")
    List<PlacementItems> findByInboundOrderId(@Param("orderId") UUID orderId);

    // 특정 로케이션에 배정된 미적치 품목들 — 같은 위치 중복 배정 방지용.
    List<PlacementItems> findByLocationIdAndIsPlacedFalse(UUID locationId);
}

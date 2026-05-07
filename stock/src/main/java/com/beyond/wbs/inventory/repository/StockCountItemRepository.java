package com.beyond.wbs.inventory.repository;

import com.beyond.wbs.inventory.domain.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {
    List<StockCountItem> findByCountOrderId(UUID countOrderId);

    /**
     * 실사지시서 내 상품 라인 필터링 — 상품 멀티필터(/product/search-advanced) 결과로
     * 받은 productIds 와 매칭되는 라인만 반환.
     */
    List<StockCountItem> findByCountOrderIdAndProductIdIn(UUID countOrderId,
                                                          Collection<UUID> productIds);

    /**
     * 모바일 작업자가 위치 QR 스캔 → 그 위치의 품목들 빠른 접근.
     */
    List<StockCountItem> findByCountOrderIdAndLocationId(UUID countOrderId, UUID locationId);

    /**
     * 모바일 작업자가 랙 QR 스캔 → 그 랙 안의 모든 location 들의 품목.
     * master 에서 rackId → locationIds 변환 후 호출.
     */
    List<StockCountItem> findByCountOrderIdAndLocationIdIn(UUID countOrderId, Collection<UUID> locationIds);
}

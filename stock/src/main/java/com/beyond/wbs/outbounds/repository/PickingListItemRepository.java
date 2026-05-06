package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.PickingListItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PickingListItemRepository extends JpaRepository<PickingListItems, UUID> {

    // 피킹리스트 기준 품목 목록조회
    List<PickingListItems> findByPickingListId(UUID pickingListId);

    // 출고지시서 품목 기준 조회
    List<PickingListItems> findByOutboundOrderItemId(UUID outboundOrderItemId);

    /**
     * 피킹리스트 내 상품 라인 필터링 — 상품 멀티필터(/product/search-advanced) 결과로
     * 받은 productIds 와 매칭되는 라인만 반환.
     */
    List<PickingListItems> findByPickingListIdAndProductIdIn(UUID pickingListId,
                                                              Collection<UUID> productIds);
}

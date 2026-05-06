package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundDispatchItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboundDispatchItemRepository extends JpaRepository<OutboundDispatchItems, UUID> {
    // 출고 전표 ID 기준 품목 목록 조회
    List<OutboundDispatchItems> findByDispatchId(UUID dispatchId);

    /**
     * 출고전표 내 상품 라인 필터링 — 상품 멀티필터 결과 productIds 와 매칭되는 라인만 반환.
     */
    List<OutboundDispatchItems> findByDispatchIdAndProductIdIn(UUID dispatchId,
                                                                Collection<UUID> productIds);
}

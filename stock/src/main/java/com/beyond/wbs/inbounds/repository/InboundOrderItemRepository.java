package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItems, UUID> {
    List<InboundOrderItems> findByInboundOrderId(UUID inboundOrderId);

    /**
     * 지시서 내 상품 라인 필터링 — 상품 멀티필터(/product/search-advanced) 결과로
     * 받은 productIds 와 매칭되는 라인만 반환.
     */
    List<InboundOrderItems> findByInboundOrderIdAndProductIdIn(UUID inboundOrderId,
                                                               Collection<UUID> productIds);
}

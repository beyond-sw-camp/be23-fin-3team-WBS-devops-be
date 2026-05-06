package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.InboundReceiptItemCondition;
import com.beyond.wbs.inbounds.domain.InboundReceiptItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InboundReceiptItemRepository extends JpaRepository<InboundReceiptItems, UUID> {
    List<InboundReceiptItems> findByReceiptId(UUID receiptId);
    List<InboundReceiptItems> findByReceiptIdIn(List<UUID> receiptIds);

    /**
     * 입고전표 내 상품 라인 필터링 — 상품 멀티필터 결과 productIds 와 매칭되는 라인만 반환.
     */
    List<InboundReceiptItems> findByReceiptIdAndProductIdIn(UUID receiptId,
                                                             Collection<UUID> productIds);

    // 특정 지시서의 정상품 적치 항목 (조건별 필터)
    // 적치 상태(isPlaced)는 이제 PlacementItems에서 관리되므로 이 레포는 조건만 필터
    List<InboundReceiptItems> findByReceiptIdInAndItemCondition(
            List<UUID> receiptIds, InboundReceiptItemCondition condition);
}

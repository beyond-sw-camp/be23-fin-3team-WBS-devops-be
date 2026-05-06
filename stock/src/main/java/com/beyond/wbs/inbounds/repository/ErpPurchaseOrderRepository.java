package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderStatus;
import com.beyond.wbs.inbounds.domain.ErpPurchaseOrders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ErpPurchaseOrderRepository extends JpaRepository<ErpPurchaseOrders, UUID> {
    List<ErpPurchaseOrders> findByClientIdAndStatus(UUID clientId, ErpPurchaseOrderStatus status);

    // 발주서 목록 화면용 — 모든 status 포함 (approved + closed) 그 client 의 PO 전체.
    List<ErpPurchaseOrders> findByClientId(UUID clientId);

    // 대시보드 "신규 발주" 카드용 — 입고지시서가 아직 만들어지지 않은 ERP 발주서 수.
    // (입고지시서 생성 시 ErpPurchaseOrders.status 가 approved → closed 로 바뀜.
    //  따라서 status = approved 카운트 = 입고지시서 미생성된 발주서 수)
    long countByClientIdAndStatus(UUID clientId, ErpPurchaseOrderStatus status);
}

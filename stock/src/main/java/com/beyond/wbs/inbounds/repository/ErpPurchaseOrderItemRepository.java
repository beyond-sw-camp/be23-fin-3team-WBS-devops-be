package com.beyond.wbs.inbounds.repository;

import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ErpPurchaseOrderItemRepository extends JpaRepository<ErpPurchaseOrderItems, UUID> {
    List<ErpPurchaseOrderItems> findByPurchaseOrderId(UUID purchaseOrderId);
}

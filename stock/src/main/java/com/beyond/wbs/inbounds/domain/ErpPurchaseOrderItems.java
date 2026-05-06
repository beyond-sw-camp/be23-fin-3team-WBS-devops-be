package com.beyond.wbs.inbounds.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "erp_purchase_order_items")
public class ErpPurchaseOrderItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // ERP 발주서 ID
    @Column(name = "purchase_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID purchaseOrderId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 발주 수량
    @Column(nullable = false)
    private Integer qty;

    // 발주 단가
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // 비고
    private String note;
}
// 더미데이터 → @PrePersist 필요 없음

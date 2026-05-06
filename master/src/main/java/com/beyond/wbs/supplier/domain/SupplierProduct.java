package com.beyond.wbs.supplier.domain;

import com.beyond.wbs.product.domain.Product;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "supplier_products")
public class SupplierProduct {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 100)
    private String venderSku;       // 협력사 자체 상품코드

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;   // 매입단가

    @Column
    private Integer leadTimeDays;   // 리드타임 (발주 후 입고까지 일수)

    @Builder.Default
    @Column(nullable = false)
    private Integer moq = 1;        // 최소발주수량

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPrimary = false; // 주거래여부

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
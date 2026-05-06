package com.beyond.wbs.location.domain;

import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductGroup;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "location_product_rules")
public class LocationProductRule {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // rack_id → location_id 로 변경
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    // 특정 상품 지정 (null 허용 - 상품그룹으로 지정할 수도 있으니)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // 상품그룹 지정 (null 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id")
    private ProductGroup productGroup;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        this.createdAt = java.time.LocalDateTime.now();
    }
}
package com.beyond.wbs.product.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
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
@Table(name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_client_sku", columnNames = {"client_id", "sku"}),
                @UniqueConstraint(name = "UQ_client_barcode", columnNames = {"client_id", "barcode"})
        }
)
public class Product extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OwnerType ownerType;

    @Column(name = "supplier_id", columnDefinition = "BINARY(16)")
    private UUID supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id")
    private ProductGroup productGroup;

    @Column(length = 50, nullable = false)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 150)
    private String nameEn;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20, nullable = false)
    private String unit;

    @Column
    private Integer unitPerBox;

    @Column(precision = 15, scale = 2)
    private BigDecimal standardPrice;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal width;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal depth;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal height;

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 부분 수정 — null 인 필드는 변경하지 않는다.
     * sku / clientId 는 PK 성격이라 수정 대상에서 제외.
     */
    public void update(String name, String nameEn, String description, String barcode,
                       String unit, Integer unitPerBox, BigDecimal standardPrice,
                       BigDecimal weight, BigDecimal width, BigDecimal depth, BigDecimal height,
                       UUID supplierId, ProductGroup productGroup) {
        if (name != null) this.name = name;
        if (nameEn != null) this.nameEn = nameEn;
        if (description != null) this.description = description;
        if (barcode != null) this.barcode = barcode;
        if (unit != null) this.unit = unit;
        if (unitPerBox != null) this.unitPerBox = unitPerBox;
        if (standardPrice != null) this.standardPrice = standardPrice;
        if (weight != null) this.weight = weight;
        if (width != null) this.width = width;
        if (depth != null) this.depth = depth;
        if (height != null) this.height = height;
        if (supplierId != null) this.supplierId = supplierId;
        if (productGroup != null) this.productGroup = productGroup;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
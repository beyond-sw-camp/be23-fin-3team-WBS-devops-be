package com.beyond.wbs.product.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "product_groups",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"client_id", "code"})
        }
)
public class ProductGroup extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 30, nullable = false)
    private String code;

    @Column(length = 100)
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 부분 수정 — null 인 필드는 변경하지 않는다.
     * code 는 외부 식별자 성격이므로 제외. category 는 이동을 허용(nullable 포함).
     */
    public void update(String name, String brand, String description, ProductCategory category) {
        if (name != null) this.name = name;
        if (brand != null) this.brand = brand;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
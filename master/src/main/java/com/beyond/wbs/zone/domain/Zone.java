package com.beyond.wbs.zone.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "zones")
public class Zone extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // 자기참조 - 대분류(null)이면 최상위, 값 있으면 중분류
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Zone parent;

    // 협력사 ID (중분류일 때만 사용, 대분류는 null)
    @Column(name = "partner_id")
    private Long partnerId;

    // 0: 대분류, 1: 중분류
    @Column(nullable = false)
    @Builder.Default
    private Integer depth = 0;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(length = 20, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ZoneType zoneType;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 대분류 존일 때 상품 카테고리 연결 (적치 추천 로직에서 사용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 부분 수정 — null 인 필드는 변경하지 않는다.
     * code 는 PK 성격이라 의도적으로 제외.
     */
    public void update(String name,
                       ZoneType zoneType,
                       Integer sortOrder,
                       ProductCategory category) {
        if (name != null) this.name = name;
        if (zoneType != null) this.zoneType = zoneType;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (category != null) this.category = category;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
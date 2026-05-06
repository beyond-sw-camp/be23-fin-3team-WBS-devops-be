package com.beyond.wbs.product.domain;

import com.beyond.wbs.code.MasterCodePolicy;
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
@Table(name = "product_categories",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"client_id", "code"})
        }
)
public class ProductCategory extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(length = MasterCodePolicy.MAX_LEN, nullable = false)
    private String code;

    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer depth;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 부분 수정 — null 인 필드는 변경하지 않는다.
     * code / parent / depth 는 트리 구조에 직접 영향을 주므로 수정 대상에서 제외.
     */
    public void update(String name, Integer sortOrder) {
        if (name != null) this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
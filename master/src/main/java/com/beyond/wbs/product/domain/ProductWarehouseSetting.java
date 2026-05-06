package com.beyond.wbs.product.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * (상품 × 창고) 별 운영 설정.
 *
 * 안전재고(minStockQty) 를 창고 단위로 따로 두기 위한 테이블.
 * row 가 없으면 = 안전재고 미설정 (low-stock 알림 대상에서 제외).
 *
 * unique key (product_id, warehouse_id) — 한 (상품, 창고) 조합당 1행.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
@Entity
@Table(
        name = "product_warehouse_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_product_warehouse",
                        columnNames = {"product_id", "warehouse_id"})
        },
        indexes = {
                @Index(name = "idx_pws_product", columnList = "product_id"),
                @Index(name = "idx_pws_warehouse", columnList = "warehouse_id")
        }
)
public class ProductWarehouseSetting extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    /** 안전재고 (가용재고가 이 값 이하가 되면 부족 알림) */
    @Column(name = "min_stock_qty", nullable = false)
    private Integer minStockQty;

    public void updateMinStockQty(Integer minStockQty) {
        if (minStockQty != null) {
            this.minStockQty = minStockQty;
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}

package com.beyond.wbs.inventory.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "stock_count_items")
public class StockCountItem {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 실사지시서 ID
    @Column(name = "count_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID countOrderId;

    // 상품 ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 로케이션 ID
    @Column(name = "location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 시스템상 수량 (실사 생성 시점 스냅샷)
    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    // 실사 수량 (작업자 직접 입력)
    @Column(name = "count_qty")
    private Integer countQty;

    // 차이 수량 (countQty - systemQty)
    @Column(name = "diff_qty")
    private Integer diffQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StockCountItemStatus status = StockCountItemStatus.pending;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "counted_by", columnDefinition = "BINARY(16)")
    private UUID countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    // 실사 수량 입력
    public void count(Integer countQty, String note, UUID countedBy) {
        this.countQty = countQty;
        this.diffQty = countQty - this.systemQty;
        this.status = StockCountItemStatus.counted;
        this.note = note;
        this.countedBy = countedBy;
        this.countedAt = LocalDateTime.now();
    }

    // 재고 조정 완료 표시
    public void markAdjusted() {
        this.status = StockCountItemStatus.adjusted;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
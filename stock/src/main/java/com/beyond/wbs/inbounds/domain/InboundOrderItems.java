package com.beyond.wbs.inbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
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
@Table(
        name = "inbound_order_items",
        indexes = {
                // 미리보기 입고예정 합산 — (product, inbound_order) 조인 가속용
                @Index(name = "idx_ibi_product_order", columnList = "product_id, inbound_order_id"),
                // 입고지시서 → 라인 조회
                @Index(name = "idx_ibi_order", columnList = "inbound_order_id")
        }
)
public class InboundOrderItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 입고지시서 ID
    @Column(name = "inbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inboundOrderId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 지시 수량
    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    // 입고 수량 (실제 입고된 수량, 기본값 0)
    @Builder.Default
    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty = 0;

    // 불량 수량 (기본값 0)
    @Builder.Default
    @Column(name = "defect_qty", nullable = false)
    private Integer defectQty = 0;

    // 상태
    // pending(대기) → receiving(입고중) → completed(완료) / shortage(부족)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InboundOrderItemStatus status;

    // 입고 단가
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // 검수 처리: 입고수량/불량수량 반영 + 상태 변경
    // 지시수량에 미달이면 shortage(부족) 종결 상태로 확정한다.
    public void inspect(int receivedQty, int defectQty) {
        // 초과 수량 차단
        if (receivedQty + defectQty > this.orderedQty) {
            throw new IllegalArgumentException(
                    "지시 수량(" + this.orderedQty + "개)을 초과할 수 없습니다. " +
                    "입력 수량: " + (receivedQty + defectQty) + "개");
        }

        this.receivedQty += receivedQty;
        this.defectQty += defectQty;

        int totalProcessed = this.receivedQty + this.defectQty;
        if (totalProcessed >= this.orderedQty) {
            this.status = InboundOrderItemStatus.completed;
        } else {
            this.status = InboundOrderItemStatus.shortage;
        }
    }

    public static InboundOrderItems fromAsnItem(ErpPurchaseOrderItems asnItem, UUID inboundOrderId) {
        return InboundOrderItems.builder()
                .inboundOrderId(inboundOrderId)
                .productId(asnItem.getProductId())
                .orderedQty(asnItem.getQty())
                .receivedQty(0)
                .defectQty(0)
                .status(InboundOrderItemStatus.pending)
                .unitPrice(asnItem.getUnitPrice())
                .build();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}

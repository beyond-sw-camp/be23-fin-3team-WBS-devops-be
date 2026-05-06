package com.beyond.wbs.outbounds.domain;

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
        name = "outbound_order_items",
        indexes = {
                // 미리보기 가용재고 — (product, outbound_order) 조인 가속용 covering 인덱스
                @Index(name = "idx_obi_product_order", columnList = "product_id, outbound_orders_id"),
                // 출고지시서 → 라인 조회
                @Index(name = "idx_obi_order", columnList = "outbound_orders_id")
        }
)
public class OutboundOrderItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 출고지시서 ID
    @Column(name = "outbound_orders_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrdersId;

    // 상품ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 지시수량
    @Column(name = "ordered_qty")
    private Integer orderedQty;

    // 예약확정수량 (승인 시 available 에서 실제로 잡은 수량)
    // orderedQty 와 같으면 전량 예약 완료, 적으면 부분 예약 (입고 완료 후 자동 보충)
    @Column(name = "reserved_qty")
    @Builder.Default
    private Integer reservedQty = 0;

    // 피킹수량
    @Column(name = "picked_qty")
    private Integer pickedQty;

    // 출고확정수량
    @Column(name = "dispatched_qty")
    private Integer dispatchedQty;

    // 출고단가
    // BigDecimal을 사용함으로써 부동소수점 오류 방지
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // 상태
    @Enumerated(EnumType.STRING)
    private OutboundOrderItemsStatus status;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    // 피킹 결과 반영
    public void applyPickedResult(Integer pickedQty, OutboundOrderItemsStatus status) {
        this.pickedQty = pickedQty;
        this.status = status;
    }

}

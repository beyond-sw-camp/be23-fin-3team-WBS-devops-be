package com.beyond.wbs.outbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "outbound_dispatch_items")
public class OutboundDispatchItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 출고전표 ID
    @Column(name = "dispatch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID dispatchId;

    // 상품 ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 출고지시서품목 ID
    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    // 출고수량
    @Column(nullable = false)
    private Integer qty;

    // LOT번호 - 같은 조건으로 생산된 제품 묶음에 붙이는 번호
    private String lotNo;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

}

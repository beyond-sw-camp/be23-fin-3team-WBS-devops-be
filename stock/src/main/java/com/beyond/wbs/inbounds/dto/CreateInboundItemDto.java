package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.InboundOrderItemStatus;
import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateInboundItemDto {
    @NotNull
    private UUID productId;
    @NotNull
    private Integer qty;
    private BigDecimal unitPrice;

    /**
     * DTO → 입고 지시서 품목 엔티티 변환
     * productId 는 프론트에서 선택한 상품 UUID를 그대로 사용한다.
     */
    public InboundOrderItems toEntity(UUID inboundOrderId, UUID productId) {
        return InboundOrderItems.builder()
                .inboundOrderId(inboundOrderId)
                .productId(productId)
                .orderedQty(this.qty)
                .receivedQty(0)
                .defectQty(0)
                .status(InboundOrderItemStatus.pending)
                .unitPrice(this.unitPrice)
                .build();
    }
}

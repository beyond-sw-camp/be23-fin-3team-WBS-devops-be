package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class InboundOrderItemResDto {
    private UUID id;
    private UUID orderId;
    private UUID productId;
    private String sku;
    private String productName;
    private Integer orderedQty;
    private Integer receivedQty;
    private Integer defectiveQty;
    // 미입고 잔여 수량 = orderedQty - receivedQty - defectiveQty (0 미만이면 0)
    private Integer remainingQty;
    private BigDecimal unitPrice;
    private String status;
    private String lotNo;

    public static InboundOrderItemResDto fromEntity(InboundOrderItems item, ProductResDto product) {
        String sku = product != null && product.getSku() != null ? product.getSku() : "";
        String name = product != null && product.getName() != null ? product.getName() : "";
        int remaining = item.getOrderedQty() - item.getReceivedQty() - item.getDefectQty();
        return InboundOrderItemResDto.builder()
                .id(item.getId())
                .orderId(item.getInboundOrderId())
                .productId(item.getProductId())
                .sku(sku)
                .productName(name)
                .orderedQty(item.getOrderedQty())
                .receivedQty(item.getReceivedQty())
                .defectiveQty(item.getDefectQty())
                .remainingQty(Math.max(remaining, 0))
                .unitPrice(item.getUnitPrice())
                .status(item.getStatus().name())
                .build();
    }

    public static InboundOrderItemResDto fromEntity(InboundOrderItems item) {
        return fromEntity(item, null);
    }
}

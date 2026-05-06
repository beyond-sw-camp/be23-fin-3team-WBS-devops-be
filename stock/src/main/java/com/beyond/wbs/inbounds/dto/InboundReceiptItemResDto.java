package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import com.beyond.wbs.inbounds.domain.InboundReceiptItems;
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
public class InboundReceiptItemResDto {
    private UUID id;
    private UUID receiptId;
    private UUID orderItemId;
    private UUID productId;
    private String sku;
    private String productName;
    private Integer qty;
    private String lotNo;
    private String itemCondition;
    private UUID inspectedBy;
    private BigDecimal unitPrice;
    private String createdAt;

    public static InboundReceiptItemResDto fromEntity(
            InboundReceiptItems item,
            InboundOrderItems orderItem,
            ProductResDto product
    ) {
        String sku = product != null && product.getSku() != null ? product.getSku() : "";
        String name = product != null && product.getName() != null ? product.getName() : "";
        return InboundReceiptItemResDto.builder()
                .id(item.getId())
                .receiptId(item.getReceiptId())
                .orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .sku(sku)
                .productName(name)
                .qty(item.getQty())
                .lotNo(item.getLotNo())
                .itemCondition(item.getItemCondition() != null ? item.getItemCondition().name() : null)
                .inspectedBy(item.getInspectedBy())
                .unitPrice(orderItem != null ? orderItem.getUnitPrice() : BigDecimal.ZERO)
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .build();
    }
}

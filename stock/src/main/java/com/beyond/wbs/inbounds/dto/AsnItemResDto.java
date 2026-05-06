package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderItems;
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
public class AsnItemResDto {
    private UUID productId;
    private String sku;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;

    // 상품이 Master Service 에 등록되어 있는지 여부.
    // 프론트 발주서 미리보기에서 "미등록 상품" 경고 표시 + 상품등록 유도에 사용.
    private Boolean matched;

    public static AsnItemResDto fromEntity(ErpPurchaseOrderItems item, ProductResDto product) {
        String sku = product != null && product.getSku() != null ? product.getSku() : null;
        String name = product != null && product.getName() != null ? product.getName() : null;
        return AsnItemResDto.builder()
                .productId(item.getProductId())
                .sku(sku)
                .productName(name)
                .qty(item.getQty())
                .unitPrice(item.getUnitPrice())
                .matched(product != null)
                .build();
    }
}

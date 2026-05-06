package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.OutboundOrderItemsStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class OutboundOrderItemResDto {
    // 품목 ID
    private UUID id;

    // 상품 ID
    private UUID productId;

    // SKU (Master Service Feign 조회 결과)
    private String sku;

    // 상품명 (Master Service Feign 조회 결과)
    private String productName;

    // 주문 수량
    private Integer orderedQty;

    // 피킹 수량
    private Integer pickedQty;

    // 출고 수량
    private Integer dispatchedQty;

    // 단가
    private BigDecimal unitPrice;

    // 품목 상태
    private OutboundOrderItemsStatus status;
}

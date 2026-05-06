package com.beyond.wbs.outbounds.dtos;

import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class OutboundDispatchItemResDto {
    // 품목 ID
    private UUID id;

    // 상품 ID
    private UUID productId;

    // SKU (인쇄용)
    private String sku;

    // 상품명 (TODO: Feign Client로 Master Service 조회)
    private String productName;

    // 위치코드 (TODO: Feign Client로 Master Service 조회)
    private String locationCode;

    // 출고 수량
    private Integer qty;

    // 단가 (전표 금액 합계용)
    private java.math.BigDecimal unitPrice;

    // LOT 번호
    private String lotNo;
}

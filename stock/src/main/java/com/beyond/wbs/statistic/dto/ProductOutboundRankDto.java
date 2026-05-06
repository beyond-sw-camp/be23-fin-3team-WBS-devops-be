package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.util.UUID;

/**
 * 품번별 출고 순위 응답 DTO
 * - 기간 내 출고량 기준 내림차순 정렬
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class ProductOutboundRankDto {
    private int rank;
    private UUID productId;
    private String productName;
    private String sku;
    private Integer outboundQty;    // 기간 내 총 출고 수량
}

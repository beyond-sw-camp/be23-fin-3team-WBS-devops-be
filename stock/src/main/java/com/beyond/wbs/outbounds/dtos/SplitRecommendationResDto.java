package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 분할 출고 자동 추천 응답.
 *
 * recommendations: CreateOutboundFromSalesOrdersReqDto.WarehouseAllocation 과 동일 구조 —
 * FE 가 검토 후 그대로 또는 수정해서 생성 API 에 제출 가능.
 *
 * unallocatedQty: 모든 창고 합쳐도 못 채운 부족분. 0 이면 전량 분배 성공.
 *
 * shortages: 부족분이 발생한 품목별 상세 (productId, productName, requiredQty, allocatedQty).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitRecommendationResDto {
    private List<CreateOutboundFromSalesOrdersReqDto.WarehouseAllocation> recommendations;
    private int unallocatedQty;
    private List<ProductShortage> shortages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductShortage {
        private UUID productId;
        private String productName;
        private int requiredQty;
        private int allocatedQty;
        private int shortageQty; // requiredQty - allocatedQty
    }
}

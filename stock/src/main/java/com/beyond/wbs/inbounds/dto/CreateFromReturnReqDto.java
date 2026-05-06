package com.beyond.wbs.inbounds.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 반품 입고지시서 생성 요청 DTO.
 *
 * 출고지시서를 기준으로 반품 수량을 입력받아
 * 입고지시서(originType:"return")를 자동 생성한다.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateFromReturnReqDto {

    // 원본 출고지시서 ID — 어느 출고의 반품인지 추적
    @NotNull
    private UUID outboundOrderId;

    // 반품 상품을 입고할 창고 ID
    @NotNull
    private UUID warehouseId;

    // 반품 사유
    private String reason;

    // 반품 품목 목록
    @NotNull
    @Valid
    private List<ReturnItem> items;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ReturnItem {
        @NotNull
        private UUID productId;

        @NotNull
        private Integer qty;  // 반품 수량
    }
}

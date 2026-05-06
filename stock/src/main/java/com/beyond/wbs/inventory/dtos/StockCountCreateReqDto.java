package com.beyond.wbs.inventory.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountCreateReqDto {

    @NotNull(message = "창고를 선택해주세요.")
    private UUID warehouseId;

    private String note;

    @NotEmpty(message = "실사 품목을 하나 이상 추가해주세요.")
    private List<StockCountItemReqDto> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockCountItemReqDto {

        @NotNull(message = "상품을 선택해주세요.")
        private UUID productId;

        @NotNull(message = "위치를 선택해주세요.")
        private UUID locationId;
    }
}
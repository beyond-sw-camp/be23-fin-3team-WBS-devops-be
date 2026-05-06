package com.beyond.wbs.inventory.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItemInputDto {

    @NotNull(message = "실사 수량을 입력해주세요.")
    @Min(value = 0, message = "실사 수량은 0 이상이어야 합니다.")
    private Integer countQty;

    private String note;
}
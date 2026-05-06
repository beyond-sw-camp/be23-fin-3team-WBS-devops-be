package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class PickItemReqDto {

    // 실제 피킹한 수량
    @NotNull(message = "피킹수량을 입력해주세요.")
    @Min(value = 0, message = "피킹 수량은 0 이상이어야 합니다.")
    private Integer pickedQty;
}

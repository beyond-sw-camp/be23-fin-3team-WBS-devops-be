package com.beyond.wbs.etcinout.dto;


import com.beyond.wbs.etcinout.domain.ItemCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutItemDto {

    @NotNull(message = "상품을 선택해주세요.")
    private UUID productId;

    @NotNull(message = "위치를 선택해주세요.")
    private UUID locationId;

    @NotNull(message = "수량을 입력해주세요.")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private Integer qty;

    private String lotNo;
    private ItemCondition condition;
    private String defectReason;
    private String note;
}

package com.beyond.wbs.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TransferOrderItemReqDto {

    @NotNull(message = "상품 ID는 필수입니다.")
    private UUID productId;

    @NotNull(message = "출발 로케이션은 필수입니다.")
    private UUID fromLocationId;

    @NotNull(message = "도착 로케이션은 필수입니다.")
    private UUID toLocationId;

    @NotNull(message = "이동 수량은 필수입니다.")
    @Min(value = 1, message = "이동 수량은 1 이상이어야 합니다.")
    private Integer orderedQty;

    private String lotNo;
}

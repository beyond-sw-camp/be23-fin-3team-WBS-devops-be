package com.beyond.wbs.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferProcessItemReqDto {

    @NotNull(message = "정상 수량은 필수입니다.")
    @Min(value = 0, message = "정상 수량은 0 이상이어야 합니다.")
    private Integer goodQty;

    @NotNull(message = "불량 수량은 필수입니다.")
    @Min(value = 0, message = "불량 수량은 0 이상이어야 합니다.")
    private Integer defectQty;
}

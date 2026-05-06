package com.beyond.wbs.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모바일 — 적치 요청 바디.
 * 도착지에 놓은 정상/불량 수량.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferPlaceItemReqDto {

    @NotNull
    @Min(0)
    private Integer goodQty;

    @NotNull
    @Min(0)
    private Integer defectQty;
}

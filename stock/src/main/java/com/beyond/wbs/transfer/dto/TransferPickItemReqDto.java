package com.beyond.wbs.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모바일 — 픽업 요청 바디.
 * 출발지에서 꺼낸 수량.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferPickItemReqDto {

    @NotNull
    @Min(1)
    private Integer qty;
}

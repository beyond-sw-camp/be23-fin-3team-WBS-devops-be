package com.beyond.wbs.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class TransferOrderCreateReqDto {

    @NotNull(message = "출발 창고는 필수입니다.")
    private UUID fromWarehouseId;

    @NotNull(message = "도착 창고는 필수입니다.")
    private UUID toWarehouseId;

    @NotNull(message = "이동 예정일은 필수입니다.")
    private LocalDate expectedDate;

    private String note;

    @NotEmpty(message = "이동 품목은 최소 1개 이상이어야 합니다.")
    @Valid
    private List<TransferOrderItemReqDto> items;
}

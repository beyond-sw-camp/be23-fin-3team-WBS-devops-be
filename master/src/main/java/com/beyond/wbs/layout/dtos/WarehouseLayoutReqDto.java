package com.beyond.wbs.layout.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class WarehouseLayoutReqDto {

    @NotNull
    private UUID warehouseId;

    @NotNull
    private Integer canvasWidth;

    @NotNull
    private Integer canvasHeight;

    private String bgColor;
}
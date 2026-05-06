package com.beyond.wbs.layout.dtos;

import com.beyond.wbs.layout.domain.WarehouseLayout;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class WarehouseLayoutResDto {
    private UUID id;
    private UUID warehouseId;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private String bgColor;

    public static WarehouseLayoutResDto from(WarehouseLayout layout) {
        return WarehouseLayoutResDto.builder()
                .id(layout.getId())
                .warehouseId(layout.getWarehouse().getId())
                .canvasWidth(layout.getCanvasWidth())
                .canvasHeight(layout.getCanvasHeight())
                .bgColor(layout.getBgColor())
                .build();
    }
}
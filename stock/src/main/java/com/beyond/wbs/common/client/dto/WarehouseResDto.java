package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class WarehouseResDto {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String warehouseType;  // NORMAL / RETURN_DEFECT / DISPOSAL
    private Boolean isActive;
}

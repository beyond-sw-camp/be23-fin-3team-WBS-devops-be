package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZoneResDto {
    private UUID id;
    private UUID warehouseId;
    private UUID parentId;
    private Long partnerId;
    private Integer depth;
    private String name;
    private String code;
    private String zoneType;
    private Integer sortOrder;
    private Boolean isActive;
    private UUID categoryId;
    private String categoryName;
}

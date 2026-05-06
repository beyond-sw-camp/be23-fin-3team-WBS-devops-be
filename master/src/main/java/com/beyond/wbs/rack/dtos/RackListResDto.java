package com.beyond.wbs.rack.dtos;

import com.beyond.wbs.rack.domain.Rack;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RackListResDto {
    private UUID id;
    private UUID zoneId;
    private String zoneName;
    private UUID warehouseId;
    private String name;
    private String code;
    private UUID supplierId;
    private String supplierName;
    private Integer levelNo;
    private String levelGuideJson;
    private Integer maxCapacity;
    private Integer widthMm;
    private Integer depthMm;
    private Integer heightMm;
    private Boolean isActive;

    public static RackListResDto from(Rack rack) {
        return RackListResDto.builder()
                .id(rack.getId())
                .zoneId(rack.getZone() != null ? rack.getZone().getId() : null)
                .zoneName(rack.getZone() != null ? rack.getZone().getName() : null)
                .warehouseId(rack.getZone() != null && rack.getZone().getWarehouse() != null
                        ? rack.getZone().getWarehouse().getId() : null)
                .name(rack.getName())
                .code(rack.getCode())
                .supplierId(rack.getSupplier() != null ? rack.getSupplier().getId() : null)
                .supplierName(rack.getSupplier() != null ? rack.getSupplier().getName() : null)
                .levelNo(rack.getLevelNo())
                .levelGuideJson(rack.getLevelGuideJson())
                .maxCapacity(rack.getMaxCapacity())
                .widthMm(rack.getWidthMm())
                .depthMm(rack.getDepthMm())
                .heightMm(rack.getHeightMm())
                .isActive(rack.getIsActive())
                .build();
    }
}

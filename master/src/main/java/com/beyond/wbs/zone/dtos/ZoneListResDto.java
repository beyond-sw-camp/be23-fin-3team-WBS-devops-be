package com.beyond.wbs.zone.dtos;

import com.beyond.wbs.zone.domain.Zone;
import com.beyond.wbs.zone.domain.ZoneType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ZoneListResDto {
    private UUID id;
    private UUID warehouseId;
    private UUID parentId;
    private Long partnerId;       // legacy
    private Integer depth;
    private String name;
    private String code;
    private ZoneType zoneType;
    private Integer sortOrder;
    private Boolean isActive;
    private UUID categoryId;
    private String categoryName;
    // 해당 구역의 활성 랙 수 — 목록·상세 카드에서 표시
    private Integer rackCount;

    public static ZoneListResDto from(Zone zone) {
        return from(zone, 0);
    }

    public static ZoneListResDto from(Zone zone, int rackCount) {
        return ZoneListResDto.builder()
                .id(zone.getId())
                .warehouseId(zone.getWarehouse() != null ? zone.getWarehouse().getId() : null)
                .parentId(zone.getParent() != null ? zone.getParent().getId() : null)
                .partnerId(zone.getPartnerId())
                .depth(zone.getDepth())
                .name(zone.getName())
                .code(zone.getCode())
                .zoneType(zone.getZoneType())
                .sortOrder(zone.getSortOrder())
                .isActive(zone.getIsActive())
                .categoryId(zone.getCategory() != null ? zone.getCategory().getId() : null)
                .categoryName(zone.getCategory() != null ? zone.getCategory().getName() : null)
                .rackCount(rackCount)
                .build();
    }
}
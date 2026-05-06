package com.beyond.wbs.layout.dtos;

import com.beyond.wbs.layout.domain.ZoneLayout;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ZoneLayoutResDto {
    private UUID id;
    private UUID zoneId;
    private String zoneName;
    private Integer posX;
    private Integer posY;
    private Integer width;
    private Integer height;
    private Integer rotation;
    private String color;
    private Integer sortOrder;

    public static ZoneLayoutResDto from(ZoneLayout layout) {
        return ZoneLayoutResDto.builder()
                .id(layout.getId())
                .zoneId(layout.getZone().getId())
                .zoneName(layout.getZone().getName())
                .posX(layout.getPosX())
                .posY(layout.getPosY())
                .width(layout.getWidth())
                .height(layout.getHeight())
                .rotation(layout.getRotation())
                .color(layout.getColor())
                .sortOrder(layout.getSortOrder())
                .build();
    }
}

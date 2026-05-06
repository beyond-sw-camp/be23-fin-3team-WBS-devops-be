package com.beyond.wbs.layout.dtos;

import com.beyond.wbs.layout.domain.RackLayout;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RackLayoutResDto {
    private UUID id;
    private UUID rackId;
    private String rackName;
    private UUID zoneId;
    private Integer posX;
    private Integer posY;
    private Integer width;
    private Integer height;
    private Integer rotation;
    private String color;

    public static RackLayoutResDto from(RackLayout layout) {
        return RackLayoutResDto.builder()
                .id(layout.getId())
                .rackId(layout.getRack().getId())
                .rackName(layout.getRack().getName())
                .zoneId(layout.getZone().getId())
                .posX(layout.getPosX())
                .posY(layout.getPosY())
                .width(layout.getWidth())
                .height(layout.getHeight())
                .rotation(layout.getRotation())
                .color(layout.getColor())
                .build();
    }
}
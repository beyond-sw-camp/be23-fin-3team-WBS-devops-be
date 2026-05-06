package com.beyond.wbs.layout.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class RackLayoutReqDto {

    @NotNull
    private UUID rackId;

    @NotNull
    private UUID zoneId;

    @NotNull
    private Integer posX;

    @NotNull
    private Integer posY;

    @NotNull
    private Integer width;

    @NotNull
    private Integer height;

    private Integer rotation;  // 0/90/180/270

    private String color;
}
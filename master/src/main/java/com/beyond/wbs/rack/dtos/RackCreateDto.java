package com.beyond.wbs.rack.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class RackCreateDto {

    @NotNull
    private UUID zoneId;

    // 검증/표시용 (저장은 zone 을 통해 도달). nullable.
    private UUID warehouseId;

    private UUID supplierId;

    @NotBlank
    @Size(max = 100)
    private String name;

    // code 는 백엔드에서 자동 생성 (RK-{구역코드}-{협력사코드}-001 형식)

    private Integer levelNo;
    private String levelGuideJson;
    @NotNull
    @Positive
    private Integer maxCapacity;

    // 물리 치수 (정보성, nullable)
    private Integer widthMm;
    private Integer depthMm;
    private Integer heightMm;

}

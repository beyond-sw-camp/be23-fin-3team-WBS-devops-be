package com.beyond.wbs.zone.dtos;

import com.beyond.wbs.zone.domain.ZoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ZoneCreateDto {

    @NotNull
    private UUID warehouseId;
    private UUID categoryId;

    // 중분류일 때만 값 있음, 대분류면 null
    private UUID parentId;

    // 중분류일 때만 값 있  음
    private Long partnerId;

    @NotBlank
    private String name;

    // code 는 백엔드에서 자동 생성 (ZN-SEL-ELC-001 형식)

    private ZoneType zoneType;

    private Integer sortOrder;

}
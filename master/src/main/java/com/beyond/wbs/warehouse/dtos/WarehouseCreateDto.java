package com.beyond.wbs.warehouse.dtos;

import com.beyond.wbs.code.enums.RegionCode;
import com.beyond.wbs.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WarehouseCreateDto {

    @NotBlank
    private String name;

    private String address;

    // 창고 타입 — 미지정 시 NORMAL
    private WarehouseType warehouseType;

    // 지역 코드 (SEL, PUS, DAE 등) — 코드 자동 생성에 사용. 미지정 시 SEL
    private RegionCode regionCode;
}
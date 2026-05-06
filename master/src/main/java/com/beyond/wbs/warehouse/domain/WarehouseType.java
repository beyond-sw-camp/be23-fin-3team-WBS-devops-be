package com.beyond.wbs.warehouse.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WarehouseType {
    NORMAL("NOR", "정상창고"),
    RETURN_DEFECT("RET", "반품+불량창고"),
    DISPOSAL("DIS", "폐기창고");

    private final String code;
    private final String description;
}

package com.beyond.wbs.warehouse.dtos;

import com.beyond.wbs.warehouse.domain.Warehouse;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class WarehouseListResDto {
    private final UUID id;
    private final String name;
    private final String code;
    private final String address;
    private final String warehouseType;
    private final Boolean isActive;
    private final LocalDateTime updatedAt;

    // 목록 카드/테이블 요약 지표
    private final int zoneCount;
    private final int rackCount;

    // JPQL `new` 생성자
    public WarehouseListResDto(UUID id, String name, String code, String address,
                               String warehouseType, Boolean isActive, LocalDateTime updatedAt,
                               Long zoneCount, Long rackCount) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.address = address;
        this.warehouseType = warehouseType;
        this.isActive = isActive;
        this.updatedAt = updatedAt;
        this.zoneCount = zoneCount != null ? zoneCount.intValue() : 0;
        this.rackCount = rackCount != null ? rackCount.intValue() : 0;
    }

    public static WarehouseListResDto from(Warehouse warehouse) {
        return new WarehouseListResDto(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getCode(),
                warehouse.getAddress(),
                warehouse.getWarehouseType() != null ? warehouse.getWarehouseType().name() : null,
                warehouse.getIsActive(),
                warehouse.getUpdatedTime(),
                0L,
                0L
        );
    }
}

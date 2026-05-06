package com.beyond.wbs.warehouse.dtos;

import com.beyond.wbs.warehouse.domain.Warehouse;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 창고 상세 조회 응답 DTO.
 *
 * 목록용 {@link WarehouseListResDto} 와 분리하여, 상세 화면 전용 필드
 * (managerName / phone / notes) 를 함께 반환한다.
 */
@Getter
public class WarehouseDetailResDto {
    private final UUID id;
    private final String code;
    private final String name;
    private final String address;
    private final String warehouseType;
    private final Boolean isActive;
    private final int zoneCount;
    private final int rackCount;
    private final LocalDateTime updatedAt;

    // 상세 전용
    private final String managerName;
    private final String phone;
    private final String notes;

    // JPQL `new` 생성자 — COUNT(...) 는 Long 반환 → int 변환
    public WarehouseDetailResDto(UUID id, String code, String name, String address,
                                 String warehouseType, Boolean isActive, LocalDateTime updatedAt,
                                 String managerName, String phone, String notes,
                                 Long zoneCount, Long rackCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.address = address;
        this.warehouseType = warehouseType;
        this.isActive = isActive;
        this.updatedAt = updatedAt;
        this.managerName = managerName;
        this.phone = phone;
        this.notes = notes;
        this.zoneCount = zoneCount != null ? zoneCount.intValue() : 0;
        this.rackCount = rackCount != null ? rackCount.intValue() : 0;
    }

    /**
     * 엔티티 + 집계값으로 DTO 생성 (JPQL projection 을 쓰지 않는 경로용).
     */
    public static WarehouseDetailResDto from(Warehouse w, int zoneCount, int rackCount) {
        return new WarehouseDetailResDto(
                w.getId(),
                w.getCode(),
                w.getName(),
                w.getAddress(),
                w.getWarehouseType() != null ? w.getWarehouseType().name() : null,
                w.getIsActive(),
                w.getUpdatedTime(),
                w.getManagerName(),
                w.getPhone(),
                w.getNotes(),
                (long) zoneCount,
                (long) rackCount
        );
    }
}

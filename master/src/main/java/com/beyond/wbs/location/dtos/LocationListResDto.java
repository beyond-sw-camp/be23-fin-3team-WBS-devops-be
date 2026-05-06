package com.beyond.wbs.location.dtos;

import com.beyond.wbs.location.domain.Location;
import com.beyond.wbs.rack.domain.Rack;
import com.beyond.wbs.zone.domain.Zone;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 로케이션 조회 응답 DTO.
 *
 * 긴 단일 code (예: LC-RK-ZN-SEL-ELEC-002-LGX-001-03) 외에,
 * 프론트에서 컬럼 분리 표시가 가능하도록 rack/zone 분해 필드를 함께 내려준다.
 */
@Getter
@Builder
public class LocationListResDto {
    private UUID id;
    private UUID rackId;
    private Integer floorNo;
    private String code;
    private String barcode;
    private Integer maxCapacity;
    private Boolean isActive;

    // 분해 필드 — 프론트 표시용
    private String rackCode;   // 예: RK-ZN-SEL-ELEC-002-LGX-001
    private String rackName;   // 예: ELEC-LogiX-01
    private UUID zoneId;
    private String zoneCode;   // 예: ZN-SEL-ELEC-002
    private String zoneName;   // 예: 전자기기존
    private String zoneType;   // STORAGE | INBOUND | OUTBOUND | DEFECT — staging 검증용

    public static LocationListResDto from(Location location) {
        Rack rack = location.getRack();
        Zone zone = rack != null ? rack.getZone() : null;
        return LocationListResDto.builder()
                .id(location.getId())
                .rackId(rack != null ? rack.getId() : null)
                .floorNo(location.getFloorNo())
                .code(location.getCode())
                .barcode(location.getBarcode())
                .maxCapacity(location.getMaxCapacity())
                .isActive(location.getIsActive())
                .rackCode(rack != null ? rack.getCode() : null)
                .rackName(rack != null ? rack.getName() : null)
                .zoneId(zone != null ? zone.getId() : null)
                .zoneCode(zone != null ? zone.getCode() : null)
                .zoneName(zone != null ? zone.getName() : null)
                .zoneType(zone != null && zone.getZoneType() != null ? zone.getZoneType().name() : null)
                .build();
    }
}
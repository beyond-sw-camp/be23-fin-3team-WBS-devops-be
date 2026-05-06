package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class LocationResDto {
    private UUID id;
    private UUID rackId;
    private Integer floorNo;
    private String code;
    private String barcode;
    private Integer maxCapacity;
    private Boolean isActive;

    // 분해 필드 — master 에서 내려주는 표시용 정보
    private String rackCode;
    private String rackName;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private String zoneType;   // STORAGE | INBOUND | OUTBOUND | DEFECT
}

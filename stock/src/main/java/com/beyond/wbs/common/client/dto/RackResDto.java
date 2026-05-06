package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * master 모듈의 RackListResDto에 매핑되는 Feign 응답 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RackResDto {
    private UUID id;
    private String name;
    private String code;
    private UUID supplierId;
    private String supplierName;
    private UUID zoneId;
    private String zoneName;
    private Integer levelNo;
    private Integer maxCapacity;
    private Integer widthMm;
    private Integer depthMm;
    private Integer heightMm;
    private Boolean isActive;
}

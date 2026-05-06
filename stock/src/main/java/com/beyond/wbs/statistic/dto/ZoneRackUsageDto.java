package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.util.UUID;

/**
 * 구역별 적재율 응답 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class ZoneRackUsageDto {
    private UUID zoneId;
    private String zoneName;
    private String zoneCode;
    private Integer totalLocations;
    private Integer occupiedLocations;
    private Double occupancyRate;
    private Integer totalRacks;
    private Integer usedRacks;
}

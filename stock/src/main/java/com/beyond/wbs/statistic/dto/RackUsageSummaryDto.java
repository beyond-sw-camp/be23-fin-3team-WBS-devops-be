package com.beyond.wbs.statistic.dto;

import lombok.*;

/**
 * 창고별 적재율 요약 응답 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class RackUsageSummaryDto {
    private Integer totalLocations;
    private Integer occupiedLocations;
    private Integer emptyLocations;
    private Double occupancyRate;
    private Integer totalRacks;
    private Integer usedRacks;
    private Integer emptyRacks;
    private Double rackOccupancyRate;
}

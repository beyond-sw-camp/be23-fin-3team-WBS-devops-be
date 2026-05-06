package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * master-service /location/by-warehouse/{id} Feign 응답 DTO.
 *
 * Master 쪽 WarehouseLocationsResDto 와 필드 구조 동일.
 * 창고 내 활성 로케이션을 Zone/Rack 정보와 함께 한 번에 내려받는다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class WarehouseLocationsResDto {

    private UUID warehouseId;
    private String warehouseName;
    private List<LocationSummaryItem> items;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class LocationSummaryItem {
        private UUID zoneId;
        private String zoneCode;
        private String zoneName;
        private UUID rackId;
        private String rackCode;
        private String rackName;
        private UUID locationId;
        private String locationCode;
        private Integer floorNo;
        private Integer maxCapacity;
    }
}

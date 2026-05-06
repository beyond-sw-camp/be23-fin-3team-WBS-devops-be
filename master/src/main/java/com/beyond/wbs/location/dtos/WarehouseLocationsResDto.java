package com.beyond.wbs.location.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * 창고 내 로케이션 벌크 조회 응답.
 *
 * 단일 Feign 호출로 창고 → 존 → 랙 → 로케이션 전부를 한 번에 내려주기 위한 DTO.
 * Stock 서비스의 "층별 재고 조회" 같은 기능에서 N+1 Feign 호출을 피할 목적.
 *
 * items 는 Location.isActive = true 인 항목만 포함. 정렬은 Service 측에서 보장.
 */
@Getter
@Builder
public class WarehouseLocationsResDto {

    private UUID warehouseId;
    private String warehouseName;
    private List<LocationSummaryItem> items;

    @Getter
    @Builder
    public static class LocationSummaryItem {
        // ── Zone ──
        private UUID zoneId;
        private String zoneCode;
        private String zoneName;

        // ── Rack ──
        private UUID rackId;
        private String rackCode;
        private String rackName;

        // ── Location ──
        private UUID locationId;
        private String locationCode;
        private Integer floorNo;
        private Integer maxCapacity;
    }
}

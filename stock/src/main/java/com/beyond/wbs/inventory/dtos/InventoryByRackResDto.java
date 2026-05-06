package com.beyond.wbs.inventory.dtos;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 창고 내 랙 단위로 그룹핑된 재고·로케이션 요약 응답.
 *
 * 사용처: 창고 레이아웃 모니터링 화면의 "랙별 재고 조회" 탭.
 * 한 랙 앞에 선 작업자가 위·아래 층을 한눈에 보는 흐름에 맞춰
 * 랙을 바깥 그룹으로, 층(floorNo)을 안쪽 순서로 배치.
 *
 *  - 재고가 없는 로케이션도 포함 (inventoryId=null, 수량=0).
 *  - Location.isActive=true 만 대상.
 *  - 랙 정렬: zone.sortOrder → zone.code → rack.code
 *  - 랙 내 로케이션 정렬: floorNo ASC
 */
@Getter
@Builder
public class InventoryByRackResDto {

    private UUID warehouseId;
    private String warehouseName;
    private List<RackGroup> racks;

    @Getter
    @Builder
    public static class RackGroup {
        // ── Rack 헤더 ──
        private UUID rackId;
        private String rackCode;
        private String rackName;
        // ── 소속 Zone 정보 (모니터링 뷰의 그룹 헤더 표시용) ──
        private UUID zoneId;
        private String zoneCode;
        private String zoneName;
        // ── 집계 ──
        private Integer locationCount;        // 이 랙의 활성 로케이션 수 (보통 = levelNo)
        private Integer occupiedCount;         // 재고가 있는 층 수
        private Integer totalAvailableQty;     // 랙 합계 가용재고
        // ── 층별 로케이션 (floorNo ASC) ──
        private List<LocationInventory> locations;
    }

    @Getter
    @Builder
    public static class LocationInventory {
        // ── Location 메타 ──
        private UUID locationId;
        private String locationCode;
        private Integer floorNo;
        private Integer maxCapacity;

        // ── 재고 정보 (한 로케이션 1 SKU 정책) ──
        private UUID inventoryId;              // 재고 없으면 null
        private UUID productId;                 // 없으면 null
        private String productSku;              // 없으면 null
        private String productName;             // 없으면 null
        private Integer availableQty;           // 0 가능
        private Integer reservedQty;
        private Integer pendingQty;
        private Integer defectQty;
        private Integer totalQty;
        private LocalDateTime updatedAt;        // 재고 row 의 updated_at
    }
}

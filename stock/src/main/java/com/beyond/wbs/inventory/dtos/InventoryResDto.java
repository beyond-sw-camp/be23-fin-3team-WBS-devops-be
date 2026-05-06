package com.beyond.wbs.inventory.dtos;

import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.inventory.domain.Inventory;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 조회 응답 DTO
 * - 창고/위치/상품별 현재 재고 상태 표시
 * - 상품명/창고명/위치명은 Feign Client로 Master Service에서 조회
 * - 긴 단일 locationCode 대신 rack/zone/floor 로 분해된 필드도 함께 내려준다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class InventoryResDto {

    private UUID id;
    private UUID productId;
    private UUID warehouseId;
    private UUID locationId;

    // 수량 정보
    private Integer availableQty;   // 가용재고
    private Integer reservedQty;    // 예약재고
    private Integer defectQty;      // 불량재고
    private Integer incomingQty;    // 입고예정재고
    private Integer pendingQty;     // 검수중재고
    private Integer totalQty;       // 전체합계

    private LocalDateTime updatedAt;

    // Master Service Feign 조회 결과
    private String productName;
    private String productSku;      // 진짜 SKU (예: MONITOR-001) — 검색/표시용
    private String warehouseName;
    private String locationCode;    // 긴 단일 코드 (폴백/상세용)

    // 로케이션 분해 필드 — 프론트 표시용
    private String rackCode;
    private String rackName;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private Integer floorNo;
    private Integer maxCapacity;    // 로케이션 최대 수용량 (수용률 시각화용)

    public static InventoryResDto fromEntity(Inventory inventory,
                                             String productName,
                                             String productSku,
                                             String warehouseName,
                                             LocationResDto location) {
        return InventoryResDto.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .warehouseId(inventory.getWarehouseId())
                .locationId(inventory.getLocationId())
                .availableQty(inventory.getAvailableQty())
                .reservedQty(inventory.getReservedQty())
                .defectQty(inventory.getDefectQty())
                .incomingQty(inventory.getIncomingQty())
                .pendingQty(inventory.getPendingQty())
                .totalQty(inventory.getTotalQty())
                .updatedAt(inventory.getUpdatedAt())
                .productName(productName)
                .productSku(productSku)
                .warehouseName(warehouseName)
                .locationCode(location != null ? location.getCode() : null)
                .rackCode(location != null ? location.getRackCode() : null)
                .rackName(location != null ? location.getRackName() : null)
                .zoneId(location != null ? location.getZoneId() : null)
                .zoneCode(location != null ? location.getZoneCode() : null)
                .zoneName(location != null ? location.getZoneName() : null)
                .floorNo(location != null ? location.getFloorNo() : null)
                .maxCapacity(location != null ? location.getMaxCapacity() : null)
                .build();
    }
}

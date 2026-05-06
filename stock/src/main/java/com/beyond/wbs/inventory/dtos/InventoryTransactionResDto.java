package com.beyond.wbs.inventory.dtos;

import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.inventory.domain.InventoryStatus;
import com.beyond.wbs.inventory.domain.InventoryTransaction;
import com.beyond.wbs.inventory.domain.RefType;
import com.beyond.wbs.inventory.domain.TxType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 이력 조회 응답 DTO
 * - 특정 재고의 변동 내역 조회 시 사용
 * - 긴 단일 locationCode 외에 rack/zone/floor 분해 필드도 함께 내려준다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class InventoryTransactionResDto {

    private UUID id;
    private UUID productId;
    private UUID inventoryId;
    private UUID warehouseId;
    private UUID locationId;

    private TxType txType;
    private Integer qty;
    private Integer qtyBefore;
    private Integer qtyAfter;

    private InventoryStatus statusFrom;
    private InventoryStatus statusTo;

    private UUID refId;
    private RefType refType;

    private String note;
    private UUID createdBy;
    private LocalDateTime createdAt;

    // Master Service Feign 조회 결과
    private String productName;
    private String warehouseName;
    private String locationCode;    // 긴 단일 코드 (폴백/상세용)

    // 로케이션 분해 필드 — 프론트 표시용
    private String rackCode;
    private String rackName;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private Integer floorNo;

    // Account Service Feign 조회 결과
    private String createdByName;

    public static InventoryTransactionResDto fromEntity(InventoryTransaction tx,
                                                        String productName,
                                                        String warehouseName,
                                                        LocationResDto location,
                                                        String createdByName) {
        return InventoryTransactionResDto.builder()
                .id(tx.getId())
                .productId(tx.getProductId())
                .inventoryId(tx.getInventoryId())
                .warehouseId(tx.getWarehouseId())
                .locationId(tx.getLocationId())
                .txType(tx.getTxType())
                .qty(tx.getQty())
                .qtyBefore(tx.getQtyBefore())
                .qtyAfter(tx.getQtyAfter())
                .statusFrom(tx.getStatusFrom())
                .statusTo(tx.getStatusTo())
                .refId(tx.getRefId())
                .refType(tx.getRefType())
                .note(tx.getNote())
                .createdBy(tx.getCreatedBy())
                .createdAt(tx.getCreatedAt())
                .productName(productName)
                .warehouseName(warehouseName)
                .locationCode(location != null ? location.getCode() : null)
                .rackCode(location != null ? location.getRackCode() : null)
                .rackName(location != null ? location.getRackName() : null)
                .zoneId(location != null ? location.getZoneId() : null)
                .zoneCode(location != null ? location.getZoneCode() : null)
                .zoneName(location != null ? location.getZoneName() : null)
                .floorNo(location != null ? location.getFloorNo() : null)
                .createdByName(createdByName)
                .build();
    }
}

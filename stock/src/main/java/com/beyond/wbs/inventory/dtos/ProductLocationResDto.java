package com.beyond.wbs.inventory.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 상품 재고 위치 응답 — 레이아웃 뷰어에서 해당 상품이 보관된 위치를 하이라이트하기 위한 DTO.
 * zone/rack/location 정보를 한 번에 반환하여 프론트에서 역매핑 없이 바로 사용.
 */
@Getter
@Builder
public class ProductLocationResDto {
    private UUID warehouseId;
    private String warehouseName;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;
    private UUID rackId;
    private String rackCode;
    private String rackName;
    private UUID locationId;
    private String locationCode;
    private Integer floorNo;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer pendingQty;
    private Integer defectQty;
    private Integer totalQty;
}

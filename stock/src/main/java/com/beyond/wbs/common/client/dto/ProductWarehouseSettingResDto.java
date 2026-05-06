package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * (상품 × 창고) 안전재고 설정 — master 의 ProductWarehouseSettingResDto 와 동일 스키마.
 *
 * stock 모듈에서 Feign 으로 받아 알림 판정용으로 사용.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProductWarehouseSettingResDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private String sku;
    private UUID warehouseId;
    private String warehouseName;
    private Integer minStockQty;
}

package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductWarehouseSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * (상품 × 창고) 안전재고 설정 응답.
 *
 * 단건 조회 / 상품별 목록 / Feign 응답 모두 동일 구조.
 * FE 표시용으로 상품/창고 이름도 함께 채움 (service 에서 join 해서 채워줌).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class ProductWarehouseSettingResDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private String sku;
    private UUID warehouseId;
    private String warehouseName;
    private Integer minStockQty;

    /** 이름 채우지 않는 단건 변환 (Feign 응답처럼 ID 만 필요할 때) */
    public static ProductWarehouseSettingResDto fromEntity(ProductWarehouseSetting e) {
        return ProductWarehouseSettingResDto.builder()
                .id(e.getId())
                .productId(e.getProductId())
                .warehouseId(e.getWarehouseId())
                .minStockQty(e.getMinStockQty())
                .build();
    }

    /** 이름까지 채워서 반환 — FE 표시용 */
    public static ProductWarehouseSettingResDto fromEntity(ProductWarehouseSetting e,
                                                             String productName, String sku,
                                                             String warehouseName) {
        return ProductWarehouseSettingResDto.builder()
                .id(e.getId())
                .productId(e.getProductId())
                .productName(productName)
                .sku(sku)
                .warehouseId(e.getWarehouseId())
                .warehouseName(warehouseName)
                .minStockQty(e.getMinStockQty())
                .build();
    }
}

package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 안전재고 upsert 요청 (있으면 수정, 없으면 생성).
 * (productId, warehouseId) 는 path 로 받고 본문엔 minStockQty 만.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductWarehouseSettingUpsertReqDto {

    @NotNull(message = "안전재고(minStockQty)는 필수입니다.")
    @PositiveOrZero(message = "안전재고(minStockQty)는 0 이상이어야 합니다.")
    private Integer minStockQty;
}

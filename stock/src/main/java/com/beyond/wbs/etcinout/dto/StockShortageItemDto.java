package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 가용재고 부족 응답에 담길 품목별 정보.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockShortageItemDto {

    private UUID itemId;
    private UUID productId;
    private UUID warehouseId;
    private UUID locationId;
    private Integer requested;     // 요청 수량
    private Integer available;     // 가용재고
    private Integer shortage;      // 부족 수량
}

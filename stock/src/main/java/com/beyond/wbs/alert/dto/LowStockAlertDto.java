package com.beyond.wbs.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

/**
 * 재고 부족 알림 DTO
 * - 가용재고가 상품별 안전재고(minStockQty) 이하인 품목을 표시
 *
 * type 값 (WebSocket push 시 채워짐, REST 응답 시엔 null):
 *   - "low_stock_added"    : 새로 부족 상태가 된 (product × 창고)
 *   - "low_stock_resolved" : 부족 해소된 (product × 창고)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LowStockAlertDto {
    private String type;
    private UUID productId;
    private String productName;
    private String sku;
    private UUID warehouseId;
    private String warehouseName;
    private Integer availableQty;   // 현재 가용재고
    private Integer minStockQty;    // 상품별 안전재고 기준
}

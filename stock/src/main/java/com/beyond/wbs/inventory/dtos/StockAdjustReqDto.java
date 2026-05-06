package com.beyond.wbs.inventory.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * 재고 수동 조정 요청 DTO
 * - 재고실사 결과 반영 또는 관리자 수동 보정 시 사용
 * - diffQty: 양수(증가) / 음수(감소) 모두 가능
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class StockAdjustReqDto {

    @NotNull(message = "상품 ID는 필수입니다.")
    private UUID productId;

    @NotNull(message = "창고 ID는 필수입니다.")
    private UUID warehouseId;

    @NotNull(message = "위치 ID는 필수입니다.")
    private UUID locationId;

    @NotNull(message = "조정 수량은 필수입니다. (양수/음수)")
    private Integer diffQty;

    // 조정 사유 (재고실사 / 파손 / 유실 등)
    private String note;
}

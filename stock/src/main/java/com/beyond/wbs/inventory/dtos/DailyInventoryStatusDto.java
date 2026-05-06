package com.beyond.wbs.inventory.dtos;

import lombok.*;

import java.time.LocalDate;

/**
 * 날짜별 재고 현황 응답 (1 row = 하루)
 *
 * 프론트에서 날짜별 재고 추이 그래프를 그릴 때 사용.
 * 스냅샷 기준점 + InventoryTransaction 델타 합산으로 계산된 값.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DailyInventoryStatusDto {
    // 날짜
    private LocalDate date;
    // 해당 날짜 말 가용재고
    private int availableQty;
    // 해당 날짜 입고 수량 (available 증가분 합계) , 그 날 하루의 입고만 (다른 날 포함 안 함)
    private int inboundQty;
    // 해당 날짜 출고 수량 (available 감소분 합계, 절대값), 그 날 하루의 출고만 (다른 날 포함 안 함)
    private int outboundQty;
}

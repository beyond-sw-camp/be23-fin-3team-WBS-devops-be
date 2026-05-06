package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 재고 회전율 응답 DTO
 *
 * 재고 회전율 = 출고량 / 평균재고
 *  - 평균재고 = (월초재고 + 월말재고) / 2
 *  - 높을수록 재고가 빠르게 소진 → 효율적 운영
 *  - 낮을수록 재고가 오래 쌓여있음 → 과잉재고 위험
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class InventoryTurnoverDto {
    private LocalDate month;            // 기준월
    private Integer outboundQty;        // 월 출고량
    private Double averageInventory;    // 평균재고 = (openQty + closeQty) / 2
    private Double turnoverRate;        // 회전율 = outboundQty / averageInventory
}

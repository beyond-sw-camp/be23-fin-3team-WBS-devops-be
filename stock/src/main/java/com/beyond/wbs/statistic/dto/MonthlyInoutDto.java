package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 월별 입출고 추이 응답 DTO
 * - 월 단위로 입고/출고 수량 합계를 표시
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class MonthlyInoutDto {
    private LocalDate month;       // 기준월 (yyyy-MM-01)
    private Integer inboundQty;    // 월 입고 합계
    private Integer outboundQty;   // 월 출고 합계
}

package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 월별 입출고 추이 응답 DTO
 * - 월 단위로 재고 흐름을 표시
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class MonthlyInoutDto {
    private LocalDate month;       // 기준월 (yyyy-MM-01)
    private Integer inboundQty;    // 월 입고 합계
    private Integer outboundQty;   // 월 출고 합계
    private Integer normalInboundQty;
    private Integer returnInboundQty;
    private Integer normalOutboundQty;
    private Integer returnOutboundQty;
    private Integer transferQty;
    private Integer etcInboundQty;
    private Integer etcOutboundQty;
    private Integer adjustmentInboundQty;
    private Integer adjustmentOutboundQty;
}

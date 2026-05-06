package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 일별 입출고 추이 응답 DTO
 * - 일 단위로 입고/출고 수량 합계를 표시
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DailyInoutDto {
    private LocalDate date;
    private Integer inboundQty;
    private Integer outboundQty;
}

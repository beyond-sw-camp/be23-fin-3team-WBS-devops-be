package com.beyond.wbs.statistic.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 일별 입출고 추이 응답 DTO
 * - 일 단위로 재고 흐름을 표시
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DailyInoutDto {
    private LocalDate date;
    private Integer inboundQty;
    private Integer outboundQty;
    private Integer normalInboundQty;
    private Integer returnInboundQty;
    private Integer normalOutboundQty;
    private Integer returnOutboundQty;
    private Integer transferQty;
    private Integer etcInboundQty;
    private Integer etcOutboundQty;
    private Integer adjustmentInboundQty;
    private Integer adjustmentOutboundQty;
    private List<OrderLinkDto> inboundOrders;
    private List<OrderLinkDto> outboundOrders;
    private List<OrderLinkDto> transferOrders;
    private List<OrderLinkDto> etcOrders;
    private List<String> skuList;
}

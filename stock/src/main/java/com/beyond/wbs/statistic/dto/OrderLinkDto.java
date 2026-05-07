package com.beyond.wbs.statistic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class OrderLinkDto {
    private UUID id;
    private String orderNo;
    private String type;
}

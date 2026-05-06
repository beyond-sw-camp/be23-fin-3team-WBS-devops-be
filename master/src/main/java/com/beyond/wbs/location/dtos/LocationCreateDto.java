package com.beyond.wbs.location.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class LocationCreateDto {

    @NotNull
    private UUID rackId;

    @NotNull
    private Integer floorNo;

    private String barcode;

    // 해당 층의 최대 보관 수량 (선택). 미입력 시 무제한/미정으로 저장된다.
    private Integer maxCapacity;
}
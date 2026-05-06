package com.beyond.wbs.rack.dtos;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 랙 부분 수정 DTO. null 필드는 변경하지 않음.
 * code/zone 은 수정 불가.
 */
@Getter
@NoArgsConstructor
public class RackUpdateDto {

    @Size(max = 100)
    private String name;

    private UUID supplierId;

    private Integer levelNo;
    private String levelGuideJson;
    @Positive
    private Integer maxCapacity;

    // 물리 치수 (정보성, nullable)
    private Integer widthMm;
    private Integer depthMm;
    private Integer heightMm;

}

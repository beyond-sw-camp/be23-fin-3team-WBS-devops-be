package com.beyond.wbs.inbounds.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateInboundReqDto {
    /** 입고처 — null 이면 자사 입고 (owner_type=OWN 상품 전용) */
    private UUID supplierId;

    @NotNull(message = "입고 창고를 선택해 주세요.")
    private UUID warehouseId;

    @NotNull(message = "입고 예정일을 선택해 주세요.")
    private String expectedDate;    // 입고 예정일 (yyyy-MM-dd)

    private String source;          // 출처 (수동 등)

    @NotNull(message = "입고 품목을 1건 이상 입력해 주세요.")
    private List<CreateInboundItemDto> items;
}

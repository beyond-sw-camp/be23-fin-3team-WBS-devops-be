package com.beyond.wbs.inbounds.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReceiveRowDto {
    @NotNull
    private UUID itemId;        // 입고지시서 품목 ID
    @NotNull
    private Integer qty;        // 검수 수량 (정상)
    private Integer defective;  // 불량 수량
    private String lotNo;       // LOT 번호
}

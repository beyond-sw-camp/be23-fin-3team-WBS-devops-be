package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class DispatchCreateReqDto {
    // 출고 지시서 ID
    @NotNull(message = "출고 지시서를 선택해주세요.")
    private UUID outboundOrderId;
}

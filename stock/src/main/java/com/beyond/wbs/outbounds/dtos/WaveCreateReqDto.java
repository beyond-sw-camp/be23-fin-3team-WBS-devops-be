package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter @ToString
@Builder
public class WaveCreateReqDto {

    @NotEmpty(message = "출고 지시서를 선택해주세요.")
    private List<UUID> outboundOrderIds;

    // 상품별 담당자 수동 지정. 비어 있거나 일부 상품이 누락되면 stock-service가 자동 배정한다.
    private Map<UUID, UUID> assignments;

}

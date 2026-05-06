package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 출고지시서 생성 결과.
 * 단일 창고면 OB 1개, 분할이면 N개 ID 가 순서대로 들어 있다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOutboundResDto {
    private List<UUID> outboundOrderIds;
    /** 부족분이 있어 일부 수량은 OB 에 담지 못했다면 그 합계 (해당 SO 라인이 부분 처리 상태로 남음) */
    private int unallocatedQty;
}

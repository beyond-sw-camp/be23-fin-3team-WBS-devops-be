package com.beyond.wbs.inbounds.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 입고지시서 생성 전 — 발주서별 추천 창고 조회 요청.
 *
 * 같은 출고예정일에 묶인 PO 들을 한 번에 보내면, 각 PO 별로 추천 창고 + 후보 목록을 돌려준다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RecommendWarehousesReqDto {
    @NotEmpty(message = "purchaseOrderIds 는 비어있을 수 없습니다.")
    private List<UUID> purchaseOrderIds;
}

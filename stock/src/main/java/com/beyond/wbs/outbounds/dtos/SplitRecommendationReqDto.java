package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 분할 출고 자동 추천 요청.
 * 한 출고처(store) 단위로 호출하며, 시스템이 가용재고 많은 창고부터
 * 그리디하게 채워 분배안을 돌려준다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitRecommendationReqDto {
    /** 같은 출고처의 SO ID 목록 (같은 ship_date) */
    @NotEmpty(message = "수주서 ID 목록은 비어 있을 수 없습니다.")
    private List<UUID> salesOrderIds;

    @NotNull(message = "출고처 ID 는 필수입니다.")
    private UUID storeId;

    /** 자기 자신 OB 제외 (재미리보기 시) — 없으면 null */
    private UUID excludeOutboundOrderId;
}

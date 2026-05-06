package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 출고지시서 멀티필터 검색 요청 — POST /outbound/orders/search 의 body.
 *
 * 흐름:
 *   ① 프론트가 master /product/search-advanced 로 productIds 수집
 *   ② 그 productIds 와 기존 status/warehouseId/storeId 를 본 DTO 로 전달
 *   ③ 백엔드는 EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 출고지시서"만 반환
 *
 * 모든 필드 옵셔널.
 */
@Getter
@Setter
@NoArgsConstructor
public class OutboundOrderSearchReqDto {
    private OutboundOrderStatus status;
    private UUID warehouseId;
    private UUID storeId;
    private List<UUID> productIds;
    private String originType;          // 출처 유형 정확매칭 ("return" / "sales_order" / "manual")
    private String excludeOriginType;   // 출처 유형 제외 (예: "return" 이면 반품 제외 = 일반 출고만)
}

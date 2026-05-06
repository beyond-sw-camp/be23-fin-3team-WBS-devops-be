package com.beyond.wbs.inbounds.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 입고지시서 멀티필터 검색 요청 — POST /inbound/inbound-orders/search 의 body.
 *
 * 흐름:
 *   ① 프론트가 master 의 /product/search-advanced 로 멀티필터 검색 → productIds 수집
 *   ② 그 productIds 와 기존 status 등 조건을 함께 본 DTO 의 body 로 전달
 *   ③ 백엔드는 productIds 매칭 라인이 1개 이상 있는 입고지시서만 반환
 *
 * 모든 필드 옵셔널.
 * - status / productIds 둘 다 null/빈 리스트면 전체 입고지시서 반환 (= 기존 GET 와 동일).
 * - productIds 만 있으면 상품 매칭 only.
 * - 둘 다 있으면 status AND productIds.
 *
 * Pageable 은 Controller 에서 별도 받음 (page/size/sort).
 */
@Getter
@Setter
@NoArgsConstructor
public class InboundOrderSearchReqDto {
    private List<String> status;            // 기존 GET 의 status query param 대응
    private List<UUID> productIds;          // 상품 매칭 (EXISTS 서브쿼리)
    private String originType;              // 출처 유형 정확매칭 ("return" / "purchase_order" / "manual")
    private String excludeOriginType;       // 출처 유형 제외 (예: "return" 이면 반품 제외 = 일반 입고만)
}

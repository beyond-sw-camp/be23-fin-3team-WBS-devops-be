package com.beyond.wbs.inbounds.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 상품 ID 리스트 요청 — 지시서 안에서 상품 라인 필터링 시 사용.
 *
 * 흐름:
 *  ① 프론트가 master 의 /product/search-advanced 로 멀티필터 검색 → productIds 수집
 *  ② 그 productIds 를 본 DTO 의 body 로 전달해 지시서 라인을 좁힘
 *
 * productIds 가 null/빈 리스트면 전체 라인 반환 (필터 미적용).
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductIdsReqDto {
    private List<UUID> productIds;
}

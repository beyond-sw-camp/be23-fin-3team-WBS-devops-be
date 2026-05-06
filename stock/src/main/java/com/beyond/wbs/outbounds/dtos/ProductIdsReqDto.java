package com.beyond.wbs.outbounds.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 상품 ID 리스트 요청 — 출고지시서/피킹리스트 안에서 상품 라인 필터링 시 사용.
 *
 * 흐름:
 *  ① 프론트가 master 의 /product/search-advanced 로 멀티필터 검색 → productIds 수집
 *  ② 그 productIds 를 본 DTO 의 body 로 전달해 라인 좁힘
 *
 * productIds 가 null/빈 리스트면 전체 라인 반환 (필터 미적용).
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductIdsReqDto {
    private List<UUID> productIds;
}

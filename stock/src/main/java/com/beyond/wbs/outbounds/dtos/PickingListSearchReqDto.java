package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.PickingListStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 피킹리스트 멀티필터 검색 요청 — POST /pickingList/search 의 body.
 *
 * 흐름:
 *   ① 프론트가 master /product/search-advanced 로 productIds 수집
 *   ② 그 productIds 와 기존 status/warehouseId 를 본 DTO 로 전달
 *   ③ 백엔드는 EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 피킹리스트"만 반환
 *
 * 모든 필드 옵셔널.
 */
@Getter
@Setter
@NoArgsConstructor
public class PickingListSearchReqDto {
    private PickingListStatus status;
    private UUID warehouseId;
    private List<UUID> productIds;
}

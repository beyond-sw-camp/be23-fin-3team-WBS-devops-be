package com.beyond.wbs.transfer.dto;

import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 이동지시서 멀티필터 검색 요청 — POST /transfer/search 의 body.
 *
 * 흐름:
 *   ① 프론트가 master /product/search-advanced 로 productIds 수집
 *   ② 그 productIds 와 기존 status/fromWarehouseId/toWarehouseId 를 본 DTO 로 전달
 *   ③ 백엔드는 EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 이동지시서"만 반환
 *
 * 모든 필드 옵셔널.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferOrderSearchReqDto {
    private TransferOrderStatus status;
    private UUID fromWarehouseId;
    private UUID toWarehouseId;
    private List<UUID> productIds;
}

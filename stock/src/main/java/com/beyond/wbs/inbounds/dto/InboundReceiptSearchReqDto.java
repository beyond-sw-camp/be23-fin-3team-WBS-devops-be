package com.beyond.wbs.inbounds.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 입고전표 멀티필터 검색 요청 — POST /inbound/receipts/search 의 body.
 *
 * 흐름:
 *   ① 프론트가 master /product/search-advanced 로 productIds 수집
 *   ② 그 productIds 와 기존 dateFrom/dateTo/warehouseId 등을 본 DTO 로 전달
 *   ③ 백엔드는 EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 입고전표"만 반환
 *
 * 모든 필드 옵셔널.
 */
@Getter
@Setter
@NoArgsConstructor
public class InboundReceiptSearchReqDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private UUID warehouseId;
    private String originType;
    private String receiptNoKeyword;
    private String orderNoKeyword;
    private List<UUID> productIds;
}

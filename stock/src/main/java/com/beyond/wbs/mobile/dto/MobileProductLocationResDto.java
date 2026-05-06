package com.beyond.wbs.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모바일 — 상품명 검색 결과에서 "이 상품이 어느 랙·로케이션에 있나"를 담는 슬롯.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileProductLocationResDto {
    private String rackCode;
    private String locationCode;
    private Integer availableQty;
    private Integer defectQty;
}

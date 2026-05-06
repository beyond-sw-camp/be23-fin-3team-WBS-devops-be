package com.beyond.wbs.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 모바일 — 상품명 검색 결과 1건.
 * 상품 1개 + 그 상품이 보관된 위치 리스트.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileInventorySearchResDto {
    private String productName;
    private String sku;
    private List<MobileProductLocationResDto> locations;
}

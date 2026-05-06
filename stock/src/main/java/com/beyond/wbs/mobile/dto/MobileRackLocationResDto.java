package com.beyond.wbs.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모바일 — 랙 QR 조회 응답의 로케이션 한 칸.
 * 한 Location = 1 SKU 정책이므로 상품 필드를 직접 담는다.
 * 빈 자리면 productName/sku 가 null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRackLocationResDto {
    private String locationCode;
    private String productName;     // null = 빈 자리
    private String sku;
    private Integer availableQty;
    private Integer defectQty;
}

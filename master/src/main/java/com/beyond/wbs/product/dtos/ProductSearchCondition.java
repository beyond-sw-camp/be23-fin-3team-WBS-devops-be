package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.OwnerType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 상품 멀티필터 검색 조건.
 * 모든 필드 옵셔널 — 제공된 항목만 AND 로 결합된다.
 *
 * 옵션 매칭 시멘틱: optionValueIds 중 하나라도 매칭되는 상품을 반환 (OR).
 * 카테고리는 단일 ID 정확매칭 (트리 자식 매칭은 향후 확장).
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductSearchCondition {

    private String keyword;       // name | sku | barcode 통합 LIKE
    private String sku;           // SKU LIKE
    private String barcode;       // barcode 정확매칭 (스캐너)
    private String name;          // 상품명 LIKE
    private String brand;         // ProductGroup.brand LIKE
    private UUID categoryId;      // ProductGroup.category — 자손 카테고리까지 자동 매칭 (Service 에서 펼침)
    private UUID supplierId;      // 매입처 정확매칭
    private Boolean isActive;     // 상품 상태
    private OwnerType ownerType;  // 소속 (OWN / PARTNER)
    private UUID productGroupId;  // 상품 그룹 정확매칭
    private BigDecimal priceMin;  // 단가 하한 (>=)
    private BigDecimal priceMax;  // 단가 상한 (<=)
    private List<UUID> optionValueIds;  // 옵션 값 OR 매칭
}

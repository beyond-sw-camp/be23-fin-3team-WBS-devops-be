package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.OwnerType;
import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductGroup;
import com.beyond.wbs.product.domain.ProductOption;
import com.beyond.wbs.product.dtos.ProductSearchCondition;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Product 동적 검색 Specification 빌더.
 *
 * 모든 단일 메서드는 입력이 비어있으면 null 반환 →
 * Specification.where(...).and(null) 은 무시되므로 옵셔널 조립이 자연스럽다.
 *
 * 진입점: forCondition(clientId, condition) — clientId 격리는 강제.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {}

    /**
     * 진입점.
     *
     * categoryIdsExpanded: 루트 + 모든 자손 ID 펼친 리스트. null 이면 cond.getCategoryId() 단일 매칭.
     * optionGroups: 옵션 타입ID → 옵션 값ID 리스트. 같은 타입 내 OR + 타입 간 AND 의 패싯 시멘틱.
     *               (Service 가 valueIds 를 typeId 로 그룹화해 전달)
     */
    public static Specification<Product> forCondition(UUID clientId,
                                                      ProductSearchCondition cond,
                                                      List<UUID> categoryIdsExpanded,
                                                      Map<UUID, List<UUID>> optionGroups) {
        Specification<Product> spec = Specification.where(hasClientId(clientId));
        if (cond == null) return spec;

        spec = spec.and(hasKeyword(cond.getKeyword()));
        spec = spec.and(hasSkuLike(cond.getSku()));
        spec = spec.and(hasBarcodeEquals(cond.getBarcode()));
        spec = spec.and(hasNameLike(cond.getName()));
        spec = spec.and(hasBrandLike(cond.getBrand()));
        if (categoryIdsExpanded != null) {
            spec = spec.and(hasCategoryIdIn(categoryIdsExpanded));
        } else {
            spec = spec.and(hasCategoryId(cond.getCategoryId()));
        }
        spec = spec.and(hasSupplierId(cond.getSupplierId()));
        spec = spec.and(isActiveEquals(cond.getIsActive()));
        spec = spec.and(hasOwnerType(cond.getOwnerType()));
        spec = spec.and(hasProductGroupId(cond.getProductGroupId()));
        spec = spec.and(hasPriceGte(cond.getPriceMin()));
        spec = spec.and(hasPriceLte(cond.getPriceMax()));
        spec = spec.and(hasOptionsByGroup(optionGroups));
        return spec;
    }

    public static Specification<Product> hasClientId(UUID clientId) {
        return (root, q, cb) -> cb.equal(root.get("clientId"), clientId);
    }

    public static Specification<Product> hasKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String pattern = "%" + keyword + "%";
        return (root, q, cb) -> cb.or(
                cb.like(root.get("name"), pattern),
                cb.like(root.get("sku"), pattern),
                cb.like(root.get("barcode"), pattern)
        );
    }

    public static Specification<Product> hasSkuLike(String sku) {
        if (!StringUtils.hasText(sku)) return null;
        return (root, q, cb) -> cb.like(root.get("sku"), "%" + sku + "%");
    }

    public static Specification<Product> hasBarcodeEquals(String barcode) {
        if (!StringUtils.hasText(barcode)) return null;
        return (root, q, cb) -> cb.equal(root.get("barcode"), barcode);
    }

    public static Specification<Product> hasNameLike(String name) {
        if (!StringUtils.hasText(name)) return null;
        return (root, q, cb) -> cb.like(root.get("name"), "%" + name + "%");
    }

    public static Specification<Product> hasBrandLike(String brand) {
        if (!StringUtils.hasText(brand)) return null;
        return (root, q, cb) -> {
            Join<Product, ProductGroup> group = root.join("productGroup", JoinType.LEFT);
            return cb.like(group.get("brand"), "%" + brand + "%");
        };
    }

    public static Specification<Product> hasCategoryId(UUID categoryId) {
        if (categoryId == null) return null;
        return (root, q, cb) -> {
            Join<Product, ProductGroup> group = root.join("productGroup", JoinType.LEFT);
            return cb.equal(group.get("category").get("id"), categoryId);
        };
    }

    /**
     * 자손 카테고리까지 펼쳐진 ID 리스트로 IN 매칭.
     * Service 에서 BFS 로 자손 수집 후 호출.
     */
    public static Specification<Product> hasCategoryIdIn(List<UUID> categoryIds) {
        if (CollectionUtils.isEmpty(categoryIds)) return null;
        return (root, q, cb) -> {
            Join<Product, ProductGroup> group = root.join("productGroup", JoinType.LEFT);
            return group.get("category").get("id").in(categoryIds);
        };
    }

    public static Specification<Product> hasOwnerType(OwnerType ownerType) {
        if (ownerType == null) return null;
        return (root, q, cb) -> cb.equal(root.get("ownerType"), ownerType);
    }

    public static Specification<Product> hasProductGroupId(UUID productGroupId) {
        if (productGroupId == null) return null;
        return (root, q, cb) -> {
            Join<Product, ProductGroup> group = root.join("productGroup", JoinType.LEFT);
            return cb.equal(group.get("id"), productGroupId);
        };
    }

    public static Specification<Product> hasPriceGte(BigDecimal min) {
        if (min == null) return null;
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("standardPrice"), min);
    }

    public static Specification<Product> hasPriceLte(BigDecimal max) {
        if (max == null) return null;
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("standardPrice"), max);
    }

    public static Specification<Product> hasSupplierId(UUID supplierId) {
        if (supplierId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("supplierId"), supplierId);
    }

    public static Specification<Product> isActiveEquals(Boolean isActive) {
        if (isActive == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isActive"), isActive);
    }

    /**
     * 옵션 패싯 매칭 — 같은 타입 내 OR + 타입 간 AND.
     *
     * 입력: optionTypeId → List<optionValueId>
     * 의미: 각 옵션 타입마다 "이 product 의 옵션 중 하나라도 그 타입의 valueIds 에 포함" 이 모두 true.
     *
     * 예) {COLOR: [BLACK, WHITE], INTERFACE: [USB-C]}
     *  → (color IN [BLACK, WHITE]) AND (interface IN [USB-C])
     *
     * 구현: 그룹마다 서브쿼리 1개 (root.id IN subquery), 결과를 cb.and 로 결합.
     */
    public static Specification<Product> hasOptionsByGroup(Map<UUID, List<UUID>> optionGroups) {
        if (optionGroups == null || optionGroups.isEmpty()) return null;
        return (root, q, cb) -> {
            Predicate combined = cb.conjunction();
            for (List<UUID> valueIds : optionGroups.values()) {
                if (valueIds == null || valueIds.isEmpty()) continue;
                Subquery<UUID> sq = q.subquery(UUID.class);
                Root<ProductOption> po = sq.from(ProductOption.class);
                sq.select(po.get("product").get("id"));
                sq.where(po.get("optionValue").get("id").in(valueIds));
                combined = cb.and(combined, root.get("id").in(sq));
            }
            return combined;
        };
    }
}

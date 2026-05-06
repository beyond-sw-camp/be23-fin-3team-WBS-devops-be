package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    // 전체 조회 (비활성 포함) — 관리자 화면에서 필요할 수 있음
    List<ProductCategory> findByClientIdAndParentIsNull(UUID clientId);
    List<ProductCategory> findByParentId(UUID parentId);

    // 활성 항목만 — 일반 조회용
    List<ProductCategory> findByClientIdAndParentIsNullAndIsActiveTrue(UUID clientId);
    List<ProductCategory> findByParentIdAndIsActiveTrue(UUID parentId);

    // RESTRICT 체크: 활성 하위 카테고리 존재 여부
    boolean existsByParentIdAndIsActiveTrue(UUID parentId);
}

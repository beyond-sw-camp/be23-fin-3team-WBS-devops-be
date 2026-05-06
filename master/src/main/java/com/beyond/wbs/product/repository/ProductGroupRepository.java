package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID> {

    // 전체 조회 (비활성 포함)
    Page<ProductGroup> findByClientId(UUID clientId, Pageable pageable);
    Page<ProductGroup> findByClientIdAndCategoryId(UUID clientId, UUID categoryId, Pageable pageable);

    // 활성 항목만 — 일반 목록용
    Page<ProductGroup> findByClientIdAndIsActiveTrue(UUID clientId, Pageable pageable);
    Page<ProductGroup> findByClientIdAndCategoryIdAndIsActiveTrue(UUID clientId, UUID categoryId, Pageable pageable);

    // 자동 제안용: 동일 카테고리의 활성 그룹 (네이밍 참고 + 순번 계산)
    List<ProductGroup> findByClientIdAndCategory_IdAndIsActiveTrue(UUID clientId, UUID categoryId);

    // RESTRICT 체크: 특정 카테고리를 참조하는 활성 그룹 존재 여부 (Category deactivate 시 사용)
    boolean existsByCategoryIdAndIsActiveTrue(UUID categoryId);

    // brand distinct 목록 — 검색 UI 드롭다운용. 빈/null 제외, 알파벳/한글 정렬.
    @Query("SELECT DISTINCT g.brand FROM ProductGroup g " +
           "WHERE g.clientId = :clientId AND g.brand IS NOT NULL AND g.brand <> '' " +
           "ORDER BY g.brand")
    List<String> findDistinctBrandsByClientId(@Param("clientId") UUID clientId);
}

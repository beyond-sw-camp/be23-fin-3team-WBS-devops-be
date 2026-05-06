package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Page<Product> findByClientId(UUID clientId, Pageable pageable);

    /** 페이징 없는 client 단위 전체 조회 — 재고 알림 baseline 등 내부 서비스에서 호출 */
    List<Product> findAllByClientId(UUID clientId);

    Optional<Product> findByClientIdAndSku(UUID clientId, String sku);

    /**
     * 바코드 정확매칭 조회 — 스캐너 입력으로 단일 상품을 즉시 찾을 때 사용.
     * client_id+barcode UNIQUE 제약이 있어 결과는 0~1건.
     */
    Optional<Product> findByClientIdAndBarcode(UUID clientId, String barcode);

    // RESTRICT 체크: 특정 그룹을 참조하는 활성 상품 존재 여부 (ProductGroup deactivate 시 사용)
    boolean existsByProductGroup_IdAndIsActiveTrue(UUID productGroupId);

    // SKU 제안용: {협력사}-{카테고리}- 접두어로 시작하는 상품 개수
    long countByClientIdAndSkuStartingWith(UUID clientId, String skuPrefix);

    /**
     * 상품명 / SKU / 바코드 키워드 검색.
     * keyword 가 null 또는 빈 문자열이면 전체 조회와 동일하게 동작.
     */
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId " +
            "AND (:keyword IS NULL OR :keyword = '' " +
            "     OR p.name LIKE CONCAT('%', :keyword, '%') " +
            "     OR p.sku LIKE CONCAT('%', :keyword, '%') " +
            "     OR p.barcode LIKE CONCAT('%', :keyword, '%'))")
    Page<Product> searchByKeyword(@Param("clientId") UUID clientId,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);
}

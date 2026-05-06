package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductWarehouseSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductWarehouseSettingRepository extends JpaRepository<ProductWarehouseSetting, UUID> {

    /** 한 (상품, 창고) 의 설정 조회 — low-stock 알림 판정용 */
    Optional<ProductWarehouseSetting> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    /** 한 상품의 모든 창고 설정 — 상품 상세에서 안전재고 표 보여줄 때 */
    List<ProductWarehouseSetting> findByProductId(UUID productId);

    /** 한 창고의 모든 상품 설정 — 창고 단위 안전재고 일람 */
    List<ProductWarehouseSetting> findByWarehouseId(UUID warehouseId);

    /**
     * 한 client 의 모든 안전재고 설정 — low-stock 알림 일괄 조회용.
     * Product 와 join 해서 client 검증 (Setting 자체엔 clientId 없음).
     */
    @Query("SELECT s FROM ProductWarehouseSetting s " +
            "JOIN Product p ON p.id = s.productId " +
            "WHERE p.clientId = :clientId")
    List<ProductWarehouseSetting> findAllByClientId(@Param("clientId") UUID clientId);
}

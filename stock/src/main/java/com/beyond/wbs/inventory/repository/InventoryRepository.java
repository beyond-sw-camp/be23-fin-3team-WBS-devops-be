package com.beyond.wbs.inventory.repository;

import com.beyond.wbs.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * (상품 + 창고 + 위치) 조합으로 재고 row 조회.
     * locationId 가 null 이면 IS NULL 로 매칭한다 (입고예정/검수중 단계의 임시 행).
     */
    @Query("SELECT i FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId " +
            "AND ((:locationId IS NULL AND i.locationId IS NULL) " +
            "     OR i.locationId = :locationId)")
    Optional<Inventory> findByProductIdAndWarehouseIdAndLocationId(
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId,
            @Param("locationId") UUID locationId);

    /**
     * 재고 변동 시 비관적 락(PESSIMISTIC_WRITE)으로 조회.
     * locationId 가 null 인 경우(적치 위치 미정 임시 행)도 IS NULL 로 매칭.
     *
     * 동시에 같은 재고 row를 건드리는 요청이 있을 때
     * 중복 차감/증가 방지를 위해 필요.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId " +
            "AND ((:locationId IS NULL AND i.locationId IS NULL) " +
            "     OR i.locationId = :locationId)")
    Optional<Inventory> findForUpdate(@Param("productId") UUID productId,
                                      @Param("warehouseId") UUID warehouseId,
                                      @Param("locationId") UUID locationId);

    /**
     * 창고 단위 재고 조회
     * - 창고별 재고 현황 화면
     */
    List<Inventory> findByWarehouseId(UUID warehouseId);

    /**
     * 회사(client) 단위 전체 재고 조회
     * - 재고 현황 페이지에서 "우리 회사 모든 창고의 재고"를 한 번에 조회하는 용도
     */
    List<Inventory> findByClientId(UUID clientId);

    /**
     * 상품 단위 재고 조회 (모든 창고/위치 합산 전 raw 조회)
     * - 같은 상품이 여러 창고/위치에 분산되어 있을 때 전체 조회
     */
    List<Inventory> findByProductId(UUID productId);

    /**
     * 창고 + 위치 조합으로 재고 조회
     * - 적치 위치 추천 시 "이 위치에 다른 상품이 있는지" 확인용
     */
    List<Inventory> findByWarehouseIdAndLocationId(UUID warehouseId, UUID locationId);

    /**
     * 피킹 위치 선정용: 해당 상품의 가용재고가 남아있는 위치를 가용수량 내림차순으로 조회.
     * - locationId 가 null 인 staging 행은 자동으로 제외 (createWave 시 실제 로케이션만 필요)
     * - 결과는 "실제로 꺼낼 수 있는 위치 후보" 목록
     */
    @Query("SELECT i FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId " +
            "AND i.locationId IS NOT NULL " +
            "AND i.availableQty > 0 " +
            "ORDER BY i.availableQty DESC")
    List<Inventory> findAvailableForPick(@Param("productId") UUID productId,
                                          @Param("warehouseId") UUID warehouseId);

    /**
     * 피킹 위치 선정용 (예약 후): 해당 상품의 실제 보관 위치를 조회.
     *
     * 출고 승인 시 available → reserved 로 이미 이동된 상태이므로
     * 피킹 배정 시에는 "물리적으로 상품이 존재하는 위치" 기준으로 조회해야 한다.
     * totalQty > 0 = "이 위치에 상품이 물리적으로 있다" (available이든 reserved든)
     */
    @Query("SELECT i FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId " +
            "AND i.locationId IS NOT NULL " +
            "AND i.totalQty > 0 " +
            "ORDER BY i.totalQty DESC")
    List<Inventory> findLocationsWithStock(@Param("productId") UUID productId,
                                            @Param("warehouseId") UUID warehouseId);

    /**
     * 주어진 location 집합 중 재고가 있는 행이 하나라도 있는지 확인.
     * - 랙 비활성화 가드 등 "이 위치들에 재고 남아있나?" 검사용
     */
    @Query("SELECT COUNT(i) > 0 FROM Inventory i " +
            "WHERE i.clientId = :clientId " +
            "AND i.locationId IN :locationIds " +
            "AND i.totalQty > 0")
    boolean existsStockInLocations(@Param("clientId") UUID clientId,
                                    @Param("locationIds") List<UUID> locationIds);

    /**
     * 출고 미리보기용 — (창고 × 품목) 단위로 가용재고 합산.
     * 한 창고 안에 여러 location 에 같은 product 가 있을 수 있으므로 SUM.
     * 결과 row: [warehouseId(UUID), productId(UUID), availableSum(Long)]
     */
    @Query("SELECT i.warehouseId, i.productId, COALESCE(SUM(i.availableQty), 0) " +
            "FROM Inventory i " +
            "WHERE i.clientId = :clientId " +
            "AND i.warehouseId IN :warehouseIds " +
            "AND i.productId IN :productIds " +
            "GROUP BY i.warehouseId, i.productId")
    List<Object[]> sumAvailableByWarehouseAndProduct(@Param("clientId") UUID clientId,
                                                      @Param("warehouseIds") List<UUID> warehouseIds,
                                                      @Param("productIds") List<UUID> productIds);

    /**
     * 단일 (productId + warehouseId) 의 가용재고 총합 — 기타출고 누적 검증용.
     * 한 창고 안 모든 위치의 availableQty 합산.
     */
    @Query("SELECT COALESCE(SUM(i.availableQty), 0) FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId")
    int sumAvailableQtyByProductAndWarehouse(@Param("productId") UUID productId,
                                              @Param("warehouseId") UUID warehouseId);

    /**
     * 단일 (productId + warehouseId) 의 불량재고 총합 — dispose_out 누적 검증용.
     */
    @Query("SELECT COALESCE(SUM(i.defectQty), 0) FROM Inventory i " +
            "WHERE i.productId = :productId " +
            "AND i.warehouseId = :warehouseId")
    int sumDefectQtyByProductAndWarehouse(@Param("productId") UUID productId,
                                           @Param("warehouseId") UUID warehouseId);

    /**
     * Location 별 totalQty 합계 — 마스터 측 location maxCapacity 변경 시 데이터 무결성 검증용.
     * "현재 보관 중인 양 > 새 maxCapacity" 케이스를 막기 위해 사용.
     * 결과 row: [locationId(UUID), totalSum(Long)]
     * 재고가 0 인 location 은 결과에서 제외 (호출자가 0 으로 간주).
     */
    @Query("SELECT i.locationId, COALESCE(SUM(i.totalQty), 0) " +
            "FROM Inventory i " +
            "WHERE i.clientId = :clientId " +
            "AND i.locationId IN :locationIds " +
            "GROUP BY i.locationId")
    List<Object[]> sumTotalQtyByLocations(@Param("clientId") UUID clientId,
                                          @Param("locationIds") List<UUID> locationIds);
}

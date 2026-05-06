package com.beyond.wbs.zone.repository;

import com.beyond.wbs.zone.domain.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    Optional<Zone> findByCode(String code);

    // 창고별 전체 존 조회
    Page<Zone> findByWarehouseId(UUID warehouseId, Pageable pageable);

    // 창고별 전체 존 (정렬 순)
    List<Zone> findByWarehouseIdOrderBySortOrderAsc(UUID warehouseId);

    // 대분류만 조회 (parent_id가 null인 것)
    List<Zone> findByWarehouseIdAndParentIsNull(UUID warehouseId);

    // 특정 대분류의 중분류 조회
    List<Zone> findByParentId(UUID parentId);

    // 카테고리별 구역 조회 (적치 위치 추천용)
    List<Zone> findByCategoryIdAndIsActiveTrue(UUID categoryId);

    // 창고 + ZoneType 조회 (불량 임시 보관존 등 적치 추천용)
    List<Zone> findByWarehouseIdAndZoneTypeAndIsActiveTrue(
            UUID warehouseId, com.beyond.wbs.zone.domain.ZoneType zoneType);

    /**
     * 해당 zone 의 활성 랙 수.
     * @return long count (없으면 0)
     */
    @Query("SELECT COUNT(r) FROM Rack r WHERE r.zone.id = :zoneId AND r.isActive = true")
    long countActiveRacksByZoneId(@Param("zoneId") UUID zoneId);
}
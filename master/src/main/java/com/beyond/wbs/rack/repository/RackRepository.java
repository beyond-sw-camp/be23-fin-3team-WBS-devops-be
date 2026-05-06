package com.beyond.wbs.rack.repository;

import com.beyond.wbs.rack.domain.Rack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RackRepository extends JpaRepository<Rack, UUID> {

    // 협력사별 랙 조회 (적치 위치 추천용)
    List<Rack> findBySupplierIdAndIsActiveTrue(UUID supplierId);

    // 구역별 랙 조회
    List<Rack> findByZoneIdAndIsActiveTrue(UUID zoneId);

    // 구역별 전체 랙
    List<Rack> findByZoneId(UUID zoneId);

    // 창고별 전체 랙 (zone 경유)
    List<Rack> findByZone_WarehouseId(UUID warehouseId);

    /**
     * 통합 필터: warehouseId / zoneId 중 하나 또는 둘 다로 필터 (둘 다 null 이면 전체).
     */
    @Query("SELECT r FROM Rack r " +
            "WHERE (:warehouseId IS NULL OR r.zone.warehouse.id = :warehouseId) " +
            "  AND (:zoneId IS NULL OR r.zone.id = :zoneId)")
    List<Rack> findByFilters(@Param("warehouseId") UUID warehouseId,
                             @Param("zoneId") UUID zoneId);
}

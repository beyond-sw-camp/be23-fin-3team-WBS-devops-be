package com.beyond.wbs.warehouse.repository;

import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.domain.WarehouseType;
import com.beyond.wbs.warehouse.dtos.WarehouseDetailResDto;
import com.beyond.wbs.warehouse.dtos.WarehouseListResDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findByCode(String code);
    Page<Warehouse> findByClientId(UUID clientId, Pageable pageable);

    /**
     * 창고 상세 조회 — 상세 전용 필드 + 활성 Zone/Rack 카운트까지 한 번에 반환.
     */
    @Query("""
            SELECT new com.beyond.wbs.warehouse.dtos.WarehouseDetailResDto(
                w.id, w.code, w.name, w.address,
                CAST(w.warehouseType AS string),
                w.isActive, w.updatedTime,
                w.managerName, w.phone, w.notes,
                (SELECT COUNT(z) FROM Zone z WHERE z.warehouse = w AND z.isActive = true),
                (SELECT COUNT(r) FROM Rack r WHERE r.zone.warehouse = w AND r.isActive = true)
            )
            FROM Warehouse w
            WHERE w.id = :id
            """)
    Optional<WarehouseDetailResDto> findDetailById(@Param("id") UUID id);

    /**
     * 창고 목록 조회 — 활성 Zone 수 / 활성 Rack 수를 상관 서브쿼리로 집계.
     *
     * - N+1 회피: 한 번의 쿼리로 모든 창고의 zoneCount/rackCount 를 반환
     * - Rack 은 Zone 을 통해 Warehouse 와 연결되므로 r.zone.warehouse 경로를 사용
     * - warehouseType 이 null 이면 타입 필터 무시
     * - countQuery 는 집계 없이 단순 카운트 (페이지 메타 전용)
     */
    @Query(value = """
            SELECT new com.beyond.wbs.warehouse.dtos.WarehouseListResDto(
                w.id, w.name, w.code, w.address,
                CAST(w.warehouseType AS string),
                w.isActive, w.updatedTime,
                (SELECT COUNT(z) FROM Zone z WHERE z.warehouse = w AND z.isActive = true),
                (SELECT COUNT(r) FROM Rack r WHERE r.zone.warehouse = w AND r.isActive = true)
            )
            FROM Warehouse w
            WHERE w.clientId = :clientId
            AND (:warehouseType IS NULL OR w.warehouseType = :warehouseType)
            """,
            countQuery = """
                    SELECT COUNT(w) FROM Warehouse w
                    WHERE w.clientId = :clientId
                    AND (:warehouseType IS NULL OR w.warehouseType = :warehouseType)
                    """)
    Page<WarehouseListResDto> findListByClientId(@Param("clientId") UUID clientId,
                                                  @Param("warehouseType") WarehouseType warehouseType,
                                                  Pageable pageable);
}

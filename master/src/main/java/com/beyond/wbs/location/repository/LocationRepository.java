package com.beyond.wbs.location.repository;

import com.beyond.wbs.location.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    Page<Location> findByRackId(UUID rackId, Pageable pageable);

    // 랙에 이미 존재하는 로케이션 목록 — RackService 가 층수 변경 시 부족분만
    // 추가 생성하기 위해 기존 floor_no 집합을 확인하는 용도.
    List<Location> findByRackId(UUID rackId);

    /**
     * 창고 단위 활성 로케이션 전체 조회 (Zone/Rack/Warehouse 함께 로딩).
     * Stock 서비스의 층별 재고 요약 등 벌크 조회용.
     * 정렬은 호출부에서 추가 처리한다 (zone.sortOrder, zone.code, rack.code, floorNo 순 권장).
     */
    @Query("SELECT l FROM Location l " +
            "JOIN FETCH l.rack r " +
            "JOIN FETCH r.zone z " +
            "JOIN FETCH z.warehouse w " +
            "WHERE w.id = :warehouseId AND l.isActive = true")
    List<Location> findActiveByWarehouseIdWithDetails(@Param("warehouseId") UUID warehouseId);
}

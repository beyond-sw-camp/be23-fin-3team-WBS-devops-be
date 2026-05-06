package com.beyond.wbs.inventory.repository;

import com.beyond.wbs.inventory.domain.InventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, UUID> {

    /**
     * 특정 월의 (상품+창고+위치) 스냅샷 조회
     * - 월말 마감 중복 생성 방지용
     */
    Optional<InventorySnapshot> findByClientIdAndProductIdAndWarehouseIdAndLocationIdAndSnapshotMonth(
            UUID clientId, UUID productId, UUID warehouseId, UUID locationId, LocalDate snapshotMonth);

    /**
     * 특정 회사의 월별 스냅샷 전체 조회
     * - 월말 재고 리포트 화면
     */
    List<InventorySnapshot> findByClientIdAndSnapshotMonth(UUID clientId, LocalDate snapshotMonth);
}

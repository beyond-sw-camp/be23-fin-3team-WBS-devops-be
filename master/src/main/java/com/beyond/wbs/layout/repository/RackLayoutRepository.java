package com.beyond.wbs.layout.repository;

import com.beyond.wbs.layout.domain.RackLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RackLayoutRepository extends JpaRepository<RackLayout, UUID> {
    Optional<RackLayout> findByRackId(UUID rackId);
    List<RackLayout> findByZoneIdAndRack_IsActiveTrueAndZone_IsActiveTrue(UUID zoneId);
    List<RackLayout> findByZone_WarehouseIdAndRack_IsActiveTrueAndZone_IsActiveTrue(UUID warehouseId);
}

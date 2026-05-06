package com.beyond.wbs.layout.repository;

import com.beyond.wbs.layout.domain.ZoneLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneLayoutRepository extends JpaRepository<ZoneLayout, UUID> {
    Optional<ZoneLayout> findByZoneId(UUID zoneId);
    List<ZoneLayout> findByZone_WarehouseIdAndZone_IsActiveTrue(UUID warehouseId);
}

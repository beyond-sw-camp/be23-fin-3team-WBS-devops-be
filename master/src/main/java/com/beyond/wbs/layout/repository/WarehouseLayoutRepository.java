package com.beyond.wbs.layout.repository;

import com.beyond.wbs.layout.domain.WarehouseLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseLayoutRepository extends JpaRepository<WarehouseLayout, UUID> {
    Optional<WarehouseLayout> findByWarehouseId(UUID warehouseId);
}
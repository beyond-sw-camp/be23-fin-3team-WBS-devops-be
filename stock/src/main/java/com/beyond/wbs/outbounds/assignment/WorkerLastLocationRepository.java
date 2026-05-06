package com.beyond.wbs.outbounds.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkerLastLocationRepository extends JpaRepository<WorkerLastLocation, UUID> {
    Optional<WorkerLastLocation> findByClientIdAndUserId(UUID clientId, UUID userId);
}

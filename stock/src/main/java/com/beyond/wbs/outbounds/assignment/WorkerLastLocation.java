package com.beyond.wbs.outbounds.assignment;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "worker_last_locations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worker_last_location_client_user",
                columnNames = {"client_id", "user_id"}
        )
)
public class WorkerLastLocation {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    @Column(name = "zone_id", columnDefinition = "BINARY(16)")
    private UUID zoneId;

    @Column(name = "zone_code", length = 50)
    private String zoneCode;

    @Column(name = "zone_name", length = 100)
    private String zoneName;

    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    @Column(name = "location_code", length = 80)
    private String locationCode;

    @Column(name = "last_worked_at", nullable = false)
    private LocalDateTime lastWorkedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        if (lastWorkedAt == null) {
            lastWorkedAt = LocalDateTime.now();
        }
    }

    public void updateLocation(
            UUID warehouseId,
            UUID zoneId,
            String zoneCode,
            String zoneName,
            UUID locationId,
            String locationCode) {
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.zoneCode = zoneCode;
        this.zoneName = zoneName;
        this.locationId = locationId;
        this.locationCode = locationCode;
        this.lastWorkedAt = LocalDateTime.now();
    }
}

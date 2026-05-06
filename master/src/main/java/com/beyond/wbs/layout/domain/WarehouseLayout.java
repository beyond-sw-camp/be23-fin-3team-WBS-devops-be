package com.beyond.wbs.layout.domain;

import com.beyond.wbs.warehouse.domain.Warehouse;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "warehouse_layouts")
public class WarehouseLayout {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private Integer canvasWidth;   // 캔버스 가로 (px)

    @Column(nullable = false)
    private Integer canvasHeight;  // 캔버스 세로 (px)

    @Column(length = 10)
    private String bgColor;        // 배경색 HEX코드

    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        this.updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public void update(Integer canvasWidth, Integer canvasHeight, String bgColor) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.bgColor = bgColor;
    }
}
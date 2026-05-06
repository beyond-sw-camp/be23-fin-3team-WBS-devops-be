package com.beyond.wbs.layout.domain;

import com.beyond.wbs.zone.domain.Zone;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "zone_layouts")
public class ZoneLayout {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "pos_x", nullable = false)
    private Integer posX;      // 캔버스 기준 절대좌표 x

    @Column(name = "pos_y", nullable = false)
    private Integer posY;      // 캔버스 기준 절대좌표 y

    @Column(nullable = false)
    private Integer width;     // 가로

    @Column(nullable = false)
    private Integer height;    // 세로

    @Column(nullable = false)
    @Builder.Default
    private Integer rotation = 0;  // 회전각

    @Column(length = 10)
    private String color;      // HEX코드

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Integer posX, Integer posY, Integer width, Integer height, Integer rotation, String color) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.rotation = rotation != null ? rotation : 0;
        this.color = color;
    }
}

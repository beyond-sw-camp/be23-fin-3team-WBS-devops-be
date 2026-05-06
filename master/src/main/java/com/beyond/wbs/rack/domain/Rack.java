package com.beyond.wbs.rack.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.beyond.wbs.supplier.domain.Supplier;
import com.beyond.wbs.zone.domain.Zone;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "racks")
public class Rack extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // warehouse_id 제거 - zone이 warehouse를 알고 있으므로 불필요
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(length = 30, nullable = false)
    private String code;

    // 랙의 층 수 (Location 이 채워지는 단위. 1층/2층/3층...)
    @Column(name = "level_no")
    private Integer levelNo;

    // 층별 가이드 JSON (예: 층마다 보관 가능 SKU 그룹 등)
    @Column(name = "level_guide_json", columnDefinition = "TEXT")
    private String levelGuideJson;

    // 최대 수용량 (랙 전체)
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    // ── 물리 치수 (정보성, 비즈니스 로직 미사용) ──
    @Column(name = "width_mm")
    private Integer widthMm;       // 가로 (mm)

    @Column(name = "depth_mm")
    private Integer depthMm;       // 세로 (mm)

    @Column(name = "height_mm")
    private Integer heightMm;      // 높이 (mm)

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 부분 수정 — null 인 필드는 변경하지 않는다.
     * code/zone 은 수정 불가.
     */
    public void update(String name,
                       Supplier supplier,
                       Integer levelNo,
                       String levelGuideJson,
                       Integer maxCapacity,
                       Integer widthMm,
                       Integer depthMm,
                       Integer heightMm) {
        if (name != null) this.name = name;
        if (supplier != null) this.supplier = supplier;
        if (levelNo != null) this.levelNo = levelNo;
        if (levelGuideJson != null) this.levelGuideJson = levelGuideJson;
        if (maxCapacity != null) this.maxCapacity = maxCapacity;
        if (widthMm != null) this.widthMm = widthMm;
        if (depthMm != null) this.depthMm = depthMm;
        if (heightMm != null) this.heightMm = heightMm;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}

package com.beyond.wbs.location.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.beyond.wbs.rack.domain.Rack;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "locations")
public class Location extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rack_id", nullable = false)
    private Rack rack;

    @Column(nullable = false)
    private Integer floorNo;

    @Column(nullable = false)
    private String code;

    @Column
    private String barcode;

    // 해당 층(Location)의 최대 보관 수량
    // - 적치 추천 시 잔여 용량 필터, 재고 전체 집계의 기준이 된다.
    // - nullable: 값이 없으면 "무제한/미정"으로 취급한다.
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * maxCapacity 변경. null 입력 시 무제한/미정 처리.
     * 0 이하 값은 호출자가 미리 검증.
     */
    public void updateMaxCapacity(Integer newMaxCapacity) {
        this.maxCapacity = newMaxCapacity;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
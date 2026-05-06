package com.beyond.wbs.store.domain;

import com.beyond.wbs.code.MasterCodePolicy;
import com.beyond.wbs.domain.BaseTimeEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "stores")
public class Store extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20)
    private String bizNo;

    @Column(length = MasterCodePolicy.MAX_LEN, nullable = false)
    private String code;

    @Column(length = 50)
    private String ceoName;

    @Column(length = 20)
    private String tel;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * 자동 웨이브 생성 대상 여부 (opt-in).
     * true: WaveScheduler 가 이 출고처의 출고지시서를 자동으로 웨이브 묶음 처리.
     * false: 자동 처리 제외 (수동 웨이브 생성만 가능).
     * 기본값 false — 명시적으로 켜야 자동 처리됨 (안전 기본값).
     */
    @Builder.Default
    @Column(name = "auto_wave_enabled", nullable = false)
    private Boolean autoWaveEnabled = false;

    public void deactivate() {
        this.isActive = false;
    }

    public void updateAutoWaveEnabled(Boolean enabled) {
        if (enabled != null) {
            this.autoWaveEnabled = enabled;
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
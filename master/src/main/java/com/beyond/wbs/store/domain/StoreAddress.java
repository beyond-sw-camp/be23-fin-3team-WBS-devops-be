package com.beyond.wbs.store.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "store_addresses")
public class StoreAddress {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(length = 100, nullable = false)
    private String name;        // 배송지명 (예: 본사, 물류창고)

    @Column(length = 100, nullable = false)
    private String receiver;    // 수령인

    @Column(length = 20, nullable = false)
    private String tel;         // 연락처

    @Column(length = 255, nullable = false)
    private String address;     // 주소

    @Column(length = 255)
    private String addressDetail; // 상세주소

    @Column(length = 10)
    private String zipcode;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDefault = false;  // 기본배송지

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void setDefault() {
        this.isDefault = true;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
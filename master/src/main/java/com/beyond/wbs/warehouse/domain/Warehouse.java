package com.beyond.wbs.warehouse.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 2. 접근 제어 추가@Getter @ToString
@Getter @ToString
@Builder
@Entity
@Table(name = "warehouses",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"client_id", "code"})
        }
)
public class Warehouse extends BaseTimeEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)") // MySQL/MariaDB 최적화
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20, nullable = false)
    private String code;

    @Column(length = 255)
    private String address;

    // 창고 타입 (NORMAL / RETURN / DISPOSAL)
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private WarehouseType warehouseType;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    // ── 상세 화면에서만 쓰이는 보조 정보 ──
    @Column(name = "manager_name", length = 50)
    private String managerName;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;


    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    /**
     * 상세 화면의 "정보 수정" 모달에서 호출.
     * null 이 들어온 필드는 덮어쓰지 않고 유지한다 (부분 수정 지원).
     * code 는 PK 성격이라 수정 대상에 포함하지 않는다.
     */
    public void updateDetail(String name, String address,
                             String managerName, String phone, String notes) {
        if (name != null) this.name = name;
        if (address != null) this.address = address;
        if (managerName != null) this.managerName = managerName;
        if (phone != null) this.phone = phone;
        if (notes != null) this.notes = notes;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

}

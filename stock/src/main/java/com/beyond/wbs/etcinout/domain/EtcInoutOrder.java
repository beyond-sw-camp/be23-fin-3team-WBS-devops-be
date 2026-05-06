package com.beyond.wbs.etcinout.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "etc_inout_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EtcInoutOrder {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    // 생성자ID
    @Column(name = "created_by", columnDefinition = "BINARY(16)", nullable = false)
    private UUID createdBy;

    // 고객사ID
    @Column(name = "client_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID clientId;

    // 입고처(공장) 반송 시 사용
    @Column(name = "supplier_id", columnDefinition = "BINARY(16)")
    private UUID supplierId;

    // 출고처(매장) 반품 시 사용
    @Column(name = "store_id", columnDefinition = "BINARY(16)")
    private UUID storeId;

    // 창고 ID
    @Column(name = "warehouse_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID warehouseId;

    // 관리 식별 번호
    @Column(name = "order_no", length = 30, nullable = false)
    private String orderNo;

    // 입출고 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "io_type", length = 20, nullable = false)
    private IoType ioType;

    // 입출고
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    // 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EtcInoutStatus status = EtcInoutStatus.draft;

    // 비고
    @Column(name = "note")
    private String note;

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 자동 배정된 작업자 (모바일 처리 담당) — 승인 시점에 박힘
    @Column(name = "assigned_to", columnDefinition = "BINARY(16)")
    private UUID assignedTo;

    // 승인자
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    // 승인 일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 완료 처리자 (웹 직접 완료 또는 모바일 작업자)
    @Column(name = "completed_by", columnDefinition = "BINARY(16)")
    private UUID completedBy;

    // 완료 일시
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 수정 일시
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 출처 ID (outbound_order 또는 inbound_order의 ID)
    @Column(name = "ref_id")
    private UUID refId;

    // 출처 입출고
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type")
    private RefType refType;

    // 기타입출고 수정
    public void update(IoType ioType, String note, UUID warehouseId,
                       UUID supplierId, UUID storeId, UUID refId, RefType refType) {
        this.ioType = ioType;
        this.note = note;
        this.warehouseId = warehouseId;
        this.supplierId = supplierId;
        this.storeId = storeId;
        this.refId = refId;
        this.refType = refType;
        if (this.ioType != null) {
            this.direction = this.ioType.name().endsWith("in")
                    ? Direction.in : Direction.out;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // 상태 변경
    public void updateStatus(EtcInoutStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    // 승인 (draft → approved) + 작업자 자동 배정
    public void approve(UUID approvedBy, UUID assignedTo) {
        this.status = EtcInoutStatus.approved;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.assignedTo = assignedTo;
        this.updatedAt = LocalDateTime.now();
    }

    // 완료 (draft 또는 approved → completed)
    public void complete(UUID completedBy) {
        this.status = EtcInoutStatus.completed;
        this.completedBy = completedBy;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.status == null) {
            this.status = EtcInoutStatus.draft;
        }
        if (this.direction == null && this.ioType != null){
            this.direction = this.ioType.name().endsWith
            ("in")
                    ? Direction.in : Direction.out;
        }
    }
}
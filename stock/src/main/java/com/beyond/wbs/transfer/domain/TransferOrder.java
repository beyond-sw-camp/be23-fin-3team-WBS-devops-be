package com.beyond.wbs.transfer.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "transfer_orders")
public class TransferOrder {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (회사)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // 지시서 번호 (TR-2026-001)
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    // Master Service 참조 (출발 창고)
    @Column(name = "from_warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID fromWarehouseId;

    // Master Service 참조 (도착 창고)
    @Column(name = "to_warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID toWarehouseId;

    // 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferOrderStatus status = TransferOrderStatus.draft;

    // 이동 예정일
    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    // Account Service 참조 (생성자)
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // Account Service 참조 (승인자)
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    // Account Service 참조 (이동 담당자)
    @Column(name = "assigned_to", columnDefinition = "BINARY(16)")
    private UUID assignedTo;

    // 승인 일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 비고
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // === 상태 전환 메서드 ===

    // 승인: draft → approved
    public void approve(UUID approverId, UUID assignedTo) {
        this.status = TransferOrderStatus.approved;
        this.approvedBy = approverId;
        this.assignedTo = assignedTo;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 상태 변경 (approved → in_progress 등)
    public void changeStatus(TransferOrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    // 완료: 모든 품목 처리 완료 (in_progress → completed)
    public void complete() {
        this.status = TransferOrderStatus.completed;
        this.updatedAt = LocalDateTime.now();
    }

    // 부분 완료: 불량이 섞여 처리됨 (in_progress → partial)
    public void partial() {
        this.status = TransferOrderStatus.partial;
        this.updatedAt = LocalDateTime.now();
    }

    // 취소: * → cancelled (단, 완료되었거나 이미 취소된 건 불가)
    public void cancel() {
        if (this.status == TransferOrderStatus.completed
                || this.status == TransferOrderStatus.cancelled) {
            throw new IllegalStateException("완료되었거나 이미 취소된 지시서는 취소할 수 없습니다.");
        }
        this.status = TransferOrderStatus.cancelled;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}

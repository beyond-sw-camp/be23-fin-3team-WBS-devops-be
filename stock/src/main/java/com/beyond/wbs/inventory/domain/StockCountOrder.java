package com.beyond.wbs.inventory.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "stock_count_orders")
public class StockCountOrder {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 고객사 ID
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // 창고 ID
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // 실사지시서 번호
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    // 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StockCountStatus status = StockCountStatus.draft;

    // 생성자
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // 승인자
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    // 비고
    @Column(columnDefinition = "TEXT")
    private String note;

    // 생성일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 승인일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 완료일시
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 실사 시작 (draft → in_progress)
    public void start(UUID approvedBy) {
        this.status = StockCountStatus.in_progress;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
    }

    // 실사 완료 (in_progress → completed)
    public void complete() {
        this.status = StockCountStatus.completed;
        this.completedAt = LocalDateTime.now();
    }

    // 실사 취소
    public void cancel() {
        this.status = StockCountStatus.cancelled;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
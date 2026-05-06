package com.beyond.wbs.inbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "placement_orders")
public class PlacementOrders {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 입고 지시서 ID
    @Column(name = "inbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inboundOrderId;

    // 회사 ID
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // 창고 ID
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // 적치 지시서 번호 (자동채번)
    @Column(name = "placement_no", nullable = false, length = 30)
    private String placementNo;

    // 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlacementOrderStatus status = PlacementOrderStatus.pending;

    // 적치 담당자 (선택)
    @Column(name = "assigned_to", columnDefinition = "BINARY(16)")
    private UUID assignedTo;

    // 완료 처리자
    @Column(name = "completed_by", columnDefinition = "BINARY(16)")
    private UUID completedBy;

    // 생성일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 완료일시
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 적치 지시서 생성
    public static PlacementOrders create(UUID inboundOrderId, UUID clientId,
                                          UUID warehouseId, String placementNo) {
        return PlacementOrders.builder()
                .inboundOrderId(inboundOrderId)
                .clientId(clientId)
                .warehouseId(warehouseId)
                .placementNo(placementNo)
                .status(PlacementOrderStatus.pending)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // 작업 시작
    public void startProgress() {
        this.status = PlacementOrderStatus.in_progress;
    }

    // 적치 완료
    public void complete() {
        this.status = PlacementOrderStatus.completed;
        this.completedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}

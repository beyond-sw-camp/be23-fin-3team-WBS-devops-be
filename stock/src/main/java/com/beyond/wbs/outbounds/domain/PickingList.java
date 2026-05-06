package com.beyond.wbs.outbounds.domain;

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
@Table(name = "picking_lists")
public class PickingList {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (고객사)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // 피킹리스트 번호 (자동채번: PK-00001)
    @Column(name = "picking_no", nullable = false, length = 20)
    private String pickingNo;

    // Master Service 내부 참조 - 창고
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Account Service 참조 (웨이브 생성자)
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // Account Service 참조 (피킹 담당자)
    @Column(name = "assigned_to", nullable = false, columnDefinition = "BINARY(16)")
    private UUID assignedTo;

    // 피킹 상태
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PickingListStatus status = PickingListStatus.pending;

    // 피킹 시작 일시
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    // 피킹 완료 일시
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    // 피킹리스트 상태 변경
    public void changeStatus(PickingListStatus status) {
        this.status = status;
        if (this.startedAt == null && status == PickingListStatus.in_progress) {
            this.startedAt = LocalDateTime.now();
        }
        if (status == PickingListStatus.completed || status == PickingListStatus.partial) {
            this.completedAt = LocalDateTime.now();
        }
    }

}

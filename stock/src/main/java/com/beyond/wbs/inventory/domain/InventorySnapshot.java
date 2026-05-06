package com.beyond.wbs.inventory.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 스냅샷 (Inventory Snapshot)
 *
 * 월말 마감 등 특정 시점의 재고 상태를 박제한다.
 * 매월 1일 새벽에 전월 마감 데이터를 자동 생성하는 식으로 활용 (배치 또는 Kafka)
 *
 * 기록 항목:
 * - 월초 수량(openQty)
 * - 월말 수량(closeQty)
 * - 월간 입고 합(inboundQty)
 * - 월간 출고 합(outboundQty)
 *
 * 마감 후엔 수정 불가 (재무팀/회계 리포트용)
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "inventory_snapshots",
        // 같은 (회사+상품+창고+위치+월) 조합 중복 방지
        uniqueConstraints = @UniqueConstraint(
                name = "uk_snapshot_unique",
                columnNames = {"client_id", "product_id", "warehouse_id", "location_id", "snapshot_month"}
        ))
public class InventorySnapshot {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (고객사)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 - 상품
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Master Service 참조 - 창고
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Master Service 참조 - 위치 (랙)
    @Column(name = "location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 스냅샷 기준월 (예: 2026-04-01 = 2026년 4월 마감)
    @Column(name = "snapshot_month", nullable = false)
    private LocalDate snapshotMonth;

    // 월초 수량 (전월 마감 = 이번 월 시작)
    @Column(name = "open_qty", nullable = false)
    @Builder.Default
    private Integer openQty = 0;

    // 월말 수량 (이번 월 마감 시점 가용재고)
    @Column(name = "close_qty", nullable = false)
    @Builder.Default
    private Integer closeQty = 0;

    // 월 입고 합계
    @Column(name = "inbound_qty", nullable = false)
    @Builder.Default
    private Integer inboundQty = 0;

    // 월 출고 합계
    @Column(name = "outbound_qty", nullable = false)
    @Builder.Default
    private Integer outboundQty = 0;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

package com.beyond.wbs.transfer.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 이동 실행 이력 (감사 로그)
 *
 * 작업자가 웨이브에서 실제 수행한 개별 작업을 한 건씩 기록.
 * 한 품목을 여러 번 나눠 처리해도 각 실행마다 row가 남아서,
 * "누가 언제 몇 개를 처리했는지" 전체 이력을 역추적할 수 있다.
 *
 * 용도: 감사(audit), 장애 분석, 작업자 KPI, 재고 불일치 원인 추적 등.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "transfer_executions")
public class TransferExecution {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 이동지시서 참조
    @Column(name = "transfer_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID transferOrderId;

    // 이동지시서 품목 참조
    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Master Service 참조 (출발 로케이션)
    @Column(name = "from_location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID fromLocationId;

    // Master Service 참조 (도착 로케이션)
    @Column(name = "to_location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID toLocationId;

    // 이번 실행에서 처리한 정상 수량
    @Column(nullable = false)
    private Integer qty;

    // 이번 실행에서 발견한 불량 수량
    @Builder.Default
    @Column(name = "defect_qty", nullable = false)
    private Integer defectQty = 0;

    // LOT 번호 (옵션)
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    // Account Service 참조 (실행 작업자)
    @Column(name = "executed_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID executedBy;

    // 실행 일시
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.executedAt == null) {
            this.executedAt = LocalDateTime.now();
        }
    }
}

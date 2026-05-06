package com.beyond.wbs.outbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 출고지시서 ↔ ERP 수주서 라인 단위 연결.
 *
 * 한 출고지시서가 여러 수주서·여러 라인을 포함할 수 있고,
 * 한 수주서 라인도 여러 출고지시서에 분할되어 들어갈 수 있다 (다대다, 라인 기준).
 *
 * 한 행이 의미하는 것:
 *   "이 출고지시서(outboundOrderId) 안에 수주서(salesOrderId)의 라인(salesOrderItemId)에서
 *    qty 만큼이 할당되어 있다."
 *
 * cancelledAt 으로 soft delete:
 *   - 출고지시서 취소 시 cancelledAt = now() 로 표시 (행 삭제하지 않음)
 *   - 수주서 진행률 누적 카운트는 WHERE cancelled_at IS NULL 로 자동 제외
 *   - 진행률 페이지에서 "취소된 지시서" 회색 이력으로 표시 가능
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(
        name = "outbound_sales_order_links",
        indexes = {
                // 수주서 라인 → 활성 링크 조회 (진행률 계산 핵심)
                @Index(name = "idx_oslink_so_item_active", columnList = "sales_order_item_id, cancelled_at"),
                // 출고지시서 → 연결된 수주서 라인 조회
                @Index(name = "idx_oslink_outbound", columnList = "outbound_order_id"),
                // 수주서 → 연결된 출고지시서 조회 (진행률 페이지 "연결된 출고지시서 목록")
                @Index(name = "idx_oslink_so", columnList = "sales_order_id, cancelled_at")
        }
)
public class OutboundSalesOrderLinks {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "outbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrderId;

    @Column(name = "sales_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID salesOrderId;

    @Column(name = "sales_order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID salesOrderItemId;

    /** 이 링크가 의미하는 할당 수량 — 해당 SO 라인에서 이 OB 로 가는 수량 */
    @Column(nullable = false)
    private Integer qty;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** soft delete — null 이면 활성, not null 이면 취소된 시각 */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** 출고지시서 취소 시 호출 — soft delete */
    public void markCancelled() {
        if (this.cancelledAt == null) {
            this.cancelledAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return cancelledAt == null;
    }
}

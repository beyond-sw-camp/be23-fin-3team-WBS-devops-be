package com.beyond.wbs.outbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "erp_sales_order_items")
public class ErpSalesOrderItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Stock Service 내부 참조 (ERP 수주서)
    @Column(name = "sales_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID salesOrderId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 수주 수량
    @Column(nullable = false)
    private Integer qty;

    /**
     * 출고지시서로 할당된 누적 수량 (cancelled 제외).
     * - SO 선택 화면에서 "미처리/부분/완료" 상태 표시 기준.
     * - 추가 출고지시서 생성 가능 수량 = qty - allocatedQty.
     * - OutboundSalesOrderLinks 의 활성 합계와 같아야 함 — 이벤트 핸들러가 갱신.
     */
    @Column(name = "allocated_qty", nullable = false)
    @Builder.Default
    private Integer allocatedQty = 0;

    /**
     * 실제 출고(dispatch) 완료된 누적 수량.
     * - 진행률 페이지 "처리량" 표시 기준.
     * - dispatch 이벤트 발생 시 갱신.
     */
    @Column(name = "dispatched_qty", nullable = false)
    @Builder.Default
    private Integer dispatchedQty = 0;

    // 수주 단가 (전체자릿수 15자리, 소숫점 이하 2자리)
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // 비고
    private String note;

    /** 추가 출고지시서로 할당 가능한 잔여 수량 */
    public int remainingToAllocate() {
        return Math.max(0, qty - (allocatedQty == null ? 0 : allocatedQty));
    }

    /** 진행률 % (0~100) — dispatch 기준 */
    public int dispatchProgressPercent() {
        if (qty == null || qty == 0) return 0;
        int d = dispatchedQty == null ? 0 : dispatchedQty;
        return Math.min(100, (int) Math.round((d * 100.0) / qty));
    }
}

// 더미데이터 (ERP 수주서):
//  이미 id 포함해서 DB에 넣어두는 것
//  → @PrePersist 필요 없음

//우리 시스템에서 생성하는 데이터 (출고전표 등):
//  서비스에서 객체 만들 때 id가 없음
//  → @PrePersist 필요
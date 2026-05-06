package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderStatus;
import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "발주서 목록" 화면용 응답 — 한 행 = 한 PO + 진행률 보강 필드.
 *
 * 수주서 진행률 페이지의 ErpSalesOrderResDto 와 대칭. PO 1건 ↔ 입고지시서 1건 (1:1) 정책이라
 * 분할(N개) 없이 단일 inbound 정보만 보강.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PurchaseOrderListItemResDto {

    /** 발주서 (PO) 단위 — FE 처리상태 라벨 */
    public enum ProcessStatus {
        NOT_STARTED,   // 입고지시서 미생성 (PO.status = approved)
        IN_PROGRESS,   // 입고지시서 생성됨, 검수 미완 (status != completed)
        COMPLETED      // 입고지시서 status = completed/partial (또는 모든 품목 received_qty 충족)
    }

    private UUID id;
    private String poNo;

    private UUID supplierId;
    private String supplierName;

    private LocalDate orderDate;
    private LocalDate scheduledDate;

    /** PO 자체의 status (draft/approved/closed) */
    private ErpPurchaseOrderStatus poStatus;

    /** FE 화면 라벨용 진행 상태 */
    private ProcessStatus processStatus;

    // ── 연결된 입고지시서 정보 (NOT_STARTED 면 null) ──
    private UUID inboundOrderId;
    private String inboundOrderNo;
    private InboundOrderStatus inboundStatus;

    /** 검수 진행률 = sum(received_qty) / sum(ordered_qty) * 100. 0~100. 입고지시서 없으면 0. */
    private int receiveProgressPercent;

    /** 발주서 품목 종 수 */
    private int itemCount;
    /** 발주서 전체 주문 수량 */
    private int totalOrderedQty;
}

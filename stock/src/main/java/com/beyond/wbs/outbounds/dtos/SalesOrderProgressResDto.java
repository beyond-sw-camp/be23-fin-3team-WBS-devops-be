package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 수주서 진행률 조회 응답.
 *
 * 한 SO 의 전체 진행 상태를 요약해서 보여준다:
 *   - SO 헤더 (거래처/날짜/상태)
 *   - 전체 진행률 (주문량 / 분배량 / 출고량 합계)
 *   - 품목별 진행률
 *   - 이 SO 에서 만들어진 OB 목록 (취소 이력 포함)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderProgressResDto {
    // ── 헤더 ──
    private UUID id;
    private String salesOrderNumber;
    private UUID storeId;
    private String storeName;
    private LocalDate orderDate;
    private LocalDate scheduledDate;
    private String status; // ErpSalesOrderStatus.name()

    // ── 전체 합계 (모든 품목 합산) ──
    private int totalOrderedQty;     // 주문량 합 (qty)
    private int totalAllocatedQty;   // 분배된 양 합 (allocatedQty) — OB 로 빠져나간 누적
    private int totalDispatchedQty;  // 실제 출고된 양 합 (dispatchedQty)

    /** 전체 출고 진행률 % — totalDispatchedQty / totalOrderedQty × 100 */
    private int dispatchProgressPercent;

    // ── 품목별 진행률 ──
    private List<ItemProgress> items;

    // ── 이 SO 와 연결된 OB 목록 (취소된 것도 이력으로 포함) ──
    private List<LinkedOutbound> linkedOutbounds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemProgress {
        private UUID id;            // SO item id
        private UUID productId;
        private String productName;
        private String sku;
        private int orderedQty;     // 주문량
        private int allocatedQty;   // 분배된 양 (현재 활성 OB 들에 묶인 누적)
        private int dispatchedQty;  // 실제 출고된 양
        private int remainingToDispatch;     // 주문 대비 아직 출고 안 된 양 = orderedQty - dispatchedQty
        private int remainingToAllocate;     // 추가 OB 로 더 보낼 수 있는 양 = orderedQty - allocatedQty
        private int dispatchProgressPercent; // 0-100
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedOutbound {
        private UUID outboundOrderId;
        private String outboundOrderNo;
        private UUID warehouseId;
        private String warehouseName;
        private String status; // OutboundOrderStatus.name()
        private LocalDate scheduledDate;
        /** 이 OB 에 들어간 SO 의 품목·수량 (link.qty 기준) */
        private List<LinkedItem> items;
        /** 링크가 soft delete 되었으면 true — 진행률 페이지에서 회색 이력 표시 */
        private boolean cancelled;
        private LocalDateTime cancelledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedItem {
        private UUID productId;
        private String productName;
        /** 이 SO 에서 이 OB 로 들어간 양 (link.qty) */
        private int qty;
    }
}

package com.beyond.wbs.inbounds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 입고전표 목록 한 행.
 *
 * 1행 = 1전표. 부모 입고지시서 정보 + 출처(발주서/반품 출고지시서) 정보 포함.
 *
 * originType 별 originNo 의미:
 *   - "purchase_order" : ErpPurchaseOrders.poNo
 *   - "return"         : OutboundOrders.orderNo (반품의 원 출고지시서)
 *   - "manual"         : null
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class InboundReceiptListItemResDto {
    // 전표
    private UUID id;
    private String receiptNo;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;

    // 부모 입고지시서
    private UUID inboundOrderId;
    private String orderNo;

    // 출처 (발주서/반품/수동)
    private String originType;
    private UUID originId;
    private String originNo;

    // 창고
    private UUID warehouseId;
    private String warehouseName;

    // 협력사 (반품·수동의 경우 null 가능)
    private UUID supplierId;
    private String supplierName;

    // 입고 담당자
    private UUID receivedBy;
    private String receivedByName;
}

package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.InboundOrders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class InboundOrderResDto {
    private UUID id;
    private String orderNo;
    private UUID supplierId;
    private String supplierName;
    private UUID warehouseId;
    private String warehouseName;
    private String expectedDate;
    private String status;
    private String source;
    private UUID originId;          // 원본 ID (발주서 ID / 출고지시서 ID)
    /**
     * 원본 번호 — source 별 의미:
     *   - purchase_order : 발주서 번호 (예: PO-TEST-005)
     *   - return         : 원본 출고지시서 번호
     *   - manual         : null
     * 상세 응답에서만 채움. 목록/생성 응답은 null.
     */
    private String originNo;
    private String returnFrom;      // 반품 출고처명 (source="return" 일 때만 값 있음)
    private UUID createdBy;
    private String createdByName;
    private UUID approvedBy;
    private String approvedAt;
    private String createdAt;
    private Integer totalItems;
    private Integer totalQty;

    public static InboundOrderResDto fromEntity(InboundOrders order, String supplierName,
                                                 String warehouseName, String createdByName,
                                                 int totalItems, int totalQty) {
        return fromEntity(order, supplierName, warehouseName, createdByName, totalItems, totalQty, null);
    }

    public static InboundOrderResDto fromEntity(InboundOrders order, String supplierName,
                                                 String warehouseName, String createdByName,
                                                 int totalItems, int totalQty, String returnFrom) {
        return InboundOrderResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierId(order.getSupplierId())
                .supplierName(supplierName)
                .warehouseId(order.getWarehouseId())
                .warehouseName(warehouseName)
                .expectedDate(order.getExpectedDate().toString())
                .status(order.getStatus().name())
                .source(order.getOriginType() != null ? order.getOriginType() : "수동")
                .originId(order.getOriginId())
                .returnFrom(returnFrom)
                .createdBy(order.getCreatedBy())
                .createdByName(createdByName)
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt() != null ? order.getApprovedAt().toString() : null)
                .createdAt(order.getCreatedAt().toLocalDate().toString())
                .totalItems(totalItems)
                .totalQty(totalQty)
                .build();
    }
}

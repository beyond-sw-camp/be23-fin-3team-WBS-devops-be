package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.domain.InboundReceipts;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class InboundReceiptResDto {
    private UUID id;
    private UUID inboundOrderId;
    private String orderNo;
    private UUID supplierId;
    private String supplierName;
    private UUID warehouseId;
    private String warehouseName;
    private UUID receivedBy;
    private String receivedByName;
    private String receiptNo;
    private String receivedAt;
    private String note;
    private String createdAt;
    private List<InboundReceiptItemResDto> items;

    public static InboundReceiptResDto fromEntity(
            InboundReceipts receipt,
            InboundOrders order,
            String supplierName,
            String warehouseName,
            String receivedByName,
            List<InboundReceiptItemResDto> items
    ) {
        return InboundReceiptResDto.builder()
                .id(receipt.getId())
                .inboundOrderId(receipt.getInboundOrderId())
                .orderNo(order != null ? order.getOrderNo() : null)
                .supplierId(order != null ? order.getSupplierId() : null)
                .supplierName(supplierName)
                .warehouseId(receipt.getWarehouseId())
                .warehouseName(warehouseName)
                .receivedBy(receipt.getReceivedBy())
                .receivedByName(receivedByName)
                .receiptNo(receipt.getReceiptNo())
                .receivedAt(receipt.getReceivedAt() != null ? receipt.getReceivedAt().toString() : null)
                .note(receipt.getNote())
                .createdAt(receipt.getCreatedAt() != null ? receipt.getCreatedAt().toString() : null)
                .items(items)
                .build();
    }
}

package com.beyond.wbs.transfer.dto;

import com.beyond.wbs.transfer.domain.TransferOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferOrderResDto {
    private UUID id;
    private String orderNo;
    private UUID fromWarehouseId;
    private String fromWarehouseName;
    private UUID toWarehouseId;
    private String toWarehouseName;
    private String expectedDate;
    private String status;
    private String note;
    private UUID createdBy;
    private UUID approvedBy;
    private String approvedAt;
    private String createdAt;
    private Integer totalItems;
    private Integer totalQty;
    private List<TransferOrderItemResDto> items;

    public static TransferOrderResDto fromEntity(TransferOrder order,
                                                  String fromWarehouseName,
                                                  String toWarehouseName,
                                                  int totalItems,
                                                  int totalQty,
                                                  List<TransferOrderItemResDto> items) {
        return TransferOrderResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .fromWarehouseId(order.getFromWarehouseId())
                .fromWarehouseName(fromWarehouseName)
                .toWarehouseId(order.getToWarehouseId())
                .toWarehouseName(toWarehouseName)
                .expectedDate(order.getExpectedDate().toString())
                .status(order.getStatus().name())
                .note(order.getNote())
                .createdBy(order.getCreatedBy())
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt() != null ? order.getApprovedAt().toString() : null)
                .createdAt(order.getCreatedAt().toLocalDate().toString())
                .totalItems(totalItems)
                .totalQty(totalQty)
                .items(items)
                .build();
    }
}

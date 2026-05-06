package com.beyond.wbs.inventory.dtos;

import com.beyond.wbs.inventory.domain.StockCountOrder;
import com.beyond.wbs.inventory.domain.StockCountStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountResDto {
    private UUID id;
    private String orderNo;
    private UUID warehouseId;
    private StockCountStatus status;
    private UUID createdBy;
    private UUID approvedBy;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private List<StockCountItemResDto> items;

    public static StockCountResDto from(StockCountOrder order) {
        return StockCountResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .createdBy(order.getCreatedBy())
                .approvedBy(order.getApprovedBy())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .approvedAt(order.getApprovedAt())
                .completedAt(order.getCompletedAt())
                .build();
    }
}
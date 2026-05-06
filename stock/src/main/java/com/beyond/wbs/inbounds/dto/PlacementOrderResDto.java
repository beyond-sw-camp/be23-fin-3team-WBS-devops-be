package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.PlacementOrders;
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
public class PlacementOrderResDto {
    private UUID id;
    private UUID inboundOrderId;
    private String placementNo;
    private String orderNo;
    private String status;
    private String createdAt;
    private String completedAt;
    private int totalItems;
    private int placedItems;
    private List<PlacementItemResDto> items;

    public static PlacementOrderResDto fromEntity(PlacementOrders order, String orderNo,
                                                    int totalItems, int placedItems,
                                                    List<PlacementItemResDto> items) {
        return PlacementOrderResDto.builder()
                .id(order.getId())
                .inboundOrderId(order.getInboundOrderId())
                .placementNo(order.getPlacementNo())
                .orderNo(orderNo)
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt().toString())
                .completedAt(order.getCompletedAt() != null ? order.getCompletedAt().toString() : null)
                .totalItems(totalItems)
                .placedItems(placedItems)
                .items(items)
                .build();
    }
}

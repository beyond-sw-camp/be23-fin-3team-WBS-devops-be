package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.search.inbound.InboundOrderSearchDocument;
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
public class InboundSearchResDto {

    private UUID id;
    private String orderNo;
    private String title;
    private String summary;
    private String status;
    private UUID supplierId;
    private UUID warehouseId;
    private String expectedDate;
    private String createdAt;
    private Integer totalItems;
    private Integer totalOrderedQty;
    private List<String> itemNames;

    public static InboundSearchResDto fromDocument(InboundOrderSearchDocument document) {
        return InboundSearchResDto.builder()
                .id(toUuid(document.getId()))
                .orderNo(document.getOrderNo())
                .title(document.getTitle())
                .summary(document.getSummary())
                .status(document.getStatus())
                .supplierId(toUuid(document.getSupplierId()))
                .warehouseId(toUuid(document.getWarehouseId()))
                .expectedDate(document.getExpectedDate())
                .createdAt(document.getCreatedAt())
                .totalItems(document.getTotalItems())
                .totalOrderedQty(document.getTotalOrderedQty())
                .itemNames(document.getItemNames())
                .build();
    }

    private static UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

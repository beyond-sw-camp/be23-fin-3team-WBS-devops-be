package com.beyond.wbs.search.inbound;

import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundOrderSearchDocument {
    public static final String INDEX_NAME = "inbound-orders-v1";
    public static final String DOCUMENT_TYPE = "INBOUND_ORDER";

    private String type;
    private String id;
    private String title;
    private String summary;
    private String clientId;
    private String createdAt;

    private String orderNo;
    private String status;
    private String warehouseId;
    private String supplierId;
    private String createdBy;
    private String approvedBy;
    private String originType;
    private String originId;
    private String expectedDate;
    private String approvedAt;
    private String updatedAt;
    private String note;

    private Integer totalItems;
    private Integer totalOrderedQty;
    private Integer totalReceivedQty;
    private Integer totalDefectQty;

    private List<String> productIds;
    private List<String> itemNames;

    public static InboundOrderSearchDocument from(InboundOrders order, List<InboundOrderItems> items) {
        List<InboundOrderItems> safeItems = items != null ? items : Collections.emptyList();
        List<String> productIds = safeItems.stream()
                .map(InboundOrderItems::getProductId)
                .map(UUID::toString)
                .toList();

        int totalOrderedQty = safeItems.stream()
                .mapToInt(item -> item.getOrderedQty() != null ? item.getOrderedQty() : 0)
                .sum();
        int totalReceivedQty = safeItems.stream()
                .mapToInt(item -> item.getReceivedQty() != null ? item.getReceivedQty() : 0)
                .sum();
        int totalDefectQty = safeItems.stream()
                .mapToInt(item -> item.getDefectQty() != null ? item.getDefectQty() : 0)
                .sum();

        return InboundOrderSearchDocument.builder()
                .type(DOCUMENT_TYPE)
                .id(order.getId().toString())
                .title(order.getOrderNo())
                .summary(buildSummary(order, safeItems.size(), totalOrderedQty))
                .clientId(order.getClientId().toString())
                .createdAt(toString(order.getCreatedAt()))
                .orderNo(order.getOrderNo())
                .status(order.getStatus().name())
                .warehouseId(toString(order.getWarehouseId()))
                .supplierId(toString(order.getSupplierId()))
                .createdBy(toString(order.getCreatedBy()))
                .approvedBy(toString(order.getApprovedBy()))
                .originType(order.getOriginType())
                .originId(toString(order.getOriginId()))
                .expectedDate(toString(order.getExpectedDate()))
                .approvedAt(toString(order.getApprovedAt()))
                .updatedAt(toString(order.getUpdatedAt()))
                .note(order.getNote())
                .totalItems(safeItems.size())
                .totalOrderedQty(totalOrderedQty)
                .totalReceivedQty(totalReceivedQty)
                .totalDefectQty(totalDefectQty)
                .productIds(productIds)
                .itemNames(Collections.emptyList())
                .build();
    }

    private static String buildSummary(InboundOrders order, int totalItems, int totalOrderedQty) {
        return String.format("%s / %s / %d items / %d qty",
                order.getOrderNo(),
                order.getStatus().name(),
                totalItems,
                totalOrderedQty);
    }

    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}

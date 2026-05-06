package com.beyond.wbs.instruction.render.inbound;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class InboundOrderPdfData {

    private final String clientName;
    private final String sourceNo;
    private final String warehouseName;
    private final String supplierName;
    private final LocalDate expectedDate;
    private final String note;

    private final String statusLabel;
    private final String originType;
    private final String createdByName;
    private final String issuedByName;
    private final String approvedByName;

    private final List<Line> items;
    private final int totalQty;
    private final BigDecimal totalAmount;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final int orderedQty;
        private final int receivedQty;
        private final int defectQty;
        private final BigDecimal unitPrice;
        private final BigDecimal amount;
    }
}

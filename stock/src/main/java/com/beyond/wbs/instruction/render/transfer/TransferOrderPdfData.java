package com.beyond.wbs.instruction.render.transfer;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class TransferOrderPdfData {

    private final String clientName;
    private final String sourceNo;
    private final String fromWarehouseName;
    private final String toWarehouseName;
    private final LocalDate expectedDate;
    private final String note;

    private final String statusLabel;
    private final String createdByName;
    private final String approvedByName;

    private final List<Line> items;
    private final int totalQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final String fromLocationName;
        private final String toLocationName;
        private final int orderedQty;
        private final int processedQty;
        private final int defectQty;
        private final String lotNo;
    }
}

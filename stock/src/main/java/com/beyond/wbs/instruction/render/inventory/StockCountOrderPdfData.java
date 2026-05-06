package com.beyond.wbs.instruction.render.inventory;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class StockCountOrderPdfData {

    private final String clientName;
    private final String sourceNo;
    private final String warehouseName;
    private final String statusLabel;
    private final String createdByName;
    private final String approvedByName;
    private final LocalDateTime createdAt;
    private final LocalDateTime approvedAt;
    private final String note;

    private final List<Line> items;
    private final int totalSystemQty;
    private final int totalCountQty;
    private final int totalDiffQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final String locationName;
        private final int systemQty;
        private final Integer countQty;        // null = 미실사
        private final Integer diffQty;         // null = 미실사
        private final String statusLabel;
    }
}

package com.beyond.wbs.instruction.render.etc;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EtcInoutOrderPdfData {

    private final String clientName;
    private final String sourceNo;
    private final String warehouseName;
    private final String ioTypeLabel;
    private final String directionLabel;
    private final String partnerName;          // supplier 또는 store 이름 (방향에 따라)
    private final String partnerLabel;         // "협력사" 또는 "출고처"
    private final String note;
    private final String statusLabel;
    private final String createdByName;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;

    private final List<Line> items;
    private final int totalQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final String locationName;
        private final int qty;
        private final int processedQty;
        private final String lotNo;
        private final String conditionLabel;
        private final String note;
    }
}

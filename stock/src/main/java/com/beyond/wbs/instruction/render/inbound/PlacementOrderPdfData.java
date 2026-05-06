package com.beyond.wbs.instruction.render.inbound;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PlacementOrderPdfData {

    private final String clientName;
    private final String sourceNo;             // placementNo
    private final String warehouseName;
    private final String inboundOrderNo;        // 상위 입고지시서 번호
    private final String statusLabel;
    private final String assignedToName;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;

    private final List<Line> items;
    private final int totalQty;
    private final int totalDefectQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final String locationName;       // 미배정 시 "(미배정)"
        private final int qty;
        private final String lotNo;
        private final boolean isDefect;
        private final boolean isPlaced;
        private final String completedByName;
    }
}

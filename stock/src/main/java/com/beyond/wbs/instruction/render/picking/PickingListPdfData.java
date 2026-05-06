package com.beyond.wbs.instruction.render.picking;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PickingListPdfData {

    private final String clientName;
    private final String sourceNo;
    private final String warehouseName;
    private final String statusLabel;
    private final String assignedToName;
    private final String createdByName;
    private final LocalDateTime createdAt;

    private final List<Line> items;
    private final int totalQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final String locationName;
        private final int qty;
        private final int pickedQty;
        private final String lotNo;
        private final String statusLabel;
    }
}

package com.beyond.wbs.instruction.render.outbound;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OutboundDispatchPdfData {

    private final String clientName;
    private final String sourceNo;             // dispatchNo
    private final String warehouseName;
    private final String outboundOrderNo;       // 상위 출고지시서 번호
    private final String storeName;             // 출고처 (상위 지시서에서 가져옴)
    private final String dispatchedByName;
    private final LocalDateTime dispatchedAt;

    private final List<Line> items;
    private final int totalQty;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final int qty;
        private final String lotNo;
    }
}

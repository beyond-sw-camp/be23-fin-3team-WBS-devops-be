package com.beyond.wbs.instruction.render.inbound;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InboundReceiptPdfData {

    private final String clientName;
    private final String sourceNo;             // receiptNo
    private final String warehouseName;
    private final String inboundOrderNo;        // 상위 입고지시서 번호
    private final String supplierName;
    private final LocalDateTime receivedAt;
    private final String note;
    private final String receivedByName;        // 입고 담당자 (검수자)

    private final List<Line> items;
    private final int totalNormalQty;
    private final int totalDefectQty;

    // 첨부된 불량 사진 총 장수 (모든 receipt item 합산)
    private final long defectEvidenceCount;

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final int qty;
        private final String lotNo;
        private final LocalDate expiryDate;
        private final String conditionLabel;    // 정상/불량/파손
        private final String inspectedByName;
    }
}

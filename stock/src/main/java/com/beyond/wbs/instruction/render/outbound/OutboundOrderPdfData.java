package com.beyond.wbs.instruction.render.outbound;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 출고지시서 PDF 템플릿 모델.
 *
 * Thymeleaf 템플릿 outbound-order.html이 이 객체를 `data`로 받아 바인딩한다.
 * 모든 필드는 null-safe하게 다뤄진다 (템플릿에서 ?: 또는 if 분기).
 *
 * 프론트 인쇄본과 항목 일치 (2026-04-29 정합):
 *  - 단가·금액·합계금액 포함
 *  - 상태·출처·생성자 포함
 *  - 로케이션·로트는 승인 시점 미할당이라 제거
 */
@Getter
@Builder
public class OutboundOrderPdfData {

    private final String clientName;       // 회사명 ((주) WBS 같은 발행 주체)
    private final String customerName;     // 고객사명 (출고처와 동일하지만 헤더에 노출)
    private final String sourceNo;         // 출고지시서 번호 (예: SO-00001)
    private final String warehouseName;    // 창고명
    private final String storeName;        // 출고처명
    private final String shippingAddress;
    private final String note;
    private final LocalDate scheduledDate;

    // 비즈니스 메타
    private final String statusLabel;      // "승인", "초안" 등 한글 라벨
    private final String originType;       // "ERP" 등 — null 시 "-"
    private final String createdByName;    // 생성자 이름 — null 시 "-"
    private final String issuedByName;     // 발행자 — null 시 "시스템 자동 발행"
    private final String approvedByName;   // 승인자 — null 시 "-"

    private final List<Line> items;
    private final int totalQty;
    private final BigDecimal totalAmount;  // 합계금액 (₩)

    @Getter
    @Builder
    public static class Line {
        private final String sku;
        private final String productName;
        private final int qty;
        private final BigDecimal unitPrice;  // 단가
        private final BigDecimal amount;     // 금액 = qty × unitPrice
        private final String note;           // item-level 비고 (도메인에 없으면 null)
    }
}

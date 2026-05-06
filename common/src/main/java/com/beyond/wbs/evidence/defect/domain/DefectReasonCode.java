package com.beyond.wbs.evidence.defect.domain;

/**
 * 불량 사유 정형 코드.
 * 자유 텍스트(reason_text)와 별개로, 통계·필터링 용도.
 */
public enum DefectReasonCode {
    PACKAGING_DAMAGE("포장 파손"),
    QUANTITY_SHORT  ("수량 부족"),
    QUANTITY_OVER   ("수량 초과"),
    WRONG_ITEM      ("오배송/오상품"),
    EXPIRED         ("유통기한 경과"),
    VISUAL_DEFECT   ("외관 불량"),
    FUNCTIONAL_DEFECT("기능 불량"),
    OTHER           ("기타");

    private final String displayName;

    DefectReasonCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

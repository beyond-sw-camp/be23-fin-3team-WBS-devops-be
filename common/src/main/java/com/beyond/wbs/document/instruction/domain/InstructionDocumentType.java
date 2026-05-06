package com.beyond.wbs.document.instruction.domain;

public enum InstructionDocumentType {

    OUTBOUND_ORDER     ("outbound-order",     "instruction/outbound-order",     "출고지시서"),
    INBOUND_RECEIPT    ("inbound-receipt",    "instruction/inbound-receipt",    "입고전표"),
    PICKING_LIST       ("picking-list",       "instruction/picking-list",       "피킹리스트"),
    OUTBOUND_DISPATCH  ("outbound-dispatch",  "instruction/outbound-dispatch",  "출고전표"),
    INBOUND_ORDER      ("inbound-order",      "instruction/inbound-order",      "입고지시서"),
    TRANSFER_ORDER     ("transfer-order",     "instruction/transfer-order",     "이동지시서"),
    PLACEMENT_ORDER    ("placement-order",    "instruction/placement-order",    "적치지시서"),
    ETC_INOUT_ORDER    ("etc-inout-order",    "instruction/etc-inout-order",    "기타입출고지시서"),
    STOCK_COUNT_ORDER  ("stock-count-order",  "instruction/stock-count-order",  "실사지시서");

    private final String code;
    private final String templatePath;
    private final String displayName;

    InstructionDocumentType(String code, String templatePath, String displayName) {
        this.code = code;
        this.templatePath = templatePath;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public String getDisplayName() {
        return displayName;
    }
}

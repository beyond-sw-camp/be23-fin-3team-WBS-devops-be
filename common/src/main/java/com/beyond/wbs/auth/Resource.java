package com.beyond.wbs.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Resource {
    INBOUND("RE001", "입고 관리"),
    OUTBOUND("RE002", "출고 관리"),
    TRANSFER("RE003", "이동 관리"),
    INVENTORY("RE004", "재고 관리"),
    STOCK_COUNT("RE005", "실사 관리"),
    ETC_INOUT("RE006", "기타 입출고"),
    MASTER("RE007", "마스터 관리"),
    STATISTICS("RE008", "통계");

    private final String codeValue;
    private final String codeName;
}

package com.beyond.wbs.inventory.domain;

public enum StockCountItemStatus {
    pending,   // 실사 대기
    counted,   // 실사 완료
    adjusted   // 재고 조정 반영 완료
}
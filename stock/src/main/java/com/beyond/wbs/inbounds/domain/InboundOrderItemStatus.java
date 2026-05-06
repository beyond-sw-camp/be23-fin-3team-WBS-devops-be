package com.beyond.wbs.inbounds.domain;

public enum InboundOrderItemStatus {
    pending,    // 입고 대기
    receiving,  // 입고 중
    completed,  // 입고 완료
    shortage;   // 부족 (지시 수량보다 적게 입고)
}

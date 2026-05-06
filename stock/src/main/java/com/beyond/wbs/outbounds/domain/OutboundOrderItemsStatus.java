package com.beyond.wbs.outbounds.domain;

public enum OutboundOrderItemsStatus {
    pending,    // 피킹 대기
    picking,    // 피킹 중
    completed,  // 피킹 완료
    shortage;   // 재고 부족 (일부만 피킹)
    // 원래 short였음 -> java 예약어라서 오류남 -> shortage 변경
    // 이 상품 하나가 피킹됐는가에 대한 상태
}

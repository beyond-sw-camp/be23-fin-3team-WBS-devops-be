package com.beyond.wbs.outbounds.domain;

public enum PickingListItemStatus {
    pending,    // 피킹 대기
    picking,    // 피킹 중
    completed,  // 피킹 완료
    shortage;   // 재고 부족
}

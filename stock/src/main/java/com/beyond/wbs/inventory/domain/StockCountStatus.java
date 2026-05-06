package com.beyond.wbs.inventory.domain;

public enum StockCountStatus {
    draft,        // 초안
    in_progress,  // 실사 중
    completed,    // 완료
    cancelled     // 취소
}
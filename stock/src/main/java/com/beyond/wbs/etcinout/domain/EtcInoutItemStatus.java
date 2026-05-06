package com.beyond.wbs.etcinout.domain;

// 품목 상태
public enum EtcInoutItemStatus {
    pending,    // 대기
    completed,  // 완료 (요청 수량 전량 처리)
    shortage,   // 부족 완료 (출고에서 요청보다 적게 픽업됨)
    cancelled   // 취소
}
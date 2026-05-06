package com.beyond.wbs.etcinout.domain;

// 상태
public enum EtcInoutStatus {
    draft,        // 작성중 (운영자 작성, 작업자 미배정)
    approved,     // 승인 완료 (작업자 자동 배정 → 모바일 대기)
    completed,    // 완료 (재고 반영됨)
    cancelled     // 취소
}
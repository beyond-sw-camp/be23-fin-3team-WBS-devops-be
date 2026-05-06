package com.beyond.wbs.etcinout.domain;

// 기타 입출고 유형
// 반품은 입고 모듈(InboundService.createFromReturn)에서 처리하므로 여기에 포함하지 않음.
public enum IoType {
    sample_in,       // 샘플 입고
    adjust_in,       // 재고 조정 입고
    dispose_in,      // 폐기 입고 (불량 재고 → 폐기존 이동)
    dispose_out,     // 폐기 출고 (불량 재고 물리적 폐기)
    sample_out,      // 샘플 출고
    adjust_out,      // 재고 조정 출고
    etc_in,          // 기타 입고
    etc_out          // 기타 출고
}
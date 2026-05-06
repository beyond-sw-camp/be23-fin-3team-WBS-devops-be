package com.beyond.wbs.transfer.domain;

public enum TransferOrderStatus {
    draft,        // 임시
    approved,     // 승인완료 
    in_progress,  // 진행 중 
    completed,    // 완료 (모든 품목 처리 완료)
    partial,      // 부분 완료
    cancelled;    // 취소
}

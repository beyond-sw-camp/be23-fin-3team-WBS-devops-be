package com.beyond.wbs.transfer.domain;

public enum TransferOrderItemStatus {
    pending,      // 대기
    in_progress,  // 처리 중 
    picked,       // 픽업 완료 
    completed,    // 완료
    shortage;     // 부족 (불량 섞여서 완료)
}

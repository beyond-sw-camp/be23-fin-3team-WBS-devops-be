package com.beyond.wbs.outbounds.domain;

public enum OutboundOrderStatus {
    draft,          // 초안
    approved,       // 승인완료
    in_progress,    // 처리중
    completed,      // 완료
    partial,        // 부분완료
    cancelled;      // 취소
}

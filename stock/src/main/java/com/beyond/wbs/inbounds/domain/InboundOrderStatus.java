package com.beyond.wbs.inbounds.domain;

public enum InboundOrderStatus {
    draft,          // 초안 (ASN에서 생성)
    approved,       // 승인완료 (검수 대기)
    received,       // 입고 확정 — 수량/불량/LOT 검수 완료 (가재고 발생)
    placing,        // 적치 중 — 적치 작업이 생성됨 (가재고 유지)
    completed,      // 적치 완료 — 전량 정상 입고
    partial,        // 적치 완료 — 검수/적치 중 불량 발생 (부분 입고)
    cancelled;      // 취소
}

package com.beyond.wbs.outbounds.domain;

/**
 * ERP 수주서의 출고지시서 처리 상태 — 화면 표시 / 필터용.
 *
 * 계산 기준: 모든 SO 라인의 (qty vs allocatedQty) 합산으로 판정
 *   - NOT_STARTED  : SUM(allocatedQty) == 0       — 아직 출고지시서 안 만든 상태
 *   - PARTIAL      : 0 < SUM(allocatedQty) < SUM(qty) — 일부만 분배됨
 *   - COMPLETED    : SUM(allocatedQty) >= SUM(qty)    — 전량 분배 완료 (= "처리 완료")
 *
 * COMPLETED 는 FE "처리 완료 숨기기" 토글의 대상.
 */
public enum SalesOrderProcessStatus {
    NOT_STARTED,
    PARTIAL,
    COMPLETED
}

package com.beyond.wbs.inventory.domain;

/**
 * 재고 변동 발생 원천 (Reference Type)
 * 어떤 비즈니스 이벤트로 인해 재고가 변동됐는지 추적
 *
 * - inbound_order : 입고지시서
 * - outbound_order: 출고지시서
 * - etc_inout_order: 기타 입출고 지시서
 * - stock_count   : 재고실사
 * - manual        : 수동 조정
 */
public enum RefType {
    inbound_order,
    outbound_order,
    transfer_order,
    etc_inout_order,
    stock_count,
    manual
}

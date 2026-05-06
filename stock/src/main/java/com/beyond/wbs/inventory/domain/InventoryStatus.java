package com.beyond.wbs.inventory.domain;

/**
 * 재고 상태
 *
 * - available: 가용재고 (출고 가능)
 * - reserved : 예약재고 (출고지시서 승인되어 잠긴 재고, 출고 불가)
 * - defect   : 불량재고 (검수 시 불량 판정, 별도 격리)
 * - pending  : 검수중재고 (입고 검수 대기, 아직 적치 전)
 */
public enum InventoryStatus {
    available,
    reserved,
    defect,
    incoming,
    pending
}

package com.beyond.wbs.inventory.domain;

/**
 * 재고 이력 변동 유형 (Transaction Type)
 *
 * - inbound  : 입고 (검수/적치 완료로 가재고 → 확정재고)
 * - outbound : 출고 (출고확정으로 재고 차감)
 * - reserve  : 예약 (출고지시서 승인 시 가용재고 → 예약재고)
 * - unreserve: 예약해제 (출고지시서 취소 시 예약재고 → 가용재고)
 * - transfer : 이동 (창고/위치 이동)
 * - adjust   : 조정 (재고실사 결과 반영)
 * - dispose  : 폐기 (불량/유효기간 만료 등)
 * - return   : 반품 (출고 후 반품)
 * - defect_in: 기타 입고 시점 불량 판정 (기타입고 검수에서 직접 defectQty 적재)
 */
public enum TxType {
    inbound,
    outbound,
    reserve,
    unreserve,
    transfer,
    adjust,
    dispose,
    returned,  // 'return'은 자바 예약어라 returned 사용
    defect_in
}

package com.beyond.wbs.evidence.defect.domain;

/**
 * 불량 사진이 첨부될 수 있는 도메인 row 종류.
 *
 * 1차(현재) 사용: INBOUND_ORDER_ITEM 한 종.
 * 2차 확장 시 나머지를 enable. 추가 시 FE 매핑 테이블도 함께 갱신.
 *
 * 모두 "발주/지시 품목" 단위 anchor — 검수·적치·이동 작업 전/중/후 어느 시점이든 첨부 가능.
 *
 * source_id 가리키는 곳:
 *  INBOUND_ORDER_ITEM     → inbound_order_items.id
 *  PLACEMENT_ORDER_ITEM   → placement_items.id
 *  TRANSFER_ORDER_ITEM    → transfer_order_items.id
 *  OUTBOUND_ORDER_ITEM    → outbound_order_items.id
 *  ETC_INOUT_ORDER_ITEM   → etc_inout_order_items.id
 */
public enum DefectEvidenceSourceType {
    INBOUND_ORDER_ITEM,
    PLACEMENT_ORDER_ITEM,
    TRANSFER_ORDER_ITEM,
    OUTBOUND_ORDER_ITEM,
    ETC_INOUT_ORDER_ITEM
}

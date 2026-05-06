package com.beyond.wbs.kafka.event;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * 입고 → 재고 이벤트 공통 클래스
 *
 * 입고지시서 승인/검수/불량/적치 네 가지 이벤트 모두 같은 구조.
 * 토픽 이름으로 이벤트 종류를 구분한다.
 *
 * 토픽:
 *  - inbound.approved  : 승인 (입고예정 ↑)
 *  - inbound.inspected : 검수 완료 정상품 (입고예정 ↓ / 검수중 ↑)
 *  - inbound.defect    : 검수 불량 (입고예정 ↓ / 불량 ↑)
 *  - inbound.placed    : 적치 완료 (검수중 ↓ / 가용 ↑)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InboundStockEvent {

    private UUID clientId;
    private UUID warehouseId;
    private UUID refId;
    /** 행위자 — 이 변동을 발생시킨 사용자 ID. 시스템 자동작업이면 SYSTEM_USER_ID. */
    private UUID userId;
    private List<Item> items;

    /**
     * 검수 이벤트(inbound.inspected) 전용 — 같은 이벤트 안에 불량 수량도 함께 실어
     * Consumer가 "pending 증가 → pending→defect 이동"을 같은 트랜잭션에서 순서대로 처리.
     * 토픽 분리로 인한 race(순서 꼬임) 방지용. nullable.
     */
    private List<Item> defectItems;

    /**
     * 변동 대상 품목 1건
     * - productId : 어떤 상품
     * - locationId: 어느 위치(랙)에 적치할지
     * - qty       : 몇 개
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID productId;
        private UUID locationId;
        private int qty;
    }
}

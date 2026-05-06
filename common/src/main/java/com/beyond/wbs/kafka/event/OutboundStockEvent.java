package com.beyond.wbs.kafka.event;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * 출고 → 재고 이벤트 공통 클래스
 *
 * 출고지시서 승인/취소/확정 세 가지 이벤트 모두 같은 구조.
 * 토픽 이름으로 이벤트 종류를 구분한다.
 *
 * 토픽:
 *  - outbound.approved  : 승인 (가용 → 예약)
 *  - outbound.cancelled : 취소 (예약 → 가용 원복)
 *  - outbound.completed : 확정 (예약 차감, 실물 출고)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OutboundStockEvent {

    private UUID clientId;
    private UUID warehouseId;
    private UUID refId;
    /** 행위자 — 이 변동을 발생시킨 사용자 ID. 시스템 자동작업이면 SYSTEM_USER_ID. */
    private UUID userId;
    /** 출고 구분 — "manual" | "sales_order" | "return". 통계 대시보드 분기용. */
    private String originType;
    private List<Item> items;

    /**
     * 변동 대상 품목 1건
     * - productId : 어떤 상품
     * - locationId: 어느 위치(랙)에서
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

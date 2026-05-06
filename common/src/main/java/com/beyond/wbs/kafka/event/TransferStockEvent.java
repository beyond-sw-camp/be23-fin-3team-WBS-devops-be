package com.beyond.wbs.kafka.event;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * 이동 → 재고 이벤트 공통 클래스
 *
 * 이동지시서 라이프사이클 + 실제 물량 흐름 두 측면을 모두 토픽으로 표현한다.
 * 토픽:
 *  - transfer.created   : 지시서 생성 (통계 카운트용)
 *  - transfer.approved  : 승인 (통계 카운트용)
 *  - transfer.cancelled : 취소 (통계 카운트용)
 *  - transfer.out       : 출발지에서 빠짐 (PICK 시점, warehouseId = 출발창고)
 *  - transfer.in        : 도착지에 놓임 (PLACE 시점, warehouseId = 도착창고)
 *  - transfer.completed : 마감 (리드타임 집계용)
 *
 * out/in 두 토픽으로 분리해서 발행하면 입고/출고와 동일 패턴으로
 * "창고별 처리량 = 들어온 양 + 나간 양" 집계가 단순해진다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransferStockEvent {

    private UUID clientId;
    /** out 이벤트면 출발창고, in 이벤트면 도착창고. 라이프사이클 이벤트(created/approved/...)는 출발창고 기준. */
    private UUID warehouseId;
    /** 이동지시서 ID */
    private UUID refId;
    /** 행위자 — 시스템 자동작업이면 SYSTEM_USER_ID. */
    private UUID userId;
    private List<Item> items;

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

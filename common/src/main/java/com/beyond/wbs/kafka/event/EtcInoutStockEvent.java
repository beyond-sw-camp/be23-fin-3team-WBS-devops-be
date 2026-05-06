package com.beyond.wbs.kafka.event;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * 기타 입출고 → 통계 이벤트 공통 클래스
 *
 * 토픽:
 *  - etcinout.created   : 지시서 생성
 *  - etcinout.approved  : 승인
 *  - etcinout.cancelled : 취소
 *  - etcinout.completed : 완료 (실제 재고 변동 발생 시점)
 *
 * 한 지시서는 in 또는 out 한 방향만 가지므로 direction 으로 분기 (입고/출고와 별도 카테고리).
 * 멀티테넌시: clientId 로 회사 분리.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EtcInoutStockEvent {

    private UUID clientId;
    private UUID warehouseId;
    private UUID refId;
    private UUID userId;
    /** "in" | "out" */
    private String direction;
    /** IoType enum 의 name() — sample_in / adjust_in / dispose_in / dispose_out / sample_out / adjust_out / etc_in / etc_out */
    private String ioType;
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

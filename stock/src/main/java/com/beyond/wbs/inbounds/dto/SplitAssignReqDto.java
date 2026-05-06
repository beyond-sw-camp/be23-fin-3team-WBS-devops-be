package com.beyond.wbs.inbounds.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 미배정 PlacementItem 의 수동 분할 위치 지정 요청.
 *
 * 자동 적치(검수 시) 가 한 번에 다 못 채워서 location=null 로 남은 항목을
 * 관리자가 여러 위치에 나눠서 배정할 때 사용한다. 단건 지정은 기존
 * PATCH /inbound/placements/{itemId}/assign-location 그대로 사용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SplitAssignReqDto {

    // 분할 배정 목록 — "어디에 얼마씩 담을지" 한 줄씩 들어옴
    // 예: [{locA, 50}, {locB, 70}] = "A위치에 50개, B위치에 70개"
    private List<Assignment> assignments;

    /** 한 위치에 얼마를 담을지 — (위치 ID + 수량) 한 묶음 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignment {
        private UUID locationId;  // 담을 위치
        private Integer qty;      // 담을 수량
    }
}

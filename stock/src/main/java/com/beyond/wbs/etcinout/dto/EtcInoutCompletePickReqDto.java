package com.beyond.wbs.etcinout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 작업자 — 기타출고 픽업 완료 요청.
 * 작업자는 위치 변경 권한 없음. 실제 픽업한 수량만 보고.
 * pickedQty < qty 이면 부족 출고로 기록 (status = shortage).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutCompletePickReqDto {

    @NotEmpty(message = "처리 품목이 없습니다.")
    @Valid
    private List<Item> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        @NotNull
        private UUID itemId;

        @NotNull
        @PositiveOrZero
        private Integer pickedQty;
    }
}

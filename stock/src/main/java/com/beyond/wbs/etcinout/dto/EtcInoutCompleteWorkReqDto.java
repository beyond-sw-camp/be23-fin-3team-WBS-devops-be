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
 * 모바일 작업자 — 기타입고 완료 요청 (한 카드에서 정상/불량/위치 한 번에 입력).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutCompleteWorkReqDto {

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
        private Integer processedQty;   // 정상 수량

        @NotNull
        @PositiveOrZero
        private Integer defectQty;      // 불량 수량

        // 작업자가 현장에서 변경한 적치 위치 (정상 수량용). null 이면 기존 locationId 유지.
        private UUID locationId;

        // 불량 사유 (defectQty > 0 인 경우)
        private String defectReason;

        // 불량을 둘 위치 (DEFECT zone). defectQty > 0 인 경우 필수.
        private UUID defectLocationId;
    }
}

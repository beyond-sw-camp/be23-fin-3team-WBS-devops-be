package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 수동 출고지시서 생성 전 — 미리보기 요청.
 *
 * 수주서 흐름의 {@link OutboundPreviewReqDto} 와 같은 응답({@link OutboundPreviewResDto})을 반환하지만,
 * SO 가 없으므로 출고처, 출고예정일, 품목 리스트를 직접 입력받는다.
 *
 * 이로써 수동 생성도 다음을 동일하게 활용할 수 있다:
 *  - 창고 × 품목 매트릭스 (currentAvailable / incoming / draftReserved / projected)
 *  - 추천 창고 (모든 품목이 SUFFICIENT 인 창고 중 가용 합 최대)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualPreviewReqDto {

    @NotNull(message = "출고처(storeId)는 필수입니다.")
    private UUID storeId;

    @NotNull(message = "출고예정일(scheduledDate)은 필수입니다.")
    private LocalDate scheduledDate;

    @NotEmpty(message = "품목을 1건 이상 입력해 주세요.")
    @Valid
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull(message = "상품(productId)은 필수입니다.")
        private UUID productId;

        @NotNull(message = "수량(qty)은 필수입니다.")
        @Positive(message = "수량(qty)은 1 이상이어야 합니다.")
        private Integer qty;
    }
}

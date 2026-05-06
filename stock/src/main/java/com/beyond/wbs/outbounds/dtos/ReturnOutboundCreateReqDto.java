package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 반품 출고 생성 요청 — POST /outbound/return.
 *
 * 비즈니스 시나리오:
 *   입고처에서 받은 상품 중 불량/오배송이 발견되어 입고처로 다시 돌려보내는 흐름.
 *   원본 입고지시서를 매칭해 누적 반품량을 추적한다.
 *
 * 검증:
 *   - inboundOrderId 가 같은 회사여야 함 (Service)
 *   - supplierId 가 원본 입고지시서의 입고처와 일치해야 함 (Service)
 *   - 누적 반품량 + 새 요청 ≤ InboundOrderItems.receivedQty (Service)
 */
@Getter
@Setter
@NoArgsConstructor
public class ReturnOutboundCreateReqDto {

    @NotNull
    private UUID inboundOrderId;       // 원본 입고지시서

    @NotNull
    private UUID warehouseId;          // 출고할 창고 (= 그 입고가 들어간 창고)

    @NotNull
    private UUID supplierId;           // 반품 대상 입고처 (원본 입고처와 동일해야 함)

    @NotBlank
    private String reason;             // 반품 사유 (필수)

    private String returnMethod;       // "courier" | "pickup" — 옵셔널 (현재 도메인 미저장, 향후 확장)
    private String note;               // 비고 (옵셔널)
    private LocalDate scheduledDate;   // 출고 예정일 (옵셔널, null 이면 오늘)

    @Valid
    @NotEmpty
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private UUID productId;

        @NotNull
        @Positive
        private Integer qty;
    }
}

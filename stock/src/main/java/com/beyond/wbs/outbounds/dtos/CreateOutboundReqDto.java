package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 수동 출고지시서 생성 요청.
 *
 * ERP 수주서 없이 관리자가 직접 거래처/창고/품목을 입력해 출고지시서를 만들 때 사용.
 * 생성된 OB 는 draft 상태로 저장되며, 이후 흐름 (승인 → reserve → wave) 은 ERP 수주서 흐름과 동일.
 *
 * SO 연결이 없으므로 OutboundSalesOrderLinks 는 만들어지지 않는다.
 * 진행률 페이지에서는 SO 연관 없는 standalone OB 로 표시된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOutboundReqDto {

    @NotNull(message = "거래처(storeId)는 필수입니다.")
    private UUID storeId;

    @NotNull(message = "출고 창고(warehouseId)는 필수입니다.")
    private UUID warehouseId;

    @NotNull(message = "출고 예정일(scheduledDate)은 필수입니다.")
    private LocalDate scheduledDate;

    private String shippingAddress;   // 배송지 (선택)

    private String note;              // 비고 (선택)

    @NotEmpty(message = "출고 품목을 1건 이상 입력해 주세요.")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull(message = "상품(productId)은 필수입니다.")
        private UUID productId;

        @NotNull(message = "수량(qty)은 필수입니다.")
        @Positive(message = "수량(qty)은 1 이상이어야 합니다.")
        private Integer qty;

        // 출고 단가 (선택 — 미입력 시 0)
        private BigDecimal unitPrice;
    }
}

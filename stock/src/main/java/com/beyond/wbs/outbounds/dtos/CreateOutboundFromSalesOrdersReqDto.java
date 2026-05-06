package com.beyond.wbs.outbounds.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * ERP 수주서 → 출고지시서 생성 요청.
 *
 * 호출 단위는 "한 출고처(store)" — FE 가 미리보기 결과를 보고 출고처별로 분할해서 호출한다.
 * 한 호출 안에 여러 창고가 들어가면(분할) 창고 수만큼 OB 가 생성된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOutboundFromSalesOrdersReqDto {

    /** 같은 출고처(store)의 수주서 ID 목록 — 모두 같은 ship_date 여야 함 */
    @NotEmpty(message = "수주서 ID 목록은 비어 있을 수 없습니다.")
    private List<UUID> salesOrderIds;

    /** 창고별 분배 — 1개면 단일 창고, 여러 개면 분할 출고 (창고 수만큼 OB 생성) */
    @NotEmpty(message = "창고별 분배 정보는 1개 이상이어야 합니다.")
    private List<WarehouseAllocation> warehouseAllocations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseAllocation {
        @NotNull(message = "창고 ID는 필수입니다.")
        private UUID warehouseId;

        @NotEmpty(message = "품목별 분배 정보는 1개 이상이어야 합니다.")
        private List<ProductAllocation> productAllocations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAllocation {
        @NotNull(message = "상품 ID는 필수입니다.")
        private UUID productId;

        @Positive(message = "수량은 1 이상이어야 합니다.")
        private int qty;
    }
}

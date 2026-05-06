package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 출고지시서 생성 전 미리보기 응답.
 *
 * shipDate 기준으로 출고처별로 묶고, 각 출고처 묶음 안에서 품목별로
 * 창고별 가용재고를 비교해서 보여준다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundPreviewResDto {
    /** 선택된 SO 의 출고예정일 (모두 같아야 함 — 다르면 검증 실패) */
    private LocalDate shipDate;

    /** 출고처별 묶음 — 한 출고처당 출고지시서 1장(혹은 분할 시 N장) 생성 */
    private List<StoreGroup> storeGroups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreGroup {
        private UUID storeId;
        private String storeName;
        /** 이 출고처에 묶인 SO ID 목록 */
        private List<UUID> salesOrderIds;
        /** 품목별 필요량 + 창고별 가용 매트릭스 */
        private List<ProductRequirement> requirements;
        /** 모든 품목을 단일 창고에서 충당 가능한 창고 ID — 없으면 null (분할 필요) */
        private UUID recommendedWarehouseId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRequirement {
        private UUID productId;
        private String productName;
        private String sku;
        /** 이 출고처 묶음에서 이 품목 총 필요 수량 (선택된 SO 들의 라인 합) */
        private int requiredQty;
        /** 창고별 가용 정보 */
        private List<WarehouseStock> warehouses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseStock {
        private UUID warehouseId;
        private String warehouseName;
        /** 현재 즉시 출고 가능한 수량 (inventory.availableQty 합) */
        private int currentAvailableQty;
        /** shipDate 까지 들어올 입고예정 수량 (approved/received/placing 합) */
        private int incomingQty;
        /** shipDate 까지 다른 draft 출고가 가져갈 수량 (음수처럼 작용) */
        private int draftReservedQty;
        /** 예상 가용 = current + incoming - draftReserved */
        private int projectedQty;
        /** SUFFICIENT(충분) / SHORTAGE(부족) / NONE(없음) */
        private StockStatus status;
    }

    public enum StockStatus {
        SUFFICIENT,
        SHORTAGE,
        NONE
    }
}

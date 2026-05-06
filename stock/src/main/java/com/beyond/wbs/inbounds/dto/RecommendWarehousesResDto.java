package com.beyond.wbs.inbounds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 발주서별 창고 추천 응답.
 *
 * 추천 점수 = 협력사 전용 랙 수 × 10  +  카테고리 매칭 zone 수 × 5  +  활성 NORMAL 창고 가산점 × 1
 *
 *  - 1차: 협력사 전용 랙 보유 (적치 단계 위치 추천과 정합)
 *  - 2차: PO 품목들의 카테고리 zone 보유 (전자기기 PO → 전자기기존 보유 창고)
 *  - 3차: 활성 NORMAL 창고면 가산 (입고는 항상 가능해야 하므로 동점이어도 후보로 노출)
 *
 * 사용 패턴 (수주서 → 출고지시서 흐름과 동일):
 *  - recommendedWarehouseId 는 단순 추천 힌트. FE 는 강조 표시만 하고, 사용자가 후보 중 자유 선택.
 *  - candidates 는 활성 NORMAL 창고 전체. 점수 0 인 창고도 포함되어 사용자가 선택 가능.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RecommendWarehousesResDto {

    private List<PoRecommendation> recommendations;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class PoRecommendation {
        private UUID purchaseOrderId;
        private String poNo;
        private UUID supplierId;
        private String supplierName;
        private LocalDate scheduledDate;
        /** 발주서 품목 요약 — FE 미리보기 표시용 */
        private List<PoItem> items;
        /** 추천 창고 — 점수 1위. 활성 NORMAL 창고가 있으면 채워짐. 자동 선택 X (FE 가 강조만). */
        private UUID recommendedWarehouseId;
        /** 점수 내림차순 정렬된 창고 후보 목록 — 사용자가 자유 선택 */
        private List<WarehouseCandidate> candidates;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class PoItem {
        private UUID productId;
        private String sku;
        private String productName;
        private Integer qty;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class WarehouseCandidate {
        private UUID warehouseId;
        private String warehouseCode;
        private String warehouseName;
        /** 이 협력사 전용 랙이 그 창고에 몇 개 있는지 */
        private int supplierRackCount;
        /** PO 품목들의 카테고리에 매칭되는 zone 이 그 창고에 몇 개 있는지 */
        private int categoryZoneCount;
        /** 그 창고의 전체 활성 location 수 */
        private int totalLocations;
        /** 비어있는 (inventory 행 없는) location 수 — 점수에는 안 들어가고 정보 표시용 */
        private int emptyLocations;
        /** 추천 점수 (높을수록 추천) */
        private int fitScore;
        /** 추천 사유 (한글 문구) */
        private String reason;
    }
}

package com.beyond.wbs.inventory.service;

/**
 * 적치 위치 추천 목적
 *
 * 입고 검수 후 상품이 "어떤 성격으로" 적치되느냐에 따라 추천 창고/구역이 달라진다.
 */
public enum PlacementPurpose {
    /** 정상 입고 — 해당 창고의 카테고리 Zone + 협력사 랙 매칭 */
    NORMAL,
    /** 검수 불량품 — 같은 창고의 DEFECT zone (반품창고 이동 전 임시 보관) */
    DEFECT,
    /** 반품 입고 — warehouseId 는 이미 반품창고로 지정됨. STORAGE zone 사용 */
    RETURN,
    /** 폐기 입고 — warehouseId 는 이미 폐기창고로 지정됨. STORAGE zone 사용 */
    DISPOSAL
}

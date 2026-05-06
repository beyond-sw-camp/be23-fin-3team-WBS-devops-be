package com.beyond.wbs.inventory.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * 상품 ID 리스트 요청 — 실사지시서 등 inventory 도메인 라인 필터링 시 사용.
 * productIds 가 null/빈 리스트면 전체 라인 반환.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductIdsReqDto {
    private List<UUID> productIds;
}

package com.beyond.wbs.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 모바일 — 랙 QR 재고 조회 응답.
 * 랙 1개 + 그 안의 로케이션 리스트.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRackInventoryResDto {
    private String rackCode;
    private List<MobileRackLocationResDto> locations;
}

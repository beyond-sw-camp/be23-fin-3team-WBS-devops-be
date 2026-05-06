package com.beyond.wbs.layout.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 구역 레이아웃 일괄 저장 요청.
 *
 * 정책:
 *  - items 의 각 항목은 zoneId 기준으로 upsert
 *  - deletedZoneIds 에 있는 zoneId 의 ZoneLayout 행을 삭제
 *  - 요청에 없는 다른 ZoneLayout 행은 그대로 유지 (시나리오 B 사고 방지)
 */
@Getter
@NoArgsConstructor
public class ZoneLayoutSaveDto {

    @NotNull
    private UUID warehouseId;

    @NotNull
    @Valid
    private List<ZoneLayoutItem> items;

    private List<UUID> deletedZoneIds;

    @Getter
    @NoArgsConstructor
    public static class ZoneLayoutItem {
        @NotNull private UUID zoneId;
        @NotNull private Integer posX;
        @NotNull private Integer posY;
        @NotNull private Integer width;
        @NotNull private Integer height;
        private Integer rotation;
        private String color;
        private Integer sortOrder;
    }
}

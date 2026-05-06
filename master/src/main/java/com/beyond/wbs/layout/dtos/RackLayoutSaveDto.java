package com.beyond.wbs.layout.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 랙 레이아웃 일괄 저장 요청.
 *
 * 정책: items 는 rackId 기준 upsert, deletedRackIds 는 RackLayout 행 삭제,
 *      그 외 행은 유지 (다중 사용자 안전).
 */
@Getter
@NoArgsConstructor
public class RackLayoutSaveDto {

    @NotNull
    private UUID zoneId;

    @NotNull
    private UUID warehouseId;

    @NotNull
    @Valid
    private List<RackLayoutItem> items;

    private List<UUID> deletedRackIds;

    @Getter
    @NoArgsConstructor
    public static class RackLayoutItem {
        @NotNull private UUID rackId;
        @NotNull private Integer posX;
        @NotNull private Integer posY;
        @NotNull private Integer width;
        @NotNull private Integer height;
        private Integer rotation;   // 0/90/180/270
        private String color;
    }
}

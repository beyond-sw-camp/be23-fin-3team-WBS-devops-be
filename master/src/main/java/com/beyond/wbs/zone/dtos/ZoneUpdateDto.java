package com.beyond.wbs.zone.dtos;

import com.beyond.wbs.zone.domain.ZoneType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 구역 부분 수정 DTO.
 * null 필드는 변경하지 않음.
 * code 는 수정 불가 (PK 성격).
 */
@Getter
@NoArgsConstructor
public class ZoneUpdateDto {

    @Size(max = 100)
    private String name;

    private ZoneType zoneType;

    private Integer sortOrder;

    // 카테고리 변경 시 — 적치 추천 매핑 변경 의미
    private UUID categoryId;
}

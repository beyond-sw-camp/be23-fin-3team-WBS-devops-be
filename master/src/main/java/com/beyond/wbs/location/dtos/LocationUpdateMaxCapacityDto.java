package com.beyond.wbs.location.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로케이션 maxCapacity 변경 요청.
 *
 * - null 허용: "무제한/미정"으로 되돌리는 경로.
 * - 0 이하: 서비스에서 거부 (수용량 0 은 비활성화로 처리).
 */
@Getter
@NoArgsConstructor
public class LocationUpdateMaxCapacityDto {
    private Integer maxCapacity;
}

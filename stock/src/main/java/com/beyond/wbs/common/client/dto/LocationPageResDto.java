package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * master 모듈의 GET /location/list 응답이 Page 형태라
 * content 필드만 꺼내 쓰기 위한 래퍼 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class LocationPageResDto {
    private List<LocationResDto> content;
}

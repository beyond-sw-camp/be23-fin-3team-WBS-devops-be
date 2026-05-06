package com.beyond.wbs.code.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 지역 코드 (창고·구역 코드 생성 시 사용)
 */
@Getter
@AllArgsConstructor
public enum RegionCode {
    SEL("SEL", "서울"),
    PUS("PUS", "부산"),
    DAE("DAE", "대구"),
    ICN("ICN", "인천"),
    GWJ("GWJ", "광주"),
    DJN("DJN", "대전"),
    USN("USN", "울산");

    private final String code;
    private final String description;
}

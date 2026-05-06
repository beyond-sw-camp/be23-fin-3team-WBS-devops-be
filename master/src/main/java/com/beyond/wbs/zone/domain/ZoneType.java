package com.beyond.wbs.zone.domain;

public enum ZoneType {
    STORAGE,   // 보관존 (정상 상품 보관)
    INBOUND,   // 입고존
    OUTBOUND,  // 출고존
    DEFECT     // 불량 임시 보관존 (검수 불량품을 반품창고로 이동하기 전 임시 보관)
}
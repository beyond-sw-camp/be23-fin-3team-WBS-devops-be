package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * master 모듈의 StoreResDto에 매핑되는 Feign 응답 DTO
 * 출고처(지점) 정보
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class StoreResDto {
    private UUID id;
    private String name;
    private String bizNo;
    private String code;
    private String ceoName;
    private String tel;
    private String email;
    private String address;
    private Boolean isActive;
    /** 자동 웨이브 생성 대상 여부 — WaveScheduler 가 이 값으로 필터 */
    private Boolean autoWaveEnabled;
}

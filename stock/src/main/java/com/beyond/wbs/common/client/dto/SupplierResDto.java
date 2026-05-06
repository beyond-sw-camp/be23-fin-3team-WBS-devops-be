package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 협력사(입고처) 정보 — Feign 응답 역직렬화용 DTO
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SupplierResDto {
    // 협력사 ID (UUID)
    private UUID id;

    // 협력사명
    private String name;

    // 사업자등록번호
    private String bizNo;

    // 협력사 코드
    private String code;

    // 대표자명
    private String ceoName;

    // 연락처
    private String tel;

    // 이메일
    private String email;

    // 주소
    private String address;

    // 활성 여부
    private Boolean isActive;
}

package com.beyond.wbs.common.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * account-service의 Client 정보 응답 DTO (Feign용).
 *
 * /developer/clients/{id} 엔드포인트가 ClientDetailResDto(name, bizNo, users 등)를 반환하지만,
 * stock에서는 이름·사업자번호만 필요하므로 나머지는 무시한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientResDto {
    private UUID id;
    private String name;
    private String bizNo;
}

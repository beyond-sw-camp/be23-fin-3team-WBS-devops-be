package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입고 요청 메일 미리보기 — 프론트 폼 초기값.
 *
 * <p>운영자는 이 값을 받아 폼에 채운 뒤 자유롭게 편집하고 [전송] 클릭.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRequestPreviewResDto {
    private String recipient;
    private String senderName;
    private String subject;
    private String body;
}
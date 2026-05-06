package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기타출고 [입고 요청] 클릭 시 응답 — 운영자 PC의 메일 클라이언트로 열기 위한 양식.
 *
 * <p>프론트는 mailtoUrl 을 그대로 {@code window.location.href} 로 던지거나
 * 미리보기 모달에 to/subject/body 를 보여준 뒤 [메일 작성하기] 클릭 시 mailtoUrl 발사.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRequestMailtoResDto {

    private String to;          // 받는 사람 (현재 고정 — dldmsrudq@gmail.com)
    private String subject;     // 메일 제목
    private String body;        // 메일 본문 (plain text)
    private String mailtoUrl;   // mailto:to?subject=...&body=...  (URL 인코딩됨)
}

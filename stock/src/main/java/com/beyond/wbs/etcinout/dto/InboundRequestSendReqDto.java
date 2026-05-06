package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * [메일 전송] 요청 — 프론트가 운영자가 편집한 값을 그대로 보냄.
 *
 * <p>모든 필드는 옵셔널. 비워두면 백엔드가 기본값(yml + 자동 본문) 사용.
 *
 * <p>프론트 흐름:
 *   1. GET /etc-inout/{id}/inbound-request-preview 로 기본값 미리보기 받음
 *   2. 폼에 초기값으로 박고 운영자가 편집
 *   3. 편집된 값을 이 DTO 로 보냄 → 백엔드 그대로 발송
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRequestSendReqDto {

    // 운영자가 폼에서 편집 가능한 필드 — null 이면 백엔드 기본값 사용
    private String recipient;
    private String senderName;
    private String subject;
    private String body;

    // (구버전 호환) 부족 품목 정보 — body 가 null 일 때 자동 본문 구성에 사용
    private List<ShortageItem> shortageItems;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShortageItem {
        private UUID productId;
        private String productName;
        private Integer requested;
        private Integer available;
        private Integer shortage;
    }
}
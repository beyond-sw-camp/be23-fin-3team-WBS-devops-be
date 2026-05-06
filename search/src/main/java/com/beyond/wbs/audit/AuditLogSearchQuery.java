package com.beyond.wbs.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 감사 로그 검색 조건.
 */
@Getter
@Builder
public class AuditLogSearchQuery {
    private String clientId;       // 필수 — 멀티테넌시
    private String keyword;        // 전문 검색 (userName, requestUri, entityName)
    private String serviceName;    // exact 필터 (account-service/master-service/stock-service)
    private String userId;         // exact 필터
    private String action;         // exact 필터 (생성/조회/승인/삭제...)
    private List<String> includeActions;  // 화이트리스트 — 이 값들 중 하나면 포함
    private List<String> excludeActions;  // 블랙리스트 — 이 값들 중 하나면 제외 ("내 활동" 사이드바에서 조회 노이즈 컷)
    private String httpMethod;     // exact 필터
    private String entityName;     // exact 필터 (inbound/outbound...)
    private Integer responseStatus;
    private String statusGroup;     // 2xx/4xx/5xx
    private Long minDurationMs;
    private LocalDateTime from;
    private LocalDateTime to;
    private int page;
    private int size;
}

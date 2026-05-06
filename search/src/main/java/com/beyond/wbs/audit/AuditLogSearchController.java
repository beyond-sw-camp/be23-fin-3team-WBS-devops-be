package com.beyond.wbs.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
/**
 * 감사 로그 ES 검색 컨트롤러.
 *
 * 기존 DB 조회 (/audit-logs)와 별도로,
 * Elasticsearch 전문 검색을 제공한다.
 *
 * 예) GET /audit-logs/search?keyword=USB&action=승인&from=2026-04-01
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogSearchController {

    private final AuditLogSearchService searchService;
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping("/search")
    public ResponseEntity<Page<AuditLogSearchResDto>> search(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) List<String> includeActions,
            @RequestParam(required = false) List<String> excludeActions,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false) String statusGroup,
            @RequestParam(required = false) Long minDurationMs,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        AuditLogSearchQuery query = AuditLogSearchQuery.builder()
                .clientId(clientId)
                .keyword(keyword)
                .serviceName(serviceName)
                .userId(userId)
                .action(action)
                .includeActions(includeActions)
                .excludeActions(excludeActions)
                .httpMethod(httpMethod)
                .entityName(entityName)
                .responseStatus(responseStatus)
                .statusGroup(statusGroup)
                .minDurationMs(minDurationMs)
                .from(from != null ? from.atStartOfDay() : null)
                .to(to != null ? to.plusDays(1).atStartOfDay() : null)
                .page(safePage)
                .size(safeSize)
                .build();

        Page<AuditLogSearchResDto> results = searchService.search(query)
                .map(AuditLogSearchResDto::from);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<AuditLogSuggestResDto>> suggest(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "8") int size) {

        int safeSize = Math.min(Math.max(size, 1), 20);
        return ResponseEntity.ok(searchService.suggest(clientId, keyword, safeSize));
    }

}

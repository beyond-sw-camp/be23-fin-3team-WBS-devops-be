package com.beyond.wbs.audit;

import com.beyond.wbs.kafka.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;

/**
 * 감사 로그 ES 색인/검색 서비스.
 *
 * - index(): Kafka 이벤트 → ES 문서 변환 → 색인
 * - search(): 복합 조건 전문 검색
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogSearchService {

    private final AuditLogSearchRepository repository;
    private final ElasticsearchOperations esOperations;

    private co.elastic.clients.elasticsearch._types.query_dsl.Query exactTextFilter(String field, String value) {
        return QueryBuilders.bool(b -> b
                .should(QueryBuilders.term(t -> t.field(field).value(value)))
                .should(QueryBuilders.term(t -> t.field(field + ".keyword").value(value)))
                .minimumShouldMatch("1"));
    }

    /**
     * 다중 값 중 하나라도 매칭되면 true (OR).
     * text 와 keyword 양쪽 매핑 모두 허용해 분석기 차이로 누락되지 않게 함.
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.Query exactTextAnyOf(String field, List<String> values) {
        return QueryBuilders.bool(b -> {
            for (String v : values) {
                if (v == null || v.isBlank()) continue;
                b.should(QueryBuilders.term(t -> t.field(field).value(v)));
                b.should(QueryBuilders.term(t -> t.field(field + ".keyword").value(v)));
            }
            return b.minimumShouldMatch("1");
        });
    }

    private String buildSuggestText(AuditLogSearchDocument doc) {
        return Stream.of(
                        doc.getUserName(),
                        doc.getServiceName(),
                        doc.getAction(),
                        doc.getEntityName(),
                        doc.getRequestUri(),
                        doc.getIpAddress())
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private AuditLogSearchDocument withSuggestText(AuditLogSearchDocument doc) {
        return AuditLogSearchDocument.builder()
                .id(doc.getId())
                .clientId(doc.getClientId())
                .userId(doc.getUserId())
                .serviceName(doc.getServiceName())
                .userName(doc.getUserName())
                .action(doc.getAction())
                .httpMethod(doc.getHttpMethod())
                .requestUri(doc.getRequestUri())
                .entityName(doc.getEntityName())
                .responseStatus(doc.getResponseStatus())
                .ipAddress(doc.getIpAddress())
                .durationMs(doc.getDurationMs())
                .suggestText(buildSuggestText(doc))
                .createdAt(doc.getCreatedAt())
                .requestBody(doc.getRequestBody())
                .build();
    }

    /**
     * Kafka 이벤트를 ES에 색인.
     */
    public void index(AuditLogEvent event) {
        AuditLogSearchDocument doc = AuditLogSearchDocument.builder()
                .id(event.getId() != null ? event.getId().toString() : null)
                .clientId(event.getClientId() != null ? event.getClientId().toString() : null)
                .userId(event.getUserId() != null ? event.getUserId().toString() : null)
                .serviceName(event.getServiceName())
                .userName(event.getUserName())
                .action(event.getAction())
                .httpMethod(event.getHttpMethod())
                .requestUri(event.getRequestUri())
                .entityName(event.getEntityName())
                .responseStatus(event.getResponseStatus())
                .ipAddress(event.getIpAddress())
                .durationMs(event.getDurationMs())
                .createdAt(event.getCreatedAt() != null ? event.getCreatedAt().toString() : null)
                .requestBody(event.getRequestBody())
                .build();

        repository.save(withSuggestText(doc));
    }

    /**
     * 복합 조건 전문 검색.
     *
     * keyword → userName, requestUri, serviceName, entityName, action, ipAddress 에서 multi_match
     * userId, serviceName, action, httpMethod, entityName, responseStatus → filter
     * statusGroup, minDurationMs, from/to → range filter
     * from/to → range 필터
     * clientId → 필수 term 필터 (멀티테넌시)
     */
    public Page<AuditLogSearchDocument> search(AuditLogSearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 필수: clientId
        bool.filter(QueryBuilders.term(t -> t.field("clientId").value(query.getClientId())));

        // 선택: keyword 전문 검색
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            bool.must(QueryBuilders.multiMatch(m -> m
                    .fields("userName^2", "requestUri", "serviceName", "entityName", "action", "ipAddress")
                    .query(query.getKeyword())));
        }

        // 선택: serviceName exact
        if (query.getServiceName() != null && !query.getServiceName().isBlank()) {
            bool.filter(exactTextFilter("serviceName", query.getServiceName()));
        }

        // 선택: userId exact
        if (query.getUserId() != null && !query.getUserId().isBlank()) {
            bool.filter(QueryBuilders.term(t -> t.field("userId").value(query.getUserId())));
        }

        // 선택: action exact (단일)
        if (query.getAction() != null && !query.getAction().isBlank()) {
            bool.filter(QueryBuilders.term(t -> t.field("action").value(query.getAction())));
        }

        // 선택: includeActions 화이트리스트 — 이 값들 중 하나면 포함
        if (query.getIncludeActions() != null && !query.getIncludeActions().isEmpty()) {
            bool.filter(exactTextAnyOf("action", query.getIncludeActions()));
        }

        // 선택: excludeActions 블랙리스트 — 이 값들 중 하나면 제외 ("내 활동"의 조회 노이즈 컷)
        if (query.getExcludeActions() != null && !query.getExcludeActions().isEmpty()) {
            bool.mustNot(exactTextAnyOf("action", query.getExcludeActions()));
        }

        // 선택: httpMethod exact
        if (query.getHttpMethod() != null && !query.getHttpMethod().isBlank()) {
            bool.filter(QueryBuilders.term(t -> t.field("httpMethod").value(query.getHttpMethod())));
        }

        // 선택: entityName exact
        if (query.getEntityName() != null && !query.getEntityName().isBlank()) {
            bool.filter(exactTextFilter("entityName", query.getEntityName()));
        }

        // 선택: responseStatus exact
        if (query.getResponseStatus() != null) {
            bool.filter(QueryBuilders.term(t -> t.field("responseStatus").value(query.getResponseStatus())));
        }

        // 선택: responseStatus 대역
        if (query.getStatusGroup() != null && !query.getStatusGroup().isBlank()) {
            int start = switch (query.getStatusGroup()) {
                case "2xx" -> 200;
                case "3xx" -> 300;
                case "4xx" -> 400;
                case "5xx" -> 500;
                default -> 0;
            };
            if (start > 0) {
                int end = start + 100;
                bool.filter(QueryBuilders.range(r -> r
                        .number(n -> n.field("responseStatus").gte((double) start).lt((double) end))));
            }
        }

        // 선택: 처리시간 하한
        if (query.getMinDurationMs() != null) {
            bool.filter(QueryBuilders.range(r -> r
                    .number(n -> n.field("durationMs").gte(query.getMinDurationMs().doubleValue()))));
        }

        // 선택: 날짜 범위
        if (query.getFrom() != null || query.getTo() != null) {
            bool.filter(QueryBuilders.range(r -> r
                    .date(d -> {
                        d.field("createdAt");
                        if (query.getFrom() != null) d.gte(query.getFrom().toString());
                        if (query.getTo() != null) d.lt(query.getTo().toString());
                        return d;
                    })));
        }

        PageRequest pageable = PageRequest.of(
                query.getPage(),
                query.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Query searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withPageable(pageable)
                .build();

        SearchHits<AuditLogSearchDocument> hits = esOperations.search(
                searchQuery, AuditLogSearchDocument.class);

        List<AuditLogSearchDocument> result = new ArrayList<>();
        for (SearchHit<AuditLogSearchDocument> hit : hits) {
            result.add(hit.getContent());
        }
        return new PageImpl<>(result, pageable, hits.getTotalHits());
    }

    public List<AuditLogSuggestResDto> suggest(String clientId, String keyword, int size) {
        if (keyword == null || keyword.isBlank()) return List.of();

        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.filter(QueryBuilders.term(t -> t.field("clientId").value(clientId)));
        bool.must(QueryBuilders.multiMatch(m -> m
                .fields("suggestText", "suggestText._2gram", "suggestText._3gram")
                .query(keyword)
                .type(TextQueryType.BoolPrefix)));

        Query suggestQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withPageable(PageRequest.of(0, Math.min(size * 5, 50)))
                .build();

        SearchHits<AuditLogSearchDocument> hits = esOperations.search(
                suggestQuery, AuditLogSearchDocument.class);

        Map<String, AuditLogSuggestResDto> result = new LinkedHashMap<>();
        for (SearchHit<AuditLogSearchDocument> hit : hits) {
            AuditLogSearchDocument doc = hit.getContent();
            addSuggestion(result, "user", "사용자", doc.getUserName(), size);
            addSuggestion(result, "service", "서비스", doc.getServiceName(), size);
            addSuggestion(result, "action", "작업", doc.getAction(), size);
            addSuggestion(result, "entity", "대상", doc.getEntityName(), size);
            addSuggestion(result, "path", "경로", doc.getRequestUri(), size);
            addSuggestion(result, "ip", "IP", doc.getIpAddress(), size);
            if (result.size() >= size) break;
        }
        return new ArrayList<>(result.values());
    }

    private void addSuggestion(Map<String, AuditLogSuggestResDto> result,
                               String type,
                               String labelPrefix,
                               String value,
                               int size) {
        if (value == null || value.isBlank() || result.size() >= size) return;
        String key = type + ":" + value;
        result.putIfAbsent(key, AuditLogSuggestResDto.builder()
                .type(type)
                .label(labelPrefix + ": " + value)
                .value(value)
                .build());
    }
}

package com.beyond.wbs.audit;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data Elasticsearch Repository.
 * 기본 CRUD + 페이징 자동 지원.
 * 복합 검색은 AuditLogSearchService 에서 직접 쿼리 구성.
 */
public interface AuditLogSearchRepository extends ElasticsearchRepository<AuditLogSearchDocument, String> {
}

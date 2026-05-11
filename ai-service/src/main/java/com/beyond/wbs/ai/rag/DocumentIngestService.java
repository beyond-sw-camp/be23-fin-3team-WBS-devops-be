package com.beyond.wbs.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final VectorStore vectorStore;
    private final KnowledgeSectionParser sectionParser;
    private final RagResponseCacheService responseCacheService;

    /**
     * 원문 텍스트를 섹션 인식 방식으로 인덱싱.
     *   1) 일반 텍스트 제목 기준 섹션 분할 (section_path 메타·content 프리픽스 부착)
     *   2) 섹션이 커서 토큰 한도 초과 시 TokenTextSplitter로 2차 분할 (메타 보존)
     *   3) bge-m3 임베딩 후 vector_store 저장
     */
    public int ingestText(String content, Map<String, Object> metadata) {
        List<Document> sections = sectionParser.parse(content, metadata);

        // 헤딩 없는 일반 텍스트 fallback
        if (sections.isEmpty()) {
            sections = List.of(new Document(content, metadata == null ? Map.of() : metadata));
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(sections);

        // --- Upsert: 같은 source의 기존 청크 삭제 후 새로 삽입 ---
        String source = extractSource(metadata);
        if (source != null) {
            deleteBySource(source);
        }

        log.info("ingesting: source={}, sections={}, chunks={}",
                source, sections.size(), chunks.size());

        vectorStore.add(chunks);
        evictResponseCacheBySource(source);
        return chunks.size();
    }

    private String extractSource(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object v = metadata.get("source");
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private void deleteBySource(String source) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("source", source).build());
        log.info("upsert: deleted existing chunks (source={})", source);
    }

    private void evictResponseCacheBySource(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        try {
            responseCacheService.evictBySource(source);
            if (!"RAG".equalsIgnoreCase(source.trim())) {
                responseCacheService.evictBySource("RAG");
            }
        } catch (Exception e) {
            log.warn("[AI_RAG_CACHE_EVICT_SKIP] source={}, reason={}", source, e.getMessage());
        }
    }
}

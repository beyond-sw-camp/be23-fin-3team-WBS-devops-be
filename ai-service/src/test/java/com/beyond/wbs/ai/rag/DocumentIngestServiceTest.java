package com.beyond.wbs.ai.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentIngestServiceTest {

    private VectorStore vectorStore;
    private KnowledgeSectionParser sectionParser;
    private RagResponseCacheService responseCacheService;
    private DocumentIngestService documentIngestService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        sectionParser = mock(KnowledgeSectionParser.class);
        responseCacheService = mock(RagResponseCacheService.class);
        documentIngestService = new DocumentIngestService(vectorStore, sectionParser, responseCacheService);
    }

    @Test
    void evictsResponseCacheAfterKnowledgeSourceIsIndexed() {
        Map<String, Object> metadata = Map.of("source", "wms-ui-guide", "category", "wms-ui-guide");
        doReturn(List.of(new Document("화면 경로 안내", metadata)))
                .when(sectionParser)
                .parse(any(String.class), eq(metadata));

        documentIngestService.ingestText("문서: WMS 화면 사용 가이드\n내용", metadata);

        verify(vectorStore).add(anyList());
        verify(responseCacheService).evictBySource("wms-ui-guide");
        verify(responseCacheService).evictBySource("RAG");
    }
}

package com.beyond.wbs.ai.rag;

import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceTest {

    private OpenAiChatGateway openAiChatGateway;
    private VectorStore vectorStore;
    private RagResponseCacheService responseCacheService;
    private RagChatService ragChatService;

    @BeforeEach
    void setUp() {
        openAiChatGateway = mock(OpenAiChatGateway.class);
        vectorStore = mock(VectorStore.class);
        responseCacheService = mock(RagResponseCacheService.class);
        ragChatService = new RagChatService(openAiChatGateway, vectorStore, responseCacheService);
        ReflectionTestUtils.setField(ragChatService, "searchTopK", 8);
        ReflectionTestUtils.setField(ragChatService, "searchSimilarityThreshold", 0.35);
        ReflectionTestUtils.setField(ragChatService, "minAnswerSimilarity", 0.38);
    }

    @Test
    void retriesWithoutCategoryWhenCategoryResultIsWeak() {
        when(responseCacheService.findSimilar(anyString(), anyString())).thenReturn(Optional.empty());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("불량 처리 절차", "wms-exception-handling", 0.37)))
                .thenReturn(List.of(document("불량 증빙 사진은 문서/증빙 관리에서 확인합니다.", "wms-ui-guide", 0.82)));
        when(openAiChatGateway.complete(anyString(), anyString()))
                .thenReturn("불량 사진은 문서/증빙 관리 > 불량 증빙에서 확인합니다.");

        String answer = ragChatService.ask("불량사진 어디서봐", "wms-exception-handling", List.of());

        assertEquals("불량 사진은 문서/증빙 관리 > 불량 증빙에서 확인합니다.", answer);
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        verify(openAiChatGateway).complete(anyString(), anyString());
    }

    @Test
    void returnsFixedAnswerWithoutLlmWhenNoEvidenceIsFound() {
        when(responseCacheService.findSimilar(anyString(), anyString())).thenReturn(Optional.empty());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String answer = ragChatService.ask("없는 기능 알려줘", "wms-ui-guide", List.of());

        assertEquals("죄송합니다. 요청하신 질문을 처리할 수 없습니다.", answer);
        verify(openAiChatGateway, never()).complete(anyString(), anyString());
        verify(responseCacheService, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void enrichesUiGuideRetrievalQuestionWithBusinessCategoryTerms() {
        when(responseCacheService.findSimilar(anyString(), anyString())).thenReturn(Optional.empty());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("출고 지시서는 출고 관리 > 출고 지시서 화면에서 생성합니다.", "wms-ui-guide", 0.39)));
        when(openAiChatGateway.complete(anyString(), anyString()))
                .thenReturn("출고 지시서는 출고 관리 > 출고 지시서 화면에서 생성합니다.");

        String answer = ragChatService.ask("출고지시서 어디서 만들어?", "RAG", List.of());

        assertEquals("출고 지시서는 출고 관리 > 출고 지시서 화면에서 생성합니다.", answer);
        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals("출고지시서 어디서 만들어? 출고 지시서 화면 출고 관리 출고 지시서 생성 등록 만들기 메뉴 화면 경로 화면 경로 메뉴 위치 생성 등록",
                captor.getValue().getQuery());
    }

    @Test
    void fallsBackToErrorCodeEvidenceWhenLlmAnswerGenerationFails() {
        when(responseCacheService.findSimilar(anyString(), anyString())).thenReturn(Optional.empty());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("""
                        [섹션: WMS 화면 사용 가이드 > WMS 오류 코드 기준 및 조치 가이드]
                        - INB-409: 입고 검수 완료 불가. 검수 수량 불일치, 불량 증빙 누락, 이미 완료된 입고 지시서 상태를 확인한다.
                        """, "wms-ui-guide", 0.49)));
        when(openAiChatGateway.complete(anyString(), anyString()))
                .thenThrow(new IllegalStateException("OpenAI chat completion failed: null"));

        String answer = ragChatService.ask("INB-409 이게 뭐야?", "RAG", List.of());

        assertEquals("INB-409는 입고 검수 완료 불가 상태를 의미합니다. 검수 수량 불일치, 불량 증빙 누락, 이미 완료된 입고 지시서 상태를 확인하세요.", answer);
        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals("INB-409 이게 뭐야? WMS 오류 코드 기준 조치 가이드 실패 원인 확인 감사 로그",
                captor.getValue().getQuery());
    }

    private Document document(String text, String source, double score) {
        return Document.builder()
                .text(text)
                .metadata(Map.of("source", source, "section_path", "테스트"))
                .score(score)
                .build();
    }
}

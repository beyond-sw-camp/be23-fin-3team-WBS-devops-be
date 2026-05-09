package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRoutingServiceTest {

    private OpenAiChatGateway openAiChatGateway;
    private RagChatService ragChatService;
    private WorkQueryService workQueryService;
    private ChatRoutingService chatRoutingService;

    @BeforeEach
    void setUp() {
        openAiChatGateway = mock(OpenAiChatGateway.class);
        ragChatService = mock(RagChatService.class);
        workQueryService = mock(WorkQueryService.class);
        chatRoutingService = new ChatRoutingService(openAiChatGateway, ragChatService, workQueryService);
    }

    @Test
    void routesCurrentDataQuestionToWorkQuery() {
        when(openAiChatGateway.complete(anyString(), eq("오늘 출고 건수"))).thenReturn("WORK_QUERY");
        when(workQueryService.ask(eq("오늘 출고 건수"), anyList(), any(), eq("client-1"), eq("user-1")))
                .thenReturn(new WorkQueryService.WorkQueryResponse(
                        "오늘 출고 건수",
                        "STOCK",
                        "OUTBOUND_STATUS",
                        "오늘 출고 처리 대상은 12건입니다.",
                        List.of(Map.of("cnt", 12)),
                        false
                ));

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "오늘 출고 건수", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.WORK_QUERY, result.mode());
        assertFalse(result.fallbackApplied());
        assertEquals("오늘 출고 처리 대상은 12건입니다.", result.answer());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }

    @Test
    void routesProcedureQuestionToRagOnly() {
        when(openAiChatGateway.complete(anyString(), eq("유선 마우스 출고 어떻게 해?"))).thenReturn("RAG");
        when(ragChatService.ask(eq("유선 마우스 출고 어떻게 해?"), eq(null), anyList()))
                .thenReturn("출고 지시서를 생성한 뒤 승인하고 피킹 리스트를 생성합니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "유선 마우스 출고 어떻게 해?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals(ChatMode.RAG, result.originalMode());
        assertFalse(result.fallbackApplied());
        assertEquals("출고 지시서를 생성한 뒤 승인하고 피킹 리스트를 생성합니다.", result.answer());
        verify(workQueryService, never()).ask(anyString(), anyList(), any(), anyString(), anyString());
    }

    @Test
    void routesDefectEvidencePhotoLocationQuestionToRag() {
        when(openAiChatGateway.complete(anyString(), eq("불량 사진 어디서봐"))).thenReturn("GENERAL");
        when(ragChatService.ask(eq("불량 사진 어디서봐"), eq(null), anyList()))
                .thenReturn("불량 사진은 문서/증빙 관리 > 불량 증빙에서 원천 문서 번호로 검색해 확인합니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "불량 사진 어디서봐", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals("llm_general_guarded_rag", result.routeReason());
        assertEquals("불량 사진은 문서/증빙 관리 > 불량 증빙에서 원천 문서 번호로 검색해 확인합니다.", result.answer());
        verify(workQueryService, never()).ask(anyString(), anyList(), any(), anyString(), anyString());
    }

    @Test
    void blocksSensitivePersonalInfoBeforeRouting() {
        when(openAiChatGateway.complete(anyString(), eq("김관리자 전화번호 알려줘"))).thenReturn("BLOCK");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "김관리자 전화번호 알려줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.BLOCKED, result.mode());
        assertEquals("sensitive_info_blocked", result.errorCode());
        assertEquals("해당 정보는 개인 정보에 해당되어 답변이 불가합니다.", result.answer());
        verify(workQueryService, never()).ask(anyString(), anyList(), any(), anyString(), anyString());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }

    @Test
    void keepsWorkQueryModeForShortFollowUpWithRowsContext() {
        List<Map<String, Object>> rows = List.of(Map.of("warehouse_name", "서울중앙창고", "total", 10));
        WorkQueryService.WorkQueryContext context = new WorkQueryService.WorkQueryContext(
                "INVENTORY_LOCATION",
                "창고별 재고 상위 결과입니다.",
                rows
        );
        when(openAiChatGateway.complete(anyString(), eq("하위는?"))).thenReturn("GENERAL");
        when(workQueryService.ask(eq("하위는?"), anyList(), eq(context), eq("client-1"), eq("user-1")))
                .thenReturn(new WorkQueryService.WorkQueryResponse(
                        "하위는?",
                        "STOCK",
                        "INVENTORY_LOCATION",
                        "하위 재고는 부산창고입니다.",
                        rows,
                        true
                ));

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "하위는?",
                List.of(new ChatTurn("user", "창고별 재고 상위 5개 보여줘")),
                context,
                "client-1",
                "user-1",
                "관리자"
        );

        assertEquals(ChatMode.WORK_QUERY, result.mode());
        assertTrue(result.followUp());
        assertEquals("llm_general_guarded_followup", result.routeReason());
    }

    @Test
    void doesNotFallbackToRagWhenWorkQueryFails() {
        when(openAiChatGateway.complete(anyString(), eq("오늘 출고 건수"))).thenReturn("WORK_QUERY");
        when(workQueryService.ask(eq("오늘 출고 건수"), anyList(), any(), eq("client-1"), eq("user-1")))
                .thenThrow(new IllegalStateException("stock-service failed"));

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "오늘 출고 건수", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.WORK_QUERY, result.mode());
        assertEquals(ChatMode.WORK_QUERY, result.originalMode());
        assertTrue(result.fallbackApplied());
        assertEquals("work_query_failed", result.errorCode());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }
}

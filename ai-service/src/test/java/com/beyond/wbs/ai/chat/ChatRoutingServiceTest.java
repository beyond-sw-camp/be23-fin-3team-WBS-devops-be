package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
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
    private ObjectMapper objectMapper;
    private RagChatService ragChatService;
    private WorkQueryService workQueryService;
    private ChatRoutingService chatRoutingService;

    @BeforeEach
    void setUp() {
        openAiChatGateway = mock(OpenAiChatGateway.class);
        objectMapper = new ObjectMapper();
        ragChatService = mock(RagChatService.class);
        workQueryService = mock(WorkQueryService.class);
        chatRoutingService = new ChatRoutingService(openAiChatGateway, objectMapper, ragChatService, workQueryService);
    }

    @Test
    void routesCurrentDataQuestionToWorkQuery() {
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("WORK_QUERY");
        when(workQueryService.askWithRoute(eq("오늘 출고 건수"), anyList(), any(), eq("client-1"), eq("user-1"), any()))
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
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("RAG");
        when(ragChatService.ask(eq("유선 마우스 출고 어떻게 해?"), eq(null), anyList()))
                .thenReturn("출고 지시서를 생성한 뒤 승인하고 피킹 리스트를 생성합니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "유선 마우스 출고 어떻게 해?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals(ChatMode.RAG, result.originalMode());
        assertFalse(result.fallbackApplied());
        assertEquals("출고 지시서를 생성한 뒤 승인하고 피킹 리스트를 생성합니다.", result.answer());
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void routesDefectEvidencePhotoLocationQuestionToRag() {
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(ragChatService.ask(eq("불량 사진 어디서봐"), eq(null), anyList()))
                .thenReturn("불량 사진은 문서/증빙 관리 > 불량 증빙에서 원천 문서 번호로 검색해 확인합니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "불량 사진 어디서봐", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals("llm_general_guarded_rag", result.routeReason());
        assertEquals("불량 사진은 문서/증빙 관리 > 불량 증빙에서 원천 문서 번호로 검색해 확인합니다.", result.answer());
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void blocksSensitivePersonalInfoBeforeRouting() {
        when(openAiChatGateway.complete(anyString(), eq("김관리자 전화번호 알려줘"))).thenReturn("BLOCK");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "김관리자 전화번호 알려줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.BLOCKED, result.mode());
        assertEquals("sensitive_info_blocked", result.errorCode());
        assertEquals("해당 정보는 개인 정보에 해당되어 답변이 불가합니다.", result.answer());
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }

    @Test
    void handlesOffensiveQuestionAsGeneralBoundaryMessage() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "시발 뭐야", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertTrue(result.answer().contains("정중한 표현"));
        verify(openAiChatGateway, never()).complete(anyString(), anyString());
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }

    @Test
    void handlesLottoQuestionAsUnsupportedGeneral() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "로또 번호 추천해줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertEquals("해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요.", result.answer());
        verify(openAiChatGateway, never()).complete(anyString(), anyString());
    }

    @Test
    void handlesLunchMenuQuestionAsUnsupportedGeneral() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "점심 메뉴 추천해줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertEquals("해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요.", result.answer());
        verify(openAiChatGateway, never()).complete(anyString(), anyString());
    }

    @Test
    void handlesBotPersonalMealQuestionAsUnsupportedGeneral() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "넌 먹었냐", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertEquals("해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요.", result.answer());
        verify(openAiChatGateway, never()).complete(anyString(), anyString());
    }

    @Test
    void structuredRouterCanReturnUnsupportedWithoutToolExecution() {
        when(openAiChatGateway.completeJson(anyString(), anyString()))
                .thenReturn("""
                        {
                          "action": "UNSUPPORTED",
                          "confidence": 0.96,
                          "reason": "non_wms_smalltalk",
                          "answer": "해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요."
                        }
                        """);

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "너 주말에 뭐해?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertEquals("non_wms_smalltalk", result.routeReason());
        assertEquals("해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요.", result.answer());
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
        verify(ragChatService, never()).ask(anyString(), any(), anyList());
    }

    @Test
    void structuredRouterCanChooseRagDirectly() {
        when(openAiChatGateway.completeJson(anyString(), anyString()))
                .thenReturn("""
                        {"action":"RAG","confidence":0.93,"reason":"manual_question","answer":""}
                        """);
        when(ragChatService.ask(eq("자동배정 기준 설명해줘"), eq(null), anyList()))
                .thenReturn("자동배정은 작업자 위치와 작업량을 기준으로 수행됩니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "자동배정 기준 설명해줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals("manual_question", result.routeReason());
        assertTrue(result.answer().contains("자동배정"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void structuredRouterPassesWorkIntentAndSlotsToWorkQueryExecutor() {
        when(openAiChatGateway.completeJson(anyString(), anyString()))
                .thenReturn("""
                        {
                          "action": "WORK_QUERY",
                          "confidence": 0.94,
                          "reason": "current_inventory_lookup",
                          "target": "STOCK",
                          "intent": "INVENTORY_LOCATION",
                          "slots": {
                            "product": "무선 마우스",
                            "warehouse": "서울",
                            "keyword": "무선 마우스",
                            "date": ""
                          },
                          "answer": ""
                        }
                        """);
        when(workQueryService.askWithRoute(eq("서울 무선 마우스 어디 있어?"), anyList(), any(), eq("client-1"), eq("user-1"), any()))
                .thenReturn(new WorkQueryService.WorkQueryResponse(
                        "서울 무선 마우스 어디 있어?",
                        "STOCK",
                        "INVENTORY_LOCATION",
                        "무선 마우스는 서울중앙창고 A-01에 있습니다.",
                        List.of(Map.of("product_name", "무선 마우스")),
                        false
                ));

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "서울 무선 마우스 어디 있어?", List.of(), null, "client-1", "user-1", "관리자");

        ArgumentCaptor<WorkQueryService.WorkQueryRoute> routeCaptor =
                ArgumentCaptor.forClass(WorkQueryService.WorkQueryRoute.class);
        verify(workQueryService).askWithRoute(
                eq("서울 무선 마우스 어디 있어?"),
                anyList(),
                any(),
                eq("client-1"),
                eq("user-1"),
                routeCaptor.capture());

        WorkQueryService.WorkQueryRoute route = routeCaptor.getValue();
        assertEquals(ChatMode.WORK_QUERY, result.mode());
        assertEquals("INVENTORY_LOCATION", route.intent());
        assertEquals("STOCK", route.target());
        assertEquals("무선 마우스", route.slots().get("product"));
        assertEquals("서울", route.slots().get("warehouse"));
    }

    @Test
    void handlesDateQuestionWithoutWorkQuery() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "오늘 날짜 뭐야?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertTrue(result.answer().contains("오늘은"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void handlesHolidayQuestionAsUnsupportedGeneral() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "오늘 휴무야?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertTrue(result.answer().contains("휴무나 근태 일정"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void handlesSpellingQuestionAsUnsupportedGeneral() {
        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "이 문장 맞춤법 검사해줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.GENERAL, result.mode());
        assertTrue(result.answer().contains("맞춤법 검사"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void routesSessionExtensionQuestionToRag() {
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(ragChatService.ask(eq("시간 연장 어떻게 해?"), eq(null), anyList()))
                .thenReturn("상단 헤더의 남은 시간을 확인하고 저장 후 재로그인하세요.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "시간 연장 어떻게 해?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals("llm_general_guarded_rag", result.routeReason());
        assertTrue(result.answer().contains("남은 시간"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void routesCodeAndLogoutQuestionsToRag() {
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(ragChatService.ask(eq("번호 생성 기준 알려줘"), eq(null), anyList()))
                .thenReturn("지시서 번호는 업무 prefix, 날짜, 일련번호 기준으로 생성됩니다.");

        ChatRoutingService.RouteResult numberResult = chatRoutingService.route(
                "번호 생성 기준 알려줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, numberResult.mode());
        assertTrue(numberResult.answer().contains("업무 prefix"));

        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(ragChatService.ask(eq("로그아웃 방법 알려줘"), eq(null), anyList()))
                .thenReturn("우측 상단 사용자 메뉴에서 로그아웃을 선택하세요.");

        ChatRoutingService.RouteResult logoutResult = chatRoutingService.route(
                "로그아웃 방법 알려줘", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, logoutResult.mode());
        assertTrue(logoutResult.answer().contains("로그아웃"));
    }

    @Test
    void routesErrorCodeManagementQuestionToRag() {
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(ragChatService.ask(eq("오류코드 관리는 어디서 해?"), eq(null), anyList()))
                .thenReturn("오류 메시지는 원래 화면과 공통 관리 > 감사 로그에서 확인합니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route(
                "오류코드 관리는 어디서 해?", List.of(), null, "client-1", "user-1", "관리자");

        assertEquals(ChatMode.RAG, result.mode());
        assertTrue(result.answer().contains("감사 로그"));
        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void routesOperationalTroubleshootingQuestionsToRag() {
        List<String> questions = List.of(
                "권한 없다고 뜨는데 왜 그래?",
                "메뉴가 안 보여",
                "알림이 안 와",
                "파일 업로드 실패했어",
                "불량 증빙 사진 어디서 봐?",
                "입고 검수 완료가 안 돼",
                "출고지시서 생성 실패 원인 알려줘",
                "피킹 리스트가 안 만들어져",
                "작업자 자동배정 왜 안 돼?",
                "재고 수량이 화면마다 달라",
                "세션 만료됐는데 작성하던 내용 사라졌어",
                "엑셀 다운로드 어디서 해?",
                "QR/바코드 다시 출력하고 싶어",
                "공통코드 수정하면 어디에 영향 있어?",
                "창고/로케이션 비활성화가 안 돼"
        );

        for (String question : questions) {
            when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
            when(ragChatService.ask(eq(question), eq(null), anyList()))
                    .thenReturn("운영 가이드 기준으로 원인과 조치 방법을 안내합니다.");

            ChatRoutingService.RouteResult result = chatRoutingService.route(
                    question, List.of(), null, "client-1", "user-1", "관리자");

            assertEquals(ChatMode.RAG, result.mode(), question);
            assertTrue(result.answer().contains("운영 가이드"), question);
        }

        verify(workQueryService, never()).askWithRoute(anyString(), anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void keepsWorkQueryModeForShortFollowUpWithRowsContext() {
        List<Map<String, Object>> rows = List.of(Map.of("warehouse_name", "서울중앙창고", "total", 10));
        WorkQueryService.WorkQueryContext context = new WorkQueryService.WorkQueryContext(
                "INVENTORY_LOCATION",
                "창고별 재고 상위 결과입니다.",
                rows
        );
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("GENERAL");
        when(workQueryService.askWithRoute(eq("하위는?"), anyList(), eq(context), eq("client-1"), eq("user-1"), any()))
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
        when(openAiChatGateway.completeJson(anyString(), anyString())).thenReturn("WORK_QUERY");
        when(workQueryService.askWithRoute(eq("오늘 출고 건수"), anyList(), any(), eq("client-1"), eq("user-1"), any()))
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

package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.sql.TextToSqlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRoutingServiceTest {

    private RagChatService ragChatService;
    private TextToSqlService textToSqlService;
    private ChatRoutingService chatRoutingService;

    @BeforeEach
    void setUp() {
        ragChatService = mock(RagChatService.class);
        textToSqlService = mock(TextToSqlService.class);
        ChatClient chatClient = mock(ChatClient.class);
        chatRoutingService = new ChatRoutingService(chatClient, ragChatService, textToSqlService);
    }

    @Test
    void routesSqlQuestionByHeuristic() {
        TextToSqlService.Result sqlResult = new TextToSqlService.Result(
                "오늘 출고 건수",
                "SELECT COUNT(*) AS cnt FROM outbound_orders LIMIT 100",
                List.of(Map.of("cnt", 12)),
                21L,
                "오늘 출고 주문은 12건입니다."
        );
        when(textToSqlService.analyze(eq("오늘 출고 건수"), anyList())).thenReturn(sqlResult);

        ChatRoutingService.RouteResult result = chatRoutingService.route("오늘 출고 건수", List.of());

        assertEquals(ChatMode.SQL, result.mode());
        assertEquals(ChatMode.SQL, result.originalMode());
        assertEquals("heuristic_sql_hint", result.routeReason());
        assertFalse(result.fallbackApplied());
        assertEquals("오늘 출고 주문은 12건입니다.", result.answer());
        verify(ragChatService, never()).ask(any(), any(), anyList());
    }

    @Test
    void routesRagQuestionByHeuristic() {
        when(ragChatService.ask(eq("출고 절차는 어떻게 진행돼?"), eq(null), anyList()))
                .thenReturn("출고 절차 안내입니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route("출고 절차는 어떻게 진행돼?", List.of());

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals(ChatMode.RAG, result.originalMode());
        assertEquals("heuristic_rag_hint", result.routeReason());
        assertFalse(result.fallbackApplied());
        assertEquals("출고 절차 안내입니다.", result.answer());
        verify(textToSqlService, never()).analyze(any(), anyList());
    }

    @Test
    void keepsSqlModeForShortFollowUpWhenHistoryLooksSql() {
        List<ChatTurn> history = List.of(
                new ChatTurn("user", "창고별 재고 상위 5개 보여줘"),
                new ChatTurn("assistant", "SELECT w.name, SUM(i.total_qty) AS total FROM inventories i JOIN master_db.warehouses w ON i.warehouse_id = w.id GROUP BY w.name ORDER BY total DESC LIMIT 5")
        );
        TextToSqlService.Result sqlResult = new TextToSqlService.Result(
                "하위는?",
                "SELECT w.name, SUM(i.total_qty) AS total FROM inventories i JOIN master_db.warehouses w ON i.warehouse_id = w.id GROUP BY w.name ORDER BY total ASC LIMIT 5",
                List.of(Map.of("warehouse_name", "부산", "total", 3)),
                17L,
                "재고가 가장 적은 창고는 부산입니다."
        );
        when(textToSqlService.analyze(eq("하위는?"), eq(history))).thenReturn(sqlResult);

        ChatRoutingService.RouteResult result = chatRoutingService.route("하위는?", history);

        assertEquals(ChatMode.SQL, result.mode());
        assertEquals("heuristic_sql_followup", result.routeReason());
        assertFalse(result.fallbackApplied());
    }

    @Test
    void fallsBackToRagWhenSqlExecutionFails() {
        when(textToSqlService.analyze(eq("오늘 출고 건수"), anyList()))
                .thenThrow(new IllegalStateException("sql failed"));
        when(ragChatService.ask(eq("오늘 출고 건수"), eq(null), anyList()))
                .thenReturn("문서 기준으로는 오늘 출고 집계 방법만 안내할 수 있습니다.");

        ChatRoutingService.RouteResult result = chatRoutingService.route("오늘 출고 건수", List.of());

        assertEquals(ChatMode.RAG, result.mode());
        assertEquals(ChatMode.SQL, result.originalMode());
        assertEquals("fallback_rag_after_sql_error", result.routeReason());
        assertTrue(result.fallbackApplied());
        assertEquals("sql_execution_failed", result.errorCode());
        assertTrue(result.retryable());
    }
}

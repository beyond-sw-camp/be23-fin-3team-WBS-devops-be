package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoutingService {

    private static final Pattern WORK_QUERY_HINT = Pattern.compile(
            "(?i)(몇\\s*개|몇\\s*건|건수|합계|총\\s*재고|총합|평균|상위|하위|top\\s*\\d*|bottom\\s*\\d*|" +
                    "상품별|창고별|위치별|비율|추이|현황|목록|오늘|어제|이번\\s*달|지난\\s*달|최근|" +
                    "내\\s*피킹|내가|나의|담당|해야\\s*할|입고\\s*예정|출고\\s*예정|부족\\s*재고|재고\\s*위치)"
    );

    private static final Pattern RAG_HINT = Pattern.compile(
            "(?i)(어떻게|방법|절차|프로세스|매뉴얼|sop|가이드|설명|의미|정의|정책|규정|기준|원칙|왜|차이|주의)"
    );

    private static final Pattern SHORT_FOLLOW_UP = Pattern.compile(
            "^(하위는\\??|상위는\\??|어제는\\??|오늘은\\??|그 다음은\\??|그럼\\??|그리고\\??|다시\\??|더 보여줘\\??|" +
                    "첫 번째만\\??|첫번째만\\??|두 번째만\\??|두번째만\\??|진행 중인 것만\\??|대기 중인 것만\\??)$"
    );

    private final ChatClient chatClient;
    private final RagChatService ragChatService;
    private final WorkQueryService workQueryService;

    public RouteResult route(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context,
            String clientId,
            String userId) {
        long startedAt = System.nanoTime();
        List<ChatTurn> safeHistory = history == null ? List.of() : history;
        String normalizedQuestion = normalize(question);

        ClassificationDecision decision = classify(normalizedQuestion, safeHistory, context);
        log.info("[AI_CHAT_ROUTE] mode={}, reason={}, clientId={}, userId={}, question='{}'",
                decision.mode(), decision.reason(), mask(clientId), mask(userId), normalizedQuestion);

        if (decision.mode() == ChatMode.WORK_QUERY) {
            try {
                WorkQueryService.WorkQueryResponse response = workQueryService.ask(
                        normalizedQuestion,
                        safeHistory,
                        context,
                        clientId,
                        userId
                );
                return RouteResult.workQuery(
                        decision.reason(),
                        false,
                        null,
                        false,
                        response.answer(),
                        response.intent(),
                        response.rows(),
                        response.followUp(),
                        elapsedMs(startedAt)
                );
            } catch (Exception e) {
                log.warn("[AI_CHAT_ROUTE_FALLBACK] from=WORK_QUERY, to=RAG, reason={}, question='{}'",
                        e.getMessage(), normalizedQuestion);
                String fallbackAnswer = ragChatService.ask(normalizedQuestion, null, safeHistory);
                return RouteResult.rag(
                        ChatMode.WORK_QUERY,
                        "fallback_rag_after_work_query_error",
                        true,
                        "work_query_failed",
                        true,
                        fallbackAnswer,
                        elapsedMs(startedAt)
                );
            }
        }

        String answer = ragChatService.ask(normalizedQuestion, null, safeHistory);
        return RouteResult.rag(
                ChatMode.RAG,
                decision.reason(),
                false,
                null,
                false,
                answer,
                elapsedMs(startedAt)
        );
    }

    private ClassificationDecision classify(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context) {
        if (question.isBlank()) {
            return new ClassificationDecision(ChatMode.RAG, "default_rag_blank_question");
        }

        boolean workQueryHint = WORK_QUERY_HINT.matcher(question).find();
        boolean ragHint = RAG_HINT.matcher(question).find();

        if (context != null && context.rows() != null && !context.rows().isEmpty()
                && SHORT_FOLLOW_UP.matcher(question).matches()) {
            return new ClassificationDecision(ChatMode.WORK_QUERY, "heuristic_work_query_followup");
        }
        if (workQueryHint && !ragHint) {
            return new ClassificationDecision(ChatMode.WORK_QUERY, "heuristic_work_query_hint");
        }
        if (ragHint && !workQueryHint) {
            return new ClassificationDecision(ChatMode.RAG, "heuristic_rag_hint");
        }

        try {
            return classifyWithLlm(question, history);
        } catch (Exception e) {
            log.warn("[AI_CHAT_ROUTE] llm_classification_failed question='{}', reason={}", question, e.getMessage());
            return workQueryHint
                    ? new ClassificationDecision(ChatMode.WORK_QUERY, "fallback_work_query_after_llm_error")
                    : new ClassificationDecision(ChatMode.RAG, "fallback_rag_after_llm_error");
        }
    }

    private ClassificationDecision classifyWithLlm(String question, List<ChatTurn> history) {
        String historyBlock = formatHistory(history);
        String prompt = """
                너는 Spring Boot MSA 기반 WMS 챗봇의 라우터다.
                사용자의 현재 질문을 보고 어떤 기능 API를 호출할지 선택한다.

                [WORK_QUERY]
                - 현재 업무 데이터 조회 API를 호출해야 하는 질문
                - 예: 내 피킹 작업, 오늘 처리할 지시서, 입고/출고 현황, 재고 위치, 부족 재고
                - DB에 있는 현재 상태/수량/목록/건수를 알아야 답할 수 있는 질문

                [RAG]
                - pgvector 문서 검색 API를 호출해야 하는 질문
                - 예: 화면 사용법, 상태값 의미, 업무 절차, 예외 처리 기준, 전자기기 보관 주의사항
                - 운영 매뉴얼/FAQ/정책/기준을 근거로 답해야 하는 질문

                규칙:
                - 반드시 WORK_QUERY 또는 RAG 한 단어만 출력한다.
                - 설명, JSON, markdown 금지.
                - 짧은 후속 질문은 최근 대화 맥락을 보고 같은 기능으로 분류한다.

                최근 대화:
                %s
                """.formatted(historyBlock.isBlank() ? "(없음)" : historyBlock);

        String response = chatClient.prompt()
                .system(prompt)
                .user(question)
                .call()
                .content();

        String normalized = normalize(response).toUpperCase(Locale.ROOT);
        return normalized.contains("WORK_QUERY")
                ? new ClassificationDecision(ChatMode.WORK_QUERY, "llm_work_query")
                : new ClassificationDecision(ChatMode.RAG, "llm_rag");
    }

    private String formatHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            ChatTurn turn = history.get(i);
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
            sb.append(role).append(": ").append(normalize(turn.content())).append('\n');
        }
        return sb.toString().trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String mask(String value) {
        if (value == null || value.isBlank() || value.length() < 8) {
            return value;
        }
        return value.substring(0, 8) + "...";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ClassificationDecision(ChatMode mode, String reason) {
    }

    public record RouteResult(
            ChatMode mode,
            ChatMode originalMode,
            String routeReason,
            boolean fallbackApplied,
            String errorCode,
            boolean retryable,
            String answer,
            String intent,
            List<Map<String, Object>> rows,
            Boolean followUp,
            Long executionTimeMs) {

        public static RouteResult rag(
                ChatMode originalMode,
                String routeReason,
                boolean fallbackApplied,
                String errorCode,
                boolean retryable,
                String answer,
                Long executionTimeMs) {
            return new RouteResult(
                    ChatMode.RAG,
                    originalMode,
                    routeReason,
                    fallbackApplied,
                    errorCode,
                    retryable,
                    answer,
                    null,
                    List.of(),
                    false,
                    executionTimeMs
            );
        }

        public static RouteResult workQuery(
                String routeReason,
                boolean fallbackApplied,
                String errorCode,
                boolean retryable,
                String answer,
                String intent,
                List<Map<String, Object>> rows,
                boolean followUp,
                Long executionTimeMs) {
            return new RouteResult(
                    ChatMode.WORK_QUERY,
                    ChatMode.WORK_QUERY,
                    routeReason,
                    fallbackApplied,
                    errorCode,
                    retryable,
                    answer,
                    intent,
                    rows,
                    followUp,
                    executionTimeMs
            );
        }
    }
}

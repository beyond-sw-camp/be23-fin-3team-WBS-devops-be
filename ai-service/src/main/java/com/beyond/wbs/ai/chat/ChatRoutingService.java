package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                    "어디|어디에|어딨|어딧|위치|찾아|남아|남았|있나|있어|보유|수량|" +
                    "내\\s*피킹|내가|나의|담당|해야\\s*할|입고\\s*예정|출고\\s*예정|부족\\s*재고|재고\\s*위치)"
    );

    private static final Pattern RAG_HINT = Pattern.compile(
            "(?i)(어떻게|방법|절차|프로세스|매뉴얼|sop|가이드|설명|의미|정의|정책|규정|기준|원칙|왜|차이|주의)"
    );

    private static final Pattern SHORT_FOLLOW_UP = Pattern.compile(
            "^(하위는\\??|상위는\\??|어제는\\??|오늘은\\??|그 다음은\\??|그럼\\??|그리고\\??|다시\\??|더 보여줘\\??|" +
                    "첫 번째만\\??|첫번째만\\??|두 번째만\\??|두번째만\\??|진행 중인 것만\\??|대기 중인 것만\\??)$"
    );

    private static final Pattern GENERAL_CHAT_HINT = Pattern.compile(
            "(?i)^(안녕|안녕하세요|하이|hello|hi|반가워|고마워|감사|땡큐|thanks|너\\s*누구|넌\\s*누구|" +
                    "나\\s*누구|난\\s*누구|나는\\s*누구|내가\\s*누구|뭐\\s*할\\s*수\\s*있|뭘\\s*할\\s*수\\s*있|도움말|사용법|시작|챗봇|ai\\s*챗봇)[\\s!?？!.]*$"
    );

    private static final Pattern USER_IDENTITY_QUESTION = Pattern.compile(
            "(?i).*(나\\s*누구|난\\s*누구|나는\\s*누구|내가\\s*누구).*"
    );

    private final OpenAiChatGateway openAiChatGateway;
    private final RagChatService ragChatService;
    private final WorkQueryService workQueryService;

    public RouteResult route(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context,
            String clientId,
            String userId,
            String userName) {
        long startedAt = System.nanoTime();
        List<ChatTurn> safeHistory = history == null ? List.of() : history;
        String normalizedQuestion = normalize(question);

        ClassificationDecision decision = classify(normalizedQuestion, safeHistory, context);
        log.info("[AI_CHAT_ROUTE] mode={}, reason={}, clientId={}, userId={}, question='{}'",
                decision.mode(), decision.reason(), mask(clientId), mask(userId), normalizedQuestion);

        if (decision.mode() == ChatMode.GENERAL) {
            return RouteResult.general(
                    decision.reason(),
                    generalAnswer(normalizedQuestion, userName),
                    elapsedMs(startedAt)
            );
        }

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
                String fallbackAnswer = safeRagFallback(normalizedQuestion, safeHistory);
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

        String answer = safeRagFallback(normalizedQuestion, safeHistory);
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

    private String safeRagFallback(String question, List<ChatTurn> history) {
        try {
            return ragChatService.ask(question, null, history);
        } catch (Exception e) {
            log.warn("[AI_CHAT_ROUTE] rag_failed question='{}', reason={}", question, e.getMessage());
            if (isOpenAiQuotaError(e)) {
                return "현재 OpenAI API 사용 한도가 초과되어 AI 답변을 생성할 수 없습니다. 결제/크레딧 상태를 확인한 뒤 다시 시도해주세요.";
            }
            return "죄송합니다. 요청하신 질문을 처리할 수 없습니다.";
        }
    }

    private boolean isOpenAiQuotaError(Exception e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("insufficient_quota")
                || message.contains("exceeded your current quota")
                || message.contains("HTTP 429"));
    }

    private ClassificationDecision classify(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context) {
        if (question.isBlank()) {
            return new ClassificationDecision(ChatMode.GENERAL, "default_general_blank_question");
        }

        boolean workQueryHint = WORK_QUERY_HINT.matcher(question).find();
        boolean ragHint = RAG_HINT.matcher(question).find();
        boolean followUpHint = context != null && context.rows() != null && !context.rows().isEmpty()
                && SHORT_FOLLOW_UP.matcher(question).matches();

        if (isUserIdentityQuestion(question) || isObviousGeneral(question)) {
            return new ClassificationDecision(ChatMode.GENERAL, "heuristic_general_chat");
        }

        try {
            ClassificationDecision llmDecision = classifyWithLlm(question, history);
            return guardLlmDecision(llmDecision, workQueryHint, ragHint, followUpHint);
        } catch (Exception e) {
            log.warn("[AI_CHAT_ROUTE] llm_classification_failed question='{}', reason={}", question, e.getMessage());
            if (followUpHint || workQueryHint) {
                return new ClassificationDecision(ChatMode.WORK_QUERY, "fallback_work_query_after_llm_error");
            }
            if (ragHint) {
                return new ClassificationDecision(ChatMode.RAG, "fallback_rag_after_llm_error");
            }
            return new ClassificationDecision(ChatMode.GENERAL, "fallback_general_after_llm_error");
        }
    }

    private boolean isObviousGeneral(String question) {
        return GENERAL_CHAT_HINT.matcher(question).matches()
                && !WORK_QUERY_HINT.matcher(question).find()
                && !RAG_HINT.matcher(question).find();
    }

    private boolean isUserIdentityQuestion(String question) {
        return USER_IDENTITY_QUESTION.matcher(question).matches();
    }

    private ClassificationDecision guardLlmDecision(
            ClassificationDecision llmDecision,
            boolean workQueryHint,
            boolean ragHint,
            boolean followUpHint) {
        if (followUpHint && llmDecision.mode() == ChatMode.GENERAL) {
            return new ClassificationDecision(ChatMode.WORK_QUERY, llmDecision.reason() + "_guarded_followup");
        }
        if (workQueryHint && llmDecision.mode() == ChatMode.GENERAL) {
            return new ClassificationDecision(ChatMode.WORK_QUERY, llmDecision.reason() + "_guarded_work_query");
        }
        if (ragHint && !workQueryHint && llmDecision.mode() == ChatMode.GENERAL) {
            return new ClassificationDecision(ChatMode.RAG, llmDecision.reason() + "_guarded_rag");
        }
        return llmDecision;
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
                - 상품/품목/창고/재고/지시서/피킹/입고/출고의 현재 상태를 묻는 질문
                - 예: 마우스 어딨어, 마우스 위치 알려줘, 유선 마우스 출고 어떻게 해, 오늘 뭐해야돼, 5월 6일에 뭐해야돼

                [RAG]
                - pgvector 문서 검색 API를 호출해야 하는 질문
                - 예: 화면 사용법, 상태값 의미, 업무 절차, 예외 처리 기준, 전자기기 보관 주의사항
                - 운영 매뉴얼/FAQ/정책/기준을 근거로 답해야 하는 질문

                [GENERAL]
                - 인사, 감사, 챗봇 소개, 할 수 있는 일 안내처럼 DB나 문서 검색이 필요 없는 일반 대화
                - 예: 안녕, 고마워, 너 누구야, 뭐 할 수 있어?
                - 상품명이나 업무 대상이 포함된 질문은 GENERAL이 아니다.

                규칙:
                - 반드시 WORK_QUERY, RAG, GENERAL 중 한 단어만 출력한다.
                - 설명, JSON, markdown 금지.
                - 짧은 후속 질문은 최근 대화 맥락을 보고 같은 기능으로 분류한다.
                - 재고 위치/수량/존재 여부/오늘 할 일/지시서 목록은 말투가 짧거나 구어체여도 WORK_QUERY다.
                - 특정 상품명이 포함된 "출고 어떻게/출고 방법" 질문은 재고 확인이 먼저 필요하므로 WORK_QUERY다.
                - 방법/절차/기준/의미/왜를 묻는 설명형 질문은 RAG다.

                최근 대화:
                %s

                [현재 질문]
                %s
                """.formatted(historyBlock.isBlank() ? "(없음)" : historyBlock, question);

        String response = openAiChatGateway.complete(prompt, question);

        String normalized = normalize(response).toUpperCase(Locale.ROOT);
        if (normalized.contains("WORK_QUERY")) {
            return new ClassificationDecision(ChatMode.WORK_QUERY, "llm_work_query");
        }
        if (normalized.contains("GENERAL")) {
            return new ClassificationDecision(ChatMode.GENERAL, "llm_general");
        }
        return new ClassificationDecision(ChatMode.RAG, "llm_rag");
    }

    private String generalAnswer(String question, String userName) {
        String normalized = normalize(question).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "질문을 입력해주시면 재고 위치, 오늘 처리할 지시서, 입고/출고 현황 등을 도와드리겠습니다.";
        }
        if (isUserIdentityQuestion(normalized)) {
            String name = normalize(userName);
            if (!name.isBlank()) {
                return "현재 로그인한 사용자는 " + name + "님입니다.";
            }
            return "현재 로그인한 사용자로 접속 중입니다. 정확한 이름과 계정 정보는 우측 상단의 내 프로필에서 확인할 수 있습니다.";
        }
        if (normalized.matches(".*(너\\s*누구|넌\\s*누구).*")) {
            return "저는 WMS AI 어시스턴트입니다. 재고 위치, 오늘 처리할 지시서, 입고/출고 현황, 부족 재고를 질문하시면 업무 데이터를 조회해 답변해드립니다.";
        }
        if (normalized.contains("반가")) {
            return "반갑습니다. 재고 위치나 오늘 처리할 업무가 궁금하시면 바로 물어보세요.";
        }
        if (normalized.contains("고마") || normalized.contains("감사") || normalized.contains("thanks") || normalized.contains("땡큐")) {
            return "천만에요. 필요한 재고나 작업 정보를 물어보시면 바로 도와드리겠습니다.";
        }
        if (normalized.contains("누구") || normalized.contains("뭐 할 수") || normalized.contains("뭘 할 수")
                || normalized.contains("도움말") || normalized.contains("사용법") || normalized.contains("챗봇")) {
            return "안녕하세요. 저는 WMS AI 어시스턴트입니다. 재고 위치, 오늘 처리할 지시서, 입고/출고 현황, 부족 재고를 질문하시면 업무 데이터를 조회해 답변해드립니다.";
        }
        return "안녕하세요. 저는 WMS AI 어시스턴트입니다. 재고 위치나 오늘 처리할 업무처럼 궁금한 내용을 편하게 물어보세요.";
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

        public static RouteResult general(
                String routeReason,
                String answer,
                Long executionTimeMs) {
            return new RouteResult(
                    ChatMode.GENERAL,
                    ChatMode.GENERAL,
                    routeReason,
                    false,
                    null,
                    false,
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

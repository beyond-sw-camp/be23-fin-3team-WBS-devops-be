package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.beyond.wbs.ai.rag.RagChatService;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoutingService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Pattern WORK_QUERY_HINT = Pattern.compile(
            "(?i)(몇\\s*개|몇\\s*건|건수|합계|총\\s*재고|총합|평균|상위|하위|top\\s*\\d*|bottom\\s*\\d*|" +
                    "상품별|창고별|위치별|비율|추이|현황|목록|오늘|어제|이번\\s*달|지난\\s*달|최근|" +
                    "어디|어디에|어딨|어딧|위치|찾아|남아|남았|있나|있어|보유|수량|" +
                    "내\\s*피킹|내가|나의|담당|해야\\s*할|입고\\s*예정|출고\\s*예정|부족\\s*재고|재고\\s*위치)"
    );

    private static final Pattern RAG_HINT = Pattern.compile(
            "(?i)(어떻게|방법|절차|프로세스|매뉴얼|sop|가이드|설명|의미|정의|정책|규정|기준|원칙|왜|차이|주의|" +
                    "어디서|어디에서|어느\\s*화면|화면|메뉴|경로|사진|증빙|첨부|파일|" +
                    "시간\\s*연장|세션\\s*연장|세션\\s*만료|로그아웃|번호\\s*생성|채번|공통\\s*코드|" +
                    "오류\\s*코드|에러\\s*코드|권한\\s*없|접근\\s*권한|메뉴.*안\\s*보|알림.*안\\s*와|" +
                    "업로드\\s*실패|생성\\s*실패|완료.*안\\s*돼|안\\s*만들어|자동\\s*배정|자동배정|" +
                    "수량.*다르|재고.*다르|엑셀\\s*다운로드|qr|바코드|비활성화.*안\\s*돼)"
    );

    private static final Pattern SHORT_FOLLOW_UP = Pattern.compile(
            "^(하위는\\??|상위는\\??|어제는\\??|오늘은\\??|그 다음은\\??|그럼\\??|그리고\\??|다시\\??|더 보여줘\\??|" +
                    "첫 번째만\\??|첫번째만\\??|두 번째만\\??|두번째만\\??|진행 중인 것만\\??|대기 중인 것만\\??)$"
    );

    private static final Pattern GENERAL_CHAT_HINT = Pattern.compile(
            "(?i)^(안녕|안녕하세요|하이|hello|hi|반가워|고마워|감사|땡큐|thanks|너\\s*누구|넌\\s*누구|" +
                    "나\\s*누구|난\\s*누구|나는\\s*누구|내가\\s*누구|뭐\\s*할\\s*수\\s*있|뭘\\s*할\\s*수\\s*있|도움말|사용법|시작|챗봇|ai\\s*챗봇)[\\s!?？!.]*$"
    );

    private static final Pattern OFFENSIVE_HINT = Pattern.compile(
            "(?i).*(시발|씨발|ㅅㅂ|병신|ㅂㅅ|개새|새끼|꺼져|좆|존나|fuck|shit).*"
    );

    private static final Pattern OFF_TOPIC_HINT = Pattern.compile(
            "(?i).*(로또|복권|점심|메뉴\\s*추천|저녁\\s*추천|맛집|연애|운세|사주).*"
    );

    private static final Pattern BOT_PERSONAL_LIFE_HINT = Pattern.compile(
            "(?i).*((너|넌|너는|니|너도|챗봇|ai).*(먹었|먹냐|먹어|밥|식사|점심|저녁)|" +
                    "(밥|식사|점심|저녁).*(먹었|먹냐|먹어)|" +
                    "^(먹었냐|밥\\s*먹었냐|식사\\s*했냐)[\\s!?？!.]*$).*"
    );

    private static final Pattern DATE_TIME_HINT = Pattern.compile(
            "(?i).*((오늘|내일|어제)\\s*(날짜|요일)|지금\\s*몇\\s*시|현재\\s*시간|오늘\\s*며칠|날짜\\s*알려|요일\\s*알려).*"
    );

    private static final Pattern UNSUPPORTED_ADMIN_HINT = Pattern.compile(
            "(?i).*(휴무|휴가|연차|근태|맞춤법|띄어쓰기|문법\\s*검사).*"
    );

    private static final Pattern USER_IDENTITY_QUESTION = Pattern.compile(
            "(?i).*(나\\s*누구|난\\s*누구|나는\\s*누구|내가\\s*누구).*"
    );

    private static final Pattern SENSITIVE_INFO_HINT = Pattern.compile(
            "(?i).*(주민\\s*등록|주민번호|외국인\\s*등록|여권|운전\\s*면허|비밀번호|password|passwd|pwd|" +
                    "토큰|token|api\\s*key|secret|인증번호|otp|로그인\\s*아이디|login\\s*id|login_id|계좌|카드\\s*번호|전화번호|휴대폰|핸드폰|연락처|" +
                    "이메일|email|메일주소|주소|집주소|생년월일|나이|개인정보|민감정보).*"
    );

    private static final String SENSITIVE_BLOCK_ANSWER = "해당 정보는 개인 정보에 해당되어 답변이 불가합니다.";
    private static final String UNSUPPORTED_ANSWER = "해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요.";

    private final OpenAiChatGateway openAiChatGateway;
    private final ObjectMapper objectMapper;
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

        SensitiveDecision sensitiveDecision = classifySensitive(normalizedQuestion, safeHistory);
        if (sensitiveDecision.blocked()) {
            log.info("[AI_CHAT_ROUTE] mode=BLOCKED, reason={}, clientId={}, userId={}, question='{}'",
                    sensitiveDecision.reason(), mask(clientId), mask(userId), normalizedQuestion);
            return RouteResult.blocked(sensitiveDecision.reason(), SENSITIVE_BLOCK_ANSWER, elapsedMs(startedAt));
        }

        RoutingDecision decision = classify(normalizedQuestion, safeHistory, context);
        log.info("[AI_CHAT_ROUTE] mode={}, reason={}, clientId={}, userId={}, question='{}'",
                decision.mode(), decision.reason(), mask(clientId), mask(userId), normalizedQuestion);

        if (decision.mode() == ChatMode.GENERAL) {
            return RouteResult.general(
                    decision.reason(),
                    decision.answer() == null || decision.answer().isBlank()
                            ? generalAnswer(normalizedQuestion, userName)
                            : decision.answer(),
                    elapsedMs(startedAt)
            );
        }

        if (decision.mode() == ChatMode.WORK_QUERY) {
            try {
                WorkQueryService.WorkQueryResponse response = workQueryService.askWithRoute(
                        normalizedQuestion,
                        safeHistory,
                        context,
                        clientId,
                        userId,
                        decision.toWorkQueryRoute()
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
                log.warn("[AI_CHAT_ROUTE_ERROR] mode=WORK_QUERY, reason={}, question='{}'",
                        e.getMessage(), normalizedQuestion);
                return RouteResult.workQuery(
                        decision.reason(),
                        true,
                        "work_query_failed",
                        true,
                        "현재 업무 데이터를 조회하지 못했습니다. 잠시 후 다시 시도하거나 해당 화면에서 조건을 직접 확인해주세요.",
                        null,
                        List.of(),
                        false,
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

    private RoutingDecision classify(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context) {
        if (question.isBlank()) {
            return RoutingDecision.general("default_general_blank_question", null);
        }

        boolean workQueryHint = WORK_QUERY_HINT.matcher(question).find();
        boolean ragHint = RAG_HINT.matcher(question).find();
        boolean followUpHint = context != null && context.rows() != null && !context.rows().isEmpty()
                && SHORT_FOLLOW_UP.matcher(question).matches();

        if (isUserIdentityQuestion(question) || isObviousGeneral(question) || isDeterministicGeneral(question)) {
            return RoutingDecision.general("deterministic_boundary", null);
        }

        try {
            RoutingDecision llmDecision = classifyWithLlm(question, history, context);
            return guardLlmDecision(llmDecision, workQueryHint, ragHint, followUpHint);
        } catch (Exception e) {
            log.warn("[AI_CHAT_ROUTE] llm_classification_failed question='{}', reason={}", question, e.getMessage());
            if (ragHint) {
                return RoutingDecision.rag("fallback_rag_after_llm_error");
            }
            if (followUpHint || workQueryHint) {
                return RoutingDecision.workQuery("fallback_work_query_after_llm_error");
            }
            return RoutingDecision.general("fallback_general_after_llm_error", UNSUPPORTED_ANSWER);
        }
    }

    private SensitiveDecision classifySensitive(String question, List<ChatTurn> history) {
        if (question.isBlank()) {
            return new SensitiveDecision(false, "sensitive_blank_question");
        }

        boolean sensitiveHint = SENSITIVE_INFO_HINT.matcher(question).matches();
        if (!sensitiveHint) {
            return new SensitiveDecision(false, "sensitive_no_hint");
        }

        try {
            SensitiveDecision decision = classifySensitiveWithLlm(question, history);
            if (decision.blocked()) {
                return decision;
            }
            return new SensitiveDecision(false, decision.reason());
        } catch (Exception e) {
            log.warn("[AI_CHAT_ROUTE] sensitive_classification_failed question='{}', reason={}", question, e.getMessage());
            return new SensitiveDecision(true, "sensitive_heuristic_after_llm_error");
        }
    }

    private SensitiveDecision classifySensitiveWithLlm(String question, List<ChatTurn> history) {
        String prompt = """
                너는 WMS 챗봇의 개인정보/민감정보 차단 분류기다.
                사용자의 현재 질문이 개인정보 또는 민감정보의 조회/노출/추출을 요구하면 BLOCK을 출력한다.
                그렇지 않으면 ALLOW를 출력한다.

                [반드시 BLOCK]
                - 주민등록번호, 외국인등록번호, 여권번호, 운전면허번호
                - 비밀번호, 토큰, API Key, Secret, 인증번호, OTP
                - 로그인 아이디처럼 개인 계정 식별자를 특정 사용자 기준으로 알려달라는 질문
                - 개인 전화번호, 휴대폰 번호, 연락처, 개인 이메일, 집주소, 생년월일
                - 계좌번호, 카드번호, 급여, 개인 신상정보
                - 특정 직원/사용자/관리자의 위 항목을 알려달라는 질문
                - "마스킹 없이", "전체", "원문", "전부 보여줘"처럼 개인정보 노출을 요구하는 질문

                [ALLOW]
                - 재고 위치, 입고/출고 현황, 작업 목록, 지시서 상태
                - 상품, 창고, 구역, 랙, 로케이션, 공급사, 매장 같은 업무 기준 정보
                - 사용자 역할명/권한 정책처럼 개인 연락처나 인증정보를 노출하지 않는 관리 정보
                - 개인정보 처리 방법/보안 정책에 대한 일반 설명

                규칙:
                - 반드시 BLOCK 또는 ALLOW 중 한 단어만 출력한다.
                - 애매하면 BLOCK.
                - 설명, JSON, markdown 금지.

                최근 대화:
                %s

                [현재 질문]
                %s
                """.formatted(formatHistory(history).isBlank() ? "(없음)" : formatHistory(history), question);

        String response = openAiChatGateway.complete(prompt, question);
        String normalized = normalize(response).toUpperCase(Locale.ROOT);
        if (normalized.contains("BLOCK")) {
            return new SensitiveDecision(true, "llm_sensitive_block");
        }
        if (normalized.contains("ALLOW")) {
            return new SensitiveDecision(false, "llm_sensitive_allow");
        }
        return new SensitiveDecision(true, "llm_sensitive_unrecognized_block");
    }

    private boolean isObviousGeneral(String question) {
        return GENERAL_CHAT_HINT.matcher(question).matches()
                && !WORK_QUERY_HINT.matcher(question).find()
                && !RAG_HINT.matcher(question).find();
    }

    private boolean isDeterministicGeneral(String question) {
        return OFFENSIVE_HINT.matcher(question).matches()
                || OFF_TOPIC_HINT.matcher(question).matches()
                || BOT_PERSONAL_LIFE_HINT.matcher(question).matches()
                || DATE_TIME_HINT.matcher(question).matches()
                || UNSUPPORTED_ADMIN_HINT.matcher(question).matches();
    }

    private boolean isUserIdentityQuestion(String question) {
        return USER_IDENTITY_QUESTION.matcher(question).matches();
    }

    private RoutingDecision guardLlmDecision(
            RoutingDecision llmDecision,
            boolean workQueryHint,
            boolean ragHint,
            boolean followUpHint) {
        if (followUpHint && llmDecision.mode() == ChatMode.GENERAL) {
            return RoutingDecision.workQuery(llmDecision.reason() + "_guarded_followup");
        }
        if (ragHint && llmDecision.mode() == ChatMode.GENERAL && llmDecision.confidence() < 0.80) {
            return RoutingDecision.rag(llmDecision.reason() + "_guarded_rag");
        }
        if (workQueryHint && llmDecision.mode() == ChatMode.GENERAL && llmDecision.confidence() < 0.80) {
            return RoutingDecision.workQuery(llmDecision.reason() + "_guarded_work_query");
        }
        return llmDecision;
    }

    private RoutingDecision classifyWithLlm(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context) {
        String historyBlock = formatHistory(history);
        String systemPrompt = """
                너는 WMS 관리자용 AI 챗봇의 tool router다.
                사용자의 현재 질문을 보고 백엔드가 실행할 action을 JSON으로만 결정한다.

                사용할 수 있는 action:
                - WORK_QUERY: 현재 업무 데이터 조회. 재고 위치/수량, 입고/출고/피킹/이동/실사 상태, 오늘 처리할 작업, 미처리 지시서 등.
                - RAG: 업무 매뉴얼/화면 경로/절차/정책/오류 원인/사용법 설명.
                - GENERAL: 인사, 감사, 챗봇 소개, 할 수 있는 일 안내.
                - UNSUPPORTED: WMS 업무나 매뉴얼과 무관하거나, 챗봇 개인 생활/식사/감정/잡담/추천을 묻는 질문.

                엄격한 기준:
                - "점심 뭐 먹을까", "넌 먹었냐", "밥 먹었어?", "로또", "연애", "운세"는 UNSUPPORTED.
                - WMS 업무 목적이 명확하지 않은 짧은 잡담은 UNSUPPORTED.
                - 단어 하나가 업무 단어처럼 보여도 문장 의도가 업무 조회/매뉴얼이 아니면 UNSUPPORTED.
                - 현재 DB 상태를 봐야 하면 WORK_QUERY.
                - 절차, 위치한 메뉴, 왜 실패하는지, 어떻게 하는지 설명이면 RAG.
                - 최근 대화에 조회 결과가 있고 "하위는?", "오늘은?" 같은 후속 질문이면 WORK_QUERY.
                - 애매하면 UNSUPPORTED.

                JSON 스키마:
                {
                  "action": "WORK_QUERY|RAG|GENERAL|UNSUPPORTED",
                  "confidence": 0.0,
                  "reason": "짧은 내부 사유",
                  "target": "STOCK|MASTER|ACCOUNT 또는 빈 문자열",
                  "intent": "WORK_QUERY일 때 업무 intent. 예: INVENTORY_LOCATION, PENDING_WORK, INBOUND_STATUS",
                  "slots": {"keyword":"","product":"","warehouse":"","status":"","date":"","date_from":"","date_to":""},
                  "answer": "GENERAL 또는 UNSUPPORTED일 때 사용자에게 바로 보여줄 답변. 그 외 빈 문자열"
                }

                응답 규칙:
                - 반드시 JSON object만 출력한다.
                - markdown, 설명, 코드블록 금지.
                - UNSUPPORTED answer는 반드시 "해당 질문은 답변이 불가합니다. 업무, 매뉴얼 등 편하게 물어보세요." 로 출력한다.
                - WORK_QUERY를 선택하면 target, intent, slots를 함께 채운다.
                - STOCK intent: MY_PICKING_TASKS, PENDING_WORK, INBOUND_STATUS, OUTBOUND_STATUS, INVENTORY_LOCATION, LOW_STOCK
                - MASTER intent: PRODUCT_INFO, WAREHOUSE_INFO, LOCATION_INFO, SUPPLIER_INFO, STORE_INFO
                - ACCOUNT intent: USER_INFO, ROLE_INFO, CLIENT_INFO
                """;

        String userPrompt = """
                최근 대화:
                %s

                이전 업무 조회 컨텍스트 존재: %s

                현재 질문:
                %s
                """.formatted(
                historyBlock.isBlank() ? "(없음)" : historyBlock,
                context != null && context.rows() != null && !context.rows().isEmpty(),
                question);

        String response = openAiChatGateway.completeJson(systemPrompt, userPrompt);
        return parseRoutingDecision(response);
    }

    private RoutingDecision parseRoutingDecision(String raw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            String normalized = normalize(raw).toUpperCase(Locale.ROOT);
            if (normalized.contains("WORK_QUERY")) {
                return new RoutingDecision(ChatMode.WORK_QUERY, "llm_work_query", 0.50, null, "", "", Map.of());
            }
            if (normalized.contains("RAG")) {
                return new RoutingDecision(ChatMode.RAG, "llm_rag", 0.50, null, "", "", Map.of());
            }
            if (normalized.contains("GENERAL")) {
                return new RoutingDecision(ChatMode.GENERAL, "llm_general", 0.50, null, "", "", Map.of());
            }
            throw new IllegalArgumentException("라우팅 JSON 파싱 실패: " + raw, e);
        }
        String action = normalize(root.path("action").asText()).toUpperCase(Locale.ROOT);
        double confidence = root.path("confidence").isNumber() ? root.path("confidence").asDouble() : 0.0;
        String reason = normalize(root.path("reason").asText());
        String answer = normalize(root.path("answer").asText());
        String target = normalize(root.path("target").asText());
        String intent = normalize(root.path("intent").asText());
        Map<String, String> slots = parseSlots(root.path("slots"));

        if ("WORK_QUERY".equals(action)) {
            return new RoutingDecision(ChatMode.WORK_QUERY, reason.isBlank() ? "llm_json_work_query" : reason, confidence, null,
                    target, intent, slots);
        }
        if ("RAG".equals(action)) {
            return new RoutingDecision(ChatMode.RAG, reason.isBlank() ? "llm_json_rag" : reason, confidence, null, "", "", Map.of());
        }
        if ("GENERAL".equals(action)) {
            return new RoutingDecision(ChatMode.GENERAL, reason.isBlank() ? "llm_json_general" : reason, confidence, answer, "", "", Map.of());
        }
        if ("UNSUPPORTED".equals(action)) {
            return new RoutingDecision(ChatMode.GENERAL, reason.isBlank() ? "llm_json_unsupported" : reason, confidence,
                    answer.isBlank() ? UNSUPPORTED_ANSWER : answer, "", "", Map.of());
        }
        throw new IllegalArgumentException("지원하지 않는 라우팅 action: " + action);
    }

    private Map<String, String> parseSlots(JsonNode slotsNode) {
        if (slotsNode == null || !slotsNode.isObject()) {
            return Map.of();
        }
        Map<String, String> slots = new LinkedHashMap<>();
        slotsNode.fields().forEachRemaining((entry) -> slots.put(entry.getKey(), normalize(entry.getValue().asText())));
        return slots;
    }

    private String generalAnswer(String question, String userName) {
        String normalized = normalize(question).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "질문을 입력해주시면 재고 위치, 오늘 처리할 지시서, 입고/출고 현황 등을 도와드리겠습니다.";
        }
        if (OFFENSIVE_HINT.matcher(normalized).matches()) {
            return "업무 처리를 돕기 위해 정중한 표현으로 질문해 주세요. 재고 위치, 미처리 지시서, 입고/출고 현황처럼 확인할 내용을 말해주시면 바로 도와드리겠습니다.";
        }
        if (DATE_TIME_HINT.matcher(normalized).matches()) {
            LocalDate today = LocalDate.now(KOREA_ZONE);
            String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
            return "오늘은 %d년 %d월 %d일 %s입니다. 특정 날짜의 처리 작업이 궁금하면 \"5월 8일에 할 일 뭐야?\"처럼 물어보세요."
                    .formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth(), dayOfWeek);
        }
        if (OFF_TOPIC_HINT.matcher(normalized).matches() || BOT_PERSONAL_LIFE_HINT.matcher(normalized).matches()) {
            return UNSUPPORTED_ANSWER;
        }
        if (normalized.contains("휴무") || normalized.contains("휴가") || normalized.contains("연차") || normalized.contains("근태")) {
            return "휴무나 근태 일정은 현재 WMS 챗봇 조회 범위에 포함되어 있지 않습니다. 사내 근태 시스템이나 담당 관리자에게 확인해 주세요.";
        }
        if (normalized.contains("맞춤법") || normalized.contains("띄어쓰기") || normalized.contains("문법 검사")) {
            return "맞춤법 검사는 현재 WMS 업무 챗봇의 지원 범위가 아닙니다. 재고 위치, 지시서 상태, 작업 실패 원인 같은 WMS 업무 질문을 도와드릴 수 있습니다.";
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

    private record RoutingDecision(
            ChatMode mode,
            String reason,
            double confidence,
            String answer,
            String target,
            String intent,
            Map<String, String> slots) {
        private static RoutingDecision workQuery(String reason) {
            return new RoutingDecision(ChatMode.WORK_QUERY, reason, 1.0, null, "", "", Map.of());
        }

        private static RoutingDecision rag(String reason) {
            return new RoutingDecision(ChatMode.RAG, reason, 1.0, null, "", "", Map.of());
        }

        private static RoutingDecision general(String reason, String answer) {
            return new RoutingDecision(ChatMode.GENERAL, reason, 1.0, answer, "", "", Map.of());
        }

        private WorkQueryService.WorkQueryRoute toWorkQueryRoute() {
            return new WorkQueryService.WorkQueryRoute(target, intent, slots, confidence, "chat-router:" + reason);
        }
    }

    private record SensitiveDecision(boolean blocked, String reason) {
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

        public static RouteResult blocked(
                String routeReason,
                String answer,
                Long executionTimeMs) {
            return new RouteResult(
                    ChatMode.BLOCKED,
                    ChatMode.BLOCKED,
                    routeReason,
                    false,
                    "sensitive_info_blocked",
                    false,
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

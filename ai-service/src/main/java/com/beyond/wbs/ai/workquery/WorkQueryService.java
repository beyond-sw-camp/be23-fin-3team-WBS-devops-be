package com.beyond.wbs.ai.workquery;

import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class WorkQueryService {

    private final OpenAiChatGateway openAiChatGateway;
    private final ObjectMapper objectMapper;
    private final StockWorkQueryClient stockWorkQueryClient;
    private final MasterWorkQueryClient masterWorkQueryClient;
    private final AccountWorkQueryClient accountWorkQueryClient;

    private static final int LIMIT = 8;
    private static final Pattern KOREAN_MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern KOREAN_MONTH = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*월(?!\\s*\\d{1,2}\\s*일)");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public WorkQueryService(
            OpenAiChatGateway openAiChatGateway,
            ObjectMapper objectMapper,
            StockWorkQueryClient stockWorkQueryClient,
            MasterWorkQueryClient masterWorkQueryClient,
            AccountWorkQueryClient accountWorkQueryClient) {
        this.openAiChatGateway = openAiChatGateway;
        this.objectMapper = objectMapper;
        this.stockWorkQueryClient = stockWorkQueryClient;
        this.masterWorkQueryClient = masterWorkQueryClient;
        this.accountWorkQueryClient = accountWorkQueryClient;
    }

    public WorkQueryResponse ask(
            String message,
            List<ChatTurn> history,
            WorkQueryContext context,
            String clientId,
            String userId) {
        return askWithRoute(message, history, context, clientId, userId, null);
    }

    public WorkQueryResponse askWithRoute(
            String message,
            List<ChatTurn> history,
            WorkQueryContext context,
            String clientId,
            String userId,
            WorkQueryRoute requestedRoute) {
        long startedAt = System.nanoTime();
        String question = normalize(message);
        if (question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해주세요.");
        }

        FollowUpResult followUp = resolveFollowUp(question, context);
        RouteResult contextualInventoryRoute = followUp == null
                ? resolveContextualInventoryRoute(question, context)
                : null;
        RouteResult externalRoute = followUp == null && contextualInventoryRoute == null
                ? routeFromExternalDecision(question, requestedRoute)
                : null;
        RouteResult route = followUp != null
                ? RouteResult.followUp(followUp.intent())
                : contextualInventoryRoute != null
                ? contextualInventoryRoute
                : externalRoute != null
                ? externalRoute
                : routeWithLlm(question, history);
        Intent intent = route.intent();
        WorkQueryTarget target = route.target();
        log.info("[AI_WORK_QUERY_START] target={}, intent={}, followUp={}, routeSource={}, confidence={}, slots={}, clientId={}, userId={}, question='{}'",
                target, intent, followUp != null, route.source(), route.confidence(), route.slots(),
                mask(clientId), mask(userId), question);

        try {
            long apiStartedAt = System.nanoTime();
	            List<Map<String, Object>> rows = followUp != null
	                    ? followUp.rows()
	                    : callTargetApi(route, clientId, userId);
	            rows = applyQuestionFilters(question, intent, rows);
            long apiMs = elapsedMs(apiStartedAt);
            log.info("[AI_WORK_QUERY_API] target={}, intent={}, followUp={}, rows={}, apiMs={}",
                    target, intent, followUp != null, rows.size(), apiMs);

            AnswerResult answerResult = composeAnswer(question, target, intent, rows, history);
            long totalMs = elapsedMs(startedAt);

            log.info("[AI_WORK_QUERY_END] target={}, intent={}, followUp={}, routeSource={}, rows={}, apiMs={}, llmMs={}, totalMs={}, fallback={}, clientId={}, userId={}, question='{}'",
                    target, intent, followUp != null, route.source(), rows.size(), apiMs, answerResult.llmMs(), totalMs, answerResult.fallback(),
                    mask(clientId), mask(userId), question);
            return new WorkQueryResponse(question, target.name(), intent.name(), answerResult.answer(), rows, followUp != null);
        } catch (Exception e) {
            log.error("[AI_WORK_QUERY_ERROR] target={}, intent={}, followUp={}, totalMs={}, clientId={}, userId={}, question='{}', error={}",
                    target, intent, followUp != null, elapsedMs(startedAt), mask(clientId), mask(userId), question, e.getMessage(), e);
            throw e;
        }
    }

    private RouteResult routeFromExternalDecision(String question, WorkQueryRoute requestedRoute) {
        if (requestedRoute == null || isBlank(requestedRoute.intent())) {
            return null;
        }
        Intent intent = parseIntent(requestedRoute.intent());
        if (intent == null) {
            return null;
        }
        WorkQueryTarget target = parseTarget(requestedRoute.target());
        if (target == null || !supportsIntent(target, intent)) {
            target = defaultTarget(intent);
        }
        Map<String, String> slots = normalizeSlots(requestedRoute.slots(), question);
        RouteResult route = new RouteResult(
                target,
                intent,
                requestedRoute.confidence(),
                slots,
                isBlank(requestedRoute.source()) ? "chat-router" : requestedRoute.source()
        );
        return refineRoute(question, route);
    }

    private RouteResult routeWithLlm(String question, List<ChatTurn> history) {
        if (isActionQuestion(question) && explicitIntent(question) == null) {
            return RouteResult.heuristic(Intent.PENDING_WORK, baseSlots(question));
        }

        String prompt = """
                너는 WMS AI 챗봇의 라우터다.
                사용자 질문을 보고 아래 JSON만 반환한다. 설명, markdown, 코드블록 금지.

                intent 후보:
                [stock-service]
                - MY_PICKING_TASKS: 내 담당 피킹/작업 조회
                - PENDING_WORK: 오늘 처리할 업무/급한 일/우선순위/미처리 지시서 조회
                - INBOUND_STATUS: 입고/검수/적치/들어올 상품 조회
                - OUTBOUND_STATUS: 출고/배송/출하/밀린 출고 조회
                - INVENTORY_LOCATION: 특정 상품 재고 위치/수량 조회
                - LOW_STOCK: 부족/위험/품절/보충 필요 재고 조회

                [master-service]
                - PRODUCT_INFO: 상품/SKU/바코드/상품그룹/카테고리/활성 여부 조회
                - WAREHOUSE_INFO: 창고/창고 타입/담당자/활성 여부 조회
                - LOCATION_INFO: 구역/랙/로케이션/활성 여부/수용량 조회
                - SUPPLIER_INFO: 공급사/협력사/ESG/친환경 인증 조회
                - STORE_INFO: 매장/출고처/자동 웨이브 설정 조회

                [account-service]
                - USER_INFO: 사용자/직원/관리자 계정/활성 여부/역할 조회
                - ROLE_INFO: 역할/권한/permission 조회
                - CLIENT_INFO: 회사/고객사/테넌트 조회

                JSON 스키마:
                {"target":"STOCK","intent":"INVENTORY_LOCATION","confidence":0.0,"slots":{"keyword":"","product":"모니터","warehouse":"부산","status":"","date":""}}

                규칙:
                - intent는 후보 중 하나만.
                - target은 STOCK, MASTER, ACCOUNT 중 하나만.
                - "내/나/담당"과 "피킹"이 함께 있으면 반드시 MY_PICKING_TASKS.
	                - "피킹 작업 뭐야", "내가 오늘 해야 할 피킹"은 PENDING_WORK가 아니라 MY_PICKING_TASKS.
	                - "오늘 뭐부터", "우선 처리", "미처리 지시서"처럼 입고/출고/피킹이 특정되지 않은 질문만 PENDING_WORK.
	                - "마우스 어딨어", "모니터 있나", "키보드 위치 좀"처럼 상품의 위치/존재/수량을 묻는 질문은 INVENTORY_LOCATION.
                - "마우스 출고 가능한 재고 있어?", "키보드 출고 가능 수량 알려줘"처럼 현재 재고/수량/위치를 묻는 질문은 INVENTORY_LOCATION.
                - 출고 방법/절차/실패 원인 설명은 이 서비스의 대상이 아니므로, 들어오더라도 현재 재고 조회 의도로 바꾸지 않는다.
	                - "무선 말고 그냥 마우스", "무선 제외하고 마우스"처럼 제외 표현이 있으면 product는 제외어를 빼고 핵심 상품명만 쓴다. 예: product="마우스".
		                - product, warehouse, status, date는 질문에 있으면 채우고 없으면 빈 문자열.
	                - INVENTORY_LOCATION은 현재 재고 위치 조회이므로 date는 항상 빈 문자열로 둔다.
                - date는 가능하면 yyyy-MM-dd 형식으로 쓴다.
                - "5월", "이번 달"처럼 월 단위 질문이면 slots.date_from, slots.date_to를 yyyy-MM-dd 형식으로 채운다.
                - 오늘/금일은 date를 "today"로 쓴다.
                - 상품/창고/구역/랙/로케이션/공급사/매장 같은 기준 정보는 MASTER다.
                - 사용자/직원/관리자/역할/권한/회사 정보는 ACCOUNT다.
                - 자신 없으면 PENDING_WORK, confidence 0.5.

                최근 대화:
                %s

                사용자 질문: %s
                JSON:
                """.formatted(formatHistory(history), question);

        long routeStartedAt = System.nanoTime();
        try {
            String content = openAiChatGateway.complete(prompt);
            long routeMs = elapsedMs(routeStartedAt);
            RouteResult route = refineRoute(question, parseRoute(content, question));
            log.info("[AI_WORK_QUERY_ROUTE] source=llm, routeMs={}, intent={}, confidence={}, slots={}, raw={}",
                    routeMs, route.intent(), route.confidence(), route.slots(), sanitizeForLog(content));
            return route;
        } catch (Exception e) {
            long routeMs = elapsedMs(routeStartedAt);
            Intent fallbackIntent = classify(question);
            Map<String, String> fallbackSlots = fallbackIntent == Intent.INVENTORY_LOCATION
                    ? inventorySlots(question)
                    : baseSlots(question);
            RouteResult fallback = RouteResult.fallback(fallbackIntent, fallbackSlots);
            log.warn("[AI_WORK_QUERY_ROUTE] source=fallback, routeMs={}, intent={}, error={}",
                    routeMs, fallback.intent(), e.getMessage());
            return fallback;
        }
    }

    private Intent explicitIntent(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (hasAny(q, "권한", "permission", "role", "역할")) {
            return Intent.ROLE_INFO;
        }
        if (hasAny(q, "사용자", "직원", "관리자", "계정", "로그인", "login", "loginid")) {
            return Intent.USER_INFO;
        }
        if (hasAny(q, "회사", "고객사", "테넌트", "client")) {
            return Intent.CLIENT_INFO;
        }
        if (hasAny(q, "내", "나", "담당")
                && hasAny(q, "피킹", "picking")) {
            return Intent.MY_PICKING_TASKS;
        }
        if (hasAny(q, "입고", "입하", "검수", "적치")
                && !hasAny(q, "출고", "피킹")) {
            return Intent.INBOUND_STATUS;
        }
        if (hasAny(q, "출고", "배송", "출하")
                && !hasAny(q, "입고", "검수", "적치")) {
            return Intent.OUTBOUND_STATUS;
        }
        if (hasAny(q, "부족", "위험", "품절", "모자", "재고 부족", "보충", "긴급 발주")) {
            return Intent.LOW_STOCK;
        }
        if (hasAny(q, "어디", "어딨", "어딧", "위치", "찾아", "있나", "있어", "남아", "남았", "몇 개", "몇개", "수량", "보유", "재고")
                && !hasAny(q, "입고", "출고", "지시서", "처리")) {
            return Intent.INVENTORY_LOCATION;
        }
        if (hasAny(q, "공급사", "협력사", "esg", "친환경")) {
            return Intent.SUPPLIER_INFO;
        }
        if (hasAny(q, "매장", "출고처", "웨이브")) {
            return Intent.STORE_INFO;
        }
        if (hasAny(q, "로케이션", "랙", "구역", "존", "zone", "rack", "location")) {
            return Intent.LOCATION_INFO;
        }
        if (hasAny(q, "창고", "센터", "물류센터")) {
            return Intent.WAREHOUSE_INFO;
        }
        if (hasAny(q, "상품", "제품", "sku", "바코드", "카테고리", "상품그룹")) {
            return Intent.PRODUCT_INFO;
        }
        return null;
    }

	    private RouteResult parseRoute(String content, String question) throws Exception {
	        String json = extractJson(content);
	        Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
	        });
	        Intent intent = parseIntent(String.valueOf(map.get("intent")));
        if (intent == null) {
            intent = classify(question);
        }
        double confidence = parseDouble(map.get("confidence"), 0.5);
        Map<String, String> slots = normalizeSlots(map.get("slots"), question);
        WorkQueryTarget target = parseTarget(String.valueOf(map.get("target")));
        if (target == null || !supportsIntent(target, intent)) {
            target = defaultTarget(intent);
        }
	        return new RouteResult(target, intent, confidence, slots, "llm");
	    }

	    private RouteResult refineRoute(String question, RouteResult route) {
	        if (route.intent() != Intent.INVENTORY_LOCATION) {
	            return route;
	        }

	        String product = normalize(route.slots().getOrDefault("product", ""));
	        String extracted = extractProductKeyword(question);
	        if (shouldTrustCurrentProductKeyword(question, extracted)) {
	            product = extracted;
	        } else if (hasExclusionExpression(question) || product.isBlank()) {
	            if (!extracted.isBlank()) {
	                product = extracted;
	            }
	        }

	        List<String> requiredTerms = requiredProductTerms(question);
	        for (String term : requiredTerms) {
	            if (!product.contains(term)) {
	                product = (term + " " + product).trim();
	            }
	        }

	        if (product.isBlank()) {
	            return route;
	        }
	        Map<String, String> slots = new LinkedHashMap<>(route.slots());
	        slots.put("product", product);
	        slots.put("date", "");
	        return new RouteResult(route.target(), route.intent(), route.confidence(), slots, route.source() + "+refined");
	    }

    private String extractJson(String content) {
        String value = normalize(content);
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("라우터 LLM이 JSON을 반환하지 않았습니다.");
        }
        return value.substring(start, end + 1);
    }

    private Map<String, String> normalizeSlots(Object value, String question) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("keyword", "");
        slots.put("product", "");
        slots.put("warehouse", "");
        slots.put("status", "");
        slots.put("date", extractDateKeyword(question));
        DateRange dateRange = extractDateRange(question);
        slots.put("date_from", dateRange.from());
        slots.put("date_to", dateRange.to());
        if (!(value instanceof Map<?, ?> raw)) {
            slots.put("keyword", extractKeyword(question));
            return slots;
        }
        slots.put("keyword", slotValue(raw, "keyword"));
        slots.put("product", slotValue(raw, "product"));
        slots.put("warehouse", slotValue(raw, "warehouse"));
        slots.put("status", slotValue(raw, "status"));
        String date = normalizeDateSlot(slotValue(raw, "date"), question);
        if (!date.isBlank()) {
            slots.put("date", date);
        }
        String dateFrom = normalizeDateSlot(slotValue(raw, "date_from"), "");
        String dateTo = normalizeDateSlot(slotValue(raw, "date_to"), "");
        if (!dateRange.from().isBlank()) {
            slots.put("date_from", dateRange.from());
            slots.put("date_to", dateRange.to());
        } else {
            slots.put("date_from", dateFrom);
            slots.put("date_to", dateTo);
        }
        if (slots.get("keyword").isBlank()) {
            slots.put("keyword", firstNonBlank(slots.get("product"), slots.get("warehouse"), extractKeyword(question)));
        }
        return slots;
    }

    private String slotValue(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            return "";
        }
        return normalize(String.valueOf(value));
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private FollowUpResult resolveFollowUp(String question, WorkQueryContext context) {
        if (context == null || context.rows() == null || context.rows().isEmpty() || !isFollowUpQuestion(question)) {
            return null;
        }
        String q = question.toLowerCase(Locale.ROOT);
        if (!hasFollowUpReference(q) && explicitIntent(question) != null) {
            return null;
        }
        Intent previousIntent = parseIntent(context.intent());
        if (previousIntent == null) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>(context.rows());

        if (q.contains("첫") || q.contains("1번") || q.contains("첫번째") || q.contains("첫 번째")) {
            rows = rows.stream().limit(1).toList();
        } else if (q.contains("두") || q.contains("2번") || q.contains("두번째") || q.contains("두 번째")) {
            rows = rows.size() >= 2 ? List.of(rows.get(1)) : List.of();
        }

        if (q.contains("진행") || q.contains("하는 중")) {
            rows = filterByStatus(rows, "in_progress", "picking", "placing", "receiving");
        } else if (q.contains("대기") || q.contains("남은") || q.contains("해야")) {
            rows = filterByStatus(rows, "pending", "approved", "draft");
        } else if (q.contains("완료")) {
            rows = filterByStatus(rows, "completed", "received");
        }

        if (q.contains("입고만") || q.contains("입고 것") || q.contains("입고 건")) {
            rows = filterByValue(rows, "work_type", "입고");
        } else if (q.contains("출고만") || q.contains("출고 것") || q.contains("출고 건")) {
            rows = filterByValue(rows, "work_type", "출고");
        }

        return new FollowUpResult(previousIntent, rows);
    }

    private RouteResult resolveContextualInventoryRoute(String question, WorkQueryContext context) {
        Intent previousIntent = context == null ? null : parseIntent(context.intent());
        if (previousIntent != Intent.INVENTORY_LOCATION || context.rows() == null || context.rows().isEmpty()) {
            return null;
        }
        List<String> modifiers = requiredProductTerms(question);
        if (modifiers.isEmpty() || !isModifierOnlyQuestion(question, modifiers)) {
            return null;
        }
        String baseProduct = inferBaseProductFromContext(context.rows());
        if (baseProduct.isBlank()) {
            return null;
        }
        String product = modifiers.get(0) + " " + baseProduct;
        return RouteResult.heuristic(Intent.INVENTORY_LOCATION, Map.of(
                "product", product,
                "warehouse", extractWarehouseKeyword(question),
                "date", extractDateKeyword(question)
        ));
    }

    private boolean isModifierOnlyQuestion(String question, List<String> modifiers) {
        String normalized = normalize(question)
                .replaceAll("[?？!！.,]", " ")
                .replaceAll("(은|는|이|가|을|를|도|에|정보|알려줘|어디|어딨어|있어|재고|수량)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return false;
        }
        for (String modifier : modifiers) {
            if (normalized.equals(modifier)) {
                return true;
            }
        }
        return false;
    }

    private String inferBaseProductFromContext(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String productName = normalize(String.valueOf(row.getOrDefault("product_name", "")))
                    .replace("무선", " ")
                    .replace("유선", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (productName.contains("마우스")) {
                return "마우스";
            }
            if (!productName.isBlank()) {
                return productName;
            }
        }
        return "";
    }

    private boolean isFollowUpQuestion(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("그중")
                || q.contains("그 중")
                || q.contains("위에")
                || q.contains("방금")
                || q.contains("첫")
                || q.contains("두")
                || q.contains("1번")
                || q.contains("2번")
                || q.contains("진행 중")
                || q.contains("진행중")
                || q.contains("대기")
                || q.contains("완료")
                || q.contains("입고만")
                || q.contains("출고만")
                || q.contains("그거")
                || q.contains("그것");
    }

    private boolean hasFollowUpReference(String q) {
        return q.contains("그중")
                || q.contains("그 중")
                || q.contains("위에")
                || q.contains("방금")
                || q.contains("첫")
                || q.contains("두")
                || q.contains("1번")
                || q.contains("2번")
                || q.contains("그거")
                || q.contains("그것");
    }

    private List<Map<String, Object>> filterByStatus(List<Map<String, Object>> rows, String... statuses) {
        return rows.stream()
                .filter(row -> {
                    String status = normalize(String.valueOf(row.getOrDefault("status", ""))).toLowerCase(Locale.ROOT);
                    for (String candidate : statuses) {
                        if (status.equals(candidate)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    private List<Map<String, Object>> filterByValue(List<Map<String, Object>> rows, String key, String value) {
        return rows.stream()
                .filter(row -> value.equals(String.valueOf(row.get(key))))
                .toList();
    }

    private Intent parseIntent(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Intent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private WorkQueryTarget parseTarget(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return WorkQueryTarget.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private WorkQueryTarget defaultTarget(Intent intent) {
        return switch (intent) {
            case PRODUCT_INFO, WAREHOUSE_INFO, LOCATION_INFO, SUPPLIER_INFO, STORE_INFO -> WorkQueryTarget.MASTER;
            case USER_INFO, ROLE_INFO, CLIENT_INFO -> WorkQueryTarget.ACCOUNT;
            default -> WorkQueryTarget.STOCK;
        };
    }

    private boolean supportsIntent(WorkQueryTarget target, Intent intent) {
        return defaultTarget(intent) == target;
    }

    private Intent classify(String question) {
        String q = question.toLowerCase(Locale.ROOT);

        if (hasAny(q, "권한", "permission", "role", "역할")) {
            return Intent.ROLE_INFO;
        }
        if (hasAny(q, "사용자", "직원", "관리자", "계정", "로그인", "login", "loginid")) {
            return Intent.USER_INFO;
        }
        if (hasAny(q, "회사", "고객사", "테넌트", "client")) {
            return Intent.CLIENT_INFO;
        }
        if (hasAny(q, "부족", "위험", "품절", "모자", "재고 부족", "보충", "긴급 발주")) {
            return Intent.LOW_STOCK;
        }
        if (hasAny(q, "내", "나", "담당", "할 일", "해야 할", "해야될", "해야 돼", "해야돼")
                && hasAny(q, "피킹", "작업", "업무")) {
            return Intent.MY_PICKING_TASKS;
        }
        if (hasAny(q, "어디", "어딨", "어딧", "위치", "찾아", "있나", "있어", "남아", "남았", "몇 개", "몇개", "수량", "보유", "재고")
                && !hasAny(q, "입고", "출고", "지시서", "처리")) {
            return Intent.INVENTORY_LOCATION;
        }
        if (hasAny(q, "입고", "입하", "검수", "적치", "기다리는 상품", "들어올", "들어오는")) {
            return Intent.INBOUND_STATUS;
        }
        if (hasAny(q, "출고", "피킹", "배송", "출하", "밀린 출고", "나갈")) {
            return Intent.OUTBOUND_STATUS;
        }
        if (hasAny(q, "처리", "미처리", "지시서", "오늘", "급한", "우선", "뭐부터", "먼저", "해야")) {
            return Intent.PENDING_WORK;
        }
        if (hasAny(q, "공급사", "협력사", "esg", "친환경")) {
            return Intent.SUPPLIER_INFO;
        }
        if (hasAny(q, "매장", "출고처", "웨이브")) {
            return Intent.STORE_INFO;
        }
        if (hasAny(q, "로케이션", "랙", "구역", "존", "zone", "rack", "location")) {
            return Intent.LOCATION_INFO;
        }
        if (hasAny(q, "창고", "센터", "물류센터")) {
            return Intent.WAREHOUSE_INFO;
        }
        if (hasAny(q, "상품", "제품", "sku", "바코드", "카테고리", "상품그룹")) {
            return Intent.PRODUCT_INFO;
        }
        return Intent.PENDING_WORK;
    }

    private List<Map<String, Object>> callTargetApi(RouteResult route, String clientId, String userId) {
        return switch (route.target()) {
            case STOCK -> {
                StockWorkQueryClient.WorkQueryApiResponse response = stockWorkQueryClient.execute(
                        clientId,
                        userId,
                        new StockWorkQueryClient.WorkQueryApiRequest(route.intent().name(), route.slots(), LIMIT)
                );
                yield response.rows() == null ? List.of() : response.rows();
            }
            case MASTER -> {
                MasterWorkQueryClient.WorkQueryApiResponse response = masterWorkQueryClient.execute(
                        clientId,
                        userId,
                        new MasterWorkQueryClient.WorkQueryApiRequest(route.intent().name(), route.slots(), LIMIT)
                );
                yield response.rows() == null ? List.of() : response.rows();
            }
            case ACCOUNT -> {
                AccountWorkQueryClient.WorkQueryApiResponse response = accountWorkQueryClient.execute(
                        clientId,
                        userId,
                        new AccountWorkQueryClient.WorkQueryApiRequest(route.intent().name(), route.slots(), LIMIT)
                );
                yield response.rows() == null ? List.of() : response.rows();
            }
        };
    }

	    private List<Map<String, Object>> applyQuestionFilters(String question, Intent intent, List<Map<String, Object>> rows) {
	        if (intent != Intent.INVENTORY_LOCATION || rows.isEmpty()) {
	            return rows;
	        }
	        List<String> required = requiredProductTerms(question);
	        List<String> excluded = excludedProductTerms(question);
	        if (required.isEmpty() && excluded.isEmpty()) {
	            return rows;
	        }
	        return rows.stream()
	                .filter(row -> {
	                    String productName = normalize(String.valueOf(row.getOrDefault("product_name", "")));
	                    for (String term : required) {
	                        if (!term.isBlank() && !productName.contains(term)) {
	                            return false;
	                        }
	                    }
	                    for (String term : excluded) {
	                        if (!term.isBlank() && productName.contains(term)) {
	                            return false;
	                        }
	                    }
	                    return true;
	                })
	                .toList();
	    }

    private AnswerResult composeAnswer(String question, WorkQueryTarget target, Intent intent, List<Map<String, Object>> rows, List<ChatTurn> history) {
        if (rows.isEmpty()) {
            return new AnswerResult(emptyAnswer(question, intent), 0, true);
        }

        StringBuilder rowText = new StringBuilder();
        for (int i = 0; i < Math.min(rows.size(), LIMIT); i++) {
            rowText.append(i + 1).append(". ");
            rows.get(i).forEach((key, value) -> {
                if (!isHiddenAnswerField(key)) {
                    rowText.append(key).append("=").append(formatValue(value)).append(", ");
                }
            });
            rowText.append('\n');
        }

        String prompt = """
                역할: 전자기기 WMS 업무 조회 챗봇.
                임무: 업무 서비스 API 조회 결과만 근거로 자연스럽고 간결한 한국어 답변을 작성한다.
                형식: 1~2문장. 내부 컬럼명, SQL, UUID 금지. 추측 금지. 조회 결과에 없는 사실 금지.
                공통 응답 원칙:
                - 사용자가 묻지 않은 조치, 절차, 원인, 권고를 덧붙이지 않는다.
                - "필요한 작업을 진행해야 합니다", "확인해야 합니다" 같은 일반 조치 문장은 사용자가 할 일을 물은 경우에만 쓴다.
                - 수량 표현은 자연스럽게 쓴다. 예: "100개가 가용합니다" 금지, "가용 재고는 100개입니다" 사용.
                - "총 500개"와 "가용 재고 100개"를 혼동하지 않는다. 위치/출고 가능 여부 질문에는 가용 재고를 우선 말한다.
                - 날짜를 물은 경우 사용자 질문의 날짜에 대한 답으로 말한다.
                재고 위치 답변 원칙:
                - INVENTORY_LOCATION이면 상품명, 창고명, 로케이션 또는 위치를 먼저 말하고 가용 재고를 이어서 말한다.
                - 같은 상품이 여러 행이면 available_qty가 0보다 큰 행을 우선 답한다.
                - available_qty가 0인 행은 "가용 재고가 있는 위치" 또는 "다른 위치 재고"로 절대 말하지 않는다.
                - total_qty가 있어도 available_qty가 0이면 사용자가 찾는 가용 재고 위치가 아니다.
                - location_code가 "-"이거나 "위치 미지정"이면 실제 위치로 말하지 않는다. rack_code, zone_name, zone_code도 모두 없을 때만 "위치 미지정"이라고 말한다.
                - 사용자가 "다른 위치", "또 다른 위치"를 물으면 최근 대화에서 이미 답한 위치를 제외하고 available_qty가 0보다 크며 실제 위치가 있는 행만 답한다.
                - 위 조건을 만족하는 다른 행이 없으면 "다른 위치에는 재고가 없습니다."라고만 답한다.
                - 위 조건을 만족하는 다른 행이 있으면 그 위치와 가용 재고만 답하고 임의 조치를 붙이지 않는다.
                상태 해석:
                - approved는 완료가 아니라 승인 완료 후 처리 대기 상태다.
                - pending/draft는 대기, in_progress/picking/placing은 진행 중, completed/received는 완료다.
                - 사용자가 "내 피킹"을 물으면 피킹 작업만 답하고 입고/출고 지시서를 섞지 않는다.
                - 담당자 정보가 있으면 이름만 말한다. 로그인 ID, 이메일, 전화번호 같은 개인 식별자는 말하지 않는다.
                금지 답변 예시:
                - "100개가 가용합니다."
                - "필요한 작업을 진행해야 합니다."
                - "다른 위치에는 재고가 없습니다. 필요한 작업을 진행해야 합니다."

                사용자 질문: %s
                조회 대상 서비스: %s
                질문 의도: %s
                최근 대화: %s
                조회 결과:
                %s

                답변:
                """.formatted(question, target, intent.label, formatHistory(history), rowText);

        try {
            long llmStartedAt = System.nanoTime();
            log.info("[AI_WORK_QUERY_LLM_START] intent={}, rows={}", intent, rows.size());
            String answer = openAiChatGateway.complete(prompt);
            long llmMs = elapsedMs(llmStartedAt);
            log.info("[AI_WORK_QUERY_LLM_END] intent={}, llmMs={}, blank={}", intent, llmMs, isBlank(answer));
            if (!isBlank(answer)) {
                if (isActionQuestion(question) && intent == Intent.PENDING_WORK
                        && !answer.trim().endsWith("해야 합니다.")) {
                    return new AnswerResult(actionAnswer(question, rows), llmMs, true);
                }
                return new AnswerResult(answer.trim(), llmMs, false);
            }
            log.warn("[AI_WORK_QUERY] llm_summary_blank intent={}, rows={}", intent, rows.size());
            return new AnswerResult(fallbackAnswer(intent, rows), llmMs, true);
        } catch (Exception e) {
            log.warn("[AI_WORK_QUERY] llm_summary_failed: {}", e.getMessage());
            return new AnswerResult(fallbackAnswer(intent, rows), 0, true);
        }
    }

    private String fallbackAnswer(Intent intent, List<Map<String, Object>> rows) {
        Map<String, Object> first = rows.get(0);
        return switch (intent) {
            case MY_PICKING_TASKS -> "담당 피킹 작업 %d건이 있습니다. 우선 %s에서 %s 상태 작업을 확인하세요."
                    .formatted(rows.size(), formatValue(first.get("warehouse_name")), formatValue(first.get("status")));
            case INVENTORY_LOCATION -> "%s 재고는 %s의 %s에 있습니다. 가용 %s개, 예약 %s개입니다."
                    .formatted(formatValue(first.get("product_name")), formatValue(first.get("warehouse_name")),
                            formatValue(first.get("location_code")), formatValue(first.get("available_qty")),
                            formatValue(first.get("reserved_qty")));
            case LOW_STOCK -> "부족 위험 재고 %d건이 있습니다. 우선 %s의 가용 재고가 %s개입니다."
                    .formatted(rows.size(), formatValue(first.get("product_name")), formatValue(first.get("available_qty")));
            case INBOUND_STATUS -> "입고 처리 대상 %d건이 있습니다. 우선 %s는 %s 상태입니다."
                    .formatted(rows.size(), formatValue(first.get("order_no")), formatValue(first.get("status")));
            case OUTBOUND_STATUS -> "출고 처리 대상 %d건이 있습니다. 우선 %s는 %s 상태입니다."
                    .formatted(rows.size(), formatValue(first.get("order_no")), formatValue(first.get("status")));
            case PENDING_WORK -> "처리 필요 지시서 %d건이 있습니다. 우선 %s%s %s를 %s에서 처리해야 합니다."
                    .formatted(rows.size(), formatValue(first.get("work_type")),
                            assigneeActionPhrase(first),
                            formatValue(first.get("document_no")),
                            formatValue(first.get("warehouse_name")));
            case PRODUCT_INFO -> "상품 %d건이 조회됩니다. 우선 %s의 SKU는 %s이고 활성 상태는 %s입니다."
                    .formatted(rows.size(), formatValue(first.get("product_name")), formatValue(first.get("sku")),
                            formatValue(first.get("is_active")));
            case WAREHOUSE_INFO -> "창고 %d건이 조회됩니다. 우선 %s(%s)는 %s 타입입니다."
                    .formatted(rows.size(), formatValue(first.get("warehouse_name")), formatValue(first.get("warehouse_code")),
                            formatValue(first.get("warehouse_type")));
            case LOCATION_INFO -> "로케이션/랙 정보 %d건이 조회됩니다. 우선 %s 창고의 %s/%s를 확인하세요."
                    .formatted(rows.size(), formatValue(first.get("warehouse_name")), formatValue(first.get("zone_name")),
                            formatValue(first.get("location_code")));
            case SUPPLIER_INFO -> "공급사 %d건이 조회됩니다. 우선 %s의 ESG 등급은 %s입니다."
                    .formatted(rows.size(), formatValue(first.get("supplier_name")), formatValue(first.get("esg_grade")));
            case STORE_INFO -> "매장/출고처 %d건이 조회됩니다. 우선 %s(%s)의 자동 웨이브 설정은 %s입니다."
                    .formatted(rows.size(), formatValue(first.get("store_name")), formatValue(first.get("store_code")),
                            formatValue(first.get("auto_wave_enabled")));
            case USER_INFO -> "사용자 %d건이 조회됩니다. 우선 %s의 역할은 %s이고 활성 상태는 %s입니다."
                    .formatted(rows.size(), formatValue(first.get("user_name")), formatValue(first.get("role_name")),
                            formatValue(first.get("is_active")));
            case ROLE_INFO -> "역할 %d건이 조회됩니다. 우선 %s(%s)는 권한 %s개를 포함합니다."
                    .formatted(rows.size(), formatValue(first.get("role_name")), formatValue(first.get("role_code")),
                            formatValue(first.get("permission_count")));
            case CLIENT_INFO -> "고객사 %d건이 조회됩니다. 우선 %s의 활성 상태는 %s입니다."
                    .formatted(rows.size(), formatValue(first.get("client_name")), formatValue(first.get("is_active")));
        };
    }

    private String emptyAnswer(String question, Intent intent) {
        String topic = requestedDateTopic(question);
        return switch (intent) {
            case PENDING_WORK -> "%s 처리할 작업이 존재하지 않습니다.".formatted(topic);
            case MY_PICKING_TASKS -> "%s 담당 피킹 작업이 존재하지 않습니다.".formatted(topic);
            case INBOUND_STATUS -> "%s 조회되는 입고 작업이 없습니다.".formatted(topic);
            case OUTBOUND_STATUS -> "%s 조회되는 출고 작업이 없습니다.".formatted(topic);
            case LOW_STOCK -> "현재 부족 위험 재고가 없습니다.";
            case PRODUCT_INFO -> "조건에 맞는 상품 기준 정보를 찾지 못했습니다.";
            case WAREHOUSE_INFO -> "조건에 맞는 창고 기준 정보를 찾지 못했습니다.";
            case LOCATION_INFO -> "조건에 맞는 구역/랙/로케이션 정보를 찾지 못했습니다.";
            case SUPPLIER_INFO -> "조건에 맞는 공급사 정보를 찾지 못했습니다.";
            case STORE_INFO -> "조건에 맞는 매장/출고처 정보를 찾지 못했습니다.";
            case USER_INFO -> "조건에 맞는 사용자 계정 정보를 찾지 못했습니다.";
            case ROLE_INFO -> "조건에 맞는 역할/권한 정보를 찾지 못했습니다.";
            case CLIENT_INFO -> "조건에 맞는 고객사 정보를 찾지 못했습니다.";
            case INVENTORY_LOCATION -> {
                String product = extractProductKeyword(question);
                if (product.isBlank()) {
                    yield "조건에 맞는 재고 위치를 찾지 못했습니다.";
                }
                yield "%s 재고 위치를 찾지 못했습니다.".formatted(product);
            }
        };
    }

    private String actionAnswer(String question, List<Map<String, Object>> rows) {
        Map<String, Object> first = rows.get(0);
        String label = requestedDateLabel(question);
        String prefix = label.isBlank() ? "우선" : label + "에는 우선";
        return "%s %s%s %s를 %s에서 처리해야 합니다."
                .formatted(prefix, formatValue(first.get("work_type")),
                        assigneeActionPhrase(first),
                        formatValue(first.get("document_no")), formatValue(first.get("warehouse_name")));
    }

    private String assigneeActionPhrase(Map<String, Object> row) {
        String name = formatValue(row.get("assigned_to_name"));
        if ("-".equals(name)) {
            return "";
        }
        return " 담당자 %s가".formatted(name);
    }

    private boolean isHiddenAnswerField(String key) {
        return "assigned_to".equals(key)
                || "assigned_to_login_id".equals(key)
                || "login_id".equals(key)
                || "email".equals(key)
                || "phone".equals(key);
    }

    private boolean isActionQuestion(String question) {
        String q = normalize(question);
        return q.contains("뭐해야")
                || q.contains("뭐 해야")
                || q.contains("해야돼")
                || q.contains("해야 돼")
                || q.contains("해야해")
                || q.contains("해야 해")
                || q.contains("할 일")
                || q.contains("할일");
    }

	    private String extractProductKeyword(String question) {
	        String source = stripExclusionPrefix(question);
	        String keyword = source
	                .replaceAll("(?i)재고|어디에|어디|위치|있어|있나요|알려줘|몇\\s*개|몇개|남았어|남아|보유|수량|상품|제품|센터|창고|물류센터|부산|서울|대전", " ")
	                .replaceAll("(?i)어딨어|어딨니|어딨냐|어딨지|어딧어|있나|찾아줘|찾아|위치\\s*좀", " ")
	                .replaceAll("(?i)출고|출하|배송|피킹|어떻게|방법|절차|진행|원해|하려고|하고\\s*싶어|하고싶어|해줘|해주세요|해", " ")
	                .replaceAll("(?i)그냥|일반|일반적인|말고|제외하고|제외한|빼고|아닌", " ")
	                .replaceAll("[?？!！.,]", " ")
	                .replaceAll("\\s+", " ")
	                .trim();
	        return keyword.replaceAll("(은|는|이|가|을|를|도|에)$", "").trim();
	    }

	    private boolean shouldTrustCurrentProductKeyword(String question, String extracted) {
	        if (extracted.isBlank()) {
	            return false;
	        }
	        List<String> modifiers = requiredProductTerms(question);
	        if (!modifiers.isEmpty() && isModifierOnlyQuestion(question, modifiers)) {
	            return false;
	        }
	        return hasAny(question, "어디", "어딨", "어딧", "위치", "찾아", "있나", "있어", "남아",
	                "남았", "몇 개", "몇개", "수량", "보유", "재고", "정보");
	    }

	    private boolean hasExclusionExpression(String question) {
	        String q = normalize(question);
	        return q.contains("말고") || q.contains("제외하고") || q.contains("제외한")
	                || q.contains("빼고") || q.contains("아닌");
	    }

	    private String stripExclusionPrefix(String question) {
	        String q = normalize(question);
	        String[] markers = {"말고", "제외하고", "제외한", "빼고", "아닌"};
	        for (String marker : markers) {
	            int idx = q.indexOf(marker);
	            if (idx >= 0) {
	                return q.substring(idx + marker.length()).trim();
	            }
	        }
	        return q;
	    }

	    private List<String> excludedProductTerms(String question) {
	        String q = normalize(question);
	        String[] markers = {"말고", "제외하고", "제외한", "빼고", "아닌"};
	        for (String marker : markers) {
	            int idx = q.indexOf(marker);
	            if (idx <= 0) {
	                continue;
	            }
	            String excluded = q.substring(0, idx)
	                    .replaceAll("(?i)재고|상품|제품|그|저|이", " ")
	                    .replaceAll("[?？!！.,]", " ")
	                    .replaceAll("\\s+", " ")
	                    .trim();
	            if (!excluded.isBlank()) {
	                return List.of(excluded);
	            }
	        }
	        return List.of();
	    }

	    private List<String> requiredProductTerms(String question) {
	        String q = normalize(question);
	        if (q.contains("유선")) {
	            return List.of("유선");
	        }
	        if (q.contains("무선")) {
	            return List.of("무선");
	        }
	        return List.of();
	    }

    private String extractWarehouseKeyword(String question) {
        if (question.contains("부산")) {
            return "부산";
        }
        if (question.contains("서울")) {
            return "서울";
        }
        if (question.contains("대전")) {
            return "대전";
        }
        if (question.contains("인천")) {
            return "인천";
        }
        return "";
    }

    private Map<String, String> baseSlots(String question) {
        return Map.of(
                "keyword", extractKeyword(question),
                "date", extractDateKeyword(question),
                "date_from", extractDateRange(question).from(),
                "date_to", extractDateRange(question).to()
        );
    }

    private Map<String, String> inventorySlots(String question) {
        return Map.of(
                "keyword", extractProductKeyword(question),
                "product", extractProductKeyword(question),
                "warehouse", extractWarehouseKeyword(question),
                "date", "",
                "date_from", "",
                "date_to", ""
        );
    }

    private String extractKeyword(String question) {
        return normalize(question)
                .replaceAll("(?i)알려줘|조회|정보|목록|어디|있어|있나요|몇\\s*개|몇개|보여줘|찾아줘|찾아|현재|상태", " ")
                .replaceAll("[?？!！.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractDateKeyword(String question) {
        String q = normalize(question);
        LocalDate today = LocalDate.now();
        if (q.contains("오늘") || q.contains("금일")) {
            return "today";
        }
        if (q.contains("내일")) {
            return today.plusDays(1).toString();
        }
        if (q.contains("어제")) {
            return today.minusDays(1).toString();
        }
        Matcher isoMatcher = ISO_DATE.matcher(q);
        if (isoMatcher.find()) {
            return isoMatcher.group();
        }
        Matcher matcher = KOREAN_MONTH_DAY.matcher(q);
        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            int day = Integer.parseInt(matcher.group(2));
            return LocalDate.of(today.getYear(), month, day).toString();
        }
        return "";
    }

    private DateRange extractDateRange(String question) {
        String q = normalize(question);
        LocalDate today = LocalDate.now();
        if (q.contains("이번달") || q.contains("이번 달")) {
            LocalDate firstDay = today.withDayOfMonth(1);
            LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            return new DateRange(firstDay.toString(), lastDay.toString());
        }
        if (q.contains("다음달") || q.contains("다음 달")) {
            LocalDate firstDay = today.plusMonths(1).withDayOfMonth(1);
            LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            return new DateRange(firstDay.toString(), lastDay.toString());
        }
        if (q.contains("지난달") || q.contains("지난 달")) {
            LocalDate firstDay = today.minusMonths(1).withDayOfMonth(1);
            LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            return new DateRange(firstDay.toString(), lastDay.toString());
        }
        Matcher matcher = KOREAN_MONTH.matcher(q);
        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            LocalDate firstDay = LocalDate.of(today.getYear(), month, 1);
            LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            return new DateRange(firstDay.toString(), lastDay.toString());
        }
        return new DateRange("", "");
    }

    private String normalizeDateSlot(String value, String question) {
        String fallback = extractDateKeyword(question);
        if (!fallback.isBlank()) {
            return fallback;
        }
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        if ("today".equals(normalized)) {
            return "today";
        }
        if (ISO_DATE.matcher(normalized).matches()) {
            return normalized;
        }
        String parsed = extractDateKeyword(normalized);
        return parsed;
    }

    private String requestedDateLabel(String question) {
        String date = extractDateKeyword(question);
        if (date.isBlank()) {
            return "";
        }
        String q = normalize(question);
        if ("today".equals(date)) {
            return "오늘";
        }
        if (q.contains("내일")) {
            return "내일";
        }
        if (q.contains("어제")) {
            return "어제";
        }
        LocalDate parsed = LocalDate.parse(date);
        return parsed.getMonthValue() + "월 " + parsed.getDayOfMonth() + "일";
    }

    private String requestedDateTopic(String question) {
        String label = requestedDateLabel(question);
        if (label.isBlank()) {
            DateRange range = extractDateRange(question);
            if (!range.from().isBlank()) {
                LocalDate from = LocalDate.parse(range.from());
                label = from.getMonthValue() + "월";
            }
        }
        if (label.isBlank()) {
            return "현재는";
        }
        if ("오늘".equals(label) || "내일".equals(label) || "어제".equals(label)) {
            return label + "은";
        }
        return label + "에는";
    }

    private boolean hasAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String formatHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 4);
        for (int i = start; i < history.size(); i++) {
            ChatTurn turn = history.get(i);
            sb.append(turn.role()).append(": ").append(normalize(turn.content())).append('\n');
        }
        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "-";
        if (value instanceof byte[]) return "<id>";
        return value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String mask(String value) {
        if (isBlank(value) || value.length() < 8) return value;
        return value.substring(0, 8) + "...";
    }

    private String sanitizeForLog(String value) {
        return normalize(value).replaceAll("[\\r\\n]+", " ");
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record DateRange(String from, String to) {
    }

    private enum WorkQueryTarget {
        STOCK,
        MASTER,
        ACCOUNT
    }

    private enum Intent {
        MY_PICKING_TASKS("내 피킹 작업 조회"),
        PENDING_WORK("처리 필요 지시서 조회"),
        INBOUND_STATUS("입고 현황 조회"),
        OUTBOUND_STATUS("출고 현황 조회"),
        INVENTORY_LOCATION("상품 재고 위치 조회"),
        LOW_STOCK("부족 재고 조회"),
        PRODUCT_INFO("상품 기준 정보 조회"),
        WAREHOUSE_INFO("창고 기준 정보 조회"),
        LOCATION_INFO("구역/랙/로케이션 기준 정보 조회"),
        SUPPLIER_INFO("공급사 기준 정보 조회"),
        STORE_INFO("매장/출고처 기준 정보 조회"),
        USER_INFO("사용자 계정 조회"),
        ROLE_INFO("역할/권한 조회"),
        CLIENT_INFO("고객사 조회");

        private final String label;

        Intent(String label) {
            this.label = label;
        }
    }

    public record WorkQueryResponse(
            String question,
            String target,
            String intent,
            String answer,
            List<Map<String, Object>> rows,
            boolean followUp) {
    }

    public record WorkQueryContext(
            String intent,
            String answer,
            List<Map<String, Object>> rows) {
    }

    public record WorkQueryRoute(
            String target,
            String intent,
            Map<String, String> slots,
            double confidence,
            String source) {
    }

    private record AnswerResult(String answer, long llmMs, boolean fallback) {
    }

    private record FollowUpResult(Intent intent, List<Map<String, Object>> rows) {
    }

    private record RouteResult(WorkQueryTarget target, Intent intent, double confidence, Map<String, String> slots, String source) {
        private static RouteResult heuristic(Intent intent, Map<String, String> slots) {
            return new RouteResult(defaultTargetStatic(intent), intent, 1.0, slots, "heuristic");
        }

        private static RouteResult fallback(Intent intent, Map<String, String> slots) {
            return new RouteResult(defaultTargetStatic(intent), intent, 0.0, slots, "fallback");
        }

        private static RouteResult followUp(Intent intent) {
            return new RouteResult(defaultTargetStatic(intent), intent, 1.0, Map.of(), "followUp");
        }

        private static WorkQueryTarget defaultTargetStatic(Intent intent) {
            return switch (intent) {
                case PRODUCT_INFO, WAREHOUSE_INFO, LOCATION_INFO, SUPPLIER_INFO, STORE_INFO -> WorkQueryTarget.MASTER;
                case USER_INFO, ROLE_INFO, CLIENT_INFO -> WorkQueryTarget.ACCOUNT;
                default -> WorkQueryTarget.STOCK;
            };
        }
    }
}

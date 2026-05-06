package com.beyond.wbs.ai.workquery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class WorkQueryService {

    private final ChatClient chatClient;
    private final JdbcTemplate readonlyJdbc;
    private final ObjectMapper objectMapper;

    private static final int LIMIT = 8;

    public WorkQueryService(
            ChatClient chatClient,
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbc,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.readonlyJdbc = readonlyJdbc;
        this.objectMapper = objectMapper;
    }

    public WorkQueryResponse ask(
            String message,
            List<ChatTurn> history,
            WorkQueryContext context,
            String clientId,
            String userId) {
        long startedAt = System.nanoTime();
        String question = normalize(message);
        if (question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해주세요.");
        }

        FollowUpResult followUp = resolveFollowUp(question, context);
        RouteResult route = followUp != null
                ? RouteResult.followUp(followUp.intent())
                : routeWithLlm(question, history);
        Intent intent = route.intent();
        log.info("[AI_WORK_QUERY_START] intent={}, followUp={}, routeSource={}, confidence={}, slots={}, clientId={}, userId={}, question='{}'",
                intent, followUp != null, route.source(), route.confidence(), route.slots(),
                mask(clientId), mask(userId), question);

        try {
            long dbStartedAt = System.nanoTime();
            List<Map<String, Object>> rows = followUp != null
                    ? followUp.rows()
                    : query(route, question, clientId, userId);
            long dbMs = elapsedMs(dbStartedAt);
            log.info("[AI_WORK_QUERY_DB] intent={}, followUp={}, rows={}, dbMs={}",
                    intent, followUp != null, rows.size(), dbMs);

            AnswerResult answerResult = composeAnswer(question, intent, rows, history);
            long totalMs = elapsedMs(startedAt);

            log.info("[AI_WORK_QUERY_END] intent={}, followUp={}, routeSource={}, rows={}, dbMs={}, llmMs={}, totalMs={}, fallback={}, clientId={}, userId={}, question='{}'",
                    intent, followUp != null, route.source(), rows.size(), dbMs, answerResult.llmMs(), totalMs, answerResult.fallback(),
                    mask(clientId), mask(userId), question);
            return new WorkQueryResponse(question, intent.name(), answerResult.answer(), rows, followUp != null);
        } catch (Exception e) {
            log.error("[AI_WORK_QUERY_ERROR] intent={}, followUp={}, totalMs={}, clientId={}, userId={}, question='{}', error={}",
                    intent, followUp != null, elapsedMs(startedAt), mask(clientId), mask(userId), question, e.getMessage(), e);
            throw e;
        }
    }

    private RouteResult routeWithLlm(String question, List<ChatTurn> history) {
        RouteResult deterministic = routeByExplicitKeywords(question);
        if (deterministic != null) {
            log.info("[AI_WORK_QUERY_ROUTE] source=rule, routeMs=0, intent={}, confidence={}, slots={}",
                    deterministic.intent(), deterministic.confidence(), deterministic.slots());
            return deterministic;
        }

        String prompt = """
                너는 WMS AI 챗봇의 라우터다.
                사용자 질문을 보고 아래 JSON만 반환한다. 설명, markdown, 코드블록 금지.

                intent 후보:
                - MY_PICKING_TASKS: 내 담당 피킹/작업 조회
                - PENDING_WORK: 오늘 처리할 업무/급한 일/우선순위/미처리 지시서 조회
                - INBOUND_STATUS: 입고/검수/적치/들어올 상품 조회
                - OUTBOUND_STATUS: 출고/배송/출하/밀린 출고 조회
                - INVENTORY_LOCATION: 특정 상품 재고 위치/수량 조회
                - LOW_STOCK: 부족/위험/품절/보충 필요 재고 조회

                JSON 스키마:
                {"intent":"INVENTORY_LOCATION","confidence":0.0,"slots":{"product":"모니터","warehouse":"부산","status":"","date":"today"}}

                규칙:
                - intent는 후보 중 하나만.
                - "내/나/담당"과 "피킹"이 함께 있으면 반드시 MY_PICKING_TASKS.
                - "피킹 작업 뭐야", "내가 오늘 해야 할 피킹"은 PENDING_WORK가 아니라 MY_PICKING_TASKS.
                - "오늘 뭐부터", "우선 처리", "미처리 지시서"처럼 입고/출고/피킹이 특정되지 않은 질문만 PENDING_WORK.
                - product, warehouse, status, date는 질문에 있으면 채우고 없으면 빈 문자열.
                - 오늘/금일은 date를 "today"로 쓴다.
                - 자신 없으면 PENDING_WORK, confidence 0.5.

                최근 대화:
                %s

                사용자 질문: %s
                JSON:
                """.formatted(formatHistory(history), question);

        long routeStartedAt = System.nanoTime();
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            long routeMs = elapsedMs(routeStartedAt);
            RouteResult route = parseRoute(content, question);
            RouteResult corrected = correctRoute(question, route);
            if (corrected != route) {
                log.info("[AI_WORK_QUERY_ROUTE] source=rule-corrected, routeMs={}, before={}, after={}, raw={}",
                        routeMs, route.intent(), corrected.intent(), sanitizeForLog(content));
                return corrected;
            }
            log.info("[AI_WORK_QUERY_ROUTE] source=llm, routeMs={}, intent={}, confidence={}, slots={}, raw={}",
                    routeMs, route.intent(), route.confidence(), route.slots(), sanitizeForLog(content));
            return route;
        } catch (Exception e) {
            long routeMs = elapsedMs(routeStartedAt);
            RouteResult fallback = RouteResult.fallback(classify(question), Map.of());
            log.warn("[AI_WORK_QUERY_ROUTE] source=fallback, routeMs={}, intent={}, error={}",
                    routeMs, fallback.intent(), e.getMessage());
            return fallback;
        }
    }

    private RouteResult routeByExplicitKeywords(String question) {
        Intent intent = explicitIntent(question);
        return intent == null ? null : RouteResult.rule(intent, Map.of());
    }

    private RouteResult correctRoute(String question, RouteResult route) {
        Intent intent = explicitIntent(question);
        if (intent == null || intent == route.intent()) {
            return route;
        }
        return new RouteResult(intent, 1.0, route.slots(), "rule-corrected");
    }

    private Intent explicitIntent(String question) {
        String q = question.toLowerCase(Locale.ROOT);
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
        Map<String, String> slots = normalizeSlots(map.get("slots"));
        return new RouteResult(intent, confidence, slots, "llm");
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

    private Map<String, String> normalizeSlots(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        return Map.of(
                "product", slotValue(raw, "product"),
                "warehouse", slotValue(raw, "warehouse"),
                "status", slotValue(raw, "status"),
                "date", slotValue(raw, "date")
        );
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

    private Intent classify(String question) {
        String q = question.toLowerCase(Locale.ROOT);

        if (hasAny(q, "부족", "위험", "품절", "모자", "재고 부족", "보충", "긴급 발주")) {
            return Intent.LOW_STOCK;
        }
        if (hasAny(q, "내", "나", "담당", "할 일", "해야 할", "해야될", "해야 돼", "해야돼")
                && hasAny(q, "피킹", "작업", "업무")) {
            return Intent.MY_PICKING_TASKS;
        }
        if (hasAny(q, "어디", "위치", "남아", "남았", "몇 개", "몇개", "수량", "보유", "재고")
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
        return Intent.PENDING_WORK;
    }

    private List<Map<String, Object>> query(RouteResult route, String question, String clientId, String userId) {
        return switch (route.intent()) {
            case MY_PICKING_TASKS -> queryMyPickingTasks(clientId, userId);
            case INVENTORY_LOCATION -> queryInventoryLocation(clientId, productKeyword(route, question), route.slots().get("warehouse"));
            case LOW_STOCK -> queryLowStock(clientId);
            case INBOUND_STATUS -> queryInboundStatus(clientId);
            case OUTBOUND_STATUS -> queryOutboundStatus(clientId);
            case PENDING_WORK -> queryPendingWork(clientId);
        };
    }

    private String productKeyword(RouteResult route, String question) {
        String product = route.slots().get("product");
        return isBlank(product) ? extractProductKeyword(question) : product;
    }

    private List<Map<String, Object>> queryMyPickingTasks(String clientId, String userId) {
        if (isBlank(userId)) {
            return List.of(Map.of("message", "로그인 사용자 정보를 확인할 수 없어 담당자 기준 조회를 할 수 없습니다."));
        }
        return readonlyJdbc.queryForList("""
                SELECT pl.picking_no,
                       w.name AS warehouse_name,
                       pl.status,
                       pl.created_at,
                       COUNT(pli.id) AS item_count,
                       COALESCE(SUM(pli.qty), 0) AS required_qty,
                       COALESCE(SUM(pli.picked_qty), 0) AS picked_qty
                FROM picking_lists pl
                JOIN picking_list_items pli ON pli.picking_list_id = pl.id
                JOIN master_db.warehouses w ON w.id = pl.warehouse_id
                WHERE pl.assigned_to = UNHEX(REPLACE(?, '-', ''))
                  AND (? IS NULL OR pl.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND pl.status IN ('pending', 'in_progress')
                GROUP BY pl.id, pl.picking_no, w.name, pl.status, pl.created_at
                ORDER BY pl.created_at ASC
                LIMIT ?
                """, userId, blankToNull(clientId), blankToNull(clientId), LIMIT);
    }

    private List<Map<String, Object>> queryInventoryLocation(String clientId, String keyword, String warehouseKeyword) {
        String like = "%" + (isBlank(keyword) ? "" : keyword) + "%";
        String warehouseLike = "%" + (isBlank(warehouseKeyword) ? "" : warehouseKeyword) + "%";
        return readonlyJdbc.queryForList("""
                SELECT p.name AS product_name,
                       p.sku,
                       w.name AS warehouse_name,
                       z.code AS zone_code,
                       z.name AS zone_name,
                       r.code AS rack_code,
                       l.code AS location_code,
                       i.available_qty,
                       i.reserved_qty,
                       i.incoming_qty,
                       i.total_qty
                FROM inventories i
                JOIN master_db.products p ON p.id = i.product_id
                JOIN master_db.warehouses w ON w.id = i.warehouse_id
                LEFT JOIN master_db.locations l ON l.id = i.location_id
                LEFT JOIN master_db.racks r ON r.id = l.rack_id
                LEFT JOIN master_db.zones z ON z.id = r.zone_id
                WHERE (? IS NULL OR i.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (p.name LIKE ? OR p.sku LIKE ?)
                  AND (? = '%%' OR w.name LIKE ?)
                  AND i.total_qty > 0
                ORDER BY p.name ASC, w.name ASC, z.code ASC, r.code ASC, l.code ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, warehouseLike, warehouseLike, LIMIT);
    }

    private List<Map<String, Object>> queryLowStock(String clientId) {
        return readonlyJdbc.queryForList("""
                SELECT p.name AS product_name,
                       p.sku,
                       w.name AS warehouse_name,
                       SUM(i.available_qty) AS available_qty,
                       SUM(i.reserved_qty) AS reserved_qty,
                       SUM(i.incoming_qty) AS incoming_qty,
                       SUM(i.total_qty) AS total_qty
                FROM inventories i
                JOIN master_db.products p ON p.id = i.product_id
                JOIN master_db.warehouses w ON w.id = i.warehouse_id
                WHERE (? IS NULL OR i.client_id = UNHEX(REPLACE(?, '-', '')))
                GROUP BY p.id, p.name, p.sku, w.name
                HAVING SUM(i.available_qty) <= 5
                ORDER BY available_qty ASC, total_qty ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), LIMIT);
    }

    private List<Map<String, Object>> queryInboundStatus(String clientId) {
        return readonlyJdbc.queryForList("""
                SELECT io.order_no,
                       w.name AS warehouse_name,
                       io.status,
                       io.expected_date,
                       COUNT(ioi.id) AS item_count,
                       COALESCE(SUM(ioi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(ioi.received_qty), 0) AS received_qty
                FROM inbound_orders io
                LEFT JOIN inbound_order_items ioi ON ioi.inbound_order_id = io.id
                JOIN master_db.warehouses w ON w.id = io.warehouse_id
                WHERE (? IS NULL OR io.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND io.status IN ('draft', 'approved', 'received', 'placing')
                GROUP BY io.id, io.order_no, w.name, io.status, io.expected_date
                ORDER BY io.expected_date ASC, io.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), LIMIT);
    }

    private List<Map<String, Object>> queryOutboundStatus(String clientId) {
        return readonlyJdbc.queryForList("""
                SELECT oo.order_no,
                       w.name AS warehouse_name,
                       oo.status,
                       oo.scheduled_date,
                       COUNT(ooi.id) AS item_count,
                       COALESCE(SUM(ooi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(ooi.picked_qty), 0) AS picked_qty,
                       COALESCE(SUM(ooi.dispatched_qty), 0) AS dispatched_qty
                FROM outbound_orders oo
                LEFT JOIN outbound_order_items ooi ON ooi.outbound_orders_id = oo.id
                JOIN master_db.warehouses w ON w.id = oo.warehouse_id
                WHERE (? IS NULL OR oo.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND oo.status IN ('draft', 'approved', 'in_progress', 'partial')
                GROUP BY oo.id, oo.order_no, w.name, oo.status, oo.scheduled_date
                ORDER BY oo.scheduled_date ASC, oo.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), LIMIT);
    }

    private List<Map<String, Object>> queryPendingWork(String clientId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(readonlyJdbc.queryForList("""
                SELECT '출고' AS work_type,
                       oo.order_no AS document_no,
                       w.name AS warehouse_name,
                       oo.status,
                       oo.scheduled_date
                FROM outbound_orders oo
                JOIN master_db.warehouses w ON w.id = oo.warehouse_id
                WHERE (? IS NULL OR oo.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND oo.status IN ('draft', 'approved', 'in_progress', 'partial')
                ORDER BY oo.scheduled_date ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), LIMIT / 2));
        rows.addAll(readonlyJdbc.queryForList("""
                SELECT '입고' AS work_type,
                       io.order_no AS document_no,
                       w.name AS warehouse_name,
                       io.status,
                       io.expected_date AS scheduled_date
                FROM inbound_orders io
                JOIN master_db.warehouses w ON w.id = io.warehouse_id
                WHERE (? IS NULL OR io.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND io.status IN ('draft', 'approved', 'received', 'placing')
                ORDER BY io.expected_date ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), LIMIT / 2));
        return rows;
    }

    private AnswerResult composeAnswer(String question, Intent intent, List<Map<String, Object>> rows, List<ChatTurn> history) {
        if (rows.isEmpty()) {
            return new AnswerResult("조건에 맞는 업무 데이터가 없습니다.", 0, true);
        }

        StringBuilder rowText = new StringBuilder();
        for (int i = 0; i < Math.min(rows.size(), LIMIT); i++) {
            rowText.append(i + 1).append(". ");
            rows.get(i).forEach((key, value) -> rowText.append(key).append("=").append(formatValue(value)).append(", "));
            rowText.append('\n');
        }

        String prompt = """
                역할: 전자기기 WMS 업무 조회 챗봇.
                임무: 조회 결과만 근거로 한국어 답변을 작성한다.
                형식: 1~2문장. 내부 컬럼명, SQL, UUID 금지. 추측 금지.
                상태 해석:
                - approved는 완료가 아니라 승인 완료 후 처리 대기 상태다.
                - pending/draft는 대기, in_progress/picking/placing은 진행 중, completed/received는 완료다.
                - 사용자가 "내 피킹"을 물으면 피킹 작업만 답하고 입고/출고 지시서를 섞지 않는다.

                사용자 질문: %s
                질문 의도: %s
                최근 대화: %s
                조회 결과:
                %s

                답변:
                """.formatted(question, intent.label, formatHistory(history), rowText);

        try {
            long llmStartedAt = System.nanoTime();
            log.info("[AI_WORK_QUERY_LLM_START] intent={}, rows={}", intent, rows.size());
            String answer = chatClient.prompt().user(prompt).call().content();
            long llmMs = elapsedMs(llmStartedAt);
            log.info("[AI_WORK_QUERY_LLM_END] intent={}, llmMs={}, blank={}", intent, llmMs, isBlank(answer));
            if (!isBlank(answer)) {
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
            case PENDING_WORK -> "처리 필요 지시서 %d건이 있습니다. 우선 %s %s는 %s 상태입니다."
                    .formatted(rows.size(), formatValue(first.get("work_type")),
                            formatValue(first.get("document_no")), formatValue(first.get("status")));
        };
    }

    private String extractProductKeyword(String question) {
        return question
                .replaceAll("(?i)재고|어디|위치|있어|있나요|알려줘|몇\\s*개|몇개|남았어|남아|보유|수량|상품|제품|센터|창고|물류센터|부산|서울|대전", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private enum Intent {
        MY_PICKING_TASKS("내 피킹 작업 조회"),
        PENDING_WORK("처리 필요 지시서 조회"),
        INBOUND_STATUS("입고 현황 조회"),
        OUTBOUND_STATUS("출고 현황 조회"),
        INVENTORY_LOCATION("상품 재고 위치 조회"),
        LOW_STOCK("부족 재고 조회");

        private final String label;

        Intent(String label) {
            this.label = label;
        }
    }

    public record WorkQueryResponse(
            String question,
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

    private record AnswerResult(String answer, long llmMs, boolean fallback) {
    }

    private record FollowUpResult(Intent intent, List<Map<String, Object>> rows) {
    }

    private record RouteResult(Intent intent, double confidence, Map<String, String> slots, String source) {
        private static RouteResult rule(Intent intent, Map<String, String> slots) {
            return new RouteResult(intent, 1.0, slots, "rule");
        }

        private static RouteResult fallback(Intent intent, Map<String, String> slots) {
            return new RouteResult(intent, 0.0, slots, "fallback");
        }

        private static RouteResult followUp(Intent intent) {
            return new RouteResult(intent, 1.0, Map.of(), "followUp");
        }
    }
}

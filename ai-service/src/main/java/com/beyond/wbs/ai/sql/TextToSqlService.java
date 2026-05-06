package com.beyond.wbs.ai.sql;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자연어 질문 → LLM이 SELECT SQL 생성 → 검증 → 업무 DB 조회용 DataSource에서 실행.
 *
 * 로컬 프로젝트에서는 MySQL root 계정을 사용하므로 서비스 레벨에서 SELECT 검증,
 * 쓰기 키워드 차단, 테이블 화이트리스트, LIMIT 강제를 적용한다.
 */
@Slf4j
@Service
public class TextToSqlService {

    private final ChatClient chatClient;
    private final SchemaCatalog schemaCatalog;
    private final JdbcTemplate readonlyJdbc;

    private static final int MAX_LIMIT = 100;

    /** 명시적으로 차단할 쓰기·DDL·시스템 키워드. */
    private static final Pattern DENY = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|truncate|grant|revoke|" +
                    "create|replace|rename|lock|unlock|handler|load|call|do|set)\\b"
    );

    /** SELECT 로 시작해야 한다. */
    private static final Pattern SELECT_ONLY = Pattern.compile("(?is)^\\s*select\\s");

    /** LIMIT 절 탐색. */
    private static final Pattern LIMIT_CLAUSE = Pattern.compile("(?i)\\blimit\\s+(\\d+)");

    /** 민감 테이블(account_db·master_db 의 일부)은 여전히 차단. */
    private static final Pattern FORBIDDEN_TABLES = Pattern.compile(
            "(?i)\\b(users|permissions|roles|clients|login_history|client)\\b");

    public TextToSqlService(
            ChatClient chatClient,
            SchemaCatalog schemaCatalog,
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbc) {
        this.chatClient = chatClient;
        this.schemaCatalog = schemaCatalog;
        this.readonlyJdbc = readonlyJdbc;
    }

    public Result analyze(String question, List<ChatTurn> history) {
        String historyBlock = (history == null || history.isEmpty())
                ? ""
                : "\n========= 이전 대화 (맥락 유지용) =========\n" +
                        formatHistory(history) +
                        "\n========= 위 대화 맥락을 이해하고 현재 질문에 답하라 =========\n";
        String systemPrompt = """
                너는 MySQL SELECT 쿼리 생성기다. 설명·주석 없이 SQL 한 줄만 출력.

                ========= 절대 금지 =========
                - INSERT / UPDATE / DELETE / DDL / SET 금지.
                - users / permissions / roles / clients 등 민감 테이블 참조 금지.
                - SQL 외 다른 텍스트(설명, 주석, markdown fence ```) 출력 금지.

                ========= 반드시 지킴 =========
                - 집계/필터는 stock_db 테이블만 (inventories, inventory_transactions,
                  outbound_orders, outbound_order_items, inbound_orders, inbound_order_items).
                - 이름(warehouse_name / product_name / location_code)이 필요하면
                  master_db.warehouses / master_db.products / master_db.locations 와 JOIN.
                  → 반드시 "master_db." prefix 붙일 것.
                - 결과에 UUID 만 있지 말고, 사람이 읽을 수 있는 name / code / sku 를 포함하라.
                - 반드시 LIMIT %d 이하 포함.
                - 현재 시각 NOW(), 오늘 날짜 CURDATE().

                ========= 정렬·후속 질문 처리 =========
                - "상위 N개" / "가장 많은" / "top N" → ORDER BY ... DESC LIMIT N
                - "하위 N개" / "가장 적은" / "bottom N" / "최저"  → ORDER BY ... ASC LIMIT N
                - 짧은 후속 질문("하위는?", "그 중에서", "내림차순으로")이 들어오면
                  직전 어시스턴트 SQL 구조를 그대로 두고 필요한 부분(ORDER BY 방향 / LIMIT / WHERE)만 바꿔라.
                - 질문이 모호해도 가능한 한 가장 합리적인 SELECT 를 만들어라. 절대 질문으로 되묻지 마라.
                  필요 정보가 부족하면 전체 기본 쿼리를 돌려서 결과를 먼저 내놓아라.

                %s

                예시:
                Q: 창고별 재고 총합 상위 5개
                A: SELECT w.name AS warehouse_name, SUM(i.total_qty) AS total FROM inventories i JOIN master_db.warehouses w ON i.warehouse_id = w.id GROUP BY w.name ORDER BY total DESC LIMIT 5

                Q: 오늘 출고 주문 건수
                A: SELECT COUNT(*) AS cnt FROM outbound_orders WHERE DATE(created_at)=CURDATE() LIMIT 100

                Q: 재고 변동 타입별 건수
                A: SELECT tx_type, COUNT(*) AS cnt FROM inventory_transactions GROUP BY tx_type LIMIT 100

                Q: 상품별 총재고 상위 10
                A: SELECT p.name AS product_name, p.sku, SUM(i.total_qty) AS total FROM inventories i JOIN master_db.products p ON i.product_id = p.id GROUP BY p.id, p.name, p.sku ORDER BY total DESC LIMIT 10

                Q: 상품별 출고 합계 상위 10
                A: SELECT p.name AS product_name, SUM(ooi.ordered_qty) AS total_ordered FROM outbound_order_items ooi JOIN master_db.products p ON ooi.product_id = p.id GROUP BY p.id, p.name ORDER BY total_ordered DESC LIMIT 10

                Q: 상품별 입고 합계 상위 10
                A: SELECT p.name AS product_name, SUM(ioi.received_qty) AS total_received FROM inbound_order_items ioi JOIN master_db.products p ON ioi.product_id = p.id GROUP BY p.id, p.name ORDER BY total_received DESC LIMIT 10

                핵심 관계 힌트 (이걸 꼭 지킬 것):
                - outbound_orders 는 헤더 테이블. product_id 없음. 상품 정보는 outbound_order_items 에.
                - inbound_orders 는 헤더. product_id 없음. 상품은 inbound_order_items 에.
                - 상품·창고·위치 이름은 항상 master_db.products / warehouses / locations 와 JOIN.

                후속 질문 예시:
                [이전] 사용자: "창고별 재고 상위 5개"  →  ...ORDER BY total DESC LIMIT 5
                [지금] 사용자: "하위는?"
                A: SELECT w.name AS warehouse_name, SUM(i.total_qty) AS total FROM inventories i JOIN master_db.warehouses w ON i.warehouse_id = w.id GROUP BY w.name ORDER BY total ASC LIMIT 5

                [이전] 사용자: "오늘 출고 건수"
                [지금] 사용자: "어제는?"
                A: SELECT COUNT(*) AS cnt FROM outbound_orders WHERE DATE(created_at)=CURDATE() - INTERVAL 1 DAY LIMIT 100
                %s
                """.formatted(MAX_LIMIT, schemaCatalog.asPromptText(), historyBlock);

        String rawSql = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        log.debug("t2sql raw LLM output for '{}': {}", question, rawSql);

        String sql;
        try {
            sql = extractSql(rawSql);
            validate(sql);
        } catch (IllegalStateException e) {
            // 검증 실패 시 원문 함께 찍어 진단 편의 (에러 메시지는 그대로 던짐)
            log.warn("t2sql validation fail. question='{}', raw='{}'", question, rawSql);
            throw e;
        }

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = readonlyJdbc.queryForList(sql);
        long elapsed = System.currentTimeMillis() - start;

        // UUID binary → base64 문자열로 LLM 이 안 읽히게, 이름 컬럼 위주로 직렬화
        String summary = summarize(question, rows);

        log.info("t2sql: question='{}', rows={}, elapsed={}ms, sql={}",
                question, rows.size(), elapsed, sql);

        return new Result(question, sql, rows, elapsed, summary);
    }

    /** 실행 결과를 LLM 에 다시 던져 한국어 1~2줄 요약 생성. */
    private String summarize(String question, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "조건에 맞는 데이터가 없습니다.";
        }
        // 상위 20행만 LLM 에 전달 (토큰 절약). UUID 는 가독성 낮으니 이름·수치 컬럼이 LLM 에 잘 보이게 표기.
        StringBuilder rowsText = new StringBuilder();
        int max = Math.min(rows.size(), 20);
        for (int i = 0; i < max; i++) {
            rowsText.append(i + 1).append(". ");
            rows.get(i).forEach((k, v) -> rowsText.append(k).append("=").append(formatValue(v)).append(", "));
            rowsText.append('\n');
        }
        String prompt = """
                다음은 사용자의 질문에 대한 MySQL 조회 결과다.
                숫자·이름 중심으로 한국어 1~2문장으로 간결히 설명하라.
                UUID 가 그대로 보이면 무시하고 이름·수치만 언급하라.

                질문: %s
                결과 상위 %d행:
                %s
                """.formatted(question, max, rowsText);
        try {
            return chatClient.prompt().user(prompt).call().content().trim();
        } catch (Exception e) {
            log.warn("summarize failed: {}", e.getMessage());
            return "총 %d행 반환됨.".formatted(rows.size());
        }
    }

    private String formatValue(Object v) {
        if (v == null) return "null";
        // BINARY(16) 값은 byte[] → base64 로 오는데 길고 무의미하므로 생략 표기
        if (v instanceof byte[]) return "<uuid>";
        return v.toString();
    }

    /** LLM 출력에서 순수 SQL만 뽑아낸다. 여러 statement 가 오면 첫 SELECT 만 사용. */
    private String extractSql(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM 응답이 비었습니다.");
        }
        String s = raw.trim();
        // ```sql ... ``` fence 제거
        s = s.replaceAll("(?is)^\\s*```(?:sql)?\\s*", "")
                .replaceAll("(?s)\\s*```\\s*$", "")
                .trim();
        // 세미콜론으로 여러 statement 가 오면 첫 번째만 사용
        int semi = s.indexOf(';');
        if (semi >= 0) {
            s = s.substring(0, semi).trim();
        }
        return s;
    }

    /** 대화 히스토리를 프롬프트에 넣을 텍스트 블록으로 변환. */
    private String formatHistory(List<ChatTurn> history) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 6);  // 최근 6턴(약 3Q&A)
        for (int i = start; i < history.size(); i++) {
            var t = history.get(i);
            String role = "user".equalsIgnoreCase(t.role()) ? "사용자" : "어시스턴트";
            String content = t.content() == null ? "" : t.content().replaceAll("\\s+", " ").trim();
            if (content.length() > 200) content = content.substring(0, 200) + "…";
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    private void validate(String sql) {
        if (!SELECT_ONLY.matcher(sql).find()) {
            throw new IllegalStateException("SELECT 문이 아닙니다.");
        }
        if (DENY.matcher(sql).find()) {
            throw new IllegalStateException("금지 키워드가 포함됐습니다.");
        }
        if (FORBIDDEN_TABLES.matcher(sql).find()) {
            throw new IllegalStateException(
                    "민감 테이블 참조는 허용되지 않습니다 (users / permissions / roles 등 account_db 영역).");
        }
        String lc = sql.toLowerCase();
        boolean touchesAllowed = SchemaCatalog.ALLOWED_TABLES.stream()
                .anyMatch(lc::contains);
        if (!touchesAllowed) {
            throw new IllegalStateException("허용되지 않은 테이블입니다.");
        }
        Matcher m = LIMIT_CLAUSE.matcher(sql);
        if (!m.find()) {
            throw new IllegalStateException("LIMIT 절이 필요합니다.");
        }
        int limit = Integer.parseInt(m.group(1));
        if (limit > MAX_LIMIT) {
            throw new IllegalStateException(
                    "LIMIT 값은 %d 이하여야 합니다. (현재 %d)".formatted(MAX_LIMIT, limit));
        }
    }

    public record Result(
            String question,
            String generatedSql,
            List<Map<String, Object>> rows,
            long executionTimeMs,
            String summary) {}
}

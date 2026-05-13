package com.beyond.wbs.ai.rag;

import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final OpenAiChatGateway openAiChatGateway;
    private final VectorStore vectorStore;
    private final RagResponseCacheService responseCacheService;

    @Value("${wms.rag.search.top-k:8}")
    private int searchTopK;

    @Value("${wms.rag.search.similarity-threshold:0.35}")
    private double searchSimilarityThreshold;

    @Value("${wms.rag.search.min-answer-similarity:0.38}")
    private double minAnswerSimilarity;

    private static final String NO_EVIDENCE_ANSWER = "죄송합니다. 요청하신 질문을 처리할 수 없습니다.";

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(AUTH|DOC|INB|OUT|PICK|ASSIGN|INV|CODE|MASTER|MENU|FILE)-\\d{3}\\b"
    );

    private static final String SYSTEM_PROMPT = """
            Do NOT show your thinking or reasoning. Answer directly.
            당신은 WMS 운영 어시스턴트입니다. CONTEXT는 WMS 운영 가이드 조각이며
            각 조각 맨 앞에 "[섹션: X.Y 제목]" 형식의 출처가 있습니다.

            답변 규칙:
            1. 반드시 한국어로 답한다.
            2. 문서 내용을 그대로 복사하지 말고, 사용자의 상황에 맞게 정리해서 답한다.
            3. 최근 대화 맥락이 있으면 현재 질문과 함께 해석한다.
            4. 사용자에게 "RAG", "CONTEXT", "출처" 같은 내부 용어를 노출하지 않는다.
            5. CONTEXT 에 없으면 추측하지 말고 "죄송합니다. 요청하신 질문을 처리할 수 없습니다." 라고 답한다.
            6. 답변은 5줄 이내로 간결하게 작성한다.
            7. markdown, 번호 목록, 불릿, 굵게 표시를 쓰지 말고 자연스러운 문장으로 답한다.
            """;

    public String ask(String question, String category) {
        return ask(question, category, List.of());
    }

    public String ask(String question, String category, List<ChatTurn> history) {
        long startedAt = System.nanoTime();
        String rewrittenQuestion = rewriteQuestion(question, history);
        String effectiveCategory = resolveCategory(category, rewrittenQuestion);
        String retrievalQuestion = enrichRetrievalQuestion(rewrittenQuestion, effectiveCategory);
        log.info("[AI_RAG_START] category={}, requestedCategory={}, originalQuestion='{}', retrievalQuestion='{}'",
                effectiveCategory, normalizeCategory(category), sanitize(question), sanitize(retrievalQuestion));
        String cacheSource = effectiveCategory;
        var cached = findSimilarQuietly(retrievalQuestion, cacheSource);
        if (cached.isPresent()) {
            RagResponseCacheService.CachedAnswer answer = cached.get();
            markCacheHitQuietly(answer.question());
            log.info("[AI_RAG_CACHE_HIT] similarity={}, source={}, elapsedMs={}, question='{}'",
                    String.format("%.4f", answer.similarity()), answer.source(), elapsedMs(startedAt), sanitize(retrievalQuestion));
            return answer.answer();
        }

        RetrievalResult retrievalResult = retrieveDocuments(retrievalQuestion, effectiveCategory);
        List<Document> documents = retrievalResult.documents();
        if (!hasEnoughEvidence(documents)) {
            log.info("[AI_RAG_NO_EVIDENCE] category={}, reason={}, topK={}, minAnswerSimilarity={}, question='{}'",
                    retrievalResult.category(), retrievalResult.reason(), documents.size(), minAnswerSimilarity, sanitize(retrievalQuestion));
            return NO_EVIDENCE_ANSWER;
        }

        long llmStartedAt = System.nanoTime();
        String answer;
        try {
            answer = openAiChatGateway.complete(
                    buildSystemPrompt(history),
                    buildRagUserPrompt(retrievalQuestion, documents)
            );
        } catch (Exception e) {
            log.warn("[AI_RAG_LLM_FAILED] reason={}, totalMs={}, question='{}'",
                    e.getMessage(), elapsedMs(startedAt), sanitize(retrievalQuestion));
            answer = evidenceFallbackAnswer(retrievalQuestion, documents);
        }
        log.info("[AI_RAG_LLM_END] answerChars={}, llmMs={}, totalMs={}, question='{}'",
                answer == null ? 0 : answer.length(), elapsedMs(llmStartedAt), elapsedMs(startedAt), sanitize(retrievalQuestion));
        if (!documents.isEmpty()) {
            saveCacheQuietly(retrievalQuestion, answer, cacheSource);
        }
        return answer;
    }

    public String retrieveContext(String question, String category, List<ChatTurn> history) {
        long startedAt = System.nanoTime();
        String rewrittenQuestion = rewriteQuestion(question, history == null ? List.of() : history);
        String effectiveCategory = resolveCategory(category, rewrittenQuestion);
        String retrievalQuestion = enrichRetrievalQuestion(rewrittenQuestion, effectiveCategory);
        RetrievalResult retrievalResult = retrieveDocuments(retrievalQuestion, effectiveCategory);
        List<Document> documents = hasEnoughEvidence(retrievalResult.documents()) ? retrievalResult.documents() : List.of();
        log.info("[AI_RAG_CONTEXT] category={}, docs={}, totalMs={}, question='{}'",
                retrievalResult.category(), documents.size(), elapsedMs(startedAt), sanitize(retrievalQuestion));
        return formatContext(documents);
    }

    public Flux<String> askStream(String question, String category) {
        return askStream(question, category, List.of());
    }

    public Flux<String> askStream(String question, String category, List<ChatTurn> history) {
        long startedAt = System.nanoTime();
        String rewrittenQuestion = rewriteQuestion(question, history);
        String effectiveCategory = resolveCategory(category, rewrittenQuestion);
        String retrievalQuestion = enrichRetrievalQuestion(rewrittenQuestion, effectiveCategory);
        log.info("[AI_RAG_STREAM_START] category={}, requestedCategory={}, originalQuestion='{}', retrievalQuestion='{}'",
                effectiveCategory, normalizeCategory(category), sanitize(question), sanitize(retrievalQuestion));
        String cacheSource = effectiveCategory;
        var cached = findSimilarQuietly(retrievalQuestion, cacheSource);
        if (cached.isPresent()) {
            RagResponseCacheService.CachedAnswer answer = cached.get();
            markCacheHitQuietly(answer.question());
            log.info("[AI_RAG_CACHE_HIT] stream=true, similarity={}, source={}, elapsedMs={}, question='{}'",
                    String.format("%.4f", answer.similarity()), answer.source(), elapsedMs(startedAt), sanitize(retrievalQuestion));
            return Flux.just(answer.answer());
        }

        RetrievalResult retrievalResult = retrieveDocuments(retrievalQuestion, effectiveCategory);
        List<Document> documents = retrievalResult.documents();
        if (!hasEnoughEvidence(documents)) {
            log.info("[AI_RAG_NO_EVIDENCE] stream=true, category={}, reason={}, topK={}, minAnswerSimilarity={}, question='{}'",
                    retrievalResult.category(), retrievalResult.reason(), documents.size(), minAnswerSimilarity, sanitize(retrievalQuestion));
            return Flux.just(NO_EVIDENCE_ANSWER);
        }

        String answer;
        try {
            answer = openAiChatGateway.complete(
                    buildSystemPrompt(history),
                    buildRagUserPrompt(retrievalQuestion, documents)
            );
        } catch (Exception e) {
            log.warn("[AI_RAG_LLM_FAILED] stream=true, reason={}, totalMs={}, question='{}'",
                    e.getMessage(), elapsedMs(startedAt), sanitize(retrievalQuestion));
            answer = evidenceFallbackAnswer(retrievalQuestion, documents);
        }
        log.info("[AI_RAG_LLM_END] stream=true, answerChars={}, totalMs={}, question='{}'",
                answer == null ? 0 : answer.length(), elapsedMs(startedAt), sanitize(retrievalQuestion));
        if (!documents.isEmpty()) {
            saveCacheQuietly(retrievalQuestion, answer, cacheSource);
        }
        return Flux.just(answer);
    }

    private String buildSystemPrompt(List<ChatTurn> history) {
        String historyBlock = formatHistory(history);
        if (historyBlock.isBlank()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n최근 대화 맥락:\n" + historyBlock;
    }

    private String rewriteQuestion(String question, List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        String historyBlock = formatHistory(history);
        try {
            return openAiChatGateway.complete("""
                    너는 RAG 검색용 질문 재작성기다.
                    최근 대화와 현재 질문을 보고, 문서 검색에 적합한 독립형 한국어 질문 1문장으로 다시 써라.
                    - 이미 독립형이면 거의 그대로 둔다.
                    - 설명, 인사, 따옴표 없이 질문 문장만 출력한다.
                    """, """
                    최근 대화:
                    %s

                    현재 질문:
                    %s
                    """.formatted(historyBlock, question));
        } catch (Exception e) {
            log.warn("rag question rewrite failed, fallback to original question: {}", e.getMessage());
            return question;
        }
    }

    private String enrichRetrievalQuestion(String question, String category) {
        String normalized = sanitize(question);
        if (normalized.isBlank()) {
            return normalized;
        }
        StringBuilder enriched = new StringBuilder(normalized);
        appendIfMatched(enriched, normalized, "출고지시서", "출고 지시서",
                "출고 지시서 화면 출고 관리 출고 지시서 생성 등록 만들기 메뉴 화면 경로");
        appendIfMatched(enriched, normalized, "출고 지시서",
                "출고 지시서 화면 출고 관리 출고 지시서 생성 등록 만들기 메뉴 화면 경로");
        appendIfMatched(enriched, normalized, "입고지시서", "입고 지시서",
                "입고 지시서 화면 입고 관리 입고 지시서 생성 등록 만들기 메뉴 화면 경로");
        appendIfMatched(enriched, normalized, "입고 지시서",
                "입고 지시서 화면 입고 관리 입고 지시서 생성 등록 만들기 메뉴 화면 경로");
        appendIfMatched(enriched, normalized, "피킹리스트", "피킹 리스트",
                "피킹 리스트 화면 지시서 목록 피킹 리스트 생성 메뉴 화면 경로");
        appendIfMatched(enriched, normalized, "피킹 리스트",
                "피킹 리스트 화면 지시서 목록 피킹 리스트 생성 메뉴 화면 경로");
        if (containsErrorCode(normalized)) {
            enriched.append(" WMS 오류 코드 기준 조치 가이드 실패 원인 확인 감사 로그");
        }
        if (hasAny(normalized, "권한", "역할", "role", "permission", "관리자", "매니저", "오퍼레이터", "operator")) {
            enriched.append(" WMS 권한 역할 정책 ADMIN MANAGER OPERATOR 리소스 액션 권한 캐시");
        }

        if ("wms-ui-guide".equalsIgnoreCase(normalizeCategory(category))
                && hasAny(normalized, "어디", "어디서", "어디에서", "메뉴", "화면", "경로", "만들", "생성", "등록")) {
            enriched.append(" 화면 경로 메뉴 위치 생성 등록");
        }
        return enriched.toString();
    }

    private void appendIfMatched(StringBuilder builder, String question, String keyword, String phrase) {
        if (question.contains(keyword) && !builder.toString().contains(phrase)) {
            builder.append(' ').append(phrase);
        }
    }

    private void appendIfMatched(StringBuilder builder, String question, String compactKeyword, String spacedKeyword, String phrase) {
        if ((question.contains(compactKeyword) || question.contains(spacedKeyword)) && !builder.toString().contains(phrase)) {
            builder.append(' ').append(phrase);
        }
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
            String content = turn.content() == null ? "" : turn.content().replaceAll("\\s+", " ").trim();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "…";
            }
            sb.append(role).append(": ").append(content).append('\n');
        }
        return sb.toString().trim();
    }

    private SearchRequest buildSearchRequest(String question, String category) {
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(question)
                .topK(searchTopK)
                .similarityThreshold(searchSimilarityThreshold);

        // category 파라미터가 있으면 metadata 필터링. RAG는 전체 검색을 의미한다.
        if (category != null && !category.isBlank() && !"RAG".equalsIgnoreCase(category)) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchBuilder.filterExpression(b.eq("category", category).build());
        }

        return searchBuilder.build();
    }

    private String buildRagUserPrompt(String question, List<Document> documents) {
        String context = formatContext(documents);
        return """
                사용자 질문:
                %s

                CONTEXT:
                %s

                위 CONTEXT만 근거로 답변:
                """.formatted(question, context);
    }

    private String formatContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            context.append("[문서 ").append(i + 1).append("]\n")
                    .append(document.getText() == null ? "" : document.getText())
                    .append("\n\n");
        }
        return context.toString().trim();
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "RAG" : category.trim();
    }

    private String resolveCategory(String category, String question) {
        if (category != null && !category.isBlank() && !"RAG".equalsIgnoreCase(category.trim())) {
            return category.trim();
        }

        String q = sanitize(question);
        if (containsErrorCode(q)) {
            return "wms-ui-guide";
        }
        if (hasAny(q, "권한", "역할", "role", "permission", "관리자", "매니저", "오퍼레이터", "operator")) {
            return "wms-role-permission-policy";
        }
        if (hasAny(q, "불량사진", "불량 사진", "불량 증빙", "증빙 사진", "증빙 파일", "첨부", "파일",
                "화면", "어디서", "어디에서", "메뉴", "경로", "조회 화면", "리스트", "지시서 화면")) {
            return "wms-ui-guide";
        }
        if (hasAny(q, "상태", "의미", "정의", "차이", "검수 대기", "적치 진행", "승인 완료", "가용", "예약")) {
            return "wms-status-definitions";
        }
        if (hasAny(q, "수량 불일치", "불량", "누락", "미지정", "예약 초과", "지연", "예외")) {
            return "wms-exception-handling";
        }
        if (hasAny(q, "모니터", "케이블", "충전기", "전자기기", "고가", "보관", "SKU")) {
            return "wms-electronics-storage-guide";
        }
        if (hasAny(q, "절차", "순서", "프로세스", "뭐부터", "처리해야", "해야 해", "하면 돼", "대응",
                "방법", "어떻게", "알려줘", "피킹", "패킹", "검수", "적치")) {
            return "wms-work-procedures";
        }
        return "RAG";
    }

    private RetrievalResult retrieveDocuments(String question, String category) {
        long searchStartedAt = System.nanoTime();
        List<Document> documents = vectorStore.similaritySearch(buildSearchRequest(question, category));
        logRetrieval(category, question, documents, elapsedMs(searchStartedAt));

        if (isRagCategory(category) || hasEnoughEvidence(documents)) {
            return new RetrievalResult(documents, normalizeCategory(category), "category_search");
        }

        try {
            long retryStartedAt = System.nanoTime();
            List<Document> fallbackDocuments = vectorStore.similaritySearch(buildSearchRequest(question, "RAG"));
            String reason = documents.isEmpty() ? "empty_category_result" : "weak_category_result";
            log.info("[AI_RAG_RETRY] reason={}, category={}, categoryTopK={}, fallbackTopK={}, retryMs={}, question='{}'",
                    reason, category, documents.size(), fallbackDocuments.size(), elapsedMs(retryStartedAt), sanitize(question));
            logRetrieval("RAG", question, fallbackDocuments, elapsedMs(retryStartedAt));
            return new RetrievalResult(fallbackDocuments, "RAG", reason);
        } catch (Exception e) {
            log.warn("[AI_RAG_RETRY] failed category={}, reason={}, question='{}'",
                    category, e.getMessage(), sanitize(question));
            return new RetrievalResult(documents, normalizeCategory(category), "retry_failed");
        }
    }

    private boolean hasEnoughEvidence(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return false;
        }
        return documents.stream()
                .map(Document::getScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .map(score -> score >= minAnswerSimilarity)
                .orElse(true);
    }

    private String evidenceFallbackAnswer(String question, List<Document> documents) {
        String code = extractErrorCode(question);
        if (!code.isBlank()) {
            return documents.stream()
                    .map(Document::getText)
                    .map(text -> errorCodeAnswerFromText(code, text))
                    .filter(answer -> !answer.isBlank())
                    .findFirst()
                    .orElse(NO_EVIDENCE_ANSWER);
        }
        return NO_EVIDENCE_ANSWER;
    }

    private String errorCodeAnswerFromText(String code, String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String upperCode = code.toUpperCase(Locale.ROOT);
        for (String line : text.split("\\R")) {
            String normalizedLine = sanitize(line);
            if (normalizedLine.toUpperCase(Locale.ROOT).contains(upperCode)) {
                return formatErrorCodeLine(upperCode, normalizedLine);
            }
        }
        return "";
    }

    private String formatErrorCodeLine(String code, String line) {
        String cleaned = line.replaceFirst("^[-*]\\s*", "").trim();
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex < 0 || colonIndex >= cleaned.length() - 1) {
            return cleaned;
        }
        String body = cleaned.substring(colonIndex + 1).trim();
        String[] parts = body.split("\\.\\s*", 2);
        String meaning = parts[0].trim();
        if (parts.length == 1 || parts[1].isBlank()) {
            return code + "는 " + meaning + " 상태를 의미합니다.";
        }
        return code + "는 " + meaning + " 상태를 의미합니다. " + toPoliteSentence(parts[1]);
    }

    private String toPoliteSentence(String value) {
        String sentence = value.trim();
        if (!sentence.endsWith(".") && !sentence.endsWith("!") && !sentence.endsWith("?")) {
            sentence += ".";
        }
        return sentence
                .replace("확인한다.", "확인하세요.")
                .replace("확인해야 한다.", "확인하세요.")
                .replace("처리한다.", "처리하세요.")
                .replace("발생한다.", "발생합니다.")
                .replace("분류한다.", "분류합니다.");
    }

    private boolean containsErrorCode(String value) {
        return ERROR_CODE_PATTERN.matcher(value == null ? "" : value).find();
    }

    private String extractErrorCode(String value) {
        Matcher matcher = ERROR_CODE_PATTERN.matcher(value == null ? "" : value);
        if (matcher.find()) {
            return matcher.group().toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private boolean isRagCategory(String category) {
        return category == null || category.isBlank() || "RAG".equalsIgnoreCase(category.trim());
    }

    private Optional<RagResponseCacheService.CachedAnswer> findSimilarQuietly(String question, String source) {
        try {
            return responseCacheService.findSimilar(question, source);
        } catch (Exception e) {
            log.warn("[AI_RAG_CACHE_SKIP] action=findSimilar, reason={}, question='{}'",
                    e.getMessage(), sanitize(question));
            return Optional.empty();
        }
    }

    private void markCacheHitQuietly(String question) {
        try {
            responseCacheService.markHit(question);
        } catch (Exception e) {
            log.warn("[AI_RAG_CACHE_SKIP] action=markHit, reason={}, question='{}'",
                    e.getMessage(), sanitize(question));
        }
    }

    private void saveCacheQuietly(String question, String answer, String source) {
        try {
            responseCacheService.save(question, answer, source);
        } catch (Exception e) {
            log.warn("[AI_RAG_CACHE_SKIP] action=save, reason={}, question='{}'",
                    e.getMessage(), sanitize(question));
        }
    }

    private boolean hasAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void logRetrieval(String category, String question, List<Document> documents, long searchMs) {
        log.info("[AI_RAG_RETRIEVE] category={}, topK={}, searchMs={}, question='{}'",
                normalizeCategory(category), documents.size(), searchMs, sanitize(question));
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            log.info("[AI_RAG_HIT] rank={}, score={}, source={}, section={}, preview='{}'",
                    i + 1,
                    document.getScore() == null ? "-" : String.format("%.4f", document.getScore()),
                    metadataValue(document, "source"),
                    metadataValue(document, "section_path"),
                    preview(document.getText()));
        }
    }

    private String metadataValue(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "-" : sanitize(String.valueOf(value));
    }

    private String preview(String value) {
        String normalized = sanitize(value);
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record RetrievalResult(List<Document> documents, String category, String reason) {
    }
}

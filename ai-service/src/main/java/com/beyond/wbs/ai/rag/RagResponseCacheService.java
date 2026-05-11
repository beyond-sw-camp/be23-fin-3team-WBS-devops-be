package com.beyond.wbs.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagResponseCacheService {

    private static final double CACHE_HIT_THRESHOLD = 0.93;
    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public Optional<CachedAnswer> findSimilar(String question, String source) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        ensureTable();
        float[] embedding = embeddingModel.embed(normalized);
        String vector = toVectorLiteral(embedding);
        String normalizedSource = normalize(source);
        boolean sourceFiltered = !normalizedSource.isBlank();

        return jdbcTemplate.query("""
                        SELECT question, answer, source, 1 - (embedding <=> ?::vector) AS similarity
                        FROM rag_response_cache
                        WHERE 1 - (embedding <=> ?::vector) >= ?
                          AND (? = FALSE OR source = ?)
                        ORDER BY embedding <=> ?::vector
                        LIMIT 1
                        """,
                ps -> {
                    ps.setString(1, vector);
                    ps.setString(2, vector);
                    ps.setDouble(3, CACHE_HIT_THRESHOLD);
                    ps.setBoolean(4, sourceFiltered);
                    ps.setString(5, normalizedSource);
                    ps.setString(6, vector);
                },
                rs -> rs.next()
                        ? Optional.of(new CachedAnswer(
                        rs.getString("question"),
                        rs.getString("answer"),
                        rs.getString("source"),
                        rs.getDouble("similarity")))
                        : Optional.<CachedAnswer>empty()
        );
    }

    public void save(String question, String answer, String source) {
        String normalizedQuestion = normalize(question);
        String normalizedAnswer = normalize(answer);
        if (normalizedQuestion.isBlank() || normalizedAnswer.isBlank()) {
            return;
        }
        if (isUncacheableFallback(normalizedAnswer)) {
            return;
        }

        ensureTable();
        float[] embedding = embeddingModel.embed(normalizedQuestion);
        String vector = toVectorLiteral(embedding);
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update("""
                        INSERT INTO rag_response_cache(question, answer, source, embedding, created_at, last_used_at, hit_count)
                        VALUES (?, ?, ?, ?::vector, ?, ?, 0)
                        ON CONFLICT (question) DO UPDATE SET
                            answer = EXCLUDED.answer,
                            source = EXCLUDED.source,
                            embedding = EXCLUDED.embedding,
                            last_used_at = EXCLUDED.last_used_at
                        """,
                truncate(normalizedQuestion, MAX_QUESTION_LENGTH),
                truncate(normalizedAnswer, MAX_ANSWER_LENGTH),
                truncate(source == null ? "RAG" : source, 100),
                vector,
                now,
                now
        );
    }

    public void markHit(String question) {
        jdbcTemplate.update("""
                        UPDATE rag_response_cache
                        SET hit_count = hit_count + 1,
                            last_used_at = ?
                        WHERE question = ?
                        """,
                Timestamp.from(Instant.now()),
                question
        );
    }

    public int evictBySource(String source) {
        String normalizedSource = normalize(source);
        if (normalizedSource.isBlank()) {
            return 0;
        }

        ensureTable();
        int rows = jdbcTemplate.update("""
                        DELETE FROM rag_response_cache
                        WHERE source = ?
                        """,
                normalizedSource
        );
        log.info("[AI_RAG_CACHE_EVICT] source={}, rows={}", normalizedSource, rows);
        return rows;
    }

    private void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_response_cache (
                    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                    question TEXT NOT NULL UNIQUE,
                    answer TEXT NOT NULL,
                    source VARCHAR(100),
                    embedding VECTOR(1536) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    hit_count INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS rag_response_cache_hnsw_idx
                ON rag_response_cache USING hnsw (embedding vector_cosine_ops)
                """);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean isUncacheableFallback(String answer) {
        return answer.contains("제공된 문서에 해당 내용이 없습니다")
                || answer.contains("요청하신 질문을 처리할 수 없습니다")
                || answer.contains("처리할 수 없습니다");
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    public record CachedAnswer(String question, String answer, String source, double similarity) {
    }
}

package com.beyond.wbs.ai.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/sql")
@RequiredArgsConstructor
public class TextToSqlController {

    private final TextToSqlService service;

    /**
     * 자연어 질문 → SQL 생성 + 실행 + 결과 반환.
     * history 에 이전 Q&A 쌍을 함께 보내면 맥락("하위 5개는?") 유지 가능.
     */
    @PostMapping("/analyze")
    public Mono<ResponseEntity<Object>> analyze(@RequestBody AnalyzeRequest req) {
        return Mono.fromCallable(() -> service.analyze(
                        req.question(),
                        req.toSharedHistory()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorResume(IllegalStateException.class, e -> {
                    log.warn("t2sql 검증 실패: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "validation_failed",
                                    "message", "질문을 SQL 로 바꾸지 못했어요. 좀 더 구체적으로 다시 물어봐주세요."
                            )));
                })
                .onErrorResume(e -> {
                    log.error("t2sql 실행 오류", e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                    "error", "execution_failed",
                                    "message", "생성된 SQL 이 실행 불가였어요. 다른 표현(예: '부산 창고 재고', '어제 출고 건수')으로 다시 시도해보세요."
                            )));
                });
    }

    public record AnalyzeRequest(String question, java.util.List<ChatTurn> history) {

        public java.util.List<com.beyond.wbs.ai.chat.dto.ChatTurn> toSharedHistory() {
            if (history == null || history.isEmpty()) {
                return java.util.List.of();
            }
            return history.stream()
                    .map(turn -> new com.beyond.wbs.ai.chat.dto.ChatTurn(turn.role(), turn.content()))
                    .toList();
        }
    }

    /**
     * 하위 호환용 타입.
     * 예전 런타임/직렬화 메타데이터가 이 내부 클래스를 참조해도 500이 나지 않도록 유지한다.
     */
    public record ChatTurn(String role, String content) {}
}

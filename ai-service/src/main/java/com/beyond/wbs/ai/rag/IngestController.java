package com.beyond.wbs.ai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class IngestController {

    private final DocumentIngestService ingestService;

    @PostMapping("/ingest")
    public Mono<Map<String, Object>> ingest(@RequestBody IngestRequest req) {
        return Mono.fromCallable(() -> {
                    int chunks = ingestService.ingestText(req.content(), req.metadata());
                    return Map.<String, Object>of(
                            "status", "success",
                            "chunks", chunks
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record IngestRequest(String content, Map<String, Object> metadata) {}
}

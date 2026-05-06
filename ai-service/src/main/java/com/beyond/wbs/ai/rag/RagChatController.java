package com.beyond.wbs.ai.rag;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatService ragService;

    @GetMapping("/chat")
    public Mono<String> chat(@RequestParam String q,
                             @RequestParam(required = false) String category) {
        return Mono.fromCallable(() -> ragService.ask(q, category))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/chat")
    public Mono<RagChatResponse> chatWithHistory(@RequestBody RagChatRequest request) {
        return Mono.fromCallable(() -> new RagChatResponse(
                        request.question(),
                        ragService.ask(
                                request.question(),
                                request.category(),
                                request.history() == null ? List.of() : request.history()
                        )
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String q,
                                   @RequestParam(required = false) String category) {
        return ragService.askStream(q, category);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamWithHistory(@RequestBody RagChatRequest request) {
        return ragService.askStream(
                request.question(),
                request.category(),
                request.history() == null ? List.of() : request.history()
        );
    }

    public record RagChatRequest(String question, String category, List<ChatTurn> history) {
    }

    public record RagChatResponse(String question, String answer) {
    }
}

package com.beyond.wbs.ai.esg;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/esg")
@RequiredArgsConstructor
public class EsgChatController {

    private final EsgChatService esgChatService;

    @PostMapping("/analyze")
    public Mono<EsgChatService.EsgChatResponse> analyze(@RequestBody EsgChatRequest request) {
        return Mono.fromCallable(() -> esgChatService.analyze(
                        request.question(),
                        request.history() == null ? List.of() : request.history()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record EsgChatRequest(String question, List<ChatTurn> history) {
    }
}

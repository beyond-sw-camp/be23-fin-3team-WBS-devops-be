package com.beyond.wbs.ai.workquery;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/work-query")
@RequiredArgsConstructor
public class WorkQueryController {

    private final WorkQueryService workQueryService;

    @PostMapping("/ask")
    public Mono<WorkQueryService.WorkQueryResponse> ask(
            @RequestBody WorkQueryRequest request,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Mono.fromCallable(() -> workQueryService.ask(
                        request.message(),
                        request.history() == null ? List.of() : request.history(),
                        request.context(),
                        clientId,
                        userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record WorkQueryRequest(String message, List<ChatTurn> history, WorkQueryService.WorkQueryContext context) {
    }
}

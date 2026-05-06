package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatRouteController {

    private final ChatRoutingService chatRoutingService;

    @PostMapping({"/ask", "/route"})
    public Mono<ChatRoutingService.RouteResult> route(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Is-Developer", required = false) String isDeveloper,
            @RequestBody RouteRequest request) {
        if (!isAdminUser(userRole, isDeveloper)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 챗봇은 관리자 전용 기능입니다.");
        }
        return Mono.fromCallable(() -> chatRoutingService.route(
                        request.question(),
                        request.history() == null ? List.of() : request.history(),
                        request.context(),
                        clientId,
                        userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record RouteRequest(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context) {
    }

    private boolean isAdminUser(String userRole, String isDeveloper) {
        return "ADMIN".equalsIgnoreCase(userRole) || "true".equalsIgnoreCase(isDeveloper);
    }
}

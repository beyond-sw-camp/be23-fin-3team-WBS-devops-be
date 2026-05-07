package com.beyond.wbs.ai.chat;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import com.beyond.wbs.ai.workquery.WorkQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatRouteController {

    private final ChatRoutingService chatRoutingService;
    private final ObjectMapper objectMapper;

    @PostMapping({"/ask", "/route"})
    public Mono<ChatRoutingService.RouteResult> route(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Is-Developer", required = false) String isDeveloper,
            @RequestBody(required = false) String rawBody) {
        if (!isAdminUser(userRole, isDeveloper)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 챗봇은 관리자 전용 기능입니다.");
        }
        RouteRequest request = parseRequest(rawBody);
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문을 입력해주세요.");
        }
        return Mono.fromCallable(() -> chatRoutingService.route(
                        request.question(),
                        request.history() == null ? List.of() : request.history(),
                        request.context(),
                        clientId,
                        userId,
                        resolveUserName(request.userName(), userName)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveUserName(String bodyUserName, String headerUserName) {
        String value = bodyUserName == null || bodyUserName.isBlank() ? headerUserName : bodyUserName;
        if (value == null || value.isBlank()) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private RouteRequest parseRequest(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, RouteRequest.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문 요청 JSON 형식이 올바르지 않습니다.");
        }
    }

    public record RouteRequest(
            String question,
            List<ChatTurn> history,
            WorkQueryService.WorkQueryContext context,
            String userName) {
    }

    private boolean isAdminUser(String userRole, String isDeveloper) {
        return "ADMIN".equalsIgnoreCase(userRole) || "true".equalsIgnoreCase(isDeveloper);
    }
}

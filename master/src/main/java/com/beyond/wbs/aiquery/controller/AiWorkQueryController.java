package com.beyond.wbs.aiquery.controller;

import com.beyond.wbs.aiquery.service.AiWorkQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/work-query")
@RequiredArgsConstructor
public class AiWorkQueryController {

    private final AiWorkQueryService aiWorkQueryService;

    @PostMapping("/execute")
    public WorkQueryApiResponse execute(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody WorkQueryApiRequest request) {
        return aiWorkQueryService.execute(clientId, userId, request);
    }

    public record WorkQueryApiRequest(
            String intent,
            Map<String, String> slots,
            int limit) {
    }

    public record WorkQueryApiResponse(
            String intent,
            List<Map<String, Object>> rows) {
    }
}

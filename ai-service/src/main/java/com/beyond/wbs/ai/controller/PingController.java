package com.beyond.wbs.ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
public class PingController {

    private final ChatClient chatClient;

    @GetMapping("/ping")
    public String ping(@RequestParam(defaultValue = "한국어로 한 문장만 인사해줘.") String q) {
        return chatClient.prompt().user(q).call().content();
    }

    @GetMapping(value = "/ping/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> pingStream(@RequestParam(defaultValue = "한국어로 짧게 인사해줘.") String q) {
        return chatClient.prompt().user(q).stream().content();
    }
}

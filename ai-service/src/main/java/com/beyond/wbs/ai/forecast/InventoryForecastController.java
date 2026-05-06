package com.beyond.wbs.ai.forecast;

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
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class InventoryForecastController {

    private final InventoryForecastService forecastService;

    @PostMapping("/analyze")
    public Mono<InventoryForecastService.ForecastResponse> analyze(@RequestBody ForecastRequest request) {
        return Mono.fromCallable(() -> forecastService.analyze(
                        request.question(),
                        request.history() == null ? List.of() : request.history()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record ForecastRequest(String question, List<ChatTurn> history) {
    }
}

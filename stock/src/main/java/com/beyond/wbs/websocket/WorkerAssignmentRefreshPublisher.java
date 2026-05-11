package com.beyond.wbs.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkerAssignmentRefreshPublisher {

    private final WebSocketPublisher webSocketPublisher;

    public void publishRefresh(String module, UUID clientId, UUID workerId, UUID orderId, String orderNo) {
        if (module == null || clientId == null || workerId == null || orderId == null) {
            return;
        }

        WorkEventMessage msg = WorkEventMessage.builder()
                .module(module)
                .type("ASSIGNMENT_REFRESH")
                .clientId(clientId)
                .orderId(orderId)
                .orderNo(orderNo)
                .userId(workerId)
                .occurredAt(LocalDateTime.now())
                .build();

        webSocketPublisher.send("/topic/worker/" + workerId + "/assignments/refresh", msg);
        webSocketPublisher.send("/topic/worker/" + workerId + "/" + module + "/refresh", msg);
    }
}

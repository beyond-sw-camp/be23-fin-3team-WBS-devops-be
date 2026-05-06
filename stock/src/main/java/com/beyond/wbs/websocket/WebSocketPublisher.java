package com.beyond.wbs.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 알림 발행 단일 진입점.
 *
 * <p>모든 서비스 코드는 이 컴포넌트의 {@link #send(String, Object)} 만 호출한다.
 * 내부적으로 Redis Pub/Sub 채널({@link #CHANNEL})로 메시지를 던지면,
 * 각 서버 인스턴스의 구독자가 받아 자기 SimpleBroker로 흘려보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    /** 모든 서버 인스턴스가 함께 구독하는 Redis Pub/Sub 채널명. */
    public static final String CHANNEL = "ws-broadcast";

    private final RedisTemplate<String, Object> webSocketRedisTemplate;

    /**
     * 지정된 STOMP destination 으로 payload를 발행한다.
     *
     * <p>Redis 발행 실패 시 예외를 호출부로 전파하지 않는다.
     * 알림은 데이터 정합성보다 우선순위가 낮으므로, 실패해도
     * 진행 중인 비즈니스 트랜잭션이 롤백되지 않게 한다.
     */
    public void send(String destination, Object payload) {
        try {
            log.info("[WS publish] sending destination={} payloadClass={}",
                    destination, payload != null ? payload.getClass().getSimpleName() : "null");
            WebSocketBroadcastEnvelope envelope =
                    new WebSocketBroadcastEnvelope(destination, payload);
            webSocketRedisTemplate.convertAndSend(CHANNEL, envelope);
            log.info("[WS publish] redis send OK destination={}", destination);
        } catch (Exception e) {
            log.warn("WebSocket 발행 실패 destination={} payload={}",
                    destination, payload, e);
        }
    }
}

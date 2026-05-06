package com.beyond.wbs.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 채널({@link WebSocketPublisher#CHANNEL})로 흘러온 메시지를
 * 자기 인스턴스의 SimpleBroker 로 흘려보내는 다리.
 *
 * <p>모든 서버 인스턴스가 이 구독자를 갖고 있으므로,
 * 어느 서버가 발행하든 모든 서버가 동일하게 받아 클라이언트에게 전달한다.
 *
 * <p>역직렬화기는 {@link RedisWebSocketConfig#webSocketRedisSerializer()} 빈을
 * 그대로 주입받아 발행 측과 동일한 ObjectMapper 설정을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final GenericJackson2JsonRedisSerializer webSocketRedisSerializer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebSocketBroadcastEnvelope envelope =
                    (WebSocketBroadcastEnvelope) webSocketRedisSerializer.deserialize(message.getBody());

            if (envelope == null) {
                log.warn("[WS subscriber] envelope null");
                return;
            }

            log.info("[WS subscriber] received envelope dest={} payloadClass={}",
                    envelope.getDestination(),
                    envelope.getPayload() != null ? envelope.getPayload().getClass().getSimpleName() : "null");
            messagingTemplate.convertAndSend(envelope.getDestination(), envelope.getPayload());
            log.info("[WS subscriber] forwarded to STOMP dest={}", envelope.getDestination());
        } catch (Exception e) {
            log.warn("Redis WebSocket 메시지 처리 실패", e);
        }
    }
}

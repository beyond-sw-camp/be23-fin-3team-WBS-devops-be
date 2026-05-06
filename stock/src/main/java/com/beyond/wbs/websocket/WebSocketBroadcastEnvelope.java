package com.beyond.wbs.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Pub/Sub 채널을 통해 다중 서버 인스턴스로 전파되는 메시지 봉투.
 *
 * <p>Redis 채널은 destination(STOMP 경로) 정보를 모르므로,
 * 받는 서버가 자기 SimpleBroker로 어디에 라우팅할지 알 수 있도록
 * destination을 payload와 함께 실어 보낸다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketBroadcastEnvelope {

    /** STOMP destination (예: "/topic/admin/inbound") */
    private String destination;

    /** 클라이언트로 전달될 실제 payload (예: WorkEventMessage) */
    private Object payload;
}

package com.beyond.wbs.websocket;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * WebSocket 알림용 Redis Pub/Sub 인프라 설정.
 *
 * <p>common 모듈의 {@code accountConnectionFactory} 를 재활용하며,
 * 빈 이름은 {@code webSocketRedisTemplate} / {@code webSocketRedisMessageListenerContainer}
 * 로 두어 common 의 기존 {@code redisTemplate} 빈과 충돌하지 않게 한다.
 *
 * <p>직렬화기({@link #webSocketRedisSerializer})는 발행 측 RedisTemplate 과
 * 수신 측 Subscriber 가 함께 주입받아 사용한다.
 */
@Configuration
public class RedisWebSocketConfig {

    @Bean
    public GenericJackson2JsonRedisSerializer webSocketRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisTemplate<String, Object> webSocketRedisTemplate(
            @Qualifier("accountRedis") RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer webSocketRedisSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(webSocketRedisSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(webSocketRedisSerializer);
        return template;
    }

    @Bean
    public RedisMessageListenerContainer webSocketRedisMessageListenerContainer(
            @Qualifier("accountRedis") RedisConnectionFactory connectionFactory,
            WebSocketRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(WebSocketPublisher.CHANNEL));
        return container;
    }
}

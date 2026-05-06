package com.beyond.wbs.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * 공통 Kafka producer 설정.
 *
 * Spring Boot 기본 KafkaTemplate 의 value-serializer 는 StringSerializer 라
 * AuditLogEvent 같은 POJO 를 보내려 하면 직렬화 실패한다.
 * 본 Config 를 common 모듈에 두어 모든 서비스 (account/master/stock 등) 가
 * 별도 yml 설정 없이도 JsonSerializer 를 쓰게 한다.
 *
 * bootstrap-servers 등 다른 producer 속성은 각 서비스 yml 의 spring.kafka.* 가 그대로 반영됨
 * (KafkaProperties 가 yml 을 읽어 ProducerFactory 에 주입). key/value serializer 만 본 Config 가 지정.
 */
@Configuration
public class CommonKafkaProducerConfig {

    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

package com.beyond.wbs.ai.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;

/**
 * WebFlux 환경에서는 Spring Boot 가 HttpMessageConverters 를 자동 생성하지 않는다.
 * OpenFeign 은 이 빈을 요구하므로 수동으로 최소 세트를 제공한다.
 *
 * (ByteArray, UTF-8 String, Jackson JSON — Feign 이 JSON 페이로드를 역직렬화하는 데 충분)
 */
@Configuration
public class FeignMessageConvertersConfig {

    @Bean
    public HttpMessageConverters httpMessageConverters() {
        return new HttpMessageConverters(
                new ByteArrayHttpMessageConverter(),
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter()
        );
    }
}

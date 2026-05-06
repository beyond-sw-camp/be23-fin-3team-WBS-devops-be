package com.beyond.wbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 엔티티/리포지토리 스캔을 ai-service 자기 패키지로 제한.
 * common 라이브러리의 MySQL 전용 컴포넌트(audit, code, redis, s3 등)는 ComponentScan에서 제외.
 */
@SpringBootApplication
@EnableFeignClients
@EntityScan(basePackages = "com.beyond.wbs.ai")
@EnableJpaRepositories(basePackages = "com.beyond.wbs.ai")
@ComponentScan(
        basePackages = "com.beyond.wbs",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.beyond\\.wbs\\.audit\\..*",
                        "com\\.beyond\\.wbs\\.code\\..*",
                        "com\\.beyond\\.wbs\\.redis\\..*",
                        "com\\.beyond\\.wbs\\.s3\\..*",
                        "com\\.beyond\\.wbs\\.search\\..*",
                        "com\\.beyond\\.wbs\\.auth\\.Permission.*"
                }
        )
)
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}

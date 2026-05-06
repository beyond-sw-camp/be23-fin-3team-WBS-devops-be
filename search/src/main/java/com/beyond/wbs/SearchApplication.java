package com.beyond.wbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Search Service — CQRS 읽기 전용 서비스.
 *
 * Kafka 이벤트를 수신하여 Elasticsearch에 색인하고,
 * 전문 검색 API를 제공한다.
 *
 * RDB 불필요 — ES만 사용하므로 DataSource/JPA/Transaction 자동 설정 제외.
 * common 모듈의 JPA 기반 컴포넌트(AuditLogAspect, AuditLogController, AuditLogRepository 등)는
 * search 에서 불필요하므로 스캔에서 제외.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
@ComponentScan(
        basePackages = "com.beyond.wbs",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.beyond\\.wbs\\.audit\\.AuditLog(?:Aspect|Controller|Repository).*",
                        "com\\.beyond\\.wbs\\.redis\\..*",
                        "com\\.beyond\\.wbs\\.s3\\..*",
                        "com\\.beyond\\.wbs\\.search\\..*",
                        "com\\.beyond\\.wbs\\.auth\\.Permission.*",
                        "com\\.beyond\\.wbs\\.code\\..*",
                        "com\\.beyond\\.wbs\\.exception\\..*"
                }
        )
)
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}

package com.beyond.wbs.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Text-to-SQL 전용 2차 DataSource.
 *
 * 핵심 포인트: defaultCandidate = false
 *   Spring Boot 자동설정이 primary DataSource를 spring.datasource 로 만들어 두는데,
 *   이 빈을 autowire 후보에 포함시키면 PgVectorStore 같은 오토컨피그가 잘못 주입받을 수 있다.
 *   defaultCandidate = false 로 "명시적 @Qualifier 요청 시에만 주입" 되게 한다.
 */
@Configuration
public class ReadonlyDataSourceConfig {

    @Bean(name = "readonlyDataSource", defaultCandidate = false)
    @ConfigurationProperties(prefix = "wms.readonly-datasource")
    public DataSource readonlyDataSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        // ConfigurationProperties 바인딩 이후에도 유지되는 옵션은 여기서 직접 설정
        // (url/username/password/driver-class-name 은 yml 로 바인딩됨)
        return ds;
    }

    @Bean(name = "readonlyJdbcTemplate", defaultCandidate = false)
    public JdbcTemplate readonlyJdbcTemplate(
            @Qualifier("readonlyDataSource") DataSource dataSource) {
        // ConfigurationProperties 바인딩이 끝난 후 read-only 옵션을 직접 켠다
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.setReadOnly(true);
            hikari.setMaximumPoolSize(3);
            hikari.setPoolName("readonly-pool");
        }
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(10);
        template.setMaxRows(100);
        return template;
    }
}

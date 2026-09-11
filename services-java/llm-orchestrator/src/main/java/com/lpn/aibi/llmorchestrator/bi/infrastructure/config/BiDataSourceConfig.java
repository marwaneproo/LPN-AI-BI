package com.lpn.aibi.llmorchestrator.bi.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class BiDataSourceConfig {

    @Bean
    DataSource biReadonlyDataSource(
            @Value("${bi.datasource.readonly.url}") String url,
            @Value("${bi.datasource.readonly.username}") String username,
            @Value("${bi.datasource.readonly.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    JdbcTemplate biReadonlyJdbcTemplate(
            @Qualifier("biReadonlyDataSource") DataSource dataSource,
            @Value("${bi.datasource.readonly.query-timeout-seconds:30}") int queryTimeoutSeconds) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(queryTimeoutSeconds);
        return jdbcTemplate;
    }
}

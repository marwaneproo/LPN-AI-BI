package com.lpn.aibi.sqlexecutor;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class DataSourceConfig {

    @Bean
    @Primary
    DataSource appAdminDataSource(
            @Value("${datasources.app-admin.url}") String url,
            @Value("${datasources.app-admin.username}") String username,
            @Value("${datasources.app-admin.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    DataSource readonlyDataSource(
            @Value("${datasources.readonly.url}") String url,
            @Value("${datasources.readonly.username}") String username,
            @Value("${datasources.readonly.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    JdbcTemplate readonlyJdbcTemplate(
            @Qualifier("readonlyDataSource") DataSource readonlyDataSource,
            @Value("${sql-executor.query-timeout-seconds}") int queryTimeoutSeconds) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(readonlyDataSource);
        jdbcTemplate.setQueryTimeout(queryTimeoutSeconds);
        return jdbcTemplate;
    }
}

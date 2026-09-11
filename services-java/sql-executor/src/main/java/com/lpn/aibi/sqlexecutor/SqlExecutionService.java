package com.lpn.aibi.sqlexecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class SqlExecutionService {

    private final SqlValidatorClient sqlValidatorClient;
    private final JdbcTemplate readonlyJdbcTemplate;
    private final int maxRowCount;

    SqlExecutionService(
            SqlValidatorClient sqlValidatorClient,
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbcTemplate,
            @Value("${sql-executor.max-row-count}") int maxRowCount) {
        this.sqlValidatorClient = sqlValidatorClient;
        this.readonlyJdbcTemplate = readonlyJdbcTemplate;
        this.maxRowCount = maxRowCount;
    }

    SqlExecutionResult execute(String sql) {
        Instant startedAt = Instant.now();
        SqlValidationResult validation = sqlValidatorClient.validate(sql);

        if (!validation.valid() || !validation.safe()) {
            throw new UnsafeSqlException(validation);
        }

        String rewrittenSql = validation.rewrittenSql();
        if (rewrittenSql == null || rewrittenSql.isBlank()) {
            throw new UnsafeSqlException(validation);
        }

        List<Map<String, Object>> rows = readonlyJdbcTemplate.queryForList(rewrittenSql);
        if (rows.size() > maxRowCount) {
            rows = rows.subList(0, maxRowCount);
        }

        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new SqlExecutionResult(rows, rows.size(), rewrittenSql, latencyMs, validation);
    }
}

package com.lpn.aibi.sqlexecutor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class SqlExecutionIntegrationTest {

    @Autowired
    private SqlExecutionService sqlExecutionService;

    @Test
    void executesSafeSqlAgainstRealPostgresAndValidator() {
        SqlExecutionResult result = sqlExecutionService.execute("SELECT 1 AS x");

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().get(0)).containsEntry("x", 1);
        assertThat(result.executedSql()).containsIgnoringCase("LIMIT 1000");
        assertThat(result.validation().safe()).isTrue();
    }
}

package com.lpn.aibi.sqlexecutor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "datasources.app-admin.url=jdbc:h2:mem:sql_executor_admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "datasources.app-admin.username=sa",
        "datasources.app-admin.password=",
        "datasources.readonly.url=jdbc:h2:mem:sql_executor_readonly;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "datasources.readonly.username=sa",
        "datasources.readonly.password=",
        "sql-executor.query-timeout-seconds=5",
        "sql-executor.max-row-count=10000"
})
@AutoConfigureMockMvc
class SqlExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate readonlyJdbcTemplate;

    @MockBean
    private SqlValidatorClient sqlValidatorClient;

    @BeforeEach
    void setUp() {
        readonlyJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id int primary key, name varchar(100))");
        readonlyJdbcTemplate.update("MERGE INTO users KEY(id) VALUES (1, 'Youssef')");
    }

    @Test
    void executeSqlRunsValidatorRewrittenSqlAndReturnsRows() throws Exception {
        when(sqlValidatorClient.validate("SELECT * FROM users"))
                .thenReturn(safeValidation("SELECT * FROM users LIMIT 1000"));

        mockMvc.perform(post("/v1/execute-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT * FROM users\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("Youssef"))
                .andExpect(jsonPath("$.row_count").value(1))
                .andExpect(jsonPath("$.executed_sql").value("SELECT * FROM users LIMIT 1000"))
                .andExpect(jsonPath("$.latency_ms").isNumber())
                .andExpect(jsonPath("$.validation.is_safe").value(true));

        verify(sqlValidatorClient).validate("SELECT * FROM users");
    }

    @Test
    void executeSqlRejectsUnsafeSqlBeforeJdbcExecution() throws Exception {
        SqlValidationResult unsafeValidation = new SqlValidationResult(
                true,
                false,
                1,
                "DELETE",
                List.of("business.users"),
                List.of("DELETE"),
                List.of(),
                null,
                List.of());
        when(sqlValidatorClient.validate("DELETE FROM users")).thenReturn(unsafeValidation);

        mockMvc.perform(post("/v1/execute-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"DELETE FROM users\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSAFE_SQL"))
                .andExpect(jsonPath("$.forbidden_constructs[0]").value("DELETE"));

        verify(sqlValidatorClient).validate("DELETE FROM users");
    }

    @Test
    void executeSqlRejectsMissingRewrittenSql() throws Exception {
        SqlValidationResult invalidValidation = new SqlValidationResult(
                true,
                true,
                1,
                "SELECT",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of());
        when(sqlValidatorClient.validate(anyString())).thenReturn(invalidValidation);

        mockMvc.perform(post("/v1/execute-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSAFE_SQL"));
    }

    private SqlValidationResult safeValidation(String rewrittenSql) {
        return new SqlValidationResult(
                true,
                true,
                1,
                "SELECT",
                List.of("business.users"),
                List.of(),
                List.of(),
                rewrittenSql,
                List.of());
    }
}

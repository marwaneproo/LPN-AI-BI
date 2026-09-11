package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "auth.api-protection-enabled=false"
})
@AutoConfigureMockMvc
class SqlGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchemaRetrievalClient schemaRetrievalClient;

    @MockBean
    @Qualifier("sqlGenerationModel")
    private ChatModel sqlGenerationModel;

    @MockBean
    @Qualifier("sqlGenerationFallbackModel")
    private ChatModel sqlGenerationFallbackModel;

    @MockBean
    @Qualifier("sqlGenerationReasoningModel")
    private ChatModel sqlGenerationReasoningModel;

    @MockBean
    @Qualifier("narratorModel")
    private ChatModel narratorModel;

    @MockBean
    private PerformanceTraceRepository performanceTraceRepository;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthRepository authRepository;

    @MockBean
    private ChatSessionRepository chatSessionRepository;

    @Test
    void generateSqlBuildsSchemaPromptAndReturnsStrippedSql() throws Exception {
        when(schemaRetrievalClient.retrieve("How many orders were placed last month?", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationModel.chat(anyString()))
                .thenReturn("""
                        ```sql
                        SELECT COUNT(*)
                        FROM C_ORDER;
                        ```
                        """);

        mockMvc.perform(post("/v1/generate-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many orders were placed last month?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT COUNT(*)\nFROM C_ORDER;"))
                .andExpect(jsonPath("$.retrieved_tables[0].table_name").value("C_ORDER"))
                .andExpect(jsonPath("$.latency_ms").isNumber())
                .andExpect(jsonPath("$.latency_breakdown_ms.schema_retrieval").isNumber())
                .andExpect(jsonPath("$.latency_breakdown_ms.sql_model").isNumber())
                .andExpect(jsonPath("$.latency_breakdown_ms.total").isNumber())
                .andExpect(jsonPath("$.request_mode").value("standard"))
                .andExpect(jsonPath("$.reasoning_mode").value(false))
                .andExpect(jsonPath("$.model_used").value("qwen2.5-coder:7b"))
                .andExpect(jsonPath("$.fallback_used").value(false))
                .andExpect(jsonPath("$.trace_id").isString());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlGenerationModel, times(1)).chat(promptCaptor.capture());
        verify(sqlGenerationFallbackModel, never()).chat(anyString());
        ArgumentCaptor<PerformanceTrace> traceCaptor = ArgumentCaptor.forClass(PerformanceTrace.class);
        verify(performanceTraceRepository, times(1)).save(traceCaptor.capture());
        PerformanceTrace trace = traceCaptor.getValue();
        assertThat(trace.question()).isEqualTo("How many orders were placed last month?");
        assertThat(trace.status()).isEqualTo("SUCCESS");
        assertThat(trace.requestMode()).isEqualTo("standard");
        assertThat(trace.reasoningMode()).isFalse();
        assertThat(trace.sqlModel()).isEqualTo("qwen2.5-coder:7b");
        assertThat(trace.sqlReasoningModel()).isEqualTo("qwen2.5-coder:14b");
        assertThat(trace.modelUsed()).isEqualTo("qwen2.5-coder:7b");
        assertThat(trace.retrievedTables()).hasSize(1);
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("You are a PostgreSQL expert");
        assertThat(prompt).contains("Output ONLY the SQL");
        assertThat(prompt).contains("Table: C_ORDER");
        assertThat(prompt).contains("Columns: C_ORDER_ID:bigint:Primary order identifier");
        assertThat(prompt).contains("Notes: Use DATEORDERED for order period filters.");
        assertThat(prompt).contains("Question: How many orders were placed last month?");
    }

    @Test
    void generateSqlFallsBackWhenPrimaryModelFails() throws Exception {
        when(schemaRetrievalClient.retrieve("How many orders were placed last month?", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationModel.chat(anyString()))
                .thenThrow(new RuntimeException("primary model failed"));
        when(sqlGenerationFallbackModel.chat(anyString()))
                .thenReturn("SELECT COUNT(*) FROM C_ORDER;");

        mockMvc.perform(post("/v1/generate-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many orders were placed last month?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT COUNT(*) FROM C_ORDER;"))
                .andExpect(jsonPath("$.retrieved_tables[0].table_name").value("C_ORDER"));

        verify(sqlGenerationModel, times(1)).chat(anyString());
        verify(sqlGenerationFallbackModel, times(1)).chat(anyString());
        ArgumentCaptor<PerformanceTrace> traceCaptor = ArgumentCaptor.forClass(PerformanceTrace.class);
        verify(performanceTraceRepository, times(1)).save(traceCaptor.capture());
        assertThat(traceCaptor.getValue().fallbackUsed()).isTrue();
    }

    @Test
    void generateSqlUsesReasoningModelWhenRequested() throws Exception {
        when(schemaRetrievalClient.retrieve("Compare two order days", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationReasoningModel.chat(anyString()))
                .thenReturn("SELECT DATEORDERED::date, SUM(GRANDTOTAL) FROM C_ORDER GROUP BY DATEORDERED::date;");

        mockMvc.perform(post("/v1/generate-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Compare two order days\",\"mode\":\"reasoning\",\"reasoning_mode\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_mode").value("reasoning"))
                .andExpect(jsonPath("$.reasoning_mode").value(true))
                .andExpect(jsonPath("$.model_used").value("qwen2.5-coder:14b"));

        verify(sqlGenerationModel, never()).chat(anyString());
        verify(sqlGenerationReasoningModel, times(1)).chat(anyString());
        verify(sqlGenerationFallbackModel, never()).chat(anyString());
    }

    @Test
    void normalizeSqlExtractsSelectFromReasoningResponse() {
        String sql = SqlGenerationService.normalizeSql("""
                </think>
                <plan>Use orders.</plan>
                <execute>
                ```sql
                SELECT COUNT(*)
                FROM C_ORDER;
                ```
                </execute>
                """);

        assertThat(sql).isEqualTo("SELECT COUNT(*)\nFROM C_ORDER;");
    }

    @Test
    void normalizeSqlPreservesCteQueries() {
        String sql = SqlGenerationService.normalizeSql("""
                Here is the SQL:
                WITH last_month_orders AS (
                    SELECT C_BPARTNER_ID, GRANDTOTAL
                    FROM C_ORDER
                ),
                top_customers AS (
                    SELECT C_BPARTNER_ID, SUM(GRANDTOTAL) AS total_order_value
                    FROM last_month_orders
                    GROUP BY C_BPARTNER_ID
                )
                SELECT C_BPARTNER_ID, total_order_value
                FROM top_customers;
                """);

        assertThat(sql).startsWith("WITH last_month_orders AS");
        assertThat(sql).contains("SELECT C_BPARTNER_ID, total_order_value");
    }

    private RetrievedTable orderTable() {
        return new RetrievedTable(
                "C_ORDER",
                "Sales",
                "Sales order header storing order dates status totals customer and document type.",
                "",
                "C_ORDER_ID:bigint:Primary order identifier|DATEORDERED:timestamp:Order date",
                "C_ORDER_ID -> C_ORDERLINE (C_ORDER_ID)",
                "Use DATEORDERED for order period filters.",
                0.91,
                null);
    }
}

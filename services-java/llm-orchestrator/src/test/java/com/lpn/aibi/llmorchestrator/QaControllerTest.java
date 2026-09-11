package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
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
class QaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchemaRetrievalClient schemaRetrievalClient;

    @MockBean
    private SqlExecutorClient sqlExecutorClient;

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
    void qaGeneratesExecutesAndNarratesAnswer() throws Exception {
        when(schemaRetrievalClient.retrieve("How many orders were placed last month?", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationModel.chat(anyString()))
                .thenReturn("""
                        SELECT COUNT(*) AS total_orders
                        FROM C_ORDER
                        WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                          AND DATEORDERED < date_trunc('month', CURRENT_DATE);
                        """);
        when(sqlExecutorClient.execute("""
                SELECT COUNT(*) AS total_orders
                FROM C_ORDER
                WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND DATEORDERED < date_trunc('month', CURRENT_DATE);
                """.trim()))
                .thenReturn(new SqlExecutionResult(
                        List.of(Map.of("total_orders", 7)),
                        1,
                        """
                        SELECT COUNT(*) AS total_orders
                        FROM C_ORDER
                        WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                          AND DATEORDERED < date_trunc('month', CURRENT_DATE);
                        """.trim(),
                        12,
                        validation()));
        when(narratorModel.chat(anyString()))
                .thenReturn("Il y a eu 7 commandes sur la periode demandee.");

        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many orders were placed last month?\",\"language\":\"fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Résultat: total orders = 7."))
                .andExpect(jsonPath("$.sql").value("""
                        SELECT COUNT(*) AS total_orders
                        FROM C_ORDER
                        WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                          AND DATEORDERED < date_trunc('month', CURRENT_DATE);
                        """.trim()))
                .andExpect(jsonPath("$.rows[0].total_orders").value(7))
                .andExpect(jsonPath("$.row_count").value(1))
                .andExpect(jsonPath("$.retrieved_tables[0].table_name").value("C_ORDER"))
                .andExpect(jsonPath("$.execution_status").value("SUCCESS"))
                .andExpect(jsonPath("$.request_mode").value("standard"))
                .andExpect(jsonPath("$.reasoning_mode").value(false))
                .andExpect(jsonPath("$.latency_breakdown_ms.sql_execution").value(12))
                .andExpect(jsonPath("$.trace_id").isString());

        verify(sqlExecutorClient, times(1)).execute("""
                SELECT COUNT(*) AS total_orders
                FROM C_ORDER
                WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND DATEORDERED < date_trunc('month', CURRENT_DATE);
                """.trim());
        verify(narratorModel, never()).chat(anyString());

        ArgumentCaptor<PerformanceTrace> traceCaptor = ArgumentCaptor.forClass(PerformanceTrace.class);
        verify(performanceTraceRepository, times(1)).save(traceCaptor.capture());
        PerformanceTrace trace = traceCaptor.getValue();
        assertThat(trace.endpoint()).isEqualTo("/v1/qa");
        assertThat(trace.status()).isEqualTo("SUCCESS");
        assertThat(trace.requestMode()).isEqualTo("standard");
        assertThat(trace.reasoningMode()).isFalse();
        assertThat(trace.sqlExecutionLatencyMs()).isEqualTo(12);
        assertThat(trace.narrationLatencyMs()).isNotNull();
    }

    @Test
    void qaRefusesDestructiveIntentBeforeGeneratingSql() throws Exception {
        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"delete all orders\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_status").value("REFUSED"))
                .andExpect(jsonPath("$.answer").value("I cannot modify or delete database data. I can only answer read-only business questions."))
                .andExpect(jsonPath("$.row_count").value(0));

        verify(schemaRetrievalClient, never()).retrieve(anyString(), org.mockito.Mockito.anyInt(), anyString());
        verify(sqlGenerationModel, never()).chat(anyString());
        verify(sqlExecutorClient, never()).execute(anyString());
    }

    @Test
    void qaRefusesObviousOutOfScopeQuestionBeforeGeneratingSql() throws Exception {
        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What's the weather today?\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_status").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.answer").value("I am LPN's BI assistant, so I can only answer questions about the imported company data."))
                .andExpect(jsonPath("$.row_count").value(0));

        verify(schemaRetrievalClient, never()).retrieve(anyString(), org.mockito.Mockito.anyInt(), anyString());
        verify(sqlGenerationModel, never()).chat(anyString());
        verify(sqlExecutorClient, never()).execute(anyString());
    }

    @Test
    void qaReturnsGuardedRejectionInsteadOfServerError() throws Exception {
        when(schemaRetrievalClient.retrieve("Show all orders", 8, "en")).thenReturn(List.of(orderTable()));
        when(sqlGenerationModel.chat(anyString())).thenReturn("DELETE FROM C_ORDER;");
        when(sqlExecutorClient.execute("DELETE FROM C_ORDER;"))
                .thenThrow(new SqlExecutionException(
                        422,
                        "UNSAFE_SQL",
                        "SQL validation rejected this query.",
                        List.of("DELETE"),
                        List.of("Only SELECT statements are allowed.")));

        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Show all orders\",\"language\":\"fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_status").value("SQL_REJECTED"))
                .andExpect(jsonPath("$.answer").value("Le SQL genere n'a pas ete execute car il a ete rejete par la couche d'execution securisee."))
                .andExpect(jsonPath("$.error").value("Only SELECT statements are allowed."));

        verify(narratorModel, never()).chat(anyString());
    }

    @Test
    void qaUsesReasoningModelWhenThinkingModeIsRequested() throws Exception {
        when(schemaRetrievalClient.retrieve("Compare two order days", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationReasoningModel.chat(anyString()))
                .thenReturn("SELECT COUNT(*) AS total_orders FROM C_ORDER;");
        when(sqlExecutorClient.execute("SELECT COUNT(*) AS total_orders FROM C_ORDER;"))
                .thenReturn(new SqlExecutionResult(
                        List.of(Map.of("total_orders", 7)),
                        1,
                        "SELECT COUNT(*) AS total_orders FROM C_ORDER;",
                        12,
                        validation()));
        when(narratorModel.chat(anyString())).thenReturn("Il y a 7 commandes.");

        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Compare two order days\",\"language\":\"fr\",\"mode\":\"reasoning\",\"reasoning_mode\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_mode").value("reasoning"))
                .andExpect(jsonPath("$.reasoning_mode").value(true))
                .andExpect(jsonPath("$.model_used").value("qwen2.5-coder:14b"));

        verify(sqlGenerationModel, never()).chat(anyString());
        verify(sqlGenerationReasoningModel, times(1)).chat(anyString());
    }

    @Test
    void qaRepairsSemanticSqlIssueBeforeExecution() throws Exception {
        when(schemaRetrievalClient.retrieve("What is the total order value and average order value?", 8, "en"))
                .thenReturn(List.of(orderTable()));
        when(sqlGenerationModel.chat(anyString()))
                .thenReturn("SELECT SUM(GRANDTOTAL) AS total_order_value FROM C_ORDER WHERE DOCSTATUS = 'CO';")
                .thenReturn("SELECT SUM(GRANDTOTAL) AS total_order_value, AVG(GRANDTOTAL) AS average_order_value FROM C_ORDER;");
        when(sqlExecutorClient.execute("SELECT SUM(GRANDTOTAL) AS total_order_value, AVG(GRANDTOTAL) AS average_order_value FROM C_ORDER;"))
                .thenReturn(new SqlExecutionResult(
                        List.of(Map.of("total_order_value", 16709787.11, "average_order_value", 4988.00)),
                        1,
                        "SELECT SUM(GRANDTOTAL) AS total_order_value, AVG(GRANDTOTAL) AS average_order_value FROM C_ORDER;",
                        9,
                        validation()));
        when(narratorModel.chat(anyString())).thenReturn("Le total est de 16 709 787,11 et la moyenne est de 4 988,00.");

        mockMvc.perform(post("/v1/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the total order value and average order value?\",\"language\":\"fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repair_attempted").value(true))
                .andExpect(jsonPath("$.semantic_validation.valid").value(true))
                .andExpect(jsonPath("$.repaired_sql").value("SELECT SUM(GRANDTOTAL) AS total_order_value, AVG(GRANDTOTAL) AS average_order_value FROM C_ORDER;"))
                .andExpect(jsonPath("$.execution_status").value("SUCCESS"));

        verify(sqlExecutorClient, times(1))
                .execute("SELECT SUM(GRANDTOTAL) AS total_order_value, AVG(GRANDTOTAL) AS average_order_value FROM C_ORDER;");
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

    private SqlValidationResult validation() {
        return new SqlValidationResult(
                true,
                true,
                1,
                "SELECT",
                List.of("C_ORDER"),
                List.of(),
                List.of(),
                "SELECT COUNT(*) AS total_orders FROM C_ORDER LIMIT 1000",
                List.of());
    }
}

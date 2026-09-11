package com.lpn.aibi.llmorchestrator;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "auth.api-protection-enabled=false"
})
@AutoConfigureMockMvc
class ForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PredictiveClient predictiveClient;

    // Required by QaService / SqlGenerationService wired in the same Spring context
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

    // -----------------------------------------------------------------------
    // /v1/forecast/ca
    // -----------------------------------------------------------------------

    @Test
    void forecastCaProxiesDefaultParamsToClient() throws Exception {
        when(predictiveClient.forecastCa("company", null, 6))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "model", "prophet_naive_blend_05",
                        "grain", "company",
                        "history", List.of(),
                        "forecast", List.of())));

        mockMvc.perform(get("/v1/forecast/ca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("prophet_naive_blend_05"))
                .andExpect(jsonPath("$.grain").value("company"));

        verify(predictiveClient).forecastCa("company", null, 6);
    }

    @Test
    void forecastCaForwardsGrainHorizonAndKeyParams() throws Exception {
        when(predictiveClient.forecastCa("commercial", 1006247, 12))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "model", "prophet_naive_blend_05",
                        "grain", "commercial",
                        "forecast", List.of())));

        mockMvc.perform(get("/v1/forecast/ca")
                        .param("grain", "commercial")
                        .param("key", "1006247")
                        .param("horizon", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grain").value("commercial"));

        verify(predictiveClient).forecastCa("commercial", 1006247, 12);
    }

    @Test
    void forecastCaPassesThroughInsufficientHistoryResponse() throws Exception {
        when(predictiveClient.forecastCa("theme", 999999, 6))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "error", "insufficient_history",
                        "available_months", 0,
                        "minimum_months", 12)));

        mockMvc.perform(get("/v1/forecast/ca")
                        .param("grain", "theme")
                        .param("key", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("insufficient_history"))
                .andExpect(jsonPath("$.available_months").value(0))
                .andExpect(jsonPath("$.minimum_months").value(12));
    }

    // -----------------------------------------------------------------------
    // /v1/forecast/backtest
    // -----------------------------------------------------------------------

    @Test
    void forecastBacktestProxiesDefaultParamsToClient() throws Exception {
        when(predictiveClient.forecastBacktest("company", null, 6))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "grain", "company",
                        "holdout_months", 6,
                        "train_months", 18,
                        "prophet_beats_naive_wape", false)));

        mockMvc.perform(get("/v1/forecast/backtest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grain").value("company"))
                .andExpect(jsonPath("$.holdout_months").value(6))
                .andExpect(jsonPath("$.train_months").value(18))
                .andExpect(jsonPath("$.prophet_beats_naive_wape").value(false));

        verify(predictiveClient).forecastBacktest("company", null, 6);
    }

    @Test
    void forecastBacktestForwardsGrainHoldoutAndKeyParams() throws Exception {
        when(predictiveClient.forecastBacktest("commercial", 1006247, 9))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "grain", "commercial",
                        "holdout_months", 9)));

        mockMvc.perform(get("/v1/forecast/backtest")
                        .param("grain", "commercial")
                        .param("key", "1006247")
                        .param("holdout", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grain").value("commercial"))
                .andExpect(jsonPath("$.holdout_months").value(9));

        verify(predictiveClient).forecastBacktest("commercial", 1006247, 9);
    }

    @Test
    void forecastBacktestPropagatesUpstreamStatusCode() throws Exception {
        when(predictiveClient.forecastBacktest("company", null, 6))
                .thenReturn(ResponseEntity.status(422).body(Map.of(
                        "detail", "holdout must be between 3 and 9")));

        mockMvc.perform(get("/v1/forecast/backtest"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("holdout must be between 3 and 9"));
    }

    // -----------------------------------------------------------------------
    // /v1/forecast/options
    // -----------------------------------------------------------------------

    @Test
    void forecastOptionsProxiesGrainParam() throws Exception {
        when(predictiveClient.forecastOptions("category"))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("key", 1113590, "label", "LITTÉRATURE GÉNÉRALE",
                               "complete_months", 24, "forecastable", true),
                        Map.of("key", 1113600, "label", "SCOLAIRE",
                               "complete_months", 18, "forecastable", true))));

        mockMvc.perform(get("/v1/forecast/options").param("grain", "category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value(1113590))
                .andExpect(jsonPath("$[0].label").value("LITTÉRATURE GÉNÉRALE"))
                .andExpect(jsonPath("$[0].forecastable").value(true))
                .andExpect(jsonPath("$[1].key").value(1113600));

        verify(predictiveClient).forecastOptions("category");
    }

    @Test
    void forecastOptionsDefaultsToCompanyGrain() throws Exception {
        when(predictiveClient.forecastOptions("company"))
                .thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(get("/v1/forecast/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(predictiveClient).forecastOptions("company");
    }
}

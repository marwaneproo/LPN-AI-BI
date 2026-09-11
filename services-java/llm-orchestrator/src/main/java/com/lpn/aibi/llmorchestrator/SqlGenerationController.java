package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SqlGenerationController {

    private final SqlGenerationService sqlGenerationService;
    private final PerformanceTraceRepository performanceTraceRepository;
    private final String sqlModel;
    private final String sqlReasoningModel;
    private final String sqlFallbackModel;
    private final String narratorModel;

    SqlGenerationController(
            SqlGenerationService sqlGenerationService,
            PerformanceTraceRepository performanceTraceRepository,
            @Value("${ollama.sql-model}") String sqlModel,
            @Value("${ollama.sql-reasoning-model}") String sqlReasoningModel,
            @Value("${ollama.sql-fallback-model}") String sqlFallbackModel,
            @Value("${ollama.narrator-model}") String narratorModel) {
        this.sqlGenerationService = sqlGenerationService;
        this.performanceTraceRepository = performanceTraceRepository;
        this.sqlModel = sqlModel;
        this.sqlReasoningModel = sqlReasoningModel;
        this.sqlFallbackModel = sqlFallbackModel;
        this.narratorModel = narratorModel;
    }

    @PostMapping("/v1/generate-sql")
    GenerateSqlResponse generateSql(@RequestBody GenerateSqlRequest request) {
        Instant startedAt = Instant.now();
        UUID traceId = UUID.randomUUID();
        SqlGenerationService.SqlModelMode requestMode =
                SqlGenerationService.SqlModelMode.from(request.mode(), request.reasoningMode());

        try {
            SqlGenerationService.SqlGenerationResult result = sqlGenerationService.generateSql(request.question(), requestMode);
            Instant completedAt = Instant.now();
            long latencyMs = elapsedMs(startedAt, completedAt);
            performanceTraceRepository.save(new PerformanceTrace(
                    traceId,
                    request.clientRequestId(),
                    "/v1/generate-sql",
                    request.question(),
                    "SUCCESS",
                    null,
                    result.requestMode(),
                    result.reasoningMode(),
                    sqlModel,
                    sqlReasoningModel,
                    sqlFallbackModel,
                    narratorModel,
                    result.modelUsed(),
                    result.fallbackUsed(),
                    result.sql(),
                    result.retrievedTables(),
                    request.frontendStartedAt(),
                    startedAt,
                    completedAt,
                    latencyMs,
                    result.schemaRetrievalLatencyMs(),
                    result.sqlModelLatencyMs(),
                    result.sqlNormalizationLatencyMs(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null));
            return new GenerateSqlResponse(
                    traceId,
                    result.sql(),
                    result.retrievedTables(),
                    latencyMs,
                    new LatencyBreakdown(
                            result.schemaRetrievalLatencyMs(),
                            result.sqlModelLatencyMs(),
                            result.sqlNormalizationLatencyMs(),
                            latencyMs),
                    result.requestMode(),
                    result.reasoningMode(),
                    result.modelUsed(),
                    result.fallbackUsed());
        } catch (RuntimeException exception) {
            Instant completedAt = Instant.now();
            long latencyMs = elapsedMs(startedAt, completedAt);
            performanceTraceRepository.save(new PerformanceTrace(
                    traceId,
                    request.clientRequestId(),
                    "/v1/generate-sql",
                    request.question(),
                    "ERROR",
                    exception.getMessage(),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    sqlModel,
                    sqlReasoningModel,
                    sqlFallbackModel,
                    narratorModel,
                    null,
                    false,
                    null,
                    List.of(),
                    request.frontendStartedAt(),
                    startedAt,
                    completedAt,
                    latencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null));
            throw exception;
        }
    }

    private static long elapsedMs(Instant startedAt, Instant completedAt) {
        return Math.max(1, Duration.between(startedAt, completedAt).toMillis());
    }

    record GenerateSqlRequest(
            String question,
            String mode,
            @JsonProperty("reasoning_mode") Boolean reasoningMode,
            @JsonProperty("client_request_id") String clientRequestId,
            @JsonProperty("frontend_started_at") Instant frontendStartedAt) {
    }

    record GenerateSqlResponse(
            @JsonProperty("trace_id") UUID traceId,
            String sql,
            @JsonProperty("retrieved_tables") List<RetrievedTable> retrievedTables,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("latency_breakdown_ms") LatencyBreakdown latencyBreakdownMs,
            @JsonProperty("request_mode") String requestMode,
            @JsonProperty("reasoning_mode") boolean reasoningMode,
            @JsonProperty("model_used") String modelUsed,
            @JsonProperty("fallback_used") boolean fallbackUsed) {
    }

    record LatencyBreakdown(
            @JsonProperty("schema_retrieval") long schemaRetrieval,
            @JsonProperty("sql_model") long sqlModel,
            @JsonProperty("sql_normalization") long sqlNormalization,
            long total) {
    }
}

package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PerformanceTraceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTraceRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private volatile boolean tableReady;

    PerformanceTraceRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${performance-tracing.enabled:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    void save(PerformanceTrace trace) {
        if (!enabled) {
            return;
        }

        try {
            ensureTable();
            jdbcTemplate.update(
                    """
                    INSERT INTO app.ai_request_performance_trace (
                      id,
                      client_request_id,
                      endpoint,
                      question,
                      status,
                      error_message,
                      request_mode,
                      reasoning_mode,
                      sql_model,
                      sql_reasoning_model,
                      sql_fallback_model,
                      narrator_model,
                      model_used,
                      fallback_used,
                      generated_sql,
                      retrieved_tables,
                      retrieved_table_count,
                      frontend_started_at,
                      backend_received_at,
                      backend_completed_at,
                      backend_total_latency_ms,
                      schema_retrieval_latency_ms,
                      sql_model_latency_ms,
                      sql_normalization_latency_ms,
                      sql_execution_latency_ms,
                      narration_latency_ms,
                      intent,
                      semantic_validation,
                      result_assessment,
                      repair_attempted,
                      repaired_sql,
                      sql_repair_latency_ms
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
                    """,
                    trace.id(),
                    trace.clientRequestId(),
                    trace.endpoint(),
                    trace.question(),
                    trace.status(),
                    trace.errorMessage(),
                    trace.requestMode(),
                    trace.reasoningMode(),
                    trace.sqlModel(),
                    trace.sqlReasoningModel(),
                    trace.sqlFallbackModel(),
                    trace.narratorModel(),
                    trace.modelUsed(),
                    trace.fallbackUsed(),
                    trace.generatedSql(),
                    retrievedTablesJson(trace.retrievedTables()),
                    trace.retrievedTables() == null ? 0 : trace.retrievedTables().size(),
                    timestamp(trace.frontendStartedAt()),
                    timestamp(trace.backendReceivedAt()),
                    timestamp(trace.backendCompletedAt()),
                    trace.backendTotalLatencyMs(),
                    trace.schemaRetrievalLatencyMs(),
                    trace.sqlModelLatencyMs(),
                    trace.sqlNormalizationLatencyMs(),
                    trace.sqlExecutionLatencyMs(),
                    trace.narrationLatencyMs(),
                    toJson(trace.intent()),
                    toJson(trace.semanticValidation()),
                    toJson(trace.resultAssessment()),
                    trace.repairAttempted(),
                    trace.repairedSql(),
                    trace.sqlRepairLatencyMs());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not persist AI performance trace {}", trace.id(), exception);
        }
    }

    void updateClientTiming(UUID traceId, Instant frontendCompletedAt, long clientTotalLatencyMs) {
        if (!enabled) {
            return;
        }

        try {
            ensureTable();
            jdbcTemplate.update(
                    """
                    UPDATE app.ai_request_performance_trace
                    SET frontend_completed_at = ?,
                        client_total_latency_ms = ?,
                        frontend_network_latency_ms =
                          CASE
                            WHEN backend_total_latency_ms IS NULL THEN NULL
                            ELSE GREATEST(? - backend_total_latency_ms, 0)
                          END
                    WHERE id = ?
                    """,
                    timestamp(frontendCompletedAt),
                    clientTotalLatencyMs,
                    clientTotalLatencyMs,
                    traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not update client timing for AI performance trace {}", traceId, exception);
        }
    }

    private void ensureTable() {
        if (tableReady) {
            return;
        }

        jdbcTemplate.execute(
                """
                CREATE SCHEMA IF NOT EXISTS app;
                CREATE TABLE IF NOT EXISTS app.ai_request_performance_trace (
                  id uuid PRIMARY KEY,
                  client_request_id text,
                  endpoint text NOT NULL,
                  question text,
                  status text NOT NULL,
                  error_message text,
                  request_mode text,
                  reasoning_mode boolean NOT NULL DEFAULT false,
                  sql_model text,
                  sql_reasoning_model text,
                  sql_fallback_model text,
                  narrator_model text,
                  model_used text,
                  fallback_used boolean NOT NULL DEFAULT false,
                  generated_sql text,
                  retrieved_tables jsonb NOT NULL DEFAULT '[]'::jsonb,
                  retrieved_table_count integer NOT NULL DEFAULT 0,
                  frontend_started_at timestamptz,
                  backend_received_at timestamptz NOT NULL,
                  backend_completed_at timestamptz,
                  frontend_completed_at timestamptz,
                  client_total_latency_ms bigint,
                  frontend_network_latency_ms bigint,
                  backend_total_latency_ms bigint,
                  schema_retrieval_latency_ms bigint,
                  sql_model_latency_ms bigint,
                  sql_normalization_latency_ms bigint,
                  sql_execution_latency_ms bigint,
                  narration_latency_ms bigint,
                  intent jsonb NOT NULL DEFAULT '{}'::jsonb,
                  semantic_validation jsonb NOT NULL DEFAULT '{}'::jsonb,
                  result_assessment jsonb NOT NULL DEFAULT '{}'::jsonb,
                  repair_attempted boolean NOT NULL DEFAULT false,
                  repaired_sql text,
                  sql_repair_latency_ms bigint,
                  ollama_load_duration_ms bigint,
                  ollama_prompt_eval_duration_ms bigint,
                  ollama_eval_duration_ms bigint,
                  created_at timestamptz NOT NULL DEFAULT now()
                );
                CREATE INDEX IF NOT EXISTS idx_ai_perf_trace_created_at
                  ON app.ai_request_performance_trace (created_at DESC);
                CREATE INDEX IF NOT EXISTS idx_ai_perf_trace_client_request_id
                  ON app.ai_request_performance_trace (client_request_id);
                """);
        jdbcTemplate.execute(
                """
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS request_mode text;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS reasoning_mode boolean NOT NULL DEFAULT false;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS sql_reasoning_model text;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS intent jsonb NOT NULL DEFAULT '{}'::jsonb;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS semantic_validation jsonb NOT NULL DEFAULT '{}'::jsonb;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS result_assessment jsonb NOT NULL DEFAULT '{}'::jsonb;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS repair_attempted boolean NOT NULL DEFAULT false;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS repaired_sql text;
                ALTER TABLE app.ai_request_performance_trace
                  ADD COLUMN IF NOT EXISTS sql_repair_latency_ms bigint;
                """);
        tableReady = true;
    }

    private String retrievedTablesJson(List<RetrievedTable> retrievedTables) {
        return toJson(retrievedTables == null ? List.of() : retrievedTables);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize performance trace JSON", exception);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

package com.lpn.aibi.llmorchestrator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record PerformanceTrace(
        UUID id,
        String clientRequestId,
        String endpoint,
        String question,
        String status,
        String errorMessage,
        String requestMode,
        boolean reasoningMode,
        String sqlModel,
        String sqlReasoningModel,
        String sqlFallbackModel,
        String narratorModel,
        String modelUsed,
        boolean fallbackUsed,
        String generatedSql,
        List<RetrievedTable> retrievedTables,
        Instant frontendStartedAt,
        Instant backendReceivedAt,
        Instant backendCompletedAt,
        Long backendTotalLatencyMs,
        Long schemaRetrievalLatencyMs,
        Long sqlModelLatencyMs,
        Long sqlNormalizationLatencyMs,
        Long sqlExecutionLatencyMs,
        Long narrationLatencyMs,
        QuestionIntent intent,
        SemanticSqlValidation semanticValidation,
        SemanticSqlValidator.ResultAssessment resultAssessment,
        boolean repairAttempted,
        String repairedSql,
        Long sqlRepairLatencyMs) {
}

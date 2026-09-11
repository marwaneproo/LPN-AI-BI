package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

record SqlExecutionResult(
        List<Map<String, Object>> rows,
        @JsonProperty("row_count") int rowCount,
        @JsonProperty("executed_sql") String executedSql,
        @JsonProperty("latency_ms") long latencyMs,
        SqlValidationResult validation) {
}

package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiEnvelope<T>(
        Meta meta,
        T data) {

    public record Meta(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("generated_at") Instant generatedAt,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("applied_filters") Map<String, Object> appliedFilters,
            Pagination pagination) {
    }

    public record Pagination(
            Integer limit,
            Integer offset,
            Integer returned,
            @JsonProperty("has_more") Boolean hasMore) {
    }
}

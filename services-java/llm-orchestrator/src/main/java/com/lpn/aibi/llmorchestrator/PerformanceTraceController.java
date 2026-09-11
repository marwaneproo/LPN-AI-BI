package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PerformanceTraceController {

    private final PerformanceTraceRepository performanceTraceRepository;

    PerformanceTraceController(PerformanceTraceRepository performanceTraceRepository) {
        this.performanceTraceRepository = performanceTraceRepository;
    }

    @PatchMapping("/v1/performance-traces/{traceId}/client-timing")
    ResponseEntity<Void> updateClientTiming(
            @PathVariable UUID traceId,
            @RequestBody ClientTimingRequest request) {
        performanceTraceRepository.updateClientTiming(
                traceId,
                request.frontendCompletedAt() == null ? Instant.now() : request.frontendCompletedAt(),
                Math.max(1, request.clientTotalLatencyMs()));
        return ResponseEntity.noContent().build();
    }

    record ClientTimingRequest(
            @JsonProperty("frontend_completed_at") Instant frontendCompletedAt,
            @JsonProperty("client_total_latency_ms") long clientTotalLatencyMs) {
    }
}

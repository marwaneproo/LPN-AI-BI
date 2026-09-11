package com.lpn.aibi.llmorchestrator.bi.api;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Meta;
import com.lpn.aibi.llmorchestrator.bi.application.BiPageResult;

final class BiEndpointSupport {

    private BiEndpointSupport() {
    }

    static <T> BiEnvelope<T> timed(Supplier<BiPageResult<T>> supplier) {
        long startedAt = System.nanoTime();
        BiPageResult<T> result = supplier.get();
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return new BiEnvelope<>(
                new Meta(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        latencyMs,
                        result.appliedFilters(),
                        result.pagination()),
                result.data());
    }
}

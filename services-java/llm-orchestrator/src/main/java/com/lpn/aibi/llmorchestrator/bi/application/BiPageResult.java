package com.lpn.aibi.llmorchestrator.bi.application;

import java.util.Map;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;

public record BiPageResult<T>(
        T data,
        Map<String, Object> appliedFilters,
        Pagination pagination) {
}

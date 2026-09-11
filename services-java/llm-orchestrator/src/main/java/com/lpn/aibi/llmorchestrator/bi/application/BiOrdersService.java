package com.lpn.aibi.llmorchestrator.bi.application;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiOrdersMartRepository;

@Service
public class BiOrdersService {

    private final BiOrdersMartRepository repository;

    public BiOrdersService(BiOrdersMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiOrdersResponse> getOrders(BiQuery query) {
        BiOrdersResponse data = new BiOrdersResponse(
                repository.readKpis(query),
                repository.readByType(query),
                repository.readCommercialFlow(query),
                repository.readStatusBreakdown(query),
                repository.readFunnel(query),
                repository.readYearToDateComparison());
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.byType().size(), false));
    }
}

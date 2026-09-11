package com.lpn.aibi.llmorchestrator.bi.application;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiCommercialMartRepository;

@Service
public class BiCommercialService {

    private final BiCommercialMartRepository repository;

    public BiCommercialService(BiCommercialMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiCommercialResponse> getCommercial(BiQuery query) {
        BiCommercialResponse data = new BiCommercialResponse(
                repository.readKpis(query),
                repository.readRevenueByCommercial(query),
                repository.readConversion(query),
                repository.readTerrain(query));
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.revenueByCommercial().size(), false));
    }
}

package com.lpn.aibi.llmorchestrator.bi.application;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiClientsMartRepository;

@Service
public class BiClientsService {

    private final BiClientsMartRepository repository;

    public BiClientsService(BiClientsMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiClientsResponse> getClients(BiQuery query) {
        BiClientsResponse data = new BiClientsResponse(
                repository.readKpis(query),
                repository.readConcentration(query),
                repository.readTopClients(query),
                repository.readByCommercial(query),
                repository.readByArticle(query),
                repository.readByRegion(query),
                repository.readFinanceRisks(query),
                repository.readYearToDateComparison());
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.topClients().size(), false));
    }
}

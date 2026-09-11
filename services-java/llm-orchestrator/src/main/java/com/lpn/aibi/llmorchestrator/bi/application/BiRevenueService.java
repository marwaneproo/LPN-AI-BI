package com.lpn.aibi.llmorchestrator.bi.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiRevenueMartRepository;

@Service
public class BiRevenueService {

    private final BiRevenueMartRepository repository;

    // The 2024 HISTORICAL_SEED (full-year totals lifted from the Compiere source
    // exports) was removed: 2024 is now fully loaded in mart.mart_sales_daily
    // (verified: the mart's 2024 totals match the export figures exactly, so the
    // seed had already stopped injecting), and readYearly() switched to
    // comparable-span sums (Jan 1 → day-of-year of the newest data) that a
    // full-year seed could not honour.

    public BiRevenueService(BiRevenueMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiRevenueResponse> getRevenue(BiQuery query) {
        // compare=true adds the same window one year back, labelled with the current
        // window's periods so the frontend overlays it by X value (see readCompareTrend).
        BiRevenueResponse data = new BiRevenueResponse(
                repository.readKpis(query),
                repository.readTrend(query),
                query.compare() ? repository.readCompareTrend(query) : List.of(),
                repository.readYearly(query),
                repository.readYearToDateComparison(),
                repository.readSignals(query),
                repository.readStatus(query),
                repository.readUnpaidExposure(query));
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.trend().size(), false));
    }
}

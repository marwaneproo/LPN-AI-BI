package com.lpn.aibi.llmorchestrator.bi.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.OverviewData;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.QuickSignals;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiOverviewMartRepository;

@Service
public class BiOverviewService {

    private static final int TOP_LIMIT = 8;
    private final BiOverviewMartRepository repository;

    public BiOverviewService(BiOverviewMartRepository repository) {
        this.repository = repository;
    }

    public OverviewResult getOverview(String from, String to, String granularity, boolean compare) {
        DateWindow window = resolveWindow(from, to);
        String resolvedGranularity = StringUtils.hasText(granularity) ? granularity : "month";

        // compare=true adds the same window one year back, labelled with the current
        // window's periods so the frontend overlays it by X value (see readCompareTrend).
        OverviewData data = new OverviewData(
                repository.readKpis(window.fromInclusive(), window.toExclusive()),
                repository.readTrend(window.fromInclusive(), window.toExclusive(), resolvedGranularity),
                compare
                        ? repository.readCompareTrend(window.fromInclusive(), window.toExclusive(), resolvedGranularity)
                        : List.of(),
                new QuickSignals(
                        repository.readTopCommercial(window.fromInclusive(), window.toExclusive()),
                        repository.readTopSupplier(window.fromInclusive(), window.toExclusive()),
                        repository.readTopRegion(window.fromInclusive(), window.toExclusive())),
                repository.readSalesMix(window.fromInclusive(), window.toExclusive()),
                repository.readTopCustomers(window.fromInclusive(), window.toExclusive(), TOP_LIMIT),
                repository.readTopProducts(window.fromInclusive(), window.toExclusive(), TOP_LIMIT),
                repository.readOrderStatuses(window.fromInclusive(), window.toExclusive()),
                repository.readInvoiceStatuses(window.fromInclusive(), window.toExclusive()),
                repository.readYearToDateComparison());

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", window.fromInclusive().toString());
        filters.put("to", window.toInclusive().toString());
        filters.put("granularity", resolvedGranularity);
        filters.put("compare", compare);

        return new OverviewResult(data, filters);
    }

    private static DateWindow resolveWindow(String from, String to) {
        if (StringUtils.hasText(from) && StringUtils.hasText(to)) {
            LocalDate fromInclusive = LocalDate.parse(from);
            LocalDate toInclusive = LocalDate.parse(to);
            return new DateWindow(fromInclusive, toInclusive.plusDays(1));
        }

        YearMonth previousMonth = YearMonth.from(LocalDate.now()).minusMonths(1);
        return new DateWindow(previousMonth.atDay(1), previousMonth.plusMonths(1).atDay(1));
    }

    public record OverviewResult(OverviewData data, Map<String, Object> appliedFilters) {
    }

    private record DateWindow(LocalDate fromInclusive, LocalDate toExclusive) {
        LocalDate toInclusive() {
            return toExclusive.minusDays(1);
        }
    }
}

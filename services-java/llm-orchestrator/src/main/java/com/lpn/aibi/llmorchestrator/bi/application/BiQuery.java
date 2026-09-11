package com.lpn.aibi.llmorchestrator.bi.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

public record BiQuery(
        LocalDate fromInclusive,
        LocalDate toExclusive,
        String granularity,
        boolean compare,
        Integer commercial,
        Integer category,
        Integer supplier,
        Integer customer,
        Integer documentType,
        String region,
        String city,
        String paymentStatus,
        int limit,
        int offset) {

    public static BiQuery of(
            String from,
            String to,
            String granularity,
            boolean compare,
            Integer commercial,
            Integer category,
            Integer supplier,
            Integer customer,
            Integer documentType,
            String region,
            String city,
            String paymentStatus,
            Integer limit,
            Integer offset) {
        LocalDate fromInclusive;
        LocalDate toExclusive;
        if (StringUtils.hasText(from) && StringUtils.hasText(to)) {
            fromInclusive = LocalDate.parse(from);
            toExclusive = LocalDate.parse(to).plusDays(1);
        } else {
            YearMonth previousMonth = YearMonth.from(LocalDate.now()).minusMonths(1);
            fromInclusive = previousMonth.atDay(1);
            toExclusive = previousMonth.plusMonths(1).atDay(1);
        }

        return new BiQuery(
                fromInclusive,
                toExclusive,
                normalizeGranularity(granularity),
                compare,
                commercial,
                category,
                supplier,
                customer,
                documentType,
                blankToNull(region),
                blankToNull(city),
                blankToNull(paymentStatus),
                clamp(limit == null ? 10 : limit, 1, 100),
                Math.max(offset == null ? 0 : offset, 0));
    }

    public LocalDate toInclusive() {
        return toExclusive.minusDays(1);
    }

    public Map<String, Object> appliedFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", fromInclusive.toString());
        filters.put("to", toInclusive().toString());
        filters.put("granularity", granularity);
        filters.put("compare", compare);
        putIfPresent(filters, "commercial", commercial);
        putIfPresent(filters, "category", category);
        putIfPresent(filters, "supplier", supplier);
        putIfPresent(filters, "customer", customer);
        putIfPresent(filters, "document_type", documentType);
        putIfPresent(filters, "region", region);
        putIfPresent(filters, "city", city);
        putIfPresent(filters, "payment_status", paymentStatus);
        filters.put("limit", limit);
        filters.put("offset", offset);
        return filters;
    }

    private static String normalizeGranularity(String granularity) {
        if (!StringUtils.hasText(granularity)) {
            return "month";
        }
        return switch (granularity) {
            case "day", "week", "month" -> granularity;
            default -> "month";
        };
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static void putIfPresent(Map<String, Object> filters, String key, Object value) {
        if (value != null) {
            filters.put(key, value);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

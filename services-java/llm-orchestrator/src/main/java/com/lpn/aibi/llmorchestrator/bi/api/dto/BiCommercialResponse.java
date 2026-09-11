package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiCommercialResponse(
        CommercialKpis kpis,
        @JsonProperty("revenue_by_commercial") List<CommercialSalesRank> revenueByCommercial,
        List<CommercialConversion> conversion,
        CommercialTerrainSignals terrain) {

    public record CommercialKpis(
            @JsonProperty("commercial_count") long commercialCount,
            @JsonProperty("top_commercial_name") String topCommercialName,
            @JsonProperty("conversion_percent") BigDecimal conversionPercent,
            @JsonProperty("region_coverage_count") long regionCoverageCount) {
    }

    public record CommercialSalesRank(
            @JsonProperty("salesrep_id") long salesrepId,
            @JsonProperty("commercial_label") String commercialLabel,
            @JsonProperty("salesrep_email") String salesrepEmail,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    public record CommercialConversion(
            @JsonProperty("commercial_label") String commercialLabel,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            BigDecimal gap) {
    }

    public record CommercialTerrainSignals(
            @JsonProperty("zone_forte") ZoneSignal zoneForte,
            @JsonProperty("client_cle") CustomerSignal clientCle,
            @JsonProperty("drill_keys") DrillKeys drillKeys) {
    }

    public record ZoneSignal(
            @JsonProperty("city_name") String cityName,
            @JsonProperty("region_name") String regionName,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record CustomerSignal(
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record DrillKeys(
            @JsonProperty("commercial_key") long commercialKey,
            @JsonProperty("customer_key") long customerKey,
            @JsonProperty("geography_key") long geographyKey) {
    }
}

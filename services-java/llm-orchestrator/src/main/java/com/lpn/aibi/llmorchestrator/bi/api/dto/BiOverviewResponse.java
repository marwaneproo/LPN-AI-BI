package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiOverviewResponse(
        Meta meta,
        OverviewData data) {

    public record Meta(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("generated_at") Instant generatedAt,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("applied_filters") Map<String, Object> appliedFilters,
            Pagination pagination) {
    }

    public record Pagination(
            Integer limit,
            Integer offset,
            Integer returned,
            @JsonProperty("has_more") Boolean hasMore) {
    }

    public record OverviewData(
            OverviewKpis kpis,
            List<PeriodSalesPoint> trend,
            /**
             * The same window one year back, re-labelled with the current window's
             * periods so it overlays on {@code trend} by X value. Empty unless
             * {@code compare=true}.
             */
            @JsonProperty("compare_trend") List<PeriodSalesPoint> compareTrend,
            @JsonProperty("quick_signals") QuickSignals quickSignals,
            @JsonProperty("sales_mix") SalesMix salesMix,
            @JsonProperty("top_customers") List<CustomerRank> topCustomers,
            @JsonProperty("top_products") List<ProductRank> topProducts,
            @JsonProperty("order_statuses") List<StatusBreakdown> orderStatuses,
            @JsonProperty("invoice_statuses") List<StatusBreakdown> invoiceStatuses,
            List<YearlyPoint> yearly) {
    }

    /** Per-year totals for the overview year-over-year KPI comparison cards. */
    public record YearlyPoint(
            int year,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("order_value") BigDecimal orderValue,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("active_customers") long activeCustomers,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    public record OverviewKpis(
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("order_value") BigDecimal orderValue,
            @JsonProperty("active_customers") long activeCustomers,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("paid_invoice_count") long paidInvoiceCount,
            @JsonProperty("unpaid_invoice_count") long unpaidInvoiceCount,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    public record PeriodSalesPoint(
            String period,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("order_value") BigDecimal orderValue,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record QuickSignals(
            @JsonProperty("top_commercial") NamedMetric topCommercial,
            @JsonProperty("top_supplier") NamedMetric topSupplier,
            @JsonProperty("top_region") NamedMetric topRegion) {
    }

    public record NamedMetric(
            String name,
            BigDecimal value) {
    }

    public record SalesMix(
            @JsonProperty("order_value") BigDecimal orderValue,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("tracked_products") long trackedProducts) {
    }

    public record CustomerRank(
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("total_order_value") BigDecimal totalOrderValue,
            @JsonProperty("average_order_value") BigDecimal averageOrderValue) {
    }

    public record ProductRank(
            @JsonProperty("product_name") String productName,
            @JsonProperty("line_count") long lineCount,
            @JsonProperty("total_quantity") BigDecimal totalQuantity,
            @JsonProperty("total_order_value") BigDecimal totalOrderValue) {
    }

    public record StatusBreakdown(
            String status,
            String label,
            @JsonProperty("item_count") long itemCount,
            @JsonProperty("total_value") BigDecimal totalValue) {
    }
}

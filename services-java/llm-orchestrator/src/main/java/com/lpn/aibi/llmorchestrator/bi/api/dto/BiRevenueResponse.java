package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiRevenueResponse(
        RevenueKpis kpis,
        List<RevenueTrendPoint> trend,
        /**
         * The same window one year back, re-labelled with the current window's
         * periods so it overlays on {@code trend} by X value. Empty unless
         * {@code compare=true}.
         */
        @JsonProperty("compare_trend") List<RevenueTrendPoint> compareTrend,
        List<YearlyPoint> yearly,
        List<YearlyPoint> comparison,
        @JsonProperty("responsible_reading") RevenueSignals responsibleReading,
        @JsonProperty("revenue_status") RevenueStatus revenueStatus,
        UnpaidExposure unpaid) {

    /** One bar group per calendar year: invoiced vs ordered CA and the gap. */
    public record YearlyPoint(
            int year,
            @JsonProperty("ca_facture") BigDecimal caFacture,
            @JsonProperty("ca_commande") BigDecimal caCommande,
            BigDecimal ecart,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("invoice_count") long invoiceCount,
            boolean partial) {
    }

    public record RevenueKpis(
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoice_gap_amount") BigDecimal invoiceGapAmount,
            @JsonProperty("average_order_value") BigDecimal averageOrderValue) {
    }

    public record RevenueTrendPoint(
            String period,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("invoice_count") long invoiceCount) {
    }

    public record RevenueSignals(
            @JsonProperty("best_month") BestMonth bestMonth,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent,
            @JsonProperty("unpaid_invoice_count") long unpaidInvoiceCount,
            @JsonProperty("unpaid_invoice_amount") BigDecimal unpaidInvoiceAmount) {
    }

    public record BestMonth(
            String period,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record RevenueStatus(
            BigDecimal facture,
            @JsonProperty("a_livrer") BigDecimal aLivrer,
            BigDecimal ecart) {
    }

    /** Unpaid-invoice exposure over the selected period, from {@code mart.mart_payment_status}. */
    public record UnpaidExposure(
            @JsonProperty("unpaid_amount") BigDecimal unpaidAmount,
            @JsonProperty("unpaid_rate_percent") BigDecimal unpaidRatePercent,
            @JsonProperty("top_customers") List<UnpaidCustomer> topCustomers,
            @JsonProperty("top_commercials") List<UnpaidCommercial> topCommercials) {
    }

    public record UnpaidCustomer(
            @JsonProperty("customer_name") String customerName,
            BigDecimal amount,
            @JsonProperty("unpaid_invoice_count") long unpaidInvoiceCount) {
    }

    public record UnpaidCommercial(
            @JsonProperty("commercial_name") String commercialName,
            BigDecimal amount,
            @JsonProperty("unpaid_invoice_count") long unpaidInvoiceCount,
            /** commercial_key of the mart — what the frontend sends back as the `commercial` click-filter param. */
            @JsonProperty("salesrep_id") long salesrepId) {
    }
}

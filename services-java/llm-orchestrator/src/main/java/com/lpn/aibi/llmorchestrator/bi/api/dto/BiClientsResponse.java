package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiClientsResponse(
        ClientKpis kpis,
        ClientConcentration concentration,
        @JsonProperty("top_clients") List<ClientRank> topClients,
        @JsonProperty("by_commercial") List<CustomerCommercialRank> byCommercial,
        @JsonProperty("by_article") List<CustomerArticleRank> byArticle,
        @JsonProperty("by_region") List<GeographyRank> byRegion,
        @JsonProperty("finance_risks") List<CustomerPaymentRisk> financeRisks,
        List<YearlyPoint> yearly) {

    /** Per-year totals for the clients year-over-year comparison cards. */
    public record YearlyPoint(
            int year,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("active_customers") long activeCustomers,
            @JsonProperty("active_commercials") long activeCommercials,
            @JsonProperty("active_regions") long activeRegions) {
    }

    public record ClientKpis(
            @JsonProperty("active_customers") long activeCustomers,
            @JsonProperty("top_customer_name") String topCustomerName,
            @JsonProperty("portfolio_commercial_count") long portfolioCommercialCount,
            @JsonProperty("geography_count") long geographyCount) {
    }

    public record ClientRank(
            @JsonProperty("customer_id") long customerId,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("average_invoice_value") BigDecimal averageInvoiceValue,
            @JsonProperty("cumulative_share_percent") BigDecimal cumulativeSharePercent) {
    }

    /** Revenue-concentration (Pareto) summary: how much of the CA the 10 largest customers hold. */
    public record ClientConcentration(
            @JsonProperty("top10_share_percent") BigDecimal top10SharePercent,
            @JsonProperty("total_customers") long totalCustomers) {
    }

    public record CustomerCommercialRank(
            @JsonProperty("salesrep_id") long salesrepId,
            @JsonProperty("commercial_label") String commercialLabel,
            @JsonProperty("customer_count") long customerCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record CustomerArticleRank(
            @JsonProperty("customer_id") long customerId,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("product_name") String productName,
            BigDecimal quantity,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record GeographyRank(
            @JsonProperty("region_name") String regionName,
            @JsonProperty("city_name") String cityName,
            @JsonProperty("customer_count") long customerCount,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record CustomerPaymentRisk(
            @JsonProperty("customer_id") long customerId,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("unpaid_invoice_count") long unpaidInvoiceCount,
            @JsonProperty("unpaid_invoice_amount") BigDecimal unpaidInvoiceAmount) {
    }
}

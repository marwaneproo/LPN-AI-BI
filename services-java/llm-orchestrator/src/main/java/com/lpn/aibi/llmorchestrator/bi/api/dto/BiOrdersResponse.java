package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiOrdersResponse(
        OrderKpis kpis,
        @JsonProperty("by_type") List<OrderTypeRank> byType,
        @JsonProperty("commercial_flow") List<CommercialOrderFlow> commercialFlow,
        @JsonProperty("status_breakdown") List<StatusBreakdown> statusBreakdown,
        OrderFunnel funnel,
        List<YearlyPoint> yearly) {

    /**
     * Ordered → delivered → invoiced conversion funnel for the selected period and
     * filters. Quantities are monotonically non-increasing; the coverage percents
     * are each measured against the ordered quantity.
     */
    public record OrderFunnel(
            @JsonProperty("quantity_ordered") BigDecimal quantityOrdered,
            @JsonProperty("quantity_delivered") BigDecimal quantityDelivered,
            @JsonProperty("quantity_invoiced") BigDecimal quantityInvoiced,
            @JsonProperty("delivery_coverage_percent") BigDecimal deliveryCoveragePercent,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    /** Per-year totals for the orders year-over-year comparison cards. */
    public record YearlyPoint(
            int year,
            @JsonProperty("order_value") BigDecimal orderValue,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent,
            @JsonProperty("delivery_coverage_percent") BigDecimal deliveryCoveragePercent) {
    }

    public record OrderKpis(
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("dominant_type_name") String dominantTypeName,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent,
            @JsonProperty("amount_invoice_coverage_percent") BigDecimal amountInvoiceCoveragePercent,
            @JsonProperty("status_count") long statusCount) {
    }

    public record OrderTypeRank(
            @JsonProperty("order_type_id") long orderTypeId,
            @JsonProperty("order_type_name") String orderTypeName,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales) {
    }

    public record CommercialOrderFlow(
            @JsonProperty("salesrep_id") long salesrepId,
            @JsonProperty("commercial_label") String commercialLabel,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("delivered_coverage_percent") BigDecimal deliveredCoveragePercent,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    public record StatusBreakdown(
            String status,
            String label,
            @JsonProperty("item_count") long itemCount,
            @JsonProperty("total_value") BigDecimal totalValue) {
    }
}

package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiAnalysisResponse(
        SalesAnalysisKpis kpis,
        List<TrendPoint> trend,
        @JsonProperty("order_type_sales") List<OrderTypeSales> orderTypeSales,
        @JsonProperty("commercial_sales") List<CommercialSales> commercialSales,
        @JsonProperty("category_sales") List<CategorySales> categorySales,
        @JsonProperty("theme_sales") List<ThemeSales> themeSales,
        @JsonProperty("supplier_sales") List<SupplierSales> supplierSales,
        @JsonProperty("distributor_sales") List<SupplierSales> distributorSales,
        @JsonProperty("geography_sales") List<GeographySales> geographySales,
        @JsonProperty("availability_risks") List<AvailabilityRisk> availabilityRisks,
        @JsonProperty("top_products") List<ProductSales> topProducts,
        @JsonProperty("top_customers") List<CustomerSales> topCustomers,
        @JsonProperty("commercial_options") List<FilterOption> commercialOptions,
        @JsonProperty("order_type_options") List<FilterOption> orderTypeOptions,
        @JsonProperty("category_options") List<FilterOption> categoryOptions,
        @JsonProperty("theme_options") List<FilterOption> themeOptions,
        @JsonProperty("supplier_options") List<FilterOption> supplierOptions,
        @JsonProperty("distributor_options") List<FilterOption> distributorOptions) {

    public record SalesAnalysisKpis(
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("active_customers") long activeCustomers,
            @JsonProperty("average_order_value") BigDecimal averageOrderValue,
            @JsonProperty("invoice_coverage_percent") BigDecimal invoiceCoveragePercent) {
    }

    public record TrendPoint(String period, @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record OrderTypeSales(@JsonProperty("order_type_id") long orderTypeId,
            @JsonProperty("order_type_name") String orderTypeName,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales) {
    }

    public record CommercialSales(@JsonProperty("salesrep_id") long salesrepId,
            @JsonProperty("commercial_label") String commercialLabel,
            @JsonProperty("salesrep_email") String salesrepEmail,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("ordered_sales") BigDecimal orderedSales,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record CategorySales(@JsonProperty("category_id") long categoryId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("line_count") long lineCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record ThemeSales(@JsonProperty("theme_name") String themeName,
            @JsonProperty("product_count") long productCount,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("line_count") long lineCount,
            BigDecimal quantity,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record SupplierSales(@JsonProperty("supplier_id") long supplierId,
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("product_count") long productCount,
            @JsonProperty("line_count") long lineCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record GeographySales(@JsonProperty("region_name") String regionName,
            @JsonProperty("city_name") String cityName,
            @JsonProperty("customer_count") long customerCount,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record AvailabilityRisk(@JsonProperty("product_id") long productId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("theme_name") String themeName,
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("qty_on_hand") BigDecimal qtyOnHand,
            @JsonProperty("qty_reserved") BigDecimal qtyReserved,
            @JsonProperty("qty_available") BigDecimal qtyAvailable,
            @JsonProperty("qty_ordered") BigDecimal qtyOrdered) {
    }

    public record ProductSales(@JsonProperty("product_name") String productName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("supplier_id") long supplierId,
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("line_count") long lineCount,
            BigDecimal quantity,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales) {
    }

    public record CustomerSales(@JsonProperty("customer_name") String customerName,
            @JsonProperty("invoice_count") long invoiceCount,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("average_invoice_value") BigDecimal averageInvoiceValue) {
    }

    public record FilterOption(
            String id,
            String label,
            @JsonProperty("item_count") long itemCount,
            @JsonProperty("total_value") BigDecimal totalValue) {
    }
}

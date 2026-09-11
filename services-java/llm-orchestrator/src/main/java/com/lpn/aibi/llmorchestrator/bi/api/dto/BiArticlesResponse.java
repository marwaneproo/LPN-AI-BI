package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BiArticlesResponse(
        ArticleKpis kpis,
        ArticleConcentration concentration,
        @JsonProperty("top_articles") List<ArticleRank> topArticles,
        @JsonProperty("mix_article") ArticleMix mixArticle,
        @JsonProperty("stock_priorities") List<StockRiskRank> stockPriorities,
        List<YearlyPoint> yearly) {

    /** Per-year totals for the articles year-over-year comparison cards. */
    public record YearlyPoint(
            int year,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("active_products") long activeProducts,
            BigDecimal quantity,
            @JsonProperty("active_categories") long activeCategories,
            @JsonProperty("active_themes") long activeThemes) {
    }

    public record ArticleKpis(
            @JsonProperty("active_products") long activeProducts,
            @JsonProperty("top_category_name") String topCategoryName,
            @JsonProperty("top_theme_name") String topThemeName,
            @JsonProperty("stock_risk_count") long stockRiskCount) {
    }

    public record ArticleRank(
            @JsonProperty("product_id") long productId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("supplier_id") long supplierId,
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("line_count") long lineCount,
            BigDecimal quantity,
            @JsonProperty("invoiced_sales") BigDecimal invoicedSales,
            @JsonProperty("cumulative_share_percent") BigDecimal cumulativeSharePercent) {
    }

    /** Revenue-concentration (Pareto) summary: how much of the CA the 10 largest products hold. */
    public record ArticleConcentration(
            @JsonProperty("top10_share_percent") BigDecimal top10SharePercent,
            @JsonProperty("total_products") long totalProducts) {
    }

    public record ArticleMix(
            List<NamedValue> categories,
            List<NamedValue> themes,
            List<NamedValue> collections) {
    }

    public record NamedValue(
            String name,
            BigDecimal value) {
    }

    public record StockRiskRank(
            @JsonProperty("product_id") long productId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("theme_name") String themeName,
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("qty_on_hand") BigDecimal qtyOnHand,
            @JsonProperty("qty_reserved") BigDecimal qtyReserved,
            @JsonProperty("qty_available") BigDecimal qtyAvailable,
            @JsonProperty("qty_ordered") BigDecimal qtyOrdered,
            @JsonProperty("is_stockout_risk") boolean stockoutRisk,
            @JsonProperty("stock_risk_level") String stockRiskLevel) {
    }
}

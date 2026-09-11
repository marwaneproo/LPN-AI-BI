package com.lpn.aibi.llmorchestrator.bi.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Achats (purchasing) BI response.
 *
 * <p><b>Data provenance — read before changing any query behind this DTO.</b>
 * The restored warehouse (lpn_ai_bi-team2.dump) contains no purchase-order or
 * purchase-invoice transactions: {@code business.c_doctype} only defines sales
 * document types (SOO/ARI/ARC) and both {@code business.c_order} and
 * {@code business.c_invoice} are 100% sales documents. There is therefore no
 * real "commande d'achat" / "facture fournisseur" fact table to report on.
 *
 * <p>What genuinely exists and is used here instead:
 * <ul>
 *   <li>{@code mart.mart_sales_by_product} already carries, for every sold
 *       product line, the {@code supplier_key}/{@code supplier_name} of the
 *       product's primary declared supplier (business.v_product_primary_supplier).
 *       Aggregating {@code ca_facture} by that supplier gives a real (not invented)
 *       "revenue attributable to each origin supplier's products" reading — a
 *       purchasing-adjacent proxy, not an actual spend/PO figure.</li>
 *   <li>{@code business.m_product_po} is a real, largely populated vendor/product
 *       price catalog ({@code pricelist}, {@code pricepo}, {@code pricelastpo},
 *       {@code pricelastinv}). It is NOT transactional (no order dates/quantities).</li>
 * </ul>
 *
 * <p>Explicitly NOT reported: OTIF / delivery-reliability score and cost-savings,
 * because the only candidate source columns ({@code deliverytime_promised},
 * {@code deliverytime_actual}, {@code qualityrating} in {@code m_product_po}) are
 * populated with a constant placeholder ("0.0") across every row — there is no
 * real signal there, and surfacing a KPI from it would be inventing data.
 */
public record BiAchatsResponse(
        AchatsKpis kpis,
        List<AchatsTrendPoint> trend,
        @JsonProperty("top_suppliers") List<SupplierRank> topSuppliers,
        @JsonProperty("top_supplier_products") List<SupplierProductRank> topSupplierProducts,
        @JsonProperty("by_category") List<CategoryBreakdown> byCategory,
        CatalogSnapshot catalog,
        @JsonProperty("methodology_note") String methodologyNote) {

    public record AchatsKpis(
            @JsonProperty("supplier_count") long supplierCount,
            @JsonProperty("product_count") long productCount,
            @JsonProperty("total_attributed_sales") BigDecimal totalAttributedSales,
            @JsonProperty("average_sales_per_supplier") BigDecimal averageSalesPerSupplier) {
    }

    public record AchatsTrendPoint(
            String period,
            @JsonProperty("supplier_count") long supplierCount,
            @JsonProperty("total_attributed_sales") BigDecimal totalAttributedSales) {
    }

    public record SupplierRank(
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("product_count") long productCount,
            @JsonProperty("total_attributed_sales") BigDecimal totalAttributedSales,
            @JsonProperty("total_quantity") BigDecimal totalQuantity) {
    }

    public record SupplierProductRank(
            @JsonProperty("supplier_name") String supplierName,
            @JsonProperty("product_name") String productName,
            @JsonProperty("total_attributed_sales") BigDecimal totalAttributedSales,
            @JsonProperty("total_quantity") BigDecimal totalQuantity) {
    }

    public record CategoryBreakdown(
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("total_attributed_sales") BigDecimal totalAttributedSales,
            @JsonProperty("supplier_count") long supplierCount,
            @JsonProperty("total_quantity") BigDecimal totalQuantity) {
    }

    /** Not date-scoped: this is a snapshot of the vendor/product catalog as it stands today. */
    public record CatalogSnapshot(
            @JsonProperty("catalog_entries") long catalogEntries,
            @JsonProperty("vendor_count") long vendorCount,
            @JsonProperty("product_count") long productCount,
            @JsonProperty("avg_catalog_price") BigDecimal avgCatalogPrice,
            @JsonProperty("avg_last_purchase_price") BigDecimal avgLastPurchasePrice,
            @JsonProperty("avg_last_invoiced_price") BigDecimal avgLastInvoicedPrice) {
    }
}

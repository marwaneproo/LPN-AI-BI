package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.AvailabilityRisk;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.CategorySales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.CommercialSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.CustomerSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.FilterOption;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.GeographySales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.OrderTypeSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.ProductSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.SalesAnalysisKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.SupplierSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.ThemeSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.TrendPoint;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiAnalysisMartRepository extends MartRepositorySupport {

    public BiAnalysisMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public SalesAnalysisKpis readKpis(BiQuery query) {
        // KPI order_count: total validated order documents counted in mart_sales_daily for the selected period.
        // KPI ordered_sales: total ordered revenue (ca_commande) in MAD for the selected period.
        // KPI invoice_count: total invoice documents counted in mart_sales_daily for the selected period.
        // KPI invoiced_sales: total invoiced revenue (ca_facture) in MAD for the selected period.
        // KPI active_customers: total active invoiced-customer contribution stored in mart_sales_daily for the selected period.
        // KPI average_order_value: ordered revenue divided by order count for the selected period.
        // KPI invoice_coverage_percent: invoiced revenue divided by ordered revenue, expressed as a percentage.
        String sql = """
                SELECT
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    COALESCE(SUM(active_customers_invoiced), 0) AS active_customers,
                    CASE WHEN COALESCE(SUM(nombre_commandes), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_commande), 0) / NULLIF(SUM(nombre_commandes), 0), 2)
                    END AS average_order_value,
                    CASE WHEN COALESCE(SUM(ca_commande), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_facture), 0) * 100 / NULLIF(SUM(ca_commande), 0), 2)
                    END AS invoice_coverage_percent
                FROM mart.mart_sales_daily
                WHERE metric_date >= :from AND metric_date < :to
                """;
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new SalesAnalysisKpis(
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales")),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales")),
                rs.getLong("active_customers"),
                money(rs.getBigDecimal("average_order_value")),
                money(rs.getBigDecimal("invoice_coverage_percent"))));
    }

    public List<TrendPoint> readTrend(BiQuery query) {
        String sql = """
                SELECT
                    CASE
                        WHEN :granularity = 'day' THEN TO_CHAR(metric_date, 'YYYY-MM-DD')
                        WHEN :granularity = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month, 'YYYY-MM')
                    END AS period,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_daily
                WHERE metric_date >= :from AND metric_date < :to
                GROUP BY period
                ORDER BY period
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new TrendPoint(
                rs.getString("period"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales")),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<OrderTypeSales> readOrderTypeSales(BiQuery query) {
        String sql = """
                SELECT c_doctype_id AS order_type_id, document_type_name AS order_type_name,
                       COALESCE(SUM(nombre_commandes), 0) AS order_count,
                       COALESCE(SUM(ca_commande), 0) AS ordered_sales
                FROM mart.mart_order_to_invoice_flow
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:documentType IS NULL OR c_doctype_id = :documentType OR document_type_key = :documentType)
                GROUP BY c_doctype_id, document_type_name
                ORDER BY ordered_sales DESC, order_type_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new OrderTypeSales(
                rs.getLong("order_type_id"),
                rs.getString("order_type_name"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales"))));
    }

    public List<CommercialSales> readCommercialSales(BiQuery query) {
        String sql = """
                SELECT ad_user_id AS salesrep_id, commercial_name AS commercial_label, salesrep_email,
                       COALESCE(SUM(nombre_commandes), 0) AS order_count,
                       COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                       COALESCE(SUM(invoice_count), 0) AS invoice_count,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_commercial
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:commercial IS NULL OR commercial_key = :commercial OR ad_user_id = :commercial)
                GROUP BY ad_user_id, commercial_name, salesrep_email
                ORDER BY invoiced_sales DESC, commercial_label
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CommercialSales(
                rs.getLong("salesrep_id"),
                rs.getString("commercial_label"),
                rs.getString("salesrep_email"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales")),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<CategorySales> readCategorySales(BiQuery query) {
        String sql = """
                SELECT product_category_key AS category_id, category_name,
                       COALESCE(SUM(invoice_line_count), 0) AS line_count,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:category IS NULL OR product_category_key = :category OR m_product_category_id = :category)
                  AND (:supplier IS NULL OR supplier_key = :supplier OR supplier_id = :supplier)
                GROUP BY product_category_key, category_name
                ORDER BY invoiced_sales DESC, category_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CategorySales(
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getLong("line_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<ThemeSales> readThemeSales(BiQuery query) {
        String sql = """
                SELECT theme_name,
                       COALESCE(SUM(nombre_produits_vendus), 0) AS product_count,
                       COALESCE(SUM(invoice_count), 0) AS invoice_count,
                       COALESCE(SUM(invoice_line_count), 0) AS line_count,
                       COALESCE(SUM(quantity_invoiced), 0) AS quantity,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:category IS NULL OR product_category_key = :category OR m_product_category_id = :category)
                  AND (:supplier IS NULL OR supplier_key = :supplier OR supplier_id = :supplier)
                GROUP BY theme_name
                ORDER BY invoiced_sales DESC, theme_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new ThemeSales(
                rs.getString("theme_name"),
                rs.getLong("product_count"),
                rs.getLong("invoice_count"),
                rs.getLong("line_count"),
                money(rs.getBigDecimal("quantity")),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<SupplierSales> readSupplierSales(BiQuery query) {
        String sql = """
                SELECT supplier_id, supplier_name,
                       COALESCE(SUM(nombre_produits_vendus), 0) AS product_count,
                       COALESCE(SUM(invoice_line_count), 0) AS line_count,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:supplier IS NULL OR supplier_key = :supplier OR supplier_id = :supplier)
                GROUP BY supplier_id, supplier_name
                ORDER BY invoiced_sales DESC, supplier_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new SupplierSales(
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getLong("product_count"),
                rs.getLong("line_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<GeographySales> readGeographySales(BiQuery query) {
        String sql = """
                SELECT region_name, city_name,
                       COALESCE(SUM(customer_count), 0) AS customer_count,
                       COALESCE(SUM(invoice_count), 0) AS invoice_count,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_region
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:region IS NULL OR region_name = :region)
                  AND (:city IS NULL OR city_name = :city)
                GROUP BY region_name, city_name
                ORDER BY invoiced_sales DESC, region_name, city_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new GeographySales(
                rs.getString("region_name"),
                rs.getString("city_name"),
                rs.getLong("customer_count"),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<AvailabilityRisk> readAvailabilityRisks(BiQuery query) {
        String sql = """
                SELECT m_product_id AS product_id, product_name, category_name, theme_name, supplier_name,
                       qty_on_hand, qty_reserved, qty_available, qty_ordered
                FROM mart.mart_stock_risk
                WHERE (:category IS NULL OR product_category_key = :category)
                  AND (:supplier IS NULL OR supplier_key = :supplier)
                ORDER BY is_stockout_risk DESC, qty_available ASC, product_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new AvailabilityRisk(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getString("theme_name"),
                rs.getString("supplier_name"),
                money(rs.getBigDecimal("qty_on_hand")),
                money(rs.getBigDecimal("qty_reserved")),
                money(rs.getBigDecimal("qty_available")),
                money(rs.getBigDecimal("qty_ordered"))));
    }

    public List<ProductSales> readTopProducts(BiQuery query) {
        String sql = """
                SELECT product_name, category_name, supplier_id, supplier_name,
                       COALESCE(SUM(invoice_line_count), 0) AS line_count,
                       COALESCE(SUM(quantity_invoiced), 0) AS quantity,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :from AND metric_date < :to
                GROUP BY product_name, category_name, supplier_id, supplier_name
                ORDER BY invoiced_sales DESC, product_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new ProductSales(
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getLong("line_count"),
                money(rs.getBigDecimal("quantity")),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<CustomerSales> readTopCustomers(BiQuery query) {
        String sql = """
                SELECT customer_name,
                       COALESCE(SUM(invoice_count), 0) AS invoice_count,
                       COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                       CASE WHEN COALESCE(SUM(invoice_count), 0) = 0 THEN 0
                            ELSE ROUND(COALESCE(SUM(ca_facture), 0) / NULLIF(SUM(invoice_count), 0), 2)
                       END AS average_invoice_value
                FROM mart.mart_sales_by_customer
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:customer IS NULL OR customer_key = :customer OR c_bpartner_id = :customer)
                GROUP BY customer_name
                ORDER BY invoiced_sales DESC, customer_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CustomerSales(
                rs.getString("customer_name"),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("average_invoice_value"))));
    }

    public List<FilterOption> readFilterOptions(BiQuery query, String idColumn, String labelColumn, String valueColumn, String tableName) {
        String sql = """
                SELECT %s AS id, %s AS label,
                       COUNT(*) AS item_count,
                       COALESCE(SUM(%s), 0) AS total_value
                FROM mart.%s
                WHERE metric_date >= :from AND metric_date < :to
                GROUP BY %s, %s
                ORDER BY total_value DESC, label
                LIMIT :limit OFFSET :offset
                """.formatted(idColumn, labelColumn, valueColumn, tableName, idColumn, labelColumn);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new FilterOption(
                rs.getString("id"),
                rs.getString("label"),
                rs.getLong("item_count"),
                money(rs.getBigDecimal("total_value"))));
    }
}

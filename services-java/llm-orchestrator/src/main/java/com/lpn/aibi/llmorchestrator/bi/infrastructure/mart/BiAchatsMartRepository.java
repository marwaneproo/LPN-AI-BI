package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.AchatsKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.AchatsTrendPoint;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.CatalogSnapshot;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.CategoryBreakdown;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.SupplierProductRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse.SupplierRank;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

/**
 * Reads Achats (purchasing) data for the BI page. Deliberately reuses the
 * existing {@code mart.mart_sales_by_product} materialized view (already
 * carries {@code supplier_key}/{@code supplier_name} per sold product line,
 * via {@code business.v_product_primary_supplier}) plus the real, largely
 * populated vendor/product catalog in {@code business.m_product_po}. No mart
 * view, table, or column is created or altered by this class — see
 * {@link com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse} for why.
 */
@Repository
public class BiAchatsMartRepository extends MartRepositorySupport {

    private static final String SUPPLIER_FILTER = """
            metric_date >= :from AND metric_date < :to
            AND supplier_key <> 0
            AND (:supplier IS NULL OR supplier_id = :supplier)
            AND (:category IS NULL OR m_product_category_id = :category)
            """;

    public BiAchatsMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public AchatsKpis readKpis(BiQuery query) {
        // KPI supplier_count: distinct origin suppliers of sold products for the selected period/filters.
        // KPI product_count: distinct products with a known origin supplier for the selected period/filters.
        // KPI total_attributed_sales: ca_facture summed over lines with a known origin supplier (spend proxy).
        // KPI average_sales_per_supplier: total_attributed_sales / supplier_count.
        String sql = """
                SELECT
                    COALESCE(COUNT(DISTINCT NULLIF(supplier_key, 0)), 0) AS supplier_count,
                    COALESCE(COUNT(DISTINCT NULLIF(product_key, 0)), 0) AS product_count,
                    COALESCE(SUM(ca_facture), 0) AS total_attributed_sales
                FROM mart.mart_sales_by_product
                WHERE %s
                """.formatted(SUPPLIER_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> {
            long supplierCount = rs.getLong("supplier_count");
            BigDecimal total = money(rs.getBigDecimal("total_attributed_sales"));
            BigDecimal average = supplierCount == 0
                    ? ZERO
                    : total.divide(BigDecimal.valueOf(supplierCount), 2, java.math.RoundingMode.HALF_UP);
            return new AchatsKpis(supplierCount, rs.getLong("product_count"), total, average);
        });
    }

    public List<AchatsTrendPoint> readTrend(BiQuery query) {
        String sql = """
                SELECT
                    CASE
                        WHEN :granularity = 'day' THEN TO_CHAR(metric_date, 'YYYY-MM-DD')
                        WHEN :granularity = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month, 'YYYY-MM')
                    END AS period,
                    COALESCE(COUNT(DISTINCT NULLIF(supplier_key, 0)), 0) AS supplier_count,
                    COALESCE(SUM(ca_facture), 0) AS total_attributed_sales
                FROM mart.mart_sales_by_product
                WHERE %s
                GROUP BY period
                ORDER BY period
                """.formatted(SUPPLIER_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new AchatsTrendPoint(
                rs.getString("period"),
                rs.getLong("supplier_count"),
                money(rs.getBigDecimal("total_attributed_sales"))));
    }

    public List<SupplierRank> readTopSuppliers(BiQuery query, int limit) {
        String sql = """
                SELECT
                    supplier_name,
                    COALESCE(COUNT(DISTINCT NULLIF(product_key, 0)), 0) AS product_count,
                    COALESCE(SUM(ca_facture), 0) AS total_attributed_sales,
                    COALESCE(SUM(quantity_invoiced), 0) AS total_quantity
                FROM mart.mart_sales_by_product
                WHERE %s
                GROUP BY supplier_name
                ORDER BY total_attributed_sales DESC, supplier_name
                LIMIT :topLimit
                """.formatted(SUPPLIER_FILTER);
        return jdbc.query(sql, params(query).addValue("topLimit", limit), (rs, rowNum) -> new SupplierRank(
                rs.getString("supplier_name"),
                rs.getLong("product_count"),
                money(rs.getBigDecimal("total_attributed_sales")),
                money(rs.getBigDecimal("total_quantity"))));
    }

    public List<SupplierProductRank> readTopSupplierProducts(BiQuery query, int limit) {
        String sql = """
                SELECT
                    supplier_name,
                    product_name,
                    COALESCE(SUM(ca_facture), 0) AS total_attributed_sales,
                    COALESCE(SUM(quantity_invoiced), 0) AS total_quantity
                FROM mart.mart_sales_by_product
                WHERE %s
                GROUP BY supplier_name, product_name
                ORDER BY total_attributed_sales DESC, product_name
                LIMIT :topLimit
                """.formatted(SUPPLIER_FILTER);
        return jdbc.query(sql, params(query).addValue("topLimit", limit), (rs, rowNum) -> new SupplierProductRank(
                rs.getString("supplier_name"),
                rs.getString("product_name"),
                money(rs.getBigDecimal("total_attributed_sales")),
                money(rs.getBigDecimal("total_quantity"))));
    }

    public List<CategoryBreakdown> readByCategory(BiQuery query) {
        String sql = """
                SELECT
                    category_name,
                    COALESCE(SUM(ca_facture), 0) AS total_attributed_sales,
                    COALESCE(COUNT(DISTINCT NULLIF(supplier_key, 0)), 0) AS supplier_count,
                    COALESCE(SUM(quantity_invoiced), 0) AS total_quantity
                FROM mart.mart_sales_by_product
                WHERE %s
                GROUP BY category_name
                ORDER BY total_attributed_sales DESC, category_name
                """.formatted(SUPPLIER_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CategoryBreakdown(
                rs.getString("category_name"),
                money(rs.getBigDecimal("total_attributed_sales")),
                rs.getLong("supplier_count"),
                money(rs.getBigDecimal("total_quantity"))));
    }

    /**
     * Vendor/product catalog snapshot — {@code business.m_product_po} has no
     * date column, so this is a point-in-time read, independent of the page's
     * selected period. Price columns are averaged only over the rows where the
     * price is actually populated (non-zero); the delivery-time/quality-rating
     * columns are intentionally not read here — see {@link BiAchatsMartRepository}
     * class javadoc.
     */
    public CatalogSnapshot readCatalogSnapshot() {
        String sql = """
                SELECT
                    COUNT(*) AS catalog_entries,
                    COUNT(DISTINCT c_bpartner_id) AS vendor_count,
                    COUNT(DISTINCT m_product_id) AS product_count,
                    AVG(pricelist) FILTER (WHERE pricelist <> 0) AS avg_catalog_price,
                    AVG(pricelastpo) FILTER (WHERE pricelastpo <> 0) AS avg_last_purchase_price,
                    AVG(pricelastinv) FILTER (WHERE pricelastinv <> 0) AS avg_last_invoiced_price
                FROM business.m_product_po
                """;
        return jdbc.getJdbcTemplate().queryForObject(sql, (rs, rowNum) -> new CatalogSnapshot(
                rs.getLong("catalog_entries"),
                rs.getLong("vendor_count"),
                rs.getLong("product_count"),
                money(rs.getBigDecimal("avg_catalog_price")),
                money(rs.getBigDecimal("avg_last_purchase_price")),
                money(rs.getBigDecimal("avg_last_invoiced_price"))));
    }
}

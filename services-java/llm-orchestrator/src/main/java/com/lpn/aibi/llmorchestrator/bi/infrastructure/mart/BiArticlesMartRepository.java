package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.ArticleConcentration;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.ArticleKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.ArticleRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.NamedValue;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.StockRiskRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.YearlyPoint;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiArticlesMartRepository extends MartRepositorySupport {

    private static final String PRODUCT_FILTER = """
            metric_date >= :from AND metric_date < :to
            AND (:category IS NULL OR product_category_key = :category OR m_product_category_id = :category)
            AND (:supplier IS NULL OR supplier_key = :supplier OR supplier_id = :supplier)
            AND (:customer IS NULL OR customer_key = :customer OR c_bpartner_id = :customer)
            """;

    public BiArticlesMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    /**
     * Year-to-date comparison for the YoY cards: Jan 1 → today of the current
     * year vs the same span last year. Fixed reading, independent of the page's
     * selected date range. Previous-year point first so the frontend's
     * sort-and-take-last-two keeps working.
     */
    public List<YearlyPoint> readYearToDateComparison() {
        String sql = """
                SELECT
                    COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_invoiced_sales,
                    COUNT(DISTINCT NULLIF(product_key, 0)) FILTER (WHERE metric_date >= :from AND metric_date < :to) AS cur_active_products,
                    COALESCE(SUM(quantity_invoiced) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_quantity,
                    COUNT(DISTINCT NULLIF(product_category_key, 0)) FILTER (WHERE metric_date >= :from AND metric_date < :to) AS cur_active_categories,
                    COUNT(DISTINCT NULLIF(theme_name, 'Thematique non renseignee')) FILTER (WHERE metric_date >= :from AND metric_date < :to) AS cur_active_themes,
                    COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_invoiced_sales,
                    COUNT(DISTINCT NULLIF(product_key, 0)) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_products,
                    COALESCE(SUM(quantity_invoiced) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_quantity,
                    COUNT(DISTINCT NULLIF(product_category_key, 0)) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_categories,
                    COUNT(DISTINCT NULLIF(theme_name, 'Thematique non renseignee')) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_themes
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :prevFrom AND metric_date < :to
                """;
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        return jdbc.queryForObject(sql, yearToDateParams(today), (rs, rowNum) -> List.of(
                new YearlyPoint(
                        currentYear - 1,
                        money(rs.getBigDecimal("prev_invoiced_sales")),
                        rs.getLong("prev_active_products"),
                        money(rs.getBigDecimal("prev_quantity")),
                        rs.getLong("prev_active_categories"),
                        rs.getLong("prev_active_themes")),
                new YearlyPoint(
                        currentYear,
                        money(rs.getBigDecimal("cur_invoiced_sales")),
                        rs.getLong("cur_active_products"),
                        money(rs.getBigDecimal("cur_quantity")),
                        rs.getLong("cur_active_categories"),
                        rs.getLong("cur_active_themes"))));
    }

    public ArticleKpis readKpis(BiQuery query) {
        // KPI active_products: distinct sold product keys in mart_sales_by_product for the selected period and filters.
        // KPI top_category_name: category with the highest invoiced revenue for the selected period and filters.
        // KPI top_theme_name: theme with the highest invoiced revenue for the selected period and filters.
        // KPI stock_risk_count: current stock-risk rows flagged as stockout risk for the selected category/supplier filters.
        String sql = """
                WITH products AS (
                    SELECT *
                    FROM mart.mart_sales_by_product
                    WHERE %s
                ),
                top_category AS (
                    SELECT category_name
                    FROM products
                    GROUP BY category_name
                    ORDER BY COALESCE(SUM(ca_facture), 0) DESC, category_name
                    LIMIT 1
                ),
                top_theme AS (
                    SELECT theme_name
                    FROM products
                    GROUP BY theme_name
                    ORDER BY COALESCE(SUM(ca_facture), 0) DESC, theme_name
                    LIMIT 1
                ),
                stock AS (
                    SELECT COALESCE(COUNT(*), 0) AS stock_risk_count
                    FROM mart.mart_stock_risk
                    WHERE is_stockout_risk = true
                      AND (:category IS NULL OR product_category_key = :category)
                      AND (:supplier IS NULL OR supplier_key = :supplier)
                ),
                product_totals AS (
                    SELECT COALESCE(COUNT(DISTINCT NULLIF(product_key, 0)), 0) AS active_products
                    FROM products
                )
                SELECT
                    product_totals.active_products,
                    COALESCE((SELECT category_name FROM top_category), 'UNKNOWN') AS top_category_name,
                    COALESCE((SELECT theme_name FROM top_theme), 'UNKNOWN') AS top_theme_name,
                    stock.stock_risk_count
                FROM product_totals CROSS JOIN stock
                """.formatted(PRODUCT_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new ArticleKpis(
                rs.getLong("active_products"),
                rs.getString("top_category_name"),
                rs.getString("top_theme_name"),
                rs.getLong("stock_risk_count")));
    }

    public List<ArticleRank> readTopArticles(BiQuery query) {
        // cumulative_share_percent: running share of total filtered CA held by products ranked at or above
        // this row (window functions see every filtered product, not just the paginated page).
        String sql = """
                WITH products AS (
                    SELECT
                        m_product_id AS product_id,
                        product_name,
                        category_name,
                        supplier_id,
                        supplier_name,
                        COALESCE(SUM(invoice_line_count), 0) AS line_count,
                        COALESCE(SUM(quantity_invoiced), 0) AS quantity,
                        COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_by_product
                    WHERE %s
                    GROUP BY m_product_id, product_name, category_name, supplier_id, supplier_name
                )
                SELECT
                    product_id,
                    product_name,
                    category_name,
                    supplier_id,
                    supplier_name,
                    line_count,
                    quantity,
                    invoiced_sales,
                    ROUND(
                        SUM(invoiced_sales) OVER (ORDER BY invoiced_sales DESC, product_name)
                        / NULLIF(SUM(invoiced_sales) OVER (), 0) * 100
                    , 2) AS cumulative_share_percent
                FROM products
                ORDER BY invoiced_sales DESC, product_name
                LIMIT :limit OFFSET :offset
                """.formatted(PRODUCT_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new ArticleRank(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getLong("line_count"),
                money(rs.getBigDecimal("quantity")),
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("cumulative_share_percent"))));
    }

    /**
     * Revenue concentration (Pareto): share of total filtered CA held by the 10
     * largest products, plus the total product count in the filtered set.
     */
    public ArticleConcentration readConcentration(BiQuery query) {
        String sql = """
                WITH products AS (
                    SELECT m_product_id, COALESCE(SUM(ca_facture), 0) AS ca
                    FROM mart.mart_sales_by_product
                    WHERE %s
                    GROUP BY m_product_id
                ),
                ranked AS (
                    SELECT ca, ROW_NUMBER() OVER (ORDER BY ca DESC) AS rn
                    FROM products
                ),
                totals AS (
                    SELECT COALESCE(SUM(ca), 0) AS total_ca, COUNT(*) AS total_products FROM products
                )
                SELECT
                    CASE WHEN totals.total_ca = 0 THEN 0
                         ELSE ROUND(COALESCE((SELECT SUM(ranked.ca) FROM ranked WHERE ranked.rn <= 10), 0) / totals.total_ca * 100, 2)
                    END AS top10_share_percent,
                    totals.total_products AS total_products
                FROM totals
                """.formatted(PRODUCT_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new ArticleConcentration(
                money(rs.getBigDecimal("top10_share_percent")),
                rs.getLong("total_products")));
    }

    public List<NamedValue> readMix(BiQuery query, String columnName) {
        String sql = """
                SELECT %s AS name, COALESCE(SUM(ca_facture), 0) AS value
                FROM mart.mart_sales_by_product
                WHERE %s
                GROUP BY %s
                ORDER BY value DESC, name
                LIMIT :limit OFFSET :offset
                """.formatted(columnName, PRODUCT_FILTER, columnName);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new NamedValue(
                rs.getString("name"),
                money(rs.getBigDecimal("value"))));
    }

    public List<StockRiskRank> readStockPriorities(BiQuery query) {
        String sql = """
                SELECT
                    m_product_id AS product_id,
                    product_name,
                    category_name,
                    theme_name,
                    supplier_name,
                    qty_on_hand,
                    qty_reserved,
                    qty_available,
                    qty_ordered,
                    is_stockout_risk,
                    stock_risk_level
                FROM mart.mart_stock_risk
                WHERE (:category IS NULL OR product_category_key = :category)
                  AND (:supplier IS NULL OR supplier_key = :supplier)
                ORDER BY is_stockout_risk DESC, qty_available ASC, product_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new StockRiskRank(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getString("theme_name"),
                rs.getString("supplier_name"),
                money(rs.getBigDecimal("qty_on_hand")),
                money(rs.getBigDecimal("qty_reserved")),
                money(rs.getBigDecimal("qty_available")),
                money(rs.getBigDecimal("qty_ordered")),
                rs.getBoolean("is_stockout_risk"),
                rs.getString("stock_risk_level")));
    }
}

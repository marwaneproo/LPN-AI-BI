package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.ClientConcentration;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.ClientKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.ClientRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.CustomerArticleRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.CustomerCommercialRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.CustomerPaymentRisk;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.GeographyRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse.YearlyPoint;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiClientsMartRepository extends MartRepositorySupport {

    private static final String CUSTOMER_FILTER = """
            metric_date >= :from AND metric_date < :to
            AND (:commercial IS NULL OR commercial_key = :commercial)
            AND (:customer IS NULL OR customer_key = :customer OR c_bpartner_id = :customer)
            AND (:region IS NULL OR region_name = :region)
            AND (:city IS NULL OR city_name = :city)
            """;

    public BiClientsMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
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
                    COUNT(DISTINCT NULLIF(customer_key, 0)) FILTER (WHERE active_customer_flag = 1 AND metric_date >= :from AND metric_date < :to) AS cur_active_customers,
                    COUNT(DISTINCT NULLIF(commercial_dim_key, 0)) FILTER (WHERE metric_date >= :from AND metric_date < :to) AS cur_active_commercials,
                    COUNT(DISTINCT NULLIF(region_name, 'Region non renseignee')) FILTER (WHERE metric_date >= :from AND metric_date < :to) AS cur_active_regions,
                    COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_invoiced_sales,
                    COUNT(DISTINCT NULLIF(customer_key, 0)) FILTER (WHERE active_customer_flag = 1 AND metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_customers,
                    COUNT(DISTINCT NULLIF(commercial_dim_key, 0)) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_commercials,
                    COUNT(DISTINCT NULLIF(region_name, 'Region non renseignee')) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo) AS prev_active_regions
                FROM mart.mart_sales_by_customer
                WHERE metric_date >= :prevFrom AND metric_date < :to
                """;
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        return jdbc.queryForObject(sql, yearToDateParams(today), (rs, rowNum) -> List.of(
                new YearlyPoint(
                        currentYear - 1,
                        money(rs.getBigDecimal("prev_invoiced_sales")),
                        rs.getLong("prev_active_customers"),
                        rs.getLong("prev_active_commercials"),
                        rs.getLong("prev_active_regions")),
                new YearlyPoint(
                        currentYear,
                        money(rs.getBigDecimal("cur_invoiced_sales")),
                        rs.getLong("cur_active_customers"),
                        rs.getLong("cur_active_commercials"),
                        rs.getLong("cur_active_regions"))));
    }

    public ClientKpis readKpis(BiQuery query) {
        // KPI active_customers: distinct customer keys in mart_sales_by_customer for the selected period and filters.
        // KPI top_customer_name: customer with the highest invoiced revenue for the selected period and filters.
        // KPI portfolio_commercial_count: distinct commercials serving the selected customer portfolio.
        // KPI geography_count: distinct informed regions contributing to the selected customer portfolio.
        String sql = """
                WITH customers AS (
                    SELECT *
                    FROM mart.mart_sales_by_customer
                    WHERE %s
                ),
                top_customer AS (
                    SELECT customer_name
                    FROM customers
                    GROUP BY customer_name
                    ORDER BY COALESCE(SUM(ca_facture), 0) DESC, customer_name
                    LIMIT 1
                )
                SELECT
                    COALESCE(COUNT(DISTINCT NULLIF(customer_key, 0)), 0) AS active_customers,
                    COALESCE((SELECT customer_name FROM top_customer), 'UNKNOWN') AS top_customer_name,
                    COALESCE(COUNT(DISTINCT NULLIF(commercial_key, 0)), 0) AS portfolio_commercial_count,
                    COALESCE(COUNT(DISTINCT NULLIF(region_name, 'Region non renseignee')), 0) AS geography_count
                FROM customers
                """.formatted(CUSTOMER_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new ClientKpis(
                rs.getLong("active_customers"),
                rs.getString("top_customer_name"),
                rs.getLong("portfolio_commercial_count"),
                rs.getLong("geography_count")));
    }

    public List<ClientRank> readTopClients(BiQuery query) {
        // cumulative_share_percent: running share of total filtered CA held by customers ranked at or above
        // this row (window functions see every filtered customer, not just the paginated page).
        String sql = """
                WITH customers AS (
                    SELECT
                        c_bpartner_id AS customer_id,
                        customer_name,
                        COALESCE(SUM(invoice_count), 0) AS invoice_count,
                        COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_by_customer
                    WHERE %s
                    GROUP BY c_bpartner_id, customer_name
                )
                SELECT
                    customer_id,
                    customer_name,
                    invoice_count,
                    invoiced_sales,
                    CASE WHEN invoice_count = 0 THEN 0 ELSE ROUND(invoiced_sales / NULLIF(invoice_count, 0), 2) END AS average_invoice_value,
                    ROUND(
                        SUM(invoiced_sales) OVER (ORDER BY invoiced_sales DESC, customer_name)
                        / NULLIF(SUM(invoiced_sales) OVER (), 0) * 100
                    , 2) AS cumulative_share_percent
                FROM customers
                ORDER BY invoiced_sales DESC, customer_name
                LIMIT :limit OFFSET :offset
                """.formatted(CUSTOMER_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new ClientRank(
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("average_invoice_value")),
                money(rs.getBigDecimal("cumulative_share_percent"))));
    }

    /**
     * Revenue concentration (Pareto): share of total filtered CA held by the 10
     * largest customers, plus the total customer count in the filtered set.
     */
    public ClientConcentration readConcentration(BiQuery query) {
        String sql = """
                WITH customers AS (
                    SELECT c_bpartner_id, COALESCE(SUM(ca_facture), 0) AS ca
                    FROM mart.mart_sales_by_customer
                    WHERE %s
                    GROUP BY c_bpartner_id
                ),
                ranked AS (
                    SELECT ca, ROW_NUMBER() OVER (ORDER BY ca DESC) AS rn
                    FROM customers
                ),
                totals AS (
                    SELECT COALESCE(SUM(ca), 0) AS total_ca, COUNT(*) AS total_customers FROM customers
                )
                SELECT
                    CASE WHEN totals.total_ca = 0 THEN 0
                         ELSE ROUND(COALESCE((SELECT SUM(ranked.ca) FROM ranked WHERE ranked.rn <= 10), 0) / totals.total_ca * 100, 2)
                    END AS top10_share_percent,
                    totals.total_customers AS total_customers
                FROM totals
                """.formatted(CUSTOMER_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new ClientConcentration(
                money(rs.getBigDecimal("top10_share_percent")),
                rs.getLong("total_customers")));
    }

    public List<CustomerCommercialRank> readByCommercial(BiQuery query) {
        String sql = """
                SELECT
                    commercial_key AS salesrep_id,
                    commercial_name AS commercial_label,
                    COALESCE(COUNT(DISTINCT NULLIF(customer_key, 0)), 0) AS customer_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_customer
                WHERE %s
                GROUP BY commercial_key, commercial_name
                ORDER BY invoiced_sales DESC, commercial_label
                LIMIT :limit OFFSET :offset
                """.formatted(CUSTOMER_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CustomerCommercialRank(
                rs.getLong("salesrep_id"),
                rs.getString("commercial_label"),
                rs.getLong("customer_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<CustomerArticleRank> readByArticle(BiQuery query) {
        String sql = """
                SELECT
                    c_bpartner_id AS customer_id,
                    customer_name,
                    product_name,
                    COALESCE(SUM(quantity_invoiced), 0) AS quantity,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_by_product
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:customer IS NULL OR customer_key = :customer OR c_bpartner_id = :customer)
                  AND (:category IS NULL OR product_category_key = :category OR m_product_category_id = :category)
                  AND (:supplier IS NULL OR supplier_key = :supplier OR supplier_id = :supplier)
                GROUP BY c_bpartner_id, customer_name, product_name
                ORDER BY invoiced_sales DESC, customer_name, product_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CustomerArticleRank(
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getString("product_name"),
                money(rs.getBigDecimal("quantity")),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<GeographyRank> readByRegion(BiQuery query) {
        String sql = """
                SELECT
                    region_name,
                    city_name,
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
        return jdbc.query(sql, params(query), (rs, rowNum) -> new GeographyRank(
                rs.getString("region_name"),
                rs.getString("city_name"),
                rs.getLong("customer_count"),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales"))));
    }

    public List<CustomerPaymentRisk> readFinanceRisks(BiQuery query) {
        String sql = """
                SELECT
                    c_bpartner_id AS customer_id,
                    customer_name,
                    COALESCE(SUM(unpaid_invoice_count), 0) AS unpaid_invoice_count,
                    COALESCE(SUM(unpaid_invoice_amount), 0) AS unpaid_invoice_amount
                FROM mart.mart_payment_status
                WHERE metric_date >= :from AND metric_date < :to
                  AND payment_status = 'UNPAID'
                  AND (:customer IS NULL OR customer_key = :customer OR c_bpartner_id = :customer)
                  AND (:commercial IS NULL OR commercial_key = :commercial)
                GROUP BY c_bpartner_id, customer_name
                ORDER BY unpaid_invoice_amount DESC, customer_name
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CustomerPaymentRisk(
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getLong("unpaid_invoice_count"),
                money(rs.getBigDecimal("unpaid_invoice_amount"))));
    }
}

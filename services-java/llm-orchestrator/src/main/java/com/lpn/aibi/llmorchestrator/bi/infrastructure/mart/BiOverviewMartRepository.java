package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.CustomerRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.NamedMetric;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.OverviewKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.PeriodSalesPoint;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.ProductRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.SalesMix;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.StatusBreakdown;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.YearlyPoint;

@Repository
public class BiOverviewMartRepository {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final JdbcTemplate jdbcTemplate;

    public BiOverviewMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OverviewKpis readKpis(LocalDate fromInclusive, LocalDate toExclusive) {
        // KPI order_count: total validated order documents counted in mart_sales_daily for the selected period.
        // KPI order_value: total ordered revenue (ca_commande) in MAD for the selected period.
        // KPI active_customers: distinct invoiced customers flagged active in mart_sales_by_customer for the selected period.
        // KPI invoice_count: total invoice documents counted in mart_sales_daily for the selected period.
        // KPI invoiced_sales: total invoiced revenue (ca_facture) in MAD for the selected period.
        // KPI paid/unpaid_invoice_count: invoice counts split by payment state in mart_sales_daily for the selected period.
        // KPI invoice_coverage_percent: invoice_count divided by order_count, expressed as a percentage.
        String sql = """
                WITH sales AS (
                    SELECT
                        COALESCE(SUM(nombre_commandes), 0) AS order_count,
                        COALESCE(SUM(ca_commande), 0) AS order_value,
                        COALESCE(SUM(invoice_count), 0) AS invoice_count,
                        COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                        COALESCE(SUM(paid_invoice_count), 0) AS paid_invoice_count,
                        COALESCE(SUM(unpaid_invoice_count), 0) AS unpaid_invoice_count
                    FROM mart.mart_sales_daily
                    WHERE metric_date >= ? AND metric_date < ?
                ),
                customers AS (
                    SELECT COALESCE(COUNT(DISTINCT NULLIF(customer_key, 0)), 0) AS active_customers
                    FROM mart.mart_sales_by_customer
                    WHERE metric_date >= ? AND metric_date < ?
                      AND active_customer_flag = 1
                )
                SELECT
                    sales.order_count,
                    sales.order_value,
                    customers.active_customers,
                    sales.invoice_count,
                    sales.invoiced_sales,
                    sales.paid_invoice_count,
                    sales.unpaid_invoice_count,
                    CASE WHEN sales.order_count = 0 THEN 0
                         ELSE ROUND((sales.invoice_count::numeric / sales.order_count::numeric) * 100, 2)
                    END AS invoice_coverage_percent
                FROM sales CROSS JOIN customers
                """;
        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new OverviewKpis(
                        rs.getLong("order_count"),
                        money(rs.getBigDecimal("order_value")),
                        rs.getLong("active_customers"),
                        rs.getLong("invoice_count"),
                        money(rs.getBigDecimal("invoiced_sales")),
                        rs.getLong("paid_invoice_count"),
                        rs.getLong("unpaid_invoice_count"),
                        money(rs.getBigDecimal("invoice_coverage_percent"))),
                date(fromInclusive),
                date(toExclusive),
                date(fromInclusive),
                date(toExclusive));
    }

    public List<PeriodSalesPoint> readTrend(LocalDate fromInclusive, LocalDate toExclusive, String granularity) {
        String sql = """
                SELECT
                    CASE
                        WHEN ? = 'day' THEN TO_CHAR(metric_date, 'YYYY-MM-DD')
                        WHEN ? = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month, 'YYYY-MM')
                    END AS period,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS order_value,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_daily
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY period
                ORDER BY period
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PeriodSalesPoint(
                        rs.getString("period"),
                        rs.getLong("order_count"),
                        money(rs.getBigDecimal("order_value")),
                        rs.getLong("invoice_count"),
                        money(rs.getBigDecimal("invoiced_sales"))),
                granularity,
                granularity,
                date(fromInclusive),
                date(toExclusive));
    }

    /**
     * The same trend as {@link #readTrend}, read one year earlier
     * (from/to shifted back by a year) so the frontend can overlay a
     * previous-period series on the current one.
     *
     * <p><b>Label alignment convention:</b> each bucket is labelled with the
     * CURRENT window's corresponding period, not its own — the shift
     * {@code + INTERVAL '1 year'} is applied to the date BEFORE bucketing, so the
     * March 2025 point comes back as {@code "2026-03"}. The frontend can then
     * merge previous into current purely by X value, with no date arithmetic.
     */
    public List<PeriodSalesPoint> readCompareTrend(LocalDate fromInclusive, LocalDate toExclusive, String granularity) {
        String sql = """
                SELECT
                    CASE
                        WHEN ? = 'day' THEN TO_CHAR(metric_date + INTERVAL '1 year', 'YYYY-MM-DD')
                        WHEN ? = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date + INTERVAL '1 year'), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month + INTERVAL '1 year', 'YYYY-MM')
                    END AS period,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS order_value,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                FROM mart.mart_sales_daily
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY period
                ORDER BY period
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PeriodSalesPoint(
                        rs.getString("period"),
                        rs.getLong("order_count"),
                        money(rs.getBigDecimal("order_value")),
                        rs.getLong("invoice_count"),
                        money(rs.getBigDecimal("invoiced_sales"))),
                granularity,
                granularity,
                date(fromInclusive.minusYears(1)),
                date(toExclusive.minusYears(1)));
    }

    public NamedMetric readTopCommercial(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                SELECT commercial_name AS name, COALESCE(SUM(ca_facture), 0) AS value
                FROM mart.mart_sales_by_commercial
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY commercial_name
                ORDER BY value DESC, commercial_name
                LIMIT 1
                """;
        return readNamedMetric(sql, fromInclusive, toExclusive);
    }

    public NamedMetric readTopSupplier(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                SELECT COALESCE(supplier_name, 'UNKNOWN') AS name, COALESCE(SUM(ca_facture), 0) AS value
                FROM mart.mart_sales_by_product
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY supplier_name
                ORDER BY value DESC, name
                LIMIT 1
                """;
        return readNamedMetric(sql, fromInclusive, toExclusive);
    }

    public NamedMetric readTopRegion(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                SELECT region_name AS name, COALESCE(SUM(ca_facture), 0) AS value
                FROM mart.mart_sales_by_region
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY region_name
                ORDER BY value DESC, region_name
                LIMIT 1
                """;
        return readNamedMetric(sql, fromInclusive, toExclusive);
    }

    public SalesMix readSalesMix(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                WITH sales AS (
                    SELECT
                        COALESCE(SUM(ca_commande), 0) AS order_value,
                        COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_daily
                    WHERE metric_date >= ? AND metric_date < ?
                ),
                products AS (
                    SELECT COALESCE(COUNT(DISTINCT NULLIF(product_key, 0)), 0) AS tracked_products
                    FROM mart.mart_sales_by_product
                    WHERE metric_date >= ? AND metric_date < ?
                )
                SELECT
                    sales.order_value,
                    sales.invoiced_sales,
                    products.tracked_products
                FROM sales CROSS JOIN products
                """;
        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new SalesMix(
                        money(rs.getBigDecimal("order_value")),
                        money(rs.getBigDecimal("invoiced_sales")),
                        rs.getLong("tracked_products")),
                date(fromInclusive),
                date(toExclusive),
                date(fromInclusive),
                date(toExclusive));
    }

    public List<CustomerRank> readTopCustomers(LocalDate fromInclusive, LocalDate toExclusive, int limit) {
        String sql = """
                SELECT
                    customer_name,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS total_order_value,
                    CASE WHEN COALESCE(SUM(nombre_commandes), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_commande), 0) / SUM(nombre_commandes), 2)
                    END AS average_order_value
                FROM mart.mart_sales_by_customer
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY customer_name
                ORDER BY total_order_value DESC, customer_name
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CustomerRank(
                        rs.getString("customer_name"),
                        rs.getLong("order_count"),
                        money(rs.getBigDecimal("total_order_value")),
                        money(rs.getBigDecimal("average_order_value"))),
                date(fromInclusive),
                date(toExclusive),
                limit);
    }

    public List<ProductRank> readTopProducts(LocalDate fromInclusive, LocalDate toExclusive, int limit) {
        String sql = """
                SELECT
                    product_name,
                    COALESCE(SUM(invoice_line_count), 0) AS line_count,
                    COALESCE(SUM(quantity_invoiced), 0) AS total_quantity,
                    COALESCE(SUM(ca_facture), 0) AS total_order_value
                FROM mart.mart_sales_by_product
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY product_name
                ORDER BY total_order_value DESC, product_name
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProductRank(
                        rs.getString("product_name"),
                        rs.getLong("line_count"),
                        money(rs.getBigDecimal("total_quantity")),
                        money(rs.getBigDecimal("total_order_value"))),
                date(fromInclusive),
                date(toExclusive),
                limit);
    }

    public List<StatusBreakdown> readOrderStatuses(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                SELECT
                    doc_status AS status,
                    doc_status AS label,
                    COALESCE(SUM(nombre_commandes), 0) AS item_count,
                    COALESCE(SUM(ca_commande), 0) AS total_value
                FROM mart.mart_order_to_invoice_flow
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY doc_status
                ORDER BY item_count DESC, status
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new StatusBreakdown(
                        rs.getString("status"),
                        rs.getString("label"),
                        rs.getLong("item_count"),
                        money(rs.getBigDecimal("total_value"))),
                date(fromInclusive),
                date(toExclusive));
    }

    /**
     * Year-to-date comparison for the YoY cards: Jan 1 → today of the current
     * year vs the same span last year. Fixed reading, independent of the page's
     * selected date range. Previous-year point first so the frontend's
     * sort-and-take-last-two keeps working.
     */
    public List<YearlyPoint> readYearToDateComparison() {
        LocalDate today = LocalDate.now();
        LocalDate fromInclusive = today.withDayOfYear(1);
        LocalDate toExclusive = today.plusDays(1);
        LocalDate prevFrom = fromInclusive.minusYears(1);
        LocalDate prevTo = toExclusive.minusYears(1);
        String sql = """
                WITH sales AS (
                    SELECT
                        COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS cur_invoiced_sales,
                        COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS cur_order_value,
                        COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS cur_order_count,
                        COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS cur_invoice_count,
                        COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS prev_invoiced_sales,
                        COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS prev_order_value,
                        COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS prev_order_count,
                        COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= ? AND metric_date < ?), 0) AS prev_invoice_count
                    FROM mart.mart_sales_daily
                    WHERE metric_date >= ? AND metric_date < ?
                ),
                customers AS (
                    SELECT
                        COUNT(DISTINCT NULLIF(customer_key, 0)) FILTER (WHERE active_customer_flag = 1 AND metric_date >= ? AND metric_date < ?) AS cur_active_customers,
                        COUNT(DISTINCT NULLIF(customer_key, 0)) FILTER (WHERE active_customer_flag = 1 AND metric_date >= ? AND metric_date < ?) AS prev_active_customers
                    FROM mart.mart_sales_by_customer
                    WHERE metric_date >= ? AND metric_date < ?
                )
                SELECT sales.*, customers.cur_active_customers, customers.prev_active_customers
                FROM sales CROSS JOIN customers
                """;
        Object[] args = {
                date(fromInclusive), date(toExclusive),
                date(fromInclusive), date(toExclusive),
                date(fromInclusive), date(toExclusive),
                date(fromInclusive), date(toExclusive),
                date(prevFrom), date(prevTo),
                date(prevFrom), date(prevTo),
                date(prevFrom), date(prevTo),
                date(prevFrom), date(prevTo),
                date(prevFrom), date(toExclusive),
                date(fromInclusive), date(toExclusive),
                date(prevFrom), date(prevTo),
                date(prevFrom), date(toExclusive),
        };
        int currentYear = today.getYear();
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> List.of(
                new YearlyPoint(
                        currentYear - 1,
                        money(rs.getBigDecimal("prev_invoiced_sales")),
                        money(rs.getBigDecimal("prev_order_value")),
                        rs.getLong("prev_order_count"),
                        rs.getLong("prev_invoice_count"),
                        rs.getLong("prev_active_customers"),
                        coverage(rs.getLong("prev_invoice_count"), rs.getLong("prev_order_count"))),
                new YearlyPoint(
                        currentYear,
                        money(rs.getBigDecimal("cur_invoiced_sales")),
                        money(rs.getBigDecimal("cur_order_value")),
                        rs.getLong("cur_order_count"),
                        rs.getLong("cur_invoice_count"),
                        rs.getLong("cur_active_customers"),
                        coverage(rs.getLong("cur_invoice_count"), rs.getLong("cur_order_count")))),
                args);
    }

    private static BigDecimal coverage(long invoiceCount, long orderCount) {
        if (orderCount == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(invoiceCount * 100.0 / orderCount).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public List<StatusBreakdown> readInvoiceStatuses(LocalDate fromInclusive, LocalDate toExclusive) {
        String sql = """
                SELECT
                    payment_status AS status,
                    payment_status AS label,
                    COALESCE(SUM(invoice_count), 0) AS item_count,
                    COALESCE(SUM(ca_facture), 0) AS total_value
                FROM mart.mart_payment_status
                WHERE metric_date >= ? AND metric_date < ?
                GROUP BY payment_status
                ORDER BY item_count DESC, status
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new StatusBreakdown(
                        rs.getString("status"),
                        rs.getString("label"),
                        rs.getLong("item_count"),
                        money(rs.getBigDecimal("total_value"))),
                date(fromInclusive),
                date(toExclusive));
    }

    private NamedMetric readNamedMetric(String sql, LocalDate fromInclusive, LocalDate toExclusive) {
        List<NamedMetric> metrics = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new NamedMetric(rs.getString("name"), money(rs.getBigDecimal("value"))),
                date(fromInclusive),
                date(toExclusive));
        if (metrics.isEmpty()) {
            return new NamedMetric("UNKNOWN", ZERO);
        }
        return metrics.get(0);
    }

    private static Date date(LocalDate value) {
        return Date.valueOf(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}

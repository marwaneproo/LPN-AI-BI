package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.BestMonth;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.RevenueKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.RevenueSignals;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.RevenueStatus;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.RevenueTrendPoint;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.UnpaidCommercial;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.UnpaidCustomer;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.UnpaidExposure;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse.YearlyPoint;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiRevenueMartRepository extends MartRepositorySupport {

    public BiRevenueMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    /**
     * Sales source for the click-to-filter-by-commercial feature:
     * {@code mart_sales_daily} normally, {@code mart_sales_by_commercial} when a
     * commercial filter is active — the two carry the SAME measures
     * (ca_facture, ca_commande, nombre_commandes, invoice_count,
     * invoice_gap_amount, period_month) and reconcile exactly when summed
     * (verified in psql to the centime on Jan–Jun 2026), the latter just adds
     * the commercial_key grain the daily mart lacks.
     */
    private String salesSource(BiQuery query) {
        return query.commercial() != null ? "mart.mart_sales_by_commercial" : "mart.mart_sales_daily";
    }

    /**
     * Companion predicate for {@link #salesSource} (and the commercial-aware
     * marts): empty when no filter, so SQL against mart_sales_daily — which has
     * no commercial_key column — never references it.
     */
    private String commercialPredicate(BiQuery query) {
        return query.commercial() != null ? "AND commercial_key = :commercial" : "";
    }

    public RevenueKpis readKpis(BiQuery query) {
        // KPI invoiced_sales: total invoiced revenue (ca_facture) in MAD for the selected period.
        // KPI ordered_sales: total ordered revenue (ca_commande) in MAD for the selected period.
        // KPI invoice_gap_amount: total facturation gap stored in the sales mart for the selected period.
        // KPI average_order_value: ordered revenue divided by order count for the selected period.
        String sql = """
                SELECT
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(invoice_gap_amount), 0) AS invoice_gap_amount,
                    CASE WHEN COALESCE(SUM(nombre_commandes), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_commande), 0) / NULLIF(SUM(nombre_commandes), 0), 2)
                    END AS average_order_value
                FROM %s
                WHERE metric_date >= :from AND metric_date < :to
                  %s
                """.formatted(salesSource(query), commercialPredicate(query));
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new RevenueKpis(
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("ordered_sales")),
                money(rs.getBigDecimal("invoice_gap_amount")),
                money(rs.getBigDecimal("average_order_value"))));
    }

    public List<RevenueTrendPoint> readTrend(BiQuery query) {
        String sql = """
                SELECT
                    CASE
                        WHEN :granularity = 'day' THEN TO_CHAR(metric_date, 'YYYY-MM-DD')
                        WHEN :granularity = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month, 'YYYY-MM')
                    END AS period,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count
                FROM %s
                WHERE metric_date >= :from AND metric_date < :to
                  %s
                GROUP BY period
                ORDER BY period
                """.formatted(salesSource(query), commercialPredicate(query));
        return jdbc.query(sql, params(query), (rs, rowNum) -> new RevenueTrendPoint(
                rs.getString("period"),
                money(rs.getBigDecimal("ordered_sales")),
                money(rs.getBigDecimal("invoiced_sales")),
                rs.getLong("order_count"),
                rs.getLong("invoice_count")));
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
    public List<RevenueTrendPoint> readCompareTrend(BiQuery query) {
        String sql = """
                SELECT
                    CASE
                        WHEN :granularity = 'day' THEN TO_CHAR(metric_date + INTERVAL '1 year', 'YYYY-MM-DD')
                        WHEN :granularity = 'week' THEN TO_CHAR(DATE_TRUNC('week', metric_date + INTERVAL '1 year'), 'YYYY-MM-DD')
                        ELSE TO_CHAR(period_month + INTERVAL '1 year', 'YYYY-MM')
                    END AS period,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count
                FROM %s
                WHERE metric_date >= :from AND metric_date < :to
                  %s
                GROUP BY period
                ORDER BY period
                """.formatted(salesSource(query), commercialPredicate(query));
        return jdbc.query(sql, previousPeriodParams(query), (rs, rowNum) -> new RevenueTrendPoint(
                rs.getString("period"),
                money(rs.getBigDecimal("ordered_sales")),
                money(rs.getBigDecimal("invoiced_sales")),
                rs.getLong("order_count"),
                rs.getLong("invoice_count")));
    }

    /**
     * Yearly CA facturé vs commandé for the multi-year chart, at COMPARABLE
     * SPAN: every year only sums Jan 1 → the day-of-year of the newest data
     * point in the mart (e.g. data ends 2026-06-15 → 2024 and 2025 are also
     * cut at June 15), so full past years no longer dwarf the year in
     * progress. Intentionally NOT constrained by the selected date range, but
     * it DOES honor the commercial click-filter (shared source switch); the
     * span cutoff stays computed on the full daily mart so the comparison day
     * never moves with the filter.
     * {@code partial} now flags the running year (the one the cutoff comes
     * from), which the frontend renders as « en cours ».
     */
    public List<YearlyPoint> readYearly(BiQuery query) {
        String sql = """
                WITH span AS (
                    SELECT
                        EXTRACT(MONTH FROM MAX(metric_date)) * 100 + EXTRACT(DAY FROM MAX(metric_date)) AS mmdd,
                        EXTRACT(YEAR FROM MAX(metric_date))::int AS latest_year
                    FROM mart.mart_sales_daily
                )
                SELECT
                    EXTRACT(YEAR FROM metric_date)::int AS year,
                    COALESCE(SUM(ca_facture), 0) AS ca_facture,
                    COALESCE(SUM(ca_commande), 0) AS ca_commande,
                    COALESCE(SUM(invoice_gap_amount), 0) AS ecart,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    (EXTRACT(YEAR FROM metric_date)::int = (SELECT latest_year FROM span)) AS partial
                FROM %s
                WHERE EXTRACT(MONTH FROM metric_date) * 100 + EXTRACT(DAY FROM metric_date) <= (SELECT mmdd FROM span)
                  %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(salesSource(query), commercialPredicate(query));
        return jdbc.query(sql, params(query), (rs, rowNum) -> new YearlyPoint(
                rs.getInt("year"),
                money(rs.getBigDecimal("ca_facture")),
                money(rs.getBigDecimal("ca_commande")),
                money(rs.getBigDecimal("ecart")),
                rs.getLong("order_count"),
                rs.getLong("invoice_count"),
                rs.getBoolean("partial")));
    }

    /**
     * Year-to-date comparison for the YoY cards: Jan 1 → today of the current
     * year vs the same span last year. Fixed reading, independent of the page's
     * selected date range. Feeds the comparison cards, while {@link #readYearly()}
     * keeps feeding the fixed multi-year hero chart; {@code partial} is not
     * meaningful for a window and is false.
     */
    public List<YearlyPoint> readYearToDateComparison() {
        String sql = """
                SELECT
                    COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_ca_facture,
                    COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_ca_commande,
                    COALESCE(SUM(invoice_gap_amount) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_ecart,
                    COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_order_count,
                    COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_invoice_count,
                    COALESCE(SUM(ca_facture) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_ca_facture,
                    COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_ca_commande,
                    COALESCE(SUM(invoice_gap_amount) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_ecart,
                    COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_order_count,
                    COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_invoice_count
                FROM mart.mart_sales_daily
                WHERE metric_date >= :prevFrom AND metric_date < :to
                """;
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        return jdbc.queryForObject(sql, yearToDateParams(today), (rs, rowNum) -> List.of(
                new YearlyPoint(
                        currentYear - 1,
                        money(rs.getBigDecimal("prev_ca_facture")),
                        money(rs.getBigDecimal("prev_ca_commande")),
                        money(rs.getBigDecimal("prev_ecart")),
                        rs.getLong("prev_order_count"),
                        rs.getLong("prev_invoice_count"),
                        false),
                new YearlyPoint(
                        currentYear,
                        money(rs.getBigDecimal("cur_ca_facture")),
                        money(rs.getBigDecimal("cur_ca_commande")),
                        money(rs.getBigDecimal("cur_ecart")),
                        rs.getLong("cur_order_count"),
                        rs.getLong("cur_invoice_count"),
                        false)));
    }

    public RevenueSignals readSignals(BiQuery query) {
        // best_month: the monthly rollup mart has no commercial grain, so the
        // filtered variant re-aggregates mart_sales_by_commercial per month.
        String bestMonthCte = query.commercial() != null
                ? """
                    SELECT TO_CHAR(period_month, 'YYYY-MM') AS period, COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_by_commercial
                    WHERE period_month >= DATE_TRUNC('month', CAST(:from AS date))
                      AND period_month < DATE_TRUNC('month', CAST(:to AS date))
                      AND commercial_key = :commercial
                    GROUP BY period_month
                    ORDER BY invoiced_sales DESC, period_month
                    LIMIT 1
                """
                : """
                    SELECT TO_CHAR(period_month, 'YYYY-MM') AS period, ca_facture AS invoiced_sales
                    FROM mart.mart_sales_monthly
                    WHERE period_month >= DATE_TRUNC('month', CAST(:from AS date))
                      AND period_month < DATE_TRUNC('month', CAST(:to AS date))
                    ORDER BY ca_facture DESC, period_month
                    LIMIT 1
                """;
        String sql = """
                WITH best_month AS (
                %s
                ),
                daily AS (
                    SELECT
                        CASE WHEN COALESCE(SUM(ca_commande), 0) = 0 THEN 0
                             ELSE ROUND(COALESCE(SUM(ca_facture), 0) * 100 / NULLIF(SUM(ca_commande), 0), 2)
                        END AS invoice_coverage_percent
                    FROM %s
                    WHERE metric_date >= :from AND metric_date < :to
                      %s
                ),
                unpaid AS (
                    SELECT
                        COALESCE(SUM(unpaid_invoice_count), 0) AS unpaid_invoice_count,
                        COALESCE(SUM(unpaid_invoice_amount), 0) AS unpaid_invoice_amount
                    FROM mart.mart_payment_status
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:commercial IS NULL OR commercial_key = :commercial)
                )
                SELECT
                    COALESCE(best_month.period, 'UNKNOWN') AS best_month_period,
                    COALESCE(best_month.invoiced_sales, 0) AS best_month_invoiced_sales,
                    daily.invoice_coverage_percent,
                    unpaid.unpaid_invoice_count,
                    unpaid.unpaid_invoice_amount
                FROM daily CROSS JOIN unpaid LEFT JOIN best_month ON true
                """.formatted(bestMonthCte, salesSource(query), commercialPredicate(query));
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new RevenueSignals(
                new BestMonth(
                        rs.getString("best_month_period"),
                        money(rs.getBigDecimal("best_month_invoiced_sales"))),
                money(rs.getBigDecimal("invoice_coverage_percent")),
                rs.getLong("unpaid_invoice_count"),
                money(rs.getBigDecimal("unpaid_invoice_amount"))));
    }

    public RevenueStatus readStatus(BiQuery query) {
        String sql = """
                WITH sales AS (
                    SELECT
                        COALESCE(SUM(ca_facture), 0) AS facture,
                        COALESCE(SUM(invoice_gap_amount), 0) AS ecart
                    FROM %s
                    WHERE metric_date >= :from AND metric_date < :to
                      %s
                ),
                flow AS (
                    SELECT COALESCE(SUM(quantity_delivery_gap), 0) AS a_livrer
                    FROM mart.mart_order_to_invoice_flow
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:commercial IS NULL OR commercial_key = :commercial)
                )
                SELECT sales.facture, flow.a_livrer, sales.ecart
                FROM sales CROSS JOIN flow
                """.formatted(salesSource(query), commercialPredicate(query));
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new RevenueStatus(
                money(rs.getBigDecimal("facture")),
                money(rs.getBigDecimal("a_livrer")),
                money(rs.getBigDecimal("ecart"))));
    }

    /**
     * Unpaid-invoice exposure over {@code [from,to)}: total unpaid amount, its
     * rate against invoiced CA, and the top 8 customers / commercials carrying
     * that exposure. Same {@code mart_payment_status} source as {@link #readSignals}.
     */
    public UnpaidExposure readUnpaidExposure(BiQuery query) {
        String totalsSql = """
                SELECT
                    COALESCE(SUM(unpaid_invoice_amount), 0) AS unpaid_amount,
                    COALESCE(SUM(ca_facture), 0) AS ca_facture
                FROM mart.mart_payment_status
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:commercial IS NULL OR commercial_key = :commercial)
                """;
        var totals = jdbc.queryForObject(totalsSql, params(query), (rs, rowNum) -> new BigDecimal[] {
                money(rs.getBigDecimal("unpaid_amount")),
                money(rs.getBigDecimal("ca_facture"))});
        BigDecimal unpaidAmount = totals[0];
        BigDecimal unpaidRatePercent = ratioPercent(unpaidAmount, totals[1]);

        String topCustomersSql = """
                SELECT customer_name,
                       SUM(unpaid_invoice_amount) AS amount,
                       SUM(unpaid_invoice_count) AS unpaid_invoice_count
                FROM mart.mart_payment_status
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:commercial IS NULL OR commercial_key = :commercial)
                GROUP BY customer_name
                HAVING SUM(unpaid_invoice_amount) > 0
                ORDER BY amount DESC
                LIMIT 8
                """;
        List<UnpaidCustomer> topCustomers = jdbc.query(topCustomersSql, params(query), (rs, rowNum) -> new UnpaidCustomer(
                rs.getString("customer_name"),
                money(rs.getBigDecimal("amount")),
                rs.getLong("unpaid_invoice_count")));

        String topCommercialsSql = """
                SELECT commercial_key,
                       commercial_name,
                       SUM(unpaid_invoice_amount) AS amount,
                       SUM(unpaid_invoice_count) AS unpaid_invoice_count
                FROM mart.mart_payment_status
                WHERE metric_date >= :from AND metric_date < :to
                  AND (:commercial IS NULL OR commercial_key = :commercial)
                GROUP BY commercial_key, commercial_name
                HAVING SUM(unpaid_invoice_amount) > 0
                ORDER BY amount DESC
                LIMIT 8
                """;
        List<UnpaidCommercial> topCommercials = jdbc.query(topCommercialsSql, params(query), (rs, rowNum) -> new UnpaidCommercial(
                rs.getString("commercial_name"),
                money(rs.getBigDecimal("amount")),
                rs.getLong("unpaid_invoice_count"),
                rs.getLong("commercial_key")));

        return new UnpaidExposure(unpaidAmount, unpaidRatePercent, topCustomers, topCommercials);
    }
}

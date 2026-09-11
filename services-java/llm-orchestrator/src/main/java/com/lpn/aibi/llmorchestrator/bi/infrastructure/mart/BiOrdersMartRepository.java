package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.CommercialOrderFlow;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.OrderFunnel;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.OrderKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.OrderTypeRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.StatusBreakdown;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse.YearlyPoint;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiOrdersMartRepository extends MartRepositorySupport {

    private static final String FLOW_FILTER = """
            metric_date >= :from AND metric_date < :to
            AND (:commercial IS NULL OR commercial_key = :commercial)
            AND (:documentType IS NULL OR c_doctype_id = :documentType OR document_type_key = :documentType)
            """;

    public BiOrdersMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
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
                WITH sales AS (
                    SELECT
                        COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_order_value,
                        COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_order_count,
                        COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_invoice_count,
                        COALESCE(SUM(ca_commande) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_order_value,
                        COALESCE(SUM(nombre_commandes) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_order_count,
                        COALESCE(SUM(invoice_count) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_invoice_count
                    FROM mart.mart_sales_daily
                    WHERE metric_date >= :prevFrom AND metric_date < :to
                ),
                flow AS (
                    SELECT
                        COALESCE(SUM(quantity_ordered) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_qty_ordered,
                        COALESCE(SUM(quantity_delivered) FILTER (WHERE metric_date >= :from AND metric_date < :to), 0) AS cur_qty_delivered,
                        COALESCE(SUM(quantity_ordered) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_qty_ordered,
                        COALESCE(SUM(quantity_delivered) FILTER (WHERE metric_date >= :prevFrom AND metric_date < :prevTo), 0) AS prev_qty_delivered
                    FROM mart.mart_order_to_invoice_flow
                    WHERE metric_date >= :prevFrom AND metric_date < :to
                )
                SELECT sales.*, flow.*
                FROM sales CROSS JOIN flow
                """;
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        return jdbc.queryForObject(sql, yearToDateParams(today), (rs, rowNum) -> List.of(
                new YearlyPoint(
                        currentYear - 1,
                        money(rs.getBigDecimal("prev_order_value")),
                        rs.getLong("prev_order_count"),
                        ratioPercent(rs.getBigDecimal("prev_invoice_count"), rs.getBigDecimal("prev_order_count")),
                        ratioPercent(rs.getBigDecimal("prev_qty_delivered"), rs.getBigDecimal("prev_qty_ordered"))),
                new YearlyPoint(
                        currentYear,
                        money(rs.getBigDecimal("cur_order_value")),
                        rs.getLong("cur_order_count"),
                        ratioPercent(rs.getBigDecimal("cur_invoice_count"), rs.getBigDecimal("cur_order_count")),
                        ratioPercent(rs.getBigDecimal("cur_qty_delivered"), rs.getBigDecimal("cur_qty_ordered")))));
    }

    public OrderKpis readKpis(BiQuery query) {
        // KPI order_count: total order documents in mart_order_to_invoice_flow for the selected period and filters.
        // KPI dominant_type_name: document type with the highest ordered revenue for the selected period and filters.
        // KPI invoice_coverage_percent: invoiced quantity divided by ordered quantity, expressed as a percentage.
        // KPI amount_invoice_coverage_percent: invoiced line revenue divided by ordered revenue, expressed as a percentage.
        // KPI status_count: number of distinct order statuses present for the selected period and filters.
        String sql = """
                WITH flow AS (
                    SELECT *
                    FROM mart.mart_order_to_invoice_flow
                    WHERE %s
                ),
                dominant AS (
                    SELECT document_type_name
                    FROM flow
                    GROUP BY document_type_name
                    ORDER BY COALESCE(SUM(ca_commande), 0) DESC, document_type_name
                    LIMIT 1
                )
                SELECT
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE((SELECT document_type_name FROM dominant), 'UNKNOWN') AS dominant_type_name,
                    CASE WHEN COALESCE(SUM(quantity_ordered), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(quantity_invoiced), 0) * 100 / NULLIF(SUM(quantity_ordered), 0), 2)
                    END AS invoice_coverage_percent,
                    CASE WHEN COALESCE(SUM(ca_commande), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_facture_ligne), 0) * 100 / NULLIF(SUM(ca_commande), 0), 2)
                    END AS amount_invoice_coverage_percent,
                    COALESCE(COUNT(DISTINCT doc_status), 0) AS status_count
                FROM flow
                """.formatted(FLOW_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new OrderKpis(
                rs.getLong("order_count"),
                rs.getString("dominant_type_name"),
                money(rs.getBigDecimal("invoice_coverage_percent")),
                money(rs.getBigDecimal("amount_invoice_coverage_percent")),
                rs.getLong("status_count")));
    }

    /**
     * Ordered → delivered → invoiced conversion funnel over
     * {@code mart_order_to_invoice_flow} for the selected window and the optional
     * :commercial / :documentType filters (same {@link #FLOW_FILTER} the other
     * order reads use). The raw quantities are summed in SQL; the delivery and
     * invoice coverage percents (each relative to the ordered quantity) are then
     * derived with the shared {@code ratioPercent} helper.
     */
    public OrderFunnel readFunnel(BiQuery query) {
        String sql = """
                SELECT
                    COALESCE(SUM(quantity_ordered), 0)   AS quantity_ordered,
                    COALESCE(SUM(quantity_delivered), 0) AS quantity_delivered,
                    COALESCE(SUM(quantity_invoiced), 0)  AS quantity_invoiced
                FROM mart.mart_order_to_invoice_flow
                WHERE %s
                """.formatted(FLOW_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> {
            BigDecimal ordered = money(rs.getBigDecimal("quantity_ordered"));
            BigDecimal delivered = money(rs.getBigDecimal("quantity_delivered"));
            BigDecimal invoiced = money(rs.getBigDecimal("quantity_invoiced"));
            return new OrderFunnel(
                    ordered,
                    delivered,
                    invoiced,
                    ratioPercent(delivered, ordered),
                    ratioPercent(invoiced, ordered));
        });
    }

    public List<OrderTypeRank> readByType(BiQuery query) {
        String sql = """
                SELECT
                    c_doctype_id AS order_type_id,
                    document_type_name AS order_type_name,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales
                FROM mart.mart_order_to_invoice_flow
                WHERE %s
                GROUP BY c_doctype_id, document_type_name
                ORDER BY ordered_sales DESC, order_type_name
                LIMIT :limit OFFSET :offset
                """.formatted(FLOW_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new OrderTypeRank(
                rs.getLong("order_type_id"),
                rs.getString("order_type_name"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales"))));
    }

    public List<CommercialOrderFlow> readCommercialFlow(BiQuery query) {
        String sql = """
                WITH commercial AS (
                    SELECT
                        ad_user_id,
                        commercial_name,
                        COALESCE(SUM(nombre_commandes), 0) AS order_count,
                        COALESCE(SUM(ca_commande), 0) AS ordered_sales
                    FROM mart.mart_sales_by_commercial
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:commercial IS NULL OR commercial_key = :commercial OR ad_user_id = :commercial)
                    GROUP BY ad_user_id, commercial_name
                ),
                flow AS (
                    SELECT
                        commercial_key,
                        CASE WHEN COALESCE(SUM(quantity_ordered), 0) = 0 THEN 0
                             ELSE ROUND(COALESCE(SUM(quantity_delivered), 0) * 100 / NULLIF(SUM(quantity_ordered), 0), 2)
                        END AS delivered_coverage_percent,
                        CASE WHEN COALESCE(SUM(quantity_ordered), 0) = 0 THEN 0
                             ELSE ROUND(COALESCE(SUM(quantity_invoiced), 0) * 100 / NULLIF(SUM(quantity_ordered), 0), 2)
                        END AS invoice_coverage_percent
                    FROM mart.mart_order_to_invoice_flow
                    WHERE %s
                    GROUP BY commercial_key
                )
                SELECT
                    commercial.ad_user_id AS salesrep_id,
                    commercial.commercial_name AS commercial_label,
                    commercial.order_count,
                    commercial.ordered_sales,
                    COALESCE(flow.delivered_coverage_percent, 0) AS delivered_coverage_percent,
                    COALESCE(flow.invoice_coverage_percent, 0) AS invoice_coverage_percent
                FROM commercial
                LEFT JOIN flow ON flow.commercial_key = commercial.ad_user_id
                ORDER BY commercial.ordered_sales DESC, commercial.commercial_name
                LIMIT :limit OFFSET :offset
                """.formatted(FLOW_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CommercialOrderFlow(
                rs.getLong("salesrep_id"),
                rs.getString("commercial_label"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales")),
                money(rs.getBigDecimal("delivered_coverage_percent")),
                money(rs.getBigDecimal("invoice_coverage_percent"))));
    }

    public List<StatusBreakdown> readStatusBreakdown(BiQuery query) {
        String sql = """
                SELECT
                    doc_status AS status,
                    doc_status AS label,
                    COALESCE(SUM(nombre_commandes), 0) AS item_count,
                    COALESCE(SUM(ca_commande), 0) AS total_value
                FROM mart.mart_order_to_invoice_flow
                WHERE %s
                GROUP BY doc_status
                ORDER BY item_count DESC, status
                """.formatted(FLOW_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new StatusBreakdown(
                rs.getString("status"),
                rs.getString("label"),
                rs.getLong("item_count"),
                money(rs.getBigDecimal("total_value"))));
    }
}

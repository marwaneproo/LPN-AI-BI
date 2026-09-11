package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.CommercialConversion;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.CommercialKpis;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.CommercialSalesRank;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.CommercialTerrainSignals;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.CustomerSignal;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.DrillKeys;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse.ZoneSignal;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@Repository
public class BiCommercialMartRepository extends MartRepositorySupport {

    private static final String COMMERCIAL_FILTER = """
            metric_date >= :from AND metric_date < :to
            AND (:commercial IS NULL OR commercial_key = :commercial OR ad_user_id = :commercial)
            """;

    public BiCommercialMartRepository(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public CommercialKpis readKpis(BiQuery query) {
        // KPI commercial_count: distinct commercials contributing sales for the selected period and filters.
        // KPI top_commercial_name: commercial with the highest invoiced revenue for the selected period and filters.
        // KPI conversion_percent: invoiced revenue divided by ordered revenue, expressed as a percentage.
        // KPI region_coverage_count: distinct regions covered by sales for the selected period and geography filters.
        String sql = """
                WITH commercial AS (
                    SELECT *
                    FROM mart.mart_sales_by_commercial
                    WHERE %s
                ),
                top_commercial AS (
                    SELECT commercial_name
                    FROM commercial
                    GROUP BY commercial_name
                    ORDER BY COALESCE(SUM(ca_facture), 0) DESC, commercial_name
                    LIMIT 1
                ),
                regions AS (
                    SELECT COALESCE(COUNT(DISTINCT region_name), 0) AS region_coverage_count
                    FROM mart.mart_sales_by_region
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:region IS NULL OR region_name = :region)
                      AND (:city IS NULL OR city_name = :city)
                ),
                commercial_totals AS (
                    SELECT
                        COALESCE(COUNT(DISTINCT NULLIF(commercial_key, 0)), 0) AS commercial_count,
                        CASE WHEN COALESCE(SUM(ca_commande), 0) = 0 THEN 0
                             ELSE ROUND(COALESCE(SUM(ca_facture), 0) * 100 / NULLIF(SUM(ca_commande), 0), 2)
                        END AS conversion_percent
                    FROM commercial
                )
                SELECT
                    commercial_totals.commercial_count,
                    COALESCE((SELECT commercial_name FROM top_commercial), 'UNKNOWN') AS top_commercial_name,
                    commercial_totals.conversion_percent,
                    regions.region_coverage_count
                FROM commercial_totals CROSS JOIN regions
                """.formatted(COMMERCIAL_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new CommercialKpis(
                rs.getLong("commercial_count"),
                rs.getString("top_commercial_name"),
                money(rs.getBigDecimal("conversion_percent")),
                rs.getLong("region_coverage_count")));
    }

    public List<CommercialSalesRank> readRevenueByCommercial(BiQuery query) {
        String sql = """
                SELECT
                    ad_user_id AS salesrep_id,
                    commercial_name AS commercial_label,
                    salesrep_email,
                    COALESCE(SUM(nombre_commandes), 0) AS order_count,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(invoice_count), 0) AS invoice_count,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    CASE WHEN COALESCE(SUM(ca_commande), 0) = 0 THEN 0
                         ELSE ROUND(COALESCE(SUM(ca_facture), 0) * 100 / NULLIF(SUM(ca_commande), 0), 2)
                    END AS invoice_coverage_percent
                FROM mart.mart_sales_by_commercial
                WHERE %s
                GROUP BY ad_user_id, commercial_name, salesrep_email
                ORDER BY invoiced_sales DESC, commercial_label
                LIMIT :limit OFFSET :offset
                """.formatted(COMMERCIAL_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CommercialSalesRank(
                rs.getLong("salesrep_id"),
                rs.getString("commercial_label"),
                rs.getString("salesrep_email"),
                rs.getLong("order_count"),
                money(rs.getBigDecimal("ordered_sales")),
                rs.getLong("invoice_count"),
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("invoice_coverage_percent"))));
    }

    public List<CommercialConversion> readConversion(BiQuery query) {
        String sql = """
                SELECT
                    commercial_name AS commercial_label,
                    COALESCE(SUM(ca_commande), 0) AS ordered_sales,
                    COALESCE(SUM(ca_facture), 0) AS invoiced_sales,
                    COALESCE(SUM(invoice_gap_amount), 0) AS gap
                FROM mart.mart_sales_by_commercial
                WHERE %s
                GROUP BY commercial_name
                ORDER BY ABS(COALESCE(SUM(invoice_gap_amount), 0)) DESC, commercial_name
                LIMIT :limit OFFSET :offset
                """.formatted(COMMERCIAL_FILTER);
        return jdbc.query(sql, params(query), (rs, rowNum) -> new CommercialConversion(
                rs.getString("commercial_label"),
                money(rs.getBigDecimal("ordered_sales")),
                money(rs.getBigDecimal("invoiced_sales")),
                money(rs.getBigDecimal("gap"))));
    }

    public CommercialTerrainSignals readTerrain(BiQuery query) {
        String sql = """
                WITH zone AS (
                    SELECT geography_key, city_name, region_name, COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_by_region
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:region IS NULL OR region_name = :region)
                      AND (:city IS NULL OR city_name = :city)
                    GROUP BY geography_key, city_name, region_name
                    ORDER BY invoiced_sales DESC, region_name, city_name
                    LIMIT 1
                ),
                client AS (
                    SELECT customer_key, customer_name, COALESCE(SUM(ca_facture), 0) AS invoiced_sales
                    FROM mart.mart_sales_by_customer
                    WHERE metric_date >= :from AND metric_date < :to
                      AND (:commercial IS NULL OR commercial_key = :commercial)
                    GROUP BY customer_key, customer_name
                    ORDER BY invoiced_sales DESC, customer_name
                    LIMIT 1
                ),
                commercial AS (
                    SELECT commercial_key
                    FROM mart.mart_sales_by_commercial
                    WHERE %s
                    GROUP BY commercial_key
                    ORDER BY COALESCE(SUM(ca_facture), 0) DESC
                    LIMIT 1
                )
                SELECT
                    COALESCE(zone.city_name, 'UNKNOWN') AS city_name,
                    COALESCE(zone.region_name, 'UNKNOWN') AS region_name,
                    COALESCE(zone.invoiced_sales, 0) AS zone_invoiced_sales,
                    COALESCE(client.customer_name, 'UNKNOWN') AS customer_name,
                    COALESCE(client.invoiced_sales, 0) AS customer_invoiced_sales,
                    COALESCE(commercial.commercial_key, 0) AS commercial_key,
                    COALESCE(client.customer_key, 0) AS customer_key,
                    COALESCE(zone.geography_key, 0) AS geography_key
                FROM zone FULL OUTER JOIN client ON true FULL OUTER JOIN commercial ON true
                LIMIT 1
                """.formatted(COMMERCIAL_FILTER);
        return jdbc.queryForObject(sql, params(query), (rs, rowNum) -> new CommercialTerrainSignals(
                new ZoneSignal(
                        rs.getString("city_name"),
                        rs.getString("region_name"),
                        money(rs.getBigDecimal("zone_invoiced_sales"))),
                new CustomerSignal(
                        rs.getString("customer_name"),
                        money(rs.getBigDecimal("customer_invoiced_sales"))),
                new DrillKeys(
                        rs.getLong("commercial_key"),
                        rs.getLong("customer_key"),
                        rs.getLong("geography_key"))));
    }
}

package com.lpn.aibi.llmorchestrator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "bi.datasource.readonly.url=jdbc:postgresql://localhost:5432/lpn_ai_bi?options=-c%20statement_timeout%3D30000%20-c%20search_path%3Dmart",
        "bi.datasource.readonly.username=lpn_ai_readonly",
        "bi.datasource.readonly.password=change_me_ai_readonly",
        "bi.datasource.readonly.query-timeout-seconds=30",
        "auth.api-protection-enabled=false"
})
@AutoConfigureMockMvc
class BiOverviewControllerTest {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PredictiveClient predictiveClient;

    @MockBean
    private SchemaRetrievalClient schemaRetrievalClient;

    @MockBean
    private SqlExecutorClient sqlExecutorClient;

    @MockBean
    @Qualifier("sqlGenerationModel")
    private ChatModel sqlGenerationModel;

    @MockBean
    @Qualifier("sqlGenerationFallbackModel")
    private ChatModel sqlGenerationFallbackModel;

    @MockBean
    @Qualifier("sqlGenerationReasoningModel")
    private ChatModel sqlGenerationReasoningModel;

    @MockBean
    @Qualifier("narratorModel")
    private ChatModel narratorModel;

    @MockBean
    private PerformanceTraceRepository performanceTraceRepository;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthRepository authRepository;

    @MockBean
    private ChatSessionRepository chatSessionRepository;

    // Every request pins an explicit period (May 2026) so the assertions stay
    // stable over time — the default window is "previous calendar month", which
    // drifts with the clock and made these tests expire.

    @Test
    void overviewReadsPinnedMonthOrderCountFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/overview").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.order_count").value(616))
                .andExpect(jsonPath("$.meta.applied_filters.from").value("2026-05-01"))
                .andExpect(jsonPath("$.meta.applied_filters.to").value("2026-05-31"));
    }

    @Test
    void overviewReactsToTheSelectedPeriod() throws Exception {
        // A different window must produce different KPIs, a trend bounded by the
        // window, and a two-point year-over-year comparison for the window.
        mockMvc.perform(get("/v1/bi/overview").param("from", "2026-01-01").param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.order_count").value(2524))
                .andExpect(jsonPath("$.data.trend.length()").value(3))
                .andExpect(jsonPath("$.data.trend[0].period").value("2026-01"))
                .andExpect(jsonPath("$.data.trend[2].period").value("2026-03"))
                .andExpect(jsonPath("$.data.yearly.length()").value(2))
                .andExpect(jsonPath("$.data.yearly[0].year").value(2025))
                .andExpect(jsonPath("$.data.yearly[1].year").value(2026));
    }

    @Test
    void overviewCompareTrendOverlaysThePreviousYearOnTheCurrentLabels() throws Exception {
        // compare=true reads 2025-01-01→2025-03-31 but re-labels each point with the
        // current window's period, so the frontend can overlay by X value.
        mockMvc.perform(get("/v1/bi/overview")
                        .param("from", "2026-01-01").param("to", "2026-03-31").param("compare", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compare_trend.length()").value(3))
                .andExpect(jsonPath("$.data.compare_trend[0].period").value("2026-01"))
                .andExpect(jsonPath("$.data.compare_trend[1].period").value("2026-02"))
                .andExpect(jsonPath("$.data.compare_trend[2].period").value("2026-03"))
                .andExpect(jsonPath("$.data.compare_trend[0].order_count").value(1011))
                .andExpect(jsonPath("$.data.compare_trend[0].invoiced_sales").value(2402879.91))
                .andExpect(jsonPath("$.data.trend.length()").value(3));
    }

    @Test
    void overviewCompareTrendIsEmptyWhenCompareIsOff() throws Exception {
        mockMvc.perform(get("/v1/bi/overview")
                        .param("from", "2026-01-01").param("to", "2026-03-31").param("compare", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compare_trend.length()").value(0));

        mockMvc.perform(get("/v1/bi/overview").param("from", "2026-01-01").param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compare_trend.length()").value(0));
    }

    @Test
    void overviewTrendSupportsDayGranularity() throws Exception {
        mockMvc.perform(get("/v1/bi/overview")
                        .param("from", "2026-03-01").param("to", "2026-03-31").param("granularity", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend[0].period").value(containsString("2026-03-")));
    }

    @Test
    void ordersReadsPinnedMonthOrderCountFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/orders").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.order_count").value(616))
                .andExpect(jsonPath("$.data.by_type").isArray())
                .andExpect(jsonPath("$.data.status_breakdown").isArray())
                .andExpect(jsonPath("$.data.yearly.length()").value(2))
                .andExpect(jsonPath("$.data.yearly[1].year").value(2026));
    }

    @Test
    void ordersFunnelIsMonotonicWithBoundedCoverage() throws Exception {
        // Ordered → delivered → invoiced conversion funnel over a pinned window
        // (May 2026): quantities must be monotonically non-increasing and >= 0, and
        // both coverage percents must stay within [0, 100].
        mockMvc.perform(get("/v1/bi/orders").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.quantity_ordered").value(31480.0))
                .andExpect(jsonPath("$.data.funnel.quantity_delivered").value(23934.0))
                .andExpect(jsonPath("$.data.funnel.quantity_invoiced").value(18933.0))
                .andExpect(jsonPath("$.data.funnel.delivery_coverage_percent").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.data.funnel.invoice_coverage_percent").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.data.funnel.delivery_coverage_percent").value(76.03))
                .andExpect(jsonPath("$.data.funnel.invoice_coverage_percent").value(60.14));
    }

    @Test
    void revenueReadsPinnedMonthInvoicedSalesFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/revenue").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.invoiced_sales").value(2848655.68))
                .andExpect(jsonPath("$.data.trend").isArray())
                .andExpect(jsonPath("$.data.comparison.length()").value(2))
                .andExpect(jsonPath("$.data.comparison[0].year").value(2025))
                .andExpect(jsonPath("$.data.comparison[1].year").value(2026));
    }

    @Test
    void revenueCompareTrendFollowsTheSameLabelAlignment() throws Exception {
        mockMvc.perform(get("/v1/bi/revenue")
                        .param("from", "2026-01-01").param("to", "2026-03-31").param("compare", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compare_trend.length()").value(3))
                .andExpect(jsonPath("$.data.compare_trend[0].period").value("2026-01"))
                .andExpect(jsonPath("$.data.compare_trend[0].ordered_sales").value(8799064.77));

        mockMvc.perform(get("/v1/bi/revenue").param("from", "2026-01-01").param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compare_trend.length()").value(0));
    }

    @Test
    void revenueTrendIsNotTruncatedByPagination() throws Exception {
        // 2025 has 12 full months; the old LIMIT :limit default of 10 cut this to 10 points.
        mockMvc.perform(get("/v1/bi/revenue").param("from", "2025-01-01").param("to", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend.length()").value(12));
    }

    @Test
    void revenueUnpaidExposureTotalsAreBoundedAndRankedDescending() throws Exception {
        // Unpaid-invoice exposure over a pinned window (2026-01 -> 2026-06): totals must
        // be non-negative, the rate a valid percentage, and both top-8 lists ranked by
        // amount descending. Verified independently against mart.mart_payment_status in psql.
        String body = mockMvc.perform(get("/v1/bi/revenue").param("from", "2026-01-01").param("to", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unpaid.unpaid_amount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.data.unpaid.unpaid_rate_percent").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.data.unpaid.unpaid_amount").value(14774383.04))
                .andExpect(jsonPath("$.data.unpaid.unpaid_rate_percent").value(74.43))
                .andExpect(jsonPath("$.data.unpaid.top_customers.length()").value(8))
                .andExpect(jsonPath("$.data.unpaid.top_customers[0].customer_name").value("L'Avenir Du Livre Siel 2026"))
                .andExpect(jsonPath("$.data.unpaid.top_customers[0].amount").value(1142825.63))
                .andExpect(jsonPath("$.data.unpaid.top_commercials.length()").value(8))
                .andExpect(jsonPath("$.data.unpaid.top_commercials[0].commercial_name").value("Afekkak"))
                .andExpect(jsonPath("$.data.unpaid.top_commercials[0].amount").value(6219985.88))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Double> customerAmounts = com.jayway.jsonpath.JsonPath.read(body, "$.data.unpaid.top_customers[*].amount");
        List<Double> commercialAmounts = com.jayway.jsonpath.JsonPath.read(body, "$.data.unpaid.top_commercials[*].amount");
        assertThat(customerAmounts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(commercialAmounts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void revenueCommercialFilterAppliesToEveryReading() throws Exception {
        // Click-to-filter by commercial: with commercial=1005682 (Afekkak) every
        // revenue reading must be restricted to him. Values sanity-checked in
        // psql against mart_sales_by_commercial / mart_payment_status /
        // mart_order_to_invoice_flow for 2026-01-01 → 2026-06-30 (+1d exclusive).
        mockMvc.perform(get("/v1/bi/revenue")
                        .param("from", "2026-01-01").param("to", "2026-06-30")
                        .param("commercial", "1005682"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.invoiced_sales").value(6662951.85))
                .andExpect(jsonPath("$.data.kpis.ordered_sales").value(7738962.90))
                .andExpect(jsonPath("$.data.kpis.invoice_gap_amount").value(-1076011.05))
                .andExpect(jsonPath("$.data.unpaid.unpaid_amount").value(6227999.68))
                .andExpect(jsonPath("$.data.unpaid.unpaid_rate_percent").value(93.47))
                .andExpect(jsonPath("$.data.unpaid.top_customers[0].customer_name").value("L'Avenir Du Livre Siel 2026"))
                .andExpect(jsonPath("$.data.unpaid.top_customers[0].amount").value(1142825.63))
                .andExpect(jsonPath("$.data.unpaid.top_commercials.length()").value(1))
                .andExpect(jsonPath("$.data.unpaid.top_commercials[0].commercial_name").value("Afekkak"))
                .andExpect(jsonPath("$.data.unpaid.top_commercials[0].salesrep_id").value(1005682))
                .andExpect(jsonPath("$.data.revenue_status.a_livrer").value(15147.0))
                .andExpect(jsonPath("$.data.yearly.length()").value(3));
    }

    @Test
    void revenueYearlyComparesYearsAtTheSameElapsedSpan() throws Exception {
        // readYearly() sums every year Jan 1 → the day-of-year of the newest mart
        // data (2026-06-15 in the pinned dataset), so past years are cut at the
        // same point as the year in progress instead of showing full-year totals.
        // Values sanity-checked directly in psql against mart.mart_sales_daily.
        mockMvc.perform(get("/v1/bi/revenue").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearly.length()").value(3))
                .andExpect(jsonPath("$.data.yearly[0].year").value(2024))
                .andExpect(jsonPath("$.data.yearly[0].ca_facture").value(20656398.91))
                .andExpect(jsonPath("$.data.yearly[0].ca_commande").value(43464439.78))
                .andExpect(jsonPath("$.data.yearly[0].partial").value(false))
                .andExpect(jsonPath("$.data.yearly[1].year").value(2025))
                .andExpect(jsonPath("$.data.yearly[1].ca_facture").value(23687245.29))
                .andExpect(jsonPath("$.data.yearly[1].partial").value(false))
                .andExpect(jsonPath("$.data.yearly[2].year").value(2026))
                .andExpect(jsonPath("$.data.yearly[2].ca_facture").value(19957297.94))
                .andExpect(jsonPath("$.data.yearly[2].partial").value(true));
    }

    @Test
    void articlesReadsPinnedMonthActiveProductsFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/articles").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.active_products").value(4336))
                .andExpect(jsonPath("$.data.top_articles").isArray())
                .andExpect(jsonPath("$.data.stock_priorities").isArray())
                .andExpect(jsonPath("$.data.yearly.length()").value(2));
    }

    @Test
    void articlesConcentrationAndCumulativeShareStayWithinBounds() throws Exception {
        // Revenue-concentration (Pareto): top10_share_percent is a share of a whole,
        // and the cumulative share of the last ranked product can never exceed it.
        mockMvc.perform(get("/v1/bi/articles").param("from", "2026-01-01").param("to", "2026-05-31").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concentration.top10_share_percent").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.data.concentration.total_products").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.top_articles[49].cumulative_share_percent")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(100.0)));
    }

    @Test
    void clientsReadsPinnedMonthActiveCustomersFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/clients").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.active_customers").value(175))
                .andExpect(jsonPath("$.data.top_clients").isArray())
                .andExpect(jsonPath("$.data.finance_risks").isArray())
                .andExpect(jsonPath("$.data.yearly.length()").value(2));
    }

    @Test
    void clientsConcentrationAndCumulativeShareStayWithinBounds() throws Exception {
        // Revenue-concentration (Pareto): top10_share_percent is a share of a whole,
        // and the cumulative share of the last ranked customer can never exceed it.
        mockMvc.perform(get("/v1/bi/clients").param("from", "2026-01-01").param("to", "2026-05-31").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concentration.top10_share_percent").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$.data.concentration.total_customers").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.top_clients[49].cumulative_share_percent")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(100.0)));
    }

    @Test
    void commercialReadsPinnedMonthCommercialCountFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/commercial").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.commercial_count").value(15))
                .andExpect(jsonPath("$.data.revenue_by_commercial").isArray())
                .andExpect(jsonPath("$.data.conversion").isArray());
    }

    @Test
    void analysisReadsPinnedMonthOrderCountFromMart() throws Exception {
        mockMvc.perform(get("/v1/bi/analysis").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.order_count").value(616))
                .andExpect(jsonPath("$.data.order_type_sales").isArray())
                .andExpect(jsonPath("$.data.distributor_sales").isArray());
    }

    @Test
    void overviewExportStreamsRealOoxmlWorkbook() throws Exception {
        MvcResult asyncResult = mockMvc.perform(get("/v1/bi/export/overview").param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] workbook = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("lpn-bi-overview-2026-05-01-2026-05-31.xlsx")))
                .andExpect(header().string("X-BI-Export-Page", "overview"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> parts = unzip(workbook);
        assertThat(parts).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml");
        assertThat(parts.get("xl/workbook.xml")).contains("Context").contains("KPI");
        assertThat(parts.get("xl/worksheets/sheet2.xml")).contains("order_count").contains("<v>616</v>");
    }

    private static Map<String, String> unzip(byte[] workbook) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }
}

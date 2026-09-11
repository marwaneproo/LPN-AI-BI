package com.lpn.aibi.llmorchestrator.bi.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiClientsResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiCommercialResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.OverviewData;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.export.BiOoxmlWorkbookWriter;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.export.BiOoxmlWorkbookWriter.Sheet;

@Service
public class BiExportService {

    private final BiOverviewService overviewService;
    private final BiOrdersService ordersService;
    private final BiRevenueService revenueService;
    private final BiArticlesService articlesService;
    private final BiClientsService clientsService;
    private final BiCommercialService commercialService;
    private final BiAnalysisService analysisService;
    private final BiOoxmlWorkbookWriter writer;

    public BiExportService(
            BiOverviewService overviewService,
            BiOrdersService ordersService,
            BiRevenueService revenueService,
            BiArticlesService articlesService,
            BiClientsService clientsService,
            BiCommercialService commercialService,
            BiAnalysisService analysisService,
            BiOoxmlWorkbookWriter writer) {
        this.overviewService = overviewService;
        this.ordersService = ordersService;
        this.revenueService = revenueService;
        this.articlesService = articlesService;
        this.clientsService = clientsService;
        this.commercialService = commercialService;
        this.analysisService = analysisService;
        this.writer = writer;
    }

    public BiExportWorkbook export(String pageSlug, BiQuery query) {
        ExportPage page = ExportPage.fromSlug(pageSlug);
        Instant generatedAt = Instant.now();
        ExportPayload payload = payload(page, query);
        List<Sheet> sheets = prependContext(page, generatedAt, payload.appliedFilters(), payload.sourceMarts(), payload.sheets());
        String filename = "lpn-bi-%s-%s-%s.xlsx".formatted(page.slug(), query.fromInclusive(), query.toInclusive());
        return new BiExportWorkbook(writer.write(sheets), filename, generatedAt);
    }

    private ExportPayload payload(ExportPage page, BiQuery query) {
        return switch (page) {
            case OVERVIEW -> overviewPayload(query);
            case ORDERS -> ordersPayload(query);
            case REVENUE -> revenuePayload(query);
            case ARTICLES -> articlesPayload(query);
            case CLIENTS -> clientsPayload(query);
            case COMMERCIAL -> commercialPayload(query);
            case ANALYSIS -> analysisPayload(query);
        };
    }

    private ExportPayload overviewPayload(BiQuery query) {
        BiOverviewService.OverviewResult result = overviewService.getOverview(
                query.fromInclusive().toString(),
                query.toInclusive().toString(),
                query.granularity(),
                query.compare());
        OverviewData data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_daily, mart_sales_monthly, mart_sales_by_commercial, mart_sales_by_product, mart_sales_by_region, mart_sales_by_customer, mart_order_to_invoice_flow, mart_payment_status",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Tendance", data.trend()),
                        writer.sheet("Signaux", data.quickSignals()),
                        writer.sheet("Mix ventes", data.salesMix()),
                        writer.sheet("Top clients", data.topCustomers()),
                        writer.sheet("Top produits", data.topProducts()),
                        writer.sheet("Statuts commandes", data.orderStatuses()),
                        writer.sheet("Statuts factures", data.invoiceStatuses())));
    }

    private ExportPayload ordersPayload(BiQuery query) {
        BiPageResult<BiOrdersResponse> result = ordersService.getOrders(query);
        BiOrdersResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_order_to_invoice_flow, mart_sales_by_commercial",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Par type", data.byType()),
                        writer.sheet("Flux commercial", data.commercialFlow()),
                        writer.sheet("Statuts", data.statusBreakdown())));
    }

    private ExportPayload revenuePayload(BiQuery query) {
        BiPageResult<BiRevenueResponse> result = revenueService.getRevenue(query);
        BiRevenueResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_daily, mart_sales_monthly, mart_payment_status, mart_order_to_invoice_flow",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Tendance CA", data.trend()),
                        writer.sheet("Lecture responsable", data.responsibleReading()),
                        writer.sheet("Statuts CA", data.revenueStatus())));
    }

    private ExportPayload articlesPayload(BiQuery query) {
        BiPageResult<BiArticlesResponse> result = articlesService.getArticles(query);
        BiArticlesResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_by_product, mart_stock_risk",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Top articles", data.topArticles()),
                        writer.sheet("Mix categories", data.mixArticle().categories()),
                        writer.sheet("Mix themes", data.mixArticle().themes()),
                        writer.sheet("Mix collections", data.mixArticle().collections()),
                        writer.sheet("Priorites stock", data.stockPriorities())));
    }

    private ExportPayload clientsPayload(BiQuery query) {
        BiPageResult<BiClientsResponse> result = clientsService.getClients(query);
        BiClientsResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_by_customer, mart_sales_by_product, mart_sales_by_region, mart_payment_status",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Top clients", data.topClients()),
                        writer.sheet("Par commercial", data.byCommercial()),
                        writer.sheet("Par article", data.byArticle()),
                        writer.sheet("Geographie", data.byRegion()),
                        writer.sheet("Risque finance", data.financeRisks())));
    }

    private ExportPayload commercialPayload(BiQuery query) {
        BiPageResult<BiCommercialResponse> result = commercialService.getCommercial(query);
        BiCommercialResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_by_commercial, mart_sales_by_region, mart_sales_by_customer",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("CA commerciaux", data.revenueByCommercial()),
                        writer.sheet("Conversion", data.conversion()),
                        writer.sheet("Terrain", data.terrain())));
    }

    private ExportPayload analysisPayload(BiQuery query) {
        BiPageResult<BiAnalysisResponse> result = analysisService.getAnalysis(query);
        BiAnalysisResponse data = result.data();
        return new ExportPayload(
                result.appliedFilters(),
                "mart_sales_daily, mart_sales_monthly, mart_order_to_invoice_flow, mart_sales_by_commercial, mart_sales_by_product, mart_sales_by_region, mart_stock_risk, mart_sales_by_customer",
                List.of(
                        writer.sheet("KPI", data.kpis()),
                        writer.sheet("Tendance", data.trend()),
                        writer.sheet("Types commandes", data.orderTypeSales()),
                        writer.sheet("Commerciaux", data.commercialSales()),
                        writer.sheet("Categories", data.categorySales()),
                        writer.sheet("Themes", data.themeSales()),
                        writer.sheet("Fournisseurs", data.supplierSales()),
                        writer.sheet("Distributeurs", data.distributorSales()),
                        writer.sheet("Geographie", data.geographySales()),
                        writer.sheet("Disponibilite", data.availabilityRisks()),
                        writer.sheet("Top produits", data.topProducts()),
                        writer.sheet("Top clients", data.topCustomers()),
                        writer.rowsSheet("Options filtres", filterOptionRows(data))));
    }

    private List<List<Object>> filterOptionRows(BiAnalysisResponse data) {
        List<List<Object>> rows = new java.util.ArrayList<>();
        rows.add(List.of("groupe", "id", "label", "item_count", "total_value"));
        appendOptions(rows, "commercial", data.commercialOptions());
        appendOptions(rows, "type_commande", data.orderTypeOptions());
        appendOptions(rows, "categorie", data.categoryOptions());
        appendOptions(rows, "theme", data.themeOptions());
        appendOptions(rows, "fournisseur", data.supplierOptions());
        appendOptions(rows, "distributeur", data.distributorOptions());
        return rows;
    }

    private void appendOptions(List<List<Object>> rows, String group, List<BiAnalysisResponse.FilterOption> options) {
        for (BiAnalysisResponse.FilterOption option : options) {
            rows.add(List.of(group, option.id(), option.label(), option.itemCount(), option.totalValue()));
        }
    }

    private List<Sheet> prependContext(ExportPage page, Instant generatedAt, Map<String, Object> filters, String sourceMarts, List<Sheet> sheets) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("Page", page.slug());
        context.put("Generated at", generatedAt.toString());
        context.put("Filters", filters.toString());
        context.put("Source marts", sourceMarts);
        context.put("Contract version", "BI-10 / BI-13");
        context.put("Data caveat", "Exported from mart views; PNG/PDF remain visual frontend captures.");
        List<Sheet> allSheets = new java.util.ArrayList<>();
        allSheets.add(writer.contextSheet(context));
        allSheets.addAll(sheets);
        return allSheets;
    }

    private enum ExportPage {
        OVERVIEW("overview"),
        ORDERS("orders"),
        REVENUE("revenue"),
        ARTICLES("articles"),
        CLIENTS("clients"),
        COMMERCIAL("commercial"),
        ANALYSIS("analysis");

        private final String slug;

        ExportPage(String slug) {
            this.slug = slug;
        }

        String slug() {
            return slug;
        }

        static ExportPage fromSlug(String value) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            for (ExportPage page : values()) {
                if (page.slug.equals(normalized)) {
                    return page;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported BI export page: " + value);
        }
    }

    private record ExportPayload(
            Map<String, Object> appliedFilters,
            String sourceMarts,
            List<Sheet> sheets) {
    }
}

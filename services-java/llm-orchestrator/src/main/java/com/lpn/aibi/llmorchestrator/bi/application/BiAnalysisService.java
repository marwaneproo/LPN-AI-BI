package com.lpn.aibi.llmorchestrator.bi.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.FilterOption;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAnalysisResponse.SupplierSales;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiAnalysisMartRepository;

@Service
public class BiAnalysisService {

    private final BiAnalysisMartRepository repository;

    public BiAnalysisService(BiAnalysisMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiAnalysisResponse> getAnalysis(BiQuery query) {
        List<SupplierSales> supplierSales = repository.readSupplierSales(query);
        List<FilterOption> supplierOptions = repository.readFilterOptions(
                query,
                "supplier_id",
                "supplier_name",
                "ca_facture",
                "mart_sales_by_product");
        BiAnalysisResponse data = new BiAnalysisResponse(
                repository.readKpis(query),
                repository.readTrend(query),
                repository.readOrderTypeSales(query),
                repository.readCommercialSales(query),
                repository.readCategorySales(query),
                repository.readThemeSales(query),
                supplierSales,
                supplierSales,
                repository.readGeographySales(query),
                repository.readAvailabilityRisks(query),
                repository.readTopProducts(query),
                repository.readTopCustomers(query),
                repository.readFilterOptions(query, "ad_user_id", "commercial_name", "ca_facture", "mart_sales_by_commercial"),
                repository.readFilterOptions(query, "c_doctype_id", "document_type_name", "ca_commande", "mart_order_to_invoice_flow"),
                repository.readFilterOptions(query, "product_category_key", "category_name", "ca_facture", "mart_sales_by_product"),
                repository.readFilterOptions(query, "theme_name", "theme_name", "ca_facture", "mart_sales_by_product"),
                supplierOptions,
                supplierOptions);
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.trend().size(), false));
    }
}

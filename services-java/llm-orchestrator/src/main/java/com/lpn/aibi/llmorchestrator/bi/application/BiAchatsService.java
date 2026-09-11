package com.lpn.aibi.llmorchestrator.bi.application;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiAchatsMartRepository;

@Service
public class BiAchatsService {

    private static final int TOP_LIMIT = 8;

    /**
     * See {@link BiAchatsResponse} for why this page reports "CA attribué au
     * fournisseur d'origine" instead of real purchase-order KPIs: the source
     * database has no purchase-order / purchase-invoice transactions.
     */
    private static final String METHODOLOGY_NOTE =
            "Aucune commande ou facture fournisseur reelle n'est presente dans la base de donnees "
                    + "(business.c_order et business.c_invoice ne contiennent que des documents de vente). "
                    + "Les indicateurs ci-dessous sont donc calcules a partir du chiffre d'affaires des ventes "
                    + "rattache au fournisseur d'origine declare de chaque produit (proxy), et du catalogue "
                    + "fournisseur (business.m_product_po). L'OTIF et les economies realisees ne sont pas "
                    + "affiches: les colonnes correspondantes de la base ne contiennent aucune donnee exploitable.";

    private final BiAchatsMartRepository repository;

    public BiAchatsService(BiAchatsMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiAchatsResponse> getAchats(BiQuery query) {
        BiAchatsResponse data = new BiAchatsResponse(
                repository.readKpis(query),
                repository.readTrend(query),
                repository.readTopSuppliers(query, TOP_LIMIT),
                repository.readTopSupplierProducts(query, TOP_LIMIT),
                repository.readByCategory(query),
                repository.readCatalogSnapshot(),
                METHODOLOGY_NOTE);
        return new BiPageResult<>(
                data,
                query.appliedFilters(),
                new Pagination(TOP_LIMIT, 0, data.topSuppliers().size(), false));
    }
}

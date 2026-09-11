package com.lpn.aibi.llmorchestrator.bi.application;

import org.springframework.stereotype.Service;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse.ArticleMix;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope.Pagination;
import com.lpn.aibi.llmorchestrator.bi.infrastructure.mart.BiArticlesMartRepository;

@Service
public class BiArticlesService {

    private final BiArticlesMartRepository repository;

    public BiArticlesService(BiArticlesMartRepository repository) {
        this.repository = repository;
    }

    public BiPageResult<BiArticlesResponse> getArticles(BiQuery query) {
        BiArticlesResponse data = new BiArticlesResponse(
                repository.readKpis(query),
                repository.readConcentration(query),
                repository.readTopArticles(query),
                new ArticleMix(
                        repository.readMix(query, "category_name"),
                        repository.readMix(query, "theme_name"),
                        repository.readMix(query, "collection_name")),
                repository.readStockPriorities(query),
                repository.readYearToDateComparison());
        return new BiPageResult<>(data, query.appliedFilters(), new Pagination(query.limit(), query.offset(), data.topArticles().size(), false));
    }
}

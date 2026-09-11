package com.lpn.aibi.llmorchestrator.bi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiArticlesResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope;
import com.lpn.aibi.llmorchestrator.bi.application.BiArticlesService;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@RestController
@RequestMapping("/v1/bi")
class BiArticlesController {

    private final BiArticlesService service;

    BiArticlesController(BiArticlesService service) {
        this.service = service;
    }

    @GetMapping("/articles")
    BiEnvelope<BiArticlesResponse> articles(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare,
            @RequestParam(name = "category", required = false) Integer category,
            @RequestParam(name = "supplier", required = false) Integer supplier,
            @RequestParam(name = "customer", required = false) Integer customer,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset) {
        BiQuery query = BiQuery.of(from, to, granularity, compare, null, category, supplier, customer, null, region, null, null, limit, offset);
        return BiEndpointSupport.timed(() -> service.getArticles(query));
    }
}

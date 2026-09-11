package com.lpn.aibi.llmorchestrator.bi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOrdersResponse;
import com.lpn.aibi.llmorchestrator.bi.application.BiOrdersService;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@RestController
@RequestMapping("/v1/bi")
class BiOrdersController {

    private final BiOrdersService service;

    BiOrdersController(BiOrdersService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    BiEnvelope<BiOrdersResponse> orders(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare,
            @RequestParam(name = "commercial", required = false) Integer commercial,
            @RequestParam(name = "document_type", required = false) Integer documentType,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset) {
        BiQuery query = BiQuery.of(from, to, granularity, compare, commercial, null, null, null, documentType, region, null, null, limit, offset);
        return BiEndpointSupport.timed(() -> service.getOrders(query));
    }
}

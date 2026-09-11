package com.lpn.aibi.llmorchestrator.bi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiRevenueResponse;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;
import com.lpn.aibi.llmorchestrator.bi.application.BiRevenueService;

@RestController
@RequestMapping("/v1/bi")
class BiRevenueController {

    private final BiRevenueService service;

    BiRevenueController(BiRevenueService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    BiEnvelope<BiRevenueResponse> revenue(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare,
            @RequestParam(name = "commercial", required = false) Integer commercial,
            @RequestParam(name = "category", required = false) Integer category,
            @RequestParam(name = "supplier", required = false) Integer supplier,
            @RequestParam(name = "customer", required = false) Integer customer,
            @RequestParam(name = "document_type", required = false) Integer documentType,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "payment_status", required = false) String paymentStatus,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset) {
        BiQuery query = BiQuery.of(from, to, granularity, compare, commercial, category, supplier, customer, documentType, region, null, paymentStatus, limit, offset);
        return BiEndpointSupport.timed(() -> service.getRevenue(query));
    }
}

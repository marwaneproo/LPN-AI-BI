package com.lpn.aibi.llmorchestrator.bi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiAchatsResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiEnvelope;
import com.lpn.aibi.llmorchestrator.bi.application.BiAchatsService;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@RestController
@RequestMapping("/v1/bi")
class BiAchatsController {

    private final BiAchatsService service;

    BiAchatsController(BiAchatsService service) {
        this.service = service;
    }

    @GetMapping("/achats")
    BiEnvelope<BiAchatsResponse> achats(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare,
            @RequestParam(name = "supplier", required = false) Integer supplier,
            @RequestParam(name = "category", required = false) Integer category,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset) {
        BiQuery query = BiQuery.of(from, to, granularity, compare, null, category, supplier, null, null, null, null, null, limit, offset);
        return BiEndpointSupport.timed(() -> service.getAchats(query));
    }
}

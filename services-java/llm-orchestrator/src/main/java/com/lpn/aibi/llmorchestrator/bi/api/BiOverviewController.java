package com.lpn.aibi.llmorchestrator.bi.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.Meta;
import com.lpn.aibi.llmorchestrator.bi.api.dto.BiOverviewResponse.Pagination;
import com.lpn.aibi.llmorchestrator.bi.application.BiOverviewService;
import com.lpn.aibi.llmorchestrator.bi.application.BiOverviewService.OverviewResult;

@RestController
@RequestMapping("/v1/bi")
class BiOverviewController {

    private final BiOverviewService service;

    BiOverviewController(BiOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    BiOverviewResponse overview(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare) {
        long startedAt = System.nanoTime();
        OverviewResult result = service.getOverview(from, to, granularity, compare);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;

        return new BiOverviewResponse(
                new Meta(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        latencyMs,
                        result.appliedFilters(),
                        new Pagination(null, null, null, false)),
                result.data());
    }
}

package com.lpn.aibi.llmorchestrator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ForecastController {

    private final PredictiveClient predictiveClient;

    ForecastController(PredictiveClient predictiveClient) {
        this.predictiveClient = predictiveClient;
    }

    @GetMapping("/v1/forecast/ca")
    ResponseEntity<Object> forecastCa(
            @RequestParam(defaultValue = "company") String grain,
            @RequestParam(required = false) Integer key,
            @RequestParam(defaultValue = "6") int horizon) {
        return predictiveClient.forecastCa(grain, key, horizon);
    }

    @GetMapping("/v1/forecast/backtest")
    ResponseEntity<Object> forecastBacktest(
            @RequestParam(defaultValue = "company") String grain,
            @RequestParam(required = false) Integer key,
            @RequestParam(defaultValue = "6") int holdout) {
        return predictiveClient.forecastBacktest(grain, key, holdout);
    }

    @GetMapping("/v1/forecast/options")
    ResponseEntity<Object> forecastOptions(
            @RequestParam(defaultValue = "company") String grain) {
        return predictiveClient.forecastOptions(grain);
    }
}

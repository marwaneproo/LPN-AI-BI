package com.lpn.aibi.llmorchestrator;

import org.springframework.http.ResponseEntity;

interface PredictiveClient {

    ResponseEntity<Object> forecastCa(String grain, Integer key, int horizon);

    ResponseEntity<Object> forecastBacktest(String grain, Integer key, int holdout);

    ResponseEntity<Object> forecastOptions(String grain);
}

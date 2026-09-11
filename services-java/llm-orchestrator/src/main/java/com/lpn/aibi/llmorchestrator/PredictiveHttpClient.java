package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
class PredictiveHttpClient implements PredictiveClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    PredictiveHttpClient(
            @Value("${predictive.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Object> forecastCa(String grain, Integer key, int horizon) {
        return get(buildUri("/v1/forecast/ca", grain, key, "horizon", horizon));
    }

    @Override
    public ResponseEntity<Object> forecastBacktest(String grain, Integer key, int holdout) {
        return get(buildUri("/v1/forecast/backtest", grain, key, "holdout", holdout));
    }

    @Override
    public ResponseEntity<Object> forecastOptions(String grain) {
        return get(URI.create(baseUrl + "/v1/forecast/options?grain=" + grain));
    }

    private ResponseEntity<Object> get(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Object body = objectMapper.readValue(response.body(), Object.class);
            return ResponseEntity.status(response.statusCode()).body(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Predictive service request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Predictive service request interrupted", exception);
        }
    }

    private URI buildUri(String path, String grain, Integer key, String windowParam, int windowValue) {
        StringBuilder sb = new StringBuilder(baseUrl).append(path);
        sb.append("?grain=").append(grain);
        if (key != null) {
            sb.append("&key=").append(key);
        }
        sb.append("&").append(windowParam).append("=").append(windowValue);
        return URI.create(sb.toString());
    }
}

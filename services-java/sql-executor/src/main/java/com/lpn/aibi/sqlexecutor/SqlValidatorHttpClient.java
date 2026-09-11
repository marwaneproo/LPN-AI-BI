package com.lpn.aibi.sqlexecutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SqlValidatorHttpClient implements SqlValidatorClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    SqlValidatorHttpClient(
            ObjectMapper objectMapper,
            @Value("${sql-validator.base-url}") String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public SqlValidationResult validate(String sql) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new ValidateSqlRequest(sql, "postgres"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize SQL validation request", exception);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/validate"))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SqlValidatorUnavailableException(
                        "SQL validator returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), SqlValidationResult.class);
        } catch (IOException exception) {
            throw new SqlValidatorUnavailableException("Could not call SQL validator", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SqlValidatorUnavailableException("SQL validator call was interrupted", exception);
        }
    }

    private record ValidateSqlRequest(String sql, String dialect) {
    }
}

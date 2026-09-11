package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SqlExecutorHttpClient implements SqlExecutorClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    SqlExecutorHttpClient(
            ObjectMapper objectMapper,
            @Value("${sql-executor.base-url}") String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public SqlExecutionResult execute(String sql) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new ExecuteSqlRequest(sql));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize SQL execution request", exception);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/execute-sql"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), SqlExecutionResult.class);
            }
            SqlExecutionError error = readError(response.body());
            throw new SqlExecutionException(
                    response.statusCode(),
                    error.code(),
                    error.message(),
                    error.forbiddenConstructs(),
                    error.errors());
        } catch (IOException exception) {
            throw new SqlExecutionException(
                    502,
                    "SQL_EXECUTOR_UNAVAILABLE",
                    "SQL executor could not be reached.",
                    List.of(),
                    List.of(exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SqlExecutionException(
                    502,
                    "SQL_EXECUTOR_INTERRUPTED",
                    "SQL executor call was interrupted.",
                    List.of(),
                    List.of(exception.getMessage()));
        }
    }

    private SqlExecutionError readError(String body) {
        try {
            return objectMapper.readValue(body, SqlExecutionError.class);
        } catch (IOException exception) {
            return new SqlExecutionError(
                    "SQL_EXECUTION_ERROR",
                    "SQL executor returned an unreadable error.",
                    List.of(),
                    List.of(body));
        }
    }

    private record ExecuteSqlRequest(String sql) {
    }

    private record SqlExecutionError(
            String code,
            String message,
            @JsonProperty("forbidden_constructs") List<String> forbiddenConstructs,
            List<String> errors) {
    }
}

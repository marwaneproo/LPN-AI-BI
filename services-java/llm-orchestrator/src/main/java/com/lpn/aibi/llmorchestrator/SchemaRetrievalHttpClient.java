package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
class SchemaRetrievalHttpClient implements SchemaRetrievalClient {

    private static final TypeReference<List<RetrievedTable>> RETRIEVED_TABLE_LIST = new TypeReference<>() {
    };

    private final URI retrieveUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    SchemaRetrievalHttpClient(@Value("${schema-retrieval.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.retrieveUri = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/retrieve");
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RetrievedTable> retrieve(String question, int topK, String language) {
        Map<String, Object> request = Map.of(
                "question", question,
                "top_k", topK,
                "language", language);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(retrieveUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Schema retrieval failed with status "
                        + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), RETRIEVED_TABLE_LIST);
        } catch (IOException exception) {
            throw new IllegalStateException("Schema retrieval request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Schema retrieval request interrupted", exception);
        }
    }
}

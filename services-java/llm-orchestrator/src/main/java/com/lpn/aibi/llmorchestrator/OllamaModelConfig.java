package com.lpn.aibi.llmorchestrator;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OllamaModelConfig {

    @Bean
    ChatModel sqlGenerationModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.sql-model}") String modelName,
            @Value("${ollama.sql-timeout-seconds}") int timeoutSeconds,
            @Value("${ollama.keep-alive-seconds}") int keepAliveSeconds,
            @Value("${ollama.num-ctx}") int numCtx,
            @Value("${ollama.sql-num-predict}") int numPredict,
            @Value("${ollama.sql-num-gpu}") int numGpu) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.0)
                .numCtx(numCtx)
                .numPredict(numPredict)
                .think(false)
                .returnThinking(false)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .defaultRequestParameters(ollamaRequestParameters(keepAliveSeconds, numGpu))
                .maxRetries(0)
                .build();
    }

    @Bean
    ChatModel sqlGenerationFallbackModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.sql-fallback-model}") String modelName,
            @Value("${ollama.sql-timeout-seconds}") int timeoutSeconds,
            @Value("${ollama.keep-alive-seconds}") int keepAliveSeconds,
            @Value("${ollama.num-ctx}") int numCtx,
            @Value("${ollama.sql-num-predict}") int numPredict,
            @Value("${ollama.sql-num-gpu}") int numGpu) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.0)
                .numCtx(numCtx)
                .numPredict(numPredict)
                .think(false)
                .returnThinking(false)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .defaultRequestParameters(ollamaRequestParameters(keepAliveSeconds, numGpu))
                .maxRetries(0)
                .build();
    }

    @Bean
    ChatModel sqlGenerationReasoningModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.sql-reasoning-model}") String modelName,
            @Value("${ollama.sql-reasoning-timeout-seconds}") int timeoutSeconds,
            @Value("${ollama.keep-alive-seconds}") int keepAliveSeconds,
            @Value("${ollama.num-ctx}") int numCtx,
            @Value("${ollama.sql-num-predict}") int numPredict,
            @Value("${ollama.sql-num-gpu}") int numGpu) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.0)
                .numCtx(numCtx)
                .numPredict(numPredict)
                .think(false)
                .returnThinking(false)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .defaultRequestParameters(ollamaRequestParameters(keepAliveSeconds, numGpu))
                .maxRetries(0)
                .build();
    }

    @Bean
    ChatModel narratorModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.narrator-model}") String modelName,
            @Value("${ollama.narrator-timeout-seconds}") int timeoutSeconds,
            @Value("${ollama.keep-alive-seconds}") int keepAliveSeconds,
            @Value("${ollama.num-ctx}") int numCtx,
            @Value("${ollama.narrator-num-predict}") int numPredict,
            @Value("${ollama.narrator-num-gpu}") int numGpu) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.2)
                .numCtx(numCtx)
                .numPredict(numPredict)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .defaultRequestParameters(ollamaRequestParameters(keepAliveSeconds, numGpu))
                .maxRetries(0)
                .build();
    }

    private static OllamaChatRequestParameters ollamaRequestParameters(int keepAliveSeconds, int numGpu) {
        return OllamaChatRequestParameters.builder()
                .keepAlive(keepAliveSeconds)
                .numGPU(numGpu)
                .build();
    }
}

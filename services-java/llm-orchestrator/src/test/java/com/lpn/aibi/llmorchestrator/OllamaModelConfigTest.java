package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = OllamaModelConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "ollama.base-url=http://example.test:11434",
            "ollama.sql-model=test-sql-model",
            "ollama.sql-reasoning-model=test-sql-reasoning-model",
            "ollama.sql-fallback-model=test-sql-fallback-model",
            "ollama.narrator-model=test-narrator-model",
            "ollama.sql-timeout-seconds=240",
            "ollama.sql-reasoning-timeout-seconds=360",
            "ollama.narrator-timeout-seconds=60",
            "ollama.keep-alive-seconds=1800",
            "ollama.num-ctx=2048",
            "ollama.sql-num-predict=123",
            "ollama.narrator-num-predict=45",
            "ollama.sql-num-gpu=0",
            "ollama.narrator-num-gpu=0"
        })
class OllamaModelConfigTest {

    private final ChatModel sqlGenerationModel;
    private final ChatModel sqlGenerationReasoningModel;
    private final ChatModel sqlGenerationFallbackModel;
    private final ChatModel narratorModel;

    OllamaModelConfigTest(
            @Qualifier("sqlGenerationModel") ChatModel sqlGenerationModel,
            @Qualifier("sqlGenerationReasoningModel") ChatModel sqlGenerationReasoningModel,
            @Qualifier("sqlGenerationFallbackModel") ChatModel sqlGenerationFallbackModel,
            @Qualifier("narratorModel") ChatModel narratorModel) {
        this.sqlGenerationModel = sqlGenerationModel;
        this.sqlGenerationReasoningModel = sqlGenerationReasoningModel;
        this.sqlGenerationFallbackModel = sqlGenerationFallbackModel;
        this.narratorModel = narratorModel;
    }

    @Test
    void configuresSqlGenerationModelFromProperties() {
        OllamaChatRequestParameters parameters = ((OllamaChatModel) sqlGenerationModel).defaultRequestParameters();

        assertThat(parameters.modelName()).isEqualTo("test-sql-model");
        assertThat(parameters.temperature()).isEqualTo(0.0);
        assertThat(parameters.numCtx()).isEqualTo(2048);
        assertThat(parameters.maxOutputTokens()).isEqualTo(123);
        assertThat(parameters.keepAlive()).isEqualTo(1800);
        assertThat(parameters.think()).isFalse();
    }

    @Test
    void configuresSqlGenerationReasoningModelFromProperties() {
        OllamaChatRequestParameters parameters =
                ((OllamaChatModel) sqlGenerationReasoningModel).defaultRequestParameters();

        assertThat(parameters.modelName()).isEqualTo("test-sql-reasoning-model");
        assertThat(parameters.temperature()).isEqualTo(0.0);
        assertThat(parameters.numCtx()).isEqualTo(2048);
        assertThat(parameters.maxOutputTokens()).isEqualTo(123);
        assertThat(parameters.keepAlive()).isEqualTo(1800);
        assertThat(parameters.think()).isFalse();
    }

    @Test
    void configuresSqlGenerationFallbackModelFromProperties() {
        OllamaChatRequestParameters parameters = ((OllamaChatModel) sqlGenerationFallbackModel).defaultRequestParameters();

        assertThat(parameters.modelName()).isEqualTo("test-sql-fallback-model");
        assertThat(parameters.temperature()).isEqualTo(0.0);
        assertThat(parameters.numCtx()).isEqualTo(2048);
        assertThat(parameters.maxOutputTokens()).isEqualTo(123);
        assertThat(parameters.keepAlive()).isEqualTo(1800);
        assertThat(parameters.think()).isFalse();
    }

    @Test
    void configuresNarratorModelFromProperties() {
        OllamaChatRequestParameters parameters = ((OllamaChatModel) narratorModel).defaultRequestParameters();

        assertThat(parameters.modelName()).isEqualTo("test-narrator-model");
        assertThat(parameters.temperature()).isEqualTo(0.2);
        assertThat(parameters.numCtx()).isEqualTo(2048);
        assertThat(parameters.maxOutputTokens()).isEqualTo(45);
        assertThat(parameters.keepAlive()).isEqualTo(1800);
    }
}

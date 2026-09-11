package com.lpn.aibi.llmorchestrator.bi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BiPackageBoundaryTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "SqlGenerationService",
            "SqlExecutorClient",
            "QaController",
            "SchemaRetrievalClient",
            "QuestionIntentDetector",
            "PredictiveClient",
            "business.",
            "staging.",
            "warehouse.");

    @Test
    void biPackageDoesNotImportOrQueryForbiddenBoundaries() throws IOException {
        Path biPackage = resolveBiPackage();

        try (Stream<Path> files = Files.walk(biPackage)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> violationsIn(path).stream())
                    .toList();

            assertThat(violations)
                    .as("BI package must stay marts-only and isolated from orchestration dependencies")
                    .isEmpty();
        }
    }

    private static List<String> violationsIn(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return FORBIDDEN_TOKENS.stream()
                    .filter(content::contains)
                    .map(token -> path + " contains " + token)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private static Path resolveBiPackage() {
        List<Path> candidates = List.of(
                Paths.get("src/main/java/com/lpn/aibi/llmorchestrator/bi"),
                Paths.get("llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi"));
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate BI package in " + Paths.get("").toAbsolutePath()));
    }
}

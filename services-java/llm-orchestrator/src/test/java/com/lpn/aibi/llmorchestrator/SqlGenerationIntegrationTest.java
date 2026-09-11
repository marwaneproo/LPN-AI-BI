package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class SqlGenerationIntegrationTest {

    @Autowired
    private SqlGenerationService sqlGenerationService;

    @Test
    void generatesSqlUsingRealSchemaRetrievalAndOllama() {
        SqlGenerationService.SqlGenerationResult result =
                sqlGenerationService.generateSql("How many orders were placed last month?");

        assertThat(result.sql()).startsWithIgnoringCase("SELECT");
        assertThat(result.retrievedTables()).isNotEmpty();
        assertThat(result.retrievedTables())
                .extracting(RetrievedTable::tableName)
                .contains("C_ORDER");
    }
}

package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class AnswerNarrationServiceTest {

    private final QuestionIntentDetector intentDetector = new QuestionIntentDetector();

    @Test
    void deterministicallyListsEveryTopNRow() {
        ChatModel unusedNarrator = mock(ChatModel.class);
        AnswerNarrationService service = new AnswerNarrationService(
                unusedNarrator,
                new ByteArrayResource("Answer:".getBytes()),
                new ObjectMapper());

        AnswerNarrationService.NarrationResult result = service.narrate(
                "Top 10 clients in the past month?",
                "SELECT ...",
                List.of(
                        Map.of("customer_name", "Client A", "order_count", 3, "total_order_value", 1200.50),
                        Map.of("customer_name", "Client B", "order_count", 2, "total_order_value", 800),
                        Map.of("customer_name", "Client C", "order_count", 1, "total_order_value", 400)),
                "French",
                new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of()),
                intentDetector.detect("Top 10 clients in the past month?"));

        assertThat(result.answer()).contains("1. Client A");
        assertThat(result.answer()).contains("2. Client B");
        assertThat(result.answer()).contains("3. Client C");
        assertThat(result.answer()).contains("valeur totale des commandes");
        verifyNoInteractions(unusedNarrator);
    }

    @Test
    void deterministicallyFormatsCustomerSalesRankingWithBusinessLabels() {
        ChatModel unusedNarrator = mock(ChatModel.class);
        AnswerNarrationService service = new AnswerNarrationService(
                unusedNarrator,
                new ByteArrayResource("Answer:".getBytes()),
                new ObjectMapper());

        AnswerNarrationService.NarrationResult result = service.narrate(
                "Top 6 clients by sales in the past month?",
                "SELECT ...",
                List.of(
                        Map.of("customername", "Client A", "invoicecount", 2, "totalsales", 1142825.63),
                        Map.of("customername", "Client B", "invoicecount", 1, "totalsales", 437595.15)),
                "French",
                new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of()),
                intentDetector.detect("Top 6 clients by sales in the past month?"));

        assertThat(result.answer()).contains("chiffre d'affaires facturé");
        assertThat(result.answer()).contains("1. Client A");
        assertThat(result.answer()).contains("nombre de factures: 2");
        assertThat(result.answer()).contains("chiffre d'affaires total: 1 142 825,63");
        assertThat(result.answer()).doesNotContain("Ligne résultat");
        verifyNoInteractions(unusedNarrator);
    }

    @Test
    void deterministicallyFormatsMonthlyComparison() {
        ChatModel unusedNarrator = mock(ChatModel.class);
        AnswerNarrationService service = new AnswerNarrationService(
                unusedNarrator,
                new ByteArrayResource("Answer:".getBytes()),
                new ObjectMapper());

        AnswerNarrationService.NarrationResult result = service.narrate(
                "Compare the client Client A past 2 month totalsales",
                "SELECT ...",
                List.of(
                        Map.of("month", "2026-03-01T00:00:00.000+00:00", "total_sales", 1177003.33),
                        Map.of("month", "2026-04-01T00:00:00.000+00:00", "total_sales", 756)),
                "French",
                new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of()),
                intentDetector.detect("Compare the client Client A past 2 month totalsales"));

        assertThat(result.answer()).contains("Comparaison de chiffre d'affaires facturé");
        assertThat(result.answer()).contains("mars 2026: 1 177 003,33");
        assertThat(result.answer()).contains("avril 2026: 756");
        assertThat(result.answer()).contains("Le niveau le plus élevé est mars 2026");
        verifyNoInteractions(unusedNarrator);
    }

    @Test
    void deterministicallyFormatsProductOrderRankingWithBusinessLabels() {
        ChatModel unusedNarrator = mock(ChatModel.class);
        AnswerNarrationService service = new AnswerNarrationService(
                unusedNarrator,
                new ByteArrayResource("Answer:".getBytes()),
                new ObjectMapper());

        AnswerNarrationService.NarrationResult result = service.narrate(
                "Which 10 products generated the highest order value in the past month?",
                "SELECT ...",
                List.of(
                        Map.of("productname", "Product A", "linecount", 4, "totalquantity", 20, "totalordervalue", 1000.25),
                        Map.of("productname", "Product B", "linecount", 2, "totalquantity", 5, "totalordervalue", 800)),
                "French",
                new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of()),
                intentDetector.detect("Which 10 products generated the highest order value in the past month?"));

        assertThat(result.answer()).contains("produits classés par valeur totale des commandes");
        assertThat(result.answer()).contains("1. Product A");
        assertThat(result.answer()).contains("nombre de lignes: 4");
        assertThat(result.answer()).contains("quantité totale: 20");
        assertThat(result.answer()).contains("valeur totale des commandes: 1 000,25");
        verifyNoInteractions(unusedNarrator);
    }

    @Test
    void deterministicallyFormatsOrderVsInvoiceComparison() {
        ChatModel unusedNarrator = mock(ChatModel.class);
        AnswerNarrationService service = new AnswerNarrationService(
                unusedNarrator,
                new ByteArrayResource("Answer:".getBytes()),
                new ObjectMapper());

        AnswerNarrationService.NarrationResult result = service.narrate(
                "Compare total order value and total invoiced sales for the past month.",
                "SELECT ...",
                List.of(Map.of(
                        "totalordervalue", 5005904.54,
                        "totalinvoicedsales", 2498222.10,
                        "difference", 2507682.44,
                        "invoicecoveragepercent", 49.91)),
                "French",
                new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of("SQL result is an aggregate")),
                intentDetector.detect("Compare total order value and total invoiced sales for the past month."));

        assertThat(result.answer()).contains("valeur totale des commandes est de 5 005 904,54");
        assertThat(result.answer()).contains("chiffre d'affaires facturé est de 2 498 222,1");
        assertThat(result.answer()).contains("L'écart commandes - factures est de 2 507 682,44");
        assertThat(result.answer()).contains("taux de couverture facturée est de 49,91 %");
        verifyNoInteractions(unusedNarrator);
    }
}

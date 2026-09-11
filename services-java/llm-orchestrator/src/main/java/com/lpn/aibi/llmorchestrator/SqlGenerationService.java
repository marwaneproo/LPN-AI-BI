package com.lpn.aibi.llmorchestrator;

import dev.langchain4j.model.chat.ChatModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class SqlGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlGenerationService.class);
    private static final Pattern FENCED_SQL = Pattern.compile("(?is)```(?:sql)?\\s*(.*?)\\s*```");
    private static final Pattern WITH_STATEMENT_START = Pattern.compile("(?is)\\bwith\\s+[a-z_][a-z0-9_]*\\s+as\\s*\\(");
    private static final Pattern SELECT_STATEMENT = Pattern.compile("(?is)\\bselect\\b.*?(?:;|$)");
    private static final Pattern TOP_N = Pattern.compile("(?i)\\btop\\s+(\\d+)\\b|\\b(?:which|quels?|quelles?)\\s+(\\d+)\\b|\\b(\\d+)\\s+(?:(?:premiers?|meilleurs?)\\s+)?(?:clients?|customers?|products?|produits?|articles?|fournisseurs?|suppliers?)\\b");
    private static final Pattern RELATIVE_MONTH_COUNT = Pattern.compile("(?i)\\b(?:last|past|previous|prior)\\s+(\\d+)\\s+months?\\b|\\b(\\d+)\\s+derniers?\\s+mois\\b");
    private static final Pattern EXPLICIT_YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern SUPPLIER = Pattern.compile("(?i)\\b(suppliers?|fournisseurs?)\\b");
    private static final Pattern CLIENT_NAME_BEFORE_PERIOD = Pattern.compile(
            "(?i)\\bclient\\s+(.+?)\\s+(?:past|last|previous|prior)\\s+\\d+\\s+months?\\b");

    private final SchemaRetrievalClient schemaRetrievalClient;
    private final ChatModel sqlGenerationModel;
    private final ChatModel sqlGenerationReasoningModel;
    private final ChatModel sqlGenerationFallbackModel;
    private final Resource promptTemplate;
    private final String sqlModelName;
    private final String sqlReasoningModelName;
    private final String sqlFallbackModelName;

    SqlGenerationService(
            SchemaRetrievalClient schemaRetrievalClient,
            @Qualifier("sqlGenerationModel") ChatModel sqlGenerationModel,
            @Qualifier("sqlGenerationReasoningModel") ChatModel sqlGenerationReasoningModel,
            @Qualifier("sqlGenerationFallbackModel") ChatModel sqlGenerationFallbackModel,
            @Value("classpath:prompts/sql-generation.en.txt") Resource promptTemplate,
            @Value("${ollama.sql-model}") String sqlModelName,
            @Value("${ollama.sql-reasoning-model}") String sqlReasoningModelName,
            @Value("${ollama.sql-fallback-model}") String sqlFallbackModelName) {
        this.schemaRetrievalClient = schemaRetrievalClient;
        this.sqlGenerationModel = sqlGenerationModel;
        this.sqlGenerationReasoningModel = sqlGenerationReasoningModel;
        this.sqlGenerationFallbackModel = sqlGenerationFallbackModel;
        this.promptTemplate = promptTemplate;
        this.sqlModelName = sqlModelName;
        this.sqlReasoningModelName = sqlReasoningModelName;
        this.sqlFallbackModelName = sqlFallbackModelName;
    }

    SqlGenerationResult generateSql(String question) {
        return generateSql(question, SqlModelMode.STANDARD);
    }

    SqlGenerationResult generateSql(String question, SqlModelMode mode) {
        return generateSql(question, mode, null);
    }

    SqlGenerationResult generateSql(String question, SqlModelMode mode, QuestionIntent intent) {
        SqlModelMode selectedMode = mode == null ? SqlModelMode.STANDARD : mode;
        Instant retrievalStartedAt = Instant.now();
        List<RetrievedTable> retrievedTables = schemaRetrievalClient.retrieve(question, 8, "en");
        long schemaRetrievalLatencyMs = elapsedMs(retrievalStartedAt);

        Optional<String> deterministicSql = deterministicSql(question, intent);
        if (deterministicSql.isPresent()) {
            return new SqlGenerationResult(
                    deterministicSql.get(),
                    retrievedTables,
                    selectedMode.apiValue(),
                    selectedMode.isReasoning(),
                    "deterministic-sql-template",
                    false,
                    schemaRetrievalLatencyMs,
                    1,
                    1);
        }

        String prompt = buildPrompt(question, retrievedTables, intent);

        Instant modelStartedAt = Instant.now();
        ModelChatResult modelResult = chatWithFallback(prompt, selectedMode);
        long sqlModelLatencyMs = elapsedMs(modelStartedAt);

        Instant normalizationStartedAt = Instant.now();
        String sql = normalizeSql(modelResult.rawResponse());
        long sqlNormalizationLatencyMs = elapsedMs(normalizationStartedAt);

        return new SqlGenerationResult(
                sql,
                retrievedTables,
                selectedMode.apiValue(),
                selectedMode.isReasoning(),
                modelResult.modelUsed(),
                modelResult.fallbackUsed(),
                schemaRetrievalLatencyMs,
                sqlModelLatencyMs,
                sqlNormalizationLatencyMs);
    }

    SqlRepairResult repairSql(
            String question,
            String currentSql,
            List<String> validationIssues,
            List<RetrievedTable> retrievedTables,
            SqlModelMode mode,
            QuestionIntent intent) {
        SqlModelMode selectedMode = mode == null ? SqlModelMode.STANDARD : mode;
        String prompt = """
                You are repairing a PostgreSQL SELECT query for a BI assistant.

                Rules:
                - Output ONLY the repaired SQL.
                - Start with SELECT, or WITH when a CTE makes the query clearer.
                - Use only schema tables and columns shown below.
                - Fix every validation issue.
                - Do not add filters not requested by the user.
                - If the user asks for last/past/previous/prior month, use a bounded previous calendar month filter.
                - If the user asks for past/last/previous/prior N months, use a bounded previous N full calendar months filter with an exclusive current-month upper bound.
                - For comparisons, return every compared date/item/entity, never only the winner.
                - For generic top customers/clients, join C_ORDER to C_BPARTNER and expose customer name, order count, total order value, and average order value.
                - For customer sales/revenue/CA/totalsales questions, join C_INVOICE to C_BPARTNER and expose customer name, invoice count, total sales, and average invoice value.
                - For order value questions, use C_ORDER.GRANDTOTAL, not C_ORDER.TOTALLINES.

                Schema:
                %s

                %s

                User question:
                %s

                Current SQL:
                %s

                Validation issues:
                %s

                Repaired SQL:
                """.formatted(
                schemaBlock(retrievedTables),
                intent == null ? "Detected intent hints: (none)" : intent.promptBlock(),
                question,
                currentSql,
                validationIssues);

        Instant modelStartedAt = Instant.now();
        ModelChatResult modelResult = chatWithFallback(prompt, selectedMode);
        long repairLatencyMs = elapsedMs(modelStartedAt);

        Instant normalizationStartedAt = Instant.now();
        String repairedSql = normalizeSql(modelResult.rawResponse());
        long normalizationLatencyMs = elapsedMs(normalizationStartedAt);

        return new SqlRepairResult(
                repairedSql,
                modelResult.modelUsed(),
                modelResult.fallbackUsed(),
                repairLatencyMs,
                normalizationLatencyMs);
    }

    private ModelChatResult chatWithFallback(String prompt, SqlModelMode mode) {
        ChatModel selectedModel = mode.isReasoning() ? sqlGenerationReasoningModel : sqlGenerationModel;
        String selectedModelName = mode.isReasoning() ? sqlReasoningModelName : sqlModelName;

        try {
            return new ModelChatResult(selectedModel.chat(prompt), selectedModelName, false);
        } catch (RuntimeException primaryException) {
            LOGGER.warn("SQL model {} failed in {} mode. Retrying with fallback SQL model.",
                    selectedModelName,
                    mode.apiValue(),
                    primaryException);
            return new ModelChatResult(sqlGenerationFallbackModel.chat(prompt), sqlFallbackModelName, true);
        }
    }

    String buildPrompt(String question, List<RetrievedTable> retrievedTables) {
        return buildPrompt(question, retrievedTables, null);
    }

    String buildPrompt(String question, List<RetrievedTable> retrievedTables, QuestionIntent intent) {
        return template()
                .replace("{schema_block}", schemaBlock(retrievedTables))
                .replace("{intent_block}", intent == null ? "Detected intent hints: (none)" : intent.promptBlock())
                .replace("{question}", question);
    }

    private Optional<String> deterministicSql(String question, QuestionIntent intent) {
        if (intent == null || question == null || question.isBlank()) {
            return Optional.empty();
        }
        boolean explicitYearRequested = parseExplicitYear(question).isPresent();
        if (explicitYearRequested && intent.hasIntent("COUNT") && intent.orderMetricQuestion()) {
            String orderDateFilter = requestedDateFilter(intent, question, "o.DATEORDERED");
            return Optional.of("""
                    SELECT COUNT(o.C_ORDER_ID) AS ordercount
                    FROM C_ORDER o
                    %s
                    """.formatted(orderDateFilter).trim());
        }
        if (explicitYearRequested
                && SUPPLIER.matcher(question).find()
                && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())) {
            String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
            return Optional.of("""
                    SELECT
                        COALESCE(s.SUPPLIER_NAME, 'Sans fournisseur') AS suppliername,
                        COUNT(DISTINCT i.C_INVOICE_ID) AS invoicecount,
                        SUM(il.LINENETAMT) AS totalinvoicedsales
                    FROM C_INVOICE i
                    JOIN C_INVOICELINE il ON il.C_INVOICE_ID = i.C_INVOICE_ID
                    LEFT JOIN V_PRODUCT_PRIMARY_SUPPLIER s ON s.M_PRODUCT_ID = il.M_PRODUCT_ID
                    %s
                    GROUP BY COALESCE(s.SUPPLIER_NAME, 'Sans fournisseur')
                    ORDER BY totalinvoicedsales DESC
                    LIMIT 100
                    """.formatted(invoiceDateFilter).trim());
        }
        if (explicitYearRequested
                && !intent.rankingQuestion()
                && intent.invoiceMetricQuestion()
                && intent.salesMetricQuestion()) {
            String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
            return Optional.of("""
                    SELECT
                        COUNT(i.C_INVOICE_ID) AS invoicecount,
                        SUM(i.GRANDTOTAL) AS totalinvoicedsales
                    FROM C_INVOICE i
                    %s
                    """.formatted(invoiceDateFilter).trim());
        }
        if (intent.customerRankingQuestion()) {
            int limit = parseTopN(question).orElse(10);
            String orderDateFilter = requestedDateFilter(intent, question, "o.DATEORDERED");
            String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
            if (intent.salesMetricQuestion() || intent.invoiceMetricQuestion()) {
                return Optional.of("""
                        SELECT
                            bp.C_BPARTNER_ID AS customerid,
                            bp.VALUE AS customercode,
                            bp.NAME AS customername,
                            COUNT(i.C_INVOICE_ID) AS invoicecount,
                            SUM(i.GRANDTOTAL) AS totalsales,
                            AVG(i.GRANDTOTAL) AS averageinvoicevalue
                        FROM C_INVOICE i
                        JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = i.C_BPARTNER_ID
                        %s
                        GROUP BY bp.C_BPARTNER_ID, bp.VALUE, bp.NAME
                        ORDER BY totalsales DESC
                        LIMIT %d
                        """.formatted(invoiceDateFilter, limit).trim());
            }
            return Optional.of("""
                    SELECT
                        bp.C_BPARTNER_ID AS customerid,
                        bp.VALUE AS customercode,
                        bp.NAME AS customername,
                        COUNT(o.C_ORDER_ID) AS ordercount,
                        SUM(o.GRANDTOTAL) AS totalordervalue,
                        AVG(o.GRANDTOTAL) AS averageordervalue
                    FROM C_ORDER o
                    JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = o.C_BPARTNER_ID
                    %s
                    GROUP BY bp.C_BPARTNER_ID, bp.VALUE, bp.NAME
                    ORDER BY totalordervalue DESC
                    LIMIT %d
                    """.formatted(orderDateFilter, limit).trim());
        }
        if (intent.supplierRankingQuestion()) {
            int limit = parseTopN(question).orElse(10);
            String metricDateFilter = requestedDateFilter(intent, question, "metric_date");
            String whereClause = metricDateFilter.isBlank()
                    ? "WHERE supplier_key <> 0"
                    : metricDateFilter + "\n  AND supplier_key <> 0";
            return Optional.of("""
                    SELECT
                        supplier_name,
                        COUNT(DISTINCT NULLIF(product_key, 0)) AS product_count,
                        SUM(ca_facture) AS total_attributed_sales,
                        SUM(quantity_invoiced) AS total_quantity
                    FROM mart.mart_sales_by_product
                    %s
                    GROUP BY supplier_name
                    ORDER BY total_attributed_sales DESC
                    LIMIT %d
                    """.formatted(whereClause, limit).trim());
        }
        if (intent.productRankingQuestion()) {
            int limit = parseTopN(question).orElse(10);
            if (intent.salesMetricQuestion() || intent.invoiceMetricQuestion()) {
                String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
                return Optional.of("""
                        SELECT
                            p.NAME AS productname,
                            COUNT(il.C_INVOICELINE_ID) AS linecount,
                            SUM(il.QTYINVOICED) AS totalquantity,
                            SUM(il.LINENETAMT) AS totalinvoicedsales,
                            AVG(il.LINENETAMT) AS averagelinevalue
                        FROM C_INVOICE i
                        JOIN C_INVOICELINE il ON il.C_INVOICE_ID = i.C_INVOICE_ID
                        JOIN M_PRODUCT p ON p.M_PRODUCT_ID = il.M_PRODUCT_ID
                        %s
                        GROUP BY p.NAME
                        ORDER BY totalinvoicedsales DESC
                        LIMIT %d
                        """.formatted(invoiceDateFilter, limit).trim());
            }
            String orderDateFilter = requestedDateFilter(intent, question, "o.DATEORDERED");
            return Optional.of("""
                    SELECT
                        p.NAME AS productname,
                        COUNT(ol.C_ORDERLINE_ID) AS linecount,
                        SUM(ol.QTYORDERED) AS totalquantity,
                        SUM(ol.LINENETAMT) AS totalordervalue,
                        AVG(ol.LINENETAMT) AS averagelinevalue
                    FROM C_ORDER o
                    JOIN C_ORDERLINE ol ON ol.C_ORDER_ID = o.C_ORDER_ID
                    JOIN M_PRODUCT p ON p.M_PRODUCT_ID = ol.M_PRODUCT_ID
                    %s
                    GROUP BY p.NAME
                    ORDER BY totalordervalue DESC
                    LIMIT %d
                    """.formatted(orderDateFilter, limit).trim());
        }
        if (intent.breakdownQuestion()
                && "CATEGORY".equals(intent.breakdownDimension())
                && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())
                && !intent.rankingQuestion()) {
            String metricDateFilter = requestedDateFilter(intent, question, "metric_date");
            return Optional.of("""
                    SELECT
                        category_name,
                        SUM(ca_facture) AS total_sales,
                        SUM(quantity_invoiced) AS total_quantity
                    FROM mart.mart_sales_by_product
                    %s
                    GROUP BY category_name
                    ORDER BY total_sales DESC
                    LIMIT 100
                    """.formatted(metricDateFilter).trim());
        }
        if (intent.hasIntent("STOCK_RISK")) {
            return Optional.of("""
                    SELECT
                        product_name,
                        category_name,
                        SUM(qty_on_hand) AS qty_on_hand,
                        SUM(qty_reserved) AS qty_reserved,
                        SUM(qty_available) AS qty_available,
                        SUM(qty_ordered) AS qty_ordered
                    FROM mart.mart_sales_by_product
                    WHERE product_key <> 0
                    GROUP BY product_name, category_name
                    HAVING SUM(qty_available) <= 5
                    ORDER BY qty_available ASC
                    LIMIT 100
                    """.trim());
        }
        if (intent.explicitPaidUnpaid() && (intent.hasDomain("CLIENTS") || intent.invoiceMetricQuestion())) {
            return Optional.of("""
                    SELECT
                        bp.C_BPARTNER_ID AS customerid,
                        bp.NAME AS customername,
                        COUNT(i.C_INVOICE_ID) AS unpaidinvoicecount,
                        SUM(i.GRANDTOTAL) AS unpaidtotal
                    FROM C_INVOICE i
                    JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = i.C_BPARTNER_ID
                    WHERE i.ISPAID = 'N'
                    GROUP BY bp.C_BPARTNER_ID, bp.NAME
                    ORDER BY unpaidtotal DESC
                    LIMIT 200
                    """.trim());
        }
        if (intent.hasDomain("ACHATS") && !intent.rankingQuestion()) {
            String metricDateFilter = requestedDateFilter(intent, question, "metric_date");
            return Optional.of("""
                    SELECT
                        COUNT(DISTINCT NULLIF(supplier_key, 0)) AS supplier_count,
                        COUNT(DISTINCT NULLIF(product_key, 0)) AS product_count,
                        SUM(ca_facture) AS total_attributed_spend,
                        SUM(quantity_invoiced) AS total_quantity
                    FROM mart.mart_sales_by_product
                    %s
                    """.formatted(metricDateFilter).trim());
        }
        if (intent.summaryQuestion() && !intent.rankingQuestion()) {
            String orderDateFilter = requestedDateFilter(intent, question, "o.DATEORDERED");
            String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
            return Optional.of("""
                    WITH order_summary AS (
                        SELECT COUNT(o.C_ORDER_ID) AS ordercount, COALESCE(SUM(o.GRANDTOTAL), 0) AS totalordervalue
                        FROM C_ORDER o
                        %s
                    ),
                    invoice_summary AS (
                        SELECT COUNT(i.C_INVOICE_ID) AS invoicecount, COALESCE(SUM(i.GRANDTOTAL), 0) AS totalinvoicedsales
                        FROM C_INVOICE i
                        %s
                    ),
                    customer_summary AS (
                        SELECT COUNT(DISTINCT bp.C_BPARTNER_ID) AS activecustomercount
                        FROM C_INVOICE i
                        JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = i.C_BPARTNER_ID
                    )
                    SELECT
                        order_summary.ordercount,
                        order_summary.totalordervalue,
                        invoice_summary.invoicecount,
                        invoice_summary.totalinvoicedsales,
                        customer_summary.activecustomercount
                    FROM order_summary
                    CROSS JOIN invoice_summary
                    CROSS JOIN customer_summary
                    """.formatted(orderDateFilter, invoiceDateFilter).trim());
        }
        if (!intent.rankingQuestion()
                && intent.orderMetricQuestion()
                && intent.hasIntent("COUNT")
                && !(intent.salesMetricQuestion() || intent.invoiceMetricQuestion())) {
            String orderDateFilter = requestedDateFilter(intent, question, "o.DATEORDERED");
            return Optional.of("""
                    SELECT COUNT(o.C_ORDER_ID) AS ordercount
                    FROM C_ORDER o
                    %s
                    """.formatted(orderDateFilter).trim());
        }
        if (!intent.rankingQuestion()
                && !intent.comparisonQuestion()
                && !intent.breakdownQuestion()
                && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())) {
            String invoiceDateFilter = requestedDateFilter(intent, question, "i.DATEINVOICED");
            return Optional.of("""
                    SELECT
                        COUNT(i.C_INVOICE_ID) AS invoicecount,
                        SUM(i.GRANDTOTAL) AS totalinvoicedsales
                    FROM C_INVOICE i
                    %s
                    """.formatted(invoiceDateFilter).trim());
        }
        if (intent.comparisonQuestion()
                && intent.orderMetricQuestion()
                && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())
                && (intent.explicitLastMonth() || intent.relativeMonthRange())) {
            int months = parseRelativeMonthCount(question).orElse(1);
            String interval = monthIntervalLiteral(months);
            return Optional.of("""
                    WITH order_totals AS (
                        SELECT COALESCE(SUM(o.GRANDTOTAL), 0) AS totalordervalue
                        FROM C_ORDER o
                        WHERE o.DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '%s'
                          AND o.DATEORDERED < date_trunc('month', CURRENT_DATE)
                    ),
                    invoice_totals AS (
                        SELECT COALESCE(SUM(i.GRANDTOTAL), 0) AS totalinvoicedsales
                        FROM C_INVOICE i
                        WHERE i.DATEINVOICED >= date_trunc('month', CURRENT_DATE) - INTERVAL '%s'
                          AND i.DATEINVOICED < date_trunc('month', CURRENT_DATE)
                    )
                    SELECT
                        order_totals.totalordervalue,
                        invoice_totals.totalinvoicedsales,
                        order_totals.totalordervalue - invoice_totals.totalinvoicedsales AS difference,
                        CASE
                            WHEN order_totals.totalordervalue = 0 THEN NULL
                            ELSE ROUND((invoice_totals.totalinvoicedsales / order_totals.totalordervalue) * 100, 2)
                        END AS invoicecoveragepercent
                    FROM order_totals
                    CROSS JOIN invoice_totals
                    """.formatted(interval, interval).trim());
        }
        if (intent.comparisonQuestion()
                && intent.salesMetricQuestion()
                && intent.relativeMonthRange()
                && question.toLowerCase(Locale.ROOT).contains("client")) {
            Optional<String> customerName = parseClientNameForRelativeMonths(question);
            if (customerName.isPresent()) {
                int months = parseRelativeMonthCount(question).orElse(2);
                return Optional.of("""
                        WITH months AS (
                            SELECT generate_series(
                                date_trunc('month', CURRENT_DATE) - INTERVAL '%d months',
                                date_trunc('month', CURRENT_DATE) - INTERVAL '1 month',
                                INTERVAL '1 month'
                            ) AS month
                        ),
                        invoice_sales AS (
                            SELECT
                                DATE_TRUNC('month', i.DATEINVOICED) AS month,
                                SUM(i.GRANDTOTAL) AS totalsales
                            FROM C_INVOICE i
                            JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = i.C_BPARTNER_ID
                            WHERE bp.NAME = '%s'
                              AND i.DATEINVOICED >= date_trunc('month', CURRENT_DATE) - INTERVAL '%d months'
                              AND i.DATEINVOICED < date_trunc('month', CURRENT_DATE)
                            GROUP BY DATE_TRUNC('month', i.DATEINVOICED)
                        )
                        SELECT
                            months.month,
                            COALESCE(invoice_sales.totalsales, 0) AS totalsales
                        FROM months
                        LEFT JOIN invoice_sales ON invoice_sales.month = months.month
                        ORDER BY months.month
                        LIMIT 1000
                        """.formatted(months, escapeSqlLiteral(customerName.get()), months).trim());
            }
        }
        return Optional.empty();
    }

    private static String requestedDateFilter(QuestionIntent intent, String question, String column) {
        Optional<Integer> explicitYear = parseExplicitYear(question);
        if (explicitYear.isPresent()) {
            int year = explicitYear.get();
            return """
                    WHERE %s >= DATE '%d-01-01'
                      AND %s < DATE '%d-01-01'
                    """.formatted(column, year, column, year + 1).trim();
        }
        if (intent.explicitToday()) {
            return """
                    WHERE %s >= CURRENT_DATE
                      AND %s < CURRENT_DATE + INTERVAL '1 day'
                    """.formatted(column, column).trim();
        }
        if (intent.explicitYesterday()) {
            return """
                    WHERE %s >= CURRENT_DATE - INTERVAL '1 day'
                      AND %s < CURRENT_DATE
                    """.formatted(column, column).trim();
        }
        if (intent.explicitThisWeek()) {
            return """
                    WHERE %s >= date_trunc('week', CURRENT_DATE)
                      AND %s < date_trunc('week', CURRENT_DATE) + INTERVAL '1 week'
                    """.formatted(column, column).trim();
        }
        if (intent.explicitLastWeek()) {
            return """
                    WHERE %s >= date_trunc('week', CURRENT_DATE) - INTERVAL '1 week'
                      AND %s < date_trunc('week', CURRENT_DATE)
                    """.formatted(column, column).trim();
        }
        if (intent.explicitThisMonth()) {
            return """
                    WHERE %s >= date_trunc('month', CURRENT_DATE)
                      AND %s < date_trunc('month', CURRENT_DATE) + INTERVAL '1 month'
                    """.formatted(column, column).trim();
        }
        if (intent.relativeMonthRange()) {
            int months = parseRelativeMonthCount(question).orElse(1);
            return """
                    WHERE %s >= date_trunc('month', CURRENT_DATE) - INTERVAL '%s'
                      AND %s < date_trunc('month', CURRENT_DATE)
                    """.formatted(column, monthIntervalLiteral(months), column).trim();
        }
        if (intent.explicitLastMonth()) {
            return """
                    WHERE %s >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                      AND %s < date_trunc('month', CURRENT_DATE)
                    """.formatted(column, column).trim();
        }
        return "";
    }

    private static Optional<Integer> parseExplicitYear(String question) {
        Matcher matcher = EXPLICIT_YEAR.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String monthIntervalLiteral(int months) {
        return months == 1 ? "1 month" : months + " months";
    }

    private static Optional<Integer> parseTopN(String question) {
        Matcher matcher = TOP_N.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = firstNonBlankGroup(matcher, 1, 2, 3);
        if (value == null) {
            return Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(value);
            return Optional.of(Math.max(1, Math.min(parsed, 100)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String firstNonBlankGroup(Matcher matcher, int... groupIndexes) {
        for (int groupIndex : groupIndexes) {
            String value = matcher.group(groupIndex);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Optional<Integer> parseRelativeMonthCount(String question) {
        Matcher matcher = RELATIVE_MONTH_COUNT.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        try {
            int parsed = Integer.parseInt(value);
            return Optional.of(Math.max(1, Math.min(parsed, 24)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> parseClientNameForRelativeMonths(String question) {
        Matcher matcher = CLIENT_NAME_BEFORE_PERIOD.matcher(question == null ? "" : question.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String name = matcher.group(1).trim();
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    static String normalizeSql(String modelResponse) {
        String candidate = modelResponse == null ? "" : modelResponse.trim();

        Matcher fencedSql = FENCED_SQL.matcher(candidate);
        if (fencedSql.find()) {
            candidate = fencedSql.group(1).trim();
        }

        candidate = candidate.replaceAll("(?is)</?execute>", "").trim();
        Matcher withStatement = WITH_STATEMENT_START.matcher(candidate);
        if (withStatement.find()) {
            candidate = candidate.substring(withStatement.start()).trim();
            int semicolon = candidate.indexOf(';');
            return semicolon >= 0 ? candidate.substring(0, semicolon + 1).trim() : candidate;
        }

        Matcher selectStatement = SELECT_STATEMENT.matcher(candidate);
        if (selectStatement.find()) {
            candidate = selectStatement.group().trim();
        }

        return candidate;
    }

    private String schemaBlock(List<RetrievedTable> retrievedTables) {
        if (retrievedTables.isEmpty()) {
            return "(no schema tables retrieved)";
        }

        StringBuilder builder = new StringBuilder();
        for (RetrievedTable table : retrievedTables) {
            builder.append("Table: ").append(table.tableName()).append('\n');
            appendIfPresent(builder, "Module", table.module());
            appendIfPresent(builder, "Description", table.descriptionEn());
            appendIfPresent(builder, "Columns", table.keyColumns());
            appendIfPresent(builder, "Relations", table.relations());
            appendIfPresent(builder, "Notes", table.notes());
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value).append('\n');
        }
    }

    private String template() {
        try {
            return StreamUtils.copyToString(promptTemplate.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read SQL generation prompt template", exception);
        }
    }

    private static long elapsedMs(Instant startedAt) {
        return Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
    }

    record SqlGenerationResult(
            String sql,
            List<RetrievedTable> retrievedTables,
            String requestMode,
            boolean reasoningMode,
            String modelUsed,
            boolean fallbackUsed,
            long schemaRetrievalLatencyMs,
            long sqlModelLatencyMs,
            long sqlNormalizationLatencyMs) {
    }

    record SqlRepairResult(
            String sql,
            String modelUsed,
            boolean fallbackUsed,
            long sqlRepairLatencyMs,
            long sqlRepairNormalizationLatencyMs) {
    }

    private record ModelChatResult(String rawResponse, String modelUsed, boolean fallbackUsed) {
    }

    enum SqlModelMode {
        STANDARD("standard"),
        REASONING("reasoning");

        private final String apiValue;

        SqlModelMode(String apiValue) {
            this.apiValue = apiValue;
        }

        String apiValue() {
            return apiValue;
        }

        boolean isReasoning() {
            return this == REASONING;
        }

        static SqlModelMode from(String mode, Boolean reasoningMode) {
            if (Boolean.TRUE.equals(reasoningMode)) {
                return REASONING;
            }
            if (mode == null || mode.isBlank()) {
                return STANDARD;
            }
            String normalized = mode.trim().toLowerCase();
            return switch (normalized) {
                case "reasoning", "thinking", "heavy", "14b" -> REASONING;
                default -> STANDARD;
            };
        }
    }
}

package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
class AnswerNarrationService {

    private static final int MAX_ROWS_FOR_PROMPT = 60;
    private static final Pattern META_NOTE_PATTERN = Pattern.compile("(?is)\\s*\\(?\\s*note\\s*[:：].*$");
    private static final Pattern RISK_WORDING = Pattern.compile("(?iU)\\b(risqu\\w*|risks?|ruptur\\w*)\\b");

    private final ChatModel narratorModel;
    private final Resource promptTemplate;
    private final ObjectMapper objectMapper;

    AnswerNarrationService(
            @Qualifier("narratorModel") ChatModel narratorModel,
            @Value("classpath:prompts/narration.en.txt") Resource promptTemplate,
            ObjectMapper objectMapper) {
        this.narratorModel = narratorModel;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
    }

    NarrationResult narrate(String question, List<Map<String, Object>> rows, String language) {
        return narrate(question, null, rows, language, null, null);
    }

    NarrationResult narrate(
            String question,
            String sql,
            List<Map<String, Object>> rows,
            String language,
            SemanticSqlValidator.ResultAssessment assessment) {
        return narrate(question, sql, rows, language, assessment, null);
    }

    NarrationResult narrate(
            String question,
            String sql,
            List<Map<String, Object>> rows,
            String language,
            SemanticSqlValidator.ResultAssessment assessment,
            QuestionIntent intent) {
        Instant startedAt = Instant.now();
        String groundedFacts = groundedFacts(rows, language, assessment, intent);
        int level = complexityLevel(question, intent);
        boolean isRanking = intent != null && intent.rankingQuestion();
        Optional<String> rankingListing = isRanking ? deterministicRankingAnswer(rows, language, intent) : Optional.empty();
        String levelInstructions = levelInstructions(level, rankingListing.isPresent());
        String prompt = template()
                .replace("{narration_type}", narrationType(intent))
                .replace("{level_instructions}", levelInstructions)
                .replace("{question}", question)
                .replace("{sql}", sql == null ? "(not provided)" : sql)
                .replace("{assessment}", assessmentAsJson(assessment))
                .replace("{grounded_facts}", groundedFacts)
                .replace("{rows}", rowsAsCompactJson(rows))
                .replace("{language}", language == null || language.isBlank() ? "English" : language);
        String llmAnswer = sanitizeAnswer(narratorModel.chat(prompt));
        String validatedLlmAnswer = validateFactualNumbers(llmAnswer, rows);
        String answer = rankingListing.map(listing -> listing + "\n\n" + validatedLlmAnswer).orElse(validatedLlmAnswer);
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    /**
     * Level 1 = simple direct lookups (a single count/total, no analytical wording).
     * Level 2 = analytical questions (rankings, comparisons, breakdowns, summaries, or explicit
     * KPI/analysis wording) that require the full executive-summary/analysis/observations/
     * recommendations/conclusion report.
     * Level 3 = strategic/predictive questions (forecast wording, or genuine risk wording) that
     * additionally require a "Risks and opportunities" section.
     * Deliberately checks the raw question text for risk wording rather than relying solely on the
     * STOCK_RISK intent tag, because that tag also fires on plain "stock" mentions with no risk framing
     * (e.g. "Quel est le stock actuel ?", which the business brief classifies as a simple Level 1 lookup).
     */
    private int complexityLevel(String question, QuestionIntent intent) {
        if (intent == null) {
            return 2;
        }
        boolean riskWording = question != null && RISK_WORDING.matcher(question).find();
        if (riskWording || intent.hasDomain("PREVISIONS")) {
            return 3;
        }
        if (intent.rankingQuestion()
                || intent.comparisonQuestion()
                || intent.breakdownQuestion()
                || intent.summaryQuestion()
                || intent.hasDomain("ANALYSES_KPI")) {
            return 2;
        }
        return 1;
    }

    private String levelInstructions(int level, boolean rankingAlreadyListed) {
        StringBuilder note = new StringBuilder();
        if (rankingAlreadyListed) {
            note.append("The full ranked list (every returned row, one line per item, using all its columns) has "
                    + "already been shown to the user verbatim above your answer. Do NOT repeat, re-list, or "
                    + "restate the individual figures already shown — write only the narrative sections below, "
                    + "referring back to the leaders already shown by name and interpreting what the ranking "
                    + "means, not recopying its numbers.\n\n");
        }
        note.append("Avoid repetition across sections: each section must add a genuinely new angle, never restate "
                + "the same figure or sentence that already appeared earlier in the answer.\n\n"
                + "Before finishing, make sure every section is fully written and the answer ends on a complete "
                + "sentence with the Conclusion present — if you are running low on room, shorten each section "
                + "rather than cutting the answer off before the end.\n\n");
        return switch (level) {
            case 1 -> note + """
                    Complexity level: 1 (simple direct question).
                    Produce only these two sections, headed naturally in {language}: Executive summary, \
                    Conclusion. Keep it short: the Executive summary gives the direct answer plus at most 1-2 \
                    sentences of business interpretation only if it genuinely adds value; the Conclusion is a \
                    single closing sentence. Do NOT add Business analysis, Observations, Risks, or \
                    Recommendations sections for this level, and never pad a simple factual answer into a \
                    long report.
                    """;
            case 3 -> note + """
                    Complexity level: 3 (strategic / predictive question).
                    Produce the full report using exactly these five sections, headed naturally in {language}: \
                    Executive summary, Business analysis, Risks and opportunities, Recommendations, Conclusion. \
                    Do not add a separate Observations section at this level — fold any notable trend or anomaly \
                    into Business analysis or into Risks and opportunities, whichever fits better. In "Risks and \
                    opportunities", name concrete risks and opportunities visible in the data; if the data cannot \
                    support a conclusion on some point, say so explicitly rather than guessing. When relevant, \
                    state the assumptions and limits of any estimate. Never omit the Conclusion.
                    """;
            default -> note + """
                    Complexity level: 2 (analytical question).
                    Produce the full report using exactly these five sections, headed naturally in {language}: \
                    Executive summary, Business analysis, Key observations, Recommendations, Conclusion. \
                    Every section is mandatory for this level. Never omit the Conclusion.
                    """;
        };
    }

    /**
     * For pure conversational/meta questions (greetings, "explain what a KPI is", etc.) that QaService detects
     * and routes here without ever running intent-based SQL generation. Deliberately a much simpler prompt than
     * narrate()/narrateUnavailable() — no report structure, no rows, no SQL — just a short, natural reply from
     * general business knowledge. Identity ("who are you") and advice ("give me advice") questions are handled
     * by the more specific narrateIdentity()/narrateAdvice() below instead of this generic method.
     */
    NarrationResult narrateConversational(String question, String language) {
        Instant startedAt = Instant.now();
        String prompt = """
                You are a friendly, professional Business Intelligence assistant chatting naturally with a \
                user in %s. This is a conversational or meta question (a greeting, a request for general \
                advice, or a request to explain a business concept) — NOT a request for specific company data. \
                Answer naturally and briefly (1-4 sentences) in a warm, professional tone. Do not use report \
                headings or sections. Do not invent specific company figures — if asked for general advice or \
                an explanation of a concept (e.g. what a KPI is, how to interpret data), answer from general \
                business knowledge, not from any specific dataset.

                Question: %s

                Answer:
                """
                .formatted(language == null || language.isBlank() ? "English" : language, question);
        String answer = sanitizeAnswer(narratorModel.chat(prompt));
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    /**
     * Official self-presentation for identity questions ("Qui es-tu ?", "Présente-toi.", "Que peux-tu faire ?").
     * Deterministic (not left to the LLM's own phrasing) so the role/capabilities/decision-support framing the
     * business asked for is guaranteed exact and consistent on every call, instead of drifting across calls.
     */
    NarrationResult narrateIdentity(String language) {
        Instant startedAt = Instant.now();
        String answer = "fr".equalsIgnoreCase(language)
                ? "Je suis l'Assistant Business Intelligence de LPN Maroc (Librairie Papeterie Nationale). "
                        + "Mon rôle est de vous aider à comprendre vos données commerciales — ventes, achats, "
                        + "stock, clients et fournisseurs — en interrogeant vos données réelles et en les "
                        + "traduisant en analyses claires. Je peux répondre à des questions chiffrées (chiffre "
                        + "d'affaires, classements, comparaisons), identifier des tendances et des risques, et "
                        + "proposer des recommandations concrètes pour vous aider à prendre de meilleures "
                        + "décisions."
                : "I'm LPN Maroc's (Librairie Papeterie Nationale) Business Intelligence Assistant. My role is "
                        + "to help you understand your business data — sales, purchases, stock, customers, and "
                        + "suppliers — by querying your real data and turning it into clear analysis. I can "
                        + "answer data questions (revenue, rankings, comparisons), surface trends and risks, "
                        + "and offer concrete recommendations to support better decisions.";
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    /**
     * For advice/interpretation questions ("Donne-moi des conseils.", "Comment interpréter ces résultats ?",
     * "Que me recommandes-tu ?") — richer than narrateConversational's short reply: a brief qualitative
     * business analysis plus concrete recommendations, but still general-business-knowledge only, no SQL/data
     * access, and no invented company-specific figures.
     */
    NarrationResult narrateAdvice(String question, String language) {
        Instant startedAt = Instant.now();
        String prompt = """
                You are an experienced Business Intelligence consultant. A user in %s is asking for general \
                advice, strategic guidance, or help interpreting results/KPIs/decisions — NOT for a specific \
                data query, and you have no SQL results to work from here. Answer with a short, genuinely \
                useful qualitative business analysis: 2-4 sentences of practical guidance, followed by 2-3 \
                concrete recommendations. Base this only on general BI/business/management best practices — \
                never invent specific figures, KPI values, trends, or company data, since none were provided. \
                Explicitly and clearly state, in the answer itself, that this guidance is based on general \
                best practices and not on an analysis of LPN's actual data (since none was queried for this \
                question) — do not let it read as if it were derived from LPN's numbers. Keep it natural \
                prose with at most a short list for the recommendations; do not use a heavy report structure.

                Question: %s

                Answer:
                """
                .formatted(language == null || language.isBlank() ? "English" : language, question);
        String answer = sanitizeAnswer(narratorModel.chat(prompt));
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    /**
     * Used when SQL generation/execution could not produce rows at all (e.g. GENERATION_ERROR).
     * Developer-facing error codes/logs are untouched by this — this only shapes the user-facing message,
     * asking the narrator model to explain what happened and what it can still say, instead of a bare error.
     */
    /**
     * For questions that read as a reference to prior context ("et pour 2025", "les cinq
     * premiers", "pourquoi") but arrive with no previous turn to attach to and no detectable
     * business domain — genuinely ambiguous rather than a fresh topic. Asks a short, natural
     * clarifying question instead of guessing at a SQL query from an incomplete question.
     */
    NarrationResult narrateClarificationRequest(String question, String language) {
        Instant startedAt = Instant.now();
        String prompt = """
                You are a professional Business Intelligence assistant. The user's question in %s reads like \
                a reference to something said earlier in the conversation (e.g. a bare year, "compared to last \
                year", "the top five", "why") — but there is no earlier question available to resolve what it \
                refers to, and no business domain (sales, purchases, stock, customers, suppliers, products) is \
                identifiable in it either. Do not guess or invent an interpretation. Ask ONE short, natural \
                clarifying question that helps the person state what they actually want to know — for example \
                what metric, what entity, or what time period. Keep it to 1-2 sentences, friendly and direct, \
                no report structure.

                Question: %s

                Answer:
                """
                .formatted(language == null || language.isBlank() ? "English" : language, question);
        String answer = sanitizeAnswer(narratorModel.chat(prompt));
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    NarrationResult narrateUnavailable(String question, String language, QuestionIntent intent, String reason) {
        Instant startedAt = Instant.now();
        String prompt = template()
                .replace("{narration_type}", narrationType(intent) + " (DATA UNAVAILABLE)")
                .replace("{level_instructions}", "No SQL result is available for this question. Do not force the "
                        + "five/six-section report structure — instead write a short, graceful explanation of "
                        + "what is missing, what would normally be available, and what you can still usefully "
                        + "say from general business knowledge of the domains involved.")
                .replace("{question}", question)
                .replace("{sql}", "(no SQL could be executed for this question)")
                .replace("{assessment}", "{\"dataAvailable\": false, \"reason\": \"" + reason.replace("\"", "'") + "\"}")
                .replace("{grounded_facts}", "No query result is available for this question (reason: " + reason
                        + "). Do not invent figures. Explain plainly what is missing, and — using only general "
                        + "knowledge of the business domains involved (" + (intent == null ? "general" : intent.domains())
                        + ") — suggest what the person could check or ask instead.")
                .replace("{rows}", "[]")
                .replace("{language}", language == null || language.isBlank() ? "English" : language);
        String answer = sanitizeAnswer(narratorModel.chat(prompt));
        long latencyMs = Math.max(1, Duration.between(startedAt, Instant.now()).toMillis());
        return new NarrationResult(answer, latencyMs);
    }

    private String narrationType(QuestionIntent intent) {
        if (intent == null) {
            return "GENERAL";
        }
        if (intent.hasIntent("STOCK_RISK")) {
            return "STOCK";
        }
        if (intent.hasDomain("PREVISIONS")) {
            return "FORECAST";
        }
        if (intent.comparisonQuestion()) {
            return "COMPARISON";
        }
        if (intent.supplierRankingQuestion() || intent.hasDomain("FOURNISSEURS")) {
            return "SUPPLIER";
        }
        if (intent.customerRankingQuestion() || intent.hasDomain("CLIENTS")) {
            return "CUSTOMER";
        }
        if (intent.rankingQuestion()) {
            return "RANKING";
        }
        if (intent.hasDomain("ANALYSES_KPI") && !intent.summaryQuestion() && !intent.breakdownQuestion()) {
            return "KPI";
        }
        if (intent.summaryQuestion() || intent.breakdownQuestion() || intent.trendQuestion()) {
            return "ANALYSIS";
        }
        if (intent.salesMetricQuestion() || intent.invoiceMetricQuestion() || intent.orderMetricQuestion()) {
            return "KPI";
        }
        return "GENERAL";
    }

    /**
     * Reuses the old deterministic-answer builders (previously returned directly as the final answer)
     * purely as an anti-hallucination anchor: exact, code-computed values the LLM must not contradict.
     * The LLM still writes the full structured narration around them.
     */
    private String groundedFacts(
            List<Map<String, Object>> rows,
            String language,
            SemanticSqlValidator.ResultAssessment assessment,
            QuestionIntent intent) {
        StringBuilder facts = new StringBuilder();
        deterministicAggregateAnswer(rows, language, assessment).ifPresent(facts::append);
        deterministicRankingAnswer(rows, language, intent).ifPresent(fact -> {
            if (!facts.isEmpty()) {
                facts.append('\n');
            }
            facts.append(fact);
        });
        deterministicComparisonAnswer(rows, language, intent).ifPresent(fact -> {
            if (!facts.isEmpty()) {
                facts.append('\n');
            }
            facts.append(fact);
        });
        remarkableValueFacts(rows).ifPresent(fact -> {
            if (!facts.isEmpty()) {
                facts.append('\n');
            }
            facts.append(fact);
        });
        if (facts.isEmpty()) {
            return "(no pre-computed summary; read exact values directly from Rows below)";
        }
        return facts.toString();
    }

    /**
     * Computes the max/min row per numeric column from the FULL row set (not the row JSON sent to the
     * prompt, which is capped at MAX_ROWS_FOR_PROMPT for context-length reasons). This guarantees that
     * "highest/lowest" claims are always grounded correctly even when a result has more rows than fit in
     * the prompt — the LLM is told to reuse these exact figures rather than eyeballing a possibly-truncated
     * row list.
     */
    private Optional<String> remarkableValueFacts(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() < 2) {
            return Optional.empty();
        }
        Map<String, Object> firstRow = rows.get(0);
        List<String> numericColumns = firstRow.keySet().stream()
                .filter(column -> !isLabelColumn(column))
                .filter(column -> firstRow.get(column) instanceof Number)
                .toList();
        if (numericColumns.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder facts = new StringBuilder();
        for (String column : numericColumns) {
            Map<String, Object> maxRow = null;
            Map<String, Object> minRow = null;
            double maxValue = Double.NEGATIVE_INFINITY;
            double minValue = Double.POSITIVE_INFINITY;
            for (Map<String, Object> row : rows) {
                Object raw = row.get(column);
                if (!(raw instanceof Number number)) {
                    continue;
                }
                double value = number.doubleValue();
                if (value > maxValue) {
                    maxValue = value;
                    maxRow = row;
                }
                if (value < minValue) {
                    minValue = value;
                    minRow = row;
                }
            }
            if (maxRow == null || minRow == null) {
                continue;
            }
            String maxLabel = rowLabel(maxRow);
            String minLabel = rowLabel(minRow);
            facts.append("Highest ").append(column).append(": ").append(maxLabel).append(" = ")
                    .append(formatValue(column, maxRow.get(column))).append(". ");
            if (!maxLabel.equals(minLabel)) {
                facts.append("Lowest ").append(column).append(": ").append(minLabel).append(" = ")
                        .append(formatValue(column, minRow.get(column))).append(". ");
            }
        }
        return facts.isEmpty() ? Optional.empty() : Optional.of(facts.toString().trim());
    }

    // Numeric token in free text: handles French space/narrow-space-grouped thousands ("12 345"),
    // plain thousands commas ("12,345"), decimal comma or point, an optional trailing %, and plain
    // integers. Deliberately permissive on formatting since it only extracts candidates — every
    // candidate is then checked against the real data before being trusted.
    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "-?\\d{1,3}(?:[ \u00A0,]\\d{3})+(?:[.,]\\d+)?%?|-?\\d+(?:[.,]\\d+)?%?");
    // Numbers this small are essentially always list/section numbering, a count of items just
    // discussed in the same sentence ("2-3 recommandations"), or a month/day — flagging every one
    // would make the filter noisy without protecting against the kind of fabricated business figure
    // (amounts, percentages, KPI values) this validation exists to catch.
    private static final int UNCHECKED_SMALL_INTEGER_THRESHOLD = 12;

    /**
     * Final reliability pass: extracts every numeric token the LLM's own narration text contains,
     * and drops (replaces with a non-numeric placeholder) any that cannot be matched — within a
     * small rounding tolerance — against a real value: every number in every row/column of the full
     * result set (not just what fit in the prompt), or a simple exact aggregate (sum/average/count)
     * computed directly from those same rows. This never edits a number that IS justified, never
     * touches the deterministic ranking listing (built separately, directly from the rows, and
     * never passed through this method), and never touches recommendation text — only numbers.
     * Applied only in narrate(), the data-backed path; the conversational/advice/identity/unavailable
     * narrations never claim to be data-derived in the first place, so they're out of scope here.
     */
    private String validateFactualNumbers(String answer, List<Map<String, Object>> rows) {
        if (answer == null || answer.isBlank() || rows == null || rows.isEmpty()) {
            return answer;
        }
        Set<Double> justified = justifiedNumericValues(rows);
        if (justified.isEmpty()) {
            return answer;
        }

        Matcher matcher = NUMERIC_TOKEN.matcher(answer);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            String token = matcher.group();
            Double parsed = parseNarrationNumber(token);
            boolean keep = parsed == null
                    || (Math.abs(parsed) < UNCHECKED_SMALL_INTEGER_THRESHOLD && !token.contains("%"))
                    || isPlausibleYear(parsed)
                    || matchesJustifiedValue(parsed, token.contains("%"), justified);
            result.append(answer, lastEnd, matcher.start());
            result.append(keep ? token : "[valeur non vérifiable]");
            lastEnd = matcher.end();
        }
        result.append(answer.substring(lastEnd));
        return result.toString();
    }

    private boolean isPlausibleYear(double value) {
        return value >= 2000 && value <= 2100 && value == Math.floor(value);
    }

    private boolean matchesJustifiedValue(double candidate, boolean isPercent, Set<Double> justified) {
        double tolerance = Math.max(0.5, Math.abs(candidate) * 0.01);
        for (double known : justified) {
            if (Math.abs(known - candidate) <= tolerance) {
                return true;
            }
            // A narration stating a share/percentage is legitimate when it's an exact (or
            // near-exact, allowing for rounding) proportion of a real total in the data.
            if (isPercent) {
                for (double denominator : justified) {
                    if (denominator == 0) {
                        continue;
                    }
                    double impliedPct = (known / denominator) * 100.0;
                    if (Math.abs(impliedPct - candidate) <= 0.15) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Every raw numeric value across every row and column of the FULL result set (not the
     * prompt-capped subset), plus the simple exact aggregates (sum, average, count) a narration
     * would legitimately state — computed the same straightforward way a person checking the data
     * by hand would, never estimated.
     */
    private Set<Double> justifiedNumericValues(List<Map<String, Object>> rows) {
        Set<Double> values = new HashSet<>();
        values.add((double) rows.size());
        Map<String, List<Double>> byColumn = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    double value = number.doubleValue();
                    values.add(value);
                    byColumn.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(value);
                }
            }
        }
        for (List<Double> columnValues : byColumn.values()) {
            double sum = columnValues.stream().mapToDouble(Double::doubleValue).sum();
            values.add(sum);
            values.add(sum / columnValues.size());
        }
        return values;
    }

    private Double parseNarrationNumber(String token) {
        String cleaned = token.replace("%", "").replace("\u00A0", "").replace(" ", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }
        long commaCount = cleaned.chars().filter(c -> c == ',').count();
        long dotCount = cleaned.chars().filter(c -> c == '.').count();
        try {
            if (commaCount > 0 && dotCount > 0) {
                // Both present: whichever comes last is the decimal separator, the other is
                // thousands grouping and gets dropped.
                cleaned = cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
                        ? cleaned.replace(".", "").replace(",", ".")
                        : cleaned.replace(",", "");
            } else if (commaCount > 1 || dotCount > 1) {
                // More than one of the same separator only happens with thousands grouping
                // ("1,234,567" or "1.234.567") — never a valid decimal number.
                cleaned = cleaned.replace(",", "").replace(".", "");
            } else if (commaCount == 1) {
                // A single comma: 3 digits after it with nothing before wider than 3 digits reads
                // as thousands grouping ("12,345"); otherwise it's a decimal comma ("12,5").
                int digitsAfter = cleaned.length() - cleaned.indexOf(',') - 1;
                cleaned = digitsAfter == 3 ? cleaned.replace(",", "") : cleaned.replace(",", ".");
            }
            // A single dot is already valid Java decimal syntax — leave it as-is.
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String rowLabel(Map<String, Object> row) {
        String label = firstNonBlank(
                valueAsString(row, "customername"),
                valueAsString(row, "customer_name"),
                valueAsString(row, "client_name"),
                valueAsString(row, "businesspartnername"),
                valueAsString(row, "supplier_name"),
                valueAsString(row, "suppliername"),
                valueAsString(row, "product_name"),
                valueAsString(row, "productname"),
                valueAsString(row, "category_name"),
                valueAsString(row, "categoryname"),
                valueAsString(row, "name"));
        return label != null && !label.isBlank() ? label : "a row";
    }

    private Optional<String> deterministicAggregateAnswer(
            List<Map<String, Object>> rows,
            String language,
            SemanticSqlValidator.ResultAssessment assessment) {
        if (rows == null || rows.size() != 1 || assessment == null || !assessment.warnings().stream()
                .anyMatch(warning -> warning.toLowerCase(Locale.ROOT).contains("aggregate"))) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.getFirst();
        if (row.isEmpty() || row.values().stream().anyMatch(value -> value instanceof Map<?, ?> || value instanceof List<?>)) {
            return Optional.empty();
        }
        boolean french = language != null && language.toLowerCase(Locale.ROOT).contains("french");
        Optional<String> orderVsInvoice = orderVsInvoiceComparisonAnswer(row, french);
        if (orderVsInvoice.isPresent()) {
            return orderVsInvoice;
        }
        Optional<String> orderTotalAverage = totalAverageAnswer(row, french, "order");
        if (orderTotalAverage.isPresent()) {
            return orderTotalAverage;
        }
        Optional<String> invoiceTotalAverage = totalAverageAnswer(row, french, "invoice");
        if (invoiceTotalAverage.isPresent()) {
            return invoiceTotalAverage;
        }
        if (row.size() <= 4) {
            String pairs = row.entrySet().stream()
                    .map(entry -> humanizeColumn(entry.getKey()) + " = " + formatValue(entry.getKey(), entry.getValue()))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return Optional.of(french ? "Résultat: " + pairs + "." : "Result: " + pairs + ".");
        }
        return Optional.empty();
    }

    private Optional<String> deterministicRankingAnswer(
            List<Map<String, Object>> rows,
            String language,
            QuestionIntent intent) {
        if (rows == null || rows.isEmpty() || intent == null || !intent.rankingQuestion()) {
            return Optional.empty();
        }
        boolean french = language != null && language.toLowerCase(Locale.ROOT).contains("french");
        String lines = java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> (index + 1) + ". " + rankingLine(rows.get(index), french, intent))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (lines.isBlank()) {
            return Optional.empty();
        }
        String intro = rankingIntro(rows.size(), french, intent);
        return Optional.of(intro + "\n" + lines);
    }

    private String rankingIntro(int rowCount, boolean french, QuestionIntent intent) {
        if (intent != null && intent.customerRankingQuestion()) {
            if (intent.salesMetricQuestion() || intent.invoiceMetricQuestion()) {
                return french
                        ? "Voici les " + rowCount + " clients classés par chiffre d'affaires facturé sur la période demandée:"
                        : "Here are the top " + rowCount + " customers by invoiced sales for the requested period:";
            }
            return french
                    ? "Voici les " + rowCount + " clients classés par valeur totale des commandes sur la période demandée:"
                    : "Here are the top " + rowCount + " customers by total order value for the requested period:";
        }
        if (intent != null && intent.productRankingQuestion()) {
            if (intent.salesMetricQuestion() || intent.invoiceMetricQuestion()) {
                return french
                        ? "Voici les " + rowCount + " produits classés par chiffre d'affaires facturé sur la période demandée:"
                        : "Here are the top " + rowCount + " products by invoiced sales for the requested period:";
            }
            return french
                    ? "Voici les " + rowCount + " produits classés par valeur totale des commandes sur la période demandée:"
                    : "Here are the top " + rowCount + " products by total order value for the requested period:";
        }
        return french
                ? "Voici les " + rowCount + " premiers résultats retournés:"
                : "Here are the top " + rowCount + " returned results:";
    }

    private String rankingLine(Map<String, Object> row, boolean french, QuestionIntent intent) {
        String label = firstNonBlank(
                valueAsString(row, "customername"),
                valueAsString(row, "customer_name"),
                valueAsString(row, "client_name"),
                valueAsString(row, "businesspartnername"),
                valueAsString(row, "supplier_name"),
                valueAsString(row, "suppliername"),
                valueAsString(row, "product_name"),
                valueAsString(row, "productname"),
                valueAsString(row, "category_name"),
                valueAsString(row, "categoryname"),
                valueAsString(row, "name"),
                labelledId(row, "c_bpartner_id", french ? "client" : "customer"),
                labelledId(row, "m_product_id", french ? "produit" : "product"),
                labelledId(row, "product_id", french ? "produit" : "product"));
        if (label == null) {
            label = french ? "Ligne résultat" : "Result row";
        }

        List<String> parts = row.entrySet().stream()
                .filter(entry -> !isLabelColumn(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .map(entry -> rankingMetricLabel(entry.getKey(), french, intent) + " : " + formatValue(entry.getKey(), entry.getValue()))
                .toList();
        if (parts.isEmpty()) {
            return label;
        }
        return label + " — " + String.join(" — ", parts);
    }

    private Optional<String> deterministicComparisonAnswer(
            List<Map<String, Object>> rows,
            String language,
            QuestionIntent intent) {
        if (rows == null || rows.size() < 2 || intent == null || !intent.comparisonQuestion()) {
            return Optional.empty();
        }
        boolean french = language != null && language.toLowerCase(Locale.ROOT).contains("french");
        String labelColumn = firstMatchingColumn(rows.getFirst(), "month", "date", "order_date", "invoice_date", "customername", "customer_name", "name");
        String metricColumn = firstMetricColumn(rows.getFirst());
        if (labelColumn == null || metricColumn == null) {
            return Optional.empty();
        }

        List<String> lines = rows.stream()
                .map(row -> "- " + formatComparisonLabel(row.get(labelColumn), french)
                        + ": " + formatValue(metricColumn, row.get(metricColumn)))
                .toList();
        Optional<Map<String, Object>> winner = rows.stream()
                .filter(row -> row.get(metricColumn) instanceof Number)
                .max((left, right) -> new BigDecimal(left.get(metricColumn).toString())
                        .compareTo(new BigDecimal(right.get(metricColumn).toString())));
        String metricLabel = comparisonMetricLabel(metricColumn, french, intent);
        String intro = french
                ? "Comparaison de " + metricLabel + " sur la période demandée:"
                : "Comparison of " + metricLabel + " for the requested period:";
        StringBuilder answer = new StringBuilder(intro).append('\n').append(String.join("\n", lines));
        winner.ifPresent(row -> answer.append('\n').append(french ? "Le niveau le plus élevé est " : "The highest value is ")
                .append(formatComparisonLabel(row.get(labelColumn), french))
                .append(french ? " avec " : " with ")
                .append(formatValue(metricColumn, row.get(metricColumn)))
                .append('.'));
        return Optional.of(answer.toString());
    }

    private boolean isLabelColumn(String column) {
        String normalized = column == null ? "" : column.toLowerCase(Locale.ROOT);
        return normalized.equals("customer_name")
                || normalized.equals("customername")
                || normalized.equals("client_name")
                || normalized.equals("businesspartnername")
                || normalized.equals("supplier_name")
                || normalized.equals("suppliername")
                || normalized.equals("product_name")
                || normalized.equals("productname")
                || normalized.equals("category_name")
                || normalized.equals("categoryname")
                || normalized.equals("name");
    }

    private String labelledId(Map<String, Object> row, String column, String label) {
        Object value = row.get(column);
        return value == null ? null : label + " " + formatValue(column, value);
    }

    private String valueAsString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Optional<String> totalAverageAnswer(Map<String, Object> row, boolean french, String subject) {
        Object total = findByColumnContains(row, "total", subject);
        Object average = findByColumnContains(row, "avg", subject);
        if (average == null) {
            average = findByColumnContains(row, "average", subject);
        }
        if (total == null || average == null) {
            return Optional.empty();
        }
        String formattedTotal = formatValue("total_" + subject + "_value", total);
        String formattedAverage = formatValue("average_" + subject + "_value", average);
        if (french && subject.equals("order")) {
            return Optional.of("Le montant total des commandes est de " + formattedTotal
                    + " et la valeur moyenne par commande est de " + formattedAverage + ".");
        }
        if (french && subject.equals("invoice")) {
            return Optional.of("Le montant total des factures est de " + formattedTotal
                    + " et la valeur moyenne par facture est de " + formattedAverage + ".");
        }
        String businessSubject = subject.equals("order") ? "orders" : "invoices";
        return Optional.of("The total value of " + businessSubject + " is " + formattedTotal
                + " and the average value is " + formattedAverage + ".");
    }

    private Object findByColumnContains(Map<String, Object> row, String needle, String subject) {
        return row.entrySet().stream()
                .filter(entry -> {
                    String column = entry.getKey().toLowerCase(Locale.ROOT);
                    return column.contains(needle) && column.contains(subject);
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String humanizeColumn(String column) {
        return column == null ? "value" : column.replace('_', ' ');
    }

    private String rankingMetricLabel(String column, boolean french, QuestionIntent intent) {
        String normalized = column == null ? "" : column.toLowerCase(Locale.ROOT).replace("_", "");
        if (normalized.contains("invoicecount")) {
            return french ? "nombre de factures" : "invoice count";
        }
        if (normalized.contains("linecount")) {
            return french ? "nombre de lignes" : "line count";
        }
        if (normalized.contains("totalquantity")) {
            return french ? "quantité totale" : "total quantity";
        }
        if (normalized.contains("ordercount")) {
            return french ? "nombre de commandes" : "order count";
        }
        if (normalized.contains("totalsales")) {
            return french ? "chiffre d'affaires total" : "total sales";
        }
        if (normalized.contains("totalinvoicedsales")) {
            return french ? "chiffre d'affaires facturé" : "invoiced sales";
        }
        if (normalized.contains("totalordervalue")) {
            return french ? "valeur totale des commandes" : "total order value";
        }
        if (normalized.contains("averageinvoice")) {
            return french ? "valeur moyenne par facture" : "average invoice value";
        }
        if (normalized.contains("averageordervalue")) {
            return french ? "valeur moyenne par commande" : "average order value";
        }
        if (normalized.contains("averagelinevalue")) {
            return french ? "valeur moyenne par ligne" : "average line value";
        }
        if (normalized.contains("total") && intent != null && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())) {
            return french ? "chiffre d'affaires total" : "total sales";
        }
        return humanizeColumn(column);
    }

    private Optional<String> orderVsInvoiceComparisonAnswer(Map<String, Object> row, boolean french) {
        Object orderValue = findByNormalizedColumn(row, "totalordervalue");
        Object invoicedSales = findByNormalizedColumn(row, "totalinvoicedsales");
        if (orderValue == null || invoicedSales == null) {
            return Optional.empty();
        }
        Object difference = findByNormalizedColumn(row, "difference");
        Object coverage = findByNormalizedColumn(row, "invoicecoveragepercent");
        String formattedOrderValue = formatValue("totalordervalue", orderValue);
        String formattedInvoicedSales = formatValue("totalinvoicedsales", invoicedSales);
        String formattedDifference = difference == null ? null : formatValue("difference", difference);
        String formattedCoverage = coverage == null ? null : formatValue("invoicecoveragepercent", coverage);
        if (french) {
            String answer = "Sur la période demandée, la valeur totale des commandes est de " + formattedOrderValue
                    + " et le chiffre d'affaires facturé est de " + formattedInvoicedSales + ".";
            if (formattedDifference != null) {
                answer += " L'écart commandes - factures est de " + formattedDifference + ".";
            }
            if (formattedCoverage != null) {
                answer += " Le taux de couverture facturée est de " + formattedCoverage + " %.";
            }
            return Optional.of(answer);
        }
        String answer = "For the requested period, total order value is " + formattedOrderValue
                + " and invoiced sales are " + formattedInvoicedSales + ".";
        if (formattedDifference != null) {
            answer += " The order-minus-invoice difference is " + formattedDifference + ".";
        }
        if (formattedCoverage != null) {
            answer += " Invoice coverage is " + formattedCoverage + "%.";
        }
        return Optional.of(answer);
    }

    private Object findByNormalizedColumn(Map<String, Object> row, String needle) {
        return row.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().toLowerCase(Locale.ROOT).replace("_", "").equals(needle))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String comparisonMetricLabel(String column, boolean french, QuestionIntent intent) {
        if (intent != null && (intent.salesMetricQuestion() || intent.invoiceMetricQuestion())) {
            return french ? "chiffre d'affaires facturé" : "invoiced sales";
        }
        if (intent != null && intent.orderMetricQuestion()) {
            return french ? "valeur des commandes" : "order value";
        }
        return french ? rankingMetricLabel(column, true, intent) : rankingMetricLabel(column, false, intent);
    }

    private String firstMatchingColumn(Map<String, Object> row, String... candidates) {
        if (row == null) {
            return null;
        }
        for (String candidate : candidates) {
            for (String column : row.keySet()) {
                if (column != null && column.equalsIgnoreCase(candidate)) {
                    return column;
                }
            }
        }
        return null;
    }

    private String firstMetricColumn(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return row.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Number)
                .filter(entry -> {
                    String column = entry.getKey().toLowerCase(Locale.ROOT);
                    return !column.endsWith("_id") && !column.equals("id") && !column.contains("count");
                })
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String formatComparisonLabel(Object value, boolean french) {
        if (value == null) {
            return french ? "valeur inconnue" : "unknown value";
        }
        String text = String.valueOf(value);
        if (text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            try {
                LocalDate date = LocalDate.parse(text.substring(0, 10));
                return date.format(DateTimeFormatter.ofPattern("MMMM yyyy", french ? Locale.FRANCE : Locale.ENGLISH));
            } catch (RuntimeException ignored) {
                return text;
            }
        }
        return text;
    }

    private String formatValue(String column, Object value) {
        if (!(value instanceof Number)) {
            return String.valueOf(value);
        }
        BigDecimal decimal = new BigDecimal(value.toString());
        String lowerColumn = column == null ? "" : column.toLowerCase(Locale.ROOT);
        if (lowerColumn.contains("count") || lowerColumn.endsWith("_id")) {
            return decimal.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        boolean moneyLike = lowerColumn.contains("total")
                || lowerColumn.contains("amount")
                || lowerColumn.contains("value")
                || lowerColumn.contains("avg")
                || lowerColumn.contains("average")
                || lowerColumn.contains("price");
        BigDecimal rounded = moneyLike ? decimal.setScale(2, RoundingMode.HALF_UP) : decimal.stripTrailingZeros();
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return format.format(rounded).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    private String sanitizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        return META_NOTE_PATTERN.matcher(answer.trim()).replaceFirst("").trim();
    }

    private String rowsAsCompactJson(List<Map<String, Object>> rows) {
        int totalRows = rows == null ? 0 : rows.size();
        List<Map<String, Object>> limitedRows = rows == null ? List.of() : rows.stream().limit(MAX_ROWS_FOR_PROMPT).toList();
        try {
            String json = objectMapper.writeValueAsString(limitedRows);
            if (totalRows > MAX_ROWS_FOR_PROMPT) {
                return json + "\n(Note: " + totalRows + " rows were returned in total; only the first "
                        + MAX_ROWS_FOR_PROMPT + " are shown above for length. Do not imply this list is "
                        + "complete — the Grounded facts above already give the correct highest/lowest values "
                        + "computed across all " + totalRows + " rows, not just the ones shown here.)";
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize SQL rows for narration", exception);
        }
    }

    private String assessmentAsJson(SemanticSqlValidator.ResultAssessment assessment) {
        try {
            return objectMapper.writeValueAsString(assessment == null
                    ? new SemanticSqlValidator.ResultAssessment(false, List.of(), List.of())
                    : assessment);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize SQL result assessment for narration", exception);
        }
    }

    private String template() {
        try {
            return StreamUtils.copyToString(promptTemplate.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read answer narration prompt template", exception);
        }
    }

    record NarrationResult(String answer, Long latencyMs) {
    }
}

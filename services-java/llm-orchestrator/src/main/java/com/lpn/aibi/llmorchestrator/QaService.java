package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class QaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QaService.class);
    private static final Pattern DESTRUCTIVE_INTENT = Pattern.compile(
            "(?i)\\b(delete|drop|truncate|update|insert|alter|create|grant|revoke|vacuum|merge|wipe)\\b");
    private static final Pattern OBVIOUS_OUT_OF_SCOPE = Pattern.compile(
            "(?i)\\b(weather|temperature|rain|raining|sunny|wind|forecast outside)\\b");
    // Whole-question whitelist: pure chit-chat / meta questions with no business data request at all.
    // Deliberately a narrow whitelist of the brief's own examples (plus close variants) rather than a broad
    // keyword match, so it never swallows a real business question that happens to share a word (e.g. "Donne-moi
    // les KPI" asks for real data and must NOT match this, while "Explique-moi les KPI" asks for an explanation).
    private static final Pattern CONVERSATIONAL_WHOLE = Pattern.compile(
            "(?iU)^\\s*(bonjour|salut|bonsoir|coucou|hello|hi|hey)\\s*[,!.:;-]*\\s*$"
                    + "|qui es[\\s-]?tu|who are you|pr[eé]sente[\\s-]?toi|introduce yourself"
                    + "|que peux[\\s-]?tu faire|what can you do|comment peux[\\s-]?tu m'?aider|how can you help"
                    + "|comment (vas|allez)[\\s-]?(tu|vous)|how are you"
                    + "|donne[\\s-]?moi des conseils|give me (some )?advice"
                    + "|que (me )?recommande[sz]?[\\s-]?(tu|vous)|what do you recommend"
                    + "|quel(s|le)? (sont|est) (vos|tes) conseils|what(’|')?s your advice"
                    + "|explique[\\s-]?moi les kpis?|explain( to me)? the kpis?|c'?est quoi (un |les )?kpis?"
                    + "|comment interpr[eé]ter (les donn[eé]es|ces r[eé]sultats|ce r[eé]sultat)|how (to|do i) interpret( the)?( results?| data)"
                    + "|comment (prendre( des| une)? d[eé]cisions?|exploiter les r[eé]sultats)|how (to|do i) (make decisions?|use the results)"
                    + "|quelle strat[eé]gie (recommandez|conseillez)[\\s-]?vous|what strategy (do you|would you) recommend"
                    + "|si (vous [eé]tiez|tu [eé]tais) (le )?directeur g[eé]n[eé]ral|if you were the (ceo|general manager)"
                    + "|que feriez[\\s-]?vous( dans cette situation)?|what would you do( in this situation)?"
                    + "|comment am[eé]liorer (notre |la )?rentabilit[eé]|how (to|do we) improve( our)? profitability"
                    + "|quelle d[eé]cision aurait( aujourd'?hui)? le plus (grand impact|d'impact)|which decision would have the (most|biggest) impact"
                    + "|sur quelles actions (devrions[\\s-]?nous |faut[\\s-]?il )?investir|what actions should we (invest|prioritize)"
                    + "|quels sont les principaux facteurs influen[cç]ant les ventes|what are the main factors influencing sales");
    // Subset of CONVERSATIONAL_WHOLE that must answer with the official LPN Maroc BI Assistant
    // self-presentation (deterministic, not left to the LLM's own phrasing) instead of a generic reply.
    private static final Pattern IDENTITY_QUESTION = Pattern.compile(
            "(?iU)qui es[\\s-]?tu|who are you|pr[eé]sente[\\s-]?toi|introduce yourself"
                    + "|que peux[\\s-]?tu faire|what can you do|comment peux[\\s-]?tu m'?aider|how can you help");
    // Subset of CONVERSATIONAL_WHOLE asking for general advice/interpretation/strategic guidance — these get
    // a richer qualitative business-analysis-and-recommendations reply from the LLM (still no SQL/data
    // access, since none of these phrasings name a specific product/supplier/category to query), rather than
    // the short casual reply used for plain greetings/small talk. Deliberately excludes hybrid questions that
    // DO name a specific business entity (e.g. "quels produits promouvoir ?") — those keep flowing through
    // the normal SQL+narration pipeline, which already produces a Recommendations section grounded in real
    // data, consistent with the rule that the LLM must never replace data that's actually available.
    private static final Pattern ADVICE_QUESTION = Pattern.compile(
            "(?iU)donne[\\s-]?moi des conseils|give me (some )?advice"
                    + "|que (me )?recommande[sz]?[\\s-]?(tu|vous)|what do you recommend"
                    + "|quel(s|le)? (sont|est) (vos|tes) conseils|what(’|')?s your advice"
                    + "|comment interpr[eé]ter (les donn[eé]es|ces r[eé]sultats|ce r[eé]sultat)|how (to|do i) interpret( the)?( results?| data)"
                    + "|comment (prendre( des| une)? d[eé]cisions?|exploiter les r[eé]sultats)|how (to|do i) (make decisions?|use the results)"
                    + "|explique[\\s-]?moi les kpis?|explain( to me)? the kpis?|c'?est quoi (un |les )?kpis?"
                    + "|quelle strat[eé]gie (recommandez|conseillez)[\\s-]?vous|what strategy (do you|would you) recommend"
                    + "|si (vous [eé]tiez|tu [eé]tais) (le )?directeur g[eé]n[eé]ral|if you were the (ceo|general manager)"
                    + "|que feriez[\\s-]?vous( dans cette situation)?|what would you do( in this situation)?"
                    + "|comment am[eé]liorer (notre |la )?rentabilit[eé]|how (to|do we) improve( our)? profitability"
                    + "|quelle d[eé]cision aurait( aujourd'?hui)? le plus (grand impact|d'impact)|which decision would have the (most|biggest) impact"
                    + "|sur quelles actions (devrions[\\s-]?nous |faut[\\s-]?il )?investir|what actions should we (invest|prioritize)"
                    + "|quels sont les principaux facteurs influen[cç]ant les ventes|what are the main factors influencing sales");
    // Leading greeting on an otherwise real business question (e.g. "Bonjour, que penses-tu du stock ?"):
    // detected only to prepend a brief greeting reply to the final answer; the business part is left untouched
    // for intent detection, which already ignores "bonjour" naturally since it matches no domain pattern.
    private static final Pattern LEADING_GREETING = Pattern.compile(
            "(?iU)^\\s*(bonjour|salut|bonsoir|coucou|hello|hi|hey)\\s*[,!.:;-]+\\s*\\S");
    // Lightweight conversational memory: a narrow whitelist of elliptical follow-ups that only make sense
    // attached to the previous turn (a bare year, "compare with last year", "the top five", "why", "more
    // detail"). Deliberately narrow and .find()-based rather than a general reference resolver — anything
    // that doesn't match is treated as a fresh topic and gets no memory at all, which is the safe default:
    // a missed follow-up just runs standalone (as today), it never risks misattaching an unrelated question
    // to stale context.
    private static final Pattern FOLLOWUP_REFERENCE = Pattern.compile(
            "(?iU)^\\s*(et\\s+)?(pour\\s+)?(19|20)\\d{2}\\s*\\??\\s*$"
                    + "|^\\s*and\\s+(for\\s+)?(19|20)\\d{2}\\s*\\??\\s*$"
                    + "|compare[rz]?\\b.{0,40}(l'?ann[eé]e|le mois|la p[eé]riode)\\s+(pr[eé]c[eé]dente?|derni[eè]re?|pass[eé]e?)"
                    + "|compared? (with|to) (last year|the previous (year|month|period))"
                    + "|^\\s*(les?\\s+)?(cinq|dix|trois|deux|quatre|\\d+)\\s+premiers?\\b"
                    + "|^\\s*(the\\s+)?(top\\s+)?\\d+\\s+first\\b"
                    + "|^\\s*pourquoi\\s*\\??\\s*$|^\\s*why\\s*\\??\\s*$"
                    + "|plus\\s+de\\s+d[eé]tails?|more\\s+details?");

    private final SqlGenerationService sqlGenerationService;
    private final QuestionIntentDetector questionIntentDetector;
    private final SemanticSqlValidator semanticSqlValidator;
    private final SqlExecutorClient sqlExecutorClient;
    private final AnswerNarrationService answerNarrationService;
    private final PerformanceTraceRepository performanceTraceRepository;
    private final String sqlModel;
    private final String sqlReasoningModel;
    private final String sqlFallbackModel;
    private final String narratorModel;

    QaService(
            SqlGenerationService sqlGenerationService,
            QuestionIntentDetector questionIntentDetector,
            SemanticSqlValidator semanticSqlValidator,
            SqlExecutorClient sqlExecutorClient,
            AnswerNarrationService answerNarrationService,
            PerformanceTraceRepository performanceTraceRepository,
            @Value("${ollama.sql-model}") String sqlModel,
            @Value("${ollama.sql-reasoning-model}") String sqlReasoningModel,
            @Value("${ollama.sql-fallback-model}") String sqlFallbackModel,
            @Value("${ollama.narrator-model}") String narratorModel) {
        this.sqlGenerationService = sqlGenerationService;
        this.questionIntentDetector = questionIntentDetector;
        this.semanticSqlValidator = semanticSqlValidator;
        this.sqlExecutorClient = sqlExecutorClient;
        this.answerNarrationService = answerNarrationService;
        this.performanceTraceRepository = performanceTraceRepository;
        this.sqlModel = sqlModel;
        this.sqlReasoningModel = sqlReasoningModel;
        this.sqlFallbackModel = sqlFallbackModel;
        this.narratorModel = narratorModel;
    }

    QaResponse answer(QaRequest request) {
        Instant startedAt = Instant.now();
        UUID traceId = UUID.randomUUID();
        String language = normalizeLanguage(request.language());
        SqlGenerationService.SqlModelMode requestMode =
                SqlGenerationService.SqlModelMode.from(request.mode(), request.reasoningMode());

        // Lightweight conversational memory: only merge the previous turn in when the current question is a
        // narrow, recognizable reference to it (a bare year, "compare with last year", "the top five", "why",
        // "more detail"). Anything else — including any question that reads as a fresh topic — runs standalone
        // exactly as before, so a natural change of subject is never blocked or contaminated by stale context.
        boolean isFollowup = isFollowupReference(request.question())
                && request.previousQuestion() != null
                && !request.previousQuestion().isBlank();
        String effectiveQuestion = isFollowup
                ? request.previousQuestion() + " — " + request.question()
                : request.question();
        if (isFollowup) {
            LOGGER.info(
                    "[2] QA conversational memory applied traceId={} previousQuestion={} currentQuestion={}",
                    traceId,
                    request.previousQuestion(),
                    request.question());
        }

        QuestionIntent intent = questionIntentDetector.detect(effectiveQuestion);
        LOGGER.debug(
                "QA request traceId={} question={} effectiveQuestion={} language={} mode={}",
                traceId,
                request.question(),
                effectiveQuestion,
                language,
                requestMode.apiValue());

        boolean mixedGreeting = hasLeadingGreeting(request.question()) && !isConversationalOnly(request.question());

        if (isConversationalOnly(request.question())) {
            LOGGER.info("[1] QA conversational-only question detected traceId={} — skipping SQL pipeline entirely", traceId);
            AnswerNarrationService.NarrationResult conversational;
            try {
                if (isIdentityQuestion(request.question())) {
                    conversational = answerNarrationService.narrateIdentity(language);
                } else if (isAdviceQuestion(request.question())) {
                    conversational = answerNarrationService.narrateAdvice(request.question(), language);
                } else {
                    conversational = answerNarrationService.narrateConversational(request.question(), language);
                }
            } catch (RuntimeException exception) {
                conversational = new AnswerNarrationService.NarrationResult(
                        "fr".equalsIgnoreCase(language) ? "Bonjour ! Comment puis-je vous aider ?" : "Hi! How can I help?",
                        null);
                LOGGER.warn("QA conversational narration failed traceId={} exception={}", traceId, exception.toString());
            }
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            saveTrace(
                    traceId,
                    request,
                    "CONVERSATIONAL",
                    null,
                    null,
                    null,
                    false,
                    null,
                    List.of(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null);
            return new QaResponse(
                    traceId,
                    conversational.answer(),
                    null,
                    List.of(),
                    0,
                    List.of(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(null, null, null, null, null, totalLatencyMs),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    null,
                    false,
                    "CONVERSATIONAL",
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null);
        }

        boolean looksAmbiguousWithoutContext = isFollowupReference(request.question())
                && (request.previousQuestion() == null || request.previousQuestion().isBlank())
                && !isConversationalOnly(request.question())
                && intent.domains().isEmpty();

        if (looksAmbiguousWithoutContext) {
            LOGGER.info("[1b] QA ambiguous follow-up with no prior context traceId={} — asking for clarification", traceId);
            AnswerNarrationService.NarrationResult clarification;
            try {
                clarification = answerNarrationService.narrateClarificationRequest(request.question(), language);
            } catch (RuntimeException exception) {
                clarification = new AnswerNarrationService.NarrationResult(
                        "fr".equalsIgnoreCase(language)
                                ? "Pourriez-vous préciser votre question — quel indicateur, quelle entité ou quelle période vous intéresse ?"
                                : "Could you clarify your question — which metric, entity, or time period are you interested in?",
                        null);
                LOGGER.warn("QA clarification narration failed traceId={} exception={}", traceId, exception.toString());
            }
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            saveTrace(
                    traceId,
                    request,
                    "CLARIFICATION_NEEDED",
                    null,
                    null,
                    null,
                    false,
                    null,
                    List.of(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null);
            return new QaResponse(
                    traceId,
                    clarification.answer(),
                    null,
                    List.of(),
                    0,
                    List.of(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(null, null, null, null, null, totalLatencyMs),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    null,
                    false,
                    "CLARIFICATION_NEEDED",
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null);
        }

        if (looksDestructive(request.question())) {
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            LOGGER.info("QA refused destructive intent traceId={} latencyMs={}", traceId, totalLatencyMs);
            saveTrace(
                    traceId,
                    request,
                    "REFUSED",
                    "Destructive database intent was refused before SQL generation.",
                    null,
                    null,
                    false,
                    null,
                    List.of(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null);
            return new QaResponse(
                    traceId,
                    refusalAnswer(language),
                    null,
                    List.of(),
                    0,
                    List.of(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(null, null, null, null, null, totalLatencyMs),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    null,
                    false,
                    "REFUSED",
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    "Destructive database intent was refused before SQL generation.");
        }

        if (looksObviouslyOutOfScope(request.question())) {
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            LOGGER.info("QA refused out-of-scope question traceId={} latencyMs={}", traceId, totalLatencyMs);
            saveTrace(
                    traceId,
                    request,
                    "OUT_OF_SCOPE",
                    "Question was refused by the BI scope guard before SQL generation.",
                    null,
                    null,
                    false,
                    null,
                    List.of(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null);
            return new QaResponse(
                    traceId,
                    outOfScopeAnswer(language),
                    null,
                    List.of(),
                    0,
                    List.of(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(null, null, null, null, null, totalLatencyMs),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    null,
                    false,
                    "OUT_OF_SCOPE",
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    "Question was refused by the BI scope guard before SQL generation.");
        }

        LOGGER.info("[3] QA intent detected traceId={} intents={} domains={}", traceId, intent.intents(), intent.domains());

        SqlGenerationService.SqlGenerationResult generated;
        try {
            LOGGER.info("[4] QA SQL generation starting (template search, then LLM if no template) traceId={}", traceId);
            generated = sqlGenerationService.generateSql(effectiveQuestion, requestMode, intent);
            LOGGER.info(
                    "[6][9] QA SQL generated traceId={} schemaMs={} modelMs={} normalizationMs={} modelUsed={} fallback={} sql={}",
                    traceId,
                    generated.schemaRetrievalLatencyMs(),
                    generated.sqlModelLatencyMs(),
                    generated.sqlNormalizationLatencyMs(),
                    generated.modelUsed(),
                    generated.fallbackUsed(),
                    generated.sql());
        } catch (RuntimeException exception) {
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            String diagnostic = exception.getClass().getName() + ": " + exception.getMessage();
            Throwable rootCause = rootCause(exception);
            String rootDiagnostic = rootCause == exception
                    ? diagnostic
                    : diagnostic + " | root cause: " + rootCause.getClass().getName() + ": " + rootCause.getMessage();
            LOGGER.error(
                    "QA SQL generation failed traceId={} latencyMs={} question={} exception={}",
                    traceId,
                    totalLatencyMs,
                    request.question(),
                    rootDiagnostic,
                    exception);
            saveTrace(
                    traceId,
                    request,
                    "ERROR",
                    rootDiagnostic,
                    null,
                    null,
                    false,
                    null,
                    List.of(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null);
            String userFacingAnswer = withGreetingPrefix(
                    gracefulUnavailableAnswer(effectiveQuestion, language, intent, "SQL_GENERATION_FAILED", traceId),
                    mixedGreeting,
                    language);
            return new QaResponse(
                    traceId,
                    userFacingAnswer,
                    null,
                    List.of(),
                    0,
                    List.of(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(null, null, null, null, null, totalLatencyMs),
                    requestMode.apiValue(),
                    requestMode.isReasoning(),
                    null,
                    false,
                    "GENERATION_ERROR",
                    null,
                    intent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    rootDiagnostic);
        }

        SemanticSqlValidation semanticValidation =
                semanticSqlValidator.validateBeforeExecution(effectiveQuestion, generated.sql(), intent);
        boolean repairAttempted = false;
        String repairedSql = null;
        String sqlForExecution = generated.sql();
        Long sqlRepairLatencyMs = null;
        if (semanticValidation.repairRequired()) {
            repairAttempted = true;
            SqlGenerationService.SqlRepairResult repair = sqlGenerationService.repairSql(
                    effectiveQuestion,
                    generated.sql(),
                    semanticValidation.issues(),
                    generated.retrievedTables(),
                    requestMode,
                    intent);
            repairedSql = repair.sql();
            sqlForExecution = repairedSql;
            sqlRepairLatencyMs = repair.sqlRepairLatencyMs() + repair.sqlRepairNormalizationLatencyMs();
            semanticValidation = semanticSqlValidator.validateBeforeExecution(effectiveQuestion, sqlForExecution, intent);
            LOGGER.info(
                    "QA SQL repaired traceId={} repairMs={} validAfterRepair={} issues={}",
                    traceId,
                    sqlRepairLatencyMs,
                    semanticValidation.valid(),
                    semanticValidation.issues());
        }

        if (!semanticValidation.valid()) {
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            saveTrace(
                    traceId,
                    request,
                    "SEMANTIC_SQL_REJECTED",
                    String.join("; ", semanticValidation.issues()),
                    sqlForExecution,
                    generated.modelUsed(),
                    generated.fallbackUsed(),
                    generated.sql(),
                    generated.retrievedTables(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    generated.schemaRetrievalLatencyMs(),
                    generated.sqlModelLatencyMs(),
                    generated.sqlNormalizationLatencyMs(),
                    null,
                    null,
                    intent,
                    semanticValidation,
                    null,
                    repairAttempted,
                    repairedSql,
                    sqlRepairLatencyMs);
            String semanticAnswer = gracefulUnavailableAnswer(effectiveQuestion, language, intent, "SEMANTIC_SQL_REJECTED", traceId);
            return new QaResponse(
                    traceId,
                    semanticAnswer,
                    sqlForExecution,
                    List.of(),
                    0,
                    generated.retrievedTables(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(
                            generated.schemaRetrievalLatencyMs(),
                            generated.sqlModelLatencyMs(),
                            generated.sqlNormalizationLatencyMs(),
                            null,
                            null,
                            totalLatencyMs),
                    generated.requestMode(),
                    generated.reasoningMode(),
                    generated.modelUsed(),
                    generated.fallbackUsed(),
                    "SEMANTIC_SQL_REJECTED",
                    null,
                    intent,
                    semanticValidation,
                    null,
                    repairAttempted,
                    repairedSql,
                    sqlRepairLatencyMs,
                    String.join("; ", semanticValidation.issues()));
        }

        SqlExecutionResult executed;
        try {
            executed = sqlExecutorClient.execute(sqlForExecution);
            LOGGER.info(
                    "QA SQL executed traceId={} executionMs={} rowCount={}",
                    traceId,
                    executed.latencyMs(),
                    executed.rowCount());
        } catch (SqlExecutionException exception) {
            Instant completedAt = Instant.now();
            long totalLatencyMs = elapsedMs(startedAt, completedAt);
            String executionStatus = executionStatus(exception);
            LOGGER.info(
                    "QA SQL execution did not complete traceId={} status={} latencyMs={} error={}",
                    traceId,
                    executionStatus,
                    totalLatencyMs,
                    detailedExecutionError(exception));
            saveTrace(
                    traceId,
                    request,
                    executionStatus,
                    detailedExecutionError(exception),
                    generated.sql(),
                    generated.modelUsed(),
                    generated.fallbackUsed(),
                    generated.sql(),
                    generated.retrievedTables(),
                    startedAt,
                    completedAt,
                    totalLatencyMs,
                    generated.schemaRetrievalLatencyMs(),
                    generated.sqlModelLatencyMs(),
                    generated.sqlNormalizationLatencyMs(),
                    null,
                    null,
                    intent,
                    semanticValidation,
                    null,
                    repairAttempted,
                    repairedSql,
                    null);
            String executionErrorReason = "SQL_REJECTED".equals(executionStatus) ? "SEMANTIC_SQL_REJECTED" : "EXECUTION_ERROR";
            String executionAnswer = gracefulUnavailableAnswer(effectiveQuestion, language, intent, executionErrorReason, traceId);
            return new QaResponse(
                    traceId,
                    executionAnswer,
                    generated.sql(),
                    List.of(),
                    0,
                    generated.retrievedTables(),
                    totalLatencyMs,
                    new QaLatencyBreakdown(
                            generated.schemaRetrievalLatencyMs(),
                            generated.sqlModelLatencyMs(),
                            generated.sqlNormalizationLatencyMs(),
                            null,
                            null,
                            totalLatencyMs),
                    generated.requestMode(),
                    generated.reasoningMode(),
                    generated.modelUsed(),
                    generated.fallbackUsed(),
                    executionStatus,
                    null,
                    intent,
                    semanticValidation,
                    null,
                    repairAttempted,
                    repairedSql,
                    sqlRepairLatencyMs,
                    detailedExecutionError(exception));
        }

        SemanticSqlValidator.ResultAssessment resultAssessment =
                semanticSqlValidator.assessResult(executed.executedSql(), executed.rows(), intent);
        AnswerNarrationService.NarrationResult narrated;
        try {
            narrated = answerNarrationService.narrate(
                    effectiveQuestion,
                    executed.executedSql(),
                    executed.rows(),
                    language,
                    resultAssessment,
                    intent);
            LOGGER.info("QA answer narrated traceId={} narrationMs={}", traceId, narrated.latencyMs());
        } catch (RuntimeException exception) {
            narrated = new AnswerNarrationService.NarrationResult(fallbackAnswer(language, executed.rows()), null);
            LOGGER.info("QA narration failed; fallback answer used traceId={} error={}", traceId, exception.getMessage());
        }
        String finalAnswer = withGreetingPrefix(narrated.answer(), mixedGreeting, language);

        Instant completedAt = Instant.now();
        long totalLatencyMs = elapsedMs(startedAt, completedAt);
        saveTrace(
                traceId,
                request,
                "SUCCESS",
                null,
                executed.executedSql(),
                generated.modelUsed(),
                generated.fallbackUsed(),
                generated.sql(),
                generated.retrievedTables(),
                startedAt,
                completedAt,
                totalLatencyMs,
                generated.schemaRetrievalLatencyMs(),
                generated.sqlModelLatencyMs(),
                generated.sqlNormalizationLatencyMs(),
                executed.latencyMs(),
                narrated.latencyMs(),
                intent,
                semanticValidation,
                resultAssessment,
                repairAttempted,
                repairedSql,
                sqlRepairLatencyMs);

        QaResponse response = new QaResponse(
                traceId,
                finalAnswer,
                executed.executedSql(),
                executed.rows() == null ? List.of() : executed.rows(),
                executed.rowCount(),
                generated.retrievedTables(),
                totalLatencyMs,
                new QaLatencyBreakdown(
                        generated.schemaRetrievalLatencyMs(),
                        generated.sqlModelLatencyMs(),
                        generated.sqlNormalizationLatencyMs(),
                        executed.latencyMs(),
                        narrated.latencyMs(),
                        totalLatencyMs),
                generated.requestMode(),
                generated.reasoningMode(),
                generated.modelUsed(),
                generated.fallbackUsed(),
                "SUCCESS",
                executed.validation(),
                intent,
                semanticValidation,
                resultAssessment,
                repairAttempted,
                repairedSql,
                sqlRepairLatencyMs,
                null);
        LOGGER.debug(
                "QA response traceId={} status={} rowCount={} latencyMs={} sql={} answer={}",
                traceId,
                response.executionStatus(),
                response.rowCount(),
                response.latencyMs(),
                response.sql(),
                response.answer());
        return response;
    }

    private void saveTrace(
            UUID traceId,
            QaRequest request,
            String status,
            String errorMessage,
            String storedSql,
            String modelUsed,
            boolean fallbackUsed,
            String generatedSql,
            List<RetrievedTable> retrievedTables,
            Instant startedAt,
            Instant completedAt,
            long totalLatencyMs,
            Long schemaRetrievalLatencyMs,
            Long sqlModelLatencyMs,
            Long sqlNormalizationLatencyMs,
            Long sqlExecutionLatencyMs,
            Long narrationLatencyMs,
            QuestionIntent intent,
            SemanticSqlValidation semanticValidation,
            SemanticSqlValidator.ResultAssessment resultAssessment,
            boolean repairAttempted,
            String repairedSql,
            Long sqlRepairLatencyMs) {
        performanceTraceRepository.save(new PerformanceTrace(
                traceId,
                request.clientRequestId(),
                "/v1/qa",
                request.question(),
                status,
                errorMessage,
                SqlGenerationService.SqlModelMode.from(request.mode(), request.reasoningMode()).apiValue(),
                SqlGenerationService.SqlModelMode.from(request.mode(), request.reasoningMode()).isReasoning(),
                sqlModel,
                sqlReasoningModel,
                sqlFallbackModel,
                narratorModel,
                modelUsed,
                fallbackUsed,
                storedSql == null ? generatedSql : storedSql,
                retrievedTables,
                request.frontendStartedAt(),
                startedAt,
                completedAt,
                totalLatencyMs,
                schemaRetrievalLatencyMs,
                sqlModelLatencyMs,
                sqlNormalizationLatencyMs,
                sqlExecutionLatencyMs,
                narrationLatencyMs,
                intent,
                semanticValidation,
                resultAssessment,
                repairAttempted,
                repairedSql,
                sqlRepairLatencyMs));
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isConversationalOnly(String question) {
        return question != null && CONVERSATIONAL_WHOLE.matcher(question).find();
    }

    private static boolean isIdentityQuestion(String question) {
        return question != null && IDENTITY_QUESTION.matcher(question).find();
    }

    private static boolean isAdviceQuestion(String question) {
        return question != null && ADVICE_QUESTION.matcher(question).find();
    }

    private static boolean isFollowupReference(String question) {
        return question != null && FOLLOWUP_REFERENCE.matcher(question).find();
    }

    private static boolean hasLeadingGreeting(String question) {
        return question != null && LEADING_GREETING.matcher(question).find();
    }

    private static String greetingPrefix(String language) {
        return "fr".equalsIgnoreCase(language) ? "Bonjour ! " : "Hi there! ";
    }

    private static String withGreetingPrefix(String answer, boolean mixedGreeting, String language) {
        if (!mixedGreeting || answer == null || answer.isBlank()) {
            return answer;
        }
        return greetingPrefix(language) + answer;
    }

    private static boolean looksDestructive(String question) {
        return question != null && DESTRUCTIVE_INTENT.matcher(question).find();
    }

    private static boolean looksObviouslyOutOfScope(String question) {
        return question != null && OBVIOUS_OUT_OF_SCOPE.matcher(question).find();
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "French";
        }
        return switch (language.trim().toLowerCase()) {
            case "fr", "french", "français", "francais" -> "French";
            case "en", "english" -> "English";
            default -> language.trim();
        };
    }

    private static String refusalAnswer(String language) {
        if ("English".equalsIgnoreCase(language)) {
            return "I cannot modify or delete database data. I can only answer read-only business questions.";
        }
        return "Je ne peux pas modifier ou supprimer les donnees. Je peux uniquement repondre a des questions business en lecture seule.";
    }

    /**
     * Shared graceful fallback for every path where SQL couldn't be generated, was rejected,
     * or failed to execute (GENERATION_ERROR / SEMANTIC_SQL_REJECTED / EXECUTION_ERROR). Never
     * surfaces the technical reason to the user — narrateUnavailable() explains the situation
     * in business language and still offers whatever qualitative value it can from general
     * domain knowledge. Falls back to a short static message only if the narrator itself is
     * unavailable (e.g. the LLM is down), so the user is never left with a raw exception.
     */
    private String gracefulUnavailableAnswer(String question, String language, QuestionIntent intent, String reason, UUID traceId) {
        try {
            String answer = answerNarrationService.narrateUnavailable(question, language, intent, reason).answer();
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
        } catch (RuntimeException narrationException) {
            LOGGER.warn(
                    "QA graceful error narration also failed traceId={} reason={} exception={}",
                    traceId,
                    reason,
                    narrationException.toString());
        }
        return generationErrorAnswer(language);
    }

    private static String generationErrorAnswer(String language) {
        if ("English".equalsIgnoreCase(language)) {
            return "I could not generate a reliable SQL query for this question. Please try a more specific business question.";
        }
        return "Je n'ai pas pu generer une requete SQL fiable pour cette question. Essaie une question business plus precise.";
    }

    private static String outOfScopeAnswer(String language) {
        if ("English".equalsIgnoreCase(language)) {
            return "I am LPN's BI assistant, so I can only answer questions about the imported company data.";
        }
        return "Je suis l'assistant BI de LPN, donc je peux uniquement repondre aux questions sur les donnees importees de l'entreprise.";
    }

    private static String executionStatus(SqlExecutionException exception) {
        return exception.statusCode() == 422 ? "SQL_REJECTED" : "EXECUTION_ERROR";
    }

    private static String detailedExecutionError(SqlExecutionException exception) {
        if (!exception.errors().isEmpty()) {
            return exception.errors().getFirst();
        }
        return exception.getMessage();
    }

    private static String fallbackAnswer(String language, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            if ("English".equalsIgnoreCase(language)) {
                return "No matching data was found.";
            }
            return "Aucune donnee correspondante n'a ete trouvee.";
        }
        if ("English".equalsIgnoreCase(language)) {
            return "The query returned " + rows.size() + " row(s). See the data preview for the exact values.";
        }
        return "La requete a retourne " + rows.size() + " ligne(s). Consulte l'apercu des donnees pour les valeurs exactes.";
    }

    private static long elapsedMs(Instant startedAt, Instant completedAt) {
        return Math.max(1, Duration.between(startedAt, completedAt).toMillis());
    }

    record QaRequest(
            String question,
            String language,
            String mode,
            @JsonProperty("reasoning_mode") Boolean reasoningMode,
            @JsonProperty("client_request_id") String clientRequestId,
            @JsonProperty("frontend_started_at") Instant frontendStartedAt,
            @JsonProperty("frontend_metadata") Map<String, Object> frontendMetadata,
            @JsonProperty("previous_question") String previousQuestion) {
    }

    record QaResponse(
            @JsonProperty("trace_id") UUID traceId,
            String answer,
            String sql,
            List<Map<String, Object>> rows,
            @JsonProperty("row_count") int rowCount,
            @JsonProperty("retrieved_tables") List<RetrievedTable> retrievedTables,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("latency_breakdown_ms") QaLatencyBreakdown latencyBreakdownMs,
            @JsonProperty("request_mode") String requestMode,
            @JsonProperty("reasoning_mode") boolean reasoningMode,
            @JsonProperty("model_used") String modelUsed,
            @JsonProperty("fallback_used") boolean fallbackUsed,
            @JsonProperty("execution_status") String executionStatus,
            SqlValidationResult validation,
            QuestionIntent intent,
            @JsonProperty("semantic_validation") SemanticSqlValidation semanticValidation,
            @JsonProperty("result_assessment") SemanticSqlValidator.ResultAssessment resultAssessment,
            @JsonProperty("repair_attempted") boolean repairAttempted,
            @JsonProperty("repaired_sql") String repairedSql,
            @JsonProperty("sql_repair_latency_ms") Long sqlRepairLatencyMs,
            String error) {
    }

    record QaLatencyBreakdown(
            @JsonProperty("schema_retrieval") Long schemaRetrieval,
            @JsonProperty("sql_model") Long sqlModel,
            @JsonProperty("sql_normalization") Long sqlNormalization,
            @JsonProperty("sql_execution") Long sqlExecution,
            Long narration,
            Long total) {
    }
}

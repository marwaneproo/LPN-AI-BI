import type {
  ChatMessageDto,
  Message,
  QaResponse,
  RetrievedTableDto,
  Source,
} from "../types/qa.types";

export function buildMockAnswer(question: string): Message {
  const lower = question.toLowerCase();
  if (lower.includes("stock") || lower.includes("rupture")) {
    return {
      id: "",
      role: "assistant",
      text: "Le risque principal se concentre sur les cahiers 96p et les stylos bleus. Cette réponse est mockée pour valider l'expérience frontend.",
      sql: "SELECT p.NAME, SUM(ol.QTYORDERED) AS qty_ordered\nFROM C_ORDERLINE ol\nJOIN M_PRODUCT p ON p.M_PRODUCT_ID = ol.M_PRODUCT_ID\nGROUP BY p.NAME\nORDER BY qty_ordered DESC\nLIMIT 10;",
      sources: [
        { table: "C_ORDERLINE", module: "Sales", score: 0.9, description: "Lignes de commandes et quantités." },
        { table: "M_PRODUCT", module: "Inventory", score: 0.86, description: "Catalogue produits." },
      ],
      rows: [
        { produit: "Cahier 96p", risque: "78%" },
        { produit: "Stylo bleu", risque: "64%" },
      ],
      latency: 1280,
    };
  }

  return {
    id: "",
    role: "assistant",
    text: "Le mois dernier, le volume de commandes est estimé à 18 430 dans cette démonstration mockée. Le backend réel sera branché plus tard.",
    sql: "SELECT COUNT(C_ORDER_ID) AS total_orders_last_month\nFROM C_ORDER\nWHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'\n  AND DATEORDERED < date_trunc('month', CURRENT_DATE);",
    sources: [
      { table: "C_ORDER", module: "Sales", score: 0.93, description: "En-têtes de commandes clients." },
      { table: "C_BPARTNER", module: "Reference", score: 0.51, description: "Partenaires clients et fournisseurs." },
    ],
    rows: [{ total_orders_last_month: 18430 }],
    latency: 1310,
  };
}

export function findPreviousUserPrompt(messages: Message[], index: number) {
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    if (messages[cursor]?.role === "user") {
      return messages[cursor].text;
    }
  }
  return undefined;
}

export function formatDebugCopy(message: Message, previousUserPrompt?: string) {
  const sources = message.sources?.length
    ? message.sources
        .map(
          (source) =>
            `- ${source.table} | ${source.module} | ${Math.round(source.score * 100)}% | ${source.description}`,
        )
        .join("\n")
    : "Aucune source.";
  const rows = message.rows?.length ? JSON.stringify(message.rows, null, 2) : "Aucune donnée.";
  const intent = message.intent?.intents?.length ? message.intent.intents.join(", ") : "Non détecté";
  const semanticIssues = [
    ...(message.semanticValidation?.issues ?? []),
    ...(message.semanticValidation?.warnings ?? []),
    ...(message.resultAssessment?.issues ?? []),
    ...(message.resultAssessment?.warnings ?? []),
  ];

  return [
    "LPN AI-BI DEBUG COPY",
    "",
    "PROMPT",
    previousUserPrompt ?? "(prompt introuvable)",
    "",
    "REPONSE IA",
    message.text || "(réponse vide)",
    "",
    "MODELE / MODE",
    `${message.reasoningMode ? "Thinking" : "Fast"}${message.modelUsed ? ` · ${message.modelUsed}` : ""}${
      message.fallbackUsed ? " · fallback" : ""
    }`,
    "",
    "LATENCE",
    message.latency ? `${message.latency} ms` : "Non disponible",
    "",
    "INTENT",
    intent,
    "",
    "VALIDATION",
    semanticIssues.length ? semanticIssues.map((issue) => `- ${issue}`).join("\n") : "Aucune alerte.",
    "",
    "SQL",
    message.sql ?? "Aucun SQL.",
    "",
    "TABLES RAG",
    sources,
    "",
    "DONNEES",
    rows,
  ].join("\n");
}

export function messageToPayload(message: Message) {
  const { id, role, text, ...payload } = message;
  return payload;
}

export function mapStoredChatMessage(message: ChatMessageDto): Message {
  const payload = message.payload ?? {};
  return {
    ...payload,
    id: message.id,
    role: message.role,
    text: message.content,
    status: payload.status ?? "done",
  };
}

export function buildLiveAnswer(response: QaResponse): Message {
  return {
    id: "",
    role: "assistant",
    text: response.answer,
    status: "done",
    sql: response.sql ?? undefined,
    sources: (response.retrieved_tables ?? []).map(mapRetrievedTable),
    rows: response.rows ?? [],
    latency: response.latency_ms,
    isLive: true,
    requestMode: response.request_mode === "reasoning" ? "reasoning" : "standard",
    reasoningMode: Boolean(response.reasoning_mode),
    modelUsed: response.model_used,
    fallbackUsed: response.fallback_used,
    semanticValidation: response.semantic_validation ?? undefined,
    resultAssessment: response.result_assessment ?? undefined,
    repairAttempted: Boolean(response.repair_attempted),
    repairedSql: response.repaired_sql,
    intent: response.intent,
    executionStatus: response.execution_status,
    latencyBreakdown: response.latency_breakdown_ms,
    sqlRepairLatencyMs: response.sql_repair_latency_ms,
    traceId: response.trace_id,
    error: response.error,
    createdAt: new Date().toISOString(),
  };
}

export function buildFrontendMetadata(question: string, reasoningMode: boolean) {
  const normalized = question.toLowerCase();
  const intents = [];
  if (/\b(how many|combien|count|nombre)\b/.test(normalized)) intents.push("COUNT");
  if (/\b(total|value|amount|montant|valeur|chiffre)\b/.test(normalized)) intents.push("SUM_TOTAL");
  if (/\b(compare|vs|versus|diff|comparer)\b/.test(normalized)) intents.push("COMPARISON");
  if (/\b(top|highest|plus haut|premiers?|5)\b/.test(normalized)) intents.push("TOP_N");
  if (/\b(product|produit|stock|rupture)\b/.test(normalized)) intents.push("PRODUCT_OR_STOCK");
  return {
    selected_mode: reasoningMode ? "reasoning" : "standard",
    client_timestamp: new Date().toISOString(),
    detected_language: /[éèàùç]/i.test(question) ? "fr" : "en",
    intent_hints: intents,
  };
}

export function mapRetrievedTable(table: RetrievedTableDto): Source {
  return {
    table: table.table_name,
    module: table.module ?? "Schema",
    score: normalizeScore(table.score),
    description:
      table.description_fr ??
      table.description_en ??
      table.key_columns ??
      "Table retrouvée par le Schema RAG.",
  };
}

export function normalizeScore(score?: number | null) {
  if (typeof score !== "number" || Number.isNaN(score)) return 0;
  const normalized = score > 1 ? score / 100 : score;
  return Math.min(1, Math.max(0, normalized));
}

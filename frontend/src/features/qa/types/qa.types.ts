import type { DataRow } from "../../../types/common.types";

export type QuestionIntent = {
  intents?: string[];
  domains?: string[];
  mentioned_dates?: string[];
  explicit_last_month?: boolean;
  explicit_completed_only?: boolean;
  explicit_paid_unpaid?: boolean;
  explicit_all_data?: boolean;
  comparison_question?: boolean;
  ranking_question?: boolean;
  aggregate_question?: boolean;
};

export type SemanticValidation = {
  valid?: boolean;
  repair_required?: boolean;
  issues?: string[];
  warnings?: string[];
};

export type ResultAssessment = {
  incomplete_result?: boolean;
  issues?: string[];
  warnings?: string[];
};

export type Source = {
  table: string;
  module: string;
  score: number;
  description: string;
};

export type RetrievedTableDto = {
  table_name: string;
  module?: string | null;
  description_en?: string | null;
  description_fr?: string | null;
  key_columns?: string | null;
  relations?: string | null;
  score?: number | null;
  expanded_from?: string | null;
};

export type Message = {
  id: string;
  role: "user" | "assistant";
  text: string;
  status?: "idle" | "thinking" | "done" | "error";
  step?: string;
  sql?: string;
  rows?: DataRow[];
  sources?: Source[];
  latency?: number;
  isLive?: boolean;
  requestMode?: "standard" | "reasoning";
  reasoningMode?: boolean;
  modelUsed?: string;
  fallbackUsed?: boolean;
  semanticValidation?: SemanticValidation;
  resultAssessment?: ResultAssessment;
  repairAttempted?: boolean;
  repairedSql?: string | null;
  intent?: QuestionIntent;
  executionStatus?: string;
  latencyBreakdown?: {
    schema_retrieval?: number | null;
    sql_model?: number | null;
    sql_normalization?: number | null;
    sql_execution?: number | null;
    narration?: number | null;
    total?: number | null;
  };
  sqlRepairLatencyMs?: number | null;
  traceId?: string;
  error?: string | null;
  createdAt?: string;
};

export type ChatSessionSummary = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
};

export type ChatMessageDto = {
  id: string;
  role: "user" | "assistant";
  content: string;
  payload?: Partial<Message> | null;
  createdAt: string;
};

export type ChatSessionDetail = {
  session: ChatSessionSummary;
  messages: ChatMessageDto[];
};

export type QaResponse = {
  trace_id?: string;
  answer: string;
  sql?: string | null;
  rows?: DataRow[];
  row_count?: number;
  retrieved_tables?: RetrievedTableDto[];
  latency_ms: number;
  latency_breakdown_ms?: {
    schema_retrieval?: number | null;
    sql_model?: number | null;
    sql_normalization?: number | null;
    sql_execution?: number | null;
    narration?: number | null;
    total?: number | null;
  };
  model_used?: string;
  fallback_used?: boolean;
  request_mode?: "standard" | "reasoning" | string;
  reasoning_mode?: boolean;
  execution_status?: string;
  intent?: QuestionIntent;
  semantic_validation?: SemanticValidation | null;
  result_assessment?: ResultAssessment | null;
  repair_attempted?: boolean;
  repaired_sql?: string | null;
  sql_repair_latency_ms?: number | null;
  error?: string | null;
};

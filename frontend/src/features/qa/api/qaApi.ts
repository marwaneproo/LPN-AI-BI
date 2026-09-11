import type { AuthSession } from "../../auth/types/auth.types";
import type {
  ChatMessageDto,
  ChatSessionDetail,
  ChatSessionSummary,
  Message,
  QaResponse,
} from "../types/qa.types";
import { API_BASE_URL, API_TIMEOUT_SECONDS, apiFetch } from "../../../api/client";
import { buildFrontendMetadata, messageToPayload } from "../utils/qaUtils";

export async function fetchChatSessions(session: AuthSession): Promise<ChatSessionSummary[]> {
  void session;
  const response = await apiFetch(`${API_BASE_URL}/v1/chat-sessions`);
  if (!response.ok) {
    throw new Error("Historique indisponible.");
  }
  return (await response.json()) as ChatSessionSummary[];
}

export async function createChatSession(session: AuthSession, title: string): Promise<ChatSessionSummary> {
  void session;
  const response = await apiFetch(`${API_BASE_URL}/v1/chat-sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title }),
  });
  if (!response.ok) {
    throw new Error("La conversation n'a pas pu être créée.");
  }
  return (await response.json()) as ChatSessionSummary;
}

export async function fetchChatSession(session: AuthSession, sessionId: string): Promise<ChatSessionDetail> {
  void session;
  const response = await apiFetch(`${API_BASE_URL}/v1/chat-sessions/${sessionId}`);
  if (!response.ok) {
    throw new Error("La conversation n'a pas pu être chargée.");
  }
  return (await response.json()) as ChatSessionDetail;
}

export async function saveChatMessage(session: AuthSession, sessionId: string, message: Message): Promise<ChatMessageDto> {
  void session;
  const response = await apiFetch(`${API_BASE_URL}/v1/chat-sessions/${sessionId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      role: message.role,
      content: message.text,
      payload: messageToPayload(message),
    }),
  });
  if (!response.ok) {
    throw new Error("Le message n'a pas pu être sauvegardé.");
  }
  return (await response.json()) as ChatMessageDto;
}

export async function askQa(
  question: string,
  clientRequestId: string,
  frontendStartedAt: Date,
  reasoningMode: boolean,
  previousQuestion?: string,
): Promise<QaResponse> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), API_TIMEOUT_SECONDS * 1000);

  try {
    const response = await apiFetch(`${API_BASE_URL}/v1/qa`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        question,
        language: "fr",
        mode: reasoningMode ? "reasoning" : "standard",
        reasoning_mode: reasoningMode,
        client_request_id: clientRequestId,
        frontend_started_at: frontendStartedAt.toISOString(),
        frontend_metadata: buildFrontendMetadata(question, reasoningMode),
        previous_question: previousQuestion ?? null,
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "");
      throw new Error(errorText ? `Backend ${response.status}: ${errorText}` : `Backend ${response.status}`);
    }

    return (await response.json()) as QaResponse;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Le backend n'a pas répondu après ${API_TIMEOUT_SECONDS} secondes.`);
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export async function reportClientTiming(traceId: string | undefined, clientTotalLatencyMs: number) {
  if (!traceId) return;

  try {
    await apiFetch(`${API_BASE_URL}/v1/performance-traces/${traceId}/client-timing`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        frontend_completed_at: new Date().toISOString(),
        client_total_latency_ms: clientTotalLatencyMs,
      }),
    });
  } catch {
    // Telemetry must never disturb the business conversation flow.
  }
}

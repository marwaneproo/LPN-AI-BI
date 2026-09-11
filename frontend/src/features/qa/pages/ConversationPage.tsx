import { FormEvent, useEffect, useState } from "react";
import { Database, Send, ShieldCheck } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { useStoredState } from "../../../hooks/useStoredState";
import { wait } from "../../../utils/misc";
import { USE_MOCKS } from "../../../api/client";
import type { AuthSession } from "../../auth/types/auth.types";
import type { Message } from "../types/qa.types";
import { defaultMessages, exampleQuestions } from "../constants/qa.constants";
import {
  buildMockAnswer,
  buildLiveAnswer,
  mapStoredChatMessage,
  findPreviousUserPrompt,
} from "../utils/qaUtils";
import {
  fetchChatSession,
  createChatSession,
  saveChatMessage,
  askQa,
  reportClientTiming,
} from "../api/qaApi";
import { MessageRow } from "../components/MessageRow";

export function ConversationPage({ session }: { session: AuthSession }) {
  const [messages, setMessages] = useState<Message[]>(defaultMessages);
  const [question, setQuestion] = useState("");
  const [isThinking, setIsThinking] = useState(false);
  const [reasoningMode, setReasoningMode] = useStoredState("lpn-reasoning-mode", false);
  const [searchParams, setSearchParams] = useSearchParams();
  const activeChatId = searchParams.get("id");

  useEffect(() => {
    if (activeChatId) {
      void openChat(activeChatId);
    } else {
      setMessages(defaultMessages);
    }
  }, [activeChatId, session.username]);

  async function openChat(sessionId: string) {
    if (isThinking) return;
    try {
      const detail = await fetchChatSession(session, sessionId);
      setMessages(detail.messages.length ? detail.messages.map(mapStoredChatMessage) : defaultMessages);
    } catch {
      setMessages(defaultMessages);
    }
  }

  async function submitQuestion(value: string) {
    const clean = value.trim();
    if (!clean || isThinking) return;

    let targetChatId = activeChatId;
    if (!targetChatId && !USE_MOCKS) {
      const created = await createChatSession(session, clean);
      targetChatId = created.id;
      setSearchParams({ id: created.id }, { replace: true });
    }

    const userMessage: Message = { id: crypto.randomUUID(), role: "user", text: clean, createdAt: new Date().toISOString() };
    const assistantId = crypto.randomUUID();
    const assistant: Message = {
      id: assistantId,
      role: "assistant",
      text: "",
      status: "thinking",
      requestMode: reasoningMode ? "reasoning" : "standard",
      reasoningMode,
      step: USE_MOCKS
        ? "Récupération du schéma mock"
        : reasoningMode
          ? "Mode Thinking -> Schema RAG -> SQL -> exécution"
          : "Mode Fast -> Schema RAG -> SQL -> exécution",
    };

    setMessages((current) => [...current, userMessage, assistant]);
    setQuestion("");
    setIsThinking(true);
    if (targetChatId && !USE_MOCKS) {
      void saveChatMessage(session, targetChatId, userMessage);
    }

    if (USE_MOCKS) {
      const steps = ["Récupération du schéma mock", "Génération SQL mock", "Préparation de l'aperçu mock"];
      for (const step of steps) {
        setMessages((current) =>
          current.map((message) => (message.id === assistantId ? { ...message, step } : message)),
        );
        await wait(520);
      }

      const generated = buildMockAnswer(clean);
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId
            ? { ...generated, id: assistantId, role: "assistant", status: "done", createdAt: new Date().toISOString() }
            : message,
        ),
      );
      setIsThinking(false);
      return;
    }

    try {
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId
            ? {
                ...message,
                step: reasoningMode
                  ? "Appel /v1/qa en mode Thinking"
                  : "Appel /v1/qa en mode Fast",
              }
            : message,
        ),
      );
      const frontendStartedAt = new Date();
      const clientStartedAt = performance.now();
      const clientRequestId = crypto.randomUUID();
      // `messages` here is still the pre-turn snapshot (React state closures aren't mutated by the
      // setMessages call above within this same invocation), so this is genuinely the previous turn's
      // question — not the one just typed — which is exactly what the backend's lightweight
      // conversational memory needs to resolve elliptical follow-ups like "et pour 2025".
      const previousUserQuestion = [...messages].reverse().find((message) => message.role === "user")?.text;
      const response = await askQa(clean, clientRequestId, frontendStartedAt, reasoningMode, previousUserQuestion);
      const generated = buildLiveAnswer(response);
      const finalAssistant = { ...generated, id: assistantId, role: "assistant" as const, status: "done" as const };
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId ? finalAssistant : message,
        ),
      );
      if (targetChatId) {
        void saveChatMessage(session, targetChatId, finalAssistant);
      }
      const clientTotalLatencyMs = Math.max(1, Math.round(performance.now() - clientStartedAt));
      void reportClientTiming(response.trace_id, clientTotalLatencyMs);
    } catch (error) {
      const details = error instanceof Error ? error.message : "Erreur inconnue.";
      const errorMessage: Message = {
        id: assistantId,
        role: "assistant",
        text:
          "Je n'ai pas pu obtenir le SQL depuis le backend. Vérifie que Docker est lancé, que llm-orchestrator répond sur le port 8081, et que le modèle Ollama est disponible.\n\nDétail: " +
          details,
        status: "error",
      };
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId ? errorMessage : message,
        ),
      );
      if (targetChatId) {
        void saveChatMessage(session, targetChatId, errorMessage);
      }
    } finally {
      setIsThinking(false);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void submitQuestion(question);
  }

  return (
    <div className="conversation-main">
      <div className="message-list">
          <div className="conversation-hero">
            <span>Assistant BI LPN</span>
            <h1>Analyse tes données en langage naturel</h1>
            <p>Schema RAG, SQL sécurisé, données et traçabilité restent visibles à chaque réponse.</p>
          </div>
          {messages.map((message, index) => (
            <MessageRow
              key={message.id}
              message={message}
              previousUserPrompt={findPreviousUserPrompt(messages, index)}
            />
          ))}
        </div>

        <form className="composer" onSubmit={handleSubmit}>
          <div className="example-grid">
            {exampleQuestions.map((item) => (
              <button key={item} type="button" onClick={() => void submitQuestion(item)} disabled={isThinking}>
                {item}
              </button>
            ))}
          </div>
          <div className="composer-box">
            <textarea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Pose une question sur les ventes, factures, clients ou stocks..."
              rows={2}
            />
            <div className="composer-footer">
              <div className="composer-left">
                <button
                  type="button"
                  className={reasoningMode ? "reasoning-toggle active" : "reasoning-toggle"}
                  aria-pressed={reasoningMode}
                  onClick={() => setReasoningMode(!reasoningMode)}
                  disabled={isThinking}
                  title="Basculer entre réponse rapide et raisonnement approfondi"
                >
                  <span className="toggle-track" aria-hidden="true">
                    <span className="toggle-thumb" />
                  </span>
                  <span className="toggle-label">{reasoningMode ? "Thinking" : "Fast"}</span>
                </button>
                <div className="composer-pills" aria-label="Pipeline actif">
                  <span>
                    <Database size={13} />
                    Schema RAG
                  </span>
                  <span>
                    <ShieldCheck size={13} />
                    SQL sécurisé
                  </span>
                </div>
              </div>
              <button type="submit" className="send-button" disabled={!question.trim() || isThinking}>
                <Send size={16} />
              </button>
            </div>
          </div>
        </form>
    </div>
  );
}

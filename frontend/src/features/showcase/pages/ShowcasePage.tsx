import { FormEvent, useState } from "react";
import { Clock3, LogOut, Send, Sparkles, UserCircle } from "lucide-react";
import lpnAiLogo from "../../../../logo4.png";
import { USE_MOCKS } from "../../../api/client";
import { wait } from "../../../utils/misc";
import type { Message } from "../../qa/types/qa.types";
import { buildMockAnswer, buildLiveAnswer } from "../../qa/utils/qaUtils";
import { askQa, reportClientTiming } from "../../qa/api/qaApi";

function ShowcaseMessage({ message }: { message: Message }) {
  return (
    <article className={`showcase-message ${message.role === "user" ? "user" : "assistant"}`}>
      <div className="showcase-message-meta">
        {message.role === "assistant" ? <Sparkles size={15} /> : <UserCircle size={15} />}
        <strong>{message.role === "assistant" ? "Assistant BI" : "Vous"}</strong>
        {message.latency ? <span>{Math.round(message.latency / 1000)}s</span> : null}
      </div>
      {message.status === "thinking" ? (
        <div className="showcase-loading">
          <span />
          <span />
          <span />
          <p>{message.step}</p>
        </div>
      ) : message.status === "error" ? (
        <p className="showcase-error">{message.text}</p>
      ) : (
        <p>{message.text}</p>
      )}
    </article>
  );
}

export function ShowcaseExperience({ onSignOut }: { onSignOut: () => void }) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "showcase-welcome",
      role: "assistant",
      text: "Bonjour. Posez une question sur les ventes, les factures, les clients ou les produits.",
      status: "done",
    },
  ]);
  const [question, setQuestion] = useState("");
  const [isThinking, setIsThinking] = useState(false);

  async function submitQuestion(value: string) {
    const clean = value.trim();
    if (!clean || isThinking) return;

    const userMessage: Message = { id: crypto.randomUUID(), role: "user", text: clean };
    const assistantId = crypto.randomUUID();
    setMessages((current) => [
      ...current,
      userMessage,
      {
        id: assistantId,
        role: "assistant",
        text: "",
        status: "thinking",
        step: "Analyse de la demande et préparation de la réponse",
      },
    ]);
    setQuestion("");
    setIsThinking(true);

    try {
      const frontendStartedAt = new Date();
      const clientStartedAt = performance.now();
      const clientRequestId = crypto.randomUUID();
      let traceId: string | undefined;
      const generated = USE_MOCKS
        ? await wait(900).then(() => buildMockAnswer(clean))
        : await askQa(clean, clientRequestId, frontendStartedAt, false).then((response) => {
            traceId = response.trace_id;
            return buildLiveAnswer(response);
          });
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId
            ? {
                ...generated,
                id: assistantId,
                role: "assistant",
                status: "done",
                sql: undefined,
                sources: undefined,
                rows: undefined,
                semanticValidation: undefined,
                resultAssessment: undefined,
                intent: undefined,
              }
            : message,
        ),
      );
      if (!USE_MOCKS) {
        const clientTotalLatencyMs = Math.max(1, Math.round(performance.now() - clientStartedAt));
        void reportClientTiming(traceId, clientTotalLatencyMs);
      }
    } catch (error) {
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId
            ? {
                id: assistantId,
                role: "assistant",
                text: "Je n'ai pas pu finaliser la réponse pour le moment. Merci de réessayer dans quelques instants.",
                status: "error",
              }
            : message,
        ),
      );
    } finally {
      setIsThinking(false);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void submitQuestion(question);
  }

  return (
    <main className="showcase-shell">
      <header className="showcase-topbar">
        <div className="showcase-brand">
          <img src={lpnAiLogo} alt="LPN AI" />
          <span>Assistant BI</span>
        </div>
        <button className="showcase-logout" onClick={onSignOut}>
          <LogOut size={15} />
          Sortir
        </button>
      </header>

      <section className="showcase-stage">
        <div className="showcase-heading">
          <span>Processus vente</span>
          <h1>Interrogez les données LPN en langage naturel</h1>
          <p>Une interface claire pour transformer une question métier en réponse exploitable.</p>
        </div>

        <div className="showcase-chat">
          {messages.map((message) => (
            <ShowcaseMessage key={message.id} message={message} />
          ))}
        </div>

        <form className="showcase-composer" onSubmit={handleSubmit}>
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Exemple: How many orders were placed last month?"
            rows={2}
          />
          <button type="submit" disabled={!question.trim() || isThinking}>
            {isThinking ? <Clock3 size={16} /> : <Send size={16} />}
          </button>
        </form>
      </section>
    </main>
  );
}

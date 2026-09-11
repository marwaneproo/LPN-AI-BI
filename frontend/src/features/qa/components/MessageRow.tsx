import { useState } from "react";
import { AlertTriangle, Code2, Copy, Sparkles, UserCircle } from "lucide-react";
import type { Message } from "../types/qa.types";
import { formatDebugCopy } from "../utils/qaUtils";
import { InsightPanels } from "./InsightPanels";
import { MessageExportMenu } from "./MessageExportMenu";

export function MessageRow({ message, previousUserPrompt }: { message: Message; previousUserPrompt?: string }) {
  const [copied, setCopied] = useState(false);
  const [devModeOpen, setDevModeOpen] = useState(false);
  const canCopyDebug = message.role === "assistant" && message.status === "done";
  const isFinishedAssistantMessage = message.role === "assistant" && message.status === "done";

  async function copyMessage() {
    const payload = canCopyDebug ? formatDebugCopy(message, previousUserPrompt) : message.text;
    try {
      await navigator.clipboard.writeText(payload);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      setCopied(false);
    }
  }

  return (
    <article className={`message-row ${message.role}-message`}>
      <div className={message.role === "assistant" ? "avatar assistant-avatar" : "avatar user-avatar"}>
        {message.role === "assistant" ? <Sparkles size={15} /> : <UserCircle size={15} />}
      </div>
      <div className="message-content">
        <div className="message-meta">
          <strong>{message.role === "assistant" ? "Assistant BI" : "Vous"}</strong>
          {message.latency ? <span>{message.latency} ms</span> : null}
          {isFinishedAssistantMessage ? <MessageExportMenu message={message} question={previousUserPrompt} /> : null}
          {isFinishedAssistantMessage ? (
            <button
              type="button"
              className={devModeOpen ? "ghost-icon dev-mode-active" : "ghost-icon"}
              aria-label={devModeOpen ? "Masquer le mode développeur" : "Afficher le mode développeur"}
              aria-expanded={devModeOpen}
              title="Mode développeur (informations techniques)"
              onClick={() => setDevModeOpen((v) => !v)}
            >
              <Code2 size={13} />
            </button>
          ) : null}
          <button
            className={copied ? "ghost-icon copied" : "ghost-icon"}
            aria-label={canCopyDebug ? "Copier le diagnostic complet" : "Copier"}
            title={canCopyDebug ? "Copier prompt, réponse, SQL, tables et données" : "Copier"}
            onClick={() => void copyMessage()}
          >
            <Copy size={13} />
          </button>
          {copied ? <span className="copy-confirm">Copié</span> : null}
        </div>
        {message.status === "thinking" ? (
          <div className="thinking-line">
            <span />
            {message.step}
          </div>
        ) : message.status === "error" ? (
          <div className="error-panel">
            <AlertTriangle size={15} />
            <p>{message.text}</p>
          </div>
        ) : (
          <p>{message.text}</p>
        )}
        {isFinishedAssistantMessage && message.sql && devModeOpen ? <InsightPanels message={message} /> : null}
      </div>
    </article>
  );
}

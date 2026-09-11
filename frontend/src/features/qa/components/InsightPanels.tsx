import { useState } from "react";
import { DataPreview } from "../../../components/ui/DataTable";
import type { Message } from "../types/qa.types";
import { QualityNotes } from "./QualityNotes";

export function InsightPanels({ message }: { message: Message }) {
  const [openPanel, setOpenPanel] = useState<"sources" | "sql" | "data" | "intent">("intent");
  const hasRows = Boolean(message.rows?.length);
  const intent = message.intent;
  const latency = message.latencyBreakdown;

  return (
    <div className="insight-zone">
      <div className="chip-row">
        <button className={openPanel === "intent" ? "chip active-chip" : "chip"} onClick={() => setOpenPanel("intent")}>
          Intent &amp; statut
        </button>
        <button className={openPanel === "sources" ? "chip active-chip" : "chip"} onClick={() => setOpenPanel("sources")}>
          Sources ({message.sources?.length ?? 0})
        </button>
        <button className={openPanel === "sql" ? "chip active-chip" : "chip"} onClick={() => setOpenPanel("sql")}>
          SQL
        </button>
        <button className={openPanel === "data" ? "chip active-chip" : "chip"} onClick={() => setOpenPanel("data")}>
          {hasRows ? "Données" : "Données"}
        </button>
        <span className="latency-label">
          {message.isLive ? "Backend connecté, SQL exécuté" : "Aperçu mock"}
        </span>
        {message.isLive ? (
          <span className={message.reasoningMode ? "mode-badge reasoning" : "mode-badge"}>
            {message.reasoningMode ? "Thinking" : "Fast"}
            {message.modelUsed ? ` · ${message.modelUsed}` : ""}
            {message.fallbackUsed ? " · fallback" : ""}
          </span>
        ) : null}
        {message.repairAttempted ? <span className="quality-badge repaired">SQL réparé</span> : null}
        {message.semanticValidation?.valid ? <span className="quality-badge valid">SQL cadré</span> : null}
        {message.resultAssessment?.incomplete_result ? (
          <span className="quality-badge warning">Résultat incomplet</span>
        ) : null}
      </div>
      {message.isLive ? <QualityNotes message={message} /> : null}
      {openPanel === "intent" ? (
        <div className="panel-grid intent-panel">
          <div className="source-card">
            <div>
              <strong>Statut d'exécution</strong>
            </div>
            <p>{message.executionStatus ?? (message.status === "done" ? "OK" : "—")}</p>
            {message.traceId ? <small>Trace ID : {message.traceId}</small> : null}
          </div>
          <div className="source-card">
            <div>
              <strong>Intent(s) détecté(s)</strong>
            </div>
            <p>{intent?.intents?.length ? intent.intents.join(", ") : "—"}</p>
          </div>
          <div className="source-card">
            <div>
              <strong>Domaine(s) métier</strong>
            </div>
            <p>{intent?.domains?.length ? intent.domains.join(", ") : "—"}</p>
          </div>
          <div className="source-card">
            <div>
              <strong>Temps d'exécution</strong>
            </div>
            <p>
              {latency?.total ? `${latency.total} ms total` : message.latency ? `${message.latency} ms` : "—"}
            </p>
            {latency ? (
              <small>
                {[
                  latency.schema_retrieval ? `RAG ${latency.schema_retrieval}ms` : null,
                  latency.sql_model ? `SQL ${latency.sql_model}ms` : null,
                  latency.sql_execution ? `Exec ${latency.sql_execution}ms` : null,
                  latency.narration ? `Narration ${latency.narration}ms` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </small>
            ) : null}
          </div>
          {message.semanticValidation ? (
            <div className="source-card">
              <div>
                <strong>Validation SQL</strong>
              </div>
              <p>{message.semanticValidation.valid ? "Valide" : "Non validée"}</p>
              {message.semanticValidation.issues?.length ? (
                <small>{message.semanticValidation.issues.join(" · ")}</small>
              ) : null}
            </div>
          ) : null}
          {message.error ? (
            <div className="source-card">
              <div>
                <strong>Erreur (diagnostic)</strong>
              </div>
              <p>{message.error}</p>
            </div>
          ) : null}
        </div>
      ) : null}
      {openPanel === "sources" ? (
        <div className="panel-grid">
          {message.sources?.map((source) => (
            <div key={source.table} className="source-card">
              <div>
                <strong>{source.table}</strong>
                <span>{source.module}</span>
              </div>
              <p>{source.description}</p>
              <small>Score {Math.round(source.score * 100)}%</small>
            </div>
          ))}
        </div>
      ) : null}
      {openPanel === "sql" ? <pre className="sql-panel">{message.sql}</pre> : null}
      {openPanel === "data" ? <DataPreview rows={message.rows ?? []} /> : null}
    </div>
  );
}

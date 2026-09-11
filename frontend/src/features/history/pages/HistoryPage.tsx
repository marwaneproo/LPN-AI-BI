import { useEffect, useState } from "react";
import { History, Search } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Card } from "../../../components/ui/Card";
import { PageFrame } from "../../../components/layout/PageFrame";
import { formatShortDate } from "../../../utils/formatters";
import type { AuthSession } from "../../auth/types/auth.types";
import type { ChatSessionSummary } from "../../qa/types/qa.types";
import { fetchChatSessions } from "../../qa/api/qaApi";

export function HistoryPage({ session }: { session: AuthSession }) {
  const [chatSessions, setChatSessions] = useState<ChatSessionSummary[]>([]);
  const [chatSearch, setChatSearch] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const isRecentFilter = searchParams.get("filter") === "recent";

  useEffect(() => {
    void fetchHistory();
  }, [session.username]);

  async function fetchHistory() {
    setIsLoading(true);
    try {
      const sessions = await fetchChatSessions(session);
      setChatSessions(sessions);
    } catch {
      setChatSessions([]);
    } finally {
      setIsLoading(false);
    }
  }

  const now = new Date();
  let displayedSessions = chatSessions.filter((item) =>
    item.title.toLowerCase().includes(chatSearch.trim().toLowerCase())
  );

  if (isRecentFilter) {
    displayedSessions = displayedSessions.filter((item) => {
      const d = new Date(item.updatedAt);
      return now.getTime() - d.getTime() < 7 * 24 * 60 * 60 * 1000; // Last 7 days
    });
  }

  return (
    <PageFrame
      title={isRecentFilter ? "Conversations récentes" : "Toutes les sessions"}
      subtitle="Historique de vos échanges avec l'assistant BI."
      headerAside={
        <button className="primary-soft" onClick={() => navigate("/conversation")}>
          Nouvelle conversation
        </button>
      }
    >
      <Card>
        <div style={{ display: "flex", gap: "12px", marginBottom: "16px", alignItems: "center", justifyContent: "space-between" }}>
          <label className="search-box full" style={{ margin: 0, flex: 1 }}>
            <Search size={14} />
            <input
              placeholder="Rechercher une conversation..."
              value={chatSearch}
              onChange={(e) => setChatSearch(e.target.value)}
            />
          </label>
          <button className="icon-button" onClick={() => void fetchHistory()} disabled={isLoading} title="Rafraîchir">
            <History size={16} />
          </button>
        </div>

        <div className="history-list" style={{ display: "grid", gap: "8px" }}>
          {isLoading ? (
            <div className="history-empty" style={{ padding: "20px", textAlign: "center", color: "var(--text-muted)" }}>
              Chargement de l'historique...
            </div>
          ) : displayedSessions.length ? (
            displayedSessions.map((item) => (
              <button
                key={item.id}
                className="history-button"
                style={{ textAlign: "left", padding: "12px 16px", background: "var(--bg-elevated)", border: "1px solid var(--border)", borderRadius: "var(--radius-md)", cursor: "pointer" }}
                onClick={() => navigate(`/conversation?id=${item.id}`)}
              >
                <div style={{ fontWeight: 500, color: "var(--text-primary)", marginBottom: "4px" }}>{item.title}</div>
                <small style={{ color: "var(--text-tertiary)" }}>
                  {item.messageCount} messages · Modifié le {formatShortDate(item.updatedAt)}
                </small>
              </button>
            ))
          ) : (
            <div className="history-empty" style={{ padding: "20px", textAlign: "center", color: "var(--text-muted)" }}>
              Aucune conversation trouvée.
            </div>
          )}
        </div>
      </Card>
    </PageFrame>
  );
}

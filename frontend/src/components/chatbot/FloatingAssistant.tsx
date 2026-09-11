import { useState, type FormEvent } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Sparkles, X, Send, Loader2, Maximize2 } from "lucide-react";
import { askQa } from "../../features/qa/api/qaApi";
import robotIcon from "../../assets/brand/assistant-robot-icon.png";

type QuickMessage = { role: "user" | "assistant"; text?: string; loading?: boolean };

const SUGGESTIONS = [
  "Quel est le volume de commandes du mois dernier ?",
  "Quels produits risquent une rupture de stock ?",
  "Quel est le chiffre d'affaires de la semaine ?",
];

/**
 * Floating assistant button, visible on every authenticated page except the
 * full-screen conversation page ("/conversation"), where the same feature is
 * already front and center.
 */
export function FloatingAssistant() {
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<QuickMessage[]>([]);
  const [sending, setSending] = useState(false);

  if (location.pathname === "/conversation") return null;

  async function ask(question: string) {
    const trimmed = question.trim();
    if (!trimmed || sending) return;
    setInput("");
    setSending(true);
    setMessages((current) => [...current, { role: "user", text: trimmed }, { role: "assistant", loading: true }]);
    try {
      const clientRequestId = window.crypto?.randomUUID ? window.crypto.randomUUID() : `client-${Date.now()}`;
      const response = await askQa(trimmed, clientRequestId, new Date(), false);
      setMessages((current) => {
        const next = [...current];
        next[next.length - 1] = { role: "assistant", text: response.answer };
        return next;
      });
    } catch (error) {
      setMessages((current) => {
        const next = [...current];
        next[next.length - 1] = {
          role: "assistant",
          text: error instanceof Error ? error.message : "Une erreur est survenue. Réessayez.",
        };
        return next;
      });
    } finally {
      setSending(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void ask(input);
  }

  return (
    <>
      {open ? (
        <div className="fixed bottom-24 right-6 z-[999] flex h-[520px] w-[380px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
          <div className="flex items-center justify-between gap-2 border-b border-slate-100 bg-gradient-to-r from-lpn-600 to-lpn-500 px-4 py-3 text-white">
            <div className="flex items-center gap-2">
              <Sparkles size={16} />
              <div>
                <p className="text-sm font-semibold leading-tight">Assistant IA LPN</p>
                <p className="text-[11px] text-lpn-50/90">Posez une question sur vos données</p>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => {
                  setOpen(false);
                  navigate("/conversation");
                }}
                title="Ouvrir la conversation complète"
                className="rounded-lg p-1.5 hover:bg-white/15"
                type="button"
              >
                <Maximize2 size={14} />
              </button>
              <button onClick={() => setOpen(false)} className="rounded-lg p-1.5 hover:bg-white/15" type="button">
                <X size={14} />
              </button>
            </div>
          </div>

          <div className="flex-1 space-y-3 overflow-y-auto p-4">
            {messages.length === 0 ? (
              <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
                <Sparkles size={26} className="text-lpn-300" />
                <p className="text-sm text-slate-400">Posez une question en langage naturel sur vos données LPN.</p>
                <div className="flex flex-col gap-2">
                  {SUGGESTIONS.map((s) => (
                    <button
                      key={s}
                      onClick={() => void ask(s)}
                      type="button"
                      className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:border-lpn-300 hover:text-lpn-600"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
            {messages.map((m, i) =>
              m.role === "user" ? (
                <div key={i} className="flex justify-end">
                  <div className="max-w-[85%] rounded-2xl rounded-tr-sm bg-lpn-500 px-3.5 py-2 text-sm text-white">
                    {m.text}
                  </div>
                </div>
              ) : (
                <div key={i} className="flex justify-start">
                  <div className="max-w-[85%] rounded-2xl rounded-tl-sm border border-slate-100 bg-slate-50 px-3.5 py-2 text-sm text-ink">
                    {m.loading ? (
                      <span className="flex items-center gap-2 text-slate-400">
                        <Loader2 size={13} className="animate-spin" /> Analyse en cours…
                      </span>
                    ) : (
                      m.text
                    )}
                  </div>
                </div>
              ),
            )}
          </div>

          <form onSubmit={onSubmit} className="flex items-center gap-2 border-t border-slate-100 p-3">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Écrivez votre question…"
              className="lb-input-field !py-2 text-sm"
              disabled={sending}
            />
            <button type="submit" className="lb-btn-primary !px-3 !py-2" disabled={sending}>
              <Send size={15} />
            </button>
          </form>
        </div>
      ) : null}

      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Ouvrir l'assistant IA"
        type="button"
        className={`group fixed bottom-6 right-6 z-[999] flex h-16 w-16 items-center justify-center rounded-full bg-white shadow-xl shadow-lpn-500/25 ring-2 ring-white transition-transform hover:scale-105 active:scale-95${open ? "" : " fab-breathe"}`}
      >
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-lpn-400 opacity-20 group-hover:opacity-30" />
        {open ? (
          <span className="flex h-full w-full items-center justify-center rounded-full bg-gradient-to-br from-lpn-600 to-lpn-400 text-white">
            <X size={22} />
          </span>
        ) : (
          <img src={robotIcon} alt="Assistant IA LPN" className="h-full w-full rounded-full object-cover drop-shadow-md" />
        )}
      </button>
    </>
  );
}

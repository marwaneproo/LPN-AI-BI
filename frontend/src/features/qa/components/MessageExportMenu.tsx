import { useEffect, useRef, useState } from "react";
import { Download, FileText, Loader2 } from "lucide-react";
import type { Message } from "../types/qa.types";
import { exportMessageAsPdf, exportMessageAsWord } from "../utils/messageExport";

export function MessageExportMenu({ message, question }: { message: Message; question?: string }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<"pdf" | "word" | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const handleKeydown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    window.addEventListener("keydown", handleKeydown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      window.removeEventListener("keydown", handleKeydown);
    };
  }, [open]);

  async function handleExport(format: "pdf" | "word") {
    setBusy(format);
    try {
      if (format === "pdf") {
        await exportMessageAsPdf(message, question);
      } else {
        await exportMessageAsWord(message, question);
      }
      setOpen(false);
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error(`Message export failed (format=${format})`, error);
      }
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="message-export-menu" ref={containerRef}>
      <button
        type="button"
        className="ghost-icon"
        aria-label="Exporter cette réponse"
        aria-haspopup="menu"
        aria-expanded={open}
        title="Exporter cette réponse (PDF ou Word)"
        onClick={() => setOpen((v) => !v)}
      >
        <Download size={13} />
      </button>
      {open ? (
        <div className="message-export-dropdown" role="menu">
          <button type="button" role="menuitem" disabled={busy !== null} onClick={() => void handleExport("pdf")}>
            {busy === "pdf" ? <Loader2 size={13} className="bi-spin" /> : <FileText size={13} />}
            Exporter en PDF
          </button>
          <button type="button" role="menuitem" disabled={busy !== null} onClick={() => void handleExport("word")}>
            {busy === "word" ? <Loader2 size={13} className="bi-spin" /> : <FileText size={13} />}
            Exporter en Word (.docx)
          </button>
        </div>
      ) : null}
    </div>
  );
}

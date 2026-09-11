import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

type BiFullscreenModalProps = {
  open: boolean;
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
};

/**
 * Generic fullscreen overlay for any BI chart/widget. Renders the SAME React children the
 * card already has (no re-fetch, no re-mount of data — just a portaled re-render), so every
 * interaction the chart already supports (zoom, tooltip, legend, filters) keeps working as-is.
 * Follows BiDetailDrawer's ESC/portal/scroll-lock conventions so behaviour stays consistent
 * across every modal in the app. Reusable across every BI view (Overview, Ventes, Achats,
 * Produits, Clients, Fournisseurs, Stocks, and any future page) since it is generic over
 * `children` rather than tied to a specific chart type.
 */
export function BiFullscreenModal({ open, title, subtitle, onClose, children }: BiFullscreenModalProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return undefined;
    const handleKeydown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    previouslyFocused.current = document.activeElement as HTMLElement | null;
    closeButtonRef.current?.focus();
    return () => {
      previouslyFocused.current?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div className="bi-fullscreen-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="bi-fullscreen-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bi-fullscreen-modal-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="bi-fullscreen-modal-header">
          <div>
            <h3 id="bi-fullscreen-modal-title">{title}</h3>
            {subtitle ? <p>{subtitle}</p> : null}
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            className="bi-icon-button"
            onClick={onClose}
            aria-label="Fermer le plein écran"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <div className="bi-fullscreen-modal-body">{children}</div>
      </section>
    </div>,
    document.body,
  );
}

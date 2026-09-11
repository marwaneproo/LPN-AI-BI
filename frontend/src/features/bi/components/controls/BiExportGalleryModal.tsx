import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { CheckSquare, Download, Loader2, Square, X } from "lucide-react";
import type { RegisteredBiWidget } from "../../context/BiWidgetRegistry";
import { captureBiWidgetPng, downloadPngDataUrl, safeFilePart } from "../../utils/biExport";

type GalleryItem = {
  widget: RegisteredBiWidget;
  dataUrl: string | null;
  failed: boolean;
};

type BiExportGalleryModalProps = {
  open: boolean;
  onClose: () => void;
  widgets: RegisteredBiWidget[];
  pageTitle: string;
  pageSlug: string;
  rangeLabel: string;
};

/**
 * Opened instead of an immediate single-file download when the user picks the PNG format in
 * BiExportDialog. Shows every chart currently visible in the current view (via the
 * BiWidgetRegistry — never another tab's charts, since those aren't mounted) as a real
 * thumbnail — the same capture used for the final high-res download, not an approximation —
 * with a checkbox each, "select all", and per-item or bulk export:
 *  - one selected  -> downloads that single high-res PNG
 *  - several selected -> downloads one PNG file per widget
 *  - all selected -> downloads every visible widget of this view
 * Period/filters/colors/dimensions are already "baked into" each widget's rendered DOM (the
 * page already applied them before this modal ever opens), so every capture reflects them
 * automatically with no extra wiring here.
 */
export function BiExportGalleryModal({ open, onClose, widgets, pageTitle, pageSlug, rangeLabel }: BiExportGalleryModalProps) {
  const [items, setItems] = useState<GalleryItem[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [isCapturing, setIsCapturing] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setIsCapturing(true);
    setSelected(new Set(widgets.map((w) => w.id)));
    setItems(widgets.map((widget) => ({ widget, dataUrl: null, failed: false })));

    let cancelled = false;
    Promise.all(
      widgets.map(async (widget) => {
        try {
          const dataUrl = await captureBiWidgetPng(widget.element);
          return { widget, dataUrl, failed: false };
        } catch {
          return { widget, dataUrl: null, failed: true };
        }
      }),
    ).then((results) => {
      if (!cancelled) {
        setItems(results);
        setIsCapturing(false);
      }
    });

    return () => {
      cancelled = true;
    };
    // Only re-run when the modal is (re)opened, not on every widgets-array identity change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const handleKeydown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isDownloading) onClose();
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [open, onClose, isDownloading]);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) return null;

  const allSelected = items.length > 0 && selected.size === items.length;

  const toggleOne = (id: string) => {
    setSelected((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(items.map((item) => item.widget.id)));
  };

  const handleDownload = async () => {
    const toDownload = items.filter((item) => selected.has(item.widget.id) && item.dataUrl);
    if (toDownload.length === 0) return;
    setIsDownloading(true);
    try {
      for (const item of toDownload) {
        const filenameBase = `${safeFilePart(pageSlug)}_${safeFilePart(item.widget.title)}`;
        downloadPngDataUrl(item.dataUrl as string, filenameBase);
        // Small stagger so the browser reliably triggers every download instead of
        // silently dropping some when many `<a download>` clicks fire back to back.
        await new Promise((resolve) => setTimeout(resolve, 120));
      }
      setTimeout(onClose, 300);
    } finally {
      setIsDownloading(false);
    }
  };

  const selectionLabel =
    selected.size === 0
      ? "Sélectionnez au moins un graphique"
      : selected.size === 1
        ? "Télécharger ce graphique (PNG haute résolution)"
        : `Télécharger ${selected.size} graphiques (${selected.size} fichiers PNG)`;

  return createPortal(
    <div className="bi-modal-backdrop" role="presentation" onMouseDown={isDownloading ? undefined : onClose}>
      <section
        className="bi-export-gallery"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bi-export-gallery-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="bi-modal-header">
          <div>
            <span className="bi-modal-eyebrow">Export PNG</span>
            <h3 id="bi-export-gallery-title">{pageTitle} — {rangeLabel}</h3>
          </div>
          <button type="button" className="bi-icon-button" onClick={onClose} aria-label="Fermer" disabled={isDownloading}>
            <X size={17} aria-hidden="true" />
          </button>
        </header>

        <div className="bi-export-gallery-toolbar">
          <button type="button" className="bi-export-gallery-select-all" onClick={toggleAll} disabled={items.length === 0}>
            {allSelected ? <CheckSquare size={15} aria-hidden="true" /> : <Square size={15} aria-hidden="true" />}
            Tout sélectionner
          </button>
          <span className="bi-export-gallery-count">
            {items.length} graphique{items.length > 1 ? "s" : ""} visible{items.length > 1 ? "s" : ""} dans cette vue
          </span>
        </div>

        <div className="bi-export-gallery-grid">
          {items.map((item) => {
            const isSelected = selected.has(item.widget.id);
            return (
              <button
                type="button"
                key={item.widget.id}
                className={`bi-export-gallery-card${isSelected ? " is-selected" : ""}`}
                onClick={() => toggleOne(item.widget.id)}
                disabled={!item.dataUrl}
                aria-pressed={isSelected}
              >
                <span className="bi-export-gallery-thumb">
                  {item.dataUrl ? (
                    <img src={item.dataUrl} alt="" />
                  ) : item.failed ? (
                    <span className="bi-export-gallery-thumb-error">Capture indisponible</span>
                  ) : (
                    <Loader2 size={20} aria-hidden="true" className="bi-spin" />
                  )}
                </span>
                <span className="bi-export-gallery-card-footer">
                  <span className="bi-export-gallery-checkbox" aria-hidden="true">
                    {isSelected ? <CheckSquare size={15} /> : <Square size={15} />}
                  </span>
                  <span className="bi-export-gallery-title">{item.widget.title}</span>
                </span>
              </button>
            );
          })}
        </div>

        <footer className="bi-modal-footer">
          <button type="button" className="bi-modal-secondary" onClick={onClose} disabled={isDownloading}>
            Annuler
          </button>
          <button
            type="button"
            className="bi-modal-primary"
            onClick={() => void handleDownload()}
            disabled={selected.size === 0 || isCapturing || isDownloading}
          >
            {isDownloading ? (
              <>
                <Loader2 size={16} aria-hidden="true" className="bi-spin" />
                Téléchargement…
              </>
            ) : (
              <>
                <Download size={16} aria-hidden="true" />
                {selectionLabel}
              </>
            )}
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  );
}

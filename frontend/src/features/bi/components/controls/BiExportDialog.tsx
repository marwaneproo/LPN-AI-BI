import { useState, useEffect } from "react";
import { Check, FileDown, FileImage, FileSpreadsheet, Loader2, X } from "lucide-react";
import type { BiExportFormat } from "../../utils/biExport";

type ExportOption = {
  format: BiExportFormat;
  label: string;
  description: string;
  hint: string;
  icon: typeof FileImage;
};

const exportOptions: ExportOption[] = [
  {
    format: "png",
    label: "Image PNG",
    description: "Capture haute résolution de la vue BI.",
    hint: "Idéal pour Slack, email ou présentation rapide",
    icon: FileImage,
  },
  {
    format: "pdf",
    label: "Document PDF",
    description: "Document mis en page avec en-tête et contexte.",
    hint: "Pour les réunions, comités et rapports imprimés",
    icon: FileDown,
  },
  {
    format: "excel",
    label: "Classeur Excel",
    description: "Classeur Excel avec le contexte et les métadonnées du rapport.",
    hint: "Pour explorer les données dans un tableur",
    icon: FileSpreadsheet,
  },
];

export function BiExportDialog({
  open,
  isExporting,
  exportSuccess,
  error,
  pageTitle,
  rangeLabel,
  compareEnabled,
  onClose,
  onExport,
}: {
  open: boolean;
  isExporting: boolean;
  exportSuccess: boolean;
  error: string | null;
  pageTitle: string;
  rangeLabel: string;
  compareEnabled: boolean;
  onClose: () => void;
  onExport: (format: BiExportFormat) => void;
}) {
  const [selected, setSelected] = useState<BiExportFormat | null>(null);

  useEffect(() => {
    if (open) {
      setSelected(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isExporting) onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, open, isExporting]);

  if (!open) return null;

  const handleExport = () => {
    if (!selected || isExporting) return;
    onExport(selected);
  };

  const filenameBase = `lpn-bi-${pageTitle.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}`;
  const extension = selected === "excel" ? "xlsx" : selected ?? "…";

  return (
    <div
      className="bi-modal-backdrop"
      role="presentation"
      onMouseDown={isExporting ? undefined : onClose}
    >
      <section
        className="bi-export-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bi-export-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="bi-modal-header">
          <div>
            <span className="bi-modal-eyebrow">Export</span>
            <h3 id="bi-export-dialog-title">Exporter cette vue</h3>
          </div>
          <button
            type="button"
            className="bi-icon-button"
            onClick={onClose}
            aria-label="Fermer"
            disabled={isExporting}
          >
            <X size={17} aria-hidden="true" />
          </button>
        </header>

        {/* Export context */}
        <div className="bi-export-context">
          <div className="bi-export-context-row">
            <span className="bi-export-context-label">Page</span>
            <span className="bi-export-context-value">{pageTitle}</span>
          </div>
          <div className="bi-export-context-row">
            <span className="bi-export-context-label">Période</span>
            <span className="bi-export-context-value">{rangeLabel}</span>
          </div>
          <div className="bi-export-context-row">
            <span className="bi-export-context-label">Comparaison</span>
            <span className="bi-export-context-value">{compareEnabled ? "Activée" : "Désactivée"}</span>
          </div>
        </div>

        {error && (
          <div className="bi-export-error" role="alert">
            {error}
          </div>
        )}

        {exportSuccess && (
          <div className="bi-export-success" role="status">
            <Check size={15} aria-hidden="true" />
            Export terminé avec succès.
          </div>
        )}

        {/* Format cards */}
        <div className="bi-dialog-section">
          <span className="bi-dialog-section-label">Format</span>
          <div className="bi-export-grid">
            {exportOptions.map((option) => {
              const Icon = option.icon;
              const isSelected = selected === option.format;
              return (
                <button
                  type="button"
                  className={`bi-export-card${isSelected ? " is-selected" : ""}`}
                  key={option.format}
                  onClick={() => setSelected(option.format)}
                  disabled={isExporting}
                  aria-pressed={isSelected}
                >
                  <span className="bi-export-card-icon">
                    <Icon size={20} aria-hidden="true" />
                  </span>
                  <div className="bi-export-card-body">
                    <strong>{option.label}</strong>
                    <span>{option.description}</span>
                    <small>{option.hint}</small>
                  </div>
                  {isSelected && (
                    <span className="bi-export-card-check" aria-hidden="true">
                      <Check size={14} />
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* Filename preview */}
        {selected && (
          <div className="bi-export-filename">
            <span className="bi-export-filename-label">Fichier</span>
            <code>{filenameBase}.{extension}</code>
          </div>
        )}

        <footer className="bi-modal-footer">
          <button
            type="button"
            className="bi-modal-secondary"
            onClick={onClose}
            disabled={isExporting}
          >
            Annuler
          </button>
          <button
            type="button"
            className="bi-modal-primary"
            onClick={handleExport}
            disabled={!selected || isExporting}
          >
            {isExporting ? (
              <>
                <Loader2 size={16} aria-hidden="true" className="bi-spin" />
                Export en cours…
              </>
            ) : (
              <>
                <Check size={16} aria-hidden="true" />
                Exporter
              </>
            )}
          </button>
        </footer>
      </section>
    </div>
  );
}

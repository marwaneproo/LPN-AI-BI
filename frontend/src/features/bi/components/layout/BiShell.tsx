import type { ReactNode } from "react";
import { useRef, useState } from "react";
import { Calendar, ChevronDown, Download, RefreshCw } from "lucide-react";
import { useLocation } from "react-router-dom";
import { PageFrame } from "../../../../components/layout/PageFrame";
import { formatBiRangeLabel, type BiDateRange } from "../controls/BiDateRangeDialog";
import { BiDateRangePopover } from "../controls/BiDateRangePopover";
import { BiExportDialog } from "../controls/BiExportDialog";
import { BiExportGalleryModal } from "../controls/BiExportGalleryModal";
import { BiFilterChips, type BiFilterChip } from "../controls/BiFilterChips";
import { BiSectionNav } from "../navigation/BiSectionNav";
import { BI_DEFAULT_RANGE } from "../../hooks/useBiRange";
import { useRelativeTimeLabel } from "../../hooks/useRelativeTimeLabel";
import { exportBiView, biExportErrorMessage, type BiExportFormat } from "../../utils/biExport";
import { BiWidgetRegistryProvider, useBiWidgetRegistry } from "../../context/BiWidgetRegistry";
import type { RegisteredBiWidget } from "../../context/BiWidgetRegistry";

type BiShellProps = {
  title: string;
  subtitle: string;
  eyebrow: string;
  description: string;
  children: ReactNode;
  range: BiDateRange;
  onApplyRange: (range: BiDateRange) => void;
  compareEnabled: boolean;
  onToggleCompare: () => void;
  filtersSlot?: ReactNode;
  /** Extra removable filter chips (e.g. Clients-page filters) rendered after the built-in période/comparaison chips. */
  activeFilterChips?: BiFilterChip[];
  /** ISO timestamp of the page's data (meta.generated_at) — drives the live "Mis à jour il y a X" pill. */
  lastUpdatedAt?: string;
};

function exportPageSlug(pathname: string) {
  if (pathname.includes("/commandes")) return "orders";
  if (pathname.includes("/chiffre-affaires")) return "revenue";
  if (pathname.includes("/articles")) return "articles";
  if (pathname.includes("/clients")) return "clients";
  if (pathname.includes("/achats")) return "achats";
  if (pathname.includes("/commercial")) return "commercial";
  if (pathname.includes("/analyse-vente")) return "analysis";
  return "overview";
}

export function BiShell(props: BiShellProps) {
  return (
    <BiWidgetRegistryProvider>
      <BiShellContent {...props} />
    </BiWidgetRegistryProvider>
  );
}

function BiShellContent({
  title,
  subtitle,
  eyebrow,
  description,
  children,
  range,
  onApplyRange,
  compareEnabled,
  onToggleCompare,
  filtersSlot,
  activeFilterChips,
  lastUpdatedAt,
}: BiShellProps) {
  const location = useLocation();
  const exportRef = useRef<HTMLElement | null>(null);
  const dateButtonRef = useRef<HTMLButtonElement | null>(null);
  const widgetRegistry = useBiWidgetRegistry();
  const [isDateDialogOpen, setDateDialogOpen] = useState(false);
  const [isExportDialogOpen, setExportDialogOpen] = useState(false);
  const [isExporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [isGalleryOpen, setGalleryOpen] = useState(false);
  const [galleryWidgets, setGalleryWidgets] = useState<RegisteredBiWidget[]>([]);
  const rangeLabel = formatBiRangeLabel(range);
  const { label: updatedLabel, absolute: updatedAbsolute } = useRelativeTimeLabel(lastUpdatedAt);
  const pageSlug = exportPageSlug(location.pathname);

  const filterChips: BiFilterChip[] = [
    {
      key: "periode",
      label: `Période : ${rangeLabel}`,
      ariaLabel: `Réinitialiser la période (actuellement ${rangeLabel})`,
      onRemove: () => onApplyRange(BI_DEFAULT_RANGE),
    },
    ...(compareEnabled
      ? [
          {
            key: "comparaison",
            label: "Comparaison active",
            ariaLabel: "Désactiver la comparaison avec la période précédente",
            onRemove: onToggleCompare,
          },
        ]
      : []),
    ...(activeFilterChips ?? []),
  ];

  const handleApplyDateRange = (next: BiDateRange) => {
    onApplyRange(next);
    setDateDialogOpen(false);
  };

  const handleExport = async (format: BiExportFormat) => {
    // PNG is handled by the multi-select export gallery instead of an immediate single-file
    // download — see BiExportGalleryModal. It reads the widgets already registered by every
    // BiWidgetCard currently mounted in THIS view (never another tab's, since those aren't
    // mounted), so period/filters/zoom already baked into their rendered DOM come along for free.
    if (format === "png") {
      setGalleryWidgets(widgetRegistry.getWidgets());
      setExportDialogOpen(false);
      setGalleryOpen(true);
      return;
    }
    if (!exportRef.current) {
      setExportError("La zone à exporter est introuvable. Rechargez la page puis réessayez.");
      return;
    }
    setExporting(true);
    setExportError(null);
    setExportSuccess(false);
    try {
      await exportBiView(exportRef.current, format, {
        title,
        subtitle,
        rangeLabel,
        comparisonEnabled: compareEnabled,
        pageSlug,
        from: range.from,
        to: range.to,
        granularity: range.mode === "day" ? "day" : "month",
      });
      setExportSuccess(true);
      setTimeout(() => setExportDialogOpen(false), 1200);
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error(`BI export failed (format=${format}, page=${title})`, error);
      }
      setExportError(biExportErrorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const openExportDialog = () => {
    setExportError(null);
    setExportSuccess(false);
    setExportDialogOpen(true);
  };

  return (
    <PageFrame className="bi-shell-page" title={title} subtitle={subtitle} hideHeader>
      <div className="bi-floating-nav-wrap">
        <BiSectionNav />
      </div>

      <section className="bi-stage" ref={exportRef}>
        <div className="bi-spotlight-sticky">
          <div className="bi-spotlight">
            <div className="bi-spotlight-copy">
              <span className="bi-spotlight-eyebrow">{eyebrow}</span>
              <h2>{title}</h2>
              <p>{description}</p>
            </div>
            <div className="bi-spotlight-actions">
              <button
                ref={dateButtonRef}
                type="button"
                className="bi-spotlight-btn bi-spotlight-btn-outline"
                aria-label={`Changer la période. Période sélectionnée : ${rangeLabel}`}
                onClick={() => setDateDialogOpen((v) => !v)}
              >
                <Calendar size={14} aria-hidden="true" />
                <span>{rangeLabel}</span>
                <ChevronDown size={13} aria-hidden="true" />
              </button>
              <button
                type="button"
                className={`bi-spotlight-btn bi-spotlight-btn-outline${compareEnabled ? " is-active" : ""}`}
                aria-pressed={compareEnabled}
                aria-label="Comparer avec la période précédente"
                onClick={onToggleCompare}
              >
                <RefreshCw size={14} aria-hidden="true" />
                <span>{compareEnabled ? "Comparé" : "Comparer"}</span>
              </button>
              {filtersSlot}
              <span
                className="bi-spotlight-status"
                title={updatedAbsolute ? `Données générées le ${updatedAbsolute}` : undefined}
                aria-label={updatedLabel ? `Données mises à jour ${updatedLabel}` : "Mis à jour"}
              >
                <span className="bi-spotlight-status-dot" aria-hidden="true" />
                {updatedLabel ? `Mis à jour ${updatedLabel}` : "Mis à jour"}
              </span>
              <button type="button" className="bi-spotlight-btn bi-spotlight-btn-primary" aria-label="Exporter les données" onClick={openExportDialog}>
                <Download size={14} aria-hidden="true" />
                <span>Exporter</span>
              </button>
            </div>
          </div>

          <BiFilterChips chips={filterChips} />
        </div>

        {children}
      </section>

      <BiDateRangePopover open={isDateDialogOpen} value={range} onClose={() => setDateDialogOpen(false)} onApply={handleApplyDateRange} anchorRef={dateButtonRef} />
      <BiExportDialog
        open={isExportDialogOpen}
        isExporting={isExporting}
        exportSuccess={exportSuccess}
        error={exportError}
        pageTitle={title}
        rangeLabel={rangeLabel}
        compareEnabled={compareEnabled}
        onClose={() => setExportDialogOpen(false)}
        onExport={handleExport}
      />
      <BiExportGalleryModal
        open={isGalleryOpen}
        onClose={() => setGalleryOpen(false)}
        widgets={galleryWidgets}
        pageTitle={title}
        pageSlug={pageSlug}
        rangeLabel={rangeLabel}
      />
    </PageFrame>
  );
}

import type { ReactNode } from "react";
import { useEffect, useId, useRef, useState } from "react";
import { AlertTriangle, Download, ImageDown, Maximize2, ShieldCheck, TableProperties } from "lucide-react";
import { formatDelta, formatPercent } from "../../../../utils/formatters";
import type { BiWidgetDetail } from "../../types/bi.types";
import { BiDetailDrawer } from "../BiDetailDrawer";
import { BiFullscreenModal } from "../BiFullscreenModal";
import { BiDataTable } from "../BiDataTable";
import { downloadBiCsv } from "../../utils/biCsv";
import { exportBiWidgetPng } from "../../utils/biExport";
import { useOptionalBiWidgetRegistry } from "../../context/BiWidgetRegistry";

type BiMetricCardProps = {
  label: string;
  value: string;
  helper: string;
  tone?: "blue" | "green" | "pink" | "amber" | "red";
};

type BiWidgetCardProps = {
  title: string;
  subtitle: string;
  children: ReactNode;
  wide?: boolean;
  className?: string;
  action?: ReactNode;
  /** When present, adds a "Voir les données" trigger opening a sortable-table drawer + CSV download. */
  detail?: BiWidgetDetail;
};

type BiBarItem = {
  label: string;
  value: number;
  color?: string;
};

type BiSignalItem = {
  label: string;
  value: string;
  helper: string;
};

export function BiMetricCard({ label, value, helper, tone = "blue" }: BiMetricCardProps) {
  return (
    <article className={`bi-metric-card bi-metric-card-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{helper}</small>
    </article>
  );
}

const COMPARE_TONE_COLOR: Record<string, string> = {
  blue: "var(--chart-blue)",
  green: "var(--chart-green)",
  pink: "var(--bi-pink)",
  amber: "var(--chart-amber)",
  violet: "var(--chart-violet)",
};

type BiComparisonCardProps = {
  label: string;
  value: string;
  helper?: string;
  tone?: "blue" | "green" | "pink" | "amber" | "violet";
  currentYear: number;
  previousYear: number;
  currentValue: number;
  previousValue: number;
  increaseIsGood?: boolean;
};

/** KPI card with a year-over-year 2-bar comparison (prior vs current year) + % delta. */
export function BiComparisonCard({
  label,
  value,
  helper,
  tone = "blue",
  currentYear,
  previousYear,
  currentValue,
  previousValue,
  increaseIsGood = true,
}: BiComparisonCardProps) {
  const current = Number(currentValue || 0);
  const previous = Number(previousValue || 0);
  const max = Math.max(Math.abs(current), Math.abs(previous), 1);
  const currentPct = Math.max(3, Math.round((Math.abs(current) / max) * 100));
  const previousPct = Math.max(3, Math.round((Math.abs(previous) / max) * 100));
  const hasComparison = previousYear !== currentYear && previous !== 0;
  const delta = hasComparison ? formatDelta(current, previous, increaseIsGood) : { text: "—", tone: "neutral" as const };
  const deltaDirection = delta.text.startsWith("+") ? "▲ " : delta.text.startsWith("-") ? "▼ " : "";
  const valueParts = value.endsWith(" KDH") ? value.replace(/\s*KDH$/, "") : null;
  const valueNode = valueParts ? <>{valueParts}<span className="bi-compare-unit">KDH</span></> : value;

  return (
    <article className={`bi-metric-card bi-metric-card-${tone} bi-compare-card`}>
      <span>{label}</span>
      <strong>{valueNode}</strong>
      {helper ? <small>{helper}</small> : null}
      <div className="bi-compare">
        <div className="bi-compare-head">
          {hasComparison ? (
            <span className={`bi-compare-pct ${delta.tone === "good" ? "is-up" : delta.tone === "bad" ? "is-down" : "is-flat"}`}>
              {deltaDirection}
              {delta.text}
            </span>
          ) : (
            <span className="bi-compare-pct is-flat">—</span>
          )}
          <small>vs {previousYear}</small>
        </div>
        <div className="bi-compare-bars">
          <div className="bi-compare-row">
            <span className="bi-compare-year">{previousYear}</span>
            <span className="bi-compare-track">
              <span className="bi-compare-fill bi-compare-prev" style={{ width: `${previousPct}%` }} />
            </span>
          </div>
          <div className="bi-compare-row">
            <span className="bi-compare-year">{currentYear}</span>
            <span className="bi-compare-track">
              <span className="bi-compare-fill" style={{ width: `${currentPct}%`, background: COMPARE_TONE_COLOR[tone] }} />
            </span>
          </div>
        </div>
      </div>
    </article>
  );
}

type WidgetDownloadState = "idle" | "busy" | "error";

const DOWNLOAD_LABELS: Record<WidgetDownloadState, string> = {
  idle: "Télécharger ce graphique en PNG",
  busy: "Capture du graphique en cours…",
  error: "La capture a échoué — réessayez.",
};

export function BiWidgetCard({ title, subtitle, children, wide = false, className = "", action, detail }: BiWidgetCardProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const cardRef = useRef<HTMLElement | null>(null);
  const [downloadState, setDownloadState] = useState<WidgetDownloadState>("idle");
  const errorResetRef = useRef<number | undefined>(undefined);
  const widgetId = useId();
  const registry = useOptionalBiWidgetRegistry();

  useEffect(() => () => window.clearTimeout(errorResetRef.current), []);

  // Register/refresh this widget in the shared registry so the export gallery (BiShell) can
  // enumerate every chart currently on screen without any page-level wiring.
  useEffect(() => {
    if (!registry || !cardRef.current) return undefined;
    registry.register(widgetId, title, subtitle, cardRef.current);
    return () => registry.unregister(widgetId);
  }, [registry, widgetId, title, subtitle]);

  const handleDownloadPng = async () => {
    if (!cardRef.current || downloadState === "busy") return;
    window.clearTimeout(errorResetRef.current);
    setDownloadState("busy");
    try {
      await exportBiWidgetPng(cardRef.current, title);
      setDownloadState("idle");
    } catch (error) {
      if (import.meta.env.DEV) {
        console.error(`BI widget PNG export failed (widget=${title})`, error);
      }
      setDownloadState("error");
      errorResetRef.current = window.setTimeout(() => setDownloadState("idle"), 3000);
    }
  };

  return (
    <section ref={cardRef} className={`bi-widget-card${wide ? " bi-widget-card-wide" : ""}${className ? ` ${className}` : ""}`}>
      <div className="bi-widget-heading">
        <div>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        <div className="bi-widget-heading-actions">
          {action}
          {detail ? (
            <button type="button" className="bi-widget-detail-btn" onClick={() => setDrawerOpen(true)}>
              <TableProperties size={13} aria-hidden="true" />
              <span>Voir les données</span>
            </button>
          ) : null}
          <button
            type="button"
            className="bi-widget-fullscreen-btn"
            aria-label={`Plein écran — ${title}`}
            title="Afficher en plein écran"
            onClick={() => setFullscreenOpen(true)}
          >
            <Maximize2 size={13} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="bi-widget-download-btn"
            data-state={downloadState}
            disabled={downloadState === "busy"}
            aria-busy={downloadState === "busy"}
            aria-label={`${DOWNLOAD_LABELS[downloadState]} — ${title}`}
            title={DOWNLOAD_LABELS[downloadState]}
            onClick={() => void handleDownloadPng()}
          >
            <ImageDown size={13} aria-hidden="true" />
          </button>
        </div>
      </div>
      {children}
      {detail ? (
        <BiDetailDrawer
          open={drawerOpen}
          title={detail.title}
          subtitle="Données détaillées de ce widget, triables par colonne."
          onClose={() => setDrawerOpen(false)}
          footer={
            <button
              type="button"
              className="bi-drawer-download"
              onClick={() => downloadBiCsv(detail.columns, detail.rows, detail.filename)}
            >
              <Download size={14} aria-hidden="true" />
              Télécharger CSV
            </button>
          }
        >
          <BiDataTable columns={detail.columns} rows={detail.rows} />
        </BiDetailDrawer>
      ) : null}
      <BiFullscreenModal open={fullscreenOpen} title={title} subtitle={subtitle} onClose={() => setFullscreenOpen(false)}>
        {children}
      </BiFullscreenModal>
    </section>
  );
}

export function BiBarPreview({ items }: { items: BiBarItem[] }) {
  const max = Math.max(...items.map((item) => item.value), 1);

  return (
    <div className="bi-bar-preview" aria-label="Apercu graphique">
      {items.map((item) => (
        <div className="bi-bar-preview-row" key={item.label}>
          <span>{item.label}</span>
          <div className="bi-bar-track">
            <span
              className="bi-bar-fill"
              style={{
                width: `${Math.max(8, Math.round((item.value / max) * 100))}%`,
                background: item.color,
              }}
            />
          </div>
          <strong>{formatPercent(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

export function BiColumnPreview({ items }: { items: BiBarItem[] }) {
  const max = Math.max(...items.map((item) => item.value), 1);

  return (
    <div className="bi-column-preview" aria-label="Apercu histogramme">
      {items.map((item) => {
        const pct = Math.max(14, Math.round((item.value / max) * 100));
        return (
          <div className="bi-column-preview-item" key={item.label}>
            <div className="bi-column-preview-track">
              <span className="bi-column-preview-spacer" style={{ flexGrow: 100 - pct }} />
              <span
                className="bi-column-preview-bar"
                style={{ flexGrow: pct, background: item.color }}
              />
            </div>
            <small>{item.label}</small>
          </div>
        );
      })}
    </div>
  );
}

export function BiSignalList({ items }: { items: BiSignalItem[] }) {
  return (
    <div className="bi-signal-list">
      {items.map((item) => (
        <div className="bi-signal-row" key={item.label}>
          <div>
            <span>{item.label}</span>
            <small>{item.helper}</small>
          </div>
          <strong>{item.value}</strong>
        </div>
      ))}
    </div>
  );
}

type BiCommercialFlowItem = {
  id: string | number;
  /** Real salesrep_id when the row maps to an actual commercial (null for "Sans commercial"). */
  salesrepId?: number | null;
  label: string;
  meta: string;
  /** Ordered CA, already formatted for display. */
  orderedSales: string;
  /** Bar width relative to the top commercial (0–100). */
  widthPct: number;
  deliveredPercent: number;
  invoicedPercent: number;
};

/**
 * Per-commercial ordered-CA reading with delivered% / invoiced% coverage badges.
 * With `onItemClick`, rows with a real salesrep_id become toggle buttons
 * (click = filter the page on that commercial, re-click = clear) and
 * `selectedId` marks the active one.
 */
export function BiCommercialFlowList({
  items,
  selectedId = null,
  onItemClick,
}: {
  items: BiCommercialFlowItem[];
  selectedId?: number | null;
  onItemClick?: (item: BiCommercialFlowItem) => void;
}) {
  return (
    <div className="bi-commercial-flow" aria-label="Lecture commerciale — CA commandé et couvertures">
      {items.map((item) => {
        const isClickable = Boolean(onItemClick && item.salesrepId);
        const isSelected = selectedId !== null && item.salesrepId === selectedId;
        const rowClassName = `bi-commercial-flow-row${isClickable ? " bi-commercial-flow-row-clickable" : ""}${isSelected ? " bi-commercial-flow-row-selected" : ""}`;
        const content = (
          <>
            <div className="bi-commercial-flow-head">
              <span className="bi-commercial-flow-name" title={item.label}>{item.label}</span>
              <strong className="bi-commercial-flow-value">{item.orderedSales}</strong>
            </div>
            <div className="bi-commercial-flow-track">
              <span className="bi-commercial-flow-fill" style={{ width: `${Math.max(6, Math.round(item.widthPct))}%` }} />
            </div>
            <div className="bi-commercial-flow-foot">
              <small className="bi-commercial-flow-meta">{item.meta}</small>
              <span className="bi-commercial-flow-badges">
                <span className="bi-coverage-badge bi-coverage-badge-delivered">Livré {formatPercent(item.deliveredPercent)}</span>
                <span className="bi-coverage-badge bi-coverage-badge-invoiced">Facturé {formatPercent(item.invoicedPercent)}</span>
              </span>
            </div>
          </>
        );

        if (!isClickable) {
          return (
            <div className={rowClassName} key={item.id}>
              {content}
            </div>
          );
        }

        return (
          <button
            type="button"
            className={rowClassName}
            key={item.id}
            aria-pressed={isSelected}
            title={isSelected ? `Retirer le filtre ${item.label}` : `Filtrer la page sur ${item.label}`}
            onClick={() => onItemClick?.(item)}
          >
            {content}
          </button>
        );
      })}
    </div>
  );
}

type BiUnpaidTableRow = {
  id: string | number;
  name: string;
  amount: string;
  invoiceCount: number;
};

/** Compact top-unpaid-clients table: plain semantic HTML, styled via the shared table rules in theme.css. */
export function BiUnpaidTable({ rows }: { rows: BiUnpaidTableRow[] }) {
  return (
    <div className="table-wrap bi-unpaid-table">
      <table>
        <thead>
          <tr>
            <th>Client</th>
            <th>Montant</th>
            <th>Factures</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{row.name}</td>
              <td className="bi-unpaid-amount">{row.amount}</td>
              <td>{row.invoiceCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export type BiStockPriorityRow = {
  id: string | number;
  name: string;
  category: string;
  /** Already formatted with formatNumber (may be negative). */
  available: string;
  reserved: string;
  badge: { text: string; tone: "critical" | "low" | "ok" };
};

/** Compact stock-priorities table: plain semantic HTML like BiUnpaidTable, risk badge in the last column. */
export function BiStockPriorityTable({ rows }: { rows: BiStockPriorityRow[] }) {
  return (
    <div className="table-wrap bi-stock-table">
      <table>
        <thead>
          <tr>
            <th>Article</th>
            <th>Catégorie</th>
            <th>Disponible</th>
            <th>Réservé</th>
            <th>Risque</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td className="bi-stock-name" title={row.name}>{row.name}</td>
              <td className="bi-stock-category">{row.category}</td>
              <td className="bi-stock-qty">{row.available}</td>
              <td className="bi-stock-qty">{row.reserved}</td>
              <td>
                <span className={`bi-risk-tag bi-risk-${row.badge.tone}`}>{row.badge.text}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export type BiAlertItem = {
  id: string;
  severity: "critical" | "warning";
  title: string;
  detail: string;
  value: string;
};

/** Horizontal rail of at most 3 alerts derived frontend-side from already-fetched page data. */
export function BiAlertRail({ alerts }: { alerts: BiAlertItem[] }) {
  if (!alerts.length) {
    return (
      <div className="bi-alert-rail">
        <div className="bi-alert bi-alert-ok">
          <ShieldCheck size={18} aria-hidden="true" />
          <div className="bi-alert-main">
            <strong>Aucune alerte</strong>
            <small>Couverture facture, impayés et concentration client restent dans les seuils sur la période.</small>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bi-alert-rail" role="list" aria-label="Alertes de pilotage">
      {alerts.slice(0, 3).map((alert) => (
        <div className={`bi-alert bi-alert-${alert.severity}`} role="listitem" key={alert.id}>
          <AlertTriangle size={18} aria-hidden="true" />
          <div className="bi-alert-main">
            <strong>{alert.title}</strong>
            <small>{alert.detail}</small>
          </div>
          <span className="bi-alert-value">{alert.value}</span>
        </div>
      ))}
    </div>
  );
}

type BiInsightItem = {
  label: string;
  title: string;
  value: string;
  helper?: string;
  accent?: string;
  badge?: { text: string; tone: "critical" | "low" | "ok" };
};

export function BiInsightRail({ items }: { items: BiInsightItem[] }) {
  return (
    <div className="bi-insight-rail">
      {items.map((item) => (
        <div className="bi-insight-row" key={`${item.label}-${item.title}`}>
          <span className="bi-insight-row-accent" style={item.accent ? { background: item.accent } : undefined} aria-hidden="true" />
          <div className="bi-insight-main">
            <span>{item.label}</span>
            <strong>{item.title}</strong>
          </div>
          {item.badge ? (
            <span className={`bi-risk-tag bi-risk-${item.badge.tone}`}>{item.badge.text}</span>
          ) : (
            <span className="bi-insight-value">
              {item.value}
              {item.helper ? <small>{item.helper}</small> : null}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

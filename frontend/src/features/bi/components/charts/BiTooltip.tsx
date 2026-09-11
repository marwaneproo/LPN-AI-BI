import type { ReactNode } from "react";
import type { DeltaTone } from "../../../../utils/formatters";
import { formatCompactMoney } from "../../../../utils/formatters";

export type BiTooltipRow = {
  label: string;
  value: string;
  /** Series dot colour; omit for rows that don't map to a drawn series. */
  color?: string;
  /** Previous-period reading + delta, rendered as a muted second line under the row. */
  previous?: { value: string; delta: { text: string; tone: DeltaTone } };
};

/**
 * Presentational tooltip card shared by every BI chart: title (period or entity),
 * colour-dot series rows with formatted values, optional previous-period delta per
 * row, and one muted context line. Styled by `.bi-tooltip` in theme.css.
 */
export function BiTooltipCard({ title, rows, context }: { title?: string; rows: BiTooltipRow[]; context?: ReactNode }) {
  return (
    <div className="bi-tooltip">
      {title ? <strong className="bi-tooltip-title">{title}</strong> : null}
      {rows.map((row) => (
        <span className="bi-tooltip-series" key={row.label}>
          <span className="bi-tooltip-row">
            {row.color ? <i className="bi-tooltip-dot" style={{ background: row.color }} aria-hidden="true" /> : null}
            <span className="bi-tooltip-name">{row.label}</span>
            <span className="bi-tooltip-value">{row.value}</span>
          </span>
          {row.previous ? (
            <small className="bi-tooltip-compare">
              Période précédente&nbsp;: {row.previous.value}
              <em className="bi-tooltip-delta" data-tone={row.previous.delta.tone}>
                {row.previous.delta.text}
              </em>
            </small>
          ) : null}
        </span>
      ))}
      {context ? <small className="bi-tooltip-context">{context}</small> : null}
    </div>
  );
}

type RechartsTooltipEntry = {
  name?: string;
  value?: number;
  color?: string;
  stroke?: string;
  fill?: string;
  payload?: Record<string, unknown>;
};

/** Resolves the drawn series colour of a Recharts tooltip entry (line/area stroke first, then bar fill). */
function seriesColor(entry: RechartsTooltipEntry) {
  const color = entry.color ?? entry.stroke ?? entry.fill ?? (typeof entry.payload?.fill === "string" ? entry.payload.fill : undefined);
  return color === "none" ? undefined : color;
}

/** Default Recharts adapter: one row per series, title from the datum's fullName (or axis label), context from its meta. */
export function BiTooltip({
  active,
  payload,
  label,
  valueFormatter = formatCompactMoney,
}: {
  active?: boolean;
  payload?: RechartsTooltipEntry[];
  label?: string;
  valueFormatter?: (value: number) => string;
}) {
  if (!active || !payload?.length) return null;
  const source = payload[0]?.payload;
  const fullName = typeof source?.fullName === "string" ? source.fullName : label;
  const meta = typeof source?.meta === "string" && source.meta ? source.meta : undefined;
  const rows: BiTooltipRow[] = payload.map((entry) => ({
    label: entry.name ?? "",
    value: valueFormatter(Number(entry.value ?? 0)),
    color: seriesColor(entry),
  }));
  return <BiTooltipCard title={fullName} rows={rows} context={meta} />;
}

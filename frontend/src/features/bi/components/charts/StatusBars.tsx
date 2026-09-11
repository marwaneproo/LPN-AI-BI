import type { StatusBreakdown } from "../../types/bi.types";
import { formatCompactMoney, formatNumber } from "../../../../utils/formatters";
import { EmptyChartState } from "../../../../components/ui/EmptyChartState";

export function StatusBars({ rows }: { rows: StatusBreakdown[] }) {
  const total = rows.reduce((sum, row) => sum + Number(row.total_value ?? 0), 0);

  if (!rows.length) {
    return <EmptyChartState text="Aucun statut disponible pour cette période." hint="Élargissez la période pour voir la répartition par statut." />;
  }

  return (
    <div className="status-bars">
      {rows.map((row, index) => {
        const percent = total > 0 ? Math.max(3, (Number(row.total_value) / total) * 100) : 0;
        return (
          <div key={`${row.status}-${index}`} className="status-row">
            <div>
              <strong>{row.label}</strong>
              <span>{formatNumber(row.item_count)} lignes · {formatCompactMoney(row.total_value)}</span>
            </div>
            <div className="status-track">
              <span style={{ width: `${percent}%` }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

import type { FormattedAnalysisTrendPoint } from "../../types/bi.types";
import { formatCompactMoney, formatDelta, formatMoneyTick, formatNumber } from "../../../../utils/formatters";
import { EmptyChartState } from "../../../../components/ui/EmptyChartState";
import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { BiTooltipCard, type BiTooltipRow } from "./BiTooltip";

const SERIES = [
  { name: "Ventes commandées", key: "orderedSales", previousKey: "prevOrderedSales", color: "var(--chart-blue)" },
  { name: "CA facturé", key: "invoicedSales", previousKey: "prevInvoicedSales", color: "var(--chart-green)" },
] as const;

/** Shared tooltip for both modes: series rows with dots, previous-period delta when "Comparer" is on, counts as context. */
function TrendTooltip({ active, payload, label }: { active?: boolean; payload?: Array<{ payload?: FormattedAnalysisTrendPoint }>; label?: string }) {
  const point = payload?.[0]?.payload;
  if (!active || !point) return null;

  const rows: BiTooltipRow[] = SERIES.map((series) => {
    const current = point[series.key];
    const previous = point[series.previousKey];
    return {
      label: series.name,
      value: formatCompactMoney(current),
      color: series.color,
      previous: previous === undefined ? undefined : { value: formatCompactMoney(previous), delta: formatDelta(current, previous) },
    };
  });

  return (
    <BiTooltipCard
      title={label ?? point.label}
      rows={rows}
      context={`${formatNumber(point.orderCount)} commandes · ${formatNumber(point.invoiceCount)} factures`}
    />
  );
}

export function SalesAnalysisTrendChart({ data }: { data: FormattedAnalysisTrendPoint[] }) {
  if (!data.length) {
    return <EmptyChartState text="Aucune tendance disponible pour ces filtres." hint="Élargissez la période pour afficher la tendance." />;
  }

  const hasComparison = data.some((point) => point.prevInvoicedSales !== undefined);

  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 18, right: 28, left: 0, bottom: 6 }}>
        <defs>
          <linearGradient id="analysisOrdersFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--chart-blue)" stopOpacity={0.3} />
            <stop offset="100%" stopColor="var(--chart-blue)" stopOpacity={0.02} />
          </linearGradient>
          <linearGradient id="analysisInvoicesFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--chart-green)" stopOpacity={0.26} />
            <stop offset="100%" stopColor="var(--chart-green)" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke="var(--chart-grid)" strokeDasharray="3 7" vertical={false} />
        <XAxis dataKey="label" tickLine={false} axisLine={false} stroke="var(--text-tertiary)" fontSize={12} dy={8} />
        <YAxis tickFormatter={formatMoneyTick} tickLine={false} axisLine={false} stroke="var(--text-tertiary)" fontSize={11} width={52} />
        <Tooltip content={<TrendTooltip />} cursor={{ stroke: "var(--chart-cursor-line)", strokeWidth: 1 }} />
        <Legend
          verticalAlign="top"
          align="right"
          iconType="circle"
          iconSize={8}
          wrapperStyle={{ color: "var(--text-tertiary)", fontSize: 11, paddingBottom: 10, whiteSpace: "nowrap" }}
        />
        <Area type="monotone" name="Ventes commandées" dataKey="orderedSales" stroke="var(--chart-blue)" fill="url(#analysisOrdersFill)" strokeWidth={2.5} activeDot={{ r: 5 }} />
        <Area type="monotone" name="CA facturé" dataKey="invoicedSales" stroke="var(--chart-green)" fill="url(#analysisInvoicesFill)" strokeWidth={2.5} activeDot={{ r: 5 }} />
        {hasComparison ? (
          <Area
            type="monotone"
            name="Période précédente"
            dataKey="prevOrderedSales"
            stroke="var(--chart-compare)"
            strokeDasharray="5 5"
            strokeWidth={1.5}
            strokeOpacity={0.65}
            fill="none"
            dot={false}
            activeDot={{ r: 3 }}
          />
        ) : null}
        {hasComparison ? (
          <Area
            type="monotone"
            name="Période précédente"
            dataKey="prevInvoicedSales"
            legendType="none"
            stroke="var(--chart-compare)"
            strokeDasharray="5 5"
            strokeWidth={2}
            fill="none"
            dot={false}
            activeDot={{ r: 3 }}
          />
        ) : null}
      </AreaChart>
    </ResponsiveContainer>
  );
}

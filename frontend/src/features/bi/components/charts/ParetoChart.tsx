import { Bar, CartesianGrid, ComposedChart, Legend, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { EmptyChartState } from "../../../../components/ui/EmptyChartState";
import { formatCompactMoney, formatMoneyTick, formatPercent } from "../../../../utils/formatters";
import { BiTooltipCard } from "./BiTooltip";

export type ParetoDatum = {
  id: string;
  name: string;
  fullName: string;
  /** CA facturé for this single row. */
  value: number;
  /** This row's own share of the total filtered CA (cumulative[i] - cumulative[i-1]). */
  part: number;
  /** Running share of the total filtered CA held by rows ranked at or above this one. */
  cumulative: number;
};

function ParetoTooltip({
  active,
  payload,
  barName,
  barColor,
  lineColor,
}: {
  active?: boolean;
  payload?: Array<{ payload?: ParetoDatum }>;
  barName: string;
  barColor: string;
  lineColor: string;
}) {
  if (!active || !payload?.length) return null;
  const item = payload[0]?.payload;
  if (!item) return null;
  return (
    <BiTooltipCard
      title={item.fullName}
      rows={[
        { label: barName, value: formatCompactMoney(item.value), color: barColor },
        { label: "Part cumulée", value: formatPercent(item.cumulative), color: lineColor },
      ]}
      context={`Cet élément pèse ${formatPercent(item.part)} du CA total filtré.`}
    />
  );
}

export function ParetoChart({
  data,
  barColor = "var(--chart-blue)",
  lineColor = "var(--chart-amber)",
  barName = "CA facturé",
}: {
  data: ParetoDatum[];
  barColor?: string;
  lineColor?: string;
  barName?: string;
}) {
  if (!data.length) {
    return <EmptyChartState text="Aucune donnée de concentration disponible." hint="Élargissez la période pour recalculer le Pareto." />;
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data} margin={{ top: 8, right: 12, left: 4, bottom: 56 }}>
        <CartesianGrid stroke="var(--chart-grid)" strokeDasharray="3 7" vertical={false} />
        <XAxis
          dataKey="name"
          interval={0}
          tickLine={false}
          axisLine={false}
          stroke="var(--text-tertiary)"
          fontSize={11}
          angle={-35}
          textAnchor="end"
          height={62}
        />
        <YAxis
          yAxisId="value"
          tickFormatter={formatMoneyTick}
          tickLine={false}
          axisLine={false}
          stroke="var(--text-tertiary)"
          fontSize={11}
          width={52}
        />
        <YAxis
          yAxisId="percent"
          orientation="right"
          domain={[0, 100]}
          ticks={[0, 20, 40, 60, 80, 100]}
          tickFormatter={(tick: number) => `${tick}%`}
          tickLine={false}
          axisLine={false}
          stroke="var(--text-tertiary)"
          fontSize={11}
          width={40}
        />
        <Tooltip content={<ParetoTooltip barName={barName} barColor={barColor} lineColor={lineColor} />} cursor={{ fill: "var(--chart-cursor)" }} />
        <Legend
          verticalAlign="top"
          align="right"
          iconType="circle"
          iconSize={8}
          wrapperStyle={{ color: "var(--text-tertiary)", fontSize: 11, paddingBottom: 10, whiteSpace: "nowrap" }}
        />
        <ReferenceLine
          yAxisId="percent"
          y={80}
          stroke="var(--danger)"
          strokeDasharray="4 4"
          strokeWidth={1}
          label={{ value: "80 %", position: "insideTopRight", fill: "var(--danger)", fontSize: 11 }}
        />
        <Bar yAxisId="value" dataKey="value" name={barName} fill={barColor} radius={[4, 4, 0, 0]} barSize={24} />
        <Line
          yAxisId="percent"
          type="monotone"
          dataKey="cumulative"
          name="Part cumulée"
          stroke={lineColor}
          strokeWidth={2}
          dot={{ r: 4, strokeWidth: 2, stroke: "var(--bg-panel)", fill: lineColor }}
          activeDot={{ r: 5 }}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

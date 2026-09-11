import type { RankedBarDatum } from "../../types/bi.types";
import { formatCompactMoney } from "../../../../utils/formatters";
import { EmptyChartState } from "../../../../components/ui/EmptyChartState";
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { BiTooltip } from "./BiTooltip";

export function RankedBarChart({
  data,
  color,
  detail = false,
  selectedId = "",
  onItemClick,
  valueFormatter = formatCompactMoney,
  valueName = "Valeur",
  showXAxis = false,
  showValueLabels = false,
  vertical = false,
}: {
  data: RankedBarDatum[];
  color: string;
  detail?: boolean;
  selectedId?: string;
  onItemClick?: (item: RankedBarDatum) => void;
  valueFormatter?: (value: number) => string;
  valueName?: string;
  showXAxis?: boolean;
  showValueLabels?: boolean;
  vertical?: boolean;
}) {
  if (!data.length) {
    return <EmptyChartState text="Aucune donnée de classement disponible." hint="Élargissez la période pour afficher un classement." />;
  }

  // Axis ticks drop the "KDH" unit (tooltips and data labels keep it); no-op for non-money formatters.
  const axisTickFormatter = (value: number) => valueFormatter(value).replace(/\s*KDH$/, "");

  if (vertical) {
    return (
      <VerticalRankedBarChart
        data={data}
        color={color}
        selectedId={selectedId}
        onItemClick={onItemClick}
        valueFormatter={valueFormatter}
        valueName={valueName}
        showValueLabels={showValueLabels}
      />
    );
  }

  const chartHeight = detail ? Math.max(520, data.length * 48) : Math.max(300, data.length * 38);
  const yAxisWidth = detail ? 210 : 142;
  const isInteractive = Boolean(onItemClick);
  const chartClassName = [
    "ranked-chart",
    detail ? "ranked-chart-detail" : "",
    isInteractive ? "ranked-chart-interactive" : "",
    selectedId ? "ranked-chart-has-selection" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={chartClassName} style={{ height: chartHeight }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: detail || showValueLabels ? 52 : 24, left: 8, bottom: showXAxis ? 18 : 4 }}>
          <CartesianGrid stroke="var(--chart-grid)" strokeDasharray="3 7" horizontal={false} />
          <XAxis
            type="number"
            hide={!showXAxis}
            tickFormatter={axisTickFormatter}
            tickLine={false}
            axisLine={false}
            stroke="var(--text-tertiary)"
            fontSize={11}
          />
          <YAxis type="category" dataKey="name" width={yAxisWidth} tickLine={false} axisLine={false} stroke="var(--text-tertiary)" fontSize={12} />
          <Tooltip content={<BiTooltip valueFormatter={valueFormatter} />} cursor={{ fill: "var(--chart-cursor)" }} />
          <Bar
            dataKey="value"
            name={valueName}
            fill={color}
            radius={[0, 9, 9, 0]}
            barSize={detail ? 18 : 14}
            onClick={(entry) => {
              const item = (entry as { payload?: RankedBarDatum }).payload;
              if (item && onItemClick) {
                onItemClick(item);
              }
            }}
          >
            {data.map((item) => (
              <Cell
                key={item.id ?? item.fullName}
                className={item.selected ? "ranked-bar-selected" : ""}
                fill={item.selected ? "var(--chart-blue)" : color}
                fillOpacity={selectedId ? (item.selected ? 1 : 0.36) : 1}
                cursor={isInteractive ? "pointer" : "default"}
              />
            ))}
            {detail || showValueLabels ? <LabelList dataKey="value" position="right" formatter={(value: number) => valueFormatter(value)} fill="var(--text-secondary)" fontSize={12} /> : null}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function VerticalRankedBarChart({
  data,
  color,
  selectedId = "",
  onItemClick,
  valueFormatter = formatCompactMoney,
  valueName = "Valeur",
  showValueLabels = false,
}: {
  data: RankedBarDatum[];
  color: string;
  selectedId?: string;
  onItemClick?: (item: RankedBarDatum) => void;
  valueFormatter?: (value: number) => string;
  valueName?: string;
  showValueLabels?: boolean;
}) {
  const isInteractive = Boolean(onItemClick);
  // Axis ticks drop the "KDH" unit (tooltips and data labels keep it); no-op for non-money formatters.
  const axisTickFormatter = (value: number) => valueFormatter(value).replace(/\s*KDH$/, "");
  const chartClassName = [
    "ranked-chart",
    "ranked-chart-vertical",
    isInteractive ? "ranked-chart-interactive" : "",
    selectedId ? "ranked-chart-has-selection" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={chartClassName}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 14, right: 12, left: 0, bottom: 56 }}>
          <CartesianGrid stroke="var(--chart-grid)" strokeDasharray="3 7" vertical={false} />
          <XAxis
            type="category"
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
            type="number"
            tickFormatter={axisTickFormatter}
            tickLine={false}
            axisLine={false}
            stroke="var(--text-tertiary)"
            fontSize={11}
            width={46}
          />
          <Tooltip content={<BiTooltip valueFormatter={valueFormatter} />} cursor={{ fill: "var(--chart-cursor)" }} />
          <Bar
            dataKey="value"
            name={valueName}
            fill={color}
            radius={[9, 9, 0, 0]}
            barSize={22}
            onClick={(entry) => {
              const item = (entry as { payload?: RankedBarDatum }).payload;
              if (item && onItemClick) {
                onItemClick(item);
              }
            }}
          >
            {data.map((item) => (
              <Cell
                key={item.id ?? item.fullName}
                className={item.selected ? "ranked-bar-selected" : ""}
                fill={item.selected ? "var(--chart-blue)" : color}
                fillOpacity={selectedId ? (item.selected ? 1 : 0.36) : 1}
                cursor={isInteractive ? "pointer" : "default"}
              />
            ))}
            {showValueLabels ? <LabelList dataKey="value" position="top" formatter={(value: number) => valueFormatter(value)} fill="var(--text-secondary)" fontSize={11} /> : null}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

import { CSSProperties, useState } from "react";
import type { ContributionPieDatum, RankedBarDatum } from "../../types/bi.types";
import { formatCompactMoney, formatPercent, shortenLabel } from "../../../../utils/formatters";
import { EmptyChartState } from "../../../../components/ui/EmptyChartState";
import { Cell, Pie, PieChart, ResponsiveContainer, Sector, Tooltip } from "recharts";
import { BiTooltipCard } from "./BiTooltip";

// Identity slots in fixed assignment order (CVD-validated in theme.css);
// the neutral --pie-9 is reserved for the synthetic « Autres » bucket so a
// residual fold never borrows a categorical hue.
const contributionPiePalette = [
  "var(--pie-1)",
  "var(--pie-2)",
  "var(--pie-3)",
  "var(--pie-4)",
  "var(--pie-5)",
  "var(--pie-6)",
  "var(--pie-7)",
  "var(--pie-8)",
];
const contributionPieOthersColor = "var(--pie-9)";

function renderActiveContributionSlice(props: unknown) {
  const {
    cx,
    cy,
    innerRadius,
    outerRadius,
    startAngle,
    endAngle,
    midAngle,
    fill,
  } = props as {
    cx: number;
    cy: number;
    innerRadius: number;
    outerRadius: number;
    startAngle: number;
    endAngle: number;
    midAngle: number;
    fill: string;
  };
  const offset = 12;
  const angle = (-midAngle * Math.PI) / 180;
  const dx = Math.cos(angle) * offset;
  const dy = Math.sin(angle) * offset;

  const liftStyle = {
    "--pie-lift-x": `${dx}px`,
    "--pie-lift-y": `${dy}px`,
  } as CSSProperties;

  return (
    <g className="contribution-pie-active-slice" style={liftStyle}>
      <Sector
        cx={cx}
        cy={cy}
        innerRadius={innerRadius}
        outerRadius={outerRadius + 5}
        startAngle={startAngle}
        endAngle={endAngle}
        fill={fill}
      />
      <Sector
        cx={cx}
        cy={cy}
        innerRadius={outerRadius + 8}
        outerRadius={outerRadius + 10}
        startAngle={startAngle}
        endAngle={endAngle}
        fill={fill}
        className="contribution-pie-active-ring"
      />
    </g>
  );
}

function ContributionPieTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload?: ContributionPieDatum; value?: number }>;
}) {
  if (!active || !payload?.length) return null;
  const item = payload[0]?.payload;
  if (!item) return null;

  return (
    <BiTooltipCard
      title={item.fullName}
      rows={[
        { label: "CA facturé", value: formatCompactMoney(Number(item.value ?? payload[0]?.value ?? 0)), color: item.color },
        { label: "Part du top", value: formatPercent(item.percent) },
      ]}
      context={item.meta || undefined}
    />
  );
}

/**
 * Percent-only direct label just outside the slice. Identity (name + colour)
 * lives in the side legend, so a full "name + %" spider label would be
 * redundant — and long names overflowed the card into the neighbouring widget.
 */
function renderContributionPieLabel(props: unknown) {
  const { cx, cy, midAngle, outerRadius, percent } = props as {
    cx: number;
    cy: number;
    midAngle: number;
    outerRadius: number;
    percent: number;
  };
  const percentValue = percent > 1 ? percent : percent * 100;
  if (!percentValue || percentValue < 6) {
    return null;
  }
  const radius = outerRadius + 18;
  const angle = (-midAngle * Math.PI) / 180;
  const x = cx + radius * Math.cos(angle);
  const y = cy + radius * Math.sin(angle);
  const anchor = x >= cx ? "start" : "end";

  return (
    <text className="contribution-pie-label" x={x} y={y} textAnchor={anchor} dominantBaseline="central">
      {formatPercent(percentValue)}
    </text>
  );
}

export function ContributionPieChart({
  data,
  emptyText,
}: {
  data: RankedBarDatum[];
  emptyText: string;
}) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const baseData = data.filter((item) => Number(item.value) > 0);
  if (!baseData.length) {
    return <EmptyChartState text={emptyText} hint="Élargissez la période pour retrouver une répartition." />;
  }

  const maxSlices = 8;
  const visible = baseData.slice(0, maxSlices - 1);
  const rest = baseData.slice(maxSlices - 1);
  const chartDataBase: Array<RankedBarDatum & { isOther?: boolean }> =
    rest.length > 1
      ? [
          ...visible,
          {
            name: "Autres",
            fullName: `Autres (${rest.length})`,
            value: rest.reduce((sum, item) => sum + item.value, 0),
            meta: rest.map((item) => item.name).join(" · "),
            isOther: true,
          },
        ]
      : baseData;

  const total = chartDataBase.reduce((sum, item) => sum + item.value, 0);
  const chartData: ContributionPieDatum[] = chartDataBase.map((item, index) => ({
    ...item,
    name: shortenLabel(item.name, 18),
    color: item.isOther ? contributionPieOthersColor : contributionPiePalette[index % contributionPiePalette.length],
    percent: total > 0 ? (item.value / total) * 100 : 0,
  }));
  const leader = chartData[0];

  return (
    <div className="contribution-pie-module">
      <div className="contribution-pie-side">
        <div className="contribution-pie-summary">
          <span>Total top</span>
          <strong>{formatCompactMoney(total)}</strong>
          <small>{leader ? `${leader.fullName} domine avec ${formatPercent(leader.percent)}` : "Classement indisponible"}</small>
        </div>
        <div className="contribution-pie-legend">
          {chartData.map((item) => (
            <div className="contribution-pie-row" key={item.fullName}>
              <span className="contribution-pie-swatch" style={{ background: item.color }} />
              <div>
                <strong>{shortenLabel(item.fullName, 28)}</strong>
                <small>{item.meta}</small>
              </div>
              <b>{formatPercent(item.percent)}</b>
            </div>
          ))}
        </div>
      </div>
      <div className="contribution-pie-chart" aria-label="Répartition circulaire du CA facturé">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
            <Pie
              data={chartData}
              dataKey="value"
              nameKey="fullName"
              cx="50%"
              cy="50%"
              outerRadius="64%"
              innerRadius={0}
              paddingAngle={1}
              stroke="var(--dashboard-surface)"
              strokeWidth={2}
              labelLine={false}
              label={renderContributionPieLabel}
              activeIndex={activeIndex ?? undefined}
              activeShape={renderActiveContributionSlice}
              onMouseEnter={(_entry: unknown, index: number) => setActiveIndex(index)}
              onMouseLeave={() => setActiveIndex(null)}
              isAnimationActive
              animationDuration={650}
            >
              {chartData.map((entry) => (
                <Cell key={entry.fullName} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip content={<ContributionPieTooltip />} cursor={false} />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

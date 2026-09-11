import { useMemo } from "react";
import type { BiRevenueYearlyPoint } from "../../types/bi.types";
import { formatCompactMoney, formatMoneyTick } from "../../../../utils/formatters";

/** One vivid hue per year — satisfies the "different colour per year" brief. */
const YEAR_HUES = [
  "var(--chart-amber)",
  "var(--chart-blue)",
  "var(--chart-violet)",
  "var(--chart-green)",
];

const TICK_INTERVALS = 4;

/** Smooth ceiling (1 / 2 / 2.5 / 5 / 10 × 10ⁿ) so the Y axis tops out cleanly. */
function niceCeil(value: number) {
  if (value <= 0) return 1;
  const pow = 10 ** Math.floor(Math.log10(value));
  const n = value / pow;
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10;
  return step * pow;
}

export function BiYearlyCaChart({ points }: { points: BiRevenueYearlyPoint[] }) {
  const { niceMax, ticks } = useMemo(() => {
    const dataMax = Math.max(...points.flatMap((p) => [p.ca_facture, p.ca_commande]), 1);
    const max = niceCeil(dataMax);
    const tickValues = Array.from({ length: TICK_INTERVALS + 1 }, (_, i) => (max * i) / TICK_INTERVALS);
    return { niceMax: max, ticks: tickValues };
  }, [points]);

  if (!points.length) return null;

  return (
    <div className="bi-yearly" aria-label="CA facturé et commandé par année">
      <div className="bi-yearly-legend">
        <span className="bi-yearly-lg">
          <i className="bi-yearly-sw bi-yearly-sw--solid" aria-hidden="true" />
          CA facturé
        </span>
        <span className="bi-yearly-lg">
          <i className="bi-yearly-sw bi-yearly-sw--ghost" aria-hidden="true" />
          CA commandé
        </span>
      </div>

      <div className="bi-yearly-plot">
        {/* gridlines + Y-axis ticks */}
        {ticks.map((tick, i) => (
          <div className="bi-yearly-gridline" key={tick} style={{ bottom: `${(i / TICK_INTERVALS) * 100}%` }}>
            <span className="bi-yearly-tick">{formatMoneyTick(tick)}</span>
          </div>
        ))}

        {/* grouped bars */}
        <div className="bi-yearly-bars">
          {points.map((point, idx) => {
            const hue = YEAR_HUES[idx % YEAR_HUES.length];
            const facturePct = Math.min(1, Math.max(0, point.ca_facture / niceMax));
            const commandePct = Math.min(1, Math.max(0, point.ca_commande / niceMax));
            return (
              <div className="bi-yearly-group" key={point.year}>
                <div className="bi-yearly-cols">
                  {/* CA facturé — solid per-year hue */}
                  <div className="bi-yearly-col">
                    <span className="bi-yearly-spacer" style={{ flexGrow: Math.max(0.0001, 1 - facturePct) }} />
                    <span
                      className="bi-yearly-bar bi-yearly-bar--solid"
                      style={{
                        flexGrow: Math.max(0.05, facturePct),
                        background: `linear-gradient(180deg, ${hue}, color-mix(in oklab, ${hue} 68%, black))`,
                      }}
                    >
                      <span className="bi-yearly-val">{formatCompactMoney(point.ca_facture)}</span>
                    </span>
                  </div>
                  {/* CA commandé — light ghost of the same hue */}
                  <div className="bi-yearly-col">
                    <span className="bi-yearly-spacer" style={{ flexGrow: Math.max(0.0001, 1 - commandePct) }} />
                    <span
                      className="bi-yearly-bar bi-yearly-bar--ghost"
                      style={{
                        flexGrow: Math.max(0.05, commandePct),
                        background: `color-mix(in oklab, ${hue} 22%, transparent)`,
                        borderColor: `color-mix(in oklab, ${hue} 52%, transparent)`,
                      }}
                    >
                      <span className="bi-yearly-val bi-yearly-val--ghost">{formatCompactMoney(point.ca_commande)}</span>
                    </span>
                  </div>
                </div>
                <small className="bi-yearly-xlabel">
                  {point.year}
                  {point.partial ? <em> · en cours</em> : null}
                </small>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

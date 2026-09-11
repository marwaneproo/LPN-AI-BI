import { useMemo } from "react";
import type { BiOrderFunnel } from "../../types/bi.types";
import { formatNumber, formatPercent } from "../../../../utils/formatters";

/**
 * Ordered → delivered → invoiced conversion funnel. Three horizontal steps whose
 * bar width is proportional to the quantity (commandé = 100 % reference); each step
 * shows its absolute quantity and its share of the PREVIOUS step, and the drop
 * between two steps is annotated ("−12 % non livré"). Custom markup in the spirit
 * of BiYearlyCaChart — no chart library, both themes via theme.css chart vars.
 */

type FunnelStep = {
  key: string;
  label: string;
  quantity: number;
  /** Bar width, proportional to the ordered quantity (0–100). */
  widthPct: number;
  /** Share of the previous step, already formatted (null on the first step). */
  shareLabel: string | null;
  tone: string;
};

type FunnelDrop = {
  key: string;
  /** How much of the previous step was lost, formatted (e.g. "−8,9 %"). */
  lostLabel: string;
  reason: string;
};

function share(part: number, whole: number) {
  return whole > 0 ? (part / whole) * 100 : 0;
}

export function BiConversionFunnel({ funnel }: { funnel: BiOrderFunnel }) {
  const { steps, drops } = useMemo(() => {
    const ordered = Number(funnel.quantity_ordered ?? 0);
    const delivered = Number(funnel.quantity_delivered ?? 0);
    const invoiced = Number(funnel.quantity_invoiced ?? 0);

    const deliveredOfOrdered = share(delivered, ordered);
    const invoicedOfDelivered = share(invoiced, delivered);

    const stepList: FunnelStep[] = [
      {
        key: "ordered",
        label: "Commandé",
        quantity: ordered,
        widthPct: 100,
        shareLabel: null,
        tone: "var(--chart-blue)",
      },
      {
        key: "delivered",
        label: "Livré",
        quantity: delivered,
        widthPct: share(delivered, ordered),
        shareLabel: `${formatPercent(deliveredOfOrdered)} du commandé`,
        tone: "var(--chart-amber)",
      },
      {
        key: "invoiced",
        label: "Facturé",
        quantity: invoiced,
        widthPct: share(invoiced, ordered),
        shareLabel: `${formatPercent(invoicedOfDelivered)} du livré`,
        tone: "var(--chart-green)",
      },
    ];

    const dropList: FunnelDrop[] = [
      { key: "non-livre", lostLabel: `−${formatPercent(100 - deliveredOfOrdered)}`, reason: "non livré" },
      { key: "non-facture", lostLabel: `−${formatPercent(100 - invoicedOfDelivered)}`, reason: "non facturé" },
    ];

    return { steps: stepList, drops: dropList };
  }, [funnel]);

  return (
    <div className="bi-funnel" aria-label="Entonnoir de conversion des commandes">
      {steps.map((step, index) => (
        <div className="bi-funnel-step" key={step.key}>
          <div className="bi-funnel-line">
            <span className="bi-funnel-dot" style={{ background: step.tone }} aria-hidden="true" />
            <span className="bi-funnel-label">{step.label}</span>
            <strong className="bi-funnel-qty">{formatNumber(step.quantity)}</strong>
            {step.shareLabel ? <span className="bi-funnel-share">{step.shareLabel}</span> : null}
          </div>
          <div className="bi-funnel-track">
            <span
              className="bi-funnel-bar"
              style={{
                width: `${Math.max(2, Math.min(100, step.widthPct))}%`,
                background: `linear-gradient(90deg, ${step.tone}, color-mix(in oklab, ${step.tone} 72%, black))`,
              }}
            />
          </div>
          {index < drops.length ? (
            <div className="bi-funnel-drop">
              <span className="bi-funnel-drop-arrow" aria-hidden="true">↓</span>
              <span className="bi-funnel-drop-loss">{drops[index].lostLabel}</span>
              <span className="bi-funnel-drop-reason">{drops[index].reason}</span>
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}

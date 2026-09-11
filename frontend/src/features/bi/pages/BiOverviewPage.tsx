import { AlertTriangle } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import { BiAlertRail, BiComparisonCard, BiMetricCard, BiWidgetCard } from "../components/widgets/BiWidgets";
import { SalesAnalysisTrendChart } from "../components/charts/SalesAnalysisTrendChart";
import { useBiOverview } from "../hooks/useBiOverview";
import { useBiRange } from "../hooks/useBiRange";
import type { BiDetailColumn } from "../types/bi.types";
import { biCsvFilename } from "../utils/biCsv";

const CONTRIBUTOR_TONES = ["blue", "green", "pink"] as const;

const ACTIVITY_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "label", label: "Période" },
  { key: "orderCount", label: "Commandes", format: "number" },
  { key: "orderedSales", label: "CA commandé", format: "moneyCompact" },
  { key: "invoiceCount", label: "Factures", format: "number" },
  { key: "invoicedSales", label: "CA facturé", format: "moneyCompact" },
];

export function BiOverviewPage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const { snapshot, isLoading, error, refreshOverview, kpis, trendPoints, quickSignals, alerts, comparisonCards } = useBiOverview(filters);

  return (
    <BiShell
      title="Vue d'ensemble"
      subtitle="Lecture exécutive du processus de vente."
      eyebrow="Pilotage vente"
      description="Une entrée courte pour lire le CA, l'activité, les clients et les alertes avant de plonger dans les vues détaillées."
      range={range}
      onApplyRange={applyRange}
      compareEnabled={compareEnabled}
      onToggleCompare={toggleCompare}
      lastUpdatedAt={snapshot?.meta.generated_at}
    >
      {error ? (
        <Card className="dashboard-error">
          <AlertTriangle size={20} />
          <div>
            <strong>Impossible de charger la vue d'ensemble BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshOverview()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement de la vue d'ensemble">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs année sur année">
            {comparisonCards.map((card) => (
              <BiComparisonCard
                key={card.key}
                label={card.label}
                value={card.value}
                helper={card.helper}
                tone={card.tone}
                currentYear={card.currentYear}
                previousYear={card.previousYear}
                currentValue={card.currentValue}
                previousValue={card.previousValue}
                increaseIsGood={(card as { increaseIsGood?: boolean }).increaseIsGood}
              />
            ))}
          </section>

          <section className="bi-analytics-grid">
            <BiWidgetCard
              className="bi-hero"
              title="Activité commandée vs facturée"
              subtitle={`CA commandé et CA facturé sur la période sélectionnée · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "Activité commandée vs facturée",
                columns: ACTIVITY_DETAIL_COLUMNS,
                rows: trendPoints,
                filename: biCsvFilename("overview", "activite", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-hero">
                {trendPoints.length ? (
                  <SalesAnalysisTrendChart data={trendPoints} />
                ) : (
                  <EmptyChartState text="Aucune tendance disponible sur cette période." hint="Élargissez la période pour afficher l'activité." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard className="bi-span-2" title="Alertes" subtitle="Trois signaux au maximum, calculés sur la période sélectionnée.">
              <BiAlertRail alerts={alerts} />
            </BiWidgetCard>

            <div className="bi-contrib-grid" aria-label="Contributeurs de la période">
              {quickSignals.map((signal, index) => (
                <BiMetricCard
                  key={signal.label}
                  label={signal.label}
                  value={signal.value}
                  helper={signal.helper}
                  tone={CONTRIBUTOR_TONES[index % CONTRIBUTOR_TONES.length]}
                />
              ))}
            </div>
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

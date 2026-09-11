import { AlertTriangle } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import { BiComparisonCard, BiInsightRail, BiWidgetCard } from "../components/widgets/BiWidgets";
import { RankedBarChart } from "../components/charts/RankedBarChart";
import { ParetoChart } from "../components/charts/ParetoChart";
import { useBiClients } from "../hooks/useBiClients";
import { useBiRange } from "../hooks/useBiRange";
import type { BiDetailColumn } from "../types/bi.types";
import { biCsvFilename } from "../utils/biCsv";

const TOP_CLIENTS_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "customer_name", label: "Client" },
  { key: "invoice_count", label: "Factures", format: "number" },
  { key: "invoiced_sales", label: "CA facturé", format: "moneyCompact" },
  { key: "average_invoice_value", label: "Panier moyen", format: "moneyCompact" },
];

const CLIENT_PARETO_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "customer_name", label: "Client" },
  { key: "invoiced_sales", label: "CA facturé", format: "moneyCompact" },
  { key: "cumulative_share_percent", label: "Part cumulée", format: "percent" },
];

export function BiClientsPage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const {
    snapshot,
    isLoading,
    error,
    refreshClients,
    kpis,
    topClientBars,
    clientParetoBars,
    top10ShareLabel,
    regionBars,
    financeInsights,
    comparisonCards,
  } = useBiClients(filters);

  return (
    <BiShell
      title="Client"
      subtitle="Analyse client par commercial, article, région et contribution CA."
      eyebrow="Portefeuille client"
      description="Cette page prépare une lecture client claire: meilleurs clients, clients par commercial, articles achetés, zones géographiques et contribution au CA réel."
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
            <strong>Impossible de charger les clients BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshClients()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement des clients">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs clients année sur année">
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
              title="Top clients"
              subtitle={`Classement par CA facturé · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "Top clients",
                columns: TOP_CLIENTS_DETAIL_COLUMNS,
                rows: snapshot.data.top_clients,
                filename: biCsvFilename("clients", "top-clients", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-auto">
                {topClientBars.length ? (
                  <RankedBarChart data={topClientBars} color="var(--chart-blue)" valueName="CA facturé" detail />
                ) : (
                  <EmptyChartState text="Aucun client facturé sur cette période." hint="Élargissez la période pour afficher le classement des clients." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Concentration du CA"
              subtitle="Pareto des plus gros clients — repère à 80% du CA."
              action={
                <div className="bi-headline-stat">
                  <strong>{top10ShareLabel}</strong>
                  <small>Top 10 clients</small>
                </div>
              }
              detail={{
                title: "Concentration du CA — clients",
                columns: CLIENT_PARETO_DETAIL_COLUMNS,
                rows: snapshot.data.top_clients,
                filename: biCsvFilename("clients", "concentration", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-lg">
                {clientParetoBars.length ? (
                  <ParetoChart data={clientParetoBars} barColor="var(--chart-blue)" lineColor="var(--chart-amber)" barName="CA facturé" />
                ) : (
                  <EmptyChartState text="Aucune donnée de concentration sur cette période." hint="Élargissez la période pour recalculer le Pareto." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard title="Top zones géographiques" subtitle="Villes et régions qui portent le CA.">
              <div className="bi-chart-body">
                {regionBars.length ? (
                  <RankedBarChart data={regionBars} color="var(--chart-green)" valueName="CA facturé" />
                ) : (
                  <EmptyChartState text="Aucune zone géographique sur cette période." hint="Élargissez la période pour afficher les régions porteuses." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard title="Risque impayés" subtitle="Comptes à relancer en priorité.">
              {financeInsights.length ? (
                <BiInsightRail items={financeInsights} />
              ) : (
                <EmptyChartState text="Aucun risque impayé détecté." hint="Bonne nouvelle — aucun compte à relancer sur cette fenêtre." />
              )}
            </BiWidgetCard>
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

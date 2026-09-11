import { useMemo, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import type { BiFilterChip } from "../components/controls/BiFilterChips";
import { BiComparisonCard, BiInsightRail, BiMetricCard, BiUnpaidTable, BiWidgetCard } from "../components/widgets/BiWidgets";
import { BiYearlyCaChart } from "../components/widgets/BiYearlyCaChart";
import { SalesAnalysisTrendChart } from "../components/charts/SalesAnalysisTrendChart";
import { RankedBarChart } from "../components/charts/RankedBarChart";
import { useBiRevenue } from "../hooks/useBiRevenue";
import { useBiRange } from "../hooks/useBiRange";
import { formatCompactMoney } from "../../../utils/formatters";
import type { BiDetailColumn } from "../types/bi.types";
import { biCsvFilename } from "../utils/biCsv";

const CA_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "label", label: "Période" },
  { key: "orderCount", label: "Commandes", format: "number" },
  { key: "orderedSales", label: "CA commandé", format: "moneyCompact" },
  { key: "invoiceCount", label: "Factures", format: "number" },
  { key: "invoicedSales", label: "CA facturé", format: "moneyCompact" },
];

const UNPAID_CUSTOMER_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "customer_name", label: "Client" },
  { key: "amount", label: "Montant impayé", format: "money" },
  { key: "unpaid_invoice_count", label: "Factures", format: "number" },
];

const UNPAID_COMMERCIAL_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "commercial_name", label: "Commercial" },
  { key: "amount", label: "Montant impayé", format: "money" },
  { key: "unpaid_invoice_count", label: "Factures", format: "number" },
];

type CommercialSelection = { id: number; label: string };

export function BiRevenuePage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const [showYearlyDetail, setShowYearlyDetail] = useState(false);
  const [selectedCommercial, setSelectedCommercial] = useState<CommercialSelection | null>(null);

  const revenueFilters = useMemo(
    () => ({ ...filters, commercial: selectedCommercial?.id }),
    [filters, selectedCommercial],
  );

  // Click a bar in « Risque impayés — par commercial » = filter every widget of
  // this page on that commercial; re-click (or the chip's ✕) = clear.
  const toggleCommercial = (candidate: CommercialSelection) => {
    setSelectedCommercial((current) => (current?.id === candidate.id ? null : candidate));
  };

  const commercialChips: BiFilterChip[] = selectedCommercial
    ? [
        {
          key: "commercial",
          label: `Commercial : ${selectedCommercial.label}`,
          ariaLabel: `Retirer le filtre commercial ${selectedCommercial.label}`,
          onRemove: () => setSelectedCommercial(null),
        },
      ]
    : [];

  const {
    snapshot,
    isLoading,
    error,
    refreshRevenue,
    kpis,
    yearlyPoints,
    trendPoints,
    revenueStatusInsights,
    comparisonCards,
    unpaidAmountLabel,
    unpaidRateLabel,
    unpaidCustomerRows,
    unpaidCommercialBars,
  } = useBiRevenue(revenueFilters);

  return (
    <BiShell
      title="Chiffre d'affaires"
      subtitle="Comparer CA réel, commandes et écarts de facturation."
      eyebrow="CA & conversion"
      description="Cette section isolera la lecture financière: CA facturé, CA commandé, couverture et différence par période."
      range={range}
      onApplyRange={applyRange}
      compareEnabled={compareEnabled}
      onToggleCompare={toggleCompare}
      lastUpdatedAt={snapshot?.meta.generated_at}
      activeFilterChips={commercialChips}
    >
      {error ? (
        <Card className="dashboard-error">
          <AlertTriangle size={20} />
          <div>
            <strong>Impossible de charger le chiffre d'affaires BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshRevenue()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement du chiffre d'affaires">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs chiffre d'affaires année sur année">
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
              title="CA sur période"
              subtitle={`CA facturé et CA commandé sur la période sélectionnée · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "CA sur période",
                columns: CA_DETAIL_COLUMNS,
                rows: trendPoints,
                filename: biCsvFilename("chiffre-affaires", "activite", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-hero">
                {trendPoints.length ? (
                  <SalesAnalysisTrendChart data={trendPoints} />
                ) : (
                  <EmptyChartState text="Aucune tendance CA sur cette période." hint="Élargissez la période pour afficher le chiffre d'affaires." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard className="bi-span-2" title="Statuts CA" subtitle="Lecture financière avant forage : facturé, à livrer, écart et impayés.">
              <BiInsightRail items={revenueStatusInsights} />
            </BiWidgetCard>

            <BiMetricCard label="Montant impayé" value={unpaidAmountLabel} helper="Cumul des factures non soldées sur la période" tone="red" />
            <BiMetricCard label="Taux d'impayés" value={unpaidRateLabel} helper="Impayé / CA facturé sur la période" tone="amber" />

            <BiWidgetCard
              className="bi-span-2"
              title="Risque impayés — top clients"
              subtitle="Comptes qui concentrent le plus d'impayés sur la période."
              detail={{
                title: "Risque impayés — top clients",
                columns: UNPAID_CUSTOMER_DETAIL_COLUMNS,
                rows: snapshot.data.unpaid.top_customers,
                filename: biCsvFilename("chiffre-affaires", "impayes-clients", range.from, range.to),
              }}
            >
              {unpaidCustomerRows.length ? (
                <BiUnpaidTable rows={unpaidCustomerRows} />
              ) : (
                <EmptyChartState text="Aucun impayé sur la période." hint="Bonne nouvelle — aucun compte à relancer sur cette fenêtre." />
              )}
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Risque impayés — par commercial"
              subtitle={
                selectedCommercial
                  ? `Page filtrée sur ${selectedCommercial.label} — cliquez à nouveau sur sa barre pour retirer le filtre.`
                  : "Exposition impayés par portefeuille commercial — cliquez sur une barre pour filtrer toute la page."
              }
              detail={{
                title: "Risque impayés — par commercial",
                columns: UNPAID_COMMERCIAL_DETAIL_COLUMNS,
                rows: snapshot.data.unpaid.top_commercials,
                filename: biCsvFilename("chiffre-affaires", "impayes-commerciaux", range.from, range.to),
              }}
            >
              <div className="bi-chart-body">
                {unpaidCommercialBars.length ? (
                  <RankedBarChart
                    data={unpaidCommercialBars}
                    color="var(--chart-amber)"
                    valueName="Montant impayé"
                    showValueLabels
                    selectedId={selectedCommercial ? String(selectedCommercial.id) : ""}
                    onItemClick={(item) => {
                      const salesrepId = Number(item.id);
                      if (Number.isFinite(salesrepId) && salesrepId > 0) {
                        toggleCommercial({ id: salesrepId, label: item.fullName });
                      }
                    }}
                  />
                ) : (
                  <EmptyChartState text="Aucun impayé par commercial sur la période." hint="Bonne nouvelle — aucun portefeuille exposé sur cette fenêtre." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="CA facturé vs commandé par année"
              subtitle="À périmètre comparable : chaque année cumule du 1ᵉʳ janvier au dernier jour de données de l'année en cours."
              action={
                <button
                  type="button"
                  className="bi-collapse-toggle"
                  onClick={() => setShowYearlyDetail((open) => !open)}
                  aria-expanded={showYearlyDetail}
                >
                  {showYearlyDetail ? "Réduire" : "Afficher le graphique"}
                </button>
              }
            >
              {!yearlyPoints.length ? (
                <EmptyChartState text="Aucune donnée annuelle disponible." hint="Le repère pluriannuel se remplit dès qu'une année complète est chargée dans l'entrepôt." />
              ) : showYearlyDetail ? (
                <div className="bi-chart-body bi-chart-body-lg">
                  <BiYearlyCaChart points={yearlyPoints} />
                </div>
              ) : (
                <div className="bi-yearly-summary" aria-label="Résumé annuel du chiffre d'affaires">
                  {yearlyPoints.map((point) => (
                    <div className="bi-yearly-summary-chip" key={point.year}>
                      <small>
                        {point.year}
                        {point.partial ? " · en cours" : ""}
                      </small>
                      <strong>{formatCompactMoney(point.ca_facture)}</strong>
                      <small>commandé {formatCompactMoney(point.ca_commande)}</small>
                    </div>
                  ))}
                </div>
              )}
            </BiWidgetCard>
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

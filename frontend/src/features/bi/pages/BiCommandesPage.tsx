import { useMemo, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import type { BiFilterChip } from "../components/controls/BiFilterChips";
import { BiCommercialFlowList, BiComparisonCard, BiInsightRail, BiWidgetCard } from "../components/widgets/BiWidgets";
import { BiConversionFunnel } from "../components/widgets/BiConversionFunnel";
import { RankedBarChart } from "../components/charts/RankedBarChart";
import { useBiOrders } from "../hooks/useBiOrders";
import { useBiRange } from "../hooks/useBiRange";
import type { BiDetailColumn } from "../types/bi.types";
import { biCsvFilename } from "../utils/biCsv";

type CommercialSelection = { id: number; label: string };

const TYPE_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "order_type_name", label: "Type de commande" },
  { key: "order_count", label: "Commandes", format: "number" },
  { key: "ordered_sales", label: "CA commandé", format: "moneyCompact" },
];

const COMMERCIAL_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "commercial_label", label: "Commercial" },
  { key: "order_count", label: "Commandes", format: "number" },
  { key: "ordered_sales", label: "CA commandé", format: "moneyCompact" },
  { key: "delivered_coverage_percent", label: "Livré", format: "percent" },
  { key: "invoice_coverage_percent", label: "Facturé", format: "percent" },
];

export function BiCommandesPage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const [selectedCommercial, setSelectedCommercial] = useState<CommercialSelection | null>(null);

  const ordersFilters = useMemo(
    () => ({ ...filters, commercial: selectedCommercial?.id }),
    [filters, selectedCommercial],
  );
  const { snapshot, isLoading, error, refreshOrders, kpis, typeBars, commercialReadings, statusInsights, comparisonCards, funnel, hasFunnel } =
    useBiOrders(ordersFilters);

  // Click a commercial row = filter every widget of this page on him; re-click (or the chip's ✕) = clear.
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

  return (
    <BiShell
      title="Type de commandes"
      subtitle="Lecture des commandes par type, commercial, statut et transformation."
      eyebrow="Commandes"
      description="Cette page prépare l'analyse des commandes: volumes créés, types de documents, commerciaux porteurs et passage vers livraison puis facturation."
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
            <strong>Impossible de charger les commandes BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshOrders()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement des commandes">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs commandes année sur année">
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
              title="Commandes par type"
              subtitle={`CA commandé par type de document · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "Commandes par type",
                columns: TYPE_DETAIL_COLUMNS,
                rows: snapshot.data.by_type,
                filename: biCsvFilename("commandes", "types", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-hero">
                {typeBars.length ? (
                  <RankedBarChart data={typeBars} color="var(--chart-blue)" valueName="CA commandé" showValueLabels vertical />
                ) : (
                  <EmptyChartState text="Aucun type de commande sur cette période." hint="Élargissez la période pour afficher les types de documents." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Transformation des commandes"
              subtitle="Entonnoir des quantités commandées → livrées → facturées sur la période sélectionnée."
            >
              <div className="bi-chart-body bi-chart-body-lg">
                {hasFunnel && funnel ? (
                  <BiConversionFunnel funnel={funnel} />
                ) : (
                  <EmptyChartState text="Aucune quantité commandée sur la période." hint="Élargissez la période pour reconstituer l'entonnoir commandé → livré → facturé." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              title="Lecture commerciale"
              subtitle={
                selectedCommercial
                  ? `Page filtrée sur ${selectedCommercial.label} — cliquez à nouveau pour retirer le filtre.`
                  : "CA commandé par commercial — cliquez sur un commercial pour filtrer toute la page."
              }
              detail={{
                title: "Lecture commerciale",
                columns: COMMERCIAL_DETAIL_COLUMNS,
                rows: snapshot.data.commercial_flow,
                filename: biCsvFilename("commandes", "commerciaux", range.from, range.to),
              }}
            >
              <div className="bi-chart-body">
                {commercialReadings.length ? (
                  <BiCommercialFlowList
                    items={commercialReadings}
                    selectedId={selectedCommercial?.id ?? null}
                    onItemClick={(item) => {
                      if (item.salesrepId) toggleCommercial({ id: item.salesrepId, label: item.label });
                    }}
                  />
                ) : (
                  <EmptyChartState text="Aucun commercial actif sur cette période." hint="Élargissez la période pour afficher la lecture commerciale." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard title="Points de contrôle" subtitle="Répartition des commandes par statut.">
              {statusInsights.length ? (
                <BiInsightRail items={statusInsights} />
              ) : (
                <EmptyChartState text="Aucun statut de commande sur cette période." hint="Élargissez la période pour voir les points de contrôle." />
              )}
            </BiWidgetCard>
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

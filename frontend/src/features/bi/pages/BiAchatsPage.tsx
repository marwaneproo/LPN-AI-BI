import { AlertTriangle, Coins, Package, Truck, Wallet } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import { BiKpiCard } from "../components/kpi/BiKpiCard";
import { BiWidgetCard } from "../components/widgets/BiWidgets";
import { RankedBarChart } from "../components/charts/RankedBarChart";
import { ParetoChart } from "../components/charts/ParetoChart";
import { ContributionPieChart } from "../components/charts/ContributionPieChart";
import { useBiAchats } from "../hooks/useBiAchats";
import { useBiRange } from "../hooks/useBiRange";
import type { BiDetailColumn } from "../types/bi.types";
import { formatCompactMoney, formatNumber } from "../../../utils/formatters";
import { biCsvFilename } from "../utils/biCsv";

const SUPPLIER_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "supplier_name", label: "Fournisseur" },
  { key: "product_count", label: "Produits", format: "number" },
  { key: "total_attributed_sales", label: "CA attribué", format: "moneyCompact" },
  { key: "total_quantity", label: "Quantité vendue", format: "number" },
];

const SUPPLIER_PRODUCT_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "supplier_name", label: "Fournisseur" },
  { key: "product_name", label: "Produit" },
  { key: "total_attributed_sales", label: "CA attribué", format: "moneyCompact" },
  { key: "total_quantity", label: "Quantité vendue", format: "number" },
];

const CATEGORY_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "category_name", label: "Catégorie" },
  { key: "total_attributed_sales", label: "CA attribué", format: "moneyCompact" },
  { key: "supplier_count", label: "Fournisseurs", format: "number" },
  { key: "total_quantity", label: "Quantité vendue", format: "number" },
];

export function BiAchatsPage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const {
    snapshot,
    isLoading,
    error,
    refreshAchats,
    kpis,
    catalog,
    topSupplierBars,
    supplierParetoBars,
    topSupplierProductBars,
    categoryBars,
    categoryQuantityBars,
    trendBars,
  } = useBiAchats(filters);

  return (
    <BiShell
      title="Achats"
      subtitle="Lecture fournisseurs à partir des ventes rattachées à leur fournisseur d'origine et du catalogue fournisseur."
      eyebrow="Fournisseurs"
      description="Cette page n'affiche que des données réelles de la base."
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
            <strong>Impossible de charger les achats BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshAchats()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement des achats">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs achats">
            <BiKpiCard
              label="Fournisseurs actifs"
              value={formatNumber(kpis.supplier_count)}
              helper="Fournisseurs d'origine des ventes sur la période"
              icon={<Truck size={18} />}
            />
            <BiKpiCard
              label="Produits sourcés"
              value={formatNumber(kpis.product_count)}
              helper="Produits vendus avec fournisseur d'origine connu"
              icon={<Package size={18} />}
            />
            <BiKpiCard
              label="CA attribué aux fournisseurs"
              value={formatCompactMoney(kpis.total_attributed_sales)}
              helper="CA facturé des produits rattachés à un fournisseur"
              icon={<Wallet size={18} />}
              strong
            />
            <BiKpiCard
              label="CA moyen par fournisseur"
              value={formatCompactMoney(kpis.average_sales_per_supplier)}
              helper="CA attribué / fournisseurs actifs"
              icon={<Coins size={18} />}
            />
          </section>

          <section className="bi-analytics-grid">
            <BiWidgetCard
              className="bi-hero"
              title="Top fournisseurs"
              subtitle={`Classement par CA attribué · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "Top fournisseurs",
                columns: SUPPLIER_DETAIL_COLUMNS,
                rows: snapshot.data.top_suppliers,
                filename: biCsvFilename("achats", "top-fournisseurs", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-auto">
                {topSupplierBars.length ? (
                  <RankedBarChart data={topSupplierBars} color="var(--chart-blue)" valueName="CA attribué" detail />
                ) : (
                  <EmptyChartState text="Aucun fournisseur identifié sur cette période." hint="Élargissez la période pour afficher les fournisseurs." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Concentration fournisseurs"
              subtitle="Pareto des fournisseurs — repère à 80% du CA attribué."
              detail={{
                title: "Concentration fournisseurs",
                columns: SUPPLIER_DETAIL_COLUMNS,
                rows: snapshot.data.top_suppliers,
                filename: biCsvFilename("achats", "concentration", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-lg">
                {supplierParetoBars.length ? (
                  <ParetoChart data={supplierParetoBars} barColor="var(--chart-blue)" lineColor="var(--chart-amber)" barName="CA attribué" />
                ) : (
                  <EmptyChartState text="Aucune donnée de concentration sur cette période." hint="Élargissez la période pour recalculer le Pareto." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              title="Évolution mensuelle"
              subtitle="CA attribué aux fournisseurs sur la période sélectionnée."
              detail={{
                title: "Évolution mensuelle des achats",
                columns: [
                  { key: "name", label: "Période" },
                  { key: "value", label: "CA attribué", format: "moneyCompact" },
                ],
                rows: trendBars,
                filename: biCsvFilename("achats", "evolution", range.from, range.to),
              }}
            >
              <div className="bi-chart-body">
                {trendBars.length ? (
                  <RankedBarChart data={trendBars} color="var(--chart-green)" valueName="CA attribué" showXAxis vertical />
                ) : (
                  <EmptyChartState text="Aucune évolution disponible sur cette période." hint="Élargissez la période pour afficher l'évolution." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard title="Top produits par fournisseur" subtitle="Produits vendus classés par CA, avec leur fournisseur d'origine.">
              <div className="bi-chart-body">
                {topSupplierProductBars.length ? (
                  <RankedBarChart data={topSupplierProductBars} color="var(--chart-violet)" valueName="CA attribué" />
                ) : (
                  <EmptyChartState text="Aucun produit sur cette période." hint="Élargissez la période pour afficher le classement." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              title="Répartition par catégorie"
              subtitle="CA attribué aux fournisseurs, par catégorie de produit."
              detail={{
                title: "Répartition par catégorie",
                columns: CATEGORY_DETAIL_COLUMNS,
                rows: snapshot.data.by_category,
                filename: biCsvFilename("achats", "categories", range.from, range.to),
              }}
            >
              <ContributionPieChart data={categoryBars} emptyText="Aucune catégorie sur cette période." />
            </BiWidgetCard>

            <BiWidgetCard
              title="Quantité vendue par catégorie"
              subtitle="Volume vendu (unités facturées) par catégorie de produit, sur la période sélectionnée."
              detail={{
                title: "Quantité vendue par catégorie",
                columns: CATEGORY_DETAIL_COLUMNS,
                rows: snapshot.data.by_category,
                filename: biCsvFilename("achats", "categories-quantite", range.from, range.to),
              }}
            >
              <div className="bi-chart-body">
                {categoryQuantityBars.length ? (
                  <RankedBarChart data={categoryQuantityBars} color="var(--chart-amber)" valueName="Quantité vendue" />
                ) : (
                  <EmptyChartState text="Aucune quantité disponible sur cette période." hint="Élargissez la période pour afficher le volume par catégorie." />
                )}
              </div>
            </BiWidgetCard>

            {catalog ? (
              <BiWidgetCard
                className="bi-span-2"
                title="Catalogue fournisseurs"
                subtitle="Photographie du catalogue produit/fournisseur (indépendante de la période sélectionnée)."
              >
                <section className="bi-compare-grid" aria-label="Indicateurs catalogue fournisseurs">
                  <BiKpiCard
                    label="Entrées catalogue"
                    value={formatNumber(catalog.catalog_entries)}
                    helper="Lignes produit / fournisseur"
                    icon={<Package size={18} />}
                  />
                  <BiKpiCard
                    label="Fournisseurs catalogués"
                    value={formatNumber(catalog.vendor_count)}
                    helper="Fournisseurs référencés au catalogue"
                    icon={<Truck size={18} />}
                  />
                  <BiKpiCard
                    label="Prix catalogue moyen"
                    value={formatCompactMoney(catalog.avg_catalog_price)}
                    helper="Moyenne des prix catalogue renseignés"
                    icon={<Wallet size={18} />}
                  />
                  <BiKpiCard
                    label="Dernier prix d'achat moyen"
                    value={formatCompactMoney(catalog.avg_last_purchase_price)}
                    helper="Moyenne des derniers prix d'achat connus"
                    icon={<Coins size={18} />}
                  />
                </section>
              </BiWidgetCard>
            ) : null}
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

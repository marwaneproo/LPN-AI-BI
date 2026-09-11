import { AlertTriangle } from "lucide-react";
import { Card } from "../../../components/ui/Card";
import { EmptyChartState } from "../../../components/ui/EmptyChartState";
import { BiShell } from "../components/layout/BiShell";
import { BiComparisonCard, BiStockPriorityTable, BiWidgetCard } from "../components/widgets/BiWidgets";
import { RankedBarChart } from "../components/charts/RankedBarChart";
import { ContributionPieChart } from "../components/charts/ContributionPieChart";
import { ParetoChart } from "../components/charts/ParetoChart";
import { useBiArticles } from "../hooks/useBiArticles";
import { useBiRange } from "../hooks/useBiRange";
import type { BiDetailColumn } from "../types/bi.types";
import { biCsvFilename } from "../utils/biCsv";

const TOP_ARTICLES_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "product_name", label: "Article" },
  { key: "category_name", label: "Catégorie" },
  { key: "quantity", label: "Quantité", format: "number" },
  { key: "invoiced_sales", label: "CA facturé", format: "moneyCompact" },
];

const ARTICLE_PARETO_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "product_name", label: "Article" },
  { key: "invoiced_sales", label: "CA facturé", format: "moneyCompact" },
  { key: "cumulative_share_percent", label: "Part cumulée", format: "percent" },
];

const STOCK_DETAIL_COLUMNS: BiDetailColumn[] = [
  { key: "product_name", label: "Article" },
  { key: "category_name", label: "Catégorie" },
  { key: "supplier_name", label: "Fournisseur" },
  { key: "qty_on_hand", label: "En stock", format: "number" },
  { key: "qty_reserved", label: "Réservé", format: "number" },
  { key: "qty_available", label: "Disponible", format: "number" },
  { key: "stock_risk_level", label: "Niveau de risque" },
];

export function BiArticlesPage() {
  const { range, applyRange, compareEnabled, toggleCompare, filters } = useBiRange();
  const {
    snapshot,
    isLoading,
    error,
    refreshArticles,
    kpis,
    topArticleBars,
    articleParetoBars,
    top10ShareLabel,
    categoryPie,
    themeBars,
    stockRows,
    comparisonCards,
  } = useBiArticles(filters);

  return (
    <BiShell
      title="Articles"
      subtitle="Analyse produits, thématiques, disponibilité et distributeurs."
      eyebrow="Catalogue & ventes"
      description="Une page pensée pour comprendre quels articles tirent le CA, quels thèmes performent et où surveiller la disponibilité."
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
            <strong>Impossible de charger les articles BI</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void refreshArticles()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !snapshot ? (
        <div className="dashboard-loading-grid" aria-label="Chargement des articles">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="dashboard-skeleton">
              <span />
            </Card>
          ))}
        </div>
      ) : null}

      {snapshot && kpis ? (
        <>
          <section className="bi-compare-grid" aria-label="Indicateurs articles année sur année">
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
              title="Top articles"
              subtitle={`Produits moteurs du CA facturé · ${snapshot.meta.latency_ms} ms`}
              detail={{
                title: "Top articles",
                columns: TOP_ARTICLES_DETAIL_COLUMNS,
                rows: snapshot.data.top_articles,
                filename: biCsvFilename("articles", "top-articles", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-auto">
                {topArticleBars.length ? (
                  <RankedBarChart data={topArticleBars} color="var(--chart-blue)" valueName="CA facturé" detail />
                ) : (
                  <EmptyChartState text="Aucun article facturé sur cette période." hint="Élargissez la période pour afficher le classement des articles." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Concentration du CA"
              subtitle="Pareto des articles les plus vendus — repère à 80% du CA."
              action={
                <div className="bi-headline-stat">
                  <strong>{top10ShareLabel}</strong>
                  <small>Top 10 articles</small>
                </div>
              }
              detail={{
                title: "Concentration du CA — articles",
                columns: ARTICLE_PARETO_DETAIL_COLUMNS,
                rows: snapshot.data.top_articles,
                filename: biCsvFilename("articles", "concentration", range.from, range.to),
              }}
            >
              <div className="bi-chart-body bi-chart-body-lg">
                {articleParetoBars.length ? (
                  <ParetoChart data={articleParetoBars} barColor="var(--chart-blue)" lineColor="var(--chart-amber)" barName="CA facturé" />
                ) : (
                  <EmptyChartState text="Aucune donnée de concentration sur cette période." hint="Élargissez la période pour recalculer le Pareto." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard title="Répartition du CA par catégorie" subtitle="Poids de chaque catégorie dans le CA facturé.">
              {categoryPie.length ? (
                <ContributionPieChart data={categoryPie} emptyText="Aucune catégorie disponible." />
              ) : (
                <EmptyChartState text="Aucune catégorie sur cette période." hint="Élargissez la période pour retrouver la répartition par catégorie." />
              )}
            </BiWidgetCard>

            <BiWidgetCard title="Top thématiques" subtitle="Thèmes les plus vendus.">
              <div className="bi-chart-body">
                {themeBars.length ? (
                  <RankedBarChart data={themeBars} color="var(--chart-violet)" valueName="CA facturé" />
                ) : (
                  <EmptyChartState text="Aucune thématique sur cette période." hint="Élargissez la période pour afficher les thèmes porteurs." />
                )}
              </div>
            </BiWidgetCard>

            <BiWidgetCard
              className="bi-span-2"
              title="Priorités stock"
              subtitle="Articles à surveiller en disponibilité — instantané indépendant de la période."
              detail={{
                title: "Priorités stock",
                columns: STOCK_DETAIL_COLUMNS,
                rows: snapshot.data.stock_priorities,
                filename: biCsvFilename("articles", "priorites-stock", range.from, range.to),
              }}
            >
              {stockRows.length ? (
                <BiStockPriorityTable rows={stockRows} />
              ) : (
                <EmptyChartState
                  text="Aucune priorité stock détectée."
                  hint="Le stock est un instantané indépendant de la période — rien à surveiller pour l'instant."
                />
              )}
            </BiWidgetCard>
          </section>
        </>
      ) : null}
    </BiShell>
  );
}

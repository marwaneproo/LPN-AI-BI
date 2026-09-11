import { useEffect, useMemo, useState } from "react";
import { fetchBiArticles } from "../api/biApi";
import type { BiArticlesFilters, BiArticlesResponse, BiNamedValue, RankedBarDatum } from "../types/bi.types";
import type { ParetoDatum } from "../components/charts/ParetoChart";
import { formatCompactMoney, formatNumber, formatPercent, shortenLabel } from "../../../utils/formatters";

function mapNamedValues(items: BiNamedValue[] | undefined, prefix: string): RankedBarDatum[] {
  return (items ?? []).slice(0, 8).map((item, index) => ({
    id: `${prefix}-${index}`,
    name: shortenLabel(item.name, 20),
    fullName: item.name,
    value: Number(item.value ?? 0),
    meta: "",
  }));
}

const ARTICLE_COLORS = ["var(--chart-blue)", "var(--bi-pink)", "var(--chart-green)", "var(--chart-amber)", "var(--chart-violet)", "var(--chart-blue-muted)"];

function topNamedValue(items: BiNamedValue[] | undefined) {
  return items?.[0] ?? null;
}

/** Maps the mart's stock_risk_level (+ stockout flag) to the shared risk-badge tone/label. */
function stockRiskBadge(riskLevel: string, isStockoutRisk: boolean): { tone: "critical" | "low" | "ok"; badgeText: string } {
  const level = (riskLevel || "").toLowerCase();
  const tone: "critical" | "low" | "ok" =
    isStockoutRisk || level.includes("crit") || level.includes("rupture")
      ? "critical"
      : level.includes("low") || level.includes("faible")
        ? "low"
        : "ok";
  return { tone, badgeText: tone === "critical" ? "Rupture" : tone === "low" ? "Faible" : "OK" };
}

export function useBiArticles(filters: BiArticlesFilters = {}) {
  const [snapshot, setSnapshot] = useState<BiArticlesResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function refreshArticles() {
    setIsLoading(true);
    setError("");
    try {
      setSnapshot(await fetchBiArticles(filters));
    } catch (error) {
      setError(error instanceof Error ? error.message : "Articles BI indisponibles.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshArticles();
  }, [filters.compare, filters.from, filters.granularity, filters.to]);

  const kpis = snapshot?.data.kpis;
  const topArticleItems = useMemo(
    () =>
      (snapshot?.data.top_articles ?? []).slice(0, 6).map((article, index) => ({
        label: shortenLabel(article.product_name, 18),
        value: Number(article.invoiced_sales ?? 0),
        color: ARTICLE_COLORS[index % ARTICLE_COLORS.length],
      })),
    [snapshot],
  );
  const mixSignals = useMemo(() => {
    const mix = snapshot?.data.mix_article;
    const topCategory = topNamedValue(mix?.categories);
    const topTheme = topNamedValue(mix?.themes);
    const topCollection = topNamedValue(mix?.collections);
    return [
      topCategory
        ? {
            label: "Catégorie",
            value: shortenLabel(topCategory.name, 18),
            helper: `CA facturé: ${formatCompactMoney(topCategory.value)}`,
          }
        : null,
      topTheme
        ? {
            label: "Thématique",
            value: shortenLabel(topTheme.name, 18),
            helper: `CA facturé: ${formatCompactMoney(topTheme.value)}`,
          }
        : null,
      topCollection
        ? {
            label: "Collection",
            value: shortenLabel(topCollection.name, 18),
            helper: `CA facturé: ${formatCompactMoney(topCollection.value)}`,
          }
        : null,
    ].filter((item): item is { label: string; value: string; helper: string } => item !== null);
  }, [snapshot]);
  const stockSignals = useMemo(
    () =>
      (snapshot?.data.stock_priorities ?? []).slice(0, 3).map((stock) => ({
        label: shortenLabel(stock.product_name, 22),
        value: stock.stock_risk_level || "A suivre",
        helper: `Disponible: ${formatNumber(stock.qty_available)} · réservé: ${formatNumber(stock.qty_reserved)}`,
      })),
    [snapshot],
  );

  const comparisonCards = useMemo(() => {
    const years = [...(snapshot?.data.yearly ?? [])].sort((a, b) => a.year - b.year);
    if (!years.length) return [];
    const current = years[years.length - 1];
    const previous = years.length > 1 ? years[years.length - 2] : null;
    const prevYear = previous?.year ?? current.year;

    return [
      {
        key: "ca",
        label: "CA articles",
        tone: "green" as const,
        value: formatCompactMoney(current.invoiced_sales),
        helper: `Facturé articles ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.invoiced_sales || 0),
        previousValue: Number(previous?.invoiced_sales ?? 0),
      },
      {
        key: "produits",
        label: "Produits actifs",
        tone: "blue" as const,
        value: formatNumber(current.active_products),
        helper: `Produits vendus ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_products || 0),
        previousValue: Number(previous?.active_products ?? 0),
      },
      {
        key: "quantite",
        label: "Quantité vendue",
        tone: "violet" as const,
        value: formatNumber(current.quantity),
        helper: `Unités facturées ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.quantity || 0),
        previousValue: Number(previous?.quantity ?? 0),
      },
      {
        key: "categories",
        label: "Catégories actives",
        tone: "amber" as const,
        value: formatNumber(current.active_categories),
        helper: `Catégories vendues ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_categories || 0),
        previousValue: Number(previous?.active_categories ?? 0),
      },
      {
        key: "themes",
        label: "Thèmes actifs",
        tone: "pink" as const,
        value: formatNumber(current.active_themes),
        helper: `Thématiques vendues ${current.year}`,
        currentYear: current.year,
        previousYear: prevYear,
        currentValue: Number(current.active_themes || 0),
        previousValue: Number(previous?.active_themes ?? 0),
      },
    ];
  }, [snapshot]);

  const topArticleBars = useMemo<RankedBarDatum[]>(
    () =>
      (snapshot?.data.top_articles ?? []).slice(0, 8).map((article, index) => ({
        id: `article-${index}`,
        name: shortenLabel(article.product_name, 24),
        fullName: article.product_name,
        value: Number(article.invoiced_sales ?? 0),
        meta: `${shortenLabel(article.category_name, 18)} · ${formatNumber(article.quantity)} unités`,
      })),
    [snapshot],
  );

  const articleConcentration = snapshot?.data.concentration;
  const top10ShareLabel = articleConcentration ? formatPercent(Number(articleConcentration.top10_share_percent ?? 0)) : "—";

  const articleParetoBars = useMemo<ParetoDatum[]>(() => {
    const topArticles = (snapshot?.data.top_articles ?? []).slice(0, 8);
    let previousCumulative = 0;
    return topArticles.map((article, index) => {
      const cumulative = Number(article.cumulative_share_percent ?? 0);
      const part = cumulative - previousCumulative;
      previousCumulative = cumulative;
      return {
        id: `article-pareto-${index}`,
        name: shortenLabel(article.product_name, 16),
        fullName: article.product_name,
        value: Number(article.invoiced_sales ?? 0),
        part,
        cumulative,
      };
    });
  }, [snapshot]);

  const categoryPie = useMemo<RankedBarDatum[]>(() => mapNamedValues(snapshot?.data.mix_article?.categories, "cat"), [snapshot]);
  const themeBars = useMemo<RankedBarDatum[]>(() => mapNamedValues(snapshot?.data.mix_article?.themes, "theme"), [snapshot]);

  const stockInsights = useMemo(() => {
    return (snapshot?.data.stock_priorities ?? []).slice(0, 6).map((stock) => {
      const { tone, badgeText } = stockRiskBadge(stock.stock_risk_level, stock.is_stockout_risk);
      const accent = tone === "critical" ? "var(--danger, oklch(58% 0.2 25))" : tone === "low" ? "var(--chart-amber)" : "var(--chart-green)";
      return {
        label: `Dispo ${formatNumber(stock.qty_available)} · Rés ${formatNumber(stock.qty_reserved)}`,
        title: shortenLabel(stock.product_name, 30),
        value: "",
        accent,
        badge: { text: badgeText, tone },
      };
    });
  }, [snapshot]);

  const stockRows = useMemo(() => {
    return (snapshot?.data.stock_priorities ?? []).map((stock) => {
      const { tone, badgeText } = stockRiskBadge(stock.stock_risk_level, stock.is_stockout_risk);
      return {
        id: stock.product_id,
        name: shortenLabel(stock.product_name, 38),
        category: shortenLabel(stock.category_name, 22),
        available: formatNumber(stock.qty_available),
        reserved: formatNumber(stock.qty_reserved),
        badge: { text: badgeText, tone },
      };
    });
  }, [snapshot]);

  return {
    snapshot,
    isLoading,
    error,
    refreshArticles,
    kpis,
    topArticleItems,
    mixSignals,
    stockSignals,
    topArticleBars,
    articleParetoBars,
    top10ShareLabel,
    categoryPie,
    themeBars,
    stockInsights,
    stockRows,
    comparisonCards,
  };
}

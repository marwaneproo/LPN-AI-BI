import { formatForecastMonthLabel } from "../../bi/utils/biFormatters";
import type { ForecastCaResponse } from "../../forecasts/types/forecast.types";
import type { PlanningEntityRow, PlanningTrend } from "../types/planning.types";

export type MonthlyShare = {
  month: string;
  label: string;
  total: number;
  sharePct: number;
};

export type CompanyInsights = {
  total: number;
  average: number;
  nextMonth: number;
  variationVsPreviousPct: number | null;
  trend: PlanningTrend;
  highestMonth: MonthlyShare | null;
  lowestMonth: MonthlyShare | null;
  monthlyBreakdown: MonthlyShare[];
  summary: string[];
  recommendations: string[];
};

export type CategoryInsights = {
  topCategory: PlanningEntityRow | null;
  growingCategories: PlanningEntityRow[];
  decliningCategories: PlanningEntityRow[];
  concentrationCount: number;
  peakMonth: MonthlyShare | null;
  lowMonth: MonthlyShare | null;
  summary: string[];
  recommendations: string[];
};

function trendLabel(trend: PlanningTrend): string {
  if (trend === "croissance") return "en croissance";
  if (trend === "baisse") return "en baisse";
  return "stable";
}

/**
 * Company-level decisional insights, computed entirely from the real ForecastCaResponse
 * already fetched for the Historique + Prévision chart — no additional calls, nothing
 * invented. "Variation vs période précédente" compares the forecast horizon's average
 * against the trailing real-history window of the same length (the same comparison already
 * used for the category YoY calculation, just expressed month-over-month here).
 */
export function computeCompanyInsights(data: ForecastCaResponse, horizon: number): CompanyInsights | null {
  if (data.error || !data.forecast.length) return null;

  const monthlyBreakdown: MonthlyShare[] = (() => {
    const total = data.forecast.reduce((sum, point) => sum + Number(point.yhat), 0);
    return data.forecast.map((point) => ({
      month: point.month,
      label: formatForecastMonthLabel(point.month),
      total: Number(point.yhat),
      sharePct: total > 0 ? (Number(point.yhat) / total) * 100 : 0,
    }));
  })();

  const total = monthlyBreakdown.reduce((sum, point) => sum + point.total, 0);
  const average = total / Math.max(1, monthlyBreakdown.length);
  const nextMonth = monthlyBreakdown[0]?.total ?? 0;

  const trailingHistory = data.history.slice(-monthlyBreakdown.length);
  const historyAvg =
    trailingHistory.length > 0
      ? trailingHistory.reduce((sum, point) => sum + Number(point.actual), 0) / trailingHistory.length
      : average;
  const variationVsPreviousPct = historyAvg > 0 ? ((average - historyAvg) / historyAvg) * 100 : null;

  const delta = historyAvg > 0 ? (average - historyAvg) / historyAvg : 0;
  const trend: PlanningTrend = delta > 0.03 ? "croissance" : delta < -0.03 ? "baisse" : "stabilite";

  const sorted = [...monthlyBreakdown].sort((a, b) => b.total - a.total);
  const highestMonth = sorted[0] ?? null;
  const lowestMonth = sorted[sorted.length - 1] ?? null;

  const summary: string[] = [
    `Sur les ${horizon} prochains mois, la demande prévisionnelle totale de l'entreprise est estimée à ${Math.round(total).toLocaleString("fr-FR")} (moyenne mensuelle ${Math.round(average).toLocaleString("fr-FR")}).`,
    `La tendance globale est ${trendLabel(trend)}${variationVsPreviousPct !== null ? ` (${variationVsPreviousPct >= 0 ? "+" : ""}${variationVsPreviousPct.toFixed(1)}% vs la période historique équivalente)` : ""}.`,
  ];
  if (highestMonth) summary.push(`Le mois le plus chargé prévu est ${highestMonth.label}, avec ${Math.round(highestMonth.total).toLocaleString("fr-FR")} (${highestMonth.sharePct.toFixed(1)}% du total de l'horizon).`);
  if (lowestMonth && lowestMonth.month !== highestMonth?.month) summary.push(`Le mois le plus calme prévu est ${lowestMonth.label}, avec ${Math.round(lowestMonth.total).toLocaleString("fr-FR")}.`);

  const recommendations: string[] = [];
  if (trend === "croissance") recommendations.push("Anticiper une hausse de la demande : sécuriser les approvisionnements avant le pic identifié plutôt que d'attendre la confirmation mensuelle.");
  if (trend === "baisse") recommendations.push("Demande en repli : éviter le surstockage global et réévaluer les commandes en cours à la baisse.");
  if (trend === "stabilite") recommendations.push("Demande stable : maintenir le rythme d'approvisionnement actuel, sans ajustement majeur nécessaire.");
  if (highestMonth) recommendations.push(`Préparer les approvisionnements en amont de ${highestMonth.label}, mois de plus forte demande prévue.`);
  if (variationVsPreviousPct !== null && Math.abs(variationVsPreviousPct) > 15) {
    recommendations.push(`Écart important vs l'historique (${variationVsPreviousPct >= 0 ? "+" : ""}${variationVsPreviousPct.toFixed(1)}%) : vérifier les hypothèses du plan d'approvisionnement avant validation.`);
  }

  return { total, average, nextMonth, variationVsPreviousPct, trend, highestMonth, lowestMonth, monthlyBreakdown, summary, recommendations };
}

/**
 * Category-level decisional insights, computed entirely from the already-fetched
 * PlanningEntityRow[] (real per-category Prophet forecasts) — no additional calls.
 */
export function computeCategoryInsights(rows: PlanningEntityRow[], horizon: number): CategoryInsights | null {
  if (rows.length === 0) return null;

  const topCategory = rows[0] ?? null;
  const growingCategories = rows.filter((row) => row.trend === "croissance");
  const decliningCategories = rows.filter((row) => row.trend === "baisse");

  const total = rows.reduce((sum, row) => sum + row.horizonTotal, 0);
  let cumulative = 0;
  let concentrationCount = 0;
  for (const row of rows) {
    cumulative += row.horizonTotal;
    concentrationCount += 1;
    if (total > 0 && cumulative / total >= 0.8) break;
  }

  const monthTotals = new Map<string, number>();
  rows.forEach((row) => row.monthly.forEach((point) => monthTotals.set(point.month, (monthTotals.get(point.month) ?? 0) + point.yhat)));
  const monthEntries = Array.from(monthTotals.entries()).sort((a, b) => b[1] - a[1]);
  const grandTotal = Array.from(monthTotals.values()).reduce((sum, v) => sum + v, 0);
  const peakMonth = monthEntries[0] ? { month: monthEntries[0][0], label: formatForecastMonthLabel(monthEntries[0][0]), total: monthEntries[0][1], sharePct: grandTotal > 0 ? (monthEntries[0][1] / grandTotal) * 100 : 0 } : null;
  const lowEntry = monthEntries[monthEntries.length - 1];
  const lowMonth = lowEntry ? { month: lowEntry[0], label: formatForecastMonthLabel(lowEntry[0]), total: lowEntry[1], sharePct: grandTotal > 0 ? (lowEntry[1] / grandTotal) * 100 : 0 } : null;

  const summary: string[] = [
    `${rows.length} catégories disposent d'une prévision fiable sur ${horizon} mois. ${topCategory ? `La catégorie la plus demandée est ${topCategory.label} (${Math.round(topCategory.horizonTotal).toLocaleString("fr-FR")} prévus).` : ""}`,
    `${concentrationCount} catégorie${concentrationCount > 1 ? "s" : ""} concentre${concentrationCount > 1 ? "nt" : ""} environ 80% du besoin prévisionnel total — c'est là que doit se concentrer l'effort de planification.`,
  ];
  if (growingCategories.length > 0) summary.push(`${growingCategories.length} catégorie${growingCategories.length > 1 ? "s" : ""} en croissance : ${growingCategories.slice(0, 4).map((r) => r.label).join(", ")}.`);
  if (decliningCategories.length > 0) summary.push(`${decliningCategories.length} catégorie${decliningCategories.length > 1 ? "s" : ""} en baisse : ${decliningCategories.slice(0, 4).map((r) => r.label).join(", ")}.`);

  const recommendations: string[] = [];
  if (topCategory) recommendations.push(`Prioriser les approvisionnements de ${topCategory.label}, principal contributeur à la demande prévisionnelle.`);
  if (concentrationCount > 0) recommendations.push(`Concentrer le suivi rapproché sur les ${concentrationCount} catégorie${concentrationCount > 1 ? "s" : ""} représentant l'essentiel du besoin (logique 80/20).`);
  growingCategories.slice(0, 3).forEach((row) => recommendations.push(`Renforcer les stocks de ${row.label} (tendance en croissance).`));
  decliningCategories.slice(0, 3).forEach((row) => recommendations.push(`Réduire les commandes prévues pour ${row.label} (tendance en baisse) afin d'éviter le surstock.`));
  if (peakMonth) recommendations.push(`Anticiper un pic de besoin global en ${peakMonth.label} — planifier les commandes fournisseurs en amont.`);

  return { topCategory, growingCategories, decliningCategories, concentrationCount, peakMonth, lowMonth, summary, recommendations };
}

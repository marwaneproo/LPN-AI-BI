import { fetchForecastCa, fetchForecastOptions } from "../../forecasts/api/forecastApi";
import { formatForecastMonthLabel } from "../../bi/utils/biFormatters";
import type { PlanningEntityRow, PlanningMonthPoint, PlanningTrend } from "../types/planning.types";

function classifyTrend(forecastAvg: number, historyAvg: number): PlanningTrend {
  if (historyAvg <= 0) return "stabilite";
  const delta = (forecastAvg - historyAvg) / historyAvg;
  if (delta > 0.03) return "croissance";
  if (delta < -0.03) return "baisse";
  return "stabilite";
}

/**
 * Builds one planning row for a single forecastable entity (a category, today — the only
 * non-company grain the real forecast engine supports).
 * Every number here comes straight from the real Prophet-based /v1/forecast/ca response —
 * nothing is invented client-side beyond simple arithmetic (sums, averages, % deltas).
 */
async function buildEntityRow(
  grain: "category",
  key: number,
  label: string,
  completeMonths: number,
  horizon: number,
): Promise<PlanningEntityRow | null> {
  const data = await fetchForecastCa(grain, key, horizon).catch(() => null);
  if (!data || data.error || !data.forecast.length) return null;

  const monthly: PlanningMonthPoint[] = data.forecast.map((point) => ({
    month: point.month,
    label: formatForecastMonthLabel(point.month),
    yhat: Math.max(0, Number(point.yhat)),
  }));

  const horizonTotal = monthly.reduce((sum, point) => sum + point.yhat, 0);
  const forecastAvg = horizonTotal / Math.max(1, monthly.length);

  const trailingHistory = data.history.slice(-monthly.length);
  const historyAvg =
    trailingHistory.length > 0
      ? trailingHistory.reduce((sum, point) => sum + Number(point.actual), 0) / trailingHistory.length
      : forecastAvg;

  const nextMonthStr = data.forecast[0]?.month;
  let yoyPct: number | null = null;
  if (nextMonthStr) {
    const [year, month] = nextMonthStr.split("-").map(Number);
    const sameMonthPrefix = `${year - 1}-${String(month).padStart(2, "0")}`;
    const lastYearPoint = data.history.find((h) => h.month.startsWith(sameMonthPrefix));
    if (lastYearPoint && Number(lastYearPoint.actual) > 0) {
      yoyPct = ((Number(data.forecast[0].yhat) - Number(lastYearPoint.actual)) / Number(lastYearPoint.actual)) * 100;
    }
  }

  return {
    key,
    label,
    monthly,
    horizonTotal,
    nextMonth: monthly[0]?.yhat ?? 0,
    yoyPct,
    trend: classifyTrend(forecastAvg, historyAvg),
    completeMonths,
  };
}

/**
 * Fetches real per-category forecasts (one real /v1/forecast/ca call per forecastable
 * category) and returns them ranked by forecast horizon total, descending. Used to power
 * the ranking, Pareto, heatmap, breakdown, and trend visualizations for the "Catégorie" level.
 */
export async function fetchCategoryPlanningRows(horizon: number): Promise<PlanningEntityRow[]> {
  const options = await fetchForecastOptions("category");
  const forecastable = options.filter((option) => option.forecastable);
  const rows = await Promise.all(
    forecastable.map((option) => buildEntityRow("category", option.key, option.label, option.complete_months, horizon)),
  );
  return rows
    .filter((row): row is PlanningEntityRow => row !== null)
    .sort((a, b) => b.horizonTotal - a.horizonTotal);
}

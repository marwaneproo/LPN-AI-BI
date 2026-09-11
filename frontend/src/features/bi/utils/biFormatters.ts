import { parseIsoDate, toIsoDate } from "../../../utils/dateUtils";
import { formatNumber } from "../../../utils/formatters";

/** Formats BI trend periods day-aware: YYYY-MM-DD as "dd mon.", YYYY-MM as "mon.". */
export function formatPeriodLabel(period: string) {
  const [year, month, day] = period.split("-").map(Number);
  if (!year || !month) return period;
  const date = new Date(year, month - 1, day || 1);
  return date.toLocaleDateString("fr-FR", day ? { day: "2-digit", month: "short" } : { month: "short" });
}

/** Formats analysis periods day-aware: YYYY-MM-DD as "dd mon.", YYYY-MM as "mon.". */
export function formatAnalysisPeriodLabel(period: string) {
  const [year, month, day] = period.split("-").map(Number);
  if (!year || !month) return period;
  const date = new Date(year, month - 1, day || 1);
  return date.toLocaleDateString("fr-FR", day ? { day: "2-digit", month: "short" } : { month: "short" });
}

export function formatAnalysisDate(value: string) {
  const parsed = parseIsoDate(value);
  if (!parsed) return value || "date inconnue";
  return parsed.toLocaleDateString("fr-FR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function formatAnalysisRangeSummary(fromDate: string, toDate: string) {
  const from = parseIsoDate(fromDate);
  const to = parseIsoDate(toDate);
  if (!from || !to) {
    return "Période analysée non définie";
  }
  const dayMs = 24 * 60 * 60 * 1000;
  const days = Math.max(1, Math.round((to.getTime() - from.getTime()) / dayMs) + 1);
  const duration =
    days >= 60
      ? `${formatNumber(Math.round(days / 30))} mois environ`
      : days >= 14
        ? `${formatNumber(Math.round(days / 7))} semaines environ`
        : `${formatNumber(days)} jours`;
  return `Période analysée : du ${formatAnalysisDate(fromDate)} au ${formatAnalysisDate(toDate)}, ${duration}`;
}

export function formatForecastMonthLabel(month: string) {
  const [yearStr, mStr] = month.split("-");
  const year = Number(yearStr);
  const m = Number(mStr);
  if (!year || !m) return month;
  return new Date(year, m - 1, 1).toLocaleDateString("fr-FR", { month: "short", year: "2-digit" });
}

export function getDefaultAnalysisRange() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - 29);
  return {
    fromDate: toIsoDate(from),
    toDate: toIsoDate(to),
  };
}

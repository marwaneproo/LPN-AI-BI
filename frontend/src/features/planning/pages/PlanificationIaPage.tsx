import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  Boxes,
  Building2,
  Clock3,
  LineChart as LineChartIcon,
  Lightbulb,
  Minus,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import {
  Area,
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import syncIconSvg from "../../../assets/icons/sync.svg";
import { Card, CardTitle } from "../../../components/ui/Card";
import { PageFrame } from "../../../components/layout/PageFrame";
import { BiKpiCard } from "../../bi/components/kpi/BiKpiCard";
import { formatCompactMoney, formatPercent, compactAxisTick } from "../../../utils/formatters";
import { formatForecastMonthLabel } from "../../bi/utils/biFormatters";
import { fetchForecastCa, fetchForecastOptions } from "../../forecasts/api/forecastApi";
import { fetchCategoryPlanningRows } from "../api/planningApi";
import { computeCategoryInsights, computeCompanyInsights } from "../services/planningInsights";
import type { ForecastCaResponse, ForecastChartPoint, ForecastOption } from "../../forecasts/types/forecast.types";
import type { PlanningEntityRow, PlanningLevel } from "../types/planning.types";

const LEVEL_OPTIONS: Array<{ value: PlanningLevel; label: string; icon: typeof Building2 }> = [
  { value: "company", label: "Entreprise", icon: Building2 },
  { value: "category", label: "Catégorie", icon: Boxes },
];

const HORIZON_OPTIONS = [
  { value: 1, label: "1 mois" },
  { value: 3, label: "3 mois" },
  { value: 6, label: "6 mois" },
  { value: 12, label: "12 mois" },
];

const PIE_COLORS = [
  "var(--chart-blue)",
  "var(--chart-green)",
  "var(--chart-amber)",
  "var(--chart-violet)",
  "var(--chart-red)",
];

function trendMeta(trend: PlanningEntityRow["trend"]) {
  if (trend === "croissance") return { label: "Croissance", icon: TrendingUp, className: "quality-badge valid" };
  if (trend === "baisse") return { label: "Baisse", icon: TrendingDown, className: "quality-badge warning" };
  return { label: "Stable", icon: Minus, className: "quality-badge" };
}

function buildChartData(data: ForecastCaResponse | null): ForecastChartPoint[] {
  if (!data || data.error) return [];
  const { history, forecast } = data;
  const histPoints: ForecastChartPoint[] = history.map((h) => ({
    label: formatForecastMonthLabel(h.month),
    month: h.month,
    actual: Number(h.actual),
    yhat: null,
    lower: null,
    bandwidth: null,
  }));
  if (histPoints.length > 0) {
    const last = histPoints[histPoints.length - 1];
    last.yhat = last.actual;
    last.lower = last.actual;
    last.bandwidth = 0;
  }
  const fcastPoints: ForecastChartPoint[] = forecast.map((f) => ({
    label: formatForecastMonthLabel(f.month),
    month: f.month,
    actual: null,
    yhat: Number(f.yhat),
    lower: Number(f.lower),
    bandwidth: Number(f.upper) - Number(f.lower),
  }));
  return [...histPoints, ...fcastPoints];
}

function HistoryForecastChart({ data }: { data: ForecastChartPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <ComposedChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} interval="preserveStartEnd" />
        <YAxis tickFormatter={compactAxisTick} tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} width={68} />
        <Tooltip
          formatter={(value: number, name: string) => [formatCompactMoney(value), name]}
          labelStyle={{ color: "var(--text-primary)" }}
        />
        <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
        <Area type="monotone" dataKey="lower" stackId="band" fill="transparent" stroke="none" legendType="none" name="lower_offset" isAnimationActive={false} />
        <Area type="monotone" dataKey="bandwidth" stackId="band" fill="var(--accent)" fillOpacity={0.12} stroke="none" name="Intervalle de confiance" isAnimationActive={false} />
        <Line type="monotone" dataKey="actual" stroke="var(--accent)" strokeWidth={2} dot={false} name="Historique" connectNulls={false} isAnimationActive={false} />
        <Line type="monotone" dataKey="yhat" stroke="var(--accent)" strokeWidth={2} strokeDasharray="5 5" dot={false} name="Prévision" connectNulls={false} isAnimationActive={false} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

function DecisionSynthesisCard({ summary, recommendations }: { summary: string[]; recommendations: string[] }) {
  return (
    <Card>
      <CardTitle
        title="Synthèse décisionnelle"
        subtitle="Calculée en temps réel à partir des prévisions et données actuellement affichées."
      />
      <div className="panel-grid">
        <div className="source-card">
          <div>
            <strong><Sparkles size={13} /> Principaux résultats</strong>
          </div>
          <ul className="planning-summary-list">
            {summary.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
        <div className="source-card">
          <div>
            <strong><Lightbulb size={13} /> Recommandations décisionnelles</strong>
          </div>
          <ul className="planning-recommendation-list">
            {recommendations.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      </div>
    </Card>
  );
}

export function PlanificationIaPage() {
  const [level, setLevel] = useState<PlanningLevel>("company");
  const [horizon, setHorizon] = useState(6);

  const [companyData, setCompanyData] = useState<ForecastCaResponse | null>(null);

  const [categoryRows, setCategoryRows] = useState<PlanningEntityRow[]>([]);
  const [categoryOptions, setCategoryOptions] = useState<ForecastOption[]>([]);
  const [selectedCategoryKey, setSelectedCategoryKey] = useState<number | null>(null);
  const [selectedCategoryData, setSelectedCategoryData] = useState<ForecastCaResponse | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  async function loadCompany() {
    const data = await fetchForecastCa("company", null, horizon);
    setCompanyData(data);
  }

  async function loadCategory() {
    const [rows, options] = await Promise.all([fetchCategoryPlanningRows(horizon), fetchForecastOptions("category")]);
    setCategoryRows(rows);
    setCategoryOptions(options.filter((option) => option.forecastable));
    const preferredKey = rows[0]?.key ?? null;
    setSelectedCategoryKey((current) => (current !== null && rows.some((row) => row.key === current) ? current : preferredKey));
  }

  async function load() {
    setIsLoading(true);
    setError("");
    try {
      if (level === "company") {
        await loadCompany();
      } else {
        await loadCategory();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Planification indisponible.");
    } finally {
      setIsLoading(false);
    }
  }

  // Reactive: any change to level or horizon automatically recalculates everything below —
  // no manual reload button required.
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [level, horizon]);

  useEffect(() => {
    if (level !== "category" || selectedCategoryKey === null) {
      setSelectedCategoryData(null);
      return;
    }
    let cancelled = false;
    fetchForecastCa("category", selectedCategoryKey, horizon)
      .then((data) => { if (!cancelled) setSelectedCategoryData(data); })
      .catch(() => { if (!cancelled) setSelectedCategoryData(null); });
    return () => { cancelled = true; };
  }, [level, selectedCategoryKey, horizon]);

  const companyChartData = useMemo(() => buildChartData(companyData), [companyData]);
  const companyInsufficient = companyData?.error === "insufficient_history";
  const companyInsights = useMemo(
    () => (companyData && !companyInsufficient ? computeCompanyInsights(companyData, horizon) : null),
    [companyData, companyInsufficient, horizon],
  );

  const selectedCategoryChartData = useMemo(() => buildChartData(selectedCategoryData), [selectedCategoryData]);

  const categoryInsights = useMemo(() => computeCategoryInsights(categoryRows, horizon), [categoryRows, horizon]);

  // Display-only limit for the "Prévision des besoins par catégorie" chart, to avoid
  // overlapping with the Heatmap below when there are many categories. categoryRows itself
  // (already sorted descending) stays the full ranking everywhere else — KPIs, heatmap,
  // donut, trends, and the decisional synthesis all keep using every category.
  const topCategoryRows = useMemo(() => categoryRows.slice(0, 10), [categoryRows]);

  const paretoData = useMemo(() => {
    const total = categoryRows.reduce((sum, row) => sum + row.horizonTotal, 0);
    let cumulative = 0;
    return categoryRows.map((row) => {
      cumulative += row.horizonTotal;
      return {
        label: row.label,
        value: row.horizonTotal,
        cumulativePct: total > 0 ? (cumulative / total) * 100 : 0,
      };
    });
  }, [categoryRows]);

  const heatmapMonths = useMemo(() => {
    const months = new Set<string>();
    categoryRows.forEach((row) => row.monthly.forEach((point) => months.add(point.month)));
    return Array.from(months).sort();
  }, [categoryRows]);

  const heatmapMax = useMemo(
    () => categoryRows.reduce((max, row) => Math.max(max, ...row.monthly.map((point) => point.yhat)), 0),
    [categoryRows],
  );

  const donutData = useMemo(
    () => categoryRows.slice(0, 8).map((row) => ({ name: row.label, value: row.horizonTotal })),
    [categoryRows],
  );

  return (
    <PageFrame
      title="Planification IA"
      subtitle="Aide à la décision pour préparer les futurs approvisionnements — à partir des prévisions réelles et des données disponibles."
      headerAside={
        <div className="dashboard-actions">
          <button className="primary-soft dashboard-refresh" onClick={() => void load()} disabled={isLoading}>
            {isLoading ? <Clock3 size={15} /> : <img src={syncIconSvg} alt="" style={{ width: 16, height: 16, objectFit: "contain" }} />}
            {isLoading ? "Calcul..." : "Actualiser"}
          </button>
        </div>
      }
    >
      <Card>
        <CardTitle title="Paramètres" subtitle="Niveau d'analyse et horizon — tout se recalcule automatiquement." />
        <div className="analysis-filter-grid">
          <label>
            <span>Niveau d'analyse</span>
            <select value={level} onChange={(e) => setLevel(e.target.value as PlanningLevel)}>
              {LEVEL_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
          {level === "category" ? (
            <label>
              <span>Catégorie (historique détaillé)</span>
              <select
                value={selectedCategoryKey ?? ""}
                onChange={(e) => setSelectedCategoryKey(e.target.value ? Number(e.target.value) : null)}
                disabled={categoryOptions.length === 0}
              >
                {categoryOptions.length === 0 && <option value="">Aucune donnée disponible</option>}
                {categoryOptions.map((opt) => (
                  <option key={opt.key} value={opt.key}>{opt.label}</option>
                ))}
              </select>
            </label>
          ) : null}
          <label>
            <span>Horizon</span>
            <select value={horizon} onChange={(e) => setHorizon(Number(e.target.value))}>
              {HORIZON_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
        </div>
      </Card>

      {error ? (
        <Card className="dashboard-error">
          <AlertTriangle size={20} />
          <div>
            <strong>Planification indisponible</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void load()}>Réessayer</button>
          </div>
        </Card>
      ) : null}

      {isLoading ? (
        <div className="dashboard-loading-grid">
          {Array.from({ length: 4 }, (_, i) => (
            <Card key={i} className="dashboard-skeleton"><span /></Card>
          ))}
        </div>
      ) : null}

      {!isLoading && !error && level === "company" ? (
        companyInsufficient ? (
          <Card className="empty-state">
            <LineChartIcon size={24} />
            <div>
              <strong>Pas assez d'historique</strong>
              <p>
                Disponible&nbsp;: {companyData?.available_months ?? 0} mois, minimum requis&nbsp;:{" "}
                {companyData?.minimum_months ?? 12}.
              </p>
            </div>
          </Card>
        ) : (
          <div className="planning-stack">
            <h3 className="planning-section-heading">Vue d'ensemble — Entreprise</h3>
            {companyInsights ? (
              <div className="bi-kpi-grid">
                <BiKpiCard
                  label="Total prévisionnel"
                  value={formatCompactMoney(companyInsights.total)}
                  helper={`Sur ${horizon} mois`}
                  icon={<Boxes size={17} />}
                  strong
                />
                <BiKpiCard
                  label="Moyenne mensuelle"
                  value={formatCompactMoney(companyInsights.average)}
                  helper="Prévision moyenne par mois"
                  icon={<LineChartIcon size={17} />}
                />
                <BiKpiCard
                  label="Variation vs historique"
                  value={companyInsights.variationVsPreviousPct !== null ? `${companyInsights.variationVsPreviousPct >= 0 ? "+" : ""}${formatPercent(companyInsights.variationVsPreviousPct)}%` : "—"}
                  helper="vs période historique équivalente"
                  icon={companyInsights.trend === "baisse" ? <TrendingDown size={17} /> : <TrendingUp size={17} />}
                  good={companyInsights.trend !== "baisse"}
                />
                <BiKpiCard
                  label="Mois le plus chargé"
                  value={companyInsights.highestMonth?.label ?? "—"}
                  helper={companyInsights.highestMonth ? formatCompactMoney(companyInsights.highestMonth.total) : ""}
                  icon={<Sparkles size={17} />}
                />
              </div>
            ) : null}

            <Card>
              <CardTitle title="Historique + Prévision — Entreprise" subtitle="Trait plein = historique, pointillé = prévision, avec intervalle de confiance." />
              <div className="chart-box chart-box-large">
                <HistoryForecastChart data={companyChartData} />
              </div>
            </Card>

            {companyInsights ? (
              <Card>
                <CardTitle title="Répartition mensuelle des besoins prévisionnels" subtitle="Part de chaque mois dans le total de l'horizon sélectionné." />
                <div className="chart-box">
                  <ResponsiveContainer width="100%" height={280}>
                    <ComposedChart data={companyInsights.monthlyBreakdown} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} />
                      <YAxis tickFormatter={compactAxisTick} tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} />
                      <Tooltip formatter={(value: number) => formatCompactMoney(value)} />
                      <Bar dataKey="total" name="Besoin prévisionnel" fill="var(--chart-blue)" radius={[6, 6, 0, 0]} />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            ) : null}

            {companyInsights ? (
              <>
                <h3 className="planning-section-heading">Aide à la décision</h3>
                <DecisionSynthesisCard summary={companyInsights.summary} recommendations={companyInsights.recommendations} />
              </>
            ) : null}
          </div>
        )
      ) : null}

      {!isLoading && !error && level === "category" ? (
        <div className="planning-stack">
          <h3 className="planning-section-heading">Historique et prévision par catégorie</h3>
          <Card>
            <CardTitle
              title={`Historique + Prévision — ${categoryOptions.find((o) => o.key === selectedCategoryKey)?.label ?? "Catégorie"}`}
              subtitle="Trait plein = historique, pointillé = prévision, avec intervalle de confiance."
            />
            <div className="chart-box chart-box-large">
              <HistoryForecastChart data={selectedCategoryChartData} />
            </div>
          </Card>

          <h3 className="planning-section-heading">Besoins prévisionnels par catégorie</h3>

          <Card>
            <CardTitle title="Prévision des besoins par catégorie" subtitle={`Total prévisionnel sur ${horizon} mois, classement décroissant.`} />
            <div className="chart-box">
              <ResponsiveContainer width="100%" height={Math.max(220, topCategoryRows.length * 34)}>
                <ComposedChart data={topCategoryRows.map((r) => ({ label: r.label, value: r.horizonTotal }))} layout="vertical" margin={{ left: 8, right: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis type="number" tickFormatter={compactAxisTick} tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} />
                  <YAxis type="category" dataKey="label" width={140} tick={{ fontSize: 11, fill: "var(--text-tertiary)" }} />
                  <Tooltip formatter={(value: number) => formatCompactMoney(value)} />
                  <Bar dataKey="value" name="Besoin prévisionnel" fill="var(--chart-blue)" radius={[0, 6, 6, 0]} />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card>
            <CardTitle title="Heatmap des besoins futurs" subtitle="Mois × Catégories — intensité de couleur proportionnelle au besoin prévisionnel." />
            <div className="chart-box" style={{ overflowX: "auto" }}>
              <table className="planning-heatmap">
                <thead>
                  <tr>
                    <th />
                    {heatmapMonths.map((month) => (
                      <th key={month}>{formatForecastMonthLabel(month)}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {categoryRows.map((row) => (
                    <tr key={row.key}>
                      <th scope="row">{row.label}</th>
                      {heatmapMonths.map((month) => {
                        const point = row.monthly.find((p) => p.month === month);
                        const intensity = point && heatmapMax > 0 ? point.yhat / heatmapMax : 0;
                        return (
                          <td
                            key={month}
                            style={{ background: point ? `color-mix(in oklab, var(--chart-blue) ${Math.round(intensity * 85)}%, transparent)` : "transparent" }}
                            title={point ? formatCompactMoney(point.yhat) : "—"}
                          >
                            {point ? formatCompactMoney(point.yhat) : "—"}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <h3 className="planning-section-heading">Répartition et tendances</h3>

          <Card>
            <CardTitle title="Répartition prévisionnelle" subtitle="Part de chaque catégorie dans le besoin prévisionnel total." />
            <div className="chart-box">
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie data={donutData} dataKey="value" nameKey="name" innerRadius={60} outerRadius={100} paddingAngle={2}>
                    {donutData.map((entry, index) => (
                      <Cell key={entry.name} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value: number) => formatCompactMoney(value)} />
                  <Legend wrapperStyle={{ fontSize: 11 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card>
            <CardTitle title="Tendances prévisionnelles" subtitle="Croissance, stabilité ou baisse par catégorie, vs même horizon l'an dernier." />
            <div className="panel-grid">
              {categoryRows.map((row) => {
                const meta = trendMeta(row.trend);
                const Icon = meta.icon;
                return (
                  <div key={row.key} className="source-card">
                    <div>
                      <strong>{row.label}</strong>
                      <span className={meta.className}><Icon size={12} /> {meta.label}</span>
                    </div>
                    <p>{formatCompactMoney(row.horizonTotal)} prévu sur {horizon} mois</p>
                    {row.yoyPct !== null ? (
                      <small>{row.yoyPct >= 0 ? "+" : ""}{formatPercent(row.yoyPct)}% vs même mois l'an dernier</small>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </Card>

          {categoryInsights ? (
            <>
              <h3 className="planning-section-heading">Aide à la décision</h3>
              <DecisionSynthesisCard summary={categoryInsights.summary} recommendations={categoryInsights.recommendations} />
            </>
          ) : null}
        </div>
      ) : null}
    </PageFrame>
  );
}

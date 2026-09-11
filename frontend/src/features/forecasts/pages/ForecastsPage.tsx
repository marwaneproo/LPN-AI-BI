import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  Clock3,
  LineChart,
  Search,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
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
import { FORECAST_GRAIN_OPTIONS, FORECAST_HORIZON_OPTIONS } from "../constants/forecast.constants";
import { fetchForecastCa, fetchForecastBacktest, fetchForecastOptions } from "../api/forecastApi";
import type {
  ForecastGrain,
  ForecastCaResponse,
  ForecastChartPoint,
  ForecastOption,
} from "../types/forecast.types";

function ForecastTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: Array<{ dataKey?: string; value?: number; payload?: Record<string, unknown> }>;
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  const raw = payload[0]?.payload as ForecastChartPoint | undefined;
  return (
    <div className="bi-tooltip">
      <strong>{label}</strong>
      {raw?.actual != null ? (
        <span>CA facturé&nbsp;: {formatCompactMoney(raw.actual)}</span>
      ) : null}
      {raw?.yhat != null ? (
        <span>Prévision&nbsp;: {formatCompactMoney(raw.yhat)}</span>
      ) : null}
      {raw?.lower != null && raw?.bandwidth != null ? (
        <small>IC&nbsp;: {formatCompactMoney(raw.lower)} – {formatCompactMoney(raw.lower + raw.bandwidth)}</small>
      ) : null}
    </div>
  );
}

function ForecastChart({ data }: { data: ForecastChartPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height={340}>
      <ComposedChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
        <XAxis
          dataKey="label"
          tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
          interval="preserveStartEnd"
        />
        <YAxis
          tickFormatter={compactAxisTick}
          tick={{ fontSize: 11, fill: "var(--text-tertiary)" }}
          width={68}
        />
        <Tooltip content={<ForecastTooltip />} />
        <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
        <Area
          type="monotone"
          dataKey="lower"
          stackId="band"
          fill="transparent"
          stroke="none"
          legendType="none"
          name="lower_offset"
          isAnimationActive={false}
        />
        <Area
          type="monotone"
          dataKey="bandwidth"
          stackId="band"
          fill="var(--accent)"
          fillOpacity={0.12}
          stroke="none"
          name="Intervalle de confiance"
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="actual"
          stroke="var(--accent)"
          strokeWidth={2}
          dot={false}
          name="CA facturé"
          connectNulls={false}
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="yhat"
          stroke="var(--accent)"
          strokeWidth={2}
          strokeDasharray="5 5"
          dot={false}
          name="Prévision"
          connectNulls={false}
          isAnimationActive={false}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export function ForecastsPage() {
  const [grain, setGrain] = useState<ForecastGrain>("company");
  const [keyInput, setKeyInput] = useState("");
  const [options, setOptions] = useState<ForecastOption[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [horizon, setHorizon] = useState(6);
  const [forecastData, setForecastData] = useState<ForecastCaResponse | null>(null);
  const [wape, setWape] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const resolvedKey = grain !== "company" && keyInput.trim() !== "" ? parseInt(keyInput, 10) : null;

  async function loadForecast() {
    setIsLoading(true);
    setError("");
    try {
      const [caResult, btResult] = await Promise.all([
        fetchForecastCa(grain, resolvedKey, horizon),
        fetchForecastBacktest("company", null, 6).catch(() => ({ blend_05_metrics: { wape: 0, mae: 0, rmse: 0, mape_pct: 0 } })),
      ]);
      setForecastData(caResult);
      setWape(btResult.blend_05_metrics?.wape ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Prévisions indisponibles.");
      setForecastData(null);
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadForecast();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (grain === "company") {
      setOptions([]);
      return;
    }
    let cancelled = false;
    setOptionsLoading(true);
    fetchForecastOptions(grain)
      .then((opts) => {
        if (cancelled) return;
        setOptions(opts);
        const first = opts.find((o) => o.forecastable) ?? opts[0];
        setKeyInput(first ? String(first.key) : "");
      })
      .catch(() => { if (!cancelled) setOptions([]); })
      .finally(() => { if (!cancelled) setOptionsLoading(false); });
    return () => { cancelled = true; };
  }, [grain]);

  const insufficientHistory = forecastData?.error === "insufficient_history";

  const chartData = useMemo<ForecastChartPoint[]>(() => {
    if (!forecastData || insufficientHistory) return [];
    const { history, forecast } = forecastData;

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
  }, [forecastData, insufficientHistory]);

  const kpis = useMemo(() => {
    if (!forecastData || insufficientHistory || !forecastData.forecast.length) return null;
    const { history, forecast } = forecastData;

    const nextMonthYhat = Number(forecast[0].yhat);
    const total3m = forecast.slice(0, 3).reduce((acc, f) => acc + Number(f.yhat), 0);

    const nextMonthStr = forecast[0].month;
    const parts = nextMonthStr.split("-").map(Number);
    const nextYear = parts[0];
    const nextM = parts[1];
    const sameMonthPrefix = `${nextYear - 1}-${String(nextM).padStart(2, "0")}`;
    const lastYearPoint = history.find((h) => h.month.startsWith(sameMonthPrefix));
    const yoy =
      lastYearPoint && Number(lastYearPoint.actual) > 0
        ? ((nextMonthYhat - Number(lastYearPoint.actual)) / Number(lastYearPoint.actual)) * 100
        : null;

    const accuracy = (wape !== null && wape > 0) ? (1 - wape) * 100 : null;

    return { nextMonthYhat, total3m, yoy, accuracy };
  }, [forecastData, insufficientHistory, wape]);

  return (
    <PageFrame
      title="Prévisions"
      subtitle="Prévision de CA — modèle Prophet + naïf, 24 mois d'historique, recalibré sur données facturées."
      headerAside={
        <div className="dashboard-actions">
          <button className="primary-soft dashboard-refresh" onClick={() => void loadForecast()} disabled={isLoading}>
            {isLoading ? <Clock3 size={15} /> : <img src={syncIconSvg} alt="" style={{ width: 16, height: 16, objectFit: "contain" }} />}
            {isLoading ? "Calcul..." : "Actualiser"}
          </button>
        </div>
      }
    >
      <Card>
        <CardTitle title="Paramètres" subtitle="Choisir le niveau d'analyse et l'horizon de prévision." />
        <div className="analysis-filter-grid">
          <label>
            <span>Niveau d'analyse</span>
            <select
              value={grain}
              onChange={(e) => {
                setGrain(e.target.value as ForecastGrain);
                setKeyInput("");
              }}
            >
              {FORECAST_GRAIN_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
          {grain !== "company" ? (
            <label>
              <span>Entité</span>
              <select
                value={keyInput}
                onChange={(e) => setKeyInput(e.target.value)}
                disabled={optionsLoading}
              >
                {optionsLoading && <option value="">Chargement…</option>}
                {!optionsLoading && options.length === 0 && (
                  <option value="">Aucune donnée disponible</option>
                )}
                {!optionsLoading && options.map((opt) => (
                  <option key={opt.key} value={String(opt.key)} disabled={!opt.forecastable}>
                    {opt.label} ({opt.complete_months} mois){!opt.forecastable ? " — données insuffisantes" : ""}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
          <label>
            <span>Horizon</span>
            <select value={horizon} onChange={(e) => setHorizon(Number(e.target.value))}>
              {FORECAST_HORIZON_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="analysis-filter-actions">
          <div />
          <button
            className="send-button analysis-apply-button"
            onClick={() => void loadForecast()}
            disabled={isLoading}
            aria-label="Appliquer"
          >
            <Search size={17} />
          </button>
        </div>
      </Card>

      {error ? (
        <Card className="dashboard-error">
          <AlertTriangle size={20} />
          <div>
            <strong>Prévisions indisponibles</strong>
            <p>{error}</p>
            <button className="primary-soft dashboard-error-action" onClick={() => void loadForecast()}>
              Réessayer
            </button>
          </div>
        </Card>
      ) : null}

      {isLoading && !forecastData ? (
        <div className="dashboard-loading-grid">
          {Array.from({ length: 4 }, (_, i) => (
            <Card key={i} className="dashboard-skeleton"><span /></Card>
          ))}
        </div>
      ) : null}

      {!isLoading && insufficientHistory ? (
        <Card className="empty-state">
          <LineChart size={24} />
          <div>
            <strong>Pas assez d'historique</strong>
            <p>
              Cette sélection ne dispose pas de suffisamment de données pour une prévision
              (disponible&nbsp;: {forecastData?.available_months ?? 0} mois, minimum&nbsp;: {forecastData?.minimum_months ?? 12}).
            </p>
          </div>
        </Card>
      ) : null}

      {!isLoading && !error && kpis ? (
        <div className="bi-kpi-grid">
          <BiKpiCard
            label="Prévision mois prochain"
            value={formatCompactMoney(kpis.nextMonthYhat)}
            helper={forecastData?.forecast[0]?.month ? formatForecastMonthLabel(forecastData.forecast[0].month) : ""}
            icon={<LineChart size={17} />}
            strong
          />
          <BiKpiCard
            label="Total 3 prochains mois"
            value={formatCompactMoney(kpis.total3m)}
            helper="Somme des prévisions sur 3 mois"
            icon={<BarChart3 size={17} />}
          />
          <BiKpiCard
            label="Croissance YoY"
            value={kpis.yoy !== null ? `${kpis.yoy >= 0 ? "+" : ""}${formatPercent(kpis.yoy)}%` : "N/A"}
            helper="Prévision mois prochain vs même mois l'an passé"
            icon={<Activity size={17} />}
            good={kpis.yoy !== null && kpis.yoy > 0}
          />
          <BiKpiCard
            label="Précision modèle"
            value={kpis.accuracy !== null ? `${formatPercent(kpis.accuracy)}%` : "N/A"}
            helper="1−WAPE backtest 6 mois (Entreprise)"
            icon={<CheckCircle2 size={17} />}
            good={kpis.accuracy !== null && kpis.accuracy > 70}
          />
        </div>
      ) : null}

      {!isLoading && !error && !insufficientHistory && chartData.length > 0 ? (
        <Card>
          <CardTitle
            title="CA facturé & Prévisions"
            subtitle={`Historique (trait plein) + prévision ${horizon} mois (pointillé) avec intervalle de confiance.`}
          />
          <div className="chart-box chart-box-large">
            <ForecastChart data={chartData} />
          </div>
        </Card>
      ) : null}
    </PageFrame>
  );
}

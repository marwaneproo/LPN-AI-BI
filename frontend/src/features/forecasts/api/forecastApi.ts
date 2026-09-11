import type { ForecastBacktestResponse, ForecastCaResponse, ForecastOption } from "../types/forecast.types";
import { API_BASE_URL, apiFetch } from "../../../api/client";

export async function fetchForecastCa(grain: string, key: number | null, horizon: number): Promise<ForecastCaResponse> {
  const params = new URLSearchParams({ grain, horizon: String(horizon) });
  if (key !== null && !Number.isNaN(key)) params.set("key", String(key));
  const response = await apiFetch(`${API_BASE_URL}/v1/forecast/ca?${params.toString()}`);
  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    throw new Error(errorText ? `Prévisions (${response.status}): ${errorText}` : `Prévisions indisponibles (${response.status})`);
  }
  return (await response.json()) as ForecastCaResponse;
}

export async function fetchForecastBacktest(grain: string, key: number | null, holdout: number): Promise<ForecastBacktestResponse> {
  const params = new URLSearchParams({ grain, holdout: String(holdout) });
  if (key !== null && !Number.isNaN(key)) params.set("key", String(key));
  const response = await apiFetch(`${API_BASE_URL}/v1/forecast/backtest?${params.toString()}`);
  if (!response.ok) {
    return { blend_05_metrics: { wape: 0, mae: 0, rmse: 0, mape_pct: 0 } };
  }
  return (await response.json()) as ForecastBacktestResponse;
}

export async function fetchForecastOptions(grain: string): Promise<ForecastOption[]> {
  const response = await apiFetch(`${API_BASE_URL}/v1/forecast/options?grain=${encodeURIComponent(grain)}`);
  if (!response.ok) return [];
  return (await response.json()) as ForecastOption[];
}

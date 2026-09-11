export type ForecastGrain = "company" | "commercial" | "category" | "theme";

export type ForecastHistoryPoint = {
  month: string;
  actual: number;
};

export type ForecastFuturePoint = {
  month: string;
  yhat: number;
  lower: number;
  upper: number;
  prophet_yhat?: number;
  naive_lag12?: number;
};

export type ForecastCaResponse = {
  history: ForecastHistoryPoint[];
  forecast: ForecastFuturePoint[];
  model: string;
  grain: string;
  key: number | null;
  generated_at: string;
  error?: string;
  available_months?: number;
  minimum_months?: number;
};

export type ForecastBacktestResponse = {
  blend_05_metrics?: {
    wape: number;
    mae: number;
    rmse: number;
    mape_pct: number;
  };
};

export type ForecastChartPoint = {
  label: string;
  month: string;
  actual: number | null;
  yhat: number | null;
  lower: number | null;
  bandwidth: number | null;
};

export type ForecastOption = {
  key: number;
  label: string;
  complete_months: number;
  forecastable: boolean;
};

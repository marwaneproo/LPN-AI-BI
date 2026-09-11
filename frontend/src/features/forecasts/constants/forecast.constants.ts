import type { ForecastGrain } from "../types/forecast.types";

export const FORECAST_GRAIN_OPTIONS: Array<{ value: ForecastGrain; label: string }> = [
  { value: "company", label: "Entreprise" },
  { value: "commercial", label: "Commercial" },
  { value: "category", label: "Type d'article" },
  { value: "theme", label: "Thématique" },
];

export const FORECAST_HORIZON_OPTIONS = [
  { value: 3, label: "3 mois" },
  { value: 6, label: "6 mois" },
  { value: 12, label: "12 mois" },
];

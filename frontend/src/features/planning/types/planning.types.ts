export type PlanningLevel = "company" | "category";

export type PlanningTrend = "croissance" | "stabilite" | "baisse";

export type PlanningMonthPoint = {
  month: string;
  label: string;
  yhat: number;
};

export type PlanningEntityRow = {
  key: number;
  label: string;
  monthly: PlanningMonthPoint[];
  horizonTotal: number;
  nextMonth: number;
  yoyPct: number | null;
  trend: PlanningTrend;
  completeMonths: number;
};

"""ML-4b: Backtest comparison across Prophet hyperparameter configs.

Run with: uv run python scripts/ml4b_tune_comparison.py
(from D:/LPN_PROJECT/services-python or with PYTHONPATH set)
"""
from __future__ import annotations

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "services-python"))

from predictive.config import Settings
from predictive.backtest import run_backtest

SETTINGS = Settings(
    database_url="postgresql://lpn_ai_readonly:change_me_ai_readonly@localhost:5433/lpn_ai_bi",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)

CONFIGS = [
    {"label": "baseline(f3,cps.05,linear)", "yearly_fourier_order": 3,  "changepoint_prior_scale": 0.05, "growth": "linear"},
    {"label": "fourier5(f5,cps.05,linear)", "yearly_fourier_order": 5,  "changepoint_prior_scale": 0.05, "growth": "linear"},
    {"label": "fourier6(f6,cps.05,linear)", "yearly_fourier_order": 6,  "changepoint_prior_scale": 0.05, "growth": "linear"},
    {"label": "cps001  (f3,cps.01,linear)", "yearly_fourier_order": 3,  "changepoint_prior_scale": 0.01, "growth": "linear"},
    {"label": "flat    (f3,cps.05,flat  )", "yearly_fourier_order": 3,  "changepoint_prior_scale": 0.05, "growth": "flat"},
    {"label": "flat_f6 (f6,cps.05,flat  )", "yearly_fourier_order": 6,  "changepoint_prior_scale": 0.05, "growth": "flat"},
]

print("\n=== ML-4b Backtest Comparison — Company Grain ===\n")
header = f"{'Config':<34} {'6m P-WAPE':>10} {'6m N-WAPE':>10} {'6m B-WAPE':>10} | {'9m P-WAPE':>10} {'9m N-WAPE':>10} {'9m B-WAPE':>10}"
print(header)
print("-" * len(header))

results = {}
for cfg in CONFIGS:
    label = cfg["label"]
    pc = {k: v for k, v in cfg.items() if k != "label"}
    r6 = run_backtest(SETTINGS, grain="company", holdout_months=6, prophet_config=pc)
    r9 = run_backtest(SETTINGS, grain="company", holdout_months=9, prophet_config=pc)

    pw6 = r6["prophet_metrics"]["wape"]
    nw6 = r6["naive_lag12_metrics"]["wape"]
    bw6 = r6["blend_05_metrics"]["wape"]
    pw9 = r9["prophet_metrics"]["wape"]
    nw9 = r9["naive_lag12_metrics"]["wape"]
    bw9 = r9["blend_05_metrics"]["wape"]

    results[label] = {"pw6": pw6, "nw6": nw6, "bw6": bw6, "pw9": pw9, "nw9": nw9, "bw9": bw9}

    beats6 = "WIN" if pw6 < nw6 else "   "
    beats9 = "WIN" if pw9 < nw9 else "   "
    bbeats6 = "WIN" if bw6 < nw6 else "   "
    bbeats9 = "WIN" if bw9 < nw9 else "   "
    print(f"{label:<34} {pw6:>8.4f}{beats6} {nw6:>8.4f}    {bw6:>8.4f}{bbeats6} | {pw9:>8.4f}{beats9} {nw9:>8.4f}    {bw9:>8.4f}{bbeats9}")

print("\n--- naive is always the same number; shown for reference ---\n")

# Per-month breakdown for interesting configs
print("\n=== Per-month breakdown (6m holdout) ===")
for cfg in CONFIGS:
    label = cfg["label"]
    pc = {k: v for k, v in cfg.items() if k != "label"}
    r6 = run_backtest(SETTINGS, grain="company", holdout_months=6, prophet_config=pc)
    print(f"\n  {label}")
    for row in r6["per_month"]:
        m = row["month"]
        act = row["actual"] / 1e6
        p = row["prophet_yhat"] / 1e6
        n = row["naive_lag12"] / 1e6 if row["naive_lag12"] else float("nan")
        pe = row["prophet_error_abs"] / 1e6
        ne = row["naive_error_abs"] / 1e6 if row["naive_error_abs"] else float("nan")
        winner = "P" if pe < ne else "N"
        print(f"    {m}: act={act:.2f}M  p={p:.2f}M  n={n:.2f}M  ({winner} wins; perr={pe:.2f}M nerr={ne:.2f}M)")

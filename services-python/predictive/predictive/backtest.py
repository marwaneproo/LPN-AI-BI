from __future__ import annotations

"""Hold-out backtest for the Prophet monthly CA forecast.

Design: train on the first (N - holdout_months) complete months, forecast the tail,
compare to actuals.

Metrics:
  - WAPE  (weighted absolute percentage error = sum|e| / sum|y|) — HEADLINE
    Robust for strongly seasonal data with 10-20x range.  Low-season months
    (troughs ~2M) dominate MAPE but are down-weighted in WAPE.
  - MAE   (absolute, in monetary units)
  - RMSE  (absolute, in monetary units)
  - MAPE  (reported for completeness; annotated as inflated by low-season months)

Baselines compared per run:
  - Prophet (config via prophet_config dict, defaults to production config in forecast.py)
  - Naive seasonal: same-month-last-year (lag-12)
  - Blend 50/50: 0.5*Prophet_yhat + 0.5*naive_lag12 (always computed alongside)

Architecture rule: reuses _fit_prophet from forecast.py — no modeling duplication.
Backtest never writes to the in-memory model cache (separate call, separate train window).
"""

import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from predictive.config import Settings
from predictive.data import Grain, load_monthly_ca
from predictive.forecast import MIN_HISTORY_MONTHS, _PROD_CPS, _PROD_FOURIER_ORDER, _PROD_GROWTH, _fit_prophet

logging.getLogger("prophet").setLevel(logging.WARNING)
logging.getLogger("cmdstanpy").setLevel(logging.WARNING)

# Need at least this many training months so Prophet sees >= 1 full seasonal cycle
_MIN_TRAIN_MONTHS = MIN_HISTORY_MONTHS  # 12


def _metrics(actual: np.ndarray, pred: np.ndarray) -> dict[str, float]:
    """Return WAPE, MAE, RMSE, MAPE for matched actual/predicted arrays."""
    err = actual - pred
    abs_err = np.abs(err)
    total_actual = actual.sum()
    wape = float(abs_err.sum() / total_actual) if total_actual > 0 else float("nan")
    mae = float(abs_err.mean())
    rmse = float(np.sqrt((err ** 2).mean()))
    nz = actual != 0
    mape = float(np.mean(abs_err[nz] / actual[nz])) * 100 if nz.any() else float("nan")
    return {
        "wape": round(wape, 6),
        "mae": round(mae, 2),
        "rmse": round(rmse, 2),
        "mape_pct": round(mape, 2),
    }


def run_backtest(
    settings: Settings,
    *,
    grain: Grain = "company",
    key: int | None = None,
    holdout_months: int = 6,
    prophet_config: dict | None = None,
) -> dict:
    """Run a hold-out backtest and return metrics + per-month comparison.

    prophet_config is an optional dict of Prophet hyperparameters to override the
    production defaults.  Supported keys:
      - yearly_fourier_order (int)
      - changepoint_prior_scale (float)
      - growth (str: 'linear' | 'flat')
      - seasonality_mode (str)

    Always returns blend_05_metrics: WAPE/MAE/RMSE for the 0.5*Prophet+0.5*naive blend.

    Returns a backtest result dict, or an insufficient_history dict if not
    enough history exists to support the requested holdout window.
    """
    cfg: dict[str, Any] = {
        "seasonality_mode": "multiplicative",
        "yearly_fourier_order": _PROD_FOURIER_ORDER,
        "changepoint_prior_scale": _PROD_CPS,
        "growth": _PROD_GROWTH,
        **(prophet_config or {}),
    }

    df = load_monthly_ca(settings, grain=grain, key=key)
    n = len(df)
    min_needed = _MIN_TRAIN_MONTHS + holdout_months

    if n < min_needed:
        return {
            "error": "insufficient_history",
            "available_months": n,
            "minimum_months": min_needed,
            "grain": grain,
            "key": key,
        }

    train_df = df.iloc[: n - holdout_months].reset_index(drop=True)
    test_df = df.iloc[n - holdout_months :].reset_index(drop=True)
    test_months = pd.to_datetime(test_df["month"])

    # -- Prophet predictions (fresh fit, never touches the module cache) --
    model, _ = _fit_prophet(
        train_df,
        cfg["seasonality_mode"],
        cfg["yearly_fourier_order"],
        cfg["changepoint_prior_scale"],
        cfg["growth"],
    )
    future = model.make_future_dataframe(periods=holdout_months, freq="MS")
    raw = model.predict(future)
    fcast = raw.tail(holdout_months).reset_index(drop=True)

    prophet_yhat = np.array([max(0.0, float(r["yhat"])) for _, r in fcast.iterrows()])
    prophet_lower = np.array([max(0.0, float(r["yhat_lower"])) for _, r in fcast.iterrows()])
    prophet_upper = np.array([max(0.0, float(r["yhat_upper"])) for _, r in fcast.iterrows()])

    actual = test_df["ca_facture"].values.astype(float)

    # -- Naive lag-12 baseline --
    train_by_month: dict[pd.Timestamp, float] = {
        pd.Timestamp(row["month"]).normalize(): float(row["ca_facture"])
        for _, row in train_df.iterrows()
    }
    naive = np.array([
        train_by_month.get((m - pd.DateOffset(months=12)).normalize(), float("nan"))
        for m in test_months
    ])

    # -- Per-month comparison table --
    per_month = []
    for i in range(holdout_months):
        n_val = naive[i]
        per_month.append({
            "month": test_months.iloc[i].date().isoformat(),
            "actual": round(float(actual[i]), 2),
            "prophet_yhat": round(float(prophet_yhat[i]), 2),
            "prophet_lower": round(float(prophet_lower[i]), 2),
            "prophet_upper": round(float(prophet_upper[i]), 2),
            "naive_lag12": round(float(n_val), 2) if not np.isnan(n_val) else None,
            "prophet_error_abs": round(abs(float(actual[i] - prophet_yhat[i])), 2),
            "naive_error_abs": round(abs(float(actual[i] - n_val)), 2) if not np.isnan(n_val) else None,
        })

    # -- Metrics --
    prophet_m = _metrics(actual, prophet_yhat)

    valid = ~np.isnan(naive)
    naive_m = _metrics(actual[valid], naive[valid]) if valid.any() else {
        "wape": None, "mae": None, "rmse": None, "mape_pct": None
    }

    # -- Blend 50/50 --
    if valid.any():
        blend_yhat = np.where(valid, 0.5 * prophet_yhat + 0.5 * naive, prophet_yhat)
        blend_m = _metrics(actual[valid], blend_yhat[valid])
    else:
        blend_m = {"wape": None, "mae": None, "rmse": None, "mape_pct": None}

    prophet_beats_naive = (
        naive_m["wape"] is not None and prophet_m["wape"] < naive_m["wape"]
    )

    return {
        "grain": grain,
        "key": key,
        "holdout_months": holdout_months,
        "train_months": len(train_df),
        "test_period": {
            "start": test_months.iloc[0].date().isoformat(),
            "end": test_months.iloc[-1].date().isoformat(),
        },
        "prophet_config_used": {
            "yearly_fourier_order": cfg["yearly_fourier_order"],
            "changepoint_prior_scale": cfg["changepoint_prior_scale"],
            "growth": cfg["growth"],
            "seasonality_mode": cfg["seasonality_mode"],
        },
        "prophet_metrics": prophet_m,
        "naive_lag12_metrics": naive_m,
        "blend_05_metrics": blend_m,
        "prophet_beats_naive_wape": prophet_beats_naive,
        "caveat": (
            "WAPE is the headline metric (robust for 10-20x seasonal range). "
            "MAPE is inflated by low-season months (~2M troughs); treat it as informational only. "
            f"Only 2 yearly cycles in history; backtest is limited to {holdout_months}-month folds."
        ),
        "per_month": per_month,
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }


def write_backtest_report(result: dict, output_dir: str | Path) -> str:
    """Write per-month backtest result to a dated CSV and return the file path.

    Mirrors the eval reports convention: one CSV per run, dated filename.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    date_str = datetime.now().strftime("%Y%m%d")
    grain = result.get("grain", "unknown")
    key = result.get("key")
    key_part = f"_{key}" if key is not None else ""
    holdout = result.get("holdout_months", "")
    filename = output_dir / f"forecast-backtest-{date_str}-{grain}{key_part}-h{holdout}.csv"

    rows = result.get("per_month", [])
    if not rows:
        return ""

    pm = result.get("prophet_metrics", {})
    nm = result.get("naive_lag12_metrics", {})
    bm = result.get("blend_05_metrics", {})
    cfg = result.get("prophet_config_used", {})

    enriched = [
        {
            "grain": result.get("grain"),
            "key": result.get("key"),
            "holdout_months": result.get("holdout_months"),
            "train_months": result.get("train_months"),
            "test_start": result.get("test_period", {}).get("start"),
            "test_end": result.get("test_period", {}).get("end"),
            "prophet_fourier_order": cfg.get("yearly_fourier_order"),
            "prophet_cps": cfg.get("changepoint_prior_scale"),
            "prophet_growth": cfg.get("growth"),
            "prophet_wape": pm.get("wape"),
            "prophet_mae": pm.get("mae"),
            "prophet_rmse": pm.get("rmse"),
            "prophet_mape_pct": pm.get("mape_pct"),
            "naive_wape": nm.get("wape"),
            "naive_mae": nm.get("mae"),
            "naive_rmse": nm.get("rmse"),
            "naive_mape_pct": nm.get("mape_pct"),
            "blend_wape": bm.get("wape"),
            "blend_mae": bm.get("mae"),
            "prophet_beats_naive_wape": result.get("prophet_beats_naive_wape"),
            **row,
        }
        for row in rows
    ]

    pd.DataFrame(enriched).to_csv(filename, index=False, encoding="utf-8")
    return str(filename)

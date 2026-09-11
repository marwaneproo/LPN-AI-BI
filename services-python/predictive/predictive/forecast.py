from __future__ import annotations

"""Monthly CA forecast using Prophet, blended with naive lag-12.

Architecture rules:
- Deterministic ML only — no LLM, no Ollama.
- Reads from PostgreSQL via data.load_monthly_ca; never touches Oracle or Excel at request time.
- Partial months (is_partial=TRUE) are excluded by data.load_monthly_ca before fitting.
- Models are cached in-memory keyed by (grain, key); cache is valid until service restart or
  explicit clear (re-import of facts triggers a service restart in production).
- CA is non-negative: yhat and yhat_lower are clamped to 0.

Production Prophet config (chosen via ML-4b backtest comparison, see CONTEXT_JOURNAL.md):
  yearly_fourier_order=5, changepoint_prior_scale=0.05, growth='linear',
  seasonality_mode='multiplicative'.
  Forecast = 0.5*Prophet + 0.5*naive_lag12 (blend beats naive WAPE 0.2220 vs 0.2381 on 6m).
"""

import logging
from datetime import datetime, timezone
from typing import Any

import pandas as pd

from predictive.config import Settings
from predictive.data import Grain, load_monthly_ca

# Suppress Prophet/cmdstanpy verbosity
logging.getLogger("prophet").setLevel(logging.WARNING)
logging.getLogger("cmdstanpy").setLevel(logging.WARNING)

MIN_HISTORY_MONTHS: int = 12

# Production Prophet hyperparameters (set after ML-4b backtest comparison)
_PROD_FOURIER_ORDER: int = 5
_PROD_CPS: float = 0.05
_PROD_GROWTH: str = "linear"

# In-memory model cache: (grain, key) -> (fitted Prophet model, training DataFrame)
_model_cache: dict[tuple[str, int | None], Any] = {}


def clear_forecast_cache() -> None:
    """Clear all cached Prophet models (called in tests or after fact re-import)."""
    _model_cache.clear()


def build_forecast(
    settings: Settings,
    *,
    grain: Grain = "company",
    key: int | None = None,
    horizon: int = 6,
    seasonality_mode: str = "multiplicative",
    yearly_fourier_order: int = _PROD_FOURIER_ORDER,
    changepoint_prior_scale: float = _PROD_CPS,
    growth: str = _PROD_GROWTH,
) -> dict:
    """Fit (or retrieve) a Prophet model, blend with naive lag-12, and return the forecast.

    Returns either:
      - A normal forecast dict: history[], forecast[], model, grain, key, generated_at
        Each forecast row has yhat (blend), lower, upper, prophet_yhat, naive_lag12.
        model = "prophet_naive_blend_05" when naive is available, "prophet" otherwise.
      - An insufficient_history dict when < MIN_HISTORY_MONTHS complete months exist.

    yhat and yhat_lower are clamped at 0 (CA is non-negative).
    """
    df = load_monthly_ca(settings, grain=grain, key=key)

    if len(df) < MIN_HISTORY_MONTHS:
        return {
            "error": "insufficient_history",
            "available_months": len(df),
            "minimum_months": MIN_HISTORY_MONTHS,
            "grain": grain,
            "key": key,
        }

    cache_key = (grain, key)
    if cache_key not in _model_cache:
        _model_cache[cache_key] = _fit_prophet(
            df, seasonality_mode, yearly_fourier_order, changepoint_prior_scale, growth
        )

    model, train_df = _model_cache[cache_key]

    future = model.make_future_dataframe(periods=horizon, freq="MS")
    raw = model.predict(future)

    history = [
        {"month": row["ds"].date().isoformat(), "actual": float(row["y"])}
        for _, row in train_df.iterrows()
    ]

    # Naive lookup: month (normalized Timestamp) -> CA value from training data
    naive_lookup: dict[pd.Timestamp, float] = {
        pd.Timestamp(row["ds"]).normalize(): float(row["y"])
        for _, row in train_df.iterrows()
    }

    forecast_rows = raw.tail(horizon)
    forecast = []
    any_blend = False

    for _, row in forecast_rows.iterrows():
        future_month = pd.Timestamp(row["ds"]).normalize()
        lag12_month = (future_month - pd.DateOffset(months=12)).normalize()
        naive_val = naive_lookup.get(lag12_month)

        p_yhat = max(0.0, float(row["yhat"]))
        p_lower = max(0.0, float(row["yhat_lower"]))
        p_upper = max(0.0, float(row["yhat_upper"]))

        if naive_val is not None:
            yhat = round(0.5 * p_yhat + 0.5 * naive_val, 2)
            lower = round(max(0.0, 0.5 * p_lower + 0.5 * naive_val), 2)
            upper = round(0.5 * p_upper + 0.5 * naive_val, 2)
            any_blend = True
        else:
            yhat = round(p_yhat, 2)
            lower = round(p_lower, 2)
            upper = round(p_upper, 2)
            naive_val = None

        forecast.append({
            "month": future_month.date().isoformat(),
            "yhat": yhat,
            "lower": lower,
            "upper": upper,
            "prophet_yhat": round(p_yhat, 2),
            "naive_lag12": round(float(naive_val), 2) if naive_val is not None else None,
        })

    return {
        "history": history,
        "forecast": forecast,
        "model": "prophet_naive_blend_05" if any_blend else "prophet",
        "grain": grain,
        "key": key,
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }


def _fit_prophet(
    df: pd.DataFrame,
    seasonality_mode: str,
    yearly_fourier_order: int,
    changepoint_prior_scale: float = 0.05,
    growth: str = "linear",
) -> tuple[Any, pd.DataFrame]:
    """Fit a Prophet model on the supplied monthly CA DataFrame.

    Prophet expects columns ds (datetime) and y (float).
    Returns (fitted_model, prophet_training_df).
    """
    from prophet import Prophet  # late import keeps startup fast when prophet not yet warm

    train_df = df.rename(columns={"month": "ds", "ca_facture": "y"}).copy()
    train_df["ds"] = pd.to_datetime(train_df["ds"])

    model = Prophet(
        yearly_seasonality=yearly_fourier_order,
        weekly_seasonality=False,
        daily_seasonality=False,
        seasonality_mode=seasonality_mode,
        changepoint_prior_scale=changepoint_prior_scale,
        growth=growth,
    )
    model.fit(train_df)
    return model, train_df

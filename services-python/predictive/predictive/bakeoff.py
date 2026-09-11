from __future__ import annotations

"""Model Bake-off: compare 5 forecasting models on an identical chronological train/test split.

Split protocol:
  Primary: chronological 70/30 (round to whole months; always chronological, never shuffled).
  Rolling-origin CV: expanding window, _CV_HORIZON-month forecast per fold, starting from
  _CV_MIN_TRAIN months. With ~24 total months this yields ~4 folds — reported honestly.

Models (each is a standalone wrapper):
  1. seasonal_naive  — lag-12 baseline; reuses backtest.py's per-month lookup logic.
  2. ets             — statsmodels ExponentialSmoothing; tries no-trend+mul and
                       damped-add+mul variants; keeps the one with lower AIC.
  3. sarima          — SARIMAX(1,1,1)(1,1,0,12); wrapped in try/except; returns NaN on failure.
  4. prophet_tuned   — reuses _fit_prophet with production config (multiplicative, fourier=5).
  5. lightgbm        — LGBMRegressor with lag_1, lag_12, month_of_year; recursive forecast.
                       Expected to be weak with only ~17 training months (~5 feature rows).

Reuses:
  _metrics   from backtest.py  (WAPE, MAE, RMSE, MAPE)
  _fit_prophet from forecast.py (Prophet production config, no duplication)

No production endpoint or model is modified by this module.
"""

import logging
import warnings
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd

from predictive.backtest import _metrics
from predictive.config import Settings
from predictive.data import Grain, load_monthly_ca
from predictive.forecast import _PROD_CPS, _PROD_FOURIER_ORDER, _PROD_GROWTH, _fit_prophet

logging.getLogger("prophet").setLevel(logging.WARNING)
logging.getLogger("cmdstanpy").setLevel(logging.WARNING)

_TRAIN_FRAC = 0.70     # 70/30 split fraction
_CV_HORIZON = 3        # months forecast per CV fold
_CV_MIN_TRAIN = 12     # minimum train months before first CV fold

# -----------------------------------------------------------------------
# Model wrappers — uniform interface:
#   (train_df: DataFrame[month, ca_facture], horizon: int) -> np.ndarray
# Returns exactly `horizon` non-negative predictions; NaN where infeasible.
# -----------------------------------------------------------------------


def _predict_seasonal_naive(train_df: pd.DataFrame, horizon: int) -> np.ndarray:
    """Predict each future month as the same-month value 12 steps earlier (lag-12)."""
    lookup: dict[pd.Timestamp, float] = {
        pd.Timestamp(row["month"]).normalize(): float(row["ca_facture"])
        for _, row in train_df.iterrows()
    }
    last_month = pd.Timestamp(train_df["month"].iloc[-1]).normalize()
    preds = []
    for h in range(1, horizon + 1):
        future = (last_month + pd.DateOffset(months=h)).normalize()
        lag12 = (future - pd.DateOffset(months=12)).normalize()
        val = lookup.get(lag12)
        preds.append(max(0.0, float(val)) if val is not None else float("nan"))
    return np.array(preds)


def _predict_ets(train_df: pd.DataFrame, horizon: int) -> np.ndarray:
    """ETS: tries ETS(A,N,M) [no trend] and ETS(A,Ad,M) [damped-add trend].
    Keeps the variant with lower AIC.

    statsmodels "estimated" init requires ≥2 full seasonal cycles (24 months).
    For 12–23 months, uses "known" init with seasonal factors derived from the
    first 12 observations — this lets ETS work on the typical 17-month train split.
    Returns NaN array when < 12 months or on total failure.
    """
    from statsmodels.tsa.holtwinters import ExponentialSmoothing

    y = train_df["ca_facture"].values.astype(float)
    y = np.where(y > 0, y, 1.0)

    if len(y) < 12:
        return np.full(horizon, float("nan"))

    if len(y) >= 24:
        # Enough data: let statsmodels estimate all initial values
        configs = [
            (None, False, {"initialization_method": "estimated"}),
            ("add", True, {"initialization_method": "estimated"}),
        ]
    else:
        # Short series: derive initial seasonal factors from first 12 months manually
        y12 = y[:12]
        level0 = float(y12.mean())
        seas_init = y12 / level0 if level0 > 0 else np.ones(12)
        known_kw = {
            "initialization_method": "known",
            "initial_level": level0,
            "initial_seasonal": seas_init,
        }
        # Only no-trend variant: ETS(A,N,M); "known" init for trend would need initial_trend
        configs = [(None, False, known_kw)]

    best_model = None
    best_aic = float("inf")

    for trend, damped, init_kwargs in configs:
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore")
                fit = ExponentialSmoothing(
                    y,
                    trend=trend,
                    damped_trend=damped if trend is not None else False,
                    seasonal="mul",
                    seasonal_periods=12,
                    **init_kwargs,
                ).fit(optimized=True)
            aic = fit.aic
            if np.isfinite(aic) and aic < best_aic:
                best_aic = aic
                best_model = fit
        except Exception:
            continue

    if best_model is None:
        return np.full(horizon, float("nan"))

    preds = best_model.forecast(horizon)
    return np.maximum(0.0, np.asarray(preds, dtype=float))


def _predict_sarima(train_df: pd.DataFrame, horizon: int) -> np.ndarray:
    """SARIMAX(1,1,1)(1,1,0,12). Returns NaN array on convergence failure.

    With only ~17 training months this order requires seasonal differencing
    (reducing effective points to ~5), so failure is not unusual and is handled.
    """
    from statsmodels.tsa.statespace.sarimax import SARIMAX

    y = train_df["ca_facture"].values.astype(float)
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            fit = SARIMAX(
                y,
                order=(1, 1, 1),
                seasonal_order=(1, 1, 0, 12),
                enforce_stationarity=False,
                enforce_invertibility=False,
            ).fit(disp=False, maxiter=200)
        preds = np.asarray(fit.forecast(steps=horizon), dtype=float)
        return np.maximum(0.0, preds)
    except Exception:
        return np.full(horizon, float("nan"))


def _predict_prophet_tuned(train_df: pd.DataFrame, horizon: int) -> np.ndarray:
    """Prophet with production config: multiplicative seasonality, yearly_fourier_order=5."""
    model, _ = _fit_prophet(
        train_df,
        seasonality_mode="multiplicative",
        yearly_fourier_order=_PROD_FOURIER_ORDER,
        changepoint_prior_scale=_PROD_CPS,
        growth=_PROD_GROWTH,
    )
    future = model.make_future_dataframe(periods=horizon, freq="MS")
    raw = model.predict(future)
    preds = raw.tail(horizon)["yhat"].values.astype(float)
    return np.maximum(0.0, preds)


def _predict_lightgbm(train_df: pd.DataFrame, horizon: int) -> np.ndarray:
    """LightGBM with lag_1, lag_12, month_of_year features; recursive multi-step forecast.

    With ~17 training months, only ~5 feature rows are available (after lag-12 warmup).
    Expected to underperform statistical models — this is a reportable finding.
    Returns NaN if fewer than 3 feature rows can be built.
    """
    import lightgbm as lgb

    y = train_df["ca_facture"].values.astype(float)
    months = pd.to_datetime(train_df["month"])

    # Build feature matrix: start at index 12 so lag-12 is always available
    X, targets = [], []
    for i in range(12, len(y)):
        X.append([y[i - 1], y[i - 12], int(months.iloc[i].month)])
        targets.append(y[i])

    if len(X) < 3:
        return np.full(horizon, float("nan"))

    model = lgb.LGBMRegressor(
        n_estimators=100,
        num_leaves=4,
        learning_rate=0.1,
        min_child_samples=1,
        verbose=-1,
    )
    model.fit(np.array(X), np.array(targets))

    # Recursive forecast: extend y_ext with each prediction
    y_ext = list(y)
    last_month = months.iloc[-1]
    preds = []
    for h in range(1, horizon + 1):
        future_month = last_month + pd.DateOffset(months=h)
        lag1 = y_ext[-1]
        lag12 = y_ext[-12] if len(y_ext) >= 12 else float("nan")
        if not np.isfinite(lag12):
            preds.append(float("nan"))
            y_ext.append(float("nan"))
            continue
        feat = np.array([[lag1, lag12, int(future_month.month)]])
        p = max(0.0, float(model.predict(feat)[0]))
        preds.append(p)
        y_ext.append(p)

    return np.array(preds)


# -----------------------------------------------------------------------
# Ordered list of (name, wrapper_fn) — rank order does not matter here
# -----------------------------------------------------------------------

_MODEL_FNS: dict[str, object] = {
    "seasonal_naive": _predict_seasonal_naive,
    "ets": _predict_ets,
    "sarima": _predict_sarima,
    "prophet_tuned": _predict_prophet_tuned,
    "lightgbm": _predict_lightgbm,
}


# -----------------------------------------------------------------------
# Main bake-off runner
# -----------------------------------------------------------------------


def run_bakeoff(settings: Settings, *, grain: Grain = "company") -> dict:
    """Run all 5 models on an identical chronological 70/30 split + rolling CV.

    Returns:
      grain, split (train/test metadata), models (per-model metrics + per_month table),
      ranked_by_wape (list sorted ascending), winner, cv_caveat, generated_at.

    The 70/30 split is the primary comparison metric. CV results are informational.
    """
    df = load_monthly_ca(settings, grain=grain, key=None)
    df = df.sort_values("month").reset_index(drop=True)
    n = len(df)

    # Chronological 70/30 split
    train_n = max(_CV_MIN_TRAIN, round(n * _TRAIN_FRAC))
    test_n = n - train_n

    if test_n < 1:
        raise ValueError(f"Not enough data for a 70/30 split: only {n} months available.")

    train_df = df.iloc[:train_n].reset_index(drop=True)
    test_df = df.iloc[train_n:].reset_index(drop=True)
    actual = test_df["ca_facture"].values.astype(float)

    # Rolling-origin CV folds: expanding window, _CV_HORIZON-month forecast per fold
    cv_folds: list[tuple[pd.DataFrame, np.ndarray]] = []
    fold_start = _CV_MIN_TRAIN
    while fold_start + _CV_HORIZON <= n:
        cv_train = df.iloc[:fold_start].reset_index(drop=True)
        cv_actual = df.iloc[fold_start: fold_start + _CV_HORIZON]["ca_facture"].values.astype(float)
        cv_folds.append((cv_train, cv_actual))
        fold_start += _CV_HORIZON

    models_out: dict[str, dict] = {}

    for name, fn in _MODEL_FNS.items():
        # --- 70/30 split ---
        try:
            preds = fn(train_df, test_n)  # type: ignore[call-arg]
        except Exception:
            preds = np.full(test_n, float("nan"))

        # Ensure correct length
        if len(preds) != test_n:
            preds = np.resize(preds, test_n)

        valid = np.isfinite(actual) & np.isfinite(preds)
        if valid.any():
            split_m = _metrics(actual[valid], preds[valid])
        else:
            split_m = {"wape": float("nan"), "mae": float("nan"),
                       "rmse": float("nan"), "mape_pct": float("nan")}

        # --- Rolling CV: average WAPE across folds ---
        fold_wapes: list[float] = []
        for cv_train_df, cv_actual in cv_folds:
            try:
                cv_preds = fn(cv_train_df, _CV_HORIZON)  # type: ignore[call-arg]
                cv_valid = np.isfinite(cv_actual) & np.isfinite(cv_preds)
                if cv_valid.any():
                    m = _metrics(cv_actual[cv_valid], cv_preds[cv_valid])
                    if np.isfinite(m["wape"]):
                        fold_wapes.append(float(m["wape"]))
            except Exception:
                pass

        cv_wape = round(float(np.mean(fold_wapes)), 6) if fold_wapes else float("nan")

        # --- Per-month comparison table ---
        per_month = []
        for i, row in test_df.iterrows():
            pred_val = float(preds[i]) if i < len(preds) else float("nan")
            act_val = float(row["ca_facture"])
            per_month.append({
                "month": pd.Timestamp(row["month"]).date().isoformat(),
                "actual": round(act_val, 2),
                "predicted": round(pred_val, 2) if np.isfinite(pred_val) else None,
                "abs_error": round(abs(act_val - pred_val), 2) if np.isfinite(pred_val) else None,
            })

        models_out[name] = {
            "split_metrics": split_m,
            "cv_wape_mean": cv_wape,
            "cv_folds_used": len(fold_wapes),
            "per_month": per_month,
        }

    # Rank by 70/30 WAPE ascending (NaN last)
    def _sort_key(model_name: str) -> float:
        w = models_out[model_name]["split_metrics"]["wape"]
        return float(w) if np.isfinite(w) else float("inf")

    ranked_names = sorted(_MODEL_FNS.keys(), key=_sort_key)
    winner = ranked_names[0]

    return {
        "grain": grain,
        "split": {
            "train_months": train_n,
            "test_months": test_n,
            "test_period": {
                "start": pd.Timestamp(test_df["month"].iloc[0]).date().isoformat(),
                "end": pd.Timestamp(test_df["month"].iloc[-1]).date().isoformat(),
            },
        },
        "models": models_out,
        "ranked_by_wape": [
            {
                "rank": i + 1,
                "model": nm,
                "wape": models_out[nm]["split_metrics"]["wape"],
                "mae": models_out[nm]["split_metrics"]["mae"],
                "rmse": models_out[nm]["split_metrics"]["rmse"],
                "mape_pct": models_out[nm]["split_metrics"]["mape_pct"],
                "cv_wape_mean": models_out[nm]["cv_wape_mean"],
                "cv_folds_used": models_out[nm]["cv_folds_used"],
            }
            for i, nm in enumerate(ranked_names)
        ],
        "winner": winner,
        "cv_caveat": (
            f"Rolling-origin CV: {len(cv_folds)} folds total "
            f"(expanding window, {_CV_HORIZON}-month horizon, min {_CV_MIN_TRAIN} train months). "
            f"With only ~2 yearly cycles ({n} complete months) the CV has limited "
            f"statistical power. The 70/30 primary split is the main comparison."
        ),
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }


def write_bakeoff_report(result: dict, output_dir: str | Path) -> str:
    """Write bake-off summary to a dated CSV, one row per model. Returns file path."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    date_str = datetime.now().strftime("%Y%m%d")
    filename = output_dir / f"model-bakeoff-{date_str}.csv"

    rows = [
        {
            "rank": entry["rank"],
            "model": entry["model"],
            "winner": entry["model"] == result["winner"],
            "grain": result.get("grain"),
            "train_months": result["split"]["train_months"],
            "test_months": result["split"]["test_months"],
            "test_start": result["split"]["test_period"]["start"],
            "test_end": result["split"]["test_period"]["end"],
            "wape": entry["wape"],
            "mae": entry["mae"],
            "rmse": entry["rmse"],
            "mape_pct": entry["mape_pct"],
            "cv_wape_mean": entry["cv_wape_mean"],
            "cv_folds_used": entry["cv_folds_used"],
            "generated_at": result.get("generated_at"),
        }
        for entry in result["ranked_by_wape"]
    ]

    pd.DataFrame(rows).to_csv(filename, index=False, encoding="utf-8")
    return str(filename)

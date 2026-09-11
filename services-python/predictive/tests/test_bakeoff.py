from __future__ import annotations

"""Unit tests for the model bake-off (test_bakeoff.py).

All tests use a synthetic 24-month monthly series with a clear seasonal pattern.
The DB is mocked — no live PostgreSQL needed.
"""

import math
import tempfile
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from predictive.bakeoff import (
    _predict_ets,
    _predict_lightgbm,
    _predict_prophet_tuned,
    _predict_sarima,
    _predict_seasonal_naive,
    run_bakeoff,
    write_bakeoff_report,
)
from predictive.config import Settings
from predictive.main import app

client = TestClient(app)

_MOCK_SETTINGS = Settings(
    database_url="postgresql://x:x@localhost:5433/lpn_ai_bi",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)

# Seasonal pattern representative of LPN: Jul–Sep peak, Dec–Jan trough
_SEASONAL_MUL = [20, 45, 50, 40, 25, 15, 12, 10, 8, 3, 2, 5]


def _make_synthetic_df(n_months: int = 24) -> pd.DataFrame:
    """24-month series with a repeating 12-month seasonal cycle (all values positive)."""
    months = pd.date_range("2024-06-01", periods=n_months, freq="MS")
    y = [_SEASONAL_MUL[i % 12] * 1_000_000.0 for i in range(n_months)]
    return pd.DataFrame({"month": months, "ca_facture": y})


_SYNTH_DF = _make_synthetic_df(24)
_TRAIN_DF = _SYNTH_DF.iloc[:17].reset_index(drop=True)  # 70% of 24 months
_TEST_DF = _SYNTH_DF.iloc[17:].reset_index(drop=True)   # 30% = 7 months


def _settings_patch():
    return patch("predictive.main.get_settings", return_value=_MOCK_SETTINGS)


# ---------------------------------------------------------------------------
# Individual wrapper tests (no DB, direct call)
# ---------------------------------------------------------------------------

def test_seasonal_naive_shape() -> None:
    """seasonal_naive returns exactly horizon non-NaN predictions."""
    preds = _predict_seasonal_naive(_TRAIN_DF, 7)
    assert len(preds) == 7
    assert all(np.isfinite(preds)), f"NaN in naive predictions: {preds}"
    assert all(p >= 0 for p in preds)


def test_seasonal_naive_values_match_lag12() -> None:
    """Each prediction equals the value from 12 months earlier in training data."""
    preds = _predict_seasonal_naive(_TRAIN_DF, 7)
    # First future month is month 18 of series (0-indexed); lag-12 is month 6
    for i, p in enumerate(preds):
        expected_idx = 17 + i - 12  # index in _TRAIN_DF
        if 0 <= expected_idx < len(_TRAIN_DF):
            expected = float(_TRAIN_DF["ca_facture"].iloc[expected_idx])
            assert abs(p - expected) < 1.0, f"step {i}: expected {expected}, got {p}"


def test_ets_shape() -> None:
    """ETS returns horizon predictions, all finite."""
    preds = _predict_ets(_TRAIN_DF, 7)
    assert len(preds) == 7
    # ETS should converge on a perfectly seasonal series
    assert all(np.isfinite(preds)), f"NaN in ETS predictions: {preds}"
    assert all(p >= 0 for p in preds)


def test_sarima_shape_or_nan() -> None:
    """SARIMA returns array of correct length; may be NaN if SARIMAX fails to converge."""
    preds = _predict_sarima(_TRAIN_DF, 7)
    assert len(preds) == 7
    assert all(p >= 0 or np.isnan(p) for p in preds)


def test_prophet_tuned_shape() -> None:
    """prophet_tuned returns exactly horizon finite non-negative predictions."""
    preds = _predict_prophet_tuned(_TRAIN_DF, 7)
    assert len(preds) == 7
    assert all(np.isfinite(preds)), f"NaN in Prophet predictions: {preds}"
    assert all(p >= 0 for p in preds)


def test_lightgbm_shape_or_nan() -> None:
    """LightGBM returns array of correct length (may have NaN for too-short series)."""
    preds = _predict_lightgbm(_TRAIN_DF, 7)
    assert len(preds) == 7
    assert all(p >= 0 or np.isnan(p) for p in preds)


def test_lightgbm_too_short_returns_nan() -> None:
    """LightGBM returns NaN array when training series is < 13 months."""
    short_df = _TRAIN_DF.iloc[:10].reset_index(drop=True)
    preds = _predict_lightgbm(short_df, 3)
    assert len(preds) == 3
    assert all(np.isnan(p) for p in preds)


# ---------------------------------------------------------------------------
# run_bakeoff tests (DB mocked via load_monthly_ca patch)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def bakeoff_result():
    """Run bakeoff once on synthetic data; share result across tests in this module."""
    with patch("predictive.bakeoff.load_monthly_ca", return_value=_SYNTH_DF):
        return run_bakeoff(_MOCK_SETTINGS, grain="company")


def test_bakeoff_returns_all_five_models(bakeoff_result) -> None:
    """All 5 model names are present in the result."""
    expected = {"seasonal_naive", "ets", "sarima", "prophet_tuned", "lightgbm"}
    assert set(bakeoff_result["models"].keys()) == expected


def test_bakeoff_seasonal_naive_has_finite_wape(bakeoff_result) -> None:
    """seasonal_naive baseline always produces finite WAPE (lag-12 well defined for 2 cycles)."""
    wape = bakeoff_result["models"]["seasonal_naive"]["split_metrics"]["wape"]
    assert math.isfinite(wape), f"seasonal_naive WAPE is not finite: {wape}"
    assert 0 <= wape <= 10, f"WAPE out of sane range: {wape}"


def test_bakeoff_all_well_fitted_models_have_finite_wape(bakeoff_result) -> None:
    """ETS, Prophet, and seasonal_naive all return finite WAPE. SARIMA/LGB may be NaN."""
    for model in ("seasonal_naive", "ets", "prophet_tuned"):
        wape = bakeoff_result["models"][model]["split_metrics"]["wape"]
        assert math.isfinite(wape), f"{model} WAPE is not finite: {wape}"


def test_bakeoff_names_a_winner(bakeoff_result) -> None:
    """run_bakeoff names exactly one winner that is a known model."""
    winner = bakeoff_result["winner"]
    assert winner in bakeoff_result["models"], f"winner '{winner}' not in models dict"


def test_bakeoff_ranked_list_sorted_ascending(bakeoff_result) -> None:
    """ranked_by_wape list is sorted ascending (WAPE, NaN last)."""
    ranked = bakeoff_result["ranked_by_wape"]
    assert len(ranked) == 5
    wapes = [r["wape"] for r in ranked]
    finite = [w for w in wapes if math.isfinite(w)]
    assert finite == sorted(finite), f"Ranked WAPEs not ascending: {wapes}"


def test_bakeoff_split_is_70_30(bakeoff_result) -> None:
    """70/30 split produces correct train/test counts on 24-month series."""
    assert bakeoff_result["split"]["train_months"] == 17
    assert bakeoff_result["split"]["test_months"] == 7


def test_bakeoff_cv_caveat_present(bakeoff_result) -> None:
    """cv_caveat key exists and mentions the fold count."""
    assert "cv_caveat" in bakeoff_result
    assert "folds" in bakeoff_result["cv_caveat"]


def test_bakeoff_per_month_table_length(bakeoff_result) -> None:
    """Each model's per_month table has exactly test_months rows."""
    test_n = bakeoff_result["split"]["test_months"]
    for name, data in bakeoff_result["models"].items():
        assert len(data["per_month"]) == test_n, f"{name}: expected {test_n} rows"


# ---------------------------------------------------------------------------
# /v1/forecast/bakeoff endpoint test
# ---------------------------------------------------------------------------

def test_bakeoff_endpoint_200() -> None:
    """GET /v1/forecast/bakeoff?grain=company returns 200 with ranked_by_wape."""
    with patch("predictive.bakeoff.load_monthly_ca", return_value=_SYNTH_DF):
        with _settings_patch():
            response = client.get("/v1/forecast/bakeoff?grain=company")

    assert response.status_code == 200, response.text
    body = response.json()
    assert "ranked_by_wape" in body
    assert "winner" in body
    assert len(body["ranked_by_wape"]) == 5


def test_bakeoff_endpoint_invalid_grain_422() -> None:
    """GET /v1/forecast/bakeoff?grain=invalid returns 422."""
    response = client.get("/v1/forecast/bakeoff?grain=invalid")
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# write_bakeoff_report test
# ---------------------------------------------------------------------------

def test_write_bakeoff_report(bakeoff_result) -> None:
    """write_bakeoff_report writes a CSV with one row per model and expected columns."""
    with tempfile.TemporaryDirectory() as tmpdir:
        path = write_bakeoff_report(bakeoff_result, tmpdir)
        assert Path(path).exists()
        df = pd.read_csv(path)
        assert len(df) == 5
        required_cols = {"rank", "model", "winner", "wape", "mae", "rmse", "cv_wape_mean"}
        assert required_cols.issubset(set(df.columns)), (
            f"Missing columns: {required_cols - set(df.columns)}"
        )
        # Rank 1 row should be the winner
        winner_row = df[df["winner"] == True]
        assert len(winner_row) == 1
        assert int(winner_row.iloc[0]["rank"]) == 1

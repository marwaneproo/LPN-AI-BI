from __future__ import annotations

"""Tests for GET /v1/forecast/backtest.

Integration tests (marked with skipif) hit the real DB.
Guard and validation tests are pure (no DB needed).
"""

import os
import tempfile
from pathlib import Path
from unittest.mock import patch

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from predictive.backtest import run_backtest, write_backtest_report
from predictive.config import Settings
from predictive.main import app

client = TestClient(app)

SKIP_DB = os.environ.get("SKIP_DB_TESTS", "0") == "1"
DB_REASON = "SKIP_DB_TESTS=1 — no live DB"

_REAL_SETTINGS = Settings(
    database_url="postgresql://lpn_ai_readonly:change_me_ai_readonly@localhost:5433/lpn_ai_bi",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)


def _settings_patch():
    return patch("predictive.main.get_settings", return_value=_REAL_SETTINGS)


# ---------------------------------------------------------------------------
# Integration: company grain backtest (6-month holdout)
# ---------------------------------------------------------------------------

@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_company_backtest_6m_shape() -> None:
    """6-month hold-out returns correct structure and finite metrics."""
    with _settings_patch():
        response = client.get("/v1/forecast/backtest?grain=company&holdout=6")

    assert response.status_code == 200, response.text
    body = response.json()

    assert body["grain"] == "company"
    assert body["holdout_months"] == 6
    assert body["train_months"] == 18  # 24 - 6
    assert len(body["per_month"]) == 6

    pm = body["prophet_metrics"]
    assert 0 < pm["wape"] < 1, f"WAPE not in (0,1): {pm['wape']}"
    assert pm["mae"] > 0
    assert pm["rmse"] > 0
    assert pm["mape_pct"] >= 0

    nm = body["naive_lag12_metrics"]
    assert nm["wape"] is not None
    assert 0 < nm["wape"] < 1


@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_company_backtest_9m_shape() -> None:
    """9-month hold-out (includes Sep peak) returns correct structure."""
    with _settings_patch():
        response = client.get("/v1/forecast/backtest?grain=company&holdout=9")

    assert response.status_code == 200, response.text
    body = response.json()

    assert body["holdout_months"] == 9
    assert body["train_months"] == 15  # 24 - 9
    assert len(body["per_month"]) == 9
    # Sep should be in the test period
    months = [r["month"] for r in body["per_month"]]
    assert any("2025-09" in m for m in months), "Sep 2025 not in 9-month test window"


@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_all_per_month_fields_present() -> None:
    """Every per-month row has the required fields."""
    with _settings_patch():
        response = client.get("/v1/forecast/backtest?grain=company&holdout=6")

    body = response.json()
    required = {
        "month", "actual", "prophet_yhat", "prophet_lower", "prophet_upper",
        "naive_lag12", "prophet_error_abs", "naive_error_abs",
    }
    for row in body["per_month"]:
        assert required == set(row.keys()), f"Missing keys: {required - set(row.keys())}"
        assert row["actual"] > 0
        assert row["prophet_yhat"] >= 0
        assert row["prophet_lower"] >= 0
        assert row["prophet_lower"] <= row["prophet_yhat"] + 1e-3
        assert row["prophet_yhat"] <= row["prophet_upper"] + 1e-3


@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_prophet_vs_naive_wape_documented() -> None:
    """Prophet vs naive comparison field is present and boolean."""
    with _settings_patch():
        response = client.get("/v1/forecast/backtest?grain=company&holdout=6")

    body = response.json()
    assert isinstance(body["prophet_beats_naive_wape"], bool)
    # The result is what it is — we verify it's documented, not forced
    pm_wape = body["prophet_metrics"]["wape"]
    nm_wape = body["naive_lag12_metrics"]["wape"]
    expected = pm_wape < nm_wape
    assert body["prophet_beats_naive_wape"] == expected


# ---------------------------------------------------------------------------
# Guard: insufficient history (mocked)
# ---------------------------------------------------------------------------

def test_backtest_insufficient_history_guard() -> None:
    """A grain/key with insufficient history returns the insufficient_history response."""
    empty_df = pd.DataFrame({"month": [], "ca_facture": []})

    with patch("predictive.backtest.load_monthly_ca", return_value=empty_df):
        with _settings_patch():
            response = client.get("/v1/forecast/backtest?grain=theme&key=999999&holdout=6")

    assert response.status_code == 200
    body = response.json()
    assert body["error"] == "insufficient_history"
    assert body["available_months"] == 0
    assert body["minimum_months"] == 18  # 12 + 6


# ---------------------------------------------------------------------------
# write_backtest_report helper
# ---------------------------------------------------------------------------

def test_write_backtest_report() -> None:
    """write_backtest_report writes a CSV with the expected columns."""
    fake_result = {
        "grain": "company",
        "key": None,
        "holdout_months": 6,
        "train_months": 18,
        "test_period": {"start": "2025-12-01", "end": "2026-05-01"},
        "prophet_metrics": {"wape": 0.12, "mae": 500000.0, "rmse": 700000.0, "mape_pct": 18.5},
        "naive_lag12_metrics": {"wape": 0.18, "mae": 700000.0, "rmse": 900000.0, "mape_pct": 25.0},
        "prophet_beats_naive_wape": True,
        "caveat": "test",
        "per_month": [
            {
                "month": "2025-12-01", "actual": 3920000.0,
                "prophet_yhat": 4100000.0, "prophet_lower": 3000000.0, "prophet_upper": 5000000.0,
                "naive_lag12": 4620000.0, "prophet_error_abs": 180000.0, "naive_error_abs": 700000.0,
            }
        ],
        "generated_at": "2026-06-17T10:00:00+00:00",
    }

    with tempfile.TemporaryDirectory() as tmpdir:
        path = write_backtest_report(fake_result, tmpdir)
        assert Path(path).exists()
        df = pd.read_csv(path)
        assert len(df) == 1
        expected_cols = {"grain", "prophet_wape", "naive_wape", "month", "actual",
                         "prophet_yhat", "naive_lag12"}
        assert expected_cols.issubset(set(df.columns))
        assert df.iloc[0]["prophet_wape"] == 0.12
        assert df.iloc[0]["naive_wape"] == 0.18


# ---------------------------------------------------------------------------
# Validation errors (no DB needed)
# ---------------------------------------------------------------------------

def test_invalid_grain_backtest_422() -> None:
    response = client.get("/v1/forecast/backtest?grain=bad")
    assert response.status_code == 422


def test_commercial_without_key_backtest_422() -> None:
    response = client.get("/v1/forecast/backtest?grain=commercial")
    assert response.status_code == 422


def test_holdout_too_large_422() -> None:
    response = client.get("/v1/forecast/backtest?grain=company&holdout=10")
    assert response.status_code == 422


def test_holdout_too_small_422() -> None:
    response = client.get("/v1/forecast/backtest?grain=company&holdout=2")
    assert response.status_code == 422

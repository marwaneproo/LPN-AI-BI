from __future__ import annotations

"""Tests for GET /v1/forecast/ca — Prophet forecast endpoint.

Two modes:
  1. company grain with real DB data  — verifies shape, bound ordering, Jul–Sep peak.
  2. insufficient_history guard path  — uses a fake key that has zero history.

These tests require a live PostgreSQL connection with fact_sales_monthly populated.
They are marked 'integration' and should be skipped in pure-unit runs (set env
SKIP_DB_TESTS=1 to skip them).

Test for the guard path uses unittest.mock to return an empty DataFrame so it can
run without any specific sparse key being in the DB.
"""

import os
from datetime import date
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from predictive.config import Settings
from predictive.forecast import MIN_HISTORY_MONTHS, clear_forecast_cache
from predictive.main import app

client = TestClient(app)

SKIP_DB = os.environ.get("SKIP_DB_TESTS", "0") == "1"
DB_REASON = "SKIP_DB_TESTS=1 — no live DB"


# ---------------------------------------------------------------------------
# Mock settings pointing at the real localhost DB (host-side port 5433)
# ---------------------------------------------------------------------------
_REAL_SETTINGS = Settings(
    database_url="postgresql://lpn_ai_readonly:change_me_ai_readonly@localhost:5433/lpn_ai_bi",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _mock_settings_patch():
    """Patch get_settings to return _REAL_SETTINGS (bypasses lru_cache)."""
    return patch("predictive.main.get_settings", return_value=_REAL_SETTINGS)


# ---------------------------------------------------------------------------
# Test 1 — company grain: shape, bounds, Jul–Sep peak visible
# ---------------------------------------------------------------------------

@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_company_forecast_shape_and_bounds() -> None:
    """Company forecast with default horizon=6 returns 6 future points with valid bounds."""
    clear_forecast_cache()
    with _mock_settings_patch():
        response = client.get("/v1/forecast/ca", params={"grain": "company", "horizon": 6})

    assert response.status_code == 200, response.text
    body = response.json()

    # Top-level structure — blend when naive available, pure prophet otherwise
    assert body["model"] in {"prophet", "prophet_naive_blend_05"}
    assert body["grain"] == "company"
    assert body["key"] is None
    assert "generated_at" in body

    # History must be non-empty (should be 24 complete months)
    assert len(body["history"]) >= 12
    for h in body["history"]:
        assert "month" in h
        assert "actual" in h
        assert h["actual"] >= 0

    # Forecast must have exactly horizon rows
    forecast = body["forecast"]
    assert len(forecast) == 6

    # Bounds ordering: lower <= yhat <= upper, all non-negative
    for f in forecast:
        assert f["lower"] >= 0.0, f"lower < 0: {f}"
        assert f["yhat"] >= 0.0, f"yhat < 0: {f}"
        assert f["upper"] >= 0.0, f"upper < 0: {f}"
        assert f["lower"] <= f["yhat"] + 1e-6, f"lower > yhat: {f}"
        assert f["yhat"] <= f["upper"] + 1e-6, f"yhat > upper: {f}"


@pytest.mark.skipif(SKIP_DB, reason=DB_REASON)
def test_company_forecast_peak_in_jul_to_sep() -> None:
    """History or forecast should show a Jul–Sep peak reflecting the back-to-school season."""
    clear_forecast_cache()
    with _mock_settings_patch():
        response = client.get("/v1/forecast/ca", params={"grain": "company", "horizon": 12})

    body = response.json()
    all_points = body["history"] + [
        {"month": f["month"], "actual": f["yhat"]} for f in body["forecast"]
    ]

    monthly_avg: dict[int, list[float]] = {}
    for pt in all_points:
        m = date.fromisoformat(pt["month"]).month
        monthly_avg.setdefault(m, []).append(pt["actual"])

    avg_by_month = {m: sum(v) / len(v) for m, v in monthly_avg.items()}

    peak_months = {7, 8, 9}
    other_months = set(avg_by_month.keys()) - peak_months

    if other_months:  # might not have all months in a 6-horizon forecast
        max_peak = max(avg_by_month.get(m, 0) for m in peak_months if m in avg_by_month)
        max_other = max(avg_by_month[m] for m in other_months)
        assert max_peak > max_other * 1.5, (
            f"Jul–Sep peak not clearly above other months: peak={max_peak:,.0f} vs other={max_other:,.0f}\n"
            f"Monthly averages: {avg_by_month}"
        )


# ---------------------------------------------------------------------------
# Test 2 — insufficient_history guard (mocked — no real DB required)
# ---------------------------------------------------------------------------

def test_insufficient_history_guard() -> None:
    """A grain/key with < MIN_HISTORY_MONTHS returns the insufficient_history response (mocked)."""
    clear_forecast_cache()

    empty_df = pd.DataFrame({"month": [], "ca_facture": []})

    with patch("predictive.forecast.load_monthly_ca", return_value=empty_df):
        with _mock_settings_patch():
            response = client.get(
                "/v1/forecast/ca",
                params={"grain": "theme", "key": 999999, "horizon": 6},
            )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["error"] == "insufficient_history"
    assert body["available_months"] == 0
    assert body["minimum_months"] == MIN_HISTORY_MONTHS
    assert body["grain"] == "theme"
    assert body["key"] == 999999


# ---------------------------------------------------------------------------
# Test 3 — validation errors (no DB required)
# ---------------------------------------------------------------------------

def test_invalid_grain_returns_422() -> None:
    """An unknown grain value returns HTTP 422."""
    response = client.get("/v1/forecast/ca", params={"grain": "invalid_grain"})
    assert response.status_code == 422


def test_missing_key_for_commercial_returns_422() -> None:
    """commercial grain without key returns HTTP 422."""
    response = client.get("/v1/forecast/ca", params={"grain": "commercial"})
    assert response.status_code == 422


def test_key_with_company_grain_returns_422() -> None:
    """company grain with key returns HTTP 422."""
    response = client.get("/v1/forecast/ca", params={"grain": "company", "key": 1})
    assert response.status_code == 422


def test_horizon_out_of_range_returns_422() -> None:
    """horizon > 12 returns HTTP 422 (FastAPI Query validation)."""
    response = client.get("/v1/forecast/ca", params={"grain": "company", "horizon": 13})
    assert response.status_code == 422

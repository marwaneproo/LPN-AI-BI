from __future__ import annotations

"""Tests for the /health endpoint with the DB mocked."""

from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from predictive.config import Settings
from predictive.main import app

_MOCK_STATS = {
    "total_rows": 25,
    "complete_months": 24,
    "month_min": "2024-06-01",
    "month_max_complete": "2026-05-01",
}

_MOCK_SETTINGS = Settings(
    database_url="postgresql://fake:fake@localhost:5433/fake",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)

client = TestClient(app)


def test_health_db_ok() -> None:
    """When DB is reachable, /health returns status=ok with fact stats."""
    with patch("predictive.main.get_monthly_fact_stats", return_value=_MOCK_STATS):
        with patch("predictive.main.get_settings", return_value=_MOCK_SETTINGS):
            response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["db_ok"] is True
    assert body["db_error"] is None
    assert body["monthly_fact_row_count"] == 25
    assert body["complete_months"] == 24
    assert body["month_min"] == "2024-06-01"
    assert body["month_max_complete"] == "2026-05-01"


def test_health_db_down() -> None:
    """When DB is unreachable, /health returns status=degraded with error detail."""
    with patch(
        "predictive.main.get_monthly_fact_stats",
        side_effect=Exception("connection refused"),
    ):
        with patch("predictive.main.get_settings", return_value=_MOCK_SETTINGS):
            response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "degraded"
    assert body["db_ok"] is False
    assert "connection refused" in body["db_error"]
    assert body["monthly_fact_row_count"] is None


def test_health_response_shape() -> None:
    """All expected keys are present in the /health response regardless of DB state."""
    with patch("predictive.main.get_monthly_fact_stats", return_value=_MOCK_STATS):
        with patch("predictive.main.get_settings", return_value=_MOCK_SETTINGS):
            response = client.get("/health")

    body = response.json()
    expected_keys = {
        "status", "db_ok", "db_error",
        "monthly_fact_row_count", "complete_months",
        "month_min", "month_max_complete",
    }
    assert expected_keys == set(body.keys())

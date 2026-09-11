from __future__ import annotations

"""Tests for GET /v1/forecast/options — all with DB mocked."""

from unittest.mock import patch

from fastapi.testclient import TestClient

from predictive.config import Settings
from predictive.main import app

client = TestClient(app)

_MOCK_SETTINGS = Settings(
    database_url="postgresql://lpn_ai_readonly:x@localhost:5433/lpn_ai_bi",
    forecast_table="business.fact_sales_monthly",
    forecast_table_by_commercial="business.fact_sales_monthly_by_commercial",
    forecast_table_by_category="business.fact_sales_monthly_by_category",
    forecast_table_by_theme="business.fact_sales_monthly_by_theme",
)

_MOCK_OPTIONS = [
    {"key": 1113590, "label": "LITTÉRATURE GÉNÉRALE", "complete_months": 24, "forecastable": True},
    {"key": 1113600, "label": "SCOLAIRE", "complete_months": 18, "forecastable": True},
    {"key": 1113610, "label": "NOUVEAUTÉS", "complete_months": 8, "forecastable": False},
]


def _settings_patch():
    return patch("predictive.main.get_settings", return_value=_MOCK_SETTINGS)


def _options_patch(return_value=None):
    return patch(
        "predictive.main.get_forecast_options",
        return_value=return_value if return_value is not None else _MOCK_OPTIONS,
    )


def test_options_category_grain_returns_list() -> None:
    """category grain returns the list from get_forecast_options."""
    with _options_patch(), _settings_patch():
        response = client.get("/v1/forecast/options", params={"grain": "category"})

    assert response.status_code == 200
    options = response.json()
    assert len(options) == 3
    assert options[0]["key"] == 1113590
    assert options[0]["label"] == "LITTÉRATURE GÉNÉRALE"
    assert options[0]["complete_months"] == 24
    assert options[0]["forecastable"] is True
    assert options[2]["forecastable"] is False


def test_options_company_grain_returns_empty() -> None:
    """company grain returns an empty list (no key needed)."""
    with _options_patch(return_value=[]), _settings_patch():
        response = client.get("/v1/forecast/options", params={"grain": "company"})

    assert response.status_code == 200
    assert response.json() == []


def test_options_commercial_grain_proxies_call() -> None:
    """commercial grain passes through to get_forecast_options."""
    with _options_patch() as mock_fn, _settings_patch():
        response = client.get("/v1/forecast/options", params={"grain": "commercial"})

    assert response.status_code == 200
    mock_fn.assert_called_once_with(_MOCK_SETTINGS, "commercial")


def test_options_invalid_grain_returns_422() -> None:
    """Unknown grain returns HTTP 422."""
    response = client.get("/v1/forecast/options", params={"grain": "invalid"})
    assert response.status_code == 422

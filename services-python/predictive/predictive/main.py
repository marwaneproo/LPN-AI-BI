from __future__ import annotations

from typing import Optional

from fastapi import FastAPI, HTTPException, Query

from predictive.backtest import run_backtest, write_backtest_report
from predictive.bakeoff import run_bakeoff, write_bakeoff_report
from predictive.config import Settings, get_settings
from predictive.data import Grain, get_forecast_options, get_monthly_fact_stats
from predictive.forecast import build_forecast

app = FastAPI(title="LPN Predictive", version="0.1.0")

_VALID_GRAINS: set[str] = {"company", "commercial", "category", "theme"}


@app.get("/health")
def health(settings: Settings = None) -> dict:
    """Health check — reports DB reachability and monthly fact availability."""
    if settings is None:
        settings = get_settings()

    try:
        stats = get_monthly_fact_stats(settings)
        db_ok = True
        db_error = None
    except Exception as exc:
        db_ok = False
        db_error = str(exc)
        stats = {}

    return {
        "status": "ok" if db_ok else "degraded",
        "db_ok": db_ok,
        "db_error": db_error,
        "monthly_fact_row_count": stats.get("total_rows"),
        "complete_months": stats.get("complete_months"),
        "month_min": stats.get("month_min"),
        "month_max_complete": stats.get("month_max_complete"),
    }


@app.get("/v1/forecast/options")
def forecast_options(
    grain: str = Query(default="company", description="Grain: commercial | category | theme (company returns empty list)"),
    settings: Settings = None,
) -> list:
    """Return forecastable entity options for the given grain.

    Each entry: {key, label, complete_months, forecastable}.
    Sorted by complete_months desc, then label asc.
    Returns empty list for grain=company (no key required for company).
    """
    if grain not in _VALID_GRAINS:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid grain '{grain}'. Must be one of: {sorted(_VALID_GRAINS)}",
        )
    if settings is None:
        settings = get_settings()
    return get_forecast_options(settings, grain)


@app.get("/v1/forecast/ca")
def forecast_ca(
    grain: str = Query(default="company", description="Aggregation grain: company | commercial | category | theme"),
    key: Optional[int] = Query(default=None, description="Entity key (salesrep_id, category_id, theme_id); omit for company grain"),
    horizon: int = Query(default=6, ge=1, le=12, description="Number of future months to forecast (1–12)"),
    settings: Settings = None,
) -> dict:
    """Return a Prophet monthly CA forecast for the given grain/key.

    - grain=company: full company aggregate, no key required.
    - grain=commercial: key = salesrep_id from fact_sales_monthly_by_commercial.
    - grain=category: key = category_id from fact_sales_monthly_by_category.
    - grain=theme: key = theme_id from fact_sales_monthly_by_theme (many keys will hit the insufficient_history guard).

    The response contains history[] (full training set) and forecast[] (horizon future months).
    When < 12 complete months are available for the grain/key, returns an insufficient_history error.
    """
    if grain not in _VALID_GRAINS:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid grain '{grain}'. Must be one of: {sorted(_VALID_GRAINS)}",
        )
    if grain != "company" and key is None:
        raise HTTPException(
            status_code=422,
            detail=f"grain='{grain}' requires a key parameter.",
        )
    if grain == "company" and key is not None:
        raise HTTPException(
            status_code=422,
            detail="grain='company' does not accept a key parameter.",
        )

    if settings is None:
        settings = get_settings()

    return build_forecast(settings, grain=grain, key=key, horizon=horizon)


@app.get("/v1/forecast/backtest")
def forecast_backtest(
    grain: str = Query(default="company", description="Aggregation grain: company | commercial | category | theme"),
    key: Optional[int] = Query(default=None, description="Entity key; omit for company grain"),
    holdout: int = Query(default=6, ge=3, le=9, description="Hold-out window in complete months (3–9)"),
    write_report: bool = Query(default=False, description="If true, also write a CSV report to services-python/reports/"),
    settings: Settings = None,
) -> dict:
    """Hold-out backtest: train on history minus holdout tail, forecast the tail, compare to actuals.

    Returns WAPE (headline), MAE, RMSE, MAPE for both Prophet and a naive lag-12 baseline,
    plus a per-month breakdown.

    WAPE (weighted absolute percentage error = sum|e|/sum|y|) is the headline metric because
    it is robust when the seasonal range is 10-20x.  MAPE is included but inflated by
    low-season months (~2M troughs) — treat it as informational only.
    """
    if grain not in _VALID_GRAINS:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid grain '{grain}'. Must be one of: {sorted(_VALID_GRAINS)}",
        )
    if grain != "company" and key is None:
        raise HTTPException(status_code=422, detail=f"grain='{grain}' requires a key parameter.")
    if grain == "company" and key is not None:
        raise HTTPException(status_code=422, detail="grain='company' does not accept a key parameter.")

    if settings is None:
        settings = get_settings()

    result = run_backtest(settings, grain=grain, key=key, holdout_months=holdout)

    if write_report and "error" not in result:
        import pathlib
        reports_dir = pathlib.Path(__file__).parents[2] / "reports"
        csv_path = write_backtest_report(result, reports_dir)
        result["report_csv"] = csv_path

    return result


@app.get("/v1/forecast/bakeoff")
def forecast_bakeoff(
    grain: str = Query(default="company", description="Aggregation grain (company only for v1 bake-off)"),
    write_report: bool = Query(default=False, description="If true, write a CSV report to services-python/reports/"),
    settings: Settings = None,
) -> dict:
    """Compare 5 forecasting models on an identical chronological 70/30 train/test split.

    Models evaluated: seasonal_naive, ets, sarima, prophet_tuned, lightgbm.
    Metrics: WAPE (headline), MAE, RMSE, MAPE — all via the same _metrics() as /backtest.
    Also runs rolling-origin CV (expanding window, 3-month forecasts).

    Returns per-model metrics, WAPE-ranked table, and the winner.
    SARIMA may return NaN if it fails to converge on the limited history.
    """
    if grain not in _VALID_GRAINS:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid grain '{grain}'. Must be one of: {sorted(_VALID_GRAINS)}",
        )
    if settings is None:
        settings = get_settings()

    result = run_bakeoff(settings, grain=grain)

    if write_report:
        import pathlib
        reports_dir = pathlib.Path(__file__).parents[2] / "reports"
        csv_path = write_bakeoff_report(result, reports_dir)
        result["report_csv"] = csv_path

    return result

from __future__ import annotations

"""Database access for the predictive service.

All queries use the lpn_ai_readonly role — no writes, no DDL.
Only business.fact_sales_monthly* tables are accessed.
"""

from typing import Literal

import pandas as pd
from sqlalchemy import create_engine, text

from predictive.config import Settings

Grain = Literal["company", "commercial", "category", "theme"]


def load_monthly_ca(
    settings: Settings,
    grain: Grain = "company",
    key: int | None = None,
) -> pd.DataFrame:
    """Return a DataFrame of complete monthly CA (is_partial=FALSE) for the given grain.

    Columns returned vary by grain:
      company    : month, ca_facture
      commercial : month, ca_facture  (filtered to salesrep_id == key)
      category   : month, ca_facture  (filtered to category_id == key)
      theme      : month, ca_facture  (filtered to theme_id == key)

    Partial months (is_partial=TRUE) are always excluded.
    Rows are returned sorted ascending by month.
    """
    engine = create_engine(settings.database_url, pool_pre_ping=True)

    if grain == "company":
        sql = text(
            f"SELECT month, ca_facture FROM {settings.forecast_table}"
            " WHERE NOT is_partial ORDER BY month"
        )
        params: dict = {}
    elif grain == "commercial":
        sql = text(
            f"SELECT month, ca_facture FROM {settings.forecast_table_by_commercial}"
            " WHERE NOT is_partial AND salesrep_id = :key ORDER BY month"
        )
        params = {"key": key}
    elif grain == "category":
        sql = text(
            f"SELECT month, ca_facture FROM {settings.forecast_table_by_category}"
            " WHERE NOT is_partial AND category_id = :key ORDER BY month"
        )
        params = {"key": key}
    elif grain == "theme":
        sql = text(
            f"SELECT month, ca_facture FROM {settings.forecast_table_by_theme}"
            " WHERE NOT is_partial AND theme_id = :key ORDER BY month"
        )
        params = {"key": key}
    else:
        raise ValueError(f"Unknown grain: {grain!r}")

    with engine.connect() as conn:
        df = pd.read_sql(sql, conn, params=params)

    df["month"] = pd.to_datetime(df["month"])
    df["ca_facture"] = df["ca_facture"].astype(float)
    return df.sort_values("month").reset_index(drop=True)


_OPTIONS_MIN_MONTHS = 12


def get_forecast_options(settings: Settings, grain: Grain) -> list[dict]:
    """Return entity options for a non-company grain, sorted by complete_months desc then label.

    Each entry: {key, label, complete_months, forecastable}.
    forecastable = complete_months >= _OPTIONS_MIN_MONTHS.
    Returns empty list for company grain (no key required).
    """
    if grain == "company":
        return []

    if grain == "commercial":
        table = settings.forecast_table_by_commercial
        key_col, label_col = "salesrep_id", "commercial_name"
    elif grain == "category":
        table = settings.forecast_table_by_category
        key_col, label_col = "category_id", "category_name"
    elif grain == "theme":
        table = settings.forecast_table_by_theme
        key_col, label_col = "theme_id", "theme_name"
    else:
        raise ValueError(f"Unknown grain: {grain!r}")

    sql = text(
        f"SELECT {key_col} AS key, {label_col} AS label,"
        f" SUM(CASE WHEN NOT is_partial THEN 1 ELSE 0 END)::int AS complete_months"
        f" FROM {table}"
        f" GROUP BY {key_col}, {label_col}"
        f" ORDER BY complete_months DESC, {label_col} ASC"
    )

    engine = create_engine(settings.database_url, pool_pre_ping=True)
    with engine.connect() as conn:
        rows = conn.execute(sql).mappings().all()

    return [
        {
            "key": int(row["key"]),
            "label": str(row["label"]) if row["label"] else f"#{row['key']}",
            "complete_months": int(row["complete_months"]),
            "forecastable": int(row["complete_months"]) >= _OPTIONS_MIN_MONTHS,
        }
        for row in rows
    ]


def get_monthly_fact_stats(settings: Settings) -> dict:
    """Return basic stats from fact_sales_monthly for the /health endpoint."""
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    sql = text(
        f"SELECT COUNT(*) AS total_rows,"
        f" SUM(CASE WHEN NOT is_partial THEN 1 ELSE 0 END) AS complete_months,"
        f" MIN(month) AS month_min,"
        f" MAX(CASE WHEN NOT is_partial THEN month END) AS month_max_complete"
        f" FROM {settings.forecast_table}"
    )
    with engine.connect() as conn:
        row = conn.execute(sql).mappings().one()
    return {
        "total_rows": int(row["total_rows"]),
        "complete_months": int(row["complete_months"]),
        "month_min": str(row["month_min"]) if row["month_min"] else None,
        "month_max_complete": str(row["month_max_complete"]) if row["month_max_complete"] else None,
    }

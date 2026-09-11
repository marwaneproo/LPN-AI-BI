from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # PostgreSQL read-only connection (lpn_ai_readonly role)
    database_url: str = "postgresql://lpn_ai_readonly:change_me_readonly@localhost:5433/lpn_ai_bi"

    # Table names (override in tests or for future schema changes)
    forecast_table: str = "business.fact_sales_monthly"
    forecast_table_by_commercial: str = "business.fact_sales_monthly_by_commercial"
    forecast_table_by_category: str = "business.fact_sales_monthly_by_category"
    forecast_table_by_theme: str = "business.fact_sales_monthly_by_theme"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()

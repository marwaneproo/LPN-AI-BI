"""Database connection config for the staging loader.

Defaults match the DW-01 discovery: native PG18 on ``localhost:5432``, database
``lpn_ai_bi``, superuser ``postgres`` (because ``lpn_app_admin``'s password is not
set in the native restore). The password is read from the environment and is NOT
hard-coded in the repo; export ``PGPASSWORD`` (or ``POSTGRES_PASSWORD``) before
running. All values are env-overridable.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import psycopg2
from dotenv import load_dotenv


class ConfigError(RuntimeError):
    """Raised when required connection settings are missing."""


@dataclass(frozen=True)
class DbConfig:
    host: str
    port: int
    dbname: str
    user: str
    password: str


def db_config_from_env() -> DbConfig:
    """Build a :class:`DbConfig` from the environment (with DW-01 defaults)."""

    load_dotenv()
    host = os.getenv("STAGING_PGHOST") or os.getenv("POSTGRES_HOST") or "localhost"
    port = int(os.getenv("STAGING_PGPORT") or os.getenv("POSTGRES_PORT") or "5432")
    dbname = os.getenv("STAGING_PGDATABASE") or os.getenv("POSTGRES_DB") or "lpn_ai_bi"
    user = os.getenv("STAGING_PGUSER") or os.getenv("POSTGRES_USER") or "postgres"
    password = (
        os.getenv("STAGING_PGPASSWORD")
        or os.getenv("POSTGRES_PASSWORD")
        or os.getenv("PGPASSWORD")
        or os.getenv("POSTGRES_APP_ADMIN_PASSWORD")
    )
    if not password:
        raise ConfigError(
            "PostgreSQL password is missing. Export PGPASSWORD (or POSTGRES_PASSWORD) "
            "before running the staging loader."
        )
    return DbConfig(host=host, port=port, dbname=dbname, user=user, password=password)


def connect(cfg: DbConfig | None = None):
    """Open a psycopg2 connection to the write database (autocommit off)."""

    cfg = cfg or db_config_from_env()
    return psycopg2.connect(
        host=cfg.host,
        port=cfg.port,
        dbname=cfg.dbname,
        user=cfg.user,
        password=cfg.password,
    )

from __future__ import annotations

import os
from pathlib import Path

import psycopg2
import pytest
from click.testing import CliRunner

from data_import.cli import cli


FIXTURE_BUNDLE = Path(__file__).parent / "fixtures" / "sample-bundle.zip"


@pytest.mark.skipif(
    os.getenv("RUN_POSTGRES_TESTS") != "1",
    reason="Set RUN_POSTGRES_TESTS=1 to run against the local Docker PostgreSQL.",
)
def test_import_round_trips_to_postgres() -> None:
    result = CliRunner().invoke(cli, ["import", str(FIXTURE_BUNDLE)])

    assert result.exit_code == 0, result.output
    assert "Import complete" in result.output

    with psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5433"),
        dbname=os.getenv("POSTGRES_DB", "lpn_ai_bi"),
        user=os.getenv("POSTGRES_USER", "lpn_app_admin"),
        password=os.getenv("POSTGRES_PASSWORD") or os.getenv("POSTGRES_APP_ADMIN_PASSWORD"),
    ) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT count(*) FROM business.c_order")
            assert cursor.fetchone() == (10,)

            cursor.execute("SELECT count(*) FROM app.import_history")
            assert cursor.fetchone()[0] >= 1

from __future__ import annotations

import csv
import json
import os
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path

import click
import pandas as pd
from psycopg2 import sql
from dotenv import load_dotenv
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError


COPY_ROW_THRESHOLD = 500_000


class BundleValidationError(Exception):
    """Raised when a snapshot bundle fails dry-run validation."""


class ManifestTable(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1)
    csv_file: str = Field(min_length=1)
    row_count: int = Field(ge=0)
    exported_columns: list[str] = Field(default_factory=list)
    filter_used: str | None = None


class Manifest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    snapshot_id: str = Field(min_length=1)
    exported_at: datetime
    exported_by: str = Field(min_length=1)
    source_system: str = Field(min_length=1)
    tables: list[ManifestTable] = Field(min_length=1)
    notes: str | None = None


class TableSummary(BaseModel):
    name: str
    csv_file: str
    rows: int
    columns: int


class DryRunSummary(BaseModel):
    snapshot_id: str
    exported_at: datetime
    table_summaries: list[TableSummary]

    @property
    def total_rows(self) -> int:
        return sum(table.rows for table in self.table_summaries)


class ImportResult(BaseModel):
    snapshot_id: str
    table_count: int
    total_rows: int


def validate_bundle(bundle_path: Path) -> DryRunSummary:
    if not zipfile.is_zipfile(bundle_path):
        raise BundleValidationError(f"Bundle is not a valid zip file: {bundle_path}")

    with tempfile.TemporaryDirectory(prefix="lpn-data-import-") as temp_dir:
        extract_dir = Path(temp_dir)
        with zipfile.ZipFile(bundle_path) as bundle:
            _safe_extract(bundle, extract_dir)

        manifest = _load_manifest(extract_dir / "manifest.json")
        _validate_schema_file(extract_dir / "schema.sql")

        summaries = []
        for table in manifest.tables:
            csv_path = extract_dir / table.csv_file
            actual_rows = _count_csv_rows(csv_path)
            if actual_rows != table.row_count:
                raise BundleValidationError(
                    f"Row count mismatch for {table.name}: manifest says "
                    f"{table.row_count}, CSV has {actual_rows}"
                )
            summaries.append(
                TableSummary(
                    name=table.name,
                    csv_file=table.csv_file,
                    rows=actual_rows,
                    columns=len(table.exported_columns),
                )
            )

    return DryRunSummary(
        snapshot_id=manifest.snapshot_id,
        exported_at=manifest.exported_at,
        table_summaries=summaries,
    )


def import_bundle_to_postgres(bundle_path: Path) -> ImportResult:
    if not zipfile.is_zipfile(bundle_path):
        raise BundleValidationError(f"Bundle is not a valid zip file: {bundle_path}")

    with tempfile.TemporaryDirectory(prefix="lpn-data-import-") as temp_dir:
        extract_dir = Path(temp_dir)
        with zipfile.ZipFile(bundle_path) as bundle:
            _safe_extract(bundle, extract_dir)

        manifest = _load_manifest(extract_dir / "manifest.json")
        schema_sql = _read_schema_sql(extract_dir / "schema.sql")

        summary = _validate_extracted_bundle(extract_dir, manifest)
        engine = _create_engine()
        try:
            _write_bundle(engine, extract_dir, manifest, schema_sql, summary)
        except SQLAlchemyError as exc:
            raise BundleValidationError(f"PostgreSQL import failed: {exc}") from exc

    return ImportResult(
        snapshot_id=summary.snapshot_id,
        table_count=len(summary.table_summaries),
        total_rows=summary.total_rows,
    )


def _validate_extracted_bundle(extract_dir: Path, manifest: Manifest) -> DryRunSummary:
    summaries = []
    for table in manifest.tables:
        csv_path = extract_dir / table.csv_file
        actual_rows = _count_csv_rows(csv_path)
        if actual_rows != table.row_count:
            raise BundleValidationError(
                f"Row count mismatch for {table.name}: manifest says "
                f"{table.row_count}, CSV has {actual_rows}"
            )
        summaries.append(
            TableSummary(
                name=table.name,
                csv_file=table.csv_file,
                rows=actual_rows,
                columns=len(table.exported_columns),
            )
        )

    return DryRunSummary(
        snapshot_id=manifest.snapshot_id,
        exported_at=manifest.exported_at,
        table_summaries=summaries,
    )


def _safe_extract(bundle: zipfile.ZipFile, extract_dir: Path) -> None:
    root = extract_dir.resolve()
    for member in bundle.infolist():
        target = (extract_dir / member.filename).resolve()
        if root != target and root not in target.parents:
            raise BundleValidationError(f"Bundle contains unsafe path: {member.filename}")
    bundle.extractall(extract_dir)


def _load_manifest(path: Path) -> Manifest:
    if not path.exists():
        raise BundleValidationError("manifest.json is missing from the bundle")

    try:
        raw_manifest = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise BundleValidationError(f"manifest.json is malformed: {exc.msg}") from exc

    try:
        return Manifest.model_validate(raw_manifest)
    except ValidationError as exc:
        raise BundleValidationError(f"manifest.json failed validation: {exc}") from exc


def _validate_schema_file(path: Path) -> None:
    _read_schema_sql(path)


def _read_schema_sql(path: Path) -> str:
    if not path.exists():
        raise BundleValidationError("schema.sql is missing from the bundle")

    schema_sql = path.read_text(encoding="utf-8").strip()
    if not schema_sql:
        raise BundleValidationError("schema.sql is empty")
    if "CREATE TABLE" not in schema_sql.upper():
        raise BundleValidationError("schema.sql must contain at least one CREATE TABLE statement")
    return schema_sql


def _count_csv_rows(path: Path) -> int:
    if not path.exists():
        raise BundleValidationError(f"CSV file is missing: {path.as_posix()}")

    with path.open(newline="", encoding="utf-8-sig") as csv_file:
        reader = csv.reader(csv_file)
        try:
            next(reader)
        except StopIteration as exc:
            raise BundleValidationError(f"CSV file is empty: {path.as_posix()}") from exc
        return sum(1 for _ in reader)


def _create_engine() -> Engine:
    load_dotenv()
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = os.getenv("POSTGRES_PORT", "5433")
    database = os.getenv("POSTGRES_DB", "lpn_ai_bi")
    user = os.getenv("POSTGRES_USER", "lpn_app_admin")
    password = os.getenv("POSTGRES_PASSWORD") or os.getenv("POSTGRES_APP_ADMIN_PASSWORD")

    if not password:
        raise BundleValidationError(
            "PostgreSQL password is missing. Set POSTGRES_PASSWORD or POSTGRES_APP_ADMIN_PASSWORD."
        )

    url = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{database}"
    return create_engine(url, future=True)


def _write_bundle(
    engine: Engine,
    extract_dir: Path,
    manifest: Manifest,
    schema_sql: str,
    summary: DryRunSummary,
) -> None:
    with engine.begin() as connection:
        connection.execute(text("DROP SCHEMA IF EXISTS business CASCADE"))
        connection.execute(text("CREATE SCHEMA business AUTHORIZATION lpn_app_admin"))
        connection.execute(text("REVOKE ALL ON SCHEMA app FROM PUBLIC"))
        connection.execute(text("REVOKE ALL ON SCHEMA app FROM lpn_ai_readonly"))
        connection.execute(text(schema_sql))

        for table in manifest.tables:
            csv_path = extract_dir / table.csv_file
            if table.row_count > COPY_ROW_THRESHOLD:
                _copy_large_csv(connection, csv_path, table)
            else:
                dataframe = pd.read_csv(csv_path)
                dataframe.columns = [column.lower() for column in dataframe.columns]
                dataframe.to_sql(
                    table.name.lower(),
                    con=connection,
                    schema="business",
                    if_exists="append",
                    index=False,
                    method="multi",
                    chunksize=10000,
                )

        connection.execute(text("GRANT USAGE ON SCHEMA business TO lpn_ai_readonly"))
        connection.execute(text("GRANT SELECT ON ALL TABLES IN SCHEMA business TO lpn_ai_readonly"))
        connection.execute(
            text(
                """
                ALTER DEFAULT PRIVILEGES FOR ROLE lpn_app_admin IN SCHEMA business
                GRANT SELECT ON TABLES TO lpn_ai_readonly
                """
            )
        )
        connection.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS app.import_history (
                    id serial PRIMARY KEY,
                    snapshot_id text NOT NULL,
                    imported_at timestamptz NOT NULL DEFAULT now(),
                    source_exported_at timestamptz NOT NULL,
                    table_count int NOT NULL,
                    total_rows bigint NOT NULL,
                    status text NOT NULL,
                    notes text
                )
                """
            )
        )
        connection.execute(text("REVOKE ALL ON app.import_history FROM PUBLIC"))
        connection.execute(text("REVOKE ALL ON app.import_history FROM lpn_ai_readonly"))
        connection.execute(
            text(
                """
                INSERT INTO app.import_history (
                    snapshot_id,
                    source_exported_at,
                    table_count,
                    total_rows,
                    status,
                    notes
                )
                VALUES (
                    :snapshot_id,
                    :source_exported_at,
                    :table_count,
                    :total_rows,
                    'success',
                    :notes
                )
                """
            ),
            {
                "snapshot_id": manifest.snapshot_id,
                "source_exported_at": manifest.exported_at,
                "table_count": len(summary.table_summaries),
                "total_rows": summary.total_rows,
                "notes": manifest.notes,
            },
        )


def _copy_large_csv(connection, csv_path: Path, table: ManifestTable) -> None:
    columns = table.exported_columns
    if not columns:
        raise BundleValidationError(f"{table.name} requires exported_columns for COPY loading")

    copy_statement = sql.SQL("COPY {}.{} ({}) FROM STDIN WITH (FORMAT CSV, HEADER TRUE)").format(
        sql.Identifier("business"),
        sql.Identifier(table.name.lower()),
        sql.SQL(", ").join(sql.Identifier(column.lower()) for column in columns),
    )
    raw_connection = connection.connection.driver_connection
    with raw_connection.cursor() as cursor, csv_path.open(
        "r",
        newline="",
        encoding="utf-8-sig",
    ) as csv_file:
        cursor.copy_expert(copy_statement, csv_file)


def _fetch_history(limit: int = 10) -> list[dict[str, object]]:
    engine = _create_engine()
    try:
        with engine.begin() as connection:
            connection.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS app.import_history (
                        id serial PRIMARY KEY,
                        snapshot_id text NOT NULL,
                        imported_at timestamptz NOT NULL DEFAULT now(),
                        source_exported_at timestamptz NOT NULL,
                        table_count int NOT NULL,
                        total_rows bigint NOT NULL,
                        status text NOT NULL,
                        notes text
                    )
                    """
                )
            )
            rows = connection.execute(
                text(
                    """
                    SELECT id, snapshot_id, imported_at, source_exported_at, table_count, total_rows, status
                    FROM app.import_history
                    ORDER BY imported_at DESC, id DESC
                    LIMIT :limit
                    """
                ),
                {"limit": limit},
            )
            return [dict(row._mapping) for row in rows]
    except SQLAlchemyError as exc:
        raise BundleValidationError(f"PostgreSQL history query failed: {exc}") from exc


@click.group()
def cli() -> None:
    """LPN snapshot import tooling."""


@cli.command(name="import")
@click.argument(
    "bundle_zip",
    type=click.Path(exists=True, dir_okay=False, path_type=Path),
)
@click.option(
    "--dry-run",
    is_flag=True,
    help="Validate the bundle and print the import plan without writing to PostgreSQL.",
)
def import_bundle(bundle_zip: Path, dry_run: bool) -> None:
    """Import or validate a snapshot bundle."""

    if dry_run:
        try:
            summary = validate_bundle(bundle_zip)
        except BundleValidationError as exc:
            raise click.ClickException(str(exc)) from exc

        _print_summary(summary)
        return

    try:
        result = import_bundle_to_postgres(bundle_zip)
    except BundleValidationError as exc:
        raise click.ClickException(str(exc)) from exc

    click.echo("Import complete")
    click.echo(f"Snapshot: {result.snapshot_id}")
    click.echo(f"Tables imported: {result.table_count}")
    click.echo(f"Rows imported: {result.total_rows}")


@cli.command()
def history() -> None:
    """Print the last 10 import-history rows."""

    try:
        rows = _fetch_history()
    except BundleValidationError as exc:
        raise click.ClickException(str(exc)) from exc

    if not rows:
        click.echo("No imports found.")
        return

    click.echo(f"{'ID':>4} {'Snapshot':<24} {'Imported at':<32} {'Tables':>6} {'Rows':>8} Status")
    click.echo("-" * 91)
    for row in rows:
        click.echo(
            f"{row['id']:>4} "
            f"{row['snapshot_id']:<24} "
            f"{row['imported_at'].isoformat():<32} "
            f"{row['table_count']:>6} "
            f"{row['total_rows']:>8} "
            f"{row['status']}"
        )


def _print_summary(summary: DryRunSummary) -> None:
    click.echo("DRY RUN - no data written to PostgreSQL")
    click.echo(f"Snapshot: {summary.snapshot_id}")
    click.echo(f"Exported at: {summary.exported_at.isoformat()}")
    click.echo()
    click.echo(f"{'Table':<18} {'CSV':<30} {'Rows':>8} {'Columns':>8}")
    click.echo("-" * 66)
    for table in summary.table_summaries:
        click.echo(f"{table.name:<18} {table.csv_file:<30} {table.rows:>8} {table.columns:>8}")
    click.echo("-" * 66)
    click.echo(f"Total tables: {len(summary.table_summaries)}")
    click.echo(f"Total rows: {summary.total_rows}")

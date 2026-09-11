"""CLI entry point for the staging loader.

    python -m data_import.staging_loader.cli smoke
    python -m data_import.staging_loader.cli load-table stg_c_region --window 2024_xlsx

On a structural failure the run is marked ``FAILED`` in ``etl.etl_run_log`` and
the process exits non-zero.
"""

from __future__ import annotations

import sys

import click

from . import run_log
from .config import ConfigError, connect
from .loader import StagingLoader, StructuralError
from .source_map import (
    SMOKE_TABLES,
    WINDOW_2024,
    WINDOW_2024_GIANTS,
    WINDOW_CSV,
    load_source_map,
    window_2024_all,
    window_2024_small_medium,
    window_csv_all,
)

_WINDOWS = [WINDOW_2024, WINDOW_CSV]
_PROGRESS_EVERY_BATCHES = 10  # log every N x 5,000 rows on chunked giants


def peak_working_set_mb() -> float | None:
    """Peak resident memory (MB) for this process, Windows only; else None.

    Reads ``PeakWorkingSetSize`` from ``GetProcessMemoryInfo`` so the DW-03
    report can state true peak RSS over the whole run (the giant-file guard).
    """

    try:
        import ctypes
        from ctypes import wintypes

        class _PMC(ctypes.Structure):
            _fields_ = [
                ("cb", wintypes.DWORD),
                ("PageFaultCount", wintypes.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]

        kernel32 = ctypes.windll.kernel32
        psapi = ctypes.windll.psapi
        # Declare signatures so the 64-bit pseudo-handle is not truncated.
        kernel32.GetCurrentProcess.restype = wintypes.HANDLE
        psapi.GetProcessMemoryInfo.argtypes = [
            wintypes.HANDLE,
            ctypes.POINTER(_PMC),
            wintypes.DWORD,
        ]
        psapi.GetProcessMemoryInfo.restype = wintypes.BOOL

        counters = _PMC()
        counters.cb = ctypes.sizeof(_PMC)
        handle = kernel32.GetCurrentProcess()
        if not psapi.GetProcessMemoryInfo(
            handle, ctypes.byref(counters), counters.cb
        ):
            return None
        return counters.PeakWorkingSetSize / (1024 * 1024)
    except Exception:  # noqa: BLE001 - reporting helper, never fatal
        return None


@click.group()
def cli() -> None:
    """LPN staging loader (DW-02)."""


def _run(tables: list[str], window: str, triggered_by: str) -> None:
    try:
        specs = load_source_map()
    except Exception as exc:  # noqa: BLE001 - surface config errors clearly
        raise click.ClickException(f"failed to load source map: {exc}") from exc

    unknown = [t for t in tables if t not in specs]
    if unknown:
        raise click.ClickException(f"unknown staging table(s): {unknown}")

    try:
        conn = connect()
    except ConfigError as exc:
        raise click.ClickException(str(exc)) from exc

    run_id = run_log.start_run(conn, source_tag=window, triggered_by=triggered_by)
    loader = StagingLoader(conn, run_id)
    click.echo(f"ETL run {run_id} started (window={window})")
    try:
        total_inserted = 0
        for table in tables:
            spec = specs[table]

            on_batch = None
            if spec.chunked:
                click.echo(f"  {table}: streaming chunked (5,000-row batches)…")

                def on_batch(n: int, _t: str = table) -> None:
                    if (n // 5000) % _PROGRESS_EVERY_BATCHES == 0:
                        click.echo(f"    {_t}: {n:>8,} rows inserted…")

            result = loader.load_table(spec, window, on_batch=on_batch)
            run_log.write_table_stats(
                conn,
                run_id,
                table_name=result.table,
                source_file=result.source_file,
                rows_read=result.rows_read,
                rows_inserted=result.rows_inserted,
                rows_rejected=result.rows_rejected,
                duration_ms=result.duration_ms,
            )
            total_inserted += result.rows_inserted
            delim_txt = ""
            if result.delimiter is not None:
                shown = {"\t": "\\t"}.get(result.delimiter, result.delimiter)
                delim_txt = f" delim='{shown}'"
            click.echo(
                f"  {result.table:<26} read={result.rows_read:>7} "
                f"inserted={result.rows_inserted:>7} rejected={result.rows_rejected:>5} "
                f"({result.duration_ms} ms){delim_txt}"
            )
        run_log.complete_run(conn, run_id, "SUCCESS")
        peak = peak_working_set_mb()
        peak_txt = f" — peak RSS {peak:.0f} MB" if peak is not None else ""
        click.echo(f"ETL run {run_id} SUCCESS — {total_inserted} rows inserted{peak_txt}")
    except StructuralError as exc:
        run_log.complete_run(conn, run_id, "FAILED", notes=str(exc))
        click.echo(f"STRUCTURAL FAILURE — run {run_id} marked FAILED: {exc}", err=True)
        sys.exit(1)
    finally:
        conn.close()


@cli.command()
@click.option("--window", default=WINDOW_2024, type=click.Choice(_WINDOWS))
def smoke(window: str) -> None:
    """Load the 10 tiny 2024 dimension files (DW-02 smoke set)."""

    _run(SMOKE_TABLES, window, triggered_by="cli:smoke")


@cli.command(name="load-table")
@click.argument("table")
@click.option("--window", default=WINDOW_2024, type=click.Choice(_WINDOWS))
def load_table(table: str, window: str) -> None:
    """Load a single staging table from the given window."""

    _run([table], window, triggered_by=f"cli:load-table:{table}")


@cli.command(name="load-2024")
@click.option(
    "--phase",
    default="small-medium",
    type=click.Choice(["small-medium", "giants", "all"]),
    help="small-medium = all 2024 dims+transactional except the 3 giants; "
    "giants = stream 2024_04/06/02 in 5,000-row chunks; all = both in order.",
)
def load_2024(phase: str) -> None:
    """DW-03: load the full 2024 xlsx window into staging (_source_tag='2024_xlsx').

    Always uses window=2024_xlsx. Run --phase small-medium first, inspect the
    checkpoint counts, then --phase giants. M_PRODUCT/M_PRODUCT_PO/RV_STORAGE are
    CSV-only and deferred to DW-04; C_TAX/AD_ORG/M_LOCATOR/CUSTOMER_PORTFOLIO are
    out of v1 scope (no staging table).
    """

    if phase == "small-medium":
        tables = window_2024_small_medium()
    elif phase == "giants":
        tables = list(WINDOW_2024_GIANTS)
    else:
        tables = window_2024_all()
    click.echo(f"DW-03 load-2024 phase={phase}: {len(tables)} table(s)")
    _run(tables, WINDOW_2024, triggered_by=f"cli:load-2024:{phase}")


@cli.command(name="load-csv")
@click.option(
    "--only",
    default=None,
    help="Comma-separated staging table(s) to load (default: all csv_2025 tables).",
)
def load_csv(only: str | None) -> None:
    """DW-04: load the 2025-2026 CSV window into staging (_source_tag='csv_2025').

    Loads all 28 csv_2025 staging tables (small/medium first, the three giant
    line tables last). The CSV delimiter is detected per file (the package is
    comma-delimited). M_PRODUCT / M_PRODUCT_PO / RV_STORAGE — CSV-only and
    deferred from DW-03 — are included here. Idempotent: each (table, csv_2025)
    is DELETEd before insert, so the 2024_xlsx window is untouched.
    """

    if only:
        tables = [t.strip() for t in only.split(",") if t.strip()]
    else:
        tables = window_csv_all()
    click.echo(f"DW-04 load-csv: {len(tables)} table(s)")
    _run(tables, WINDOW_CSV, triggered_by="cli:load-csv")


if __name__ == "__main__":
    cli()

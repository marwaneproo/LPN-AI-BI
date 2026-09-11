"""The chunked, idempotent, memory-safe staging loader.

Reject vs fail discipline (DW-02 hard rule):
- A NULL/unparseable **natural key** is a row-level reject -> ``etl.stg_rejects``,
  and the load keeps going.
- A **structural** problem (missing source file, blank/mismatched header, target
  table absent, DB error) raises :class:`StructuralError`, which the CLI logs as
  ``status='failed'`` and exits non-zero. Never a silent partial load.

Idempotency: each ``(table, _source_tag)`` is ``DELETE``d before insert, so a
re-run reproduces identical row counts and the *other* window survives.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator

import psycopg2
from psycopg2.extras import Json, execute_values

from .readers import HeaderError, detect_delimiter, stream_csv_rows, stream_xlsx_rows
from .source_map import WINDOW_2024, WINDOW_CSV, SourceSpec, source_dirs

BATCH_SIZE = 5000
_METADATA_PREFIX = "_"
_META_INSERT_COLS = ("_source_file", "_source_tag", "_etl_run_id")


class StructuralError(RuntimeError):
    """A run-fatal failure. The run must be marked failed and exit non-zero."""


@dataclass
class TableLoadResult:
    table: str
    source_file: str
    rows_read: int
    rows_inserted: int
    rows_rejected: int
    duration_ms: int
    delimiter: str | None = None


def iter_batches(rows: Iterable, size: int) -> Iterator[list]:
    """Yield fixed-size lists from ``rows``; the last may be smaller.

    This is the bounded-memory primitive: it never holds more than ``size``
    items, regardless of how many rows the source produces.
    """

    batch: list = []
    for row in rows:
        batch.append(row)
        if len(batch) >= size:
            yield batch
            batch = []
    if batch:
        yield batch


def fetch_table_columns(conn, schema: str, table: str) -> list[str]:
    """Return the column names of ``schema.table`` in ordinal order."""

    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT column_name
              FROM information_schema.columns
             WHERE table_schema = %s AND table_name = %s
             ORDER BY ordinal_position
            """,
            (schema, table),
        )
        return [row[0] for row in cur.fetchall()]


class StagingLoader:
    def __init__(
        self,
        conn,
        run_id,
        *,
        batch_size: int = BATCH_SIZE,
        xlsx_dir: Path | None = None,
        csv_dir: Path | None = None,
    ) -> None:
        self.conn = conn
        self.run_id = run_id
        self.run_id_str = str(run_id)
        self.batch_size = batch_size
        default_xlsx, default_csv = source_dirs()
        self.xlsx_dir = xlsx_dir or default_xlsx
        self.csv_dir = csv_dir or default_csv

    # -- public API --------------------------------------------------------

    def load_table(self, spec: SourceSpec, window: str, on_batch=None) -> TableLoadResult:
        """Resolve the source file for ``window`` and stream it into staging.

        ``on_batch`` (optional) is invoked after each flushed batch with the
        cumulative ``rows_inserted`` count — used by the CLI to log progress on
        the giant chunked files without breaking memory bounds.
        """

        path, kind = self._resolve(spec, window)
        delimiter = None
        if kind == "xlsx":
            rows = stream_xlsx_rows(path, header=list(spec.xlsx_header) if spec.xlsx_header else None)
        else:
            delimiter = detect_delimiter(path)
            rows = stream_csv_rows(path, delimiter=delimiter)
        result = self.load_rows(spec, window, path.name, rows, on_batch=on_batch)
        result.delimiter = delimiter
        return result

    def load_rows(
        self,
        spec: SourceSpec,
        source_tag: str,
        source_file: str,
        rows: Iterable[dict],
        on_batch=None,
    ) -> TableLoadResult:
        """Core DB load. ``rows`` is any iterable of standardized row-dicts.

        Structural checks (target table, header) run *before* the DELETE so a
        bad source can never wipe good staging data.
        """

        start = time.perf_counter()

        data_cols = [
            c
            for c in fetch_table_columns(self.conn, "staging", spec.staging_table)
            if not c.startswith(_METADATA_PREFIX)
        ]
        if not data_cols:
            raise StructuralError(
                f"target table staging.{spec.staging_table} not found or has no columns"
            )

        insert_cols = data_cols + list(_META_INSERT_COLS)
        cols_sql = ", ".join(f'"{c}"' for c in insert_cols)
        insert_stmt = (
            f'INSERT INTO staging."{spec.staging_table}" ({cols_sql}) VALUES %s'
        )
        delete_stmt = (
            f'DELETE FROM staging."{spec.staging_table}" WHERE _source_tag = %s'
        )

        # Peek the first row to validate the header before any mutation.
        try:
            iterator = iter(rows)
            try:
                first = next(iterator)
                has_rows = True
            except StopIteration:
                first = None
                has_rows = False
        except HeaderError as exc:
            raise StructuralError(str(exc)) from exc

        if has_rows:
            missing = [k for k in spec.natural_key if k not in first]
            if missing:
                raise StructuralError(
                    f"header mismatch in {source_file}: natural-key column(s) "
                    f"{missing} absent from source"
                )
            ordered_rows: Iterable[dict] = _prepend(first, iterator)
        else:
            ordered_rows = iter(())

        rows_read = 0
        rows_inserted = 0
        rows_rejected = 0

        try:
            with self.conn.cursor() as cur:
                cur.execute(delete_stmt, (source_tag,))

                batch: list[tuple] = []
                for row in ordered_rows:
                    rows_read += 1
                    null_keys = [k for k in spec.natural_key if row.get(k) is None]
                    if null_keys:
                        self._record_reject(
                            cur,
                            spec,
                            source_file,
                            rows_read,
                            row,
                            "null_natural_key",
                            f"null/missing natural key column(s): {null_keys}",
                        )
                        rows_rejected += 1
                        continue

                    batch.append(
                        tuple(row.get(c) for c in data_cols)
                        + (source_file, source_tag, self.run_id_str)
                    )
                    if len(batch) >= self.batch_size:
                        execute_values(cur, insert_stmt, batch, page_size=len(batch))
                        rows_inserted += len(batch)
                        batch = []
                        if spec.chunked:
                            self.conn.commit()
                        if on_batch is not None:
                            on_batch(rows_inserted)

                if batch:
                    execute_values(cur, insert_stmt, batch, page_size=len(batch))
                    rows_inserted += len(batch)

            self.conn.commit()
        except HeaderError as exc:
            self.conn.rollback()
            raise StructuralError(str(exc)) from exc
        except psycopg2.Error as exc:
            self.conn.rollback()
            raise StructuralError(
                f"database error loading staging.{spec.staging_table}: {exc}"
            ) from exc

        duration_ms = int((time.perf_counter() - start) * 1000)
        return TableLoadResult(
            table=spec.staging_table,
            source_file=source_file,
            rows_read=rows_read,
            rows_inserted=rows_inserted,
            rows_rejected=rows_rejected,
            duration_ms=duration_ms,
        )

    # -- internals ---------------------------------------------------------

    def _resolve(self, spec: SourceSpec, window: str) -> tuple[Path, str]:
        if window == WINDOW_2024:
            if spec.exclude_xlsx or not spec.xlsx_2024:
                raise StructuralError(
                    f"{spec.staging_table}: no 2024 xlsx source "
                    f"(excluded oversized file or none) — load via {WINDOW_CSV}"
                )
            path = self.xlsx_dir / spec.xlsx_2024
            kind = "xlsx"
        elif window == WINDOW_CSV:
            if not spec.csv_2025:
                raise StructuralError(f"{spec.staging_table}: no csv_2025 source")
            path = self.csv_dir / spec.csv_2025
            kind = "csv"
        else:
            raise StructuralError(f"unknown window '{window}'")

        if not path.exists():
            raise StructuralError(f"source file missing: {path}")
        return path, kind

    def _record_reject(
        self,
        cur,
        spec: SourceSpec,
        source_file: str,
        source_row: int,
        row: dict,
        reason: str,
        detail: str,
    ) -> None:
        pk_value = ",".join(str(row.get(k)) for k in spec.natural_key)
        cur.execute(
            """
            INSERT INTO etl.stg_rejects
                (etl_run_id, source_table, source_file, source_row,
                 pk_value, reject_reason, reject_detail, raw_row_json)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                self.run_id_str,
                spec.staging_table,
                source_file,
                source_row,
                pk_value,
                reason,
                detail,
                Json(row, dumps=_json_dumps),
            ),
        )


def _prepend(first, iterator):
    yield first
    yield from iterator


def _json_dumps(obj):
    import json

    return json.dumps(obj, default=str)

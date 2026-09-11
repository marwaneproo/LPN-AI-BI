"""Writers for the ``etl`` control-plane tables.

- ``etl.etl_run_log``         — one row per run (RUNNING -> SUCCESS/FAILED)
- ``etl.etl_run_table_stats`` — per-table read/insert/reject/duration counts
"""

from __future__ import annotations

import uuid


def start_run(
    conn,
    *,
    source_tag: str,
    triggered_by: str,
    git_commit: str | None = None,
    notes: str | None = None,
) -> uuid.UUID:
    run_id = uuid.uuid4()
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO etl.etl_run_log
                (etl_run_id, started_at, status, triggered_by, source_tag, git_commit, notes)
            VALUES (%s, now(), 'RUNNING', %s, %s, %s, %s)
            """,
            (str(run_id), triggered_by, source_tag, git_commit, notes),
        )
    conn.commit()
    return run_id


def complete_run(conn, run_id, status: str, notes: str | None = None) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE etl.etl_run_log
               SET completed_at = now(),
                   status = %s,
                   notes = COALESCE(%s, notes)
             WHERE etl_run_id = %s
            """,
            (status, notes, str(run_id)),
        )
    conn.commit()


def write_table_stats(
    conn,
    run_id,
    *,
    table_name: str,
    source_file: str,
    rows_read: int,
    rows_inserted: int,
    rows_rejected: int,
    duration_ms: int,
) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO etl.etl_run_table_stats
                (etl_run_id, table_name, source_file, rows_read, rows_inserted,
                 rows_skipped_dup, rows_rejected, duration_ms, completed_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now())
            """,
            (
                str(run_id),
                table_name,
                source_file,
                rows_read,
                rows_inserted,
                0,
                rows_rejected,
                duration_ms,
            ),
        )
    conn.commit()

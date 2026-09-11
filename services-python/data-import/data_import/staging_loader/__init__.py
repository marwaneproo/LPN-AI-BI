"""Chunked, memory-safe staging loader (DW-02).

Streams 2024 xlsx exports and 2025-2026 CSV exports into the ``staging.stg_*``
tables on the write database, stamping ETL metadata, logging runs to ``etl.*``,
and capturing bad rows to ``etl.stg_rejects``.

This package is independent of the legacy ``data_import.cli`` bundle importer
(which manages the ``business`` schema) and never touches ``business``.
"""

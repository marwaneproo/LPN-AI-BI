"""Memory-safe source readers.

Both readers are **generators**: they yield one standardized row-dict at a time
and never materialize the whole file. xlsx uses openpyxl ``read_only=True`` (a
lazy SAX-style row iterator); csv uses the stdlib ``csv`` module with utf-8-sig
and a **per-file detected delimiter** (:func:`detect_delimiter`, DW-04 — the
2025-2026 package is comma-delimited, not ``;``). This is what keeps memory
bounded on the 254 MB / 132 MB / 81 MB files (DW-03).
"""

from __future__ import annotations

import csv as _csv
from pathlib import Path
from typing import Iterator

from openpyxl import load_workbook

from .columns import coerce_value, standardize_column_name


class HeaderError(ValueError):
    """Raised when a source file has no usable header row (structural failure)."""


# Candidate delimiters, in tie-break preference order. ``,`` is first because the
# 2025-2026 export package is comma-delimited (DW-04 finding: every CSV in
# vente_2025_2026_import/csv uses ``,`` — the DW-02 note that only C_DOCTYPE.csv
# was comma-delimited under-counted; semicolon never actually appears).
_DELIMITER_CANDIDATES = (",", ";", "\t", "|")


def detect_delimiter(path: Path) -> str:
    """Sniff the delimiter of a CSV from its header line.

    Reads the first non-empty line and, for each candidate delimiter, parses it
    with ``csv.reader`` (so quoted fields are respected) and counts the resulting
    fields. The delimiter that splits the header into the most fields wins; ties
    break by :data:`_DELIMITER_CANDIDATES` order. Raises :class:`HeaderError` if
    the file has no usable header row. This makes the loader robust to mixed
    delimiters across the source package rather than assuming ``;``.
    """

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        header_line = ""
        for line in handle:
            if line.strip() != "":
                header_line = line.rstrip("\r\n")
                break
    if header_line == "":
        raise HeaderError(f"{path.name}: file has no rows / no header")

    best_delim = _DELIMITER_CANDIDATES[0]
    best_fields = 0
    for delim in _DELIMITER_CANDIDATES:
        fields = next(_csv.reader([header_line], delimiter=delim), [])
        if len(fields) > best_fields:
            best_fields = len(fields)
            best_delim = delim
    return best_delim


def _row_to_dict(columns: list[str], values: tuple) -> dict:
    return {col: coerce_value(val) for col, val in zip(columns, values) if col}


def stream_xlsx_rows(path: Path, header: list[str] | None = None) -> Iterator[dict]:
    """Yield one dict per data row from an xlsx file via openpyxl read_only.

    The workbook is opened read-only (streaming) and closed when the generator
    is exhausted or garbage-collected. Fully-empty rows are skipped.

    If ``header`` is given, the file is treated as **headerless**: the provided
    (already-standardized) column names are applied positionally and the first
    row is consumed as data. Used for headerless exports like the 2024 C_DOCTYPE
    file (see source_map.json ``xlsx_header``).
    """

    workbook = load_workbook(filename=str(path), read_only=True, data_only=True)
    try:
        worksheet = workbook.active
        row_iter = worksheet.iter_rows(values_only=True)
        if header is None:
            try:
                first = next(row_iter)
            except StopIteration as exc:
                raise HeaderError(f"{path.name}: file has no rows / no header") from exc
            columns = [standardize_column_name(c) for c in first]
            if not any(columns):
                raise HeaderError(f"{path.name}: header row is empty/blank")
        else:
            columns = list(header)  # headerless: do not consume a row
        for values in row_iter:
            if values is None or all(v is None for v in values):
                continue
            yield _row_to_dict(columns, values)
    finally:
        workbook.close()


def stream_csv_rows(path: Path, delimiter: str = ";") -> Iterator[dict]:
    """Yield one dict per data row from a CSV file (utf-8-sig, ``;`` delimiter)."""

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = _csv.reader(handle, delimiter=delimiter)
        try:
            header = next(reader)
        except StopIteration as exc:
            raise HeaderError(f"{path.name}: file has no rows / no header") from exc
        columns = [standardize_column_name(c) for c in header]
        if not any(columns):
            raise HeaderError(f"{path.name}: header row is empty/blank")
        for values in reader:
            if not values or all((v is None or v.strip() == "") for v in values):
                continue
            yield _row_to_dict(columns, tuple(values))

from __future__ import annotations

from pathlib import Path

import pytest
from openpyxl import Workbook

from data_import.staging_loader.columns import coerce_value, standardize_column_name
from data_import.staging_loader.readers import (
    HeaderError,
    detect_delimiter,
    stream_csv_rows,
    stream_xlsx_rows,
)


def test_standardize_column_name_matches_legacy_rules() -> None:
    assert standardize_column_name("C_REGION_ID") == "c_region_id"
    assert standardize_column_name("  Planned Margin  ") == "planned_margin"
    assert standardize_column_name("NAME2") == "name2"
    assert standardize_column_name("A--B__C") == "a_b_c"


def test_coerce_value_blanks_to_none() -> None:
    assert coerce_value("  ") is None
    assert coerce_value("") is None
    assert coerce_value(" x ") == "x"
    assert coerce_value(0) == 0
    assert coerce_value(None) is None


def test_coerce_value_null_token_to_none() -> None:
    # DW-04: M_PRODUCT_PO.csv uses the literal token NULL for empty cells.
    assert coerce_value("NULL") is None
    assert coerce_value("  NULL ") is None
    # Not a sentinel: substrings / different case are preserved.
    assert coerce_value("devise was null") == "devise was null"
    assert coerce_value("null") == "null"
    assert coerce_value("NULLABLE") == "NULLABLE"


def _write_xlsx(path: Path, rows: list[list]) -> None:
    wb = Workbook()
    ws = wb.active
    for row in rows:
        ws.append(row)
    wb.save(path)
    wb.close()


def test_stream_xlsx_rows_yields_standardized_dicts(tmp_path: Path) -> None:
    path = tmp_path / "tiny.xlsx"
    _write_xlsx(
        path,
        [
            ["C_REGION_ID", "NAME", "DESCRIPTION"],
            [10, "  Souss ", ""],
            [11, "Casablanca", None],
        ],
    )
    rows = list(stream_xlsx_rows(path))
    assert rows == [
        {"c_region_id": 10, "name": "Souss", "description": None},
        {"c_region_id": 11, "name": "Casablanca", "description": None},
    ]


def test_stream_xlsx_rows_skips_fully_empty_rows(tmp_path: Path) -> None:
    path = tmp_path / "gaps.xlsx"
    _write_xlsx(
        path,
        [
            ["ID", "NAME"],
            [1, "a"],
            [None, None],
            [2, "b"],
        ],
    )
    rows = list(stream_xlsx_rows(path))
    assert [r["id"] for r in rows] == [1, 2]


def test_stream_csv_rows_semicolon_and_bom(tmp_path: Path) -> None:
    path = tmp_path / "tiny.csv"
    path.write_text(
        "C_REGION_ID;NAME;DESCRIPTION\r\n10; Souss ;\r\n11;Casablanca;x\r\n",
        encoding="utf-8-sig",
    )
    rows = list(stream_csv_rows(path))
    assert rows == [
        {"c_region_id": "10", "name": "Souss", "description": None},
        {"c_region_id": "11", "name": "Casablanca", "description": "x"},
    ]


def test_stream_xlsx_rows_empty_workbook_raises(tmp_path: Path) -> None:
    path = tmp_path / "empty.xlsx"
    _write_xlsx(path, [])  # no rows at all
    with pytest.raises(HeaderError):
        list(stream_xlsx_rows(path))


def test_stream_xlsx_rows_headerless_uses_explicit_columns(tmp_path: Path) -> None:
    # Mirrors the 2024 C_DOCTYPE case: no header row; first row is data.
    path = tmp_path / "headerless.xlsx"
    _write_xlsx(path, [[0, "Y"], [1, "N"], [115, "Y"]])
    rows = list(stream_xlsx_rows(path, header=["c_doctype_id", "issotrx"]))
    assert rows == [
        {"c_doctype_id": 0, "issotrx": "Y"},
        {"c_doctype_id": 1, "issotrx": "N"},
        {"c_doctype_id": 115, "issotrx": "Y"},
    ]


def test_stream_csv_rows_blank_header_raises(tmp_path: Path) -> None:
    path = tmp_path / "blank.csv"
    path.write_text(";;;\n1;a;b;c\n", encoding="utf-8-sig")
    with pytest.raises(HeaderError):
        list(stream_csv_rows(path))


def test_detect_delimiter_comma(tmp_path: Path) -> None:
    # The 2025-2026 package is comma-delimited (DW-04).
    path = tmp_path / "comma.csv"
    path.write_text("C_ORDER_ID,NAME,DESCRIPTION\r\n1,a,b\r\n", encoding="utf-8-sig")
    assert detect_delimiter(path) == ","


def test_detect_delimiter_semicolon(tmp_path: Path) -> None:
    path = tmp_path / "semi.csv"
    path.write_text("C_ORDER_ID;NAME;DESCRIPTION\r\n1;a;b\r\n", encoding="utf-8-sig")
    assert detect_delimiter(path) == ";"


def test_detect_delimiter_respects_quoted_fields(tmp_path: Path) -> None:
    # A comma inside a quoted header field must not out-vote the real ';'.
    path = tmp_path / "quoted.csv"
    path.write_text('C_ID;"NAME, LONG";DESC\r\n1;x;y\r\n', encoding="utf-8-sig")
    assert detect_delimiter(path) == ";"


def test_detect_delimiter_skips_blank_lines(tmp_path: Path) -> None:
    path = tmp_path / "lead_blank.csv"
    path.write_text("\r\n\r\nA_ID,B,C\r\n1,2,3\r\n", encoding="utf-8-sig")
    assert detect_delimiter(path) == ","


def test_detect_delimiter_empty_file_raises(tmp_path: Path) -> None:
    path = tmp_path / "empty.csv"
    path.write_text("", encoding="utf-8-sig")
    with pytest.raises(HeaderError):
        detect_delimiter(path)

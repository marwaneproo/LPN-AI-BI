"""Config-driven source map loaded from :mod:`source_map.json`."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

_MAP_PATH = Path(__file__).with_name("source_map.json")

# Window identifiers double as the staging ``_source_tag`` values.
WINDOW_2024 = "2024_xlsx"
WINDOW_CSV = "csv_2025"

# DW-02 smoke set: the 10 tiny 2024 dimension files.
SMOKE_TABLES = [
    "stg_c_region",
    "stg_c_city",
    "stg_ad_user",
    "stg_m_product_category",
    "stg_m_product_type",
    "stg_c_doctype",
    "stg_m_warehouse",
    "stg_c_paymentterm",
    "stg_m_pricelist",
    "stg_c_salesregion",
]

# DW-03 — the three giant transactional xlsx (chunked=true), ordered SMALLEST first
# so a problem surfaces on the 81 MB file before the 254 MB one. These are streamed
# in <=5,000-row batches with per-batch commit.
WINDOW_2024_GIANTS = [
    "stg_c_invoiceline",  # 2024_04, 81 MB,  ~270K rows
    "stg_m_inoutline",    # 2024_06, 132 MB, ~350K rows
    "stg_c_orderline",    # 2024_02, 254 MB, ~354K rows
]


def window_2024_small_medium() -> list[str]:
    """All 2024_xlsx-loadable staging tables EXCEPT the three giants.

    Derived from ``source_map.json``: a table qualifies if it has a 2024 xlsx
    source (``xlsx_2024`` set, not ``exclude_xlsx``) and is not ``chunked``
    (the giants). CSV-only tables (M_PRODUCT / M_PRODUCT_PO / RV_STORAGE) are
    excluded — they belong to DW-04 under ``csv_2025``.
    """

    specs = load_source_map()
    return [
        name
        for name, spec in specs.items()
        if spec.xlsx_2024 and not spec.exclude_xlsx and not spec.chunked
    ]


def window_2024_all() -> list[str]:
    """The full DW-03 2024 load order: small/medium first, giants last."""

    return window_2024_small_medium() + WINDOW_2024_GIANTS


def window_csv_all() -> list[str]:
    """The full DW-04 csv_2025 load order: small/medium first, giants last.

    Every staging table in ``source_map.json`` has a ``csv_2025`` source (the
    2025-2026 export covers all 28 entities, including the CSV-only
    M_PRODUCT / M_PRODUCT_PO / RV_STORAGE deferred from DW-03). The three giant
    line tables (chunked=true) load last so a problem surfaces on the dims first.
    """

    specs = load_source_map()
    giants = [t for t in WINDOW_2024_GIANTS if t in specs and specs[t].csv_2025]
    small_medium = [
        name
        for name, spec in specs.items()
        if spec.csv_2025 and name not in giants
    ]
    return small_medium + giants


@dataclass(frozen=True)
class SourceSpec:
    staging_table: str
    natural_key: tuple[str, ...]
    xlsx_2024: str | None
    csv_2025: str | None
    chunked: bool
    exclude_xlsx: bool
    # Explicit column order for headerless 2024 xlsx exports (e.g. C_DOCTYPE).
    # None => the xlsx has a normal header row.
    xlsx_header: tuple[str, ...] | None = None


def repo_root() -> Path:
    # .../services-python/data-import/data_import/staging_loader/source_map.py
    return Path(__file__).resolve().parents[4]


def _raw() -> dict:
    return json.loads(_MAP_PATH.read_text(encoding="utf-8"))


def load_source_map() -> dict[str, SourceSpec]:
    specs: dict[str, SourceSpec] = {}
    for entry in _raw()["sources"]:
        specs[entry["staging_table"]] = SourceSpec(
            staging_table=entry["staging_table"],
            natural_key=tuple(entry["natural_key"]),
            xlsx_2024=entry.get("xlsx_2024"),
            csv_2025=entry.get("csv_2025"),
            chunked=bool(entry.get("chunked", False)),
            exclude_xlsx=bool(entry.get("exclude_xlsx", False)),
            xlsx_header=tuple(entry["xlsx_header"]) if entry.get("xlsx_header") else None,
        )
    return specs


def source_dirs() -> tuple[Path, Path]:
    """Return absolute ``(xlsx_2024_dir, csv_2025_dir)`` resolved from repo root."""

    paths = _raw()["paths"]
    root = repo_root()
    return root / paths["xlsx_2024_dir"], root / paths["csv_2025_dir"]

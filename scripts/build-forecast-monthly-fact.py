from __future__ import annotations

"""
Build monthly sales forecasting facts in PostgreSQL from the 24-month commercial raw exports.

Sources (files 53-56 in Exported_data_through_a_drive or Exported_LPN):
  53_COMMERCIAL_ORDER_HEADER_24M.xlsx  — order headers
  54_COMMERCIAL_ORDER_LINE_24M.xlsx    — order lines (category/theme grain, not read directly)
  55_COMMERCIAL_INVOICE_HEADER_24M.xlsx — invoice headers (with ISPAID)
  56_COMMERCIAL_INVOICE_LINE_24M.xlsx  — invoice lines (category/theme grain)

Tables created in business schema (additive — existing tables are NOT touched):
  fact_sales_monthly                — company-level monthly CA
  fact_sales_monthly_by_commercial  — per-commercial drill-down
  fact_sales_monthly_by_category    — per-product-category drill-down
  fact_sales_monthly_by_theme       — per-product-theme drill-down

Architecture rules:
  - No raw data committed to git.
  - Reads from local Excel files only; never touches Oracle at request time.
  - is_partial=TRUE marks the current calendar month (excluded from ML training).
  - File 57 pre-aggregated CA columns are NOT used (many-to-many inflation issue).
  - Full atomic transaction; rolled back on any error.
  - CA in fact_sales_monthly / by_commercial = GRANDTOTAL (TTC) from headers.
  - CA in by_category / by_theme = LINENETAMT (HT) from invoice lines.
"""

import argparse
import json
import os
import shutil
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd
import psycopg2
from psycopg2 import sql as pgsql


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "data" / "Exported_LPN"
ALT_SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "data" / "Exported_data_through_a_drive"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "Youssef_Extractions" / "forecast_monthly_import"

FILE_ORDER_HEADER = "53_COMMERCIAL_ORDER_HEADER_24M.xlsx"
FILE_INVOICE_HEADER = "55_COMMERCIAL_INVOICE_HEADER_24M.xlsx"
FILE_INVOICE_LINE = "56_COMMERCIAL_INVOICE_LINE_24M.xlsx"
REQUIRED_FILES = (FILE_ORDER_HEADER, FILE_INVOICE_HEADER, FILE_INVOICE_LINE)


@dataclass
class AggregatedFacts:
    monthly: pd.DataFrame
    by_commercial: pd.DataFrame
    by_category: pd.DataFrame
    by_theme: pd.DataFrame
    partial_month: date
    source_files: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build monthly sales forecasting facts in PostgreSQL from 24-month commercial exports."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=None,
        help="Directory containing files 53/55/56. Auto-detected when omitted.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"Staging directory for CSVs and manifest (default: {DEFAULT_OUTPUT_DIR})",
    )
    parser.add_argument(
        "--skip-db",
        action="store_true",
        help="Only build staging CSVs and manifest; skip PostgreSQL import.",
    )
    args = parser.parse_args()

    source_dir = _resolve_source_dir(args.source_dir)
    output_dir = args.output_dir.resolve()

    _validate_output_path(output_dir)

    print(f"Source : {source_dir}")
    print(f"Output : {output_dir}")
    print()

    # ---- load ----
    inv_hdr = _load_invoice_headers(source_dir / FILE_INVOICE_HEADER)
    ord_hdr = _load_order_headers(source_dir / FILE_ORDER_HEADER)
    inv_lines = _load_invoice_lines(source_dir / FILE_INVOICE_LINE)

    # ---- partial month ----
    today = date.today()
    partial_month = date(today.year, today.month, 1)
    print(f"\nPartial month (is_partial=TRUE, excluded from ML training): {partial_month}\n")

    # ---- aggregate ----
    facts = _aggregate(inv_hdr, ord_hdr, inv_lines, partial_month)
    facts.source_files = list(REQUIRED_FILES)

    # ---- write staging ----
    if output_dir.exists():
        shutil.rmtree(output_dir)
    csv_dir = output_dir / "csv"
    csv_dir.mkdir(parents=True)

    print("Writing staging CSVs …")
    _write_csv(facts.monthly, csv_dir / "fact_sales_monthly.csv")
    _write_csv(facts.by_commercial, csv_dir / "fact_sales_monthly_by_commercial.csv")
    _write_csv(facts.by_category, csv_dir / "fact_sales_monthly_by_category.csv")
    _write_csv(facts.by_theme, csv_dir / "fact_sales_monthly_by_theme.csv")

    # ---- manifest ----
    manifest = _build_manifest(facts, source_dir, output_dir, partial_month)
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    print(f"Manifest -> {manifest_path}")

    # ---- DB import ----
    if not args.skip_db:
        print()
        _import_to_postgres(csv_dir)
        print("PostgreSQL import complete.")
    else:
        print("--skip-db: PostgreSQL import skipped.")

    print()
    _print_summary(facts, partial_month)


# ---------------------------------------------------------------------------
# Path helpers
# ---------------------------------------------------------------------------

def _resolve_source_dir(arg: Path | None) -> Path:
    candidates = []
    if arg is not None:
        candidates.append(arg.resolve())
    candidates += [DEFAULT_SOURCE_DIR.resolve(), ALT_SOURCE_DIR.resolve()]

    for candidate in candidates:
        if candidate.is_dir() and all((candidate / f).exists() for f in REQUIRED_FILES):
            return candidate

    # Report what was tried
    tried = "\n  ".join(str(c) for c in candidates)
    raise SystemExit(
        f"Required files ({', '.join(REQUIRED_FILES)}) not found in any candidate directory:\n  {tried}"
    )


def _validate_output_path(output_dir: Path) -> None:
    expected_parent = (REPO_ROOT / "Youssef_Extractions").resolve()
    try:
        output_dir.relative_to(expected_parent)
    except ValueError as exc:
        raise SystemExit(f"Refusing to write outside Youssef_Extractions: {output_dir}") from exc


# ---------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------

def _load_invoice_headers(path: Path) -> pd.DataFrame:
    print(f"Loading {path.name} …")
    df = pd.read_excel(path, engine="openpyxl", usecols=[
        "C_INVOICE_ID", "DATEINVOICED", "GRANDTOTAL",
        "SALESREP_ID", "COMMERCIAL_NAME", "ISPAID",
    ])
    df.columns = [str(c).upper() for c in df.columns]

    df["DATEINVOICED"] = pd.to_datetime(df["DATEINVOICED"], errors="coerce")
    df["GRANDTOTAL"] = pd.to_numeric(df["GRANDTOTAL"], errors="coerce").fillna(0.0)
    df["ISPAID"] = df["ISPAID"].astype(str).str.strip().str.upper()
    df["SALESREP_ID"] = pd.to_numeric(df["SALESREP_ID"], errors="coerce").fillna(0).astype("int64")
    df["COMMERCIAL_NAME"] = df["COMMERCIAL_NAME"].astype(str).str.strip()

    bad = df["DATEINVOICED"].isna().sum()
    if bad:
        print(f"  Dropped {bad} rows with unparseable DATEINVOICED.")
    df = df.dropna(subset=["DATEINVOICED"])

    print(f"  {len(df):,} rows | {df['DATEINVOICED'].min().date()} -> {df['DATEINVOICED'].max().date()}")
    return df


def _load_order_headers(path: Path) -> pd.DataFrame:
    print(f"Loading {path.name} …")
    df = pd.read_excel(path, engine="openpyxl", usecols=[
        "C_ORDER_ID", "DATEORDERED", "GRANDTOTAL",
    ])
    df.columns = [str(c).upper() for c in df.columns]

    df["DATEORDERED"] = pd.to_datetime(df["DATEORDERED"], errors="coerce")
    df["GRANDTOTAL"] = pd.to_numeric(df["GRANDTOTAL"], errors="coerce").fillna(0.0)

    bad = df["DATEORDERED"].isna().sum()
    if bad:
        print(f"  Dropped {bad} rows with unparseable DATEORDERED.")
    df = df.dropna(subset=["DATEORDERED"])

    print(f"  {len(df):,} rows | {df['DATEORDERED'].min().date()} ->{df['DATEORDERED'].max().date()}")
    return df


def _load_invoice_lines(path: Path) -> pd.DataFrame:
    print(f"Loading {path.name} …")
    df = pd.read_excel(path, engine="openpyxl", usecols=[
        "C_INVOICELINE_ID", "DATEINVOICED", "LINENETAMT",
        "M_PRODUCT_CATEGORY_ID", "PRODUCT_CATEGORY",
        "M_PRODUCT_THEME_ID", "PRODUCT_THEME",
    ])
    df.columns = [str(c).upper() for c in df.columns]

    df["DATEINVOICED"] = pd.to_datetime(df["DATEINVOICED"], errors="coerce")
    df["LINENETAMT"] = pd.to_numeric(df["LINENETAMT"], errors="coerce").fillna(0.0)
    # Sentinel 0 for NULL IDs so they can appear as a valid group and satisfy NOT NULL PK
    df["M_PRODUCT_CATEGORY_ID"] = pd.to_numeric(df["M_PRODUCT_CATEGORY_ID"], errors="coerce").fillna(0).astype("int64")
    df["M_PRODUCT_THEME_ID"] = pd.to_numeric(df["M_PRODUCT_THEME_ID"], errors="coerce").fillna(0).astype("int64")
    df["PRODUCT_CATEGORY"] = df["PRODUCT_CATEGORY"].astype(str).str.strip().replace("nan", "Non catégorisé")
    df["PRODUCT_THEME"] = df["PRODUCT_THEME"].astype(str).str.strip().replace("nan", "Sans thème")

    df = df.dropna(subset=["DATEINVOICED"])
    print(f"  {len(df):,} rows | {df['DATEINVOICED'].min().date()} ->{df['DATEINVOICED'].max().date()}")
    return df


# ---------------------------------------------------------------------------
# Aggregation
# ---------------------------------------------------------------------------

def _month_first(series: pd.Series) -> pd.Series:
    """Convert datetime series to first-of-month date (Python date objects)."""
    return series.dt.to_period("M").dt.to_timestamp().dt.date


def _aggregate(
    inv_hdr: pd.DataFrame,
    ord_hdr: pd.DataFrame,
    inv_lines: pd.DataFrame,
    partial_month: date,
) -> AggregatedFacts:

    # -- invoice header: pre-compute paid/unpaid columns --
    inv = inv_hdr.copy()
    inv["month"] = _month_first(inv["DATEINVOICED"])
    inv["ca_paye"] = inv["GRANDTOTAL"].where(inv["ISPAID"] == "Y", 0.0)
    inv["ca_impaye"] = inv["GRANDTOTAL"].where(inv["ISPAID"] != "Y", 0.0)

    # company monthly from invoices
    monthly_inv = (
        inv.groupby("month", as_index=False)
        .agg(
            ca_facture=("GRANDTOTAL", "sum"),
            n_factures=("C_INVOICE_ID", "count"),
            ca_facture_paye=("ca_paye", "sum"),
            ca_facture_impaye=("ca_impaye", "sum"),
        )
    )

    # company monthly from orders
    ord = ord_hdr.copy()
    ord["month"] = _month_first(ord["DATEORDERED"])
    monthly_ord = (
        ord.groupby("month", as_index=False)
        .agg(
            ca_commande=("GRANDTOTAL", "sum"),
            n_commandes=("C_ORDER_ID", "count"),
        )
    )

    # merge and fill
    monthly = monthly_inv.merge(monthly_ord, on="month", how="outer")
    monthly = monthly.sort_values("month").reset_index(drop=True)
    for col in ["ca_facture", "n_factures", "ca_facture_paye", "ca_facture_impaye"]:
        monthly[col] = monthly[col].fillna(0.0)
    for col in ["ca_commande", "n_commandes"]:
        monthly[col] = monthly[col].fillna(0.0)
    monthly["n_factures"] = monthly["n_factures"].astype("int64")
    monthly["n_commandes"] = monthly["n_commandes"].astype("int64")
    monthly["is_partial"] = monthly["month"].apply(lambda m: m >= partial_month)
    # Explicit column order must match the COPY list in _create_fact_sales_monthly
    monthly = monthly[[
        "month", "ca_facture", "n_factures", "ca_commande", "n_commandes",
        "ca_facture_paye", "ca_facture_impaye", "is_partial",
    ]]

    # by_commercial
    by_commercial = (
        inv.groupby(["month", "SALESREP_ID", "COMMERCIAL_NAME"], as_index=False)
        .agg(
            ca_facture=("GRANDTOTAL", "sum"),
            n_factures=("C_INVOICE_ID", "count"),
        )
        .rename(columns={"SALESREP_ID": "salesrep_id", "COMMERCIAL_NAME": "commercial_name"})
    )
    by_commercial["n_factures"] = by_commercial["n_factures"].astype("int64")
    by_commercial["is_partial"] = by_commercial["month"].apply(lambda m: m >= partial_month)
    by_commercial = by_commercial.sort_values(["month", "salesrep_id"]).reset_index(drop=True)
    by_commercial = by_commercial[[
        "month", "salesrep_id", "commercial_name", "ca_facture", "n_factures", "is_partial",
    ]]

    # by_category (from invoice lines)
    lines = inv_lines.copy()
    lines["month"] = _month_first(lines["DATEINVOICED"])

    by_category = (
        lines.groupby(["month", "M_PRODUCT_CATEGORY_ID", "PRODUCT_CATEGORY"], as_index=False)
        .agg(ca_facture=("LINENETAMT", "sum"))
        .rename(columns={"M_PRODUCT_CATEGORY_ID": "category_id", "PRODUCT_CATEGORY": "category_name"})
    )
    by_category["is_partial"] = by_category["month"].apply(lambda m: m >= partial_month)
    by_category = by_category.sort_values(["month", "category_id"]).reset_index(drop=True)
    by_category = by_category[["month", "category_id", "category_name", "ca_facture", "is_partial"]]

    # by_theme (from invoice lines — skip zero-theme rows to avoid noise)
    by_theme = (
        lines[lines["M_PRODUCT_THEME_ID"] != 0]
        .groupby(["month", "M_PRODUCT_THEME_ID", "PRODUCT_THEME"], as_index=False)
        .agg(ca_facture=("LINENETAMT", "sum"))
        .rename(columns={"M_PRODUCT_THEME_ID": "theme_id", "PRODUCT_THEME": "theme_name"})
    )
    by_theme["is_partial"] = by_theme["month"].apply(lambda m: m >= partial_month)
    by_theme = by_theme.sort_values(["month", "theme_id"]).reset_index(drop=True)
    by_theme = by_theme[["month", "theme_id", "theme_name", "ca_facture", "is_partial"]]

    return AggregatedFacts(
        monthly=monthly,
        by_commercial=by_commercial,
        by_category=by_category,
        by_theme=by_theme,
        partial_month=partial_month,
    )


# ---------------------------------------------------------------------------
# CSV staging
# ---------------------------------------------------------------------------

def _write_csv(df: pd.DataFrame, path: Path) -> None:
    df.to_csv(path, index=False, encoding="utf-8-sig")
    print(f"  {path.name}  ({len(df):,} rows)")


# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------

def _build_manifest(
    facts: AggregatedFacts,
    source_dir: Path,
    output_dir: Path,
    partial_month: date,
) -> dict[str, Any]:
    complete = facts.monthly[~facts.monthly["is_partial"]]
    return {
        "snapshot_id": f"lpn-forecast-monthly-{datetime.now(timezone.utc).strftime('%Y%m%d')}-v1",
        "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "source_dir": str(source_dir),
        "source_files": facts.source_files,
        "partial_month": str(partial_month),
        "tables": [
            {
                "name": "fact_sales_monthly",
                "csv_file": "csv/fact_sales_monthly.csv",
                "row_count": int(len(facts.monthly)),
                "complete_months": int(len(complete)),
                "partial_months": int(facts.monthly["is_partial"].sum()),
                "month_min": str(facts.monthly["month"].min()) if len(facts.monthly) else None,
                "month_max": str(facts.monthly["month"].max()) if len(facts.monthly) else None,
                "ca_facture_total_complete": float(complete["ca_facture"].sum()),
                "ca_commande_total_complete": float(complete["ca_commande"].sum()),
            },
            {
                "name": "fact_sales_monthly_by_commercial",
                "csv_file": "csv/fact_sales_monthly_by_commercial.csv",
                "row_count": int(len(facts.by_commercial)),
                "distinct_commercials": int(facts.by_commercial["salesrep_id"].nunique()),
            },
            {
                "name": "fact_sales_monthly_by_category",
                "csv_file": "csv/fact_sales_monthly_by_category.csv",
                "row_count": int(len(facts.by_category)),
                "distinct_categories": int(facts.by_category["category_id"].nunique()),
            },
            {
                "name": "fact_sales_monthly_by_theme",
                "csv_file": "csv/fact_sales_monthly_by_theme.csv",
                "row_count": int(len(facts.by_theme)),
                "distinct_themes": int(facts.by_theme["theme_id"].nunique()),
            },
        ],
        "notes": (
            "Additive forecasting facts; does not touch existing business tables. "
            "CA in monthly/by_commercial = GRANDTOTAL (TTC) from invoice/order headers. "
            "CA in by_category/by_theme = LINENETAMT (HT) from invoice lines. "
            "is_partial=TRUE rows cover the current calendar month and must be excluded from ML training."
        ),
    }


# ---------------------------------------------------------------------------
# PostgreSQL import
# ---------------------------------------------------------------------------

def _import_to_postgres(csv_dir: Path) -> None:
    conn = psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5433"),
        dbname=os.getenv("POSTGRES_DB", "lpn_ai_bi"),
        user=os.getenv("POSTGRES_USER", "lpn_app_admin"),
        password=(
            os.getenv("POSTGRES_PASSWORD")
            or os.getenv("POSTGRES_APP_ADMIN_PASSWORD")
            or "change_me_app_admin"
        ),
    )
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            print("Creating forecast fact tables …")
            _create_fact_sales_monthly(cur, csv_dir)
            _create_fact_by_commercial(cur, csv_dir)
            _create_fact_by_category(cur, csv_dir)
            _create_fact_by_theme(cur, csv_dir)
            _grant_readonly(cur)
        conn.commit()
        print("Transaction committed.")
    except Exception:
        conn.rollback()
        print("ERROR — transaction rolled back.")
        raise
    finally:
        conn.close()


def _copy_csv(cur: Any, table: str, columns: list[str], csv_path: Path) -> None:
    col_sql = pgsql.SQL(", ").join(pgsql.Identifier(c) for c in columns)
    stmt = pgsql.SQL(
        "COPY business.{} ({}) FROM STDIN WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')"
    ).format(pgsql.Identifier(table), col_sql)
    with csv_path.open("r", encoding="utf-8-sig", newline="") as fh:
        cur.copy_expert(stmt, fh)


def _create_fact_sales_monthly(cur: Any, csv_dir: Path) -> None:
    cur.execute("DROP TABLE IF EXISTS business.fact_sales_monthly CASCADE")
    cur.execute("""
        CREATE TABLE business.fact_sales_monthly (
            month              DATE          NOT NULL PRIMARY KEY,
            ca_facture         NUMERIC(18,4) NOT NULL DEFAULT 0,
            n_factures         INTEGER       NOT NULL DEFAULT 0,
            ca_commande        NUMERIC(18,4) NOT NULL DEFAULT 0,
            n_commandes        INTEGER       NOT NULL DEFAULT 0,
            ca_facture_paye    NUMERIC(18,4) NOT NULL DEFAULT 0,
            ca_facture_impaye  NUMERIC(18,4) NOT NULL DEFAULT 0,
            is_partial         BOOLEAN       NOT NULL DEFAULT FALSE
        )
    """)
    cur.execute("""
        COMMENT ON TABLE business.fact_sales_monthly IS
        'Monthly CA facts (company level) from 24M commercial exports (files 53+55).
CA = GRANDTOTAL (TTC). is_partial=TRUE: current calendar month, exclude from ML training.'
    """)
    _copy_csv(cur, "fact_sales_monthly",
              ["month", "ca_facture", "n_factures", "ca_commande", "n_commandes",
               "ca_facture_paye", "ca_facture_impaye", "is_partial"],
              csv_dir / "fact_sales_monthly.csv")
    print("  fact_sales_monthly OK")


def _create_fact_by_commercial(cur: Any, csv_dir: Path) -> None:
    cur.execute("DROP TABLE IF EXISTS business.fact_sales_monthly_by_commercial CASCADE")
    cur.execute("""
        CREATE TABLE business.fact_sales_monthly_by_commercial (
            month            DATE          NOT NULL,
            salesrep_id      BIGINT        NOT NULL,
            commercial_name  TEXT,
            ca_facture       NUMERIC(18,4) NOT NULL DEFAULT 0,
            n_factures       INTEGER       NOT NULL DEFAULT 0,
            is_partial       BOOLEAN       NOT NULL DEFAULT FALSE,
            PRIMARY KEY (month, salesrep_id)
        )
    """)
    cur.execute("CREATE INDEX ON business.fact_sales_monthly_by_commercial (salesrep_id)")
    cur.execute("""
        COMMENT ON TABLE business.fact_sales_monthly_by_commercial IS
        'Monthly CA by commercial (salesrep) from invoice headers (file 55). CA = GRANDTOTAL (TTC).'
    """)
    _copy_csv(cur, "fact_sales_monthly_by_commercial",
              ["month", "salesrep_id", "commercial_name", "ca_facture", "n_factures", "is_partial"],
              csv_dir / "fact_sales_monthly_by_commercial.csv")
    print("  fact_sales_monthly_by_commercial OK")


def _create_fact_by_category(cur: Any, csv_dir: Path) -> None:
    cur.execute("DROP TABLE IF EXISTS business.fact_sales_monthly_by_category CASCADE")
    cur.execute("""
        CREATE TABLE business.fact_sales_monthly_by_category (
            month          DATE          NOT NULL,
            category_id    BIGINT        NOT NULL,
            category_name  TEXT,
            ca_facture     NUMERIC(18,4) NOT NULL DEFAULT 0,
            is_partial     BOOLEAN       NOT NULL DEFAULT FALSE,
            PRIMARY KEY (month, category_id)
        )
    """)
    cur.execute("CREATE INDEX ON business.fact_sales_monthly_by_category (category_id)")
    cur.execute("""
        COMMENT ON TABLE business.fact_sales_monthly_by_category IS
        'Monthly CA by product category from invoice lines (file 56). CA = LINENETAMT (HT).
category_id=0 means uncategorized.'
    """)
    _copy_csv(cur, "fact_sales_monthly_by_category",
              ["month", "category_id", "category_name", "ca_facture", "is_partial"],
              csv_dir / "fact_sales_monthly_by_category.csv")
    print("  fact_sales_monthly_by_category OK")


def _create_fact_by_theme(cur: Any, csv_dir: Path) -> None:
    cur.execute("DROP TABLE IF EXISTS business.fact_sales_monthly_by_theme CASCADE")
    cur.execute("""
        CREATE TABLE business.fact_sales_monthly_by_theme (
            month      DATE          NOT NULL,
            theme_id   BIGINT        NOT NULL,
            theme_name TEXT,
            ca_facture NUMERIC(18,4) NOT NULL DEFAULT 0,
            is_partial BOOLEAN       NOT NULL DEFAULT FALSE,
            PRIMARY KEY (month, theme_id)
        )
    """)
    cur.execute("CREATE INDEX ON business.fact_sales_monthly_by_theme (theme_id)")
    cur.execute("""
        COMMENT ON TABLE business.fact_sales_monthly_by_theme IS
        'Monthly CA by product theme from invoice lines (file 56). CA = LINENETAMT (HT).
Rows with no theme (theme_id=0) are excluded.'
    """)
    _copy_csv(cur, "fact_sales_monthly_by_theme",
              ["month", "theme_id", "theme_name", "ca_facture", "is_partial"],
              csv_dir / "fact_sales_monthly_by_theme.csv")
    print("  fact_sales_monthly_by_theme OK")


def _grant_readonly(cur: Any) -> None:
    cur.execute("GRANT USAGE ON SCHEMA business TO lpn_ai_readonly")
    cur.execute("GRANT SELECT ON ALL TABLES IN SCHEMA business TO lpn_ai_readonly")
    print("  Granted SELECT on business.* to lpn_ai_readonly")


# ---------------------------------------------------------------------------
# Console summary
# ---------------------------------------------------------------------------

def _print_summary(facts: AggregatedFacts, partial_month: date) -> None:
    complete = facts.monthly[~facts.monthly["is_partial"]]
    print("=" * 65)
    print("TASK ML-1 SUMMARY")
    print("=" * 65)
    print(f"fact_sales_monthly:")
    print(f"  Total months    : {len(facts.monthly)}")
    print(f"  Complete months : {len(complete)}  (is_partial=FALSE)")
    print(f"  Partial months  : {int(facts.monthly['is_partial'].sum())}  (is_partial=TRUE, month >= {partial_month})")
    if len(complete):
        print(f"  Month range     : {complete['month'].min()} ->{complete['month'].max()}")
        print(f"  CA facturé      : {complete['ca_facture'].sum():>16,.2f}  (GRANDTOTAL TTC, complete months)")
        print(f"  CA commandé     : {complete['ca_commande'].sum():>16,.2f}  (GRANDTOTAL TTC, complete months)")
    print()
    print(f"fact_sales_monthly_by_commercial : {len(facts.by_commercial):,} rows | {facts.by_commercial['salesrep_id'].nunique()} commercials")
    print(f"fact_sales_monthly_by_category   : {len(facts.by_category):,} rows | {facts.by_category['category_id'].nunique()} categories")
    print(f"fact_sales_monthly_by_theme      : {len(facts.by_theme):,} rows | {facts.by_theme['theme_id'].nunique()} themes")


if __name__ == "__main__":
    main()

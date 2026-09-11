from __future__ import annotations

import argparse
import csv
import json
import os
import re
import shutil
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import pandas as pd
import psycopg2
from psycopg2 import sql


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "3rd_Extraction"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "Youssef_Extractions" / "vente_bi_enrichment_import"


@dataclass(frozen=True)
class EnrichmentSource:
    table_name: str
    source_file: str
    required_headers: tuple[str, ...]
    notes: str
    optional: bool = False


SOURCES: tuple[EnrichmentSource, ...] = (
    EnrichmentSource(
        table_name="AD_USER",
        source_file="19_AD_USER_SALESREPS.xlsx",
        required_headers=("AD_USER_ID", "NAME"),
        notes="Sales representatives used to resolve C_ORDER/C_INVOICE.SALESREP_ID to real names.",
    ),
    EnrichmentSource(
        table_name="M_PRODUCT_PO",
        source_file="20_M_PRODUCT_PO.xlsx",
        required_headers=("M_PRODUCT_ID", "C_BPARTNER_ID", "ISCURRENTVENDOR"),
        notes="Product-supplier relation. Primary supplier view prefers current active vendor rows.",
    ),
    EnrichmentSource(
        table_name="C_BPARTNER_VENDOR",
        source_file="21_C_BPARTNER_VENDORS.xlsx",
        required_headers=("C_BPARTNER_ID", "NAME", "ISVENDOR"),
        notes="Vendor business partners used for supplier labels.",
    ),
    EnrichmentSource(
        table_name="PRODUCTS_WITHOUT_SUPPLIER",
        source_file="22_PRODUCTS_WITHOUT_SUPPLIER.xlsx",
        required_headers=("M_PRODUCT_ID", "NAME"),
        notes="Diagnostic list of sold products without supplier relation.",
        optional=True,
    ),
    EnrichmentSource(
        table_name="C_BP_GROUP",
        source_file="23_C_BP_GROUP.xlsx",
        required_headers=("C_BP_GROUP_ID", "NAME"),
        notes="Business partner group labels. Optional because the current export may be unavailable or mis-saved.",
        optional=True,
    ),
    EnrichmentSource(
        table_name="M_PRODUCT_TYPE",
        source_file="24_M_PRODUCT_TYPE.xlsx",
        required_headers=("M_PRODUCT_TYPE_ID", "NAME"),
        notes="Product type labels for later product analysis.",
        optional=True,
    ),
    EnrichmentSource(
        table_name="M_PRODUCT_THEME",
        source_file="25_M_PRODUCT_THEME.xlsx",
        required_headers=("M_PRODUCT_THEME_ID", "NAME"),
        notes="Product theme labels for later product analysis.",
        optional=True,
    ),
    EnrichmentSource(
        table_name="M_PRODUCT_COLLECTION",
        source_file="26_M_PRODUCT_COLLECTION.xlsx",
        required_headers=("M_PRODUCT_COLLECTION_ID", "NAME"),
        notes="Product collection labels for later product analysis.",
        optional=True,
    ),
)


INTEGER_NAME_PATTERNS = (
    re.compile(r"(^|_)ID$"),
    re.compile(r"(^|_)ID_\d+$"),
    re.compile(r"(^|_)CREATEDBY$"),
    re.compile(r"(^|_)UPDATEDBY$"),
    re.compile(r"(^|_)DELIVERYTIME_"),
    re.compile(r"(^|_)QUALITYRATING$"),
)

NUMERIC_TOKENS = (
    "AMT",
    "AMOUNT",
    "TOTAL",
    "PRICE",
    "QTY",
    "QUANTITY",
    "RATE",
    "VOLUME",
    "WEIGHT",
    "COST",
    "LIMIT",
    "MARGIN",
)

TEXT_FORCE_TOKENS = (
    "NAME",
    "VALUE",
    "DESCRIPTION",
    "EMAIL",
    "URL",
    "CODE",
    "NO",
    "NUMBER",
    "UPC",
    "STATUS",
    "TYPE",
    "RULE",
    "TAXID",
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Import 3rd-extraction BI enrichment tables into PostgreSQL.")
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--skip-db", action="store_true", help="Only build CSV staging files and manifest.")
    args = parser.parse_args()

    source_dir = args.source_dir.resolve()
    output_dir = args.output_dir.resolve()
    _validate_paths(source_dir, output_dir)

    if output_dir.exists():
        shutil.rmtree(output_dir)
    csv_dir = output_dir / "csv"
    csv_dir.mkdir(parents=True, exist_ok=True)

    manifest_tables: list[dict[str, Any]] = []
    skipped: list[dict[str, str]] = []
    for source in SOURCES:
        source_path = source_dir / source.source_file
        if not source_path.exists():
            if source.optional:
                skipped.append({"table": source.table_name, "reason": f"missing file {source.source_file}"})
                continue
            raise SystemExit(f"Required source file is missing: {source_path}")

        df = _read_first_non_empty_sheet(source_path)
        df.columns = [_clean_header(column) for column in df.columns]
        missing = [header for header in source.required_headers if header not in df.columns]
        if missing:
            if source.optional:
                skipped.append(
                    {
                        "table": source.table_name,
                        "reason": f"missing required headers {missing}; source likely not this table",
                    }
                )
                continue
            raise SystemExit(f"{source.source_file} is missing required headers for {source.table_name}: {missing}")

        csv_path = csv_dir / f"{source.table_name}.csv"
        df = _normalize_dataframe(df)
        df.to_csv(csv_path, index=False, encoding="utf-8-sig", quoting=csv.QUOTE_MINIMAL)
        manifest_tables.append(
            {
                "name": source.table_name,
                "csv_file": f"csv/{source.table_name}.csv",
                "source_file": source.source_file,
                "row_count": int(len(df)),
                "columns": list(df.columns),
                "notes": source.notes,
            }
        )

    manifest = {
        "snapshot_id": "lpn-vente-bi-enrichment-20260521-v1",
        "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "source_dir": str(source_dir),
        "tables": manifest_tables,
        "skipped": skipped,
        "notes": (
            "Additive BI enrichment import for Analyse de vente. Does not replace the canonical vente snapshot."
        ),
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")

    if not args.skip_db:
        _import_to_postgres(output_dir, manifest)

    print(f"Built enrichment staging folder: {output_dir}")
    print(f"Imported tables: {len(manifest_tables)}")
    if skipped:
        print("Skipped optional tables:")
        for item in skipped:
            print(f"  - {item['table']}: {item['reason']}")


def _validate_paths(source_dir: Path, output_dir: Path) -> None:
    if not source_dir.is_dir():
        raise SystemExit(f"Source directory does not exist: {source_dir}")
    expected_parent = (REPO_ROOT / "Youssef_Extractions").resolve()
    try:
        output_dir.relative_to(expected_parent)
    except ValueError as exc:
        raise SystemExit(f"Refusing to write outside Youssef_Extractions: {output_dir}") from exc


def _read_first_non_empty_sheet(path: Path) -> pd.DataFrame:
    workbook = pd.ExcelFile(path)
    for sheet in workbook.sheet_names:
        df = pd.read_excel(path, sheet_name=sheet)
        if len(df) > 0 and len(df.columns) > 0:
            return df
    raise SystemExit(f"No non-empty sheet found in {path}")


def _clean_header(value: Any) -> str:
    return re.sub(r"[^A-Z0-9_]+", "_", str(value).strip().upper()).strip("_")


def _normalize_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    normalized = df.copy()
    normalized = normalized.where(pd.notna(normalized), "")
    for column in normalized.columns:
        header = str(column).upper()
        if _is_integer_column(header):
            normalized[column] = normalized[column].map(_normalize_integer_cell)
        elif _is_numeric_column(header):
            normalized[column] = normalized[column].map(_normalize_decimal_cell)
        elif _is_timestamp_column(header):
            normalized[column] = normalized[column].map(_normalize_timestamp_cell)
        else:
            normalized[column] = normalized[column].map(_normalize_text_cell)
    return normalized


def _normalize_integer_cell(value: Any) -> str:
    text = _normalize_text_cell(value)
    if not text:
        return ""
    try:
        decimal = Decimal(text)
    except InvalidOperation:
        return text
    if decimal == decimal.to_integral_value():
        return str(decimal.to_integral_value())
    return text


def _normalize_decimal_cell(value: Any) -> str:
    text = _normalize_text_cell(value)
    if not text:
        return ""
    try:
        return format(Decimal(text), "f")
    except InvalidOperation:
        return text


def _normalize_timestamp_cell(value: Any) -> str:
    text = _normalize_text_cell(value)
    return "" if text.lower() in {"nat", "nan", "none"} else text


def _normalize_text_cell(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return "" if text.lower() in {"nan", "nat", "none"} else text


def _import_to_postgres(output_dir: Path, manifest: dict[str, Any]) -> None:
    connection = psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5433"),
        dbname=os.getenv("POSTGRES_DB", "lpn_ai_bi"),
        user=os.getenv("POSTGRES_USER", "lpn_app_admin"),
        password=os.getenv("POSTGRES_PASSWORD")
        or os.getenv("POSTGRES_APP_ADMIN_PASSWORD")
        or "change_me_app_admin",
    )
    connection.autocommit = False
    try:
        with connection.cursor() as cursor:
            cursor.execute("CREATE SCHEMA IF NOT EXISTS business")
            for table in manifest["tables"]:
                _replace_table(cursor, output_dir, table)
            _create_enrichment_views(cursor)
            cursor.execute("GRANT USAGE ON SCHEMA business TO lpn_ai_readonly")
            cursor.execute("GRANT SELECT ON ALL TABLES IN SCHEMA business TO lpn_ai_readonly")
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _replace_table(cursor: Any, output_dir: Path, table: dict[str, Any]) -> None:
    table_name = table["name"].lower()
    csv_path = output_dir / table["csv_file"]
    headers, samples = _read_csv_sample(csv_path)
    columns = [_infer_column(header, [row[index] for row in samples]) for index, header in enumerate(headers)]
    cursor.execute(
        sql.SQL("DROP TABLE IF EXISTS business.{} CASCADE").format(sql.Identifier(table_name))
    )
    cursor.execute(
        sql.SQL("CREATE TABLE business.{} ({})").format(
            sql.Identifier(table_name),
            sql.SQL(", ").join(
                sql.SQL("{} {}").format(sql.Identifier(column["name"]), sql.SQL(column["type"]))
                for column in columns
            ),
        )
    )
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        cursor.copy_expert(
            sql.SQL("COPY business.{} ({}) FROM STDIN WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')").format(
                sql.Identifier(table_name),
                sql.SQL(", ").join(sql.Identifier(header.lower()) for header in headers),
            ),
            handle,
        )

    for column in _index_columns(headers):
        cursor.execute(
            sql.SQL("CREATE INDEX {} ON business.{} ({})").format(
                sql.Identifier(f"idx_{table_name}_{column.lower()}"),
                sql.Identifier(table_name),
                sql.Identifier(column.lower()),
            )
        )


def _read_csv_sample(path: Path, sample_rows: int = 2000) -> tuple[list[str], list[list[str]]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.reader(handle)
        headers = next(reader)
        rows = []
        for row in reader:
            rows.append(row + [""] * (len(headers) - len(row)))
            if len(rows) >= sample_rows:
                break
    return headers, rows


def _infer_column(header: str, values: list[str]) -> dict[str, str]:
    normalized = header.upper()
    non_empty = [str(value).strip() for value in values if str(value).strip()]
    if _is_timestamp_column(normalized):
        return {"name": header.lower(), "type": "timestamp"}
    if _is_integer_column(normalized) and _all_integer(non_empty):
        return {"name": header.lower(), "type": "bigint"}
    if _is_numeric_column(normalized) and _all_decimal(non_empty):
        return {"name": header.lower(), "type": "numeric"}
    return {"name": header.lower(), "type": "text"}


def _is_timestamp_column(header: str) -> bool:
    return header in {"CREATED", "UPDATED", "PRICEEFFECTIVE", "DATE_FIN_COM", "DATE_CREATION"} or header.startswith("DATE")


def _is_integer_column(header: str) -> bool:
    if header.startswith("IS") and len(header) > 2:
        return False
    if any(token in header for token in TEXT_FORCE_TOKENS):
        return False
    return any(pattern.search(header) for pattern in INTEGER_NAME_PATTERNS)


def _is_numeric_column(header: str) -> bool:
    if header.startswith("IS") and len(header) > 2:
        return False
    if any(token in header for token in TEXT_FORCE_TOKENS):
        return False
    return any(token in header for token in NUMERIC_TOKENS)


def _all_integer(values: list[str]) -> bool:
    if not values:
        return True
    for value in values:
        try:
            decimal = Decimal(value)
        except InvalidOperation:
            return False
        if decimal != decimal.to_integral_value():
            return False
    return True


def _all_decimal(values: list[str]) -> bool:
    if not values:
        return True
    for value in values:
        try:
            Decimal(value)
        except InvalidOperation:
            return False
    return True


def _index_columns(headers: list[str]) -> list[str]:
    wanted = {
        "AD_USER_ID",
        "M_PRODUCT_ID",
        "C_BPARTNER_ID",
        "M_PRODUCT_CATEGORY_ID",
        "M_PRODUCT_TYPE_ID",
        "M_PRODUCT_THEME_ID",
        "M_PRODUCT_COLLECTION_ID",
    }
    return [header for header in headers if header.upper() in wanted]


def _create_enrichment_views(cursor: Any) -> None:
    cursor.execute(
        """
        CREATE OR REPLACE VIEW business.v_salesrep_user AS
        SELECT
            ad_user_id AS salesrep_id,
            NULLIF(name, '') AS salesrep_name,
            NULLIF(email, '') AS salesrep_email,
            NULLIF(description, '') AS salesrep_description,
            isactive
        FROM business.ad_user
        """
    )
    cursor.execute(
        """
        CREATE OR REPLACE VIEW business.v_product_primary_supplier AS
        SELECT
            ranked.m_product_id,
            ranked.c_bpartner_id AS supplier_id,
            COALESCE(NULLIF(v.name, ''), 'Fournisseur ' || ranked.c_bpartner_id) AS supplier_name,
            ranked.iscurrentvendor,
            ranked.isactive,
            ranked.pricepo,
            ranked.pricelastpo,
            ranked.vendorproductno
        FROM (
            SELECT
                po.*,
                ROW_NUMBER() OVER (
                    PARTITION BY po.m_product_id
                    ORDER BY
                        CASE WHEN po.iscurrentvendor = 'Y' THEN 0 ELSE 1 END,
                        CASE WHEN po.isactive = 'Y' THEN 0 ELSE 1 END,
                        po.updated DESC NULLS LAST,
                        po.created DESC NULLS LAST,
                        po.c_bpartner_id
                ) AS rn
            FROM business.m_product_po po
        ) ranked
        LEFT JOIN business.c_bpartner_vendor v ON v.c_bpartner_id = ranked.c_bpartner_id
        WHERE ranked.rn = 1
        """
    )
    cursor.execute("GRANT SELECT ON business.v_salesrep_user TO lpn_ai_readonly")
    cursor.execute("GRANT SELECT ON business.v_product_primary_supplier TO lpn_ai_readonly")


if __name__ == "__main__":
    main()

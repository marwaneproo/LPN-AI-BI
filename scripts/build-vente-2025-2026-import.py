from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
from dataclasses import dataclass
from datetime import date, datetime, time, timezone
from pathlib import Path
from typing import Any

import openpyxl


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "data" / "Exported_data_through_a_drive"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "Youssef_Extractions" / "vente_2025_2026_import"
FALLBACK_HEADER_DIRS = (
    REPO_ROOT / "Youssef_Extractions" / "vente_clean_import" / "csv",
    REPO_ROOT / "Youssef_Extractions" / "vente_bi_enrichment_import" / "csv",
)

VENTE_2025_2026_FILTER = (
    "Vente 2025+2026 extraction. Target range: DATE '2025-01-01' inclusive to "
    "DATE '2026-07-01' exclusive. Orders use ISSOTRX='Y', DOCSTATUS IN ('CO','CL'), "
    "C_DOCTYPETARGET_ID IN (1000028,1000034,1000032,1000031). Invoices use "
    "ISSOTRX='Y', DOCSTATUS='CO', C_DOCTYPE_ID IN (1000002,1000003,1000004)."
)


@dataclass(frozen=True)
class TableSpec:
    table_name: str
    source_files: tuple[str, ...]
    required_headers: tuple[str, ...]
    filter_used: str
    notes: str = ""
    fallback_header_csv: str | None = None
    optional: bool = False


TABLE_SPECS: tuple[TableSpec, ...] = (
    TableSpec("LPN_EXPORT_CONTROL_COUNTS", ("00_CONTROL_COUNTS_BY_QUARTER.xlsx",), ("PERIOD_NAME", "ORDER_COUNT"), "Control counts by quarter for the export package."),
    TableSpec("C_ORDER", ("01_C_ORDER_2025_2026.xlsx",), ("C_ORDER_ID", "DATEORDERED", "GRANDTOTAL"), VENTE_2025_2026_FILTER),
    TableSpec(
        "C_ORDERLINE",
        (
            "04_C_ORDERLINE_2025_Q1.xlsx",
            "05_C_ORDERLINE_2025_Q2.xlsx",
            "06_C_ORDERLINE_2025_Q3.xlsx",
            "07_ORDERLINE_2025_Q4.xlsx",
            "08_C_ORDERLINE_2026_Q1.xlsx",
            "09_C_ORDERLINE_2026_Q2.xlsx",
        ),
        ("C_ORDERLINE_ID", "C_ORDER_ID", "M_PRODUCT_ID", "LINENETAMT"),
        VENTE_2025_2026_FILTER + " Quarterly order-line chunks combined locally.",
    ),
    TableSpec("C_INVOICE", ("02_C_INVOICE_2025_2026.xlsx",), ("C_INVOICE_ID", "DATEINVOICED", "GRANDTOTAL"), VENTE_2025_2026_FILTER),
    TableSpec(
        "C_INVOICELINE",
        (
            "10_C_INVOICELINE_2025_Q1.xlsx",
            "11_C_INVOICELINE_2025_Q2.xlsx",
            "12_C_INVOICELINE_2025_Q3.xlsx",
            "13_C_INVOICELINE_2025_Q4.xlsx",
            "14_C_INVOICELINE_2026_Q1.xlsx",
            "15_C_INVOICELINE_2026_Q2.xlsx",
        ),
        ("C_INVOICELINE_ID", "C_INVOICE_ID", "M_PRODUCT_ID", "LINENETAMT"),
        VENTE_2025_2026_FILTER + " Quarterly invoice-line chunks combined locally.",
    ),
    TableSpec("M_INOUT", ("03_M_INOUT_2025_2026.xlsx",), ("M_INOUT_ID", "C_ORDER_ID", "MOVEMENTDATE"), "Delivery headers linked to selected vente orders."),
    TableSpec(
        "M_INOUTLINE",
        (
            "16_M_INOUTLINE_2025_Q1.xlsx",
            "17_M_INOUTLINE_2025_Q2.xlsx",
            "18_M_INOUTLINE_2025_Q3.xlsx",
            "19_M_INOUTLINE_2025_Q4.xlsx",
            "20_M_INOUTLINE_2026_Q1.xlsx",
            "21_M_INOUTLINE_2026_Q2.xlsx",
        ),
        ("M_INOUTLINE_ID", "M_INOUT_ID", "C_ORDERLINE_ID", "MOVEMENTQTY"),
        "Delivery-line chunks for movement dates in the 2025+2026 target period.",
    ),
    TableSpec("C_ALLOCATIONLINE", ("22_C_ALLOCATIONLINE_2025_2026.xlsx",), ("C_ALLOCATIONLINE_ID", "C_INVOICE_ID", "C_PAYMENT_ID", "AMOUNT"), "Allocation lines linked to selected vente invoices."),
    TableSpec("C_ALLOCATIONHDR", ("23_C_ALLOCATIONHDR_2025_2026.xlsx",), ("C_ALLOCATIONHDR_ID",), "Allocation headers linked through selected allocation lines."),
    TableSpec(
        "C_PAYMENT",
        ("24_C_PAYMENT_2025_2026.xlsx",),
        ("C_PAYMENT_ID", "DATETRX", "PAYAMT"),
        "Payments linked through C_ALLOCATIONLINE.C_PAYMENT_ID. Header repaired locally because the Toad export omitted headers.",
        fallback_header_csv="C_PAYMENT.csv",
    ),
    TableSpec("C_BPARTNER", ("25_C_BPARTNER_2025_2026.xlsx",), ("C_BPARTNER_ID", "VALUE", "NAME"), "Customers and partners referenced by the 2025+2026 vente process."),
    TableSpec("M_PRODUCT", ("26_M_PRODUCT_2025_2026.xlsx",), ("M_PRODUCT_ID", "VALUE", "NAME"), "Products referenced by the 2025+2026 order and invoice lines."),
    TableSpec("C_DOCTYPE", ("08_C_DOCTYPE.xlsx",), ("C_DOCTYPE_ID", "NAME"), "Document types referenced by vente orders and invoices. Reused tiny dimension export from the same folder."),
    TableSpec("C_TAX", ("09_C_TAX.xlsx",), ("C_TAX_ID", "NAME"), "Taxes referenced by vente order and invoice lines. Reused tiny dimension export from the same folder."),
    TableSpec("AD_ORG", ("29_AD_ORG_2025_2026.xlsx",), ("AD_ORG_ID", "VALUE", "NAME"), "Organizations referenced by the 2025+2026 vente process."),
    TableSpec("AD_USER", ("30_AD_USER_SALESREPS_2025_2026.xlsx",), ("AD_USER_ID", "NAME"), "Sales representatives used to resolve C_ORDER/C_INVOICE.SALESREP_ID."),
    TableSpec("M_PRODUCT_CATEGORY", ("31_M_PRODUCT_CATEGORY_2025_2026.xlsx",), ("M_PRODUCT_CATEGORY_ID", "NAME"), "Product category labels for referenced products."),
    TableSpec("M_PRODUCT_TYPE", ("32_M_PRODUCT_TYPE_2025_2026.xlsx",), ("M_PRODUCT_TYPE_ID", "NAME"), "Product type labels for referenced products."),
    TableSpec("M_PRODUCT_THEME", ("33_M_PRODUCT_THEME_2025_2026.xlsx",), ("M_PRODUCT_THEME_ID", "NAME"), "Product theme labels for referenced products."),
    TableSpec(
        "M_PRODUCT_COLLECTION",
        ("34_M_PRODUCT_COLLECTION_2025_2026.xlsx",),
        ("M_PRODUCT_COLLECTION_ID", "NAME"),
        "Product collection labels for referenced products. Header repaired locally because the Toad export omitted headers.",
        fallback_header_csv="M_PRODUCT_COLLECTION.csv",
    ),
    TableSpec("M_PRODUCT_PO", ("35_M_PRODUCT_PO_2025_2026.xlsx",), ("M_PRODUCT_ID", "C_BPARTNER_ID"), "Product-supplier relation for referenced products."),
    TableSpec("C_BPARTNER_VENDOR", ("36_C_BPARTNER_VENDOR.xlsx",), ("C_BPARTNER_ID", "NAME"), "Vendor business partners used for supplier labels."),
    TableSpec("M_WAREHOUSE", ("37_M_WAREHOUSE_2025_2026.xlsx",), ("M_WAREHOUSE_ID", "VALUE", "NAME"), "Warehouses referenced by selected deliveries."),
    TableSpec(
        "M_LOCATOR",
        ("38_M_LOCATOR_2025_2026.xlsx",),
        ("M_LOCATOR_ID", "M_WAREHOUSE_ID", "VALUE"),
        "Locators referenced by selected delivery lines. Header repaired locally because the Toad export omitted headers.",
        fallback_header_csv="M_LOCATOR.csv",
    ),
    TableSpec("RV_STORAGE", ("39_RV_STORAGE_CURRENT_FOR_SOLD_PRODUCTS.xlsx",), ("M_PRODUCT_ID", "QTYONHAND", "QTYAVAILABLE"), "Current stock rows for sold products."),
    TableSpec("C_BPARTNER_LOCATION", ("40_C_BPARTNER_LOCATION_2025_2026.xlsx",), ("C_BPARTNER_LOCATION_ID", "C_BPARTNER_ID", "C_LOCATION_ID"), "Customer/partner locations for selected partners."),
    TableSpec("C_LOCATION", ("41_C_LOCATION_2025_2026.xlsx",), ("C_LOCATION_ID",), "Physical location details for selected partner locations."),
    TableSpec("LPN_CUSTOMER_PORTFOLIO_2025_2026", ("42_CORRECTED_CUSTOMER_PORTFOLIO_2025_2026.xlsx",), ("C_BPARTNER_ID", "ORDERED_CA_TTC", "INVOICED_CA_TTC"), "Corrected customer portfolio with order and invoice CA aggregated separately."),
    TableSpec("C_BP_GROUP", ("27_C_BP_GROUP.xlsx",), ("C_BP_GROUP_ID", "NAME"), "Business partner group labels.", optional=True),
    TableSpec("C_PAYMENTTERM", ("30_C_PAYMENTTERM.xlsx",), ("C_PAYMENTTERM_ID", "NAME"), "Payment term labels.", optional=True),
    TableSpec("M_PRICELIST", ("33_M_PRICELIST.xlsx",), ("M_PRICELIST_ID", "NAME"), "Price list labels.", optional=True),
    TableSpec("C_REGION", ("40_C_REGION.xlsx",), ("C_REGION_ID", "NAME"), "Region labels for location analytics.", optional=True),
    TableSpec("C_CITY", ("41_C_CITY.xlsx",), ("C_CITY_ID", "NAME"), "City labels for location analytics.", optional=True),
    TableSpec("C_SALESREGION", ("32_C_SALESREGION.xlsx",), ("C_SALESREGION_ID", "NAME"), "Sales region labels.", optional=True),
    TableSpec("C_COMMISSION", ("34_C_COMMISSION.xlsx",), ("C_COMMISSION_ID", "NAME"), "Commission labels.", optional=True),
    TableSpec("PRODUCTS_WITHOUT_SUPPLIER", ("22_PRODUCTS_WITHOUT_SUPPLIER.xlsx",), ("M_PRODUCT_ID", "NAME"), "Diagnostic list of products without supplier relation.", optional=True),
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the 2025+2026 vente CSV import package from Toad Excel exports.")
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--keep-existing", action="store_true")
    args = parser.parse_args()

    source_dir = args.source_dir.resolve()
    output_dir = args.output_dir.resolve()
    _validate_paths(source_dir, output_dir)

    if output_dir.exists() and not args.keep_existing:
        shutil.rmtree(output_dir)
    csv_dir = output_dir / "csv"
    csv_dir.mkdir(parents=True, exist_ok=True)

    manifest_tables: list[dict[str, Any]] = []
    source_mappings: list[dict[str, Any]] = []
    skipped: list[dict[str, str]] = []

    for spec in TABLE_SPECS:
        missing_files = [file_name for file_name in spec.source_files if not (source_dir / file_name).exists()]
        if missing_files:
            if spec.optional:
                skipped.append({"table": spec.table_name, "reason": f"missing files: {', '.join(missing_files)}"})
                continue
            raise SystemExit(f"{spec.table_name}: missing required files: {', '.join(missing_files)}")

        csv_path = csv_dir / f"{spec.table_name}.csv"
        result = _write_table_csv(spec, source_dir, csv_path)
        manifest_tables.append(
            {
                "name": spec.table_name,
                "csv_file": f"csv/{spec.table_name}.csv",
                "row_count": result["row_count"],
                "exported_columns": result["headers"],
                "filter_used": spec.filter_used,
            }
        )
        source_mappings.append(
            {
                "table_name": spec.table_name,
                "csv_file": f"csv/{spec.table_name}.csv",
                "source_files": list(spec.source_files),
                "csv_data_rows": result["row_count"],
                "csv_columns": len(result["headers"]),
                "required_headers_checked": list(spec.required_headers),
                "used_fallback_header": result["used_fallback_header"],
                "notes": spec.notes,
            }
        )
        print(f"{spec.table_name:<34} {result['row_count']:>9,} rows")

    exported_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    manifest = {
        "snapshot_id": "lpn-vente-2025-2026-20260616-v1",
        "exported_at": exported_at,
        "exported_by": "Youssef Bahaddou",
        "source_system": "Compiere on Oracle 11g - LPN Mohammedia",
        "tables": manifest_tables,
        "notes": (
            "Clean 2025+2026 vente CSV staging package generated from Toad Excel exports in "
            "Youssef_Extractions/data/Exported_data_through_a_drive. Quarter chunks are combined. "
            "Quoted headers are normalized and headerless C_PAYMENT/M_LOCATOR exports are repaired from prior clean schema."
        ),
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    (output_dir / "source_mapping.json").write_text(
        json.dumps(
            {
                "generated_at": exported_at,
                "source_dir": str(source_dir),
                "output_dir": str(output_dir),
                "tables": source_mappings,
                "skipped": skipped,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    _write_report(output_dir, manifest_tables, source_mappings, skipped)
    print()
    print(f"Created 2025+2026 vente staging package: {output_dir}")
    print(f"Tables: {len(manifest_tables)}")
    print(f"Rows: {sum(table['row_count'] for table in manifest_tables):,}")


def _validate_paths(source_dir: Path, output_dir: Path) -> None:
    if not source_dir.is_dir():
        raise SystemExit(f"Source directory does not exist: {source_dir}")
    expected_parent = (REPO_ROOT / "Youssef_Extractions").resolve()
    try:
        output_dir.relative_to(expected_parent)
    except ValueError as exc:
        raise SystemExit(f"Refusing to write outside Youssef_Extractions: {output_dir}") from exc


def _write_table_csv(spec: TableSpec, source_dir: Path, csv_path: Path) -> dict[str, Any]:
    row_count = 0
    canonical_headers: list[str] | None = None
    used_fallback_header = False

    with csv_path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        for file_name in spec.source_files:
            result = _iter_workbook_rows(source_dir / file_name, spec)
            headers = result["headers"]
            used_fallback_header = used_fallback_header or result["used_fallback_header"]
            if canonical_headers is None:
                canonical_headers = headers
                _validate_headers(spec, canonical_headers, file_name)
                writer.writerow(canonical_headers)
            elif headers != canonical_headers:
                raise SystemExit(
                    f"{spec.table_name}: header mismatch in {file_name}; "
                    f"expected {canonical_headers[:8]}, got {headers[:8]}"
                )
            for row in result["rows"]:
                writer.writerow(row)
                row_count += 1

    if canonical_headers is None:
        raise SystemExit(f"{spec.table_name}: no headers produced")
    return {"headers": canonical_headers, "row_count": row_count, "used_fallback_header": used_fallback_header}


def _iter_workbook_rows(path: Path, spec: TableSpec) -> dict[str, Any]:
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    worksheet = workbook[workbook.sheetnames[0]]
    rows = worksheet.iter_rows(values_only=True)
    try:
        first_row = next(rows)
    except StopIteration as exc:
        raise SystemExit(f"Workbook is empty: {path}") from exc

    used_fallback_header = False
    if _looks_like_header(first_row):
        headers = _normalize_headers(first_row)
        data_rows = rows
    elif spec.fallback_header_csv:
        headers = _fallback_headers(spec.fallback_header_csv)
        data_rows = _prepend(first_row, rows)
        used_fallback_header = True
    else:
        raise SystemExit(f"{path.name} does not contain a recognizable header row")

    cleaned_rows = []
    for row in data_rows:
        values = list(row[: len(headers)])
        if _row_is_empty(values):
            continue
        values += [None] * (len(headers) - len(values))
        cleaned_rows.append([_clean_cell(value) for value in values])
    workbook.close()
    return {"headers": headers, "rows": cleaned_rows, "used_fallback_header": used_fallback_header}


def _prepend(first: tuple[Any, ...], rows: Any) -> Any:
    yield first
    yield from rows


def _looks_like_header(row: tuple[Any, ...]) -> bool:
    values = [value for value in row if value is not None and str(value).strip() != ""]
    if not values:
        return False
    text_values = [value for value in values if isinstance(value, str)]
    if len(text_values) / len(values) < 0.8:
        return False
    identifier_like = [
        value
        for value in text_values
        if re.fullmatch(r'"?[A-Za-z][A-Za-z0-9_#$]*"?', str(value).strip())
    ]
    return len(identifier_like) / max(len(text_values), 1) >= 0.7


def _normalize_headers(raw_headers: tuple[Any, ...]) -> list[str]:
    headers: list[str] = []
    seen: dict[str, int] = {}
    for index, value in enumerate(raw_headers, start=1):
        header = str(value).strip().strip('"').strip().upper() if value not in (None, "") else f"UNNAMED_{index}"
        header = " ".join(header.split())
        if header in seen:
            seen[header] += 1
            header = f"{header}_{seen[header]}"
        else:
            seen[header] = 1
        headers.append(header)
    while headers and headers[-1].startswith("UNNAMED_"):
        headers.pop()
    return headers


def _fallback_headers(csv_name: str) -> list[str]:
    path = next((directory / csv_name for directory in FALLBACK_HEADER_DIRS if (directory / csv_name).exists()), None)
    if path is None:
        searched = ", ".join(str(directory / csv_name) for directory in FALLBACK_HEADER_DIRS)
        raise SystemExit(f"Fallback header CSV not found. Searched: {searched}")
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return _normalize_headers(tuple(next(csv.reader(handle))))


def _validate_headers(spec: TableSpec, headers: list[str], file_name: str) -> None:
    missing = [header for header in spec.required_headers if header not in headers]
    if missing:
        raise SystemExit(
            f"{spec.table_name}: {file_name} missing required headers: {', '.join(missing)}"
        )


def _row_is_empty(values: list[Any]) -> bool:
    return all(value is None or str(value).strip() == "" for value in values)


def _clean_cell(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, datetime):
        return value.isoformat(sep=" ")
    if isinstance(value, date) and not isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, time):
        return value.isoformat()
    if isinstance(value, bool):
        return "Y" if value else "N"
    text = str(value).strip()
    if text.lower() in {"nan", "nat", "none"}:
        return ""
    text = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    return " ".join(text.split()) if "\t" in text else text


def _write_report(
    output_dir: Path,
    manifest_tables: list[dict[str, Any]],
    source_mappings: list[dict[str, Any]],
    skipped: list[dict[str, str]],
) -> None:
    lines = [
        "# Vente 2025+2026 Import Report",
        "",
        "Generated by `scripts/build-vente-2025-2026-import.py`.",
        "",
        "## Tables",
        "",
        "| Table | Rows | Columns | Source files | Notes |",
        "|---|---:|---:|---|---|",
    ]
    mappings = {mapping["table_name"]: mapping for mapping in source_mappings}
    for table in manifest_tables:
        mapping = mappings[table["name"]]
        sources = ", ".join(f"`{source}`" for source in mapping["source_files"])
        lines.append(
            f"| `{table['name']}` | {table['row_count']} | {len(table['exported_columns'])} | "
            f"{sources} | {mapping['notes']} |"
        )
    if skipped:
        lines.extend(["", "## Skipped Optional Tables", ""])
        for item in skipped:
            lines.append(f"- `{item['table']}`: {item['reason']}")
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Quarter chunks are combined into canonical transaction CSVs.",
            "- Toad quoted headers are normalized by stripping double quotes.",
            "- Headerless `C_PAYMENT` and `M_LOCATOR` exports are repaired from the previous clean import schema.",
            "- This staging package is raw data and remains under `Youssef_Extractions`, which is ignored by git.",
        ]
    )
    (output_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

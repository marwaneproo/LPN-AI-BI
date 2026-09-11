from __future__ import annotations

import csv
import json
import re
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import openpyxl


REPO_ROOT = Path(__file__).resolve().parents[1]
EXTRACTIONS_ROOT = REPO_ROOT / "Youssef_Extractions"
OUTPUT_DIR = REPO_ROOT / "docs" / "warehouse"
REPORT_MD = OUTPUT_DIR / "VENTE_DWH_SOURCE_AUDIT.md"
REPORT_JSON = OUTPUT_DIR / "vente_dwh_source_audit.json"

SCAN_DIRS = (
    EXTRACTIONS_ROOT / "1st_Extraction",
    EXTRACTIONS_ROOT / "2nd_Extraction",
    EXTRACTIONS_ROOT / "3rd_Extraction",
    EXTRACTIONS_ROOT / "vente_clean_import",
    EXTRACTIONS_ROOT / "vente_bi_enrichment_import",
)

CANONICAL_IMPORT_DIR = EXTRACTIONS_ROOT / "vente_clean_import"
ENRICHMENT_IMPORT_DIR = EXTRACTIONS_ROOT / "vente_bi_enrichment_import"


@dataclass
class SheetAudit:
    sheet_name: str
    rows: int
    columns: int
    headers: list[str]
    inferred_table: str | None
    sample: dict[str, Any]


@dataclass
class FileAudit:
    path: str
    folder: str
    file_name: str
    extension: str
    size_bytes: int
    role: str
    canonical_table: str | None
    sheets: list[SheetAudit]
    csv_rows: int | None
    csv_columns: int | None
    csv_headers: list[str]
    notes: list[str]


TABLE_HINTS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("C_ORDERLINE", ("C_ORDERLINE_ID", "C_ORDER_ID", "QTYORDERED")),
    ("C_ORDER", ("C_ORDER_ID", "DATEORDERED", "GRANDTOTAL")),
    ("C_INVOICELINE", ("C_INVOICELINE_ID", "C_INVOICE_ID", "LINENETAMT")),
    ("C_INVOICE", ("C_INVOICE_ID", "DATEINVOICED", "GRANDTOTAL")),
    ("C_BPARTNER", ("C_BPARTNER_ID", "NAME", "ISCUSTOMER")),
    ("M_PRODUCT_PO", ("M_PRODUCT_ID", "C_BPARTNER_ID", "ISCURRENTVENDOR")),
    ("M_PRODUCT_CATEGORY", ("M_PRODUCT_CATEGORY_ID", "NAME")),
    ("M_PRODUCT", ("M_PRODUCT_ID", "M_PRODUCT_CATEGORY_ID", "ISSOLD")),
    ("C_DOCTYPE", ("C_DOCTYPE_ID", "DOCBASETYPE", "NAME")),
    ("C_TAX", ("C_TAX_ID", "RATE", "NAME")),
    ("AD_ORG", ("AD_ORG_ID", "ISSUMMARY", "VALUE")),
    ("AD_USER", ("AD_USER_ID", "NAME", "EMAIL")),
    ("C_ALLOCATIONLINE", ("C_ALLOCATIONLINE_ID", "C_ALLOCATIONHDR_ID", "C_INVOICE_ID")),
    ("C_ALLOCATIONHDR", ("C_ALLOCATIONHDR_ID", "DOCUMENTNO", "DATETRX")),
    ("C_PAYMENT", ("C_PAYMENT_ID", "DATETRX", "PAYAMT")),
    ("M_INOUTLINE", ("M_INOUTLINE_ID", "M_INOUT_ID", "MOVEMENTQTY")),
    ("M_INOUT", ("M_INOUT_ID", "MOVEMENTDATE", "C_ORDER_ID")),
    ("M_WAREHOUSE", ("M_WAREHOUSE_ID", "VALUE", "NAME")),
    ("M_LOCATOR", ("M_LOCATOR_ID", "M_WAREHOUSE_ID", "VALUE")),
    ("RV_STORAGE", ("QTYONHAND", "QTYRESERVED", "QTYAVAILABLE")),
    ("C_BP_GROUP", ("C_BP_GROUP_ID", "NAME")),
    ("M_PRODUCT_TYPE", ("M_PRODUCT_TYPE_ID", "NAME")),
    ("M_PRODUCT_THEME", ("M_PRODUCT_THEME_ID", "NAME")),
    ("M_PRODUCT_COLLECTION", ("M_PRODUCT_COLLECTION_ID", "NAME")),
)


def normalize_header(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip().upper()


def clean_cell(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat()
    return value


def infer_table(headers: list[str], file_name: str) -> str | None:
    normalized = {normalize_header(h) for h in headers if h}
    upper_name = file_name.upper()

    for name, _required in TABLE_HINTS:
        if name == "C_BP_GROUP" and name in upper_name and "C_BP_GROUP_ID" not in normalized:
            continue
        if name in upper_name:
            return name

    for name, required in TABLE_HINTS:
        if all(col in normalized for col in required):
            return name

    if "PRODUCTS_WITHOUT_SUPPLIER" in upper_name:
        return "PRODUCTS_WITHOUT_SUPPLIER"
    if "STORAGE_COLUMNS" in upper_name:
        return "STORAGE_COLUMN_DIAGNOSTIC"
    return None


def load_manifest_tables(manifest_path: Path) -> tuple[dict[str, dict[str, Any]], dict[str, set[str]]]:
    if not manifest_path.exists():
        return {}, defaultdict(set)
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    tables: dict[str, dict[str, Any]] = {}
    source_files: dict[str, set[str]] = defaultdict(set)
    for table in data.get("tables", []):
        name = table.get("name")
        if not name:
            continue
        tables[name] = table
        for key in ("source_file", "source_files"):
            value = table.get(key)
            if isinstance(value, str):
                source_files[value.lower()].add(name)
            elif isinstance(value, list):
                for item in value:
                    source_files[str(item).lower()].add(name)
    return tables, source_files


def load_source_mapping(source_mapping_path: Path) -> tuple[dict[str, dict[str, Any]], dict[str, set[str]]]:
    if not source_mapping_path.exists():
        return {}, defaultdict(set)
    data = json.loads(source_mapping_path.read_text(encoding="utf-8"))
    tables: dict[str, dict[str, Any]] = {}
    source_files: dict[str, set[str]] = defaultdict(set)
    for table in data.get("tables", []):
        name = table.get("table_name") or table.get("name")
        source_file = table.get("source_file")
        if not name:
            continue
        tables[name] = table
        if source_file:
            source_files[str(source_file).lower()].add(name)
    return tables, source_files


def read_csv_audit(path: Path) -> tuple[int, int, list[str], dict[str, Any]]:
    rows = 0
    headers: list[str] = []
    sample: dict[str, Any] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle)
        try:
            headers = next(reader)
        except StopIteration:
            return 0, 0, [], {}
        for row in reader:
            rows += 1
            if not sample:
                sample = {headers[i]: row[i] if i < len(row) else "" for i in range(min(len(headers), len(row)))}
    return rows, len(headers), headers, sample


def read_xlsx_audit(path: Path) -> list[SheetAudit]:
    audits: list[SheetAudit] = []
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        for sheet in workbook.worksheets:
            max_row = sheet.max_row or 0
            max_column = sheet.max_column or 0
            headers = [normalize_header(sheet.cell(1, col).value) for col in range(1, max_column + 1)]
            headers = [h for h in headers if h]
            sample: dict[str, Any] = {}
            if max_row >= 2 and headers:
                values = [clean_cell(sheet.cell(2, col).value) for col in range(1, min(max_column, len(headers)) + 1)]
                sample = {headers[i]: values[i] if i < len(values) else None for i in range(min(len(headers), len(values)))}
            rows = max(0, max_row - 1) if headers else max_row
            audits.append(
                SheetAudit(
                    sheet_name=sheet.title,
                    rows=rows,
                    columns=len(headers) if headers else max_column,
                    headers=headers,
                    inferred_table=infer_table(headers, path.name),
                    sample=sample,
                )
            )
    finally:
        workbook.close()
    return audits


def role_for_file(
    path: Path,
    clean_source_files: dict[str, set[str]],
    enrichment_source_files: dict[str, set[str]],
) -> tuple[str, str | None, list[str]]:
    name_l = path.name.lower()
    notes: list[str] = []

    if path.suffix.lower() == ".csv" and "vente_clean_import" in [part.lower() for part in path.parts]:
        return "clean_import_csv", path.stem.upper(), ["CSV canonical staging output for PostgreSQL import."]
    if path.suffix.lower() == ".csv" and "vente_bi_enrichment_import" in [part.lower() for part in path.parts]:
        return "bi_enrichment_csv", path.stem.upper(), ["CSV enrichment staging output for PostgreSQL import."]
    if path.name.lower() in {"manifest.json", "source_mapping.json", "readme.md"}:
        return "manifest_or_documentation", None, ["Package metadata/documentation."]
    if path.suffix.lower() not in {".xlsx", ".xlsm"}:
        return "non_tabular_or_ignored", None, ["Not an Excel/CSV data table."]
    if name_l in clean_source_files:
        tables = ", ".join(sorted(clean_source_files[name_l]))
        return "canonical_raw_source", tables, [f"Used by vente_clean_import for: {tables}."]
    if name_l in enrichment_source_files:
        tables = ", ".join(sorted(enrichment_source_files[name_l]))
        return "bi_enrichment_raw_source", tables, [f"Used by vente_bi_enrichment_import for: {tables}."]
    if re.match(r"book\d+\.xlsx$", name_l):
        return "raw_candidate_or_diagnostic", None, ["Workbook name is generic; use manifests to decide whether it is canonical."]
    if "columns" in name_l or "without_supplier" in name_l:
        return "diagnostic_or_quality_file", None, ["Diagnostic/quality-control export, not a main DW fact/dimension."]
    if re.search(r"\(\d+\)\.xlsx$", name_l):
        return "duplicate_candidate", None, ["Filename indicates a repeated export copy."]
    if name_l.startswith("extract_"):
        return "legacy_first_extraction", None, ["Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages."]
    return "raw_candidate", None, []


def collect_files() -> list[Path]:
    files: list[Path] = []
    for scan_dir in SCAN_DIRS:
        if not scan_dir.exists():
            continue
        files.extend(path for path in scan_dir.rglob("*") if path.is_file())
    return sorted(files, key=lambda p: str(p).lower())


def audit_files() -> tuple[dict[str, Any], list[FileAudit]]:
    clean_tables, clean_sources = load_manifest_tables(CANONICAL_IMPORT_DIR / "manifest.json")
    clean_mapping_tables, clean_mapping_sources = load_source_mapping(CANONICAL_IMPORT_DIR / "source_mapping.json")
    clean_tables = {**clean_tables, **clean_mapping_tables}
    for source_file, table_names in clean_mapping_sources.items():
        clean_sources[source_file].update(table_names)
    enrichment_tables, enrichment_sources = load_manifest_tables(ENRICHMENT_IMPORT_DIR / "manifest.json")

    audits: list[FileAudit] = []
    for path in collect_files():
        role, canonical_table, notes = role_for_file(path, clean_sources, enrichment_sources)
        extension = path.suffix.lower()
        sheets: list[SheetAudit] = []
        csv_rows: int | None = None
        csv_columns: int | None = None
        csv_headers: list[str] = []

        try:
            if extension in {".xlsx", ".xlsm"}:
                sheets = read_xlsx_audit(path)
                inferred_tables = sorted({s.inferred_table for s in sheets if s.inferred_table})
                if not canonical_table and inferred_tables:
                    canonical_table = ", ".join(inferred_tables)
            elif extension == ".csv":
                csv_rows, csv_columns, csv_headers, sample = read_csv_audit(path)
                inferred = infer_table(csv_headers, path.name)
                if not canonical_table:
                    canonical_table = inferred
                if sample:
                    sheets = [
                        SheetAudit(
                            sheet_name="CSV",
                            rows=csv_rows,
                            columns=csv_columns,
                            headers=csv_headers,
                            inferred_table=inferred,
                            sample=sample,
                        )
                    ]
        except Exception as exc:  # Keep audit resilient across odd Excel files.
            notes.append(f"Could not inspect content: {exc}")

        audits.append(
            FileAudit(
                path=str(path),
                folder=str(path.parent),
                file_name=path.name,
                extension=extension,
                size_bytes=path.stat().st_size,
                role=role,
                canonical_table=canonical_table,
                sheets=sheets,
                csv_rows=csv_rows,
                csv_columns=csv_columns,
                csv_headers=csv_headers,
                notes=notes,
            )
        )

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "scan_dirs": [str(p) for p in SCAN_DIRS],
        "clean_manifest_tables": {
            name: {
                "row_count": table.get("row_count") or table.get("csv_data_rows"),
                "columns": len(table.get("exported_columns", table.get("columns", []))) or table.get("csv_columns"),
                "source_file": table.get("source_file") or table.get("source_files"),
            }
            for name, table in clean_tables.items()
        },
        "enrichment_manifest_tables": {
            name: {
                "row_count": table.get("row_count"),
                "columns": len(table.get("columns", [])),
                "source_file": table.get("source_file") or table.get("source_files"),
            }
            for name, table in enrichment_tables.items()
        },
    }
    return summary, audits


def build_report(summary: dict[str, Any], audits: list[FileAudit]) -> str:
    by_role: dict[str, int] = defaultdict(int)
    by_table: dict[str, list[FileAudit]] = defaultdict(list)
    for audit in audits:
        by_role[audit.role] += 1
        if audit.canonical_table:
            for table in [part.strip() for part in audit.canonical_table.split(",")]:
                by_table[table].append(audit)

    lines: list[str] = []
    lines.append("# Vente Data Warehouse Source Audit")
    lines.append("")
    lines.append(f"Generated at: `{summary['generated_at']}`")
    lines.append("")
    lines.append("## Package Coverage")
    lines.append("")
    lines.append("### Canonical vente import")
    lines.append("")
    lines.append("| Table | Rows | Columns | Source |")
    lines.append("|---|---:|---:|---|")
    for table, meta in sorted(summary["clean_manifest_tables"].items()):
        lines.append(f"| `{table}` | {meta['row_count']} | {meta['columns']} | `{meta['source_file']}` |")
    lines.append("")
    lines.append("### BI enrichment import")
    lines.append("")
    lines.append("| Table | Rows | Columns | Source |")
    lines.append("|---|---:|---:|---|")
    for table, meta in sorted(summary["enrichment_manifest_tables"].items()):
        lines.append(f"| `{table}` | {meta['row_count']} | {meta['columns']} | `{meta['source_file']}` |")
    lines.append("")

    lines.append("## File Classification Summary")
    lines.append("")
    lines.append("| Role | File count |")
    lines.append("|---|---:|")
    for role, count in sorted(by_role.items()):
        lines.append(f"| `{role}` | {count} |")
    lines.append("")

    lines.append("## File-By-File Inventory")
    lines.append("")
    lines.append("| File | Role | Inferred table | Sheets / rows | Notes |")
    lines.append("|---|---|---|---:|---|")
    for audit in audits:
        if audit.extension not in {".xlsx", ".xlsm", ".csv", ".json", ".md"}:
            continue
        sheet_bits = []
        for sheet in audit.sheets:
            sheet_bits.append(f"{sheet.sheet_name}: {sheet.rows}x{sheet.columns}")
        if audit.csv_rows is not None and not sheet_bits:
            sheet_bits.append(f"CSV: {audit.csv_rows}x{audit.csv_columns}")
        notes = " ".join(audit.notes).replace("|", "/")
        rel = Path(audit.path).relative_to(REPO_ROOT)
        lines.append(
            f"| `{rel}` | `{audit.role}` | `{audit.canonical_table or ''}` | "
            f"{'; '.join(sheet_bits) or ''} | {notes} |"
        )
    lines.append("")

    lines.append("## DW Readiness Assessment")
    lines.append("")
    lines.append("### Strong coverage already available")
    lines.append("")
    lines.append("- Order headers and lines: `C_ORDER`, `C_ORDERLINE`.")
    lines.append("- Invoice headers and lines: `C_INVOICE`, `C_INVOICELINE`.")
    lines.append("- Customer and vendor references: `C_BPARTNER`, `C_BPARTNER_VENDOR`.")
    lines.append("- Commercial names: `AD_USER`, joinable through `SALESREP_ID`.")
    lines.append("- Product/category/type/theme/collection and supplier relation: `M_PRODUCT`, `M_PRODUCT_CATEGORY`, `M_PRODUCT_PO`, enrichment tables.")
    lines.append("- Delivery flow: `M_INOUT`, `M_INOUTLINE`, plus warehouse/locator references.")
    lines.append("- Payment allocation flow: `C_ALLOCATIONLINE`, `C_ALLOCATIONHDR`, `C_PAYMENT`.")
    lines.append("- Stock availability snapshot: `RV_STORAGE`.")
    lines.append("- Document/tax/org references: `C_DOCTYPE`, `C_TAX`, `AD_ORG`.")
    lines.append("")
    lines.append("### Gaps before a full professional sales DW")
    lines.append("")
    lines.append("- Real customer group dimension still needs a verified `C_BP_GROUP`; current `23_C_BP_GROUP.xlsx` must be checked because it may not be the expected table.")
    lines.append("- Contracts, price agreements, commercial targets, commissions, and sales objectives are not covered by current extracts.")
    lines.append("- Geographic/customer address analysis needs `C_BPARTNER_LOCATION` and `C_LOCATION`.")
    lines.append("- Payment-term and due-date aging analysis needs `C_PAYMENTTERM` and invoice due-date fields if not already complete.")
    lines.append("- Commercial hierarchy/teams/roles need user-role or sales-region exports if the business wants team-level performance.")
    lines.append("- Full margin analysis needs purchase/cost source tables, not only supplier relation.")
    lines.append("")
    lines.append("## Recommended Next Exports")
    lines.append("")
    lines.append("1. `27_C_BP_GROUP.xlsx` - actual customer/vendor group reference.")
    lines.append("2. `28_C_BPARTNER_LOCATION.xlsx` - partner addresses and location links.")
    lines.append("3. `29_C_LOCATION.xlsx` - city/region/country fields for geographic sales.")
    lines.append("4. `30_C_PAYMENTTERM.xlsx` - payment-term labels and due policy.")
    lines.append("5. Discovery query exports for contract/objective/commission tables before requesting large data extracts.")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    summary, audits = audit_files()
    payload = {
        "summary": summary,
        "files": [
            {
                **{k: v for k, v in asdict(audit).items() if k != "sheets"},
                "sheets": [asdict(sheet) for sheet in audit.sheets],
            }
            for audit in audits
        ],
    }
    REPORT_JSON.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    REPORT_MD.write_text(build_report(summary, audits), encoding="utf-8")
    print(f"Wrote {REPORT_MD}")
    print(f"Wrote {REPORT_JSON}")
    print(f"Audited {len(audits)} files")


if __name__ == "__main__":
    main()

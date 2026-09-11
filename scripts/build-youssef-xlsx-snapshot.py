from __future__ import annotations

import csv
import json
import re
import shutil
import zipfile
from collections.abc import Iterable
from datetime import datetime, timedelta
from pathlib import Path
from xml.etree import ElementTree as ET


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "Youssef_Extractions"
OUTPUT_ROOT = REPO_ROOT / "local-snapshots" / "youssef-xlsx-20260424"
SNAPSHOT_ID = "lpn-youssef-xlsx-20260424-v1"
EXPORTED_AT = "2026-04-24T15:00:04Z"

NS = {
    "a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
RELNS = {"rel": "http://schemas.openxmlformats.org/package/2006/relationships"}

RAW_EXPORT_FILES = [
    "Extract_01.xlsx",
    "Extract_02.xlsx",
    "Extract_03.xlsx",
    "Extract_04.xlsx",
    "Extract_05.xlsx",
    "Extract_06.xlsx",
    "Extract_08.xlsx",
    "Extract_09.xlsx",
    "Extract_10.xlsx",
    "Extract_11.xlsx",
    "Extract_12.xlsx",
    "Extract_13.xlsx",
    "Extract_15.xlsx",
    "Extract_16.xlsx",
    "Extract_18.xlsx",
    "Extract_19.xlsx",
    "Extract_20.xlsx",
    "Extract_21.xlsx",
    "Extract_22.xlsx",
    "Extract_23.xlsx",
    "Extract_24.xlsx",
    "Extract_25.xlsx",
    "Extract_26.xlsx",
]

TABLE_SOURCES = {
    "C_ORDER": {
        "file": "Extract_20.xlsx",
        "columns": [
            "C_ORDER_ID",
            "DOCUMENTNO",
            "DOCSTATUS",
            "DOCACTION",
            "C_BPARTNER_ID",
            "DATEORDERED",
            "GRANDTOTAL",
            "TOTALLINES",
            "ISSOTRX",
            "SALESREP_ID",
            "C_DOCTYPETARGET_ID",
        ],
    },
    "C_ORDERLINE": {
        "file": "Extract_21.xlsx",
        "columns": [
            "C_ORDERLINE_ID",
            "C_ORDER_ID",
            "LINE",
            "M_PRODUCT_ID",
            "QTYORDERED",
            "QTYDELIVERED",
            "QTYINVOICED",
            "PRICEACTUAL",
            "PRICELIST",
            "PRICELIMIT",
            "LINENETAMT",
            "C_TAX_ID",
        ],
    },
    "C_INVOICE": {
        "file": "Extract_22.xlsx",
        "columns": [
            "C_INVOICE_ID",
            "DOCUMENTNO",
            "DOCSTATUS",
            "ISPAID",
            "C_BPARTNER_ID",
            "DATEINVOICED",
            "GRANDTOTAL",
            "TOTALLINES",
        ],
    },
    "C_DOCTYPE": {
        "file": "Extract_19.xlsx",
        "columns": [
            "C_DOCTYPE_ID",
            "DOCBASETYPE",
            "NAME",
            "PRINTNAME",
            "DESCRIPTION",
        ],
    },
    "LPN_ORDER_FLOW": {
        "file": "Extract_23.xlsx",
        "columns": [
            "C_ORDER_ID",
            "ORDER_NO",
            "DATEORDERED",
            "ORDER_STATUS",
            "C_ORDERLINE_ID",
            "ORDER_LINE_NO",
            "M_PRODUCT_ID",
            "QTYORDERED",
            "QTYDELIVERED",
            "QTYINVOICED",
            "M_INOUT_ID",
            "SHIPMENT_NO",
            "MOVEMENTDATE",
            "SHIPMENT_STATUS",
            "C_INVOICE_ID",
            "INVOICE_NO",
            "DATEINVOICED",
            "INVOICE_STATUS",
            "ISPAID",
            "C_INVOICELINE_ID",
            "LINENETAMT",
        ],
    },
    "LPN_INVOICE_PAYMENT": {
        "file": "Extract_24.xlsx",
        "columns": [
            "C_INVOICE_ID",
            "INVOICE_NO",
            "DATEINVOICED",
            "DOCSTATUS",
            "ISPAID",
            "C_ALLOCATIONLINE_ID",
            "C_ALLOCATIONHDR_ID",
            "ALLOCATION_DATE",
            "C_PAYMENT_ID",
            "PAYMENT_NO",
            "PAYMENT_DATE",
            "PAYAMT",
            "PAYMENT_STATUS",
        ],
    },
}

DATE_COLUMNS = {
    "DATEORDERED",
    "DATEINVOICED",
    "MOVEMENTDATE",
    "ALLOCATION_DATE",
    "PAYMENT_DATE",
}

INTEGER_COLUMNS = {
    "C_ORDER_ID",
    "C_ORDERLINE_ID",
    "C_INVOICE_ID",
    "C_BPARTNER_ID",
    "M_PRODUCT_ID",
    "C_DOCTYPE_ID",
    "SALESREP_ID",
    "C_DOCTYPETARGET_ID",
    "C_TAX_ID",
    "M_INOUT_ID",
    "C_INVOICELINE_ID",
    "C_ALLOCATIONLINE_ID",
    "C_ALLOCATIONHDR_ID",
    "C_PAYMENT_ID",
}

NUMERIC_COLUMNS = {
    "GRANDTOTAL",
    "TOTALLINES",
    "QTYORDERED",
    "QTYDELIVERED",
    "QTYINVOICED",
    "PRICEACTUAL",
    "PRICELIST",
    "PRICELIMIT",
    "LINENETAMT",
    "PAYAMT",
    "LINE",
    "ORDER_LINE_NO",
}


def main() -> None:
    _ensure_sources_exist()
    if OUTPUT_ROOT.exists():
        shutil.rmtree(OUTPUT_ROOT)

    raw_dir = OUTPUT_ROOT / "raw"
    csv_dir = OUTPUT_ROOT / "bundle" / "csv"
    raw_dir.mkdir(parents=True, exist_ok=True)
    csv_dir.mkdir(parents=True, exist_ok=True)

    for filename in RAW_EXPORT_FILES:
        shutil.copy2(SOURCE_DIR / filename, raw_dir / filename)

    loaded_tables: dict[str, list[dict[str, str]]] = {}
    for table_name, source in TABLE_SOURCES.items():
        rows = _read_xlsx_rows(SOURCE_DIR / source["file"])
        loaded_tables[table_name] = _project_rows(rows, source["columns"])

    loaded_tables["C_BPARTNER"] = _derive_bpartners(
        loaded_tables["C_ORDER"],
        loaded_tables["C_INVOICE"],
    )
    loaded_tables["M_PRODUCT"] = _derive_products(
        loaded_tables["C_ORDERLINE"],
        loaded_tables["LPN_ORDER_FLOW"],
    )

    ordered_tables = [
        "C_BPARTNER",
        "M_PRODUCT",
        "C_DOCTYPE",
        "C_ORDER",
        "C_ORDERLINE",
        "C_INVOICE",
        "LPN_ORDER_FLOW",
        "LPN_INVOICE_PAYMENT",
    ]

    manifest_tables = []
    for table_name in ordered_tables:
        rows = loaded_tables[table_name]
        csv_file = f"csv/{table_name}.csv"
        _write_csv(csv_dir / f"{table_name}.csv", rows)
        manifest_tables.append(
            {
                "name": table_name,
                "csv_file": csv_file,
                "row_count": len(rows),
                "exported_columns": list(rows[0]) if rows else [],
                "filter_used": "Derived from Youssef_Extractions Excel exports dated 2026-04-24",
            }
        )

    bundle_dir = OUTPUT_ROOT / "bundle"
    (bundle_dir / "schema.sql").write_text(_schema_sql(), encoding="utf-8")
    (bundle_dir / "manifest.json").write_text(
        json.dumps(
            {
                "snapshot_id": SNAPSHOT_ID,
                "exported_at": EXPORTED_AT,
                "exported_by": "Youssef Bahaddou",
                "source_system": "LPN Compiere Excel exports prepared for local PostgreSQL testing",
                "tables": manifest_tables,
                "notes": (
                    "Temporary PFE snapshot generated from Excel exports. "
                    "C_BPARTNER and M_PRODUCT are lightweight derived dimensions because "
                    "full master-data extracts are not present yet."
                ),
            },
            ensure_ascii=True,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    zip_path = OUTPUT_ROOT / f"{SNAPSHOT_ID}.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(bundle_dir.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(bundle_dir))

    print(f"Snapshot folder: {OUTPUT_ROOT}")
    print(f"Bundle zip: {zip_path}")
    print("Tables:")
    for table in manifest_tables:
        print(f"- {table['name']}: {table['row_count']} rows")


def _ensure_sources_exist() -> None:
    missing = [filename for filename in RAW_EXPORT_FILES if not (SOURCE_DIR / filename).exists()]
    if missing:
        raise FileNotFoundError(f"Missing Excel exports: {', '.join(missing)}")


def _read_xlsx_rows(path: Path) -> list[dict[str, str]]:
    rows = _xlsx_values(path)
    if not rows:
        return []
    headers = [_normalize_header(value) for value in rows[0]]
    records: list[dict[str, str]] = []
    for raw_row in rows[1:]:
        record = {}
        for index, header in enumerate(headers):
            if not header:
                continue
            value = raw_row[index] if index < len(raw_row) else ""
            record[header] = value
        if any(value != "" for value in record.values()):
            records.append(record)
    return records


def _xlsx_values(path: Path) -> list[list[str]]:
    with zipfile.ZipFile(path) as workbook:
        shared_strings = _shared_strings(workbook)
        sheet_path = _first_sheet_path(workbook)
        rows: list[list[str]] = []
        with workbook.open(sheet_path) as sheet_xml:
            for _, element in ET.iterparse(sheet_xml, events=("end",)):
                if _local_name(element.tag) != "row":
                    continue
                values: list[str] = []
                for cell in element.findall("a:c", NS):
                    index = _column_index(cell.attrib.get("r", "A"))
                    while len(values) < index - 1:
                        values.append("")
                    values.append(_cell_text(cell, shared_strings).strip())
                while values and values[-1] == "":
                    values.pop()
                rows.append(values)
                element.clear()
        return rows


def _shared_strings(workbook: zipfile.ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in workbook.namelist():
        return []
    root = ET.fromstring(workbook.read("xl/sharedStrings.xml"))
    strings = []
    for item in root.findall("a:si", NS):
        strings.append("".join(text.text or "" for text in item.findall(".//a:t", NS)))
    return strings


def _first_sheet_path(workbook: zipfile.ZipFile) -> str:
    workbook_xml = ET.fromstring(workbook.read("xl/workbook.xml"))
    rels_xml = ET.fromstring(workbook.read("xl/_rels/workbook.xml.rels"))
    rels = {
        rel.attrib["Id"]: rel.attrib["Target"]
        for rel in rels_xml.findall("rel:Relationship", RELNS)
    }
    first_sheet = workbook_xml.find(".//a:sheet", NS)
    if first_sheet is None:
        raise ValueError("Workbook has no sheets")
    rel_id = first_sheet.attrib[
        "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
    ]
    target = rels[rel_id]
    return target if target.startswith("xl/") else f"xl/{target}"


def _cell_text(cell: ET.Element, shared_strings: list[str]) -> str:
    cell_type = cell.attrib.get("t")
    if cell_type == "s":
        value = cell.find("a:v", NS)
        if value is None or value.text is None:
            return ""
        return shared_strings[int(value.text)]
    if cell_type == "inlineStr":
        return "".join(text.text or "" for text in cell.findall(".//a:t", NS))
    value = cell.find("a:v", NS)
    return value.text if value is not None and value.text is not None else ""


def _column_index(cell_ref: str) -> int:
    index = 0
    for char in re.sub(r"[^A-Za-z]", "", cell_ref).upper():
        index = index * 26 + ord(char) - 64
    return index


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _normalize_header(value: str) -> str:
    return value.strip().upper()


def _project_rows(rows: list[dict[str, str]], columns: list[str]) -> list[dict[str, str]]:
    projected = []
    seen_primary_values: set[str] = set()
    primary_key = columns[0]

    for row in rows:
        record = {column: _normalize_value(column, row.get(column, "")) for column in columns}
        primary_value = record.get(primary_key, "")
        if not primary_value or primary_value in seen_primary_values:
            continue
        seen_primary_values.add(primary_value)
        projected.append(record)
    return projected


def _normalize_value(column: str, value: str) -> str:
    value = value.strip()
    if value == "":
        return ""
    if column in DATE_COLUMNS:
        return _normalize_date(value)
    if column in INTEGER_COLUMNS:
        return _normalize_integer(value)
    if column in NUMERIC_COLUMNS:
        return _normalize_numeric(value)
    return value


def _normalize_date(value: str) -> str:
    if re.fullmatch(r"\d+(\.\d+)?", value):
        serial = float(value)
        if 20_000 <= serial <= 60_000:
            return (datetime(1899, 12, 30) + timedelta(days=serial)).date().isoformat()
    return value


def _normalize_integer(value: str) -> str:
    try:
        return str(int(float(value)))
    except ValueError:
        return value


def _normalize_numeric(value: str) -> str:
    try:
        normalized = f"{float(value):.10f}".rstrip("0").rstrip(".")
        return normalized if normalized else "0"
    except ValueError:
        return value


def _derive_bpartners(
    orders: Iterable[dict[str, str]],
    invoices: Iterable[dict[str, str]],
) -> list[dict[str, str]]:
    ids = {
        row["C_BPARTNER_ID"]
        for row in [*orders, *invoices]
        if row.get("C_BPARTNER_ID")
    }
    return [
        {
            "C_BPARTNER_ID": partner_id,
            "VALUE": f"BP-{partner_id}",
            "NAME": f"Business Partner {partner_id}",
        }
        for partner_id in sorted(ids, key=lambda item: int(item))
    ]


def _derive_products(
    order_lines: Iterable[dict[str, str]],
    order_flow: Iterable[dict[str, str]],
) -> list[dict[str, str]]:
    ids = {
        row["M_PRODUCT_ID"]
        for row in [*order_lines, *order_flow]
        if row.get("M_PRODUCT_ID")
    }
    return [
        {
            "M_PRODUCT_ID": product_id,
            "VALUE": f"PRD-{product_id}",
            "NAME": f"Product {product_id}",
            "ISACTIVE": "Y",
        }
        for product_id in sorted(ids, key=lambda item: int(item))
    ]


def _write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    if not rows:
        raise ValueError(f"Cannot write empty CSV: {path}")
    with path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def _schema_sql() -> str:
    return """CREATE TABLE business.c_bpartner (
    c_bpartner_id bigint PRIMARY KEY,
    value text NOT NULL,
    name text NOT NULL
);

CREATE TABLE business.m_product (
    m_product_id bigint PRIMARY KEY,
    value text NOT NULL,
    name text NOT NULL,
    isactive char(1)
);

CREATE TABLE business.c_doctype (
    c_doctype_id bigint PRIMARY KEY,
    docbasetype text,
    name text,
    printname text,
    description text
);

CREATE TABLE business.c_order (
    c_order_id bigint PRIMARY KEY,
    documentno text NOT NULL,
    docstatus char(2),
    docaction char(2),
    c_bpartner_id bigint REFERENCES business.c_bpartner(c_bpartner_id),
    dateordered timestamp,
    grandtotal numeric(18, 2),
    totallines numeric(18, 2),
    issotrx char(1),
    salesrep_id bigint,
    c_doctypetarget_id bigint
);

CREATE TABLE business.c_orderline (
    c_orderline_id bigint PRIMARY KEY,
    c_order_id bigint REFERENCES business.c_order(c_order_id),
    line numeric(18, 2),
    m_product_id bigint REFERENCES business.m_product(m_product_id),
    qtyordered numeric(18, 4),
    qtydelivered numeric(18, 4),
    qtyinvoiced numeric(18, 4),
    priceactual numeric(18, 4),
    pricelist numeric(18, 4),
    pricelimit numeric(18, 4),
    linenetamt numeric(18, 2),
    c_tax_id bigint
);

CREATE TABLE business.c_invoice (
    c_invoice_id bigint PRIMARY KEY,
    documentno text NOT NULL,
    docstatus char(2),
    ispaid char(1),
    c_bpartner_id bigint REFERENCES business.c_bpartner(c_bpartner_id),
    dateinvoiced timestamp,
    grandtotal numeric(18, 2),
    totallines numeric(18, 2)
);

CREATE TABLE business.lpn_order_flow (
    c_order_id bigint,
    order_no text,
    dateordered timestamp,
    order_status char(2),
    c_orderline_id bigint,
    order_line_no numeric(18, 2),
    m_product_id bigint,
    qtyordered numeric(18, 4),
    qtydelivered numeric(18, 4),
    qtyinvoiced numeric(18, 4),
    m_inout_id bigint,
    shipment_no text,
    movementdate timestamp,
    shipment_status char(2),
    c_invoice_id bigint,
    invoice_no text,
    dateinvoiced timestamp,
    invoice_status char(2),
    ispaid char(1),
    c_invoiceline_id bigint,
    linenetamt numeric(18, 2)
);

CREATE TABLE business.lpn_invoice_payment (
    c_invoice_id bigint,
    invoice_no text,
    dateinvoiced timestamp,
    docstatus char(2),
    ispaid char(1),
    c_allocationline_id bigint,
    c_allocationhdr_id bigint,
    allocation_date timestamp,
    c_payment_id bigint,
    payment_no text,
    payment_date timestamp,
    payamt numeric(18, 2),
    payment_status char(2)
);

CREATE INDEX idx_c_order_dateordered ON business.c_order(dateordered);
CREATE INDEX idx_c_order_bpartner ON business.c_order(c_bpartner_id);
CREATE INDEX idx_c_orderline_order ON business.c_orderline(c_order_id);
CREATE INDEX idx_c_orderline_product ON business.c_orderline(m_product_id);
CREATE INDEX idx_c_invoice_dateinvoiced ON business.c_invoice(dateinvoiced);
CREATE INDEX idx_c_invoice_bpartner ON business.c_invoice(c_bpartner_id);
CREATE INDEX idx_lpn_invoice_payment_invoice ON business.lpn_invoice_payment(c_invoice_id);
"""


if __name__ == "__main__":
    main()

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[1]
CLEAN_CSV_DIR = REPO_ROOT / "Youssef_Extractions" / "vente_clean_import" / "csv"
ENRICHMENT_CSV_DIR = REPO_ROOT / "Youssef_Extractions" / "vente_bi_enrichment_import" / "csv"
OUTPUT_DIR = REPO_ROOT / "docs" / "warehouse"
REPORT_MD = OUTPUT_DIR / "VENTE_DWH_COVERAGE_PROFILE.md"
REPORT_JSON = OUTPUT_DIR / "vente_dwh_coverage_profile.json"


def read_csv(name: str, columns: list[str] | None = None, enrichment: bool = False) -> pd.DataFrame:
    base = ENRICHMENT_CSV_DIR if enrichment else CLEAN_CSV_DIR
    path = base / f"{name}.csv"
    kwargs: dict[str, Any] = {"dtype": "string", "low_memory": False}
    if columns:
        kwargs["usecols"] = columns
    return pd.read_csv(path, **kwargs)


def to_datetime(series: pd.Series) -> pd.Series:
    return pd.to_datetime(series, errors="coerce", utc=True)


def to_numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce").fillna(0)


def non_null_count(series: pd.Series) -> int:
    return int(series.notna().sum())


def distinct_count(series: pd.Series) -> int:
    return int(series.dropna().nunique())


def value_counts_dict(series: pd.Series, limit: int = 12) -> dict[str, int]:
    counts = series.fillna("<NULL>").value_counts().head(limit)
    return {str(index): int(value) for index, value in counts.items()}


def coverage(total: int, matched: int) -> dict[str, Any]:
    return {"matched": int(matched), "total": int(total), "pct": round((matched / total * 100) if total else 0, 2)}


def primary_supplier(product_po: pd.DataFrame) -> pd.DataFrame:
    df = product_po.copy()
    for col in ["UPDATED", "CREATED"]:
        if col in df.columns:
            df[col] = to_datetime(df[col])
    df["_is_current"] = df.get("ISCURRENTVENDOR", "").fillna("").str.upper().eq("Y").astype(int)
    df["_is_active"] = df.get("ISACTIVE", "").fillna("").str.upper().eq("Y").astype(int)
    sort_cols = ["M_PRODUCT_ID", "_is_current", "_is_active"]
    ascending = [True, False, False]
    for col in ["UPDATED", "CREATED"]:
        if col in df.columns:
            sort_cols.append(col)
            ascending.append(False)
    sort_cols.append("C_BPARTNER_ID")
    ascending.append(True)
    df = df.sort_values(sort_cols, ascending=ascending, kind="mergesort")
    return df.drop_duplicates("M_PRODUCT_ID", keep="first")[["M_PRODUCT_ID", "C_BPARTNER_ID"]]


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    orders = read_csv(
        "C_ORDER",
        [
            "C_ORDER_ID",
            "DOCUMENTNO",
            "DOCSTATUS",
            "C_DOCTYPE_ID",
            "C_DOCTYPETARGET_ID",
            "SALESREP_ID",
            "DATEORDERED",
            "C_BPARTNER_ID",
            "C_BPARTNER_LOCATION_ID",
            "C_PAYMENTTERM_ID",
            "M_PRICELIST_ID",
            "C_CAMPAIGN_ID",
            "C_PROJECT_ID",
            "C_ACTIVITY_ID",
            "TOTALLINES",
            "GRANDTOTAL",
        ],
    )
    order_lines = read_csv(
        "C_ORDERLINE",
        ["C_ORDERLINE_ID", "C_ORDER_ID", "C_BPARTNER_ID", "M_PRODUCT_ID", "QTYORDERED", "QTYDELIVERED", "QTYINVOICED", "LINENETAMT"],
    )
    invoices = read_csv(
        "C_INVOICE",
        [
            "C_INVOICE_ID",
            "DOCUMENTNO",
            "DOCSTATUS",
            "C_DOCTYPE_ID",
            "C_ORDER_ID",
            "SALESREP_ID",
            "DATEINVOICED",
            "C_BPARTNER_ID",
            "C_BPARTNER_LOCATION_ID",
            "C_PAYMENTTERM_ID",
            "M_PRICELIST_ID",
            "ISPAID",
            "TOTALLINES",
            "GRANDTOTAL",
        ],
    )
    invoice_lines = read_csv("C_INVOICELINE", ["C_INVOICELINE_ID", "C_INVOICE_ID", "C_ORDERLINE_ID", "M_PRODUCT_ID", "QTYINVOICED", "LINENETAMT"])
    partners = read_csv("C_BPARTNER", ["C_BPARTNER_ID", "VALUE", "NAME", "C_BP_GROUP_ID", "ISCUSTOMER", "ISVENDOR", "SALESREP_ID", "SO_CREDITLIMIT"])
    products = read_csv("M_PRODUCT", ["M_PRODUCT_ID", "VALUE", "NAME", "M_PRODUCT_CATEGORY_ID", "PRODUCTTYPE", "ISSOLD", "ISPURCHASED", "ISSTOCKED"])
    product_categories = read_csv("M_PRODUCT_CATEGORY", ["M_PRODUCT_CATEGORY_ID", "NAME"])
    doctype = read_csv("C_DOCTYPE", ["C_DOCTYPE_ID", "NAME", "DOCBASETYPE", "DOCSUBTYPESO"])
    inouts = read_csv("M_INOUT", ["M_INOUT_ID", "DOCSTATUS", "C_DOCTYPE_ID", "C_ORDER_ID", "MOVEMENTDATE", "C_BPARTNER_ID", "M_WAREHOUSE_ID"])
    inout_lines = read_csv("M_INOUTLINE", ["M_INOUTLINE_ID", "M_INOUT_ID", "C_ORDERLINE_ID", "M_PRODUCT_ID", "MOVEMENTQTY"])
    allocations = read_csv("C_ALLOCATIONLINE", ["C_ALLOCATIONLINE_ID", "C_INVOICE_ID", "C_PAYMENT_ID", "C_BPARTNER_ID", "C_ORDER_ID", "AMOUNT"])
    payments = read_csv("C_PAYMENT", ["C_PAYMENT_ID", "DOCUMENTNO", "DATETRX", "ISRECEIPT", "TENDERTYPE", "C_BPARTNER_ID", "PAYAMT", "DOCSTATUS"])
    storage = read_csv("RV_STORAGE", ["M_PRODUCT_ID", "M_WAREHOUSE_ID", "M_LOCATOR_ID", "QTYONHAND", "QTYRESERVED", "QTYAVAILABLE", "QTYORDERED"])

    ad_user = read_csv("AD_USER", ["AD_USER_ID", "NAME", "EMAIL"], enrichment=True)
    product_po = read_csv("M_PRODUCT_PO", ["M_PRODUCT_ID", "C_BPARTNER_ID", "ISACTIVE", "ISCURRENTVENDOR", "UPDATED", "CREATED"], enrichment=True)
    vendors = read_csv("C_BPARTNER_VENDOR", ["C_BPARTNER_ID", "NAME", "VALUE", "C_BP_GROUP_ID"], enrichment=True)
    product_type = read_csv("M_PRODUCT_TYPE", enrichment=True)
    product_theme = read_csv("M_PRODUCT_THEME", enrichment=True)
    product_collection = read_csv("M_PRODUCT_COLLECTION", enrichment=True)
    products_without_supplier = read_csv("PRODUCTS_WITHOUT_SUPPLIER", enrichment=True)

    orders["DATEORDERED"] = to_datetime(orders["DATEORDERED"])
    invoices["DATEINVOICED"] = to_datetime(invoices["DATEINVOICED"])
    inouts["MOVEMENTDATE"] = to_datetime(inouts["MOVEMENTDATE"])
    payments["DATETRX"] = to_datetime(payments["DATETRX"])

    order_grandtotal = to_numeric(orders["GRANDTOTAL"])
    invoice_grandtotal = to_numeric(invoices["GRANDTOTAL"])

    salesrep_ids = pd.concat([orders["SALESREP_ID"], invoices["SALESREP_ID"]]).dropna().drop_duplicates()
    mapped_salesreps = salesrep_ids[salesrep_ids.isin(ad_user["AD_USER_ID"])]

    customer_ids = pd.concat([orders["C_BPARTNER_ID"], invoices["C_BPARTNER_ID"]]).dropna().drop_duplicates()
    mapped_customers = customer_ids[customer_ids.isin(partners["C_BPARTNER_ID"])]

    product_ids = pd.concat([order_lines["M_PRODUCT_ID"], invoice_lines["M_PRODUCT_ID"], inout_lines["M_PRODUCT_ID"]]).dropna().drop_duplicates()
    mapped_products = product_ids[product_ids.isin(products["M_PRODUCT_ID"])]

    primary_supplier_df = primary_supplier(product_po)
    invoice_products = invoice_lines["M_PRODUCT_ID"].dropna().drop_duplicates()
    order_products = order_lines["M_PRODUCT_ID"].dropna().drop_duplicates()
    invoice_supplier_match = invoice_products[invoice_products.isin(primary_supplier_df["M_PRODUCT_ID"])]
    order_supplier_match = order_products[order_products.isin(primary_supplier_df["M_PRODUCT_ID"])]

    allocations_invoice_match = allocations["C_INVOICE_ID"].dropna()
    allocations_payment_match = allocations["C_PAYMENT_ID"].dropna()

    profile: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "periods": {
            "orders": {
                "min_date": str(orders["DATEORDERED"].min()),
                "max_date": str(orders["DATEORDERED"].max()),
                "rows": len(orders),
                "total_grandtotal": round(float(order_grandtotal.sum()), 2),
            },
            "invoices": {
                "min_date": str(invoices["DATEINVOICED"].min()),
                "max_date": str(invoices["DATEINVOICED"].max()),
                "rows": len(invoices),
                "total_grandtotal": round(float(invoice_grandtotal.sum()), 2),
            },
            "deliveries": {
                "min_date": str(inouts["MOVEMENTDATE"].min()),
                "max_date": str(inouts["MOVEMENTDATE"].max()),
                "rows": len(inouts),
            },
            "payments": {
                "min_date": str(payments["DATETRX"].min()),
                "max_date": str(payments["DATETRX"].max()),
                "rows": len(payments),
            },
        },
        "row_counts": {
            "orders": len(orders),
            "order_lines": len(order_lines),
            "invoices": len(invoices),
            "invoice_lines": len(invoice_lines),
            "partners": len(partners),
            "products": len(products),
            "product_categories": len(product_categories),
            "deliveries": len(inouts),
            "delivery_lines": len(inout_lines),
            "allocations": len(allocations),
            "payments": len(payments),
            "storage_rows": len(storage),
            "salesreps": len(ad_user),
            "supplier_product_links": len(product_po),
            "vendors": len(vendors),
            "product_types": len(product_type),
            "product_themes": len(product_theme),
            "product_collections": len(product_collection),
            "products_without_supplier": len(products_without_supplier),
        },
        "key_distributions": {
            "order_docstatus": value_counts_dict(orders["DOCSTATUS"]),
            "invoice_docstatus": value_counts_dict(invoices["DOCSTATUS"]),
            "invoice_paid_flag": value_counts_dict(invoices["ISPAID"]),
            "payment_tender_type": value_counts_dict(payments["TENDERTYPE"]),
            "order_doctype_ids": value_counts_dict(orders["C_DOCTYPE_ID"]),
            "invoice_doctype_ids": value_counts_dict(invoices["C_DOCTYPE_ID"]),
        },
        "coverage": {
            "salesrep_mapping": coverage(len(salesrep_ids), len(mapped_salesreps)),
            "customer_mapping": coverage(len(customer_ids), len(mapped_customers)),
            "product_mapping": coverage(len(product_ids), len(mapped_products)),
            "invoice_product_primary_supplier_mapping": coverage(len(invoice_products), len(invoice_supplier_match)),
            "order_product_primary_supplier_mapping": coverage(len(order_products), len(order_supplier_match)),
            "allocation_invoice_links": coverage(len(allocations), non_null_count(allocations["C_INVOICE_ID"])),
            "allocation_payment_links": coverage(len(allocations), non_null_count(allocations["C_PAYMENT_ID"])),
        },
        "missing_reference_dimensions_detected_from_foreign_keys": {
            "C_BP_GROUP": {
                "present_in_c_bpartner_rows": non_null_count(partners["C_BP_GROUP_ID"]),
                "distinct_ids": distinct_count(partners["C_BP_GROUP_ID"]),
                "status": "missing valid dimension export; 23_C_BP_GROUP.xlsx is actually M_PRODUCT_TYPE-shaped.",
            },
            "C_BPARTNER_LOCATION": {
                "orders_with_location_id": non_null_count(orders["C_BPARTNER_LOCATION_ID"]),
                "invoices_with_location_id": non_null_count(invoices["C_BPARTNER_LOCATION_ID"]),
                "status": "missing table; needed for address/geography/client site analysis.",
            },
            "C_PAYMENTTERM": {
                "orders_with_payment_term": non_null_count(orders["C_PAYMENTTERM_ID"]),
                "invoices_with_payment_term": non_null_count(invoices["C_PAYMENTTERM_ID"]),
                "distinct_order_terms": distinct_count(orders["C_PAYMENTTERM_ID"]),
                "distinct_invoice_terms": distinct_count(invoices["C_PAYMENTTERM_ID"]),
                "status": "missing table; needed for payment-term labels and aging semantics.",
            },
            "M_PRICELIST": {
                "orders_with_pricelist": non_null_count(orders["M_PRICELIST_ID"]),
                "invoices_with_pricelist": non_null_count(invoices["M_PRICELIST_ID"]),
                "distinct_ids": distinct_count(pd.concat([orders["M_PRICELIST_ID"], invoices["M_PRICELIST_ID"]])),
                "status": "missing table; useful for price list/channel analysis.",
            },
            "C_CAMPAIGN_C_PROJECT_C_ACTIVITY": {
                "orders_with_campaign": non_null_count(orders["C_CAMPAIGN_ID"]),
                "orders_with_project": non_null_count(orders["C_PROJECT_ID"]),
                "orders_with_activity": non_null_count(orders["C_ACTIVITY_ID"]),
                "status": "dimension tables missing; export only if business uses campaign/project/activity sales reporting.",
            },
        },
    }

    REPORT_JSON.write_text(json.dumps(profile, indent=2, ensure_ascii=False), encoding="utf-8")
    REPORT_MD.write_text(render_markdown(profile), encoding="utf-8")
    print(f"Wrote {REPORT_MD}")
    print(f"Wrote {REPORT_JSON}")


def render_markdown(profile: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# Vente Data Warehouse Coverage Profile")
    lines.append("")
    lines.append(f"Generated at: `{profile['generated_at']}`")
    lines.append("")
    lines.append("## Period Coverage")
    lines.append("")
    lines.append("| Area | Rows | Date min | Date max | Amount |")
    lines.append("|---|---:|---|---|---:|")
    for area, meta in profile["periods"].items():
        amount = meta.get("total_grandtotal", "")
        lines.append(f"| {area} | {meta['rows']} | {meta['min_date']} | {meta['max_date']} | {amount} |")
    lines.append("")
    lines.append("## Row Counts")
    lines.append("")
    lines.append("| Dataset | Rows |")
    lines.append("|---|---:|")
    for name, count in profile["row_counts"].items():
        lines.append(f"| `{name}` | {count} |")
    lines.append("")
    lines.append("## Mapping Coverage")
    lines.append("")
    lines.append("| Mapping | Matched | Total | Coverage |")
    lines.append("|---|---:|---:|---:|")
    for name, meta in profile["coverage"].items():
        lines.append(f"| `{name}` | {meta['matched']} | {meta['total']} | {meta['pct']}% |")
    lines.append("")
    lines.append("## Key Distributions")
    lines.append("")
    for name, values in profile["key_distributions"].items():
        lines.append(f"### `{name}`")
        lines.append("")
        lines.append("| Value | Rows |")
        lines.append("|---|---:|")
        for value, count in values.items():
            lines.append(f"| `{value}` | {count} |")
        lines.append("")
    lines.append("## Missing Reference Dimensions Detected From Existing Keys")
    lines.append("")
    for name, meta in profile["missing_reference_dimensions_detected_from_foreign_keys"].items():
        lines.append(f"### `{name}`")
        lines.append("")
        for key, value in meta.items():
            lines.append(f"- `{key}`: {value}")
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    main()

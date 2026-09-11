from __future__ import annotations

from pathlib import Path
from typing import Iterable

import pandas as pd


BASE = Path(r"D:\LPN_PROJECT\Youssef_Extractions\data\Exported_LPN")

FILES = {
    "orders": "53_COMMERCIAL_ORDER_HEADER_24M.xlsx",
    "order_lines": "54_COMMERCIAL_ORDER_LINE_24M.xlsx",
    "invoices": "55_COMMERCIAL_INVOICE_HEADER_24M.xlsx",
    "invoice_lines": "56_COMMERCIAL_INVOICE_LINE_24M.xlsx",
    "customer_portfolio": "57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx",
    "customer_location": "58_COMMERCIAL_CUSTOMER_LOCATION_24M.xlsx",
}


def money(value: float | int | None) -> str:
    if value is None or pd.isna(value):
        return "n/a"
    return f"{float(value):,.2f}"


def number(value: float | int | None) -> str:
    if value is None or pd.isna(value):
        return "n/a"
    return f"{int(value):,}"


def percent(value: float | int | None) -> str:
    if value is None or pd.isna(value):
        return "n/a"
    return f"{float(value):.2f}%"


def read_xlsx(name: str, columns: list[str] | None = None) -> pd.DataFrame:
    path = BASE / name
    return pd.read_excel(path, engine="openpyxl", usecols=columns)


def date_range(df: pd.DataFrame, column: str) -> str:
    dates = pd.to_datetime(df[column], errors="coerce")
    if dates.notna().sum() == 0:
        return "n/a"
    return f"{dates.min().date()} -> {dates.max().date()} ({dates.dt.to_period('M').nunique()} months)"


def top_values(df: pd.DataFrame, group_col: str, value_col: str, n: int = 8) -> pd.DataFrame:
    data = df.copy()
    data[value_col] = pd.to_numeric(data[value_col], errors="coerce").fillna(0)
    return (
        data.groupby(group_col, dropna=False)[value_col]
        .sum()
        .sort_values(ascending=False)
        .head(n)
        .reset_index()
    )


def completeness(df: pd.DataFrame, columns: Iterable[str]) -> pd.DataFrame:
    rows = []
    total = len(df)
    for col in columns:
        if col in df.columns:
            non_null = int(df[col].notna().sum())
            rows.append(
                {
                    "column": col,
                    "non_null": non_null,
                    "null": total - non_null,
                    "coverage_pct": round(non_null * 100 / total, 2) if total else 0,
                }
            )
    return pd.DataFrame(rows)


def print_section(title: str) -> None:
    print(f"\n## {title}")


def print_df(df: pd.DataFrame, max_rows: int = 12) -> None:
    if df.empty:
        print("(empty)")
        return
    print(df.head(max_rows).to_string(index=False))


def main() -> None:
    print("# Commercial 24M Export Profiling")
    print(f"Source folder: {BASE}")

    orders = read_xlsx(FILES["orders"])
    order_lines = read_xlsx(FILES["order_lines"])
    invoices = read_xlsx(FILES["invoices"])
    invoice_lines = read_xlsx(FILES["invoice_lines"])
    customers = read_xlsx(FILES["customer_portfolio"])
    locations = read_xlsx(FILES["customer_location"])

    datasets = {
        "orders": orders,
        "order_lines": order_lines,
        "invoices": invoices,
        "invoice_lines": invoice_lines,
        "customer_portfolio": customers,
        "customer_location": locations,
    }

    print_section("Dataset inventory")
    for name, df in datasets.items():
        print(f"- {name}: rows={number(len(df))}, cols={number(len(df.columns))}")
        print(f"  columns={', '.join(map(str, df.columns[:18]))}{'...' if len(df.columns) > 18 else ''}")

    print_section("Date ranges")
    for label, df, col in [
        ("orders", orders, "DATEORDERED"),
        ("order_lines", order_lines, "DATEORDERED"),
        ("invoices", invoices, "DATEINVOICED"),
        ("invoice_lines", invoice_lines, "DATEINVOICED"),
    ]:
        print(f"- {label}.{col}: {date_range(df, col)}")

    print_section("Main commercial coverage")
    for label, df in [
        ("orders", orders),
        ("order_lines", order_lines),
        ("invoices", invoices),
        ("invoice_lines", invoice_lines),
    ]:
        print(
            f"- {label}: salesreps={number(df['SALESREP_ID'].nunique())}, "
            f"commercial_names={number(df['COMMERCIAL_NAME'].nunique())}, "
            f"customers={number(df['C_BPARTNER_ID'].nunique()) if 'C_BPARTNER_ID' in df else 'n/a'}"
        )

    print_section("Order vs invoice totals")
    order_total = pd.to_numeric(orders["GRANDTOTAL"], errors="coerce").sum()
    order_line_total = pd.to_numeric(order_lines["LINENETAMT"], errors="coerce").sum()
    invoice_total = pd.to_numeric(invoices["GRANDTOTAL"], errors="coerce").sum()
    invoice_line_total = pd.to_numeric(invoice_lines["LINENETAMT"], errors="coerce").sum()
    print(f"- Order headers GRANDTOTAL: {money(order_total)}")
    print(f"- Order lines LINENETAMT: {money(order_line_total)}")
    print(f"- Invoice headers GRANDTOTAL: {money(invoice_total)}")
    print(f"- Invoice lines LINENETAMT: {money(invoice_line_total)}")
    print(f"- Header invoice/order coverage: {percent(invoice_total / order_total * 100 if order_total else None)}")

    print_section("Order lifecycle and fulfillment")
    for col in ["DOCSTATUS", "ISDELIVERED", "ISINVOICED"]:
        if col in orders.columns:
            print(f"\n{col}")
            print_df(orders[col].value_counts(dropna=False).reset_index().rename(columns={"index": col, col: "count"}))
    qty_ordered = pd.to_numeric(order_lines["QTYORDERED"], errors="coerce").sum()
    qty_delivered = pd.to_numeric(order_lines["QTYDELIVERED"], errors="coerce").sum()
    qty_invoiced = pd.to_numeric(order_lines["QTYINVOICED"], errors="coerce").sum()
    print(f"\n- Qty ordered: {number(qty_ordered)}")
    print(f"- Qty delivered: {number(qty_delivered)} ({percent(qty_delivered / qty_ordered * 100 if qty_ordered else None)})")
    print(f"- Qty invoiced: {number(qty_invoiced)} ({percent(qty_invoiced / qty_ordered * 100 if qty_ordered else None)})")

    print_section("Invoice payment signal")
    print_df(invoices["ISPAID"].value_counts(dropna=False).reset_index().rename(columns={"index": "ISPAID", "ISPAID": "count"}))
    paid_ca = invoices.assign(GRANDTOTAL_NUM=pd.to_numeric(invoices["GRANDTOTAL"], errors="coerce").fillna(0)).groupby("ISPAID", dropna=False)["GRANDTOTAL_NUM"].sum().reset_index()
    print_df(paid_ca)

    print_section("Top commercials")
    print("\nBy ordered CA")
    print_df(top_values(orders, "COMMERCIAL_NAME", "GRANDTOTAL"))
    print("\nBy invoiced CA")
    print_df(top_values(invoices, "COMMERCIAL_NAME", "GRANDTOTAL"))

    print_section("Top product dimensions from invoice lines")
    for group in ["PRODUCT_CATEGORY", "PRODUCT_THEME", "PRODUCT_TYPE", "PRODUCT_COLLECTION", "DISTRIBUTEUR_NAME"]:
        if group in invoice_lines.columns:
            print(f"\n{group}")
            print_df(top_values(invoice_lines, group, "LINENETAMT", n=10))

    print_section("Customer portfolio")
    print(f"- Customers in portfolio: {number(len(customers))}")
    print(f"- Assigned commercials: {number(customers['ASSIGNED_SALESREP_ID'].nunique())}")
    print(f"- Customers with orders 24M: {number((pd.to_numeric(customers['ORDER_COUNT_24M'], errors='coerce').fillna(0) > 0).sum())}")
    print(f"- Customers with invoices 24M: {number((pd.to_numeric(customers['INVOICE_COUNT_24M'], errors='coerce').fillna(0) > 0).sum())}")
    print("\nTop customers by invoiced CA")
    print_df(top_values(customers, "CUSTOMER_NAME", "INVOICED_CA_24M", n=10))

    print_section("Location coverage")
    print(f"- Location rows: {number(len(locations))}")
    for col in ["CITY", "CITYNAME", "REGIONNAME", "SALES_REGION_NAME", "C_SALESREGION_ID"]:
        if col in locations.columns:
            non_null = locations[col].notna().sum()
            print(f"- {col}: {number(non_null)} non-null / {number(len(locations))} ({percent(non_null / len(locations) * 100)})")

    print_section("Key completeness")
    key_sets = {
        "orders": ["C_ORDER_ID", "DATEORDERED", "SALESREP_ID", "COMMERCIAL_NAME", "C_BPARTNER_ID", "C_BPARTNER_LOCATION_ID", "C_DOCTYPE_ID", "M_PRICELIST_ID", "C_PAYMENTTERM_ID", "GRANDTOTAL"],
        "order_lines": ["C_ORDERLINE_ID", "C_ORDER_ID", "M_PRODUCT_ID", "PRODUCT_CATEGORY", "PRODUCT_THEME", "PRODUCT_TYPE", "PRODUCT_COLLECTION", "DISTRIBUTEUR_ID", "DISTRIBUTEUR_NAME", "LINENETAMT"],
        "invoices": ["C_INVOICE_ID", "DATEINVOICED", "C_ORDER_ID", "SALESREP_ID", "COMMERCIAL_NAME", "C_BPARTNER_ID", "C_PAYMENTTERM_ID", "GRANDTOTAL", "ISPAID"],
        "invoice_lines": ["C_INVOICELINE_ID", "C_INVOICE_ID", "C_ORDERLINE_ID", "M_PRODUCT_ID", "PRODUCT_CATEGORY", "PRODUCT_THEME", "PRODUCT_TYPE", "PRODUCT_COLLECTION", "DISTRIBUTEUR_ID", "DISTRIBUTEUR_NAME", "LINENETAMT"],
        "customer_portfolio": ["C_BPARTNER_ID", "CUSTOMER_NAME", "ASSIGNED_SALESREP_ID", "ASSIGNED_COMMERCIAL_NAME", "C_BP_GROUP_ID", "PAYMENTTERM_NAME", "PRICELIST_NAME", "ORDER_COUNT_24M", "INVOICE_COUNT_24M"],
        "customer_location": ["C_BPARTNER_LOCATION_ID", "C_BPARTNER_ID", "CITY", "CITYNAME", "REGIONNAME", "SALES_REGION_NAME", "C_CITY_ID", "C_REGION_ID"],
    }
    for label, cols in key_sets.items():
        print(f"\n{label}")
        print_df(completeness(datasets[label], cols), max_rows=30)

    print_section("Join coverage")
    order_ids = set(orders["C_ORDER_ID"].dropna())
    line_order_ids = set(order_lines["C_ORDER_ID"].dropna())
    invoice_order_ids = set(invoices["C_ORDER_ID"].dropna())
    invoice_line_orderline_ids = set(invoice_lines["C_ORDERLINE_ID"].dropna())
    orderline_ids = set(order_lines["C_ORDERLINE_ID"].dropna())
    print(f"- Order headers with at least one exported line: {number(len(order_ids & line_order_ids))} / {number(len(order_ids))}")
    print(f"- Invoices linked to an exported order: {number(len(invoice_order_ids & order_ids))} / {number(len(invoice_order_ids))}")
    print(f"- Invoice lines linked to exported order lines: {number(len(invoice_line_orderline_ids & orderline_ids))} / {number(len(invoice_line_orderline_ids))}")
    print(f"- Invoice headers without C_ORDER_ID: {number(invoices['C_ORDER_ID'].isna().sum())} / {number(len(invoices))}")


if __name__ == "__main__":
    main()

from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

import pandas as pd

from extract_sources import load_config, load_sources, project_path
from transform_dimensions import as_id, as_num, build_dimensions, date_key


def key_map(dim: pd.DataFrame, natural_key: str, surrogate_key: str) -> pd.Series:
    series = dim.dropna(subset=[natural_key]).drop_duplicates(natural_key).set_index(natural_key)[surrogate_key]
    return series


def map_key(series: pd.Series, mapping: pd.Series) -> pd.Series:
    return series.astype("string").map(mapping).fillna(0).astype("Int64")


def primary_supplier(product_po: pd.DataFrame) -> pd.DataFrame:
    df = product_po.copy()
    for col in ("updated", "created"):
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], errors="coerce")
    df["_is_current"] = df.get("iscurrentvendor", "").astype("string").str.upper().eq("Y").astype(int)
    df["_is_active"] = df.get("isactive", "").astype("string").str.upper().eq("Y").astype(int)
    sort_cols = ["m_product_id", "_is_current", "_is_active"]
    ascending = [True, False, False]
    for col in ("updated", "created"):
        if col in df.columns:
            sort_cols.append(col)
            ascending.append(False)
    sort_cols.append("c_bpartner_id")
    ascending.append(True)
    df = df.sort_values(sort_cols, ascending=ascending, kind="mergesort")
    return df.drop_duplicates("m_product_id", keep="first")[["m_product_id", "c_bpartner_id"]]


def build_lookup(dimensions: dict[str, pd.DataFrame]) -> dict[str, pd.Series]:
    lookup = {
        "customer": key_map(dimensions["dim_customer"], "c_bpartner_id", "customer_key"),
        "commercial": key_map(dimensions["dim_commercial"], "ad_user_id", "commercial_key"),
        "product": key_map(dimensions["dim_product"], "m_product_id", "product_key"),
        "category": key_map(dimensions["dim_product_category"], "m_product_category_id", "product_category_key"),
        "supplier": key_map(dimensions["dim_supplier"], "c_bpartner_id", "supplier_key"),
        "geography": key_map(dimensions["dim_geography"], "c_bpartner_location_id", "geography_key"),
        "payment_term": key_map(dimensions["dim_payment_term"], "c_paymentterm_id", "payment_term_key"),
        "price_list": key_map(dimensions["dim_price_list"], "m_pricelist_id", "price_list_key"),
        "document_type": key_map(dimensions["dim_document_type"], "c_doctype_id", "document_type_key"),
        "warehouse": key_map(dimensions["dim_warehouse"], "m_warehouse_id", "warehouse_key"),
        "warehouse_locator": key_map(dimensions["dim_warehouse"], "m_locator_id", "warehouse_key"),
    }
    return lookup


def product_supplier_map(sources: dict[str, pd.DataFrame], dimensions: dict[str, pd.DataFrame]) -> pd.DataFrame:
    supplier = primary_supplier(sources["m_product_po"])
    supplier["m_product_id"] = as_id(supplier["m_product_id"])
    supplier["c_bpartner_id"] = as_id(supplier["c_bpartner_id"])
    supplier_lookup = key_map(dimensions["dim_supplier"], "c_bpartner_id", "supplier_key")
    supplier["supplier_key"] = map_key(supplier["c_bpartner_id"], supplier_lookup)
    return supplier[["m_product_id", "supplier_key"]]


def prepare_sources(sources: dict[str, pd.DataFrame]) -> dict[str, pd.DataFrame]:
    prepared = {name: df.copy() for name, df in sources.items()}
    id_columns = [
        "c_order_id",
        "c_orderline_id",
        "c_invoice_id",
        "c_invoiceline_id",
        "m_inout_id",
        "m_inoutline_id",
        "c_bpartner_id",
        "salesrep_id",
        "ad_user_id",
        "m_product_id",
        "m_product_category_id",
        "c_bpartner_location_id",
        "c_paymentterm_id",
        "m_pricelist_id",
        "c_doctype_id",
        "c_doctypetarget_id",
        "m_warehouse_id",
        "m_locator_id",
        "c_allocationline_id",
        "c_allocationhdr_id",
        "c_payment_id",
    ]
    for df in prepared.values():
        for col in id_columns:
            if col in df.columns:
                df[col] = as_id(df[col])
    return prepared


def build_facts(sources: dict[str, pd.DataFrame], dimensions: dict[str, pd.DataFrame]) -> dict[str, pd.DataFrame]:
    src = prepare_sources(sources)
    lk = build_lookup(dimensions)
    prod_supplier = product_supplier_map(src, dimensions)
    prod_cat = dimensions["dim_product"][["m_product_id", "product_category_key", "supplier_key"]].copy()

    order = src["c_order"].copy()
    fact_sales_order = pd.DataFrame(
        {
            "c_order_id": order["c_order_id"],
            "document_no": order.get("documentno"),
            "doc_status": order.get("docstatus"),
            "po_reference": order.get("poreference"),
            "order_date_key": order.get("dateordered").map(date_key),
            "customer_key": map_key(order.get("c_bpartner_id"), lk["customer"]),
            "commercial_key": map_key(order.get("salesrep_id"), lk["commercial"]),
            "geography_key": map_key(order.get("c_bpartner_location_id"), lk["geography"]),
            "payment_term_key": map_key(order.get("c_paymentterm_id"), lk["payment_term"]),
            "price_list_key": map_key(order.get("m_pricelist_id"), lk["price_list"]),
            "document_type_key": map_key(order.get("c_doctype_id"), lk["document_type"]),
            "warehouse_key": map_key(order.get("m_warehouse_id"), lk["warehouse"]),
            "total_lines_amount": as_num(order.get("totallines")),
            "grand_total_amount": as_num(order.get("grandtotal")),
            "freight_amount": as_num(order.get("freightamt")),
            "charge_amount": as_num(order.get("chargeamt")),
            "order_count": 1,
        }
    ).drop_duplicates("c_order_id")
    fact_sales_order.insert(0, "sales_order_key", range(1, len(fact_sales_order) + 1))

    order_line = src["c_orderline"].copy()
    order_context = order[["c_order_id", "documentno", "docstatus", "dateordered", "salesrep_id", "c_doctype_id"]].drop_duplicates("c_order_id")
    order_line = order_line.merge(order_context, how="left", on="c_order_id", suffixes=("", "_order"))
    order_line = order_line.merge(prod_cat, how="left", on="m_product_id")
    fact_sales_order_line = pd.DataFrame(
        {
            "c_orderline_id": order_line["c_orderline_id"],
            "c_order_id": order_line["c_order_id"],
            "document_no": order_line.get("documentno"),
            "doc_status": order_line.get("docstatus"),
            "line_no": as_num(order_line.get("line")),
            "order_date_key": order_line.get("dateordered").map(date_key),
            "customer_key": map_key(order_line.get("c_bpartner_id"), lk["customer"]),
            "commercial_key": map_key(order_line.get("salesrep_id"), lk["commercial"]),
            "product_key": map_key(order_line.get("m_product_id"), lk["product"]),
            "product_category_key": order_line.get("product_category_key").fillna(0).astype("Int64"),
            "supplier_key": order_line.get("supplier_key").fillna(0).astype("Int64"),
            "warehouse_key": map_key(order_line.get("m_warehouse_id"), lk["warehouse"]),
            "document_type_key": map_key(order_line.get("c_doctype_id"), lk["document_type"]),
            "quantity_ordered": as_num(order_line.get("qtyordered")),
            "quantity_delivered": as_num(order_line.get("qtydelivered")),
            "quantity_invoiced": as_num(order_line.get("qtyinvoiced")),
            "price_list": as_num(order_line.get("pricelist")),
            "price_actual": as_num(order_line.get("priceactual")),
            "discount_percent": as_num(order_line.get("discount", pd.Series(index=order_line.index))),
            "line_net_amount": as_num(order_line.get("linenetamt")),
            "line_count": 1,
        }
    ).drop_duplicates("c_orderline_id")
    fact_sales_order_line.insert(0, "sales_order_line_key", range(1, len(fact_sales_order_line) + 1))

    invoice = src["c_invoice"].copy()
    fact_invoice = pd.DataFrame(
        {
            "c_invoice_id": invoice["c_invoice_id"],
            "c_order_id": invoice.get("c_order_id"),
            "document_no": invoice.get("documentno"),
            "doc_status": invoice.get("docstatus"),
            "is_paid": invoice.get("ispaid").astype("string").str.upper().eq("Y"),
            "invoice_date_key": invoice.get("dateinvoiced").map(date_key),
            "customer_key": map_key(invoice.get("c_bpartner_id"), lk["customer"]),
            "commercial_key": map_key(invoice.get("salesrep_id"), lk["commercial"]),
            "geography_key": map_key(invoice.get("c_bpartner_location_id"), lk["geography"]),
            "payment_term_key": map_key(invoice.get("c_paymentterm_id"), lk["payment_term"]),
            "price_list_key": map_key(invoice.get("m_pricelist_id"), lk["price_list"]),
            "document_type_key": map_key(invoice.get("c_doctype_id"), lk["document_type"]),
            "total_lines_amount": as_num(invoice.get("totallines")),
            "grand_total_amount": as_num(invoice.get("grandtotal")),
            "invoice_count": 1,
            "paid_invoice_count": invoice.get("ispaid").astype("string").str.upper().eq("Y").astype(int),
            "unpaid_invoice_count": invoice.get("ispaid").astype("string").str.upper().eq("N").astype(int),
        }
    ).drop_duplicates("c_invoice_id")
    fact_invoice.insert(0, "invoice_key", range(1, len(fact_invoice) + 1))

    invoice_line = src["c_invoiceline"].copy()
    invoice_context = invoice[["c_invoice_id", "documentno", "dateinvoiced", "salesrep_id", "c_bpartner_id", "c_doctype_id"]].drop_duplicates("c_invoice_id")
    invoice_line = invoice_line.merge(invoice_context, how="left", on="c_invoice_id", suffixes=("", "_invoice"))
    invoice_line = invoice_line.merge(prod_cat, how="left", on="m_product_id")
    fact_invoice_line = pd.DataFrame(
        {
            "c_invoiceline_id": invoice_line["c_invoiceline_id"],
            "c_invoice_id": invoice_line["c_invoice_id"],
            "c_orderline_id": invoice_line.get("c_orderline_id"),
            "document_no": invoice_line.get("documentno"),
            "line_no": as_num(invoice_line.get("line")),
            "invoice_date_key": invoice_line.get("dateinvoiced").map(date_key),
            "customer_key": map_key(invoice_line.get("c_bpartner_id"), lk["customer"]),
            "commercial_key": map_key(invoice_line.get("salesrep_id"), lk["commercial"]),
            "product_key": map_key(invoice_line.get("m_product_id"), lk["product"]),
            "product_category_key": invoice_line.get("product_category_key").fillna(0).astype("Int64"),
            "supplier_key": invoice_line.get("supplier_key").fillna(0).astype("Int64"),
            "document_type_key": map_key(invoice_line.get("c_doctype_id"), lk["document_type"]),
            "quantity_invoiced": as_num(invoice_line.get("qtyinvoiced")),
            "price_list": as_num(invoice_line.get("pricelist")),
            "price_actual": as_num(invoice_line.get("priceactual")),
            "line_net_amount": as_num(invoice_line.get("linenetamt")),
            "invoice_line_count": 1,
        }
    ).drop_duplicates("c_invoiceline_id")
    fact_invoice_line.insert(0, "invoice_line_key", range(1, len(fact_invoice_line) + 1))

    delivery = src["m_inout"].copy()
    fact_delivery = pd.DataFrame(
        {
            "m_inout_id": delivery["m_inout_id"],
            "c_order_id": delivery.get("c_order_id"),
            "document_no": delivery.get("documentno"),
            "doc_status": delivery.get("docstatus"),
            "movement_date_key": delivery.get("movementdate").map(date_key),
            "customer_key": map_key(delivery.get("c_bpartner_id"), lk["customer"]),
            "geography_key": map_key(delivery.get("c_bpartner_location_id"), lk["geography"]),
            "warehouse_key": map_key(delivery.get("m_warehouse_id"), lk["warehouse"]),
            "document_type_key": map_key(delivery.get("c_doctype_id"), lk["document_type"]),
            "delivery_count": 1,
        }
    ).drop_duplicates("m_inout_id")
    fact_delivery.insert(0, "delivery_key", range(1, len(fact_delivery) + 1))

    delivery_line = src["m_inoutline"].copy()
    delivery_context = delivery[["m_inout_id", "movementdate", "c_bpartner_id"]].drop_duplicates("m_inout_id")
    delivery_line = delivery_line.merge(delivery_context, how="left", on="m_inout_id")
    delivery_line = delivery_line.merge(prod_cat, how="left", on="m_product_id")
    fact_delivery_line = pd.DataFrame(
        {
            "m_inoutline_id": delivery_line["m_inoutline_id"],
            "m_inout_id": delivery_line["m_inout_id"],
            "c_orderline_id": delivery_line.get("c_orderline_id"),
            "line_no": as_num(delivery_line.get("line")),
            "movement_date_key": delivery_line.get("movementdate").map(date_key),
            "customer_key": map_key(delivery_line.get("c_bpartner_id"), lk["customer"]),
            "product_key": map_key(delivery_line.get("m_product_id"), lk["product"]),
            "product_category_key": delivery_line.get("product_category_key").fillna(0).astype("Int64"),
            "supplier_key": delivery_line.get("supplier_key").fillna(0).astype("Int64"),
            "warehouse_key": map_key(delivery_line.get("m_locator_id"), lk["warehouse_locator"]),
            "movement_quantity": as_num(delivery_line.get("movementqty")),
            "entered_quantity": as_num(delivery_line.get("qtyentered")),
            "quantity_on_hand_snapshot": as_num(delivery_line.get("qtyonhand")),
            "quantity_reserved_snapshot": as_num(delivery_line.get("qtyreserved")),
            "availability_indicator": delivery_line.get("disponibilite"),
            "delivery_line_count": 1,
        }
    ).drop_duplicates("m_inoutline_id")
    fact_delivery_line.insert(0, "delivery_line_key", range(1, len(fact_delivery_line) + 1))

    allocation = src["c_allocationline"].copy()
    allocation_hdr = src["c_allocationhdr"][["c_allocationhdr_id", "datetrx"]].drop_duplicates("c_allocationhdr_id")
    payment = src["c_payment"][["c_payment_id", "documentno", "payamt", "c_doctype_id"]].drop_duplicates("c_payment_id")
    allocation = allocation.merge(allocation_hdr, how="left", on="c_allocationhdr_id")
    allocation = allocation.merge(payment, how="left", on="c_payment_id", suffixes=("", "_payment"))
    allocation_date = allocation.get("datetrx")
    if allocation_date is None:
        allocation_date = allocation.get("datetrx_x")
    if "datetrx_y" in allocation.columns:
        allocation_date = allocation_date.combine_first(allocation["datetrx_y"]) if allocation_date is not None else allocation["datetrx_y"]
    fact_payment_allocation = pd.DataFrame(
        {
            "c_allocationline_id": allocation["c_allocationline_id"],
            "c_allocationhdr_id": allocation.get("c_allocationhdr_id"),
            "c_invoice_id": allocation.get("c_invoice_id"),
            "c_payment_id": allocation.get("c_payment_id"),
            "c_order_id": allocation.get("c_order_id"),
            "payment_document_no": allocation.get("documentno"),
            "allocation_date_key": allocation_date.map(date_key),
            "customer_key": map_key(allocation.get("c_bpartner_id"), lk["customer"]),
            "commercial_key": map_key(allocation.get("salesrep_id"), lk["commercial"])
            if "salesrep_id" in allocation.columns
            else pd.Series([0] * len(allocation), dtype="Int64"),
            "payment_document_type_key": map_key(allocation.get("c_doctype_id"), lk["document_type"]),
            "allocated_amount": as_num(allocation.get("amount")),
            "discount_amount": as_num(allocation.get("discountamt")),
            "writeoff_amount": as_num(allocation.get("writeoffamt")),
            "overunder_amount": as_num(allocation.get("overunderamt")),
            "payment_amount": as_num(allocation.get("payamt")),
            "allocation_count": 1,
        }
    ).drop_duplicates("c_allocationline_id")
    fact_payment_allocation.insert(0, "payment_allocation_key", range(1, len(fact_payment_allocation) + 1))

    storage = src["rv_storage"].copy()
    storage = storage.merge(prod_cat, how="left", on="m_product_id")
    snapshot_date = None
    manifest_path = project_path("Youssef_Extractions/vente_clean_import/manifest.json")
    if manifest_path.exists():
        try:
            exported_at = json.loads(manifest_path.read_text(encoding="utf-8")).get("exported_at")
            snapshot_date = pd.to_datetime(exported_at, errors="coerce")
        except Exception:
            snapshot_date = None
    if snapshot_date is None or pd.isna(snapshot_date):
        snapshot_date = pd.Timestamp(date.today())
    fact_stock_snapshot = pd.DataFrame(
        {
            "snapshot_date_key": int(snapshot_date.strftime("%Y%m%d")),
            "m_product_id": storage.get("m_product_id"),
            "m_attribute_set_instance_id": storage.get("m_attributesetinstance_id"),
            "product_key": map_key(storage.get("m_product_id"), lk["product"]),
            "product_category_key": storage.get("product_category_key").fillna(0).astype("Int64"),
            "supplier_key": storage.get("supplier_key").fillna(0).astype("Int64"),
            "warehouse_key": map_key(storage.get("m_warehouse_id"), lk["warehouse"]),
            "quantity_on_hand": as_num(storage.get("qtyonhand")),
            "quantity_reserved": as_num(storage.get("qtyreserved")),
            "quantity_available": as_num(storage.get("qtyavailable")),
            "quantity_ordered": as_num(storage.get("qtyordered")),
            "stock_row_count": 1,
        }
    )
    fact_stock_snapshot.insert(0, "stock_snapshot_key", range(1, len(fact_stock_snapshot) + 1))

    return {
        "fact_sales_order": fact_sales_order,
        "fact_sales_order_line": fact_sales_order_line,
        "fact_invoice": fact_invoice,
        "fact_invoice_line": fact_invoice_line,
        "fact_delivery": fact_delivery,
        "fact_delivery_line": fact_delivery_line,
        "fact_payment_allocation": fact_payment_allocation,
        "fact_stock_snapshot": fact_stock_snapshot,
    }


def build_marts(facts: dict[str, pd.DataFrame], dimensions: dict[str, pd.DataFrame]) -> dict[str, pd.DataFrame]:
    date_dim = dimensions["dim_date"][["date_key", "full_date", "month_number", "month_name", "year_number"]]
    commercial = dimensions["dim_commercial"][["commercial_key", "commercial_name"]]
    customer = dimensions["dim_customer"][["customer_key", "customer_name"]]
    product = dimensions["dim_product"][["product_key", "product_name"]]
    supplier = dimensions["dim_supplier"][["supplier_key", "supplier_name"]]
    geography = dimensions["dim_geography"][["geography_key", "city_name", "region_name"]]

    invoice = facts["fact_invoice"].merge(date_dim, how="left", left_on="invoice_date_key", right_on="date_key")
    mart_sales_overview = (
        invoice.groupby(["year_number", "month_number", "month_name"], dropna=False)
        .agg(invoice_count=("invoice_count", "sum"), invoiced_ca=("grand_total_amount", "sum"), paid_invoices=("paid_invoice_count", "sum"), unpaid_invoices=("unpaid_invoice_count", "sum"))
        .reset_index()
        .sort_values(["year_number", "month_number"])
    )

    mart_sales_by_commercial = (
        invoice.merge(commercial, how="left", on="commercial_key")
        .groupby(["commercial_key", "commercial_name"], dropna=False)
        .agg(invoice_count=("invoice_count", "sum"), invoiced_ca=("grand_total_amount", "sum"))
        .reset_index()
        .sort_values("invoiced_ca", ascending=False)
    )

    mart_sales_by_customer = (
        invoice.merge(customer, how="left", on="customer_key")
        .groupby(["customer_key", "customer_name"], dropna=False)
        .agg(invoice_count=("invoice_count", "sum"), invoiced_ca=("grand_total_amount", "sum"))
        .reset_index()
        .sort_values("invoiced_ca", ascending=False)
    )

    inv_line = facts["fact_invoice_line"].merge(product, how="left", on="product_key").merge(supplier, how="left", on="supplier_key")
    mart_sales_by_product = (
        inv_line.groupby(["product_key", "product_name"], dropna=False)
        .agg(quantity_invoiced=("quantity_invoiced", "sum"), invoiced_ca=("line_net_amount", "sum"), invoice_lines=("invoice_line_count", "sum"))
        .reset_index()
        .sort_values("invoiced_ca", ascending=False)
    )
    mart_sales_by_supplier = (
        inv_line.groupby(["supplier_key", "supplier_name"], dropna=False)
        .agg(quantity_invoiced=("quantity_invoiced", "sum"), invoiced_ca=("line_net_amount", "sum"), invoice_lines=("invoice_line_count", "sum"))
        .reset_index()
        .sort_values("invoiced_ca", ascending=False)
    )
    mart_sales_by_region = (
        invoice.merge(geography, how="left", on="geography_key")
        .groupby(["region_name", "city_name"], dropna=False)
        .agg(invoice_count=("invoice_count", "sum"), invoiced_ca=("grand_total_amount", "sum"))
        .reset_index()
        .sort_values("invoiced_ca", ascending=False)
    )
    return {
        "mart_sales_overview": mart_sales_overview,
        "mart_sales_by_commercial": mart_sales_by_commercial,
        "mart_sales_by_customer": mart_sales_by_customer,
        "mart_sales_by_product": mart_sales_by_product,
        "mart_sales_by_supplier": mart_sales_by_supplier,
        "mart_sales_by_region": mart_sales_by_region,
    }


def write_outputs(facts: dict[str, pd.DataFrame], marts: dict[str, pd.DataFrame]) -> dict[str, dict[str, int]]:
    cfg = load_config()
    fact_dir = project_path(cfg["outputs"]["facts"])
    mart_dir = project_path(cfg["outputs"]["marts"])
    fact_dir.mkdir(parents=True, exist_ok=True)
    mart_dir.mkdir(parents=True, exist_ok=True)
    fact_counts = {}
    mart_counts = {}
    for name, df in facts.items():
        df.to_csv(fact_dir / f"{name}.csv", index=False, encoding="utf-8-sig")
        fact_counts[name] = len(df)
    for name, df in marts.items():
        df.to_csv(mart_dir / f"{name}.csv", index=False, encoding="utf-8-sig")
        mart_counts[name] = len(df)
    return {"facts": fact_counts, "marts": mart_counts}


def main() -> None:
    sources = load_sources()
    dimensions = build_dimensions(sources)
    facts = build_facts(sources, dimensions)
    marts = build_marts(facts, dimensions)
    counts = write_outputs(facts, marts)
    print(json.dumps(counts, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

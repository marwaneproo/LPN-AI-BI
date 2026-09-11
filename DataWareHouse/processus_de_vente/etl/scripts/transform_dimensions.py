from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

import pandas as pd

from extract_sources import load_config, load_sources, project_path


UNKNOWN_TEXT = "Unknown / Non renseigne"


def as_id(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce").astype("Int64").astype("string")


def as_num(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce")


def as_bool(series: pd.Series) -> pd.Series:
    return series.astype("string").str.upper().map({"Y": True, "N": False, "TRUE": True, "FALSE": False})


def date_key(value: Any) -> int:
    parsed = pd.to_datetime(value, errors="coerce")
    if pd.isna(parsed):
        return 0
    return int(parsed.strftime("%Y%m%d"))


def add_surrogate(df: pd.DataFrame, key_name: str) -> pd.DataFrame:
    df = df.reset_index(drop=True).copy()
    df.insert(0, key_name, range(1, len(df) + 1))
    return df


def unknown_row(columns: list[str], key_name: str) -> pd.DataFrame:
    row: dict[str, Any] = {column: pd.NA for column in columns}
    row[key_name] = 0
    for column in columns:
        if column.endswith("_name") or column in {"description", "customer_code", "supplier_code", "product_code"}:
            row[column] = UNKNOWN_TEXT
    return pd.DataFrame([row], columns=columns)


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


def build_dim_date(sources: dict[str, pd.DataFrame]) -> pd.DataFrame:
    date_columns = [
        (sources["c_order"], "dateordered"),
        (sources["c_invoice"], "dateinvoiced"),
        (sources["m_inout"], "movementdate"),
        (sources["c_allocationhdr"], "datetrx"),
        (sources["c_payment"], "datetrx"),
    ]
    all_dates: list[pd.Timestamp] = []
    for df, column in date_columns:
        if column in df.columns:
            all_dates.extend(pd.to_datetime(df[column], errors="coerce").dropna().dt.date.tolist())
    manifest_path = project_path("Youssef_Extractions/vente_clean_import/manifest.json")
    if manifest_path.exists():
        with manifest_path.open("r", encoding="utf-8") as handle:
            exported_at = json.load(handle).get("exported_at")
        exported_date = pd.to_datetime(exported_at, errors="coerce")
        if pd.notna(exported_date):
            all_dates.append(exported_date.date())
    if not all_dates:
        start = end = date.today()
    else:
        start = min(all_dates)
        end = max(all_dates)
    dates = pd.date_range(start=start, end=end, freq="D")
    dim = pd.DataFrame({"full_date": dates.date})
    dim.insert(0, "date_key", dim["full_date"].map(lambda d: int(d.strftime("%Y%m%d"))))
    dim["day_of_month"] = dates.day
    dim["month_number"] = dates.month
    dim["month_name"] = dates.strftime("%B")
    dim["quarter_number"] = dates.quarter
    dim["year_number"] = dates.year
    dim["week_of_year"] = dates.isocalendar().week.astype(int)
    dim["is_month_end"] = dates.is_month_end
    return pd.concat(
        [
            pd.DataFrame(
                [
                    {
                        "date_key": 0,
                        "full_date": pd.NaT,
                        "day_of_month": 0,
                        "month_number": 0,
                        "month_name": UNKNOWN_TEXT,
                        "quarter_number": 0,
                        "year_number": 0,
                        "week_of_year": 0,
                        "is_month_end": False,
                    }
                ]
            ),
            dim,
        ],
        ignore_index=True,
    )


def build_dimensions(sources: dict[str, pd.DataFrame]) -> dict[str, pd.DataFrame]:
    dimensions: dict[str, pd.DataFrame] = {}
    dimensions["dim_date"] = build_dim_date(sources)

    bp_group = sources["c_bp_group"].copy()
    customer = sources["c_bpartner"].copy()
    customer["c_bpartner_id"] = as_id(customer["c_bpartner_id"])
    bp_group["c_bp_group_id"] = as_id(bp_group["c_bp_group_id"])
    customer = customer.merge(
        bp_group[["c_bp_group_id", "name"]].rename(columns={"name": "customer_group_name"}),
        how="left",
        on="c_bp_group_id",
    )
    dim_customer = pd.DataFrame(
        {
            "c_bpartner_id": customer["c_bpartner_id"],
            "customer_code": customer.get("value"),
            "customer_name": customer.get("name"),
            "customer_group_id": customer.get("c_bp_group_id"),
            "customer_group_name": customer.get("customer_group_name"),
            "is_customer": as_bool(customer.get("iscustomer", pd.Series(dtype="string"))),
            "is_vendor": as_bool(customer.get("isvendor", pd.Series(dtype="string"))),
            "salesrep_id": customer.get("salesrep_id"),
            "credit_limit": as_num(customer.get("so_creditlimit", pd.Series(dtype="string"))),
            "is_active": as_bool(customer.get("isactive", pd.Series(dtype="string"))),
            "effective_from": pd.NaT,
            "effective_to": pd.NaT,
            "is_current": True,
        }
    ).drop_duplicates("c_bpartner_id")
    dim_customer = add_surrogate(dim_customer, "customer_key")
    dimensions["dim_customer"] = pd.concat([unknown_row(list(dim_customer.columns), "customer_key"), dim_customer], ignore_index=True)

    ad_user = sources["ad_user"].copy()
    ad_user["ad_user_id"] = as_id(ad_user["ad_user_id"])
    dim_commercial = pd.DataFrame(
        {
            "ad_user_id": ad_user["ad_user_id"],
            "commercial_name": ad_user.get("name"),
            "email": ad_user.get("email"),
            "description": ad_user.get("description"),
            "c_bpartner_id": ad_user.get("c_bpartner_id"),
            "is_active": as_bool(ad_user.get("isactive", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("ad_user_id")
    dim_commercial = add_surrogate(dim_commercial, "commercial_key")
    dimensions["dim_commercial"] = pd.concat([unknown_row(list(dim_commercial.columns), "commercial_key"), dim_commercial], ignore_index=True)

    category = sources["m_product_category"].copy()
    category["m_product_category_id"] = as_id(category["m_product_category_id"])
    dim_category = pd.DataFrame(
        {
            "m_product_category_id": category["m_product_category_id"],
            "category_code": category.get("value"),
            "category_name": category.get("name"),
            "description": category.get("description"),
            "planned_margin": as_num(category.get("plannedmargin", pd.Series(dtype="string"))),
            "is_default": as_bool(category.get("isdefault", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("m_product_category_id")
    dim_category = add_surrogate(dim_category, "product_category_key")
    dimensions["dim_product_category"] = pd.concat([unknown_row(list(dim_category.columns), "product_category_key"), dim_category], ignore_index=True)

    vendor = sources["c_bpartner_vendor"].copy()
    vendor["c_bpartner_id"] = as_id(vendor["c_bpartner_id"])
    dim_supplier = pd.DataFrame(
        {
            "c_bpartner_id": vendor["c_bpartner_id"],
            "supplier_code": vendor.get("value"),
            "supplier_name": vendor.get("name"),
            "supplier_group_id": vendor.get("c_bp_group_id"),
            "supplier_group_name": pd.NA,
            "is_active": as_bool(vendor.get("isactive", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("c_bpartner_id")
    dim_supplier = add_surrogate(dim_supplier, "supplier_key")
    dimensions["dim_supplier"] = pd.concat([unknown_row(list(dim_supplier.columns), "supplier_key"), dim_supplier], ignore_index=True)

    supplier_map = primary_supplier(sources["m_product_po"])
    supplier_key_map = dimensions["dim_supplier"][["supplier_key", "c_bpartner_id"]].copy()
    product = sources["m_product"].copy()
    product["m_product_id"] = as_id(product["m_product_id"])
    product["m_product_category_id"] = as_id(product["m_product_category_id"])
    supplier_map["m_product_id"] = as_id(supplier_map["m_product_id"])
    supplier_map["c_bpartner_id"] = as_id(supplier_map["c_bpartner_id"])
    product = product.merge(supplier_map, how="left", on="m_product_id")
    product = product.merge(supplier_key_map, how="left", on="c_bpartner_id")
    product = product.merge(
        dimensions["dim_product_category"][["product_category_key", "m_product_category_id"]],
        how="left",
        on="m_product_category_id",
    )
    type_lookup = sources["m_product_type"].rename(columns={"name": "product_type_name"})
    theme_lookup = sources["m_product_theme"].rename(columns={"name": "theme_name"})
    collection_lookup = sources["m_product_collection"].rename(columns={"name": "collection_name"})
    for lookup, key in [
        (type_lookup, "m_product_type_id"),
        (theme_lookup, "m_product_theme_id"),
        (collection_lookup, "m_product_collection_id"),
    ]:
        if key in product.columns and key in lookup.columns:
            product[key] = as_id(product[key])
            lookup[key] = as_id(lookup[key])
            keep = [key] + [col for col in lookup.columns if col.endswith("_name")]
            product = product.merge(lookup[keep].drop_duplicates(key), how="left", on=key)
    dim_product = pd.DataFrame(
        {
            "m_product_id": product["m_product_id"],
            "product_code": product.get("value"),
            "product_name": product.get("name"),
            "description": product.get("description"),
            "product_type_code": product.get("producttype"),
            "product_type_name": product.get("product_type_name"),
            "theme_name": product.get("theme_name"),
            "collection_name": product.get("collection_name"),
            "is_sold": as_bool(product.get("issold", pd.Series(dtype="string"))),
            "is_purchased": as_bool(product.get("ispurchased", pd.Series(dtype="string"))),
            "is_stocked": as_bool(product.get("isstocked", pd.Series(dtype="string"))),
            "product_category_key": product.get("product_category_key").fillna(0).astype("Int64"),
            "supplier_key": product.get("supplier_key").fillna(0).astype("Int64"),
            "effective_from": pd.NaT,
            "effective_to": pd.NaT,
            "is_current": True,
        }
    ).drop_duplicates("m_product_id")
    dim_product = add_surrogate(dim_product, "product_key")
    dimensions["dim_product"] = pd.concat([unknown_row(list(dim_product.columns), "product_key"), dim_product], ignore_index=True)

    sales_region = sources["c_salesregion"].copy()
    sales_region["c_salesregion_id"] = as_id(sales_region["c_salesregion_id"])
    dim_sales_region = pd.DataFrame(
        {
            "c_salesregion_id": sales_region["c_salesregion_id"],
            "sales_region_code": sales_region.get("value"),
            "sales_region_name": sales_region.get("name"),
            "description": sales_region.get("description"),
            "is_summary": as_bool(sales_region.get("issummary", pd.Series(dtype="string"))),
            "is_default": as_bool(sales_region.get("isdefault", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("c_salesregion_id")
    dim_sales_region = add_surrogate(dim_sales_region, "sales_region_key")
    dimensions["dim_sales_region"] = pd.concat([unknown_row(list(dim_sales_region.columns), "sales_region_key"), dim_sales_region], ignore_index=True)

    location = sources["c_location"].copy()
    bpl = sources["c_bpartner_location"].copy()
    region = sources["c_region"].rename(columns={"name": "region_lookup_name"})
    city = sources["c_city"].rename(columns={"name": "city_lookup_name"})
    for df, cols in [
        (bpl, ["c_bpartner_location_id", "c_location_id", "c_salesregion_id"]),
        (location, ["c_location_id", "c_region_id", "c_city_id"]),
        (region, ["c_region_id"]),
        (city, ["c_city_id"]),
    ]:
        for col in cols:
            if col in df.columns:
                df[col] = as_id(df[col])
    geo = bpl.merge(location, how="left", on="c_location_id", suffixes=("_bpl", "_loc"))
    geo = geo.merge(region[["c_region_id", "region_lookup_name"]].drop_duplicates("c_region_id"), how="left", on="c_region_id")
    geo = geo.merge(city[["c_city_id", "city_lookup_name"]].drop_duplicates("c_city_id"), how="left", on="c_city_id")
    geo = geo.merge(
        dimensions["dim_sales_region"][["sales_region_key", "c_salesregion_id"]],
        how="left",
        on="c_salesregion_id",
    )
    dim_geo = pd.DataFrame(
        {
            "c_bpartner_location_id": geo.get("c_bpartner_location_id"),
            "c_location_id": geo.get("c_location_id"),
            "location_name": geo.get("name"),
            "is_bill_to": as_bool(geo.get("isbillto", pd.Series(dtype="string"))),
            "is_ship_to": as_bool(geo.get("isshipto", pd.Series(dtype="string"))),
            "is_pay_from": as_bool(geo.get("ispayfrom", pd.Series(dtype="string"))),
            "is_remit_to": as_bool(geo.get("isremitto", pd.Series(dtype="string"))),
            "address1": geo.get("address1"),
            "address2": geo.get("address2"),
            "city_name": geo.get("cityname").fillna(geo.get("city_lookup_name")),
            "region_name": geo.get("regionname").fillna(geo.get("region_lookup_name")),
            "country_id": geo.get("c_country_id"),
            "c_city_id": geo.get("c_city_id"),
            "c_region_id": geo.get("c_region_id"),
            "sales_region_key": geo.get("sales_region_key").fillna(0).astype("Int64"),
            "sector_detail": geo.get("sectordetail"),
        }
    ).drop_duplicates(["c_bpartner_location_id", "c_location_id"])
    dim_geo = add_surrogate(dim_geo, "geography_key")
    dimensions["dim_geography"] = pd.concat([unknown_row(list(dim_geo.columns), "geography_key"), dim_geo], ignore_index=True)

    payment = sources["c_paymentterm"].copy()
    payment["c_paymentterm_id"] = as_id(payment["c_paymentterm_id"])
    dim_payment = pd.DataFrame(
        {
            "c_paymentterm_id": payment["c_paymentterm_id"],
            "payment_term_name": payment.get("name"),
            "description": payment.get("description"),
            "net_days": as_num(payment.get("netdays", pd.Series(dtype="string"))),
            "grace_days": as_num(payment.get("gracedays", pd.Series(dtype="string"))),
            "after_delivery": as_bool(payment.get("afterdelivery", pd.Series(dtype="string"))),
            "is_due_fixed": as_bool(payment.get("isduefixed", pd.Series(dtype="string"))),
            "is_default": as_bool(payment.get("isdefault", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("c_paymentterm_id")
    dim_payment = add_surrogate(dim_payment, "payment_term_key")
    dimensions["dim_payment_term"] = pd.concat([unknown_row(list(dim_payment.columns), "payment_term_key"), dim_payment], ignore_index=True)

    price = sources["m_pricelist"].copy()
    price["m_pricelist_id"] = as_id(price["m_pricelist_id"])
    dim_price = pd.DataFrame(
        {
            "m_pricelist_id": price["m_pricelist_id"],
            "price_list_name": price.get("name"),
            "description": price.get("description"),
            "base_pricelist_id": price.get("basepricelist_id"),
            "is_tax_included": as_bool(price.get("istaxincluded", pd.Series(dtype="string"))),
            "is_so_price_list": as_bool(price.get("issopricelist", pd.Series(dtype="string"))),
            "currency_id": price.get("c_currency_id"),
        }
    ).drop_duplicates("m_pricelist_id")
    dim_price = add_surrogate(dim_price, "price_list_key")
    dimensions["dim_price_list"] = pd.concat([unknown_row(list(dim_price.columns), "price_list_key"), dim_price], ignore_index=True)

    doc = sources["c_doctype"].copy()
    doc["c_doctype_id"] = as_id(doc["c_doctype_id"])
    dim_doc = pd.DataFrame(
        {
            "c_doctype_id": doc["c_doctype_id"],
            "document_type_name": doc.get("name"),
            "print_name": doc.get("printname"),
            "description": doc.get("description"),
            "doc_base_type": doc.get("docbasetype"),
            "doc_subtype_so": doc.get("docsubtypeso"),
            "is_sales_transaction": as_bool(doc.get("issotrx", pd.Series(dtype="string"))),
        }
    ).drop_duplicates("c_doctype_id")
    dim_doc = add_surrogate(dim_doc, "document_type_key")
    dimensions["dim_document_type"] = pd.concat([unknown_row(list(dim_doc.columns), "document_type_key"), dim_doc], ignore_index=True)

    wh = sources["m_warehouse"].copy()
    loc = sources["m_locator"].copy()
    for df, cols in [(wh, ["m_warehouse_id"]), (loc, ["m_warehouse_id", "m_locator_id"])]:
        for col in cols:
            if col in df.columns:
                df[col] = as_id(df[col])
    warehouse = wh.merge(loc, how="left", on="m_warehouse_id", suffixes=("_warehouse", "_locator"))
    dim_warehouse = pd.DataFrame(
        {
            "m_warehouse_id": warehouse.get("m_warehouse_id"),
            "warehouse_code": warehouse.get("value_warehouse"),
            "warehouse_name": warehouse.get("name_warehouse"),
            "m_locator_id": warehouse.get("m_locator_id"),
            "locator_value": warehouse.get("value_locator"),
            "is_default_locator": as_bool(warehouse.get("isdefault", pd.Series(dtype="string"))),
        }
    ).drop_duplicates(["m_warehouse_id", "m_locator_id"])
    dim_warehouse = add_surrogate(dim_warehouse, "warehouse_key")
    dimensions["dim_warehouse"] = pd.concat([unknown_row(list(dim_warehouse.columns), "warehouse_key"), dim_warehouse], ignore_index=True)

    return dimensions


def write_dimensions(dimensions: dict[str, pd.DataFrame]) -> dict[str, int]:
    cfg = load_config()
    output_dir = project_path(cfg["outputs"]["dimensions"])
    output_dir.mkdir(parents=True, exist_ok=True)
    row_counts = {}
    for name, df in dimensions.items():
        df.to_csv(output_dir / f"{name}.csv", index=False, encoding="utf-8-sig")
        row_counts[name] = len(df)
    return row_counts


def main() -> None:
    sources = load_sources()
    dimensions = build_dimensions(sources)
    row_counts = write_dimensions(dimensions)
    print(json.dumps({"dimensions": row_counts}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

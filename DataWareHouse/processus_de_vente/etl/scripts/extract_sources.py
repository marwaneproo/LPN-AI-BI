from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[4]
ETL_ROOT = REPO_ROOT / "DataWareHouse" / "processus_de_vente" / "etl"
CONFIG_PATH = ETL_ROOT / "config" / "etl_config.json"


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def project_path(relative_path: str) -> Path:
    return REPO_ROOT / relative_path


def standardize_column_name(name: str) -> str:
    value = str(name).strip().lower()
    value = re.sub(r"[^a-z0-9]+", "_", value)
    value = re.sub(r"_+", "_", value).strip("_")
    return value


def standardize_frame(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df.columns = [standardize_column_name(col) for col in df.columns]
    for col in df.select_dtypes(include=["object", "string"]).columns:
        df[col] = df[col].map(lambda value: value.strip() if isinstance(value, str) else value)
        df[col] = df[col].replace({"": pd.NA})
    return df


def read_csv(path: Path) -> pd.DataFrame:
    return standardize_frame(pd.read_csv(path, dtype="string", low_memory=False))


def read_xlsx(path: Path) -> pd.DataFrame:
    return standardize_frame(pd.read_excel(path, dtype=object))


def load_sources() -> dict[str, pd.DataFrame]:
    cfg = load_config()
    clean_dir = project_path(cfg["source_paths"]["clean_csv_dir"])
    enrichment_dir = project_path(cfg["source_paths"]["enrichment_csv_dir"])
    fourth_dir = project_path(cfg["source_paths"]["fourth_extraction_dir"])

    source_files = {
        "c_order": clean_dir / "C_ORDER.csv",
        "c_orderline": clean_dir / "C_ORDERLINE.csv",
        "c_invoice": clean_dir / "C_INVOICE.csv",
        "c_invoiceline": clean_dir / "C_INVOICELINE.csv",
        "c_bpartner": clean_dir / "C_BPARTNER.csv",
        "m_product": clean_dir / "M_PRODUCT.csv",
        "m_product_category": clean_dir / "M_PRODUCT_CATEGORY.csv",
        "c_doctype": clean_dir / "C_DOCTYPE.csv",
        "c_tax": clean_dir / "C_TAX.csv",
        "ad_org": clean_dir / "AD_ORG.csv",
        "c_allocationline": clean_dir / "C_ALLOCATIONLINE.csv",
        "c_allocationhdr": clean_dir / "C_ALLOCATIONHDR.csv",
        "c_payment": clean_dir / "C_PAYMENT.csv",
        "m_inout": clean_dir / "M_INOUT.csv",
        "m_inoutline": clean_dir / "M_INOUTLINE.csv",
        "m_warehouse": clean_dir / "M_WAREHOUSE.csv",
        "m_locator": clean_dir / "M_LOCATOR.csv",
        "rv_storage": clean_dir / "RV_STORAGE.csv",
        "ad_user": enrichment_dir / "AD_USER.csv",
        "m_product_po": enrichment_dir / "M_PRODUCT_PO.csv",
        "c_bpartner_vendor": enrichment_dir / "C_BPARTNER_VENDOR.csv",
        "m_product_type": enrichment_dir / "M_PRODUCT_TYPE.csv",
        "m_product_theme": enrichment_dir / "M_PRODUCT_THEME.csv",
        "m_product_collection": enrichment_dir / "M_PRODUCT_COLLECTION.csv",
        "products_without_supplier": enrichment_dir / "PRODUCTS_WITHOUT_SUPPLIER.csv",
        "c_bp_group": fourth_dir / "27_C_BP_GROUP.xlsx",
        "c_bpartner_location": fourth_dir / "28_C_BPARTNER_LOCATION.xlsx",
        "c_location": fourth_dir / "29_C_LOCATION.xlsx",
        "c_paymentterm": fourth_dir / "30_C_PAYMENTTERM.xlsx",
        "c_salesregion": fourth_dir / "32_C_SALESREGION.xlsx",
        "m_pricelist": fourth_dir / "33_M_PRICELIST.xlsx",
        "c_region": fourth_dir / "40_C_REGION.xlsx",
        "c_city": fourth_dir / "41_C_CITY.xlsx",
    }

    sources: dict[str, pd.DataFrame] = {}
    for name, path in source_files.items():
        if path.suffix.lower() == ".csv":
            sources[name] = read_csv(path)
        else:
            sources[name] = read_xlsx(path)
    return sources


def write_staging(sources: dict[str, pd.DataFrame]) -> dict[str, int]:
    cfg = load_config()
    output_dir = project_path(cfg["outputs"]["staging"])
    output_dir.mkdir(parents=True, exist_ok=True)
    row_counts: dict[str, int] = {}
    for name, df in sources.items():
        output_path = output_dir / f"stg_{name}.csv"
        df.to_csv(output_path, index=False, encoding="utf-8-sig")
        row_counts[f"stg_{name}"] = len(df)
    return row_counts


def main() -> None:
    sources = load_sources()
    row_counts = write_staging(sources)
    print(json.dumps({"staging_tables": row_counts}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

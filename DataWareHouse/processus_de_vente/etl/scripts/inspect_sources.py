from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd


ROOT = Path(__file__).resolve().parents[4]
CLEAN = ROOT / "Youssef_Extractions" / "vente_clean_import" / "csv"
ENRICH = ROOT / "Youssef_Extractions" / "vente_bi_enrichment_import" / "csv"
FOURTH = ROOT / "Youssef_Extractions" / "4th_Extraction"


def summarize_csv(path: Path) -> dict[str, Any]:
    df = pd.read_csv(path, nrows=3, dtype="string")
    return {"rows_sampled": len(df), "columns": list(df.columns), "sample": df.fillna("").to_dict(orient="records")}


def summarize_xlsx(path: Path) -> dict[str, Any]:
    df = pd.read_excel(path, nrows=3, dtype=object)
    return {"rows_sampled": len(df), "columns": [str(c) for c in df.columns], "sample": df.where(pd.notna(df), "").to_dict(orient="records")}


def main() -> None:
    selected = {
        "clean": [
            "C_ORDER.csv",
            "C_ORDERLINE.csv",
            "C_INVOICE.csv",
            "C_INVOICELINE.csv",
            "C_BPARTNER.csv",
            "M_PRODUCT.csv",
            "M_PRODUCT_CATEGORY.csv",
            "C_DOCTYPE.csv",
            "M_INOUT.csv",
            "M_INOUTLINE.csv",
            "C_ALLOCATIONLINE.csv",
            "C_ALLOCATIONHDR.csv",
            "C_PAYMENT.csv",
            "RV_STORAGE.csv",
        ],
        "enrich": [
            "AD_USER.csv",
            "M_PRODUCT_PO.csv",
            "C_BPARTNER_VENDOR.csv",
            "M_PRODUCT_TYPE.csv",
            "M_PRODUCT_THEME.csv",
            "M_PRODUCT_COLLECTION.csv",
        ],
        "fourth": [
            "27_C_BP_GROUP.xlsx",
            "28_C_BPARTNER_LOCATION.xlsx",
            "29_C_LOCATION.xlsx",
            "30_C_PAYMENTTERM.xlsx",
            "32_C_SALESREGION.xlsx",
            "33_M_PRICELIST.xlsx",
            "40_C_REGION.xlsx",
            "41_C_CITY.xlsx",
        ],
    }
    payload: dict[str, Any] = {}
    for name in selected["clean"]:
        payload[f"clean/{name}"] = summarize_csv(CLEAN / name)
    for name in selected["enrich"]:
        payload[f"enrich/{name}"] = summarize_csv(ENRICH / name)
    for name in selected["fourth"]:
        payload[f"fourth/{name}"] = summarize_xlsx(FOURTH / name)
    print(json.dumps(payload, ensure_ascii=False, indent=2, default=str))


if __name__ == "__main__":
    main()

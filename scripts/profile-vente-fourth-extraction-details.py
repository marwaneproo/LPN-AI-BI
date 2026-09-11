from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "4th_Extraction"
OUTPUT_DIR = REPO_ROOT / "docs" / "warehouse"
REPORT_MD = OUTPUT_DIR / "VENTE_FOURTH_EXTRACTION_DETAIL_PROFILE.md"
REPORT_JSON = OUTPUT_DIR / "vente_fourth_extraction_detail_profile.json"


def read_excel(name: str) -> pd.DataFrame:
    return pd.read_excel(SOURCE_DIR / name, dtype=object)


def safe_numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce")


def safe_datetime(series: pd.Series) -> pd.Series:
    return pd.to_datetime(series, errors="coerce")


def dataframe_fingerprint(df: pd.DataFrame) -> str:
    csv_bytes = df.head(5000).to_csv(index=False).encode("utf-8", errors="ignore")
    return hashlib.sha256(csv_bytes).hexdigest()


def summarize_denormalized(df: pd.DataFrame) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "rows": int(len(df)),
        "columns": int(len(df.columns)),
        "headers": [str(col) for col in df.columns],
        "fingerprint_first_5000": dataframe_fingerprint(df),
    }
    for col in ("C_ORDER_ID", "C_ORDERLINE_ID", "C_INVOICE_ID", "C_INVOICELINE_ID", "M_PRODUCT_ID", "C_BPARTNER_ID", "SALESREP_ID"):
        if col in df.columns:
            summary[f"distinct_{col.lower()}"] = int(df[col].dropna().nunique())
            summary[f"non_null_{col.lower()}"] = int(df[col].notna().sum())
    for col in ("DATE_COMMANDE", "DATEORDERED", "DATEINVOICED", "DATE_FACTURE"):
        if col in df.columns:
            dates = safe_datetime(df[col])
            summary[f"{col.lower()}_min"] = str(dates.min())
            summary[f"{col.lower()}_max"] = str(dates.max())
    for col in df.columns:
        upper = str(col).upper()
        if any(token in upper for token in ("TOTAL", "AMOUNT", "MONTANT", "CA", "LINENETAMT", "GRANDTOTAL")):
            values = safe_numeric(df[col])
            if values.notna().sum() > 0:
                summary[f"sum_{col}"] = round(float(values.fillna(0).sum()), 2)
                summary[f"non_null_{col}"] = int(values.notna().sum())
    return summary


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    profile: dict[str, Any] = {"generated_at": datetime.now(timezone.utc).isoformat(), "files": {}}

    for name in ("1.xlsx", "2.xlsx", "3.xlsx", "4.xlsx", "27_C_BP_GROUP.xlsx", "28_C_BPARTNER_LOCATION.xlsx", "29_C_LOCATION.xlsx", "30_C_PAYMENTTERM.xlsx", "31_SALES_TABLE_DISCOVERY.xlsx"):
        df = read_excel(name)
        if name in {"3.xlsx", "4.xlsx"}:
            profile["files"][name] = summarize_denormalized(df)
        else:
            profile["files"][name] = {
                "rows": int(len(df)),
                "columns": int(len(df.columns)),
                "headers": [str(col) for col in df.columns],
                "sample_rows": df.head(8).where(pd.notna(df), None).to_dict(orient="records"),
                "fingerprint_first_5000": dataframe_fingerprint(df),
            }

    if "3.xlsx" in profile["files"] and "4.xlsx" in profile["files"]:
        profile["three_four_same_first_5000"] = (
            profile["files"]["3.xlsx"]["fingerprint_first_5000"] == profile["files"]["4.xlsx"]["fingerprint_first_5000"]
        )
        profile["three_four_same_shape"] = (
            profile["files"]["3.xlsx"]["rows"] == profile["files"]["4.xlsx"]["rows"]
            and profile["files"]["3.xlsx"]["columns"] == profile["files"]["4.xlsx"]["columns"]
        )

    REPORT_JSON.write_text(json.dumps(profile, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    REPORT_MD.write_text(render_md(profile), encoding="utf-8")
    print(f"Wrote {REPORT_MD}")
    print(f"Wrote {REPORT_JSON}")


def render_md(profile: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# Vente Fourth Extraction Detail Profile")
    lines.append("")
    lines.append(f"Generated at: `{profile['generated_at']}`")
    lines.append("")
    lines.append("## Duplicate Check")
    lines.append("")
    lines.append(f"- `3.xlsx` and `4.xlsx` same shape: `{profile.get('three_four_same_shape')}`")
    lines.append(f"- `3.xlsx` and `4.xlsx` same first 5000-row fingerprint: `{profile.get('three_four_same_first_5000')}`")
    lines.append("")
    for name, meta in profile["files"].items():
        lines.append(f"## `{name}`")
        lines.append("")
        lines.append(f"- Rows: `{meta['rows']}`")
        lines.append(f"- Columns: `{meta['columns']}`")
        lines.append(f"- Headers: `{', '.join(meta['headers'])}`")
        for key, value in meta.items():
            if key in {"rows", "columns", "headers", "sample_rows", "fingerprint_first_5000"}:
                continue
            lines.append(f"- `{key}`: `{value}`")
        if meta.get("sample_rows"):
            first = meta["sample_rows"][0]
            lines.append(f"- First sample: `{json.dumps(first, ensure_ascii=False, default=str)}`")
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    main()

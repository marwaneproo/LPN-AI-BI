from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "Youssef_Extractions" / "4th_Extraction"
FILES = [
    "32_C_SALESREGION.xlsx",
    "33_M_PRICELIST.xlsx",
    "34_C_COMMISSION.xlsx",
]


def clean(value: Any) -> Any:
    if pd.isna(value):
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


def main() -> None:
    summary: dict[str, Any] = {}
    for name in FILES:
        path = SOURCE_DIR / name
        df = pd.read_excel(path, dtype=object)
        summary[name] = {
            "rows": int(len(df)),
            "columns": int(len(df.columns)),
            "headers": [str(col) for col in df.columns],
            "samples": [
                {str(k): clean(v) for k, v in row.items()}
                for row in df.head(5).to_dict(orient="records")
            ],
        }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

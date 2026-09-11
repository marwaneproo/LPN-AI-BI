from __future__ import annotations

import json
from datetime import datetime, timezone

import pandas as pd

from extract_sources import load_sources, project_path


OUTPUT_ROOT = project_path("DataWareHouse/processus_de_vente/etl/output")
VALIDATION_ROOT = project_path("DataWareHouse/processus_de_vente/etl/validation")


def read_output(folder: str, table: str) -> pd.DataFrame:
    path = OUTPUT_ROOT / folder / f"{table}.csv"
    if not path.exists():
        raise FileNotFoundError(f"Missing ETL output: {path}")
    return pd.read_csv(path, low_memory=False)


def number_sum(frame: pd.DataFrame, column: str) -> float:
    if column not in frame.columns:
        return 0.0
    return float(pd.to_numeric(frame[column], errors="coerce").fillna(0).sum())


def difference_check(name: str, source_value: float, output_value: float, tolerance: float = 0.05) -> dict:
    diff = output_value - source_value
    return {
        "name": name,
        "source_value": round(source_value, 4),
        "output_value": round(output_value, 4),
        "difference": round(diff, 4),
        "status": "PASS" if abs(diff) <= tolerance else "WARN",
    }


def zero_key_profile(fact_name: str, frame: pd.DataFrame) -> list[dict]:
    profiles = []
    for column in [c for c in frame.columns if c.endswith("_key")]:
        values = pd.to_numeric(frame[column], errors="coerce").fillna(0)
        zero_count = int((values == 0).sum())
        profiles.append(
            {
                "fact": fact_name,
                "column": column,
                "zero_key_rows": zero_count,
                "row_count": int(len(frame)),
                "zero_key_rate": round((zero_count / len(frame)) if len(frame) else 0, 4),
            }
        )
    return profiles


def validate() -> dict:
    sources = load_sources()

    fact_tables = [
        "fact_sales_order",
        "fact_sales_order_line",
        "fact_invoice",
        "fact_invoice_line",
        "fact_delivery",
        "fact_delivery_line",
        "fact_payment_allocation",
        "fact_stock_snapshot",
    ]

    facts = {table: read_output("facts", table) for table in fact_tables}
    dimensions = {
        path.stem: pd.read_csv(path, low_memory=False)
        for path in (OUTPUT_ROOT / "dimensions").glob("dim_*.csv")
    }
    marts = {
        path.stem: pd.read_csv(path, low_memory=False)
        for path in (OUTPUT_ROOT / "marts").glob("mart_*.csv")
    }

    row_expectations = {
        "fact_sales_order": ("c_order", len(sources["c_order"])),
        "fact_sales_order_line": ("c_orderline", len(sources["c_orderline"])),
        "fact_invoice": ("c_invoice", len(sources["c_invoice"])),
        "fact_invoice_line": ("c_invoiceline", len(sources["c_invoiceline"])),
        "fact_delivery": ("m_inout", len(sources["m_inout"])),
        "fact_delivery_line": ("m_inoutline", len(sources["m_inoutline"])),
        "fact_payment_allocation": ("c_allocationline", len(sources["c_allocationline"])),
        "fact_stock_snapshot": ("rv_storage", len(sources["rv_storage"])),
    }

    row_checks = []
    for fact_name, (source_name, expected_rows) in row_expectations.items():
        actual_rows = len(facts[fact_name])
        row_checks.append(
            {
                "fact": fact_name,
                "source": source_name,
                "source_rows": int(expected_rows),
                "fact_rows": int(actual_rows),
                "status": "PASS" if actual_rows == expected_rows else "WARN",
            }
        )

    total_checks = [
        difference_check(
            "sales_order_grand_total",
            number_sum(sources["c_order"], "grandtotal"),
            number_sum(facts["fact_sales_order"], "grand_total_amount"),
        ),
        difference_check(
            "sales_order_line_net_amount",
            number_sum(sources["c_orderline"], "linenetamt"),
            number_sum(facts["fact_sales_order_line"], "line_net_amount"),
        ),
        difference_check(
            "invoice_grand_total",
            number_sum(sources["c_invoice"], "grandtotal"),
            number_sum(facts["fact_invoice"], "grand_total_amount"),
        ),
        difference_check(
            "invoice_line_net_amount",
            number_sum(sources["c_invoiceline"], "linenetamt"),
            number_sum(facts["fact_invoice_line"], "line_net_amount"),
        ),
        difference_check(
            "payment_allocation_amount",
            number_sum(sources["c_allocationline"], "amount"),
            number_sum(facts["fact_payment_allocation"], "allocated_amount"),
        ),
    ]

    zero_key_profiles = []
    for fact_name, frame in facts.items():
        zero_key_profiles.extend(zero_key_profile(fact_name, frame))

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_tables": {name: int(len(frame)) for name, frame in sources.items()},
        "dimension_tables": {name: int(len(frame)) for name, frame in dimensions.items()},
        "fact_tables": {name: int(len(frame)) for name, frame in facts.items()},
        "mart_tables": {name: int(len(frame)) for name, frame in marts.items()},
        "row_checks": row_checks,
        "total_checks": total_checks,
        "zero_key_profiles": zero_key_profiles,
        "overall_status": "PASS"
        if all(check["status"] == "PASS" for check in row_checks + total_checks)
        else "WARN",
    }
    return report


def write_report(report: dict) -> None:
    VALIDATION_ROOT.mkdir(parents=True, exist_ok=True)
    (VALIDATION_ROOT / "etl_validation_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    lines = [
        "# ETL Validation Report",
        "",
        f"Generated at: `{report['generated_at']}`",
        "",
        f"Overall status: **{report['overall_status']}**",
        "",
        "## Row Checks",
        "",
        "| Fact | Source | Source rows | Fact rows | Status |",
        "|---|---|---:|---:|---|",
    ]
    for check in report["row_checks"]:
        lines.append(
            f"| {check['fact']} | {check['source']} | {check['source_rows']} | {check['fact_rows']} | {check['status']} |"
        )

    lines.extend(
        [
            "",
            "## Financial Total Checks",
            "",
            "| Check | Source value | Output value | Difference | Status |",
            "|---|---:|---:|---:|---|",
        ]
    )
    for check in report["total_checks"]:
        lines.append(
            f"| {check['name']} | {check['source_value']} | {check['output_value']} | {check['difference']} | {check['status']} |"
        )

    lines.extend(
        [
            "",
            "## Zero-Key Profile",
            "",
            "Rows with key `0` use the unknown member. They are expected when the ERP export is missing an optional reference.",
            "",
            "| Fact | Key | Zero rows | Row count | Rate |",
            "|---|---|---:|---:|---:|",
        ]
    )
    for item in report["zero_key_profiles"]:
        lines.append(
            f"| {item['fact']} | {item['column']} | {item['zero_key_rows']} | {item['row_count']} | {item['zero_key_rate']} |"
        )

    (VALIDATION_ROOT / "etl_validation_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    validation_report = validate()
    write_report(validation_report)
    print(json.dumps({"overall_status": validation_report["overall_status"]}, indent=2))

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

import psycopg2


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = (
    REPO_ROOT / "local-snapshots" / "vente-4months-20260514" / "bundle" / "manifest.json"
)


SANITY_QUERIES = {
    "orders_count": "SELECT COUNT(*) FROM business.c_order",
    "order_lines_count": "SELECT COUNT(*) FROM business.c_orderline",
    "invoices_count": "SELECT COUNT(*) FROM business.c_invoice",
    "invoice_lines_count": "SELECT COUNT(*) FROM business.c_invoiceline",
    "distinct_order_products": "SELECT COUNT(DISTINCT m_product_id) FROM business.c_orderline",
    "total_order_value": "SELECT SUM(grandtotal) FROM business.c_order",
    "total_invoice_line_value": "SELECT SUM(linenetamt) FROM business.c_invoiceline",
    "payments_via_allocations": """
        SELECT COUNT(DISTINCT al.c_payment_id)
        FROM business.c_allocationline al
        JOIN business.c_payment p ON p.c_payment_id = al.c_payment_id
    """,
    "shipment_lines_join_headers": """
        SELECT COUNT(*)
        FROM business.m_inoutline iol
        JOIN business.m_inout io ON io.m_inout_id = iol.m_inout_id
    """,
    "storage_products_with_available_stock": """
        SELECT COUNT(DISTINCT m_product_id)
        FROM business.rv_storage
        WHERE qtyavailable > 0
    """,
}


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify the vente PostgreSQL import against manifest row counts.")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    connection = psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5433"),
        dbname=os.getenv("POSTGRES_DB", "lpn_ai_bi"),
        user=os.getenv("POSTGRES_USER", "lpn_app_admin"),
        password=os.getenv("POSTGRES_PASSWORD")
        or os.getenv("POSTGRES_APP_ADMIN_PASSWORD")
        or "change_me_app_admin",
    )
    try:
        _verify_row_counts(connection, manifest)
        _run_sanity_queries(connection)
        _verify_readonly_access()
    finally:
        connection.close()


def _verify_row_counts(connection: Any, manifest: dict[str, Any]) -> None:
    print("Manifest row-count verification")
    print("-------------------------------")
    errors = []
    with connection.cursor() as cursor:
        for table in manifest["tables"]:
            table_name = table["name"].lower()
            cursor.execute(f'SELECT COUNT(*) FROM business."{table_name}"')
            actual = cursor.fetchone()[0]
            expected = table["row_count"]
            status = "OK" if actual == expected else "MISMATCH"
            print(f"{table['name']:<20} expected={expected:>7} actual={actual:>7} {status}")
            if actual != expected:
                errors.append((table["name"], expected, actual))
    if errors:
        raise SystemExit(f"Row-count mismatches detected: {errors}")


def _run_sanity_queries(connection: Any) -> None:
    print("\nSanity queries")
    print("--------------")
    with connection.cursor() as cursor:
        for name, query in SANITY_QUERIES.items():
            cursor.execute(query)
            value = cursor.fetchone()[0]
            print(f"{name:<38} {value}")


def _verify_readonly_access() -> None:
    connection = psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", "5433"),
        dbname=os.getenv("POSTGRES_DB", "lpn_ai_bi"),
        user="lpn_ai_readonly",
        password=os.getenv("POSTGRES_AI_READONLY_PASSWORD") or "change_me_ai_readonly",
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM business.c_order")
            count = cursor.fetchone()[0]
        print("\nReadonly role check")
        print("-------------------")
        print(f"lpn_ai_readonly can SELECT business.c_order: {count} rows")
    finally:
        connection.close()


if __name__ == "__main__":
    main()

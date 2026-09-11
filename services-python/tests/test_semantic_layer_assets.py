from __future__ import annotations

import csv
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCHEMA_METADATA = REPO_ROOT / "docs" / "schema_metadata.csv"
SEMANTIC_LAYER = REPO_ROOT / "docs" / "semantic_layer"


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def test_schema_metadata_covers_runtime_vente_tables_and_helper_views() -> None:
    rows = _read_csv(SCHEMA_METADATA)
    table_names = {row["table_name"] for row in rows}
    expected_runtime_objects = {
        "AD_ORG",
        "AD_USER",
        "C_ALLOCATIONHDR",
        "C_ALLOCATIONLINE",
        "C_BPARTNER",
        "C_BPARTNER_VENDOR",
        "C_DOCTYPE",
        "C_INVOICE",
        "C_INVOICELINE",
        "C_ORDER",
        "C_ORDERLINE",
        "C_PAYMENT",
        "C_TAX",
        "M_INOUT",
        "M_INOUTLINE",
        "M_LOCATOR",
        "M_PRODUCT",
        "M_PRODUCT_CATEGORY",
        "M_PRODUCT_COLLECTION",
        "M_PRODUCT_PO",
        "M_PRODUCT_THEME",
        "M_PRODUCT_TYPE",
        "M_WAREHOUSE",
        "PRODUCTS_WITHOUT_SUPPLIER",
        "RV_STORAGE",
        "V_PRODUCT_PRIMARY_SUPPLIER",
        "V_SALESREP_USER",
    }

    assert expected_runtime_objects <= table_names
    assert len(table_names) == len(rows)
    for row in rows:
        assert row["description_en"].strip()
        assert row["description_fr"].strip()
        assert row["key_columns"].strip()
        assert row["notes"].strip()
        assert row["sensitive"].strip().lower() in {"true", "false"}

    by_name = {row["table_name"]: row for row in rows}
    assert "V_PRODUCT_PRIMARY_SUPPLIER" in by_name["C_INVOICELINE"]["relations"]
    assert "M_INOUTLINE does not contain C_INVOICELINE_ID" in by_name["C_INVOICELINE"]["notes"]
    assert "M_PRODUCT.M_PRODUCT_TYPE_ID::text" in by_name["M_PRODUCT_TYPE"]["notes"]
    assert "C_INVOICELINE.M_PRODUCT_ID directly" in by_name["V_PRODUCT_PRIMARY_SUPPLIER"]["notes"]


def test_business_terms_cover_core_french_sales_vocabulary() -> None:
    rows = _read_csv(SEMANTIC_LAYER / "business_terms.csv")
    terms = {row["term"] for row in rows}
    required_terms = {
        "CA",
        "chiffre d'affaires",
        "CA facture",
        "CA commande",
        "commercial",
        "client",
        "produit",
        "type d'article",
        "fournisseur",
        "type de commande",
        "factures impayees",
        "stock disponible",
    }

    assert required_terms <= terms
    assert len(rows) >= 30
    for row in rows:
        assert row["canonical_concept"].strip()
        assert row["term_type"].strip()


def test_metrics_define_required_canonical_kpis_with_governance_fields() -> None:
    metrics_text = (SEMANTIC_LAYER / "metrics.yml").read_text(encoding="utf-8")
    required_metrics = {
        "ca_commande",
        "ca_facture",
        "nombre_commandes",
        "nombre_clients_actifs",
        "nombre_produits_vendus",
        "factures_payees",
        "factures_impayees",
        "couverture_facturation",
    }

    for metric_name in required_metrics:
        marker = f"- name: {metric_name}"
        assert marker in metrics_text
        block = metrics_text.split(marker, 1)[1].split("\n  - name:", 1)[0]
        for required_field in ("source_table:", "formula:", "date_field:", "grain:", "caveats:"):
            assert required_field in block


def test_dwh_asset_catalog_lists_ready_facts_dimensions_and_marts() -> None:
    rows = _read_csv(SEMANTIC_LAYER / "dwh_assets.csv")
    asset_names = {row["asset_name"] for row in rows}

    assert "fact_sales_order" in asset_names
    assert "fact_invoice_line" in asset_names
    assert "dim_product" in asset_names
    assert "dim_supplier" in asset_names
    assert "mart_sales_overview" in asset_names
    assert "mart_sales_by_supplier" in asset_names
    assert len(rows) == 26
    for row in rows:
        assert row["asset_type"] in {"dimension", "fact", "mart"}
        assert row["purpose"].strip()
        assert row["recommended_usage"].strip()

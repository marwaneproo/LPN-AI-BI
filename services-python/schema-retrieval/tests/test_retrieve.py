from __future__ import annotations

import os
from collections.abc import Iterator
from pathlib import Path

import pytest
from qdrant_client import QdrantClient

from schema_retrieval.config import Settings
from schema_retrieval.embedding import HashEmbeddingModel, load_embedding_model
from schema_retrieval.ingest import reindex_schema
from schema_retrieval.main import RetrieveRequest, RetrievedTable, retrieve
from schema_retrieval.retrieve import _expand_relations, retrieve_schema


SCHEMA_CSV_PATH = Path(__file__).resolve().parents[3] / "docs" / "schema_metadata.csv"


@pytest.fixture(scope="module")
def retrieval_context() -> Iterator[tuple[Settings, HashEmbeddingModel]]:
    collection_name = f"lpn_schema_test_{os.getpid()}"
    settings = Settings(
        QDRANT_URL=os.getenv("QDRANT_URL", "http://localhost:6333"),
        SCHEMA_CSV_PATH=str(SCHEMA_CSV_PATH),
        COLLECTION_NAME=collection_name,
    )
    client = QdrantClient(url=settings.qdrant_url)
    try:
        client.get_collections()
    except Exception as exc:
        pytest.skip(f"Qdrant is not reachable: {exc}")

    model = HashEmbeddingModel(settings.embedding_model, settings.embedding_dimension)
    reindex_schema(settings, model)
    yield settings, model
    if client.collection_exists(collection_name):
        client.delete_collection(collection_name)


@pytest.mark.parametrize(
    ("question", "expected_tables"),
    [
        ("How many orders did we have last month?", {"C_ORDER"}),
        ("Show sales order line quantities by product", {"C_ORDERLINE", "M_PRODUCT"}),
        ("Which customers bought products?", {"C_BPARTNER", "C_ORDER"}),
        ("Total invoice amount by customer", {"C_INVOICE", "C_BPARTNER"}),
        ("Unpaid invoice status by partner", {"C_INVOICE", "C_BPARTNER"}),
        ("Top products by ordered quantity", {"M_PRODUCT", "C_ORDERLINE"}),
        ("Sales by document number", {"C_ORDER"}),
        ("Order totals including tax", {"C_ORDER"}),
        ("Business partner credit limits", {"C_BPARTNER"}),
        ("Product analytics with shipment and invoice lines", {"M_PRODUCT"}),
    ],
)
def test_retrieve_expected_tables_in_top_8(
    retrieval_context: tuple[Settings, HashEmbeddingModel],
    question: str,
    expected_tables: set[str],
) -> None:
    settings, model = retrieval_context
    results = retrieve_schema(question, top_k=8, settings=settings, embedding_model=model)
    table_names = {result["table_name"] for result in results[:8]}
    assert expected_tables <= table_names


@pytest.mark.parametrize(
    ("question", "expected_tables"),
    [
        ("Quel est le CA facture par fournisseur ?", {"C_INVOICELINE", "V_PRODUCT_PRIMARY_SUPPLIER", "C_INVOICE"}),
        ("Commandes par commercial et type de commande", {"C_ORDER", "C_DOCTYPE"}),
        ("Produits avec risque de stock", {"RV_STORAGE", "M_PRODUCT", "M_PRODUCT_CATEGORY"}),
        ("Quel CA commande par type d'article ?", {"C_ORDERLINE", "M_PRODUCT", "M_PRODUCT_TYPE"}),
    ],
)
def test_retrieve_french_business_questions_include_critical_context(
    retrieval_context: tuple[Settings, HashEmbeddingModel],
    question: str,
    expected_tables: set[str],
) -> None:
    settings, model = retrieval_context
    results = retrieve_schema(question, top_k=8, settings=settings, embedding_model=model)
    table_names = {result["table_name"] for result in results[:8]}
    assert expected_tables <= table_names


def test_load_embedding_model_keeps_hash_backend_as_default() -> None:
    model = load_embedding_model("hash", "test-model", 32)

    assert isinstance(model, HashEmbeddingModel)
    assert model.dimension == 32


def test_load_embedding_model_rejects_unknown_backend() -> None:
    with pytest.raises(ValueError, match="Unsupported embedding backend"):
        load_embedding_model("not-a-backend", "test-model", 32)


def test_relation_expansion_includes_orderline() -> None:
    order_payload = {
        "table_name": "C_ORDER",
        "module": "Sales",
        "description_en": "Sales order header.",
        "description_fr": "",
        "key_columns": "C_ORDER_ID:bigint",
        "relations": "C_BPARTNER_ID -> C_BPARTNER (C_BPARTNER_ID)|C_ORDER_ID -> C_ORDERLINE (C_ORDER_ID)",
    }
    results = _expand_relations(
        retrieved=[{**order_payload, "score": 1.0}],
        payloads_by_table={
            "C_ORDER": order_payload,
            "C_ORDERLINE": {
                "table_name": "C_ORDERLINE",
                "module": "Sales",
                "description_en": "Sales order line.",
                "description_fr": "",
                "key_columns": "C_ORDERLINE_ID:bigint",
                "relations": "",
            },
        },
        limit=2,
    )
    table_names = {result["table_name"] for result in results}
    assert "C_ORDER" in table_names
    assert "C_ORDERLINE" in table_names
    assert len(results) <= 2
    assert any(
        result["table_name"] == "C_ORDERLINE" and result.get("expanded_from") == "C_ORDER"
        for result in results
    )


def test_retrieve_endpoint_returns_bare_table_list(
    retrieval_context: tuple[Settings, HashEmbeddingModel],
) -> None:
    results = retrieve(
        RetrieveRequest(
            question="Sales order header storing order dates status totals customer and document type",
            top_k=1,
        )
    )
    assert isinstance(results, list)
    assert all(isinstance(result, RetrievedTable) for result in results)
    assert [result.table_name for result in results] == ["C_ORDER", "C_ORDERLINE"]

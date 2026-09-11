from __future__ import annotations

import os
from pathlib import Path

from qdrant_client import QdrantClient

from schema_retrieval.config import Settings
from schema_retrieval.embedding import HashEmbeddingModel
from schema_retrieval.ingest import reindex_schema
from schema_retrieval.retrieve import retrieve_schema


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_CSV = ROOT / "docs" / "schema_metadata.csv"

CASES = [
    ("How many orders did we have last month?", {"C_ORDER"}),
    ("Show sales order line quantities by product", {"C_ORDERLINE", "M_PRODUCT"}),
    ("Which customers bought products?", {"C_BPARTNER", "C_ORDER"}),
    ("Total invoice amount by customer", {"C_INVOICE", "C_BPARTNER"}),
    ("Unpaid invoice status by partner", {"C_INVOICE", "C_BPARTNER"}),
    ("Top products by ordered quantity", {"M_PRODUCT", "C_ORDERLINE"}),
]


def main() -> int:
    settings = Settings(
        QDRANT_URL=os.getenv("QDRANT_URL", "http://localhost:6333"),
        SCHEMA_CSV_PATH=str(SCHEMA_CSV),
        COLLECTION_NAME=f"lpn_schema_diagnose_{os.getpid()}",
    )
    client = QdrantClient(url=settings.qdrant_url)
    model = HashEmbeddingModel(settings.embedding_model, settings.embedding_dimension)
    reindex_schema(settings, model)
    try:
        for question, expected in CASES:
            results = retrieve_schema(question, top_k=8, settings=settings, embedding_model=model)
            names = [
                f"{result['table_name']}={float(result['score']):.3f}"
                for result in results[:8]
            ]
            table_names = {str(result["table_name"]) for result in results[:8]}
            status = "PASS" if expected <= table_names else "MISS"
            print(f"\n[{status}] {question}")
            print(f"Expected: {', '.join(sorted(expected))}")
            print(f"Top 8:    {', '.join(names)}")
    finally:
        if client.collection_exists(settings.collection_name):
            client.delete_collection(settings.collection_name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

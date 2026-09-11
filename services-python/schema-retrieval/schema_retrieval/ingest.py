from __future__ import annotations

import hashlib
import uuid
from pathlib import Path
from typing import Any

import pandas as pd
from qdrant_client import QdrantClient, models

from schema_retrieval.config import Settings
from schema_retrieval.embedding import HashEmbeddingModel
from schema_retrieval.qdrant import get_qdrant_client


def load_schema_rows(csv_path: str) -> list[dict[str, Any]]:
    dataframe = pd.read_csv(csv_path).fillna("")
    rows = dataframe.to_dict(orient="records")
    return [row for row in rows if not _is_sensitive(row)]


def compose_table_document(row: dict[str, Any]) -> str:
    return (
        f"Table: {row.get('table_name', '')}. "
        f"Module: {row.get('module', '')}. "
        f"{row.get('description_en', '')}. "
        f"{row.get('description_fr', '')}. "
        f"Columns: {row.get('key_columns', '')}. "
        f"Relations: {row.get('relations', '')}. "
        f"Notes: {row.get('notes', '')}."
    )


def reindex_schema(settings: Settings, embedding_model: HashEmbeddingModel) -> int:
    csv_path = Path(settings.schema_csv_path)
    if not csv_path.exists():
        raise FileNotFoundError(f"Schema CSV does not exist: {csv_path}")

    rows = load_schema_rows(str(csv_path))
    documents = [compose_table_document(row) for row in rows]
    vectors = embedding_model.encode(documents)

    client = get_qdrant_client(settings)
    _recreate_collection(client, settings.collection_name, embedding_model.dimension)

    points = [
        models.PointStruct(
            id=_point_id(str(row["table_name"])),
            vector=vector,
            payload=row,
        )
        for row, vector in zip(rows, vectors, strict=True)
    ]
    if points:
        client.upsert(collection_name=settings.collection_name, points=points, wait=True)
    return len(points)


def get_collection_size(settings: Settings) -> int:
    client = get_qdrant_client(settings)
    result = client.count(collection_name=settings.collection_name, exact=True)
    return int(result.count)


def qdrant_reachable(settings: Settings) -> bool:
    try:
        get_qdrant_client(settings).get_collections()
    except Exception:
        return False
    return True


def _recreate_collection(client: QdrantClient, collection_name: str, vector_size: int) -> None:
    if client.collection_exists(collection_name):
        client.delete_collection(collection_name=collection_name)
    client.create_collection(
        collection_name=collection_name,
        vectors_config=models.VectorParams(size=vector_size, distance=models.Distance.COSINE),
    )


def _point_id(table_name: str) -> str:
    digest = hashlib.sha256(table_name.encode("utf-8")).hexdigest()
    return str(uuid.UUID(digest[:32]))


def _is_sensitive(row: dict[str, Any]) -> bool:
    return str(row.get("sensitive", "")).strip().lower() == "true"

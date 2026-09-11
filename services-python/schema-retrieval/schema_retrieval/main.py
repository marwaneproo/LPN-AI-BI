from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

from schema_retrieval.config import Settings, get_settings
from schema_retrieval.embedding import load_embedding_model
from schema_retrieval.ingest import get_collection_size, qdrant_reachable, reindex_schema
from schema_retrieval.qdrant import close_qdrant_client
from schema_retrieval.retrieve import retrieve_schema


class AppState(BaseModel):
    model_loaded: bool = False
    collection_size: int = 0


class RetrieveRequest(BaseModel):
    question: str = Field(min_length=1)
    top_k: int = Field(default=8, ge=1, le=50)
    language: Literal["en", "fr"] = "en"


class RetrievedTable(BaseModel):
    table_name: str
    module: str
    description_en: str
    description_fr: str
    key_columns: str
    relations: str
    notes: str = ""
    score: float
    expanded_from: str | None = None


settings: Settings = get_settings()
embedding_model = load_embedding_model(
    backend=settings.embedding_backend,
    model_name=settings.embedding_model,
    dimension=settings.embedding_dimension,
)
app_state = AppState(model_loaded=True)


@asynccontextmanager
async def lifespan(_: FastAPI):
    app_state.collection_size = reindex_schema(settings, embedding_model)
    yield
    close_qdrant_client()


app = FastAPI(title="LPN Schema Retrieval", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, bool | int | str]:
    reachable = qdrant_reachable(settings)
    collection_size = get_collection_size(settings) if reachable else 0
    app_state.collection_size = collection_size
    return {
        "status": "ok" if app_state.model_loaded and reachable else "degraded",
        "model_loaded": app_state.model_loaded,
        "qdrant_reachable": reachable,
        "collection_size": collection_size,
    }


@app.post("/admin/reindex")
def admin_reindex(x_admin_key: str | None = Header(default=None)) -> dict[str, int | str]:
    if x_admin_key != settings.admin_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid admin key",
        )

    app_state.collection_size = reindex_schema(settings, embedding_model)
    return {"status": "ok", "collection_size": app_state.collection_size}


@app.post("/v1/retrieve", response_model=list[RetrievedTable])
def retrieve(request: RetrieveRequest) -> list[RetrievedTable]:
    results = retrieve_schema(
        question=request.question,
        top_k=request.top_k,
        settings=settings,
        embedding_model=embedding_model,
    )
    return [RetrievedTable(**result) for result in results]

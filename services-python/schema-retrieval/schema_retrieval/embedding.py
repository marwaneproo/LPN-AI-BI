from __future__ import annotations

import hashlib
import math
import re
from typing import Protocol


TOKEN_RE = re.compile(r"[A-Za-z0-9_]+")


class EmbeddingModel(Protocol):
    model_name: str
    dimension: int

    def encode(self, documents: list[str]) -> list[list[float]]:
        ...


class HashEmbeddingModel:
    """Small deterministic embedding model for local schema indexing."""

    def __init__(self, model_name: str, dimension: int = 384) -> None:
        self.model_name = model_name
        self.dimension = dimension

    def encode(self, documents: list[str]) -> list[list[float]]:
        return [self._embed(document) for document in documents]

    def _embed(self, document: str) -> list[float]:
        vector = [0.0] * self.dimension
        tokens = TOKEN_RE.findall(document.lower())
        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.dimension
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign

        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]


class SentenceTransformerEmbeddingModel:
    """Optional multilingual embedding backend for higher-quality schema retrieval."""

    def __init__(self, model_name: str, dimension: int) -> None:
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as exc:
            raise ValueError(
                "EMBEDDING_BACKEND=sentence-transformers requires installing "
                "the optional dependency: uv sync --extra ml"
            ) from exc

        self.model_name = model_name
        self.dimension = dimension
        self._model = SentenceTransformer(model_name)

    def encode(self, documents: list[str]) -> list[list[float]]:
        vectors = self._model.encode(
            documents,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return [self._resize_vector(vector.tolist()) for vector in vectors]

    def _resize_vector(self, vector: list[float]) -> list[float]:
        if len(vector) == self.dimension:
            return vector
        if len(vector) > self.dimension:
            return vector[: self.dimension]
        return [*vector, *([0.0] * (self.dimension - len(vector)))]


def load_embedding_model(
    backend: str,
    model_name: str,
    dimension: int,
) -> EmbeddingModel:
    normalized_backend = backend.strip().lower()
    if normalized_backend == "hash":
        return HashEmbeddingModel(model_name=model_name, dimension=dimension)
    if normalized_backend in {"sentence-transformers", "sentence_transformers", "sbert"}:
        return SentenceTransformerEmbeddingModel(model_name=model_name, dimension=dimension)
    raise ValueError(f"Unsupported embedding backend: {backend}")

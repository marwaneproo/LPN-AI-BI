"""Qdrant client factory.

Supports two modes, selected by configuration:

* **Server mode** (default, used in Docker): connects over HTTP to the Qdrant
  service at ``settings.qdrant_url``.
* **Embedded mode** (used for local-native dev): runs Qdrant in-process with
  on-disk storage at ``settings.qdrant_path`` — no separate server needed.

Embedded mode takes an **exclusive file lock** on the storage directory, so only
one ``QdrantClient(path=...)`` may be open per process at a time. This module
therefore returns a single shared client instance for the whole process.
"""

from __future__ import annotations

from threading import Lock

from qdrant_client import QdrantClient

from schema_retrieval.config import Settings

_client: QdrantClient | None = None
_lock = Lock()


def get_qdrant_client(settings: Settings) -> QdrantClient:
    """Return a process-wide shared Qdrant client (created on first use)."""
    global _client
    if _client is not None:
        return _client
    with _lock:
        if _client is None:
            if settings.qdrant_path:
                _client = QdrantClient(path=settings.qdrant_path)
            else:
                _client = QdrantClient(url=settings.qdrant_url)
    return _client


def close_qdrant_client() -> None:
    """Release the shared client (and its on-disk lock in embedded mode).

    Called on service shutdown so embedded-mode storage is unlocked cleanly
    instead of being torn down noisily at interpreter exit.
    """
    global _client
    with _lock:
        if _client is not None:
            try:
                _client.close()
            finally:
                _client = None

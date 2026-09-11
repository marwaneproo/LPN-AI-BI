from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    qdrant_url: str = Field(default="http://localhost:6333", alias="QDRANT_URL")
    # When set, Qdrant runs embedded on-disk (no server) at this path. Leave
    # empty to use the server at `qdrant_url` (the Docker default).
    qdrant_path: str = Field(default="", alias="QDRANT_PATH")
    embedding_model: str = Field(
        default="paraphrase-multilingual-MiniLM-L12-v2",
        alias="EMBEDDING_MODEL",
    )
    schema_csv_path: str = Field(default="docs/schema_metadata.csv", alias="SCHEMA_CSV_PATH")
    collection_name: str = Field(default="lpn_schema", alias="COLLECTION_NAME")
    admin_key: str = Field(default="change_me_admin_key", alias="ADMIN_KEY")
    embedding_backend: str = Field(default="hash", alias="EMBEDDING_BACKEND")
    embedding_dimension: int = Field(default=384, alias="EMBEDDING_DIMENSION")


@lru_cache
def get_settings() -> Settings:
    return Settings()

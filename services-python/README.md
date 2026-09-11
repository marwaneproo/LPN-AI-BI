# Python Services

`services-python/` is a `uv` workspace for the Python side of the LPN AI-BI platform.

## Packages

| Package | Import name | Purpose |
|---------|-------------|---------|
| `data-import` | `data_import` | Snapshot import CLI/service package |
| `schema-retrieval` | `schema_retrieval` | Schema metadata retrieval API |
| `sql-validator` | `sql_validator` | SQL validation API |
| `predictive` | `predictive` | Forecasting API stub |

## Common commands

```powershell
cd D:\LPN_PROJECT\services-python
python -m uv sync
python -m uv run python -c "import data_import, schema_retrieval, sql_validator; print('ok')"
python -m uv run data-import import data-import/tests/fixtures/sample-bundle.zip --dry-run
python -m uv run data-import import data-import/tests/fixtures/sample-bundle.zip
python -m uv run data-import history
python -m uv run pytest
```

From the repository root, the same workflow is available through:

```powershell
.\scripts\python.ps1 sync
.\scripts\python.ps1 test
.\scripts\python.ps1 lint
```

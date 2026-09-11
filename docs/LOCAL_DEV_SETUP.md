# Local-native development setup (no Docker)

This guide moves the **database (PostgreSQL)** and the **vector store (Qdrant)**
out of Docker and onto your machine, and runs every backend service natively.
Docker stays untouched for later — `docker-compose.yml` is **not** modified, so
moving back to containers at the end is free.

> **Why this works easily:** every service already reads its connection settings
> from environment variables and **defaults to `localhost`** (`application.yml`
> defaults to `localhost:5433`, Python services to `localhost`). Docker only
> *overrides* those with container hostnames. Running native = "don't set the
> Docker overrides, point everything at localhost."

## Target topology

> **Postgres version/port:** Use your **existing PostgreSQL 18** on its default
> port **5432** — no need to install 16. PG18 is fully compatible and restores a
> PG16 dump cleanly. It's a shared cluster (Axelor, Darkom, …); that's safe —
> Postgres isolates by database. **Do not change its port** (other projects rely
> on 5432). The app is pointed at 5432 via env. If you instead run a dedicated
> instance on 5433, pass `-DbPort 5433` to the launcher.

| Component | How it runs locally | Port |
|---|---|---|
| PostgreSQL 18 (existing) | Native Windows install (shared cluster) | **5432** |
| Qdrant | **Embedded on-disk** inside schema-retrieval (no server) | — |
| schema-retrieval | `uvicorn` (native) | 8084 |
| sql-validator | `uvicorn` (native) | 8086 |
| sql-executor | Spring Boot `bootRun` (native) | 8082 |
| llm-orchestrator | Spring Boot `bootRun` (native) | 8081 |
| predictive | `uvicorn` (native) | 8085 |
| frontend | Vite (already proxies to `localhost:8081`) | 5173 |
| Ollama | Native (already) | 11434 |

---

## Prerequisites (install once)

1. **PostgreSQL** — you already have **PG18 on port 5432**. No install needed.
   Just make sure the command-line tools (`psql`, `pg_dump`, `pg_dumpall`,
   `pg_restore`) are on your `PATH` (e.g. `C:\Program Files\PostgreSQL\18\bin`),
   and you know the `postgres` superuser password.
2. **JDK 21** (for the Spring services) and **uv** (for the Python services) — you
   already build the project with these.
3. **Ollama** with the required models (already installed; the launcher checks them).

---

## Step 1 — Dump the existing data out of Docker

Do this **before** stopping Docker. The dump uses `docker exec`, so it does not
need a free host port.

```powershell
# from the repo root, with the Docker stack's postgres running
docker compose up -d postgres
New-Item -ItemType Directory -Force backups | Out-Null

# Full logical dump: roles (with passwords) + all databases
docker compose exec -T postgres pg_dumpall -U postgres > backups/full_dump.sql
```

> If you only want the one database (faster, no roles):
> `docker compose exec -T postgres pg_dump -U postgres -d lpn_ai_bi -Fc > backups/lpn_ai_bi.dump`
> (restore later with `pg_restore`).

You can now stop the Docker postgres (optional — native PG18 is on 5432, so there
is no port conflict):

```powershell
docker compose stop postgres
```

---

## Step 2 — Provision the native database

Your native PG18 is listening on `localhost:5432`. Load the dump into it.
(`PGPASSWORD` is your `postgres` superuser password.) The dump only adds the
`lpn_ai_bi` database + its two roles to the shared cluster — other databases are
untouched.

```powershell
$env:PGPASSWORD = "<your-postgres-superuser-password>"

# Restore everything (roles + database). Ignore "role ... already exists" notices.
psql -h localhost -p 5432 -U postgres -f backups/full_dump.sql
```

Verify roles, schemas and a table exist:

```powershell
psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -c "\du"        # lpn_app_admin, lpn_ai_readonly
psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -c "\dn"        # business, app
psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -c "\dt business.*"
```

> **Fresh start instead?** If you skipped the dump, create the DB and run the
> existing init SQL to get the roles/schemas:
> ```powershell
> psql -h localhost -p 5432 -U postgres -c "CREATE DATABASE lpn_ai_bi;"
> $env:POSTGRES_DB="lpn_ai_bi"
> $env:POSTGRES_APP_ADMIN_PASSWORD="change_me_app_admin"
> $env:POSTGRES_AI_READONLY_PASSWORD="change_me_ai_readonly"
> psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -f infra/postgres/init/00-roles-and-schemas.sql
> ```

The role passwords **must match** the values in your repo `.env`
(`POSTGRES_APP_ADMIN_PASSWORD`, `POSTGRES_AI_READONLY_PASSWORD`). The dump
preserves them automatically; for a fresh start, set them as shown above.

---

## Step 3 — Qdrant: nothing to install

Qdrant now runs **embedded on-disk** inside schema-retrieval. Setting the
`QDRANT_PATH` environment variable switches it from "connect to a server" to
"store vectors in this folder." The collection is **rebuilt automatically on
startup** from `docs/schema_metadata.csv`, so there is no separate ingest step.

The launcher sets `QDRANT_PATH` to `<repo>/.local/qdrant` for you.

---

## Step 4 — Run everything

### Option A — one launcher (recommended)

```powershell
# from the repo root
powershell -ExecutionPolicy Bypass -File scripts\launch-local-native.ps1
```

It starts Ollama, checks Postgres on 5432, launches the five services on the
ports above, then the frontend, and opens the browser. Logs go to `logs\<service>\`.

### Option B — manual (one terminal each, good for debugging)

```powershell
# Postgres connection used by the JVM + python services
$env:APP_DATABASE_URL          = "jdbc:postgresql://localhost:5432/lpn_ai_bi"
$env:APP_ADMIN_DATABASE_URL    = "jdbc:postgresql://localhost:5432/lpn_ai_bi"
$env:READONLY_DATABASE_URL     = "jdbc:postgresql://localhost:5432/lpn_ai_bi?options=-c%20statement_timeout%3D30000%20-c%20search_path%3Dbusiness"

# 1) schema-retrieval (embedded Qdrant)
cd services-python
$env:QDRANT_PATH="$PWD\..\.local\qdrant"; $env:EMBEDDING_BACKEND="hash"; $env:SCHEMA_CSV_PATH="$PWD\..\docs\schema_metadata.csv"
uv run uvicorn schema_retrieval.main:app --host 127.0.0.1 --port 8084

# 2) sql-validator
cd services-python; uv run uvicorn sql_validator.main:app --host 127.0.0.1 --port 8086

# 3) predictive
cd services-python
$env:DATABASE_URL="postgresql://lpn_ai_readonly:change_me_ai_readonly@localhost:5432/lpn_ai_bi"
uv run uvicorn predictive.main:app --host 127.0.0.1 --port 8085

# 4) sql-executor
cd services-java; $env:SERVER_PORT="8082"; .\gradlew :sql-executor:bootRun

# 5) llm-orchestrator
cd services-java; $env:SERVER_PORT="8081"; .\gradlew :llm-orchestrator:bootRun

# 6) frontend
cd frontend; npm run dev
```

Open <http://127.0.0.1:5173>.

---

## Going back to Docker (end of project)

Nothing to undo — just run your original launcher
(`Launch_LPN_AI_BI.bat`). The compose stack was never changed. To carry your
local data back into the container, `pg_dump` your native DB and load it into the
Docker volume the same way as Step 1/2 in reverse.

## Notes

- **Embedded Qdrant is single-process** — only schema-retrieval may open the
  `.local/qdrant` folder. Don't run two copies of that service at once.
- `.local/` and `backups/` should be git-ignored (large/local-only).
- Don't commit real passwords. Keep them in `.env`, which is already ignored.

# LPN AI-BI — AI Coding Agent Task Spec (Weeks 1–4)

**Audience:** AI coding assistant (Claude Opus 4.7, Codex 5.5, or equivalent).
**Project:** LPN AI-BI Platform — separate web application that queries an imported Compiere snapshot via natural language and produces forecasts.
**Phase covered:** Weeks 1–4 (Foundations & Data).
**Companion document:** `LPN_AI_BI_Production_Plan.md` is the source of truth for *why* decisions were made. This file is *what to do*.

---

## 0. How To Read And Use This File

You are an AI coding agent. The human (Youssef) is solo on this project and uses you to execute mechanical work, scaffold services, write boilerplate, and run tests. He retains all architectural authority. **You do not change the architecture.** When a task is ambiguous, you stop and ask, you do not improvise.

### 0.1. The non-negotiable rules

1. **Work only one task at a time.** Read the task, complete it, run its acceptance tests, update the context journal, then **STOP and wait for human confirmation before starting the next task.** Even if the next task seems trivial.
2. **Update `CONTEXT_JOURNAL.md` after every task.** This is the project's memory across sessions. If you skip the journal update, future agent sessions lose context.
3. **If acceptance tests fail, do not proceed.** Mark the task as `BLOCKED` in the journal, describe what failed, and wait for human input.
4. **Do not invent dependencies, versions, or library choices.** Every external dependency in this document has been chosen on purpose. If you think a different choice is better, write a note in the journal under "Suggestions for human review" and use what's specified.
5. **Do not write code outside the scope of the active task.** No "while I'm here" refactors. No "I noticed this could be improved." Stay in scope.
6. **Run commands from the repository root unless told otherwise.** Never assume a working directory.
7. **Never connect to LPN's Oracle database or attempt to.** The architecture is snapshot-based. Your application reads PostgreSQL only.

### 0.2. The context journal

Create `docs/CONTEXT_JOURNAL.md` in the first task and update it after every subsequent task. Format:

```markdown
# LPN AI-BI — Context Journal

This file is the running log of completed work. AI agents update this after every successful task. Humans use it to verify progress and onboard new agent sessions.

## Format
Each entry: ### Task X.Y — <name> — <YYYY-MM-DD HH:MM>
Then: status, what was done, files touched, acceptance test results, notes for next session.

---

## Session Log

### Task 1.1 — Repository skeleton — 2026-04-29 14:00
**Status:** ✅ Done
**Files created:**
- `README.md`
- `.gitignore`
- `docker-compose.yml` (skeleton, services not yet defined)
- ...
**Acceptance tests run:**
- `git status` clean: ✅
- `tree -L 2` shows expected folder layout: ✅
**Notes for next session:** None. Ready for Task 1.2.
```

If a task is blocked, the entry uses `**Status:** ❌ BLOCKED` with a clear `**Blocker:**` field.

### 0.3. Stack reminder (do not deviate)

| Component | Stack |
|-----------|-------|
| Frontend | React 18 + Vite 6 + TypeScript + Tailwind v4 + shadcn/ui + Recharts + ECharts |
| API Gateway | .NET 9 + YARP |
| Auth | Keycloak (off-the-shelf) |
| LLM Orchestrator | Spring Boot 3 + Java 21 + LangChain4j 1.x |
| SQL Executor | Spring Boot 3 + Java 21 + Postgres JDBC + sqlglot sidecar |
| Reporting | .NET 9 + EF Core |
| Schema Retrieval | Python 3.11 + FastAPI + sentence-transformers + Qdrant |
| Predictive | Python 3.11 + FastAPI + Prophet + LightGBM |
| Data Import | Python 3.11 CLI + pandas + SQLAlchemy + Click |
| Database | PostgreSQL 16 |
| Vector store | Qdrant |
| LLM (local) | Ollama: `a-kore/Arctic-Text2SQL-R1-7B` + `llama3.2:3b` |

### 0.4. The repository layout you will create

```
lpn-ai-bi/
├── README.md
├── .gitignore
├── docker-compose.yml
├── docs/
│   ├── CONTEXT_JOURNAL.md          ← updated after every task
│   ├── schema_metadata.csv         ← curated by human, read by Schema RAG
│   ├── architecture/               ← human-authored docs
│   └── exports/                    ← snapshot bundles from company PC
├── infra/
│   ├── postgres/
│   │   └── init/                   ← roles, schemas, init SQL
│   ├── qdrant/
│   ├── ollama/
│   ├── grafana/                    (later phases)
│   └── prometheus/                 (later phases)
├── services-dotnet/
│   ├── LpnAiBi.sln
│   ├── gateway/                    ← YARP gateway
│   └── reporting/                  ← EF Core CRUD + audit
├── services-java/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── llm-orchestrator/
│   └── sql-executor/
├── services-python/
│   ├── pyproject.toml              (workspace root)
│   ├── schema-retrieval/
│   ├── data-import/
│   ├── predictive/                 (later phases)
│   └── sql-validator/              (sqlglot sidecar)
├── frontend/                       ← existing React/Vite app, will be reorganized later
└── poc-dotnet-archive/             ← move existing .NET PoC here, read-only reference
```

### 0.5. Ports you will use

| Service | Host port | Container port |
|---------|-----------|----------------|
| Frontend (Vite) | 5173 | 5173 |
| API Gateway | 8000 | 8080 |
| LLM Orchestrator | 8081 | 8080 |
| SQL Executor | 8082 | 8080 |
| Reporting | 8083 | 8080 |
| Schema Retrieval | 8084 | 8000 |
| Data Import (no port, CLI only) | — | — |
| Predictive | 8085 | 8000 |
| SQL Validator (sqlglot) | 8086 | 8000 |
| PostgreSQL | 5433 | 5432 |
| Qdrant | 6333 | 6333 |
| Ollama | 11434 | 11434 |
| Keycloak | 8090 | 8080 |

---

## 1. Phase Overview (Weeks 1–4)

By the end of Week 4 the system can: import a Compiere snapshot into PostgreSQL, retrieve relevant tables for a natural-language question via Schema RAG, generate PostgreSQL SQL using a local LLM, validate that SQL with sqlglot, execute it under a read-only role, and return rows. All in English. The accuracy baseline is published.

```
Week 1 → repo + Postgres + Compiere export plan + schema metadata CSV
Week 2 → Data Import Pipeline + Schema Retrieval Service + LLM seed
Week 3 → Spring Boot LLM Orchestrator + LangChain4j + first end-to-end SQL
Week 4 → SQL Executor + sqlglot validator + 100-question English eval
```

Every task below is numbered W<week>.<sequence>. Stop after each one.

---

## WEEK 1 — Repository, Infrastructure, Schema Metadata

### Task 1.1 — Repository skeleton + .gitignore + README

**Goal:** Create the empty repository structure exactly as specified in §0.4. No code logic yet — just folders, README files, and `.gitignore`.

**Steps:**
1. From the repo root, create every directory listed in §0.4. Each leaf directory gets a `.gitkeep` file or a placeholder `README.md`.
2. Write a top-level `README.md` that documents: project name, one-paragraph description, link to `docs/CONTEXT_JOURNAL.md`, instructions to run `docker compose up`, link to the production plan document.
3. Write `.gitignore` covering: `node_modules/`, `dist/`, `build/`, `.gradle/`, `bin/`, `obj/`, `__pycache__/`, `.venv/`, `.idea/`, `.vscode/`, `*.env`, `docs/exports/*.zip`, `docs/exports/*.csv`, model files, IDE noise.
4. Move any existing .NET PoC code into `poc-dotnet-archive/`. Add a `README.md` inside it stating "Reference only. Not built or deployed. See production plan for migration rationale."
5. Create `docs/CONTEXT_JOURNAL.md` with the header from §0.2 and your first entry for this task.
6. `git add . && git commit -m "Task 1.1: Repository skeleton"`

**Acceptance tests (the human will run these):**
- [ ] `tree -L 2 -I 'node_modules|.git'` output matches the structure in §0.4 exactly.
- [ ] `git status` shows a clean working tree.
- [ ] `cat docs/CONTEXT_JOURNAL.md` shows Task 1.1 marked Done.
- [ ] `cat .gitignore | head -30` shows the entries listed in step 3.
- [ ] `cat README.md` mentions the project name, the journal location, and the production plan.

**Stop here.** Wait for the human to confirm the repository is correct before proceeding to Task 1.2.

---

### Task 1.2 — Docker Compose with PostgreSQL, Qdrant, Ollama (no app services yet)

**Goal:** A `docker-compose.yml` that brings up PostgreSQL 16, Qdrant, and Ollama. No application services yet — just the infrastructure dependencies. PostgreSQL must come up with two roles and two schemas already created.

**Steps:**

1. Write `infra/postgres/init/00-roles-and-schemas.sql`:
   - Create role `lpn_app_admin` with login + password from env var.
   - Create role `lpn_ai_readonly` with login + password from env var.
   - Create database `lpn_ai_bi` (this is handled via `POSTGRES_DB` env, not in the script — see Compose).
   - Inside the DB, create schemas `business` and `app`.
   - `lpn_app_admin` owns both schemas.
   - `lpn_ai_readonly` is granted `USAGE` on `business` and `SELECT` on all current and future tables in `business`. Use `ALTER DEFAULT PRIVILEGES` so future tables inherit.
   - `lpn_ai_readonly` has zero access to schema `app`.

2. Write `docker-compose.yml` at repo root with three services:
   - `postgres`: image `postgres:16-alpine`, env `POSTGRES_DB=lpn_ai_bi`, env for both role passwords (use `${...}` from a `.env` file), port `5433:5432`, volume `pgdata:/var/lib/postgresql/data`, mount `infra/postgres/init` to `/docker-entrypoint-initdb.d` read-only, healthcheck using `pg_isready`.
   - `qdrant`: image `qdrant/qdrant:latest`, port `6333:6333`, volume `qdrant_data:/qdrant/storage`.
   - `ollama`: image `ollama/ollama:latest`, port `11434:11434`, volume `ollama_data:/root/.ollama`. Add GPU reservation if NVIDIA Container Toolkit is detected (use `deploy.resources.reservations.devices` with NVIDIA driver). Note: this only works on Linux/WSL2; on bare Windows the human will need to comment out the GPU section.
   - Top-level `volumes:` block declaring `pgdata`, `qdrant_data`, `ollama_data`.
   - Top-level `networks:` block with one default network. All three services on it.

3. Write `.env.example` at repo root listing every required variable with placeholder values (`POSTGRES_DB`, `POSTGRES_APP_ADMIN_PASSWORD`, `POSTGRES_AI_READONLY_PASSWORD`). Add it to git. Tell the human to copy it to `.env` (which is gitignored).

4. Write `infra/postgres/init/00-roles-and-schemas.sql` to read the readonly password via `current_setting()` is NOT possible at init time — instead, use literal placeholders in the SQL and have Docker substitute them via `envsubst` in an entrypoint script. **Simpler approach:** create the `lpn_ai_readonly` role using a fixed password via SQL, then have the human change it later, OR use `psql -v` variable substitution. Use the second approach — pass the password as a `psql` variable by writing a wrapper shell script `infra/postgres/init/00-init.sh` that runs `psql -v ai_pwd="$POSTGRES_AI_READONLY_PASSWORD" -f /docker-entrypoint-initdb.d/00-roles-and-schemas.sql`. Place the `.sh` in `init/` and make it executable. The Postgres image runs `.sh` files in `init/` automatically.

5. Update the `CONTEXT_JOURNAL.md` entry for Task 1.2.

**Acceptance tests:**
- [ ] `docker compose config` exits 0 and shows all three services.
- [ ] `docker compose up -d` brings up all three with no errors. Wait 30 seconds.
- [ ] `docker compose ps` shows all three as `Up` and `postgres` as `healthy`.
- [ ] `docker exec lpn-ai-bi-postgres-1 psql -U lpn_app_admin -d lpn_ai_bi -c "\dn"` lists schemas `app` and `business` (plus `public` and system schemas).
- [ ] `docker exec lpn-ai-bi-postgres-1 psql -U lpn_app_admin -d lpn_ai_bi -c "\du"` shows both roles.
- [ ] `curl http://localhost:6333/healthz` returns OK.
- [ ] `curl http://localhost:11434/api/tags` returns a valid (possibly empty) model list JSON.
- [ ] `docker compose down -v && docker compose up -d` reproduces the same state — init scripts run on a fresh volume.

**Stop here.** Wait for human confirmation.

---

### Task 1.3 — Pull Ollama models

**Goal:** Both Pattern B models are pulled into the Ollama container and resident.

**Steps:**

1. Run inside the running Ollama container:
   - `docker exec lpn-ai-bi-ollama-1 ollama pull a-kore/Arctic-Text2SQL-R1-7B`
   - `docker exec lpn-ai-bi-ollama-1 ollama pull llama3.2:3b`
   - Optionally: `docker exec lpn-ai-bi-ollama-1 ollama pull qwen2.5-coder:7b-instruct` (Pattern A fallback).
2. Confirm: `docker exec lpn-ai-bi-ollama-1 ollama list` shows the two required models with sizes.
3. Run a smoke test against each: send a one-line prompt via `curl http://localhost:11434/api/generate -d '{"model":"llama3.2:3b","prompt":"Reply with exactly: OK","stream":false}'`. Verify the response is non-empty.
4. Document the smoke test commands and outputs in `docs/runbook/ollama-models.md`.
5. Update the journal.

**Acceptance tests:**
- [ ] `ollama list` inside container shows both models.
- [ ] Smoke test against `llama3.2:3b` returns a response in <5 seconds.
- [ ] Smoke test against `a-kore/Arctic-Text2SQL-R1-7B` returns a response in <30 seconds (first call may be slow due to cold load).
- [ ] `nvidia-smi` (run by human on host) shows GPU memory usage when Ollama is processing — confirms GPU acceleration. If GPU isn't being used, flag this in the journal as a BLOCKER.

**Stop here.**

---

### Task 1.4 — Compiere export query catalog (template only — human will fill it on company PC)

**Goal:** A documented procedure and template the human will use on the company PC to produce snapshot bundles. You are *not* running Compiere queries — you are producing the documentation and skeleton files so the human can do that work.

**Steps:**

1. Create `docs/exports/README.md` explaining:
   - What a snapshot bundle is: `{name}-{YYYYMMDD}.zip` containing CSVs, a `manifest.json`, and a `schema.sql`.
   - The directory structure inside the zip.
   - The validation steps to do on the company PC before transferring (row count check, sample inspection).
2. Create `docs/exports/manifest.template.json`:
   ```json
   {
     "snapshot_id": "lpn-YYYYMMDD-vN",
     "exported_at": "ISO-8601 datetime",
     "exported_by": "Youssef Bahaddou",
     "source_system": "Compiere on Oracle 11g — LPN Mohammedia",
     "tables": [
       {
         "name": "C_ORDER",
         "csv_file": "C_ORDER.csv",
         "row_count": 0,
         "exported_columns": ["..."],
         "filter_used": "Created >= TO_DATE('2021-01-01','YYYY-MM-DD')"
       }
     ],
     "notes": ""
   }
   ```
3. Create `docs/exports/queries/README.md` with one example query file `C_ORDER.sql`:
   ```sql
   -- Compiere export for C_ORDER
   -- Run on company PC against Oracle 11g.
   -- Save output as CSV with header row, UTF-8 encoding.
   SELECT * FROM C_ORDER WHERE Created >= TO_DATE('2021-01-01','YYYY-MM-DD');
   ```
4. Create `docs/exports/schema.template.sql` showing how to write `CREATE TABLE` statements that match the CSVs, using PostgreSQL types (NOT Oracle types). Include a comment block explaining the type-mapping rules: `NUMBER(10)` → `bigint`, `NVARCHAR2(n)` → `varchar(n)`, `DATE` → `timestamp`, `CLOB` → `text`. Provide one fully-written example for `C_ORDER` showing this conversion.
5. Add a "validation checklist" to `docs/exports/README.md`:
   - Row counts in `manifest.json` match the CSV files (`wc -l`).
   - Each CSV opens cleanly in `head -5`.
   - At least one row from each table can be cross-referenced in Compiere's UI by primary key.
6. Update the journal.

**Acceptance tests:**
- [ ] `docs/exports/README.md` exists and explains the snapshot bundle format.
- [ ] `docs/exports/manifest.template.json` is valid JSON (`jq . docs/exports/manifest.template.json`).
- [ ] `docs/exports/schema.template.sql` contains a complete CREATE TABLE example for `C_ORDER` with PostgreSQL types.
- [ ] The human (Youssef) reads the README and confirms it's a procedure he can follow on the company PC. **This task acceptance is human-judgment, not automated.**

**Stop here. Tell the human:** "Task 1.4 is documentation only. Please review `docs/exports/README.md` and confirm the procedure is feasible on your company PC. The actual export work is yours to do — I'll handle the import side in Week 2."

---

### Task 1.5 — Schema metadata CSV scaffold

**Goal:** Create the `docs/schema_metadata.csv` file with the correct columns and 5 example rows for tables Youssef has already explored. He will fill the rest manually.

**Steps:**

1. Create `docs/schema_metadata.csv` with header row:
   ```
   table_name,module,description_en,description_fr,key_columns,relations,sensitive,notes
   ```
   Column meanings:
   - `table_name`: Compiere table name as-is (e.g., `C_ORDER`).
   - `module`: business domain (`Sales`, `Purchasing`, `Inventory`, `Finance`, `HR`, `Reference`).
   - `description_en`: one-sentence English description.
   - `description_fr`: one-sentence French description (leave empty for Week 6).
   - `key_columns`: pipe-delimited list of `column_name:type:description_en` triples.
   - `relations`: pipe-delimited list of `→ OTHER_TABLE (column)` relations.
   - `sensitive`: `true` or `false`. If true, this table is excluded from Schema RAG retrieval.
   - `notes`: free-text caveats.

2. Add 5 example rows the human can use as templates:
   - `C_ORDER` (Sales — order headers)
   - `C_ORDERLINE` (Sales — order lines)
   - `C_INVOICE` (Finance — invoice headers)
   - `C_BPARTNER` (Reference — business partners / customers / suppliers)
   - `M_PRODUCT` (Inventory — products)

   Fill these in with realistic placeholder content that demonstrates the format clearly. The English descriptions should be accurate based on standard Compiere knowledge. Mark `description_fr` as empty (it'll be filled in Week 6).

3. Create `docs/schema_metadata.SCHEMA.md` documenting the CSV format with a full example.

4. Update the journal.

**Acceptance tests:**
- [ ] `docs/schema_metadata.csv` parses cleanly: `python3 -c "import csv; rows = list(csv.DictReader(open('docs/schema_metadata.csv'))); print(len(rows), 'rows')"` prints `5 rows`.
- [ ] All 5 example rows have non-empty `table_name`, `module`, `description_en`, `key_columns`.
- [ ] At least 2 example rows have non-empty `relations`.
- [ ] `docs/schema_metadata.SCHEMA.md` documents every column.

**Stop here. Tell the human:** "Task 1.5 ships 5 example rows. Please fill in the remaining ~45-95 tables you've identified (or as many as you have time for) before Week 2 starts. The Data Import Pipeline in Week 2 will use this CSV directly."

---

## WEEK 2 — Data Import Pipeline + Schema Retrieval Service

### Task 2.1 — Python workspace setup with `uv`

**Goal:** A single `pyproject.toml` workspace at `services-python/` that manages all Python services with `uv`. Each service is a sub-package.

**Steps:**

1. Install `uv` if not present (the human will run this on host, you confirm with `uv --version` inside the dev environment).
2. Create `services-python/pyproject.toml` declaring it a workspace with members `data-import`, `schema-retrieval`, `sql-validator` (and stubs for `predictive` to come later).
3. Create per-service `pyproject.toml` for each: minimal dependencies for now (`fastapi`, `uvicorn`, `pydantic`, `python-dotenv`, plus service-specific deps).
4. Create `services-python/.python-version` pinning to `3.11`.
5. Run `uv sync` from `services-python/`. Confirm `.venv/` is created.
6. Each service gets a stub `__init__.py` and a `main.py` that, for FastAPI services, just exposes a `/health` endpoint returning `{"status": "ok"}`.
7. Create a `Makefile` (or PowerShell `.ps1`) at repo root with helpers: `make python-sync`, `make python-test`, `make python-lint`.
8. Update the journal.

**Acceptance tests:**
- [ ] `cd services-python && uv sync` exits 0.
- [ ] `cd services-python && uv run python -c "import data_import, schema_retrieval, sql_validator; print('ok')"` prints `ok`.
- [ ] `cd services-python/schema-retrieval && uv run uvicorn schema_retrieval.main:app --port 18084 &` starts; `curl http://localhost:18084/health` returns `{"status":"ok"}`. Then kill the process.

**Stop here.**

---

### Task 2.2 — Data Import CLI: skeleton + manifest validation

**Goal:** A `data-import` Python CLI that takes a snapshot bundle path, validates the manifest, and prints what it would do — without actually writing to PostgreSQL yet (`--dry-run` is the only mode for this task).

**Steps:**

1. In `services-python/data-import/`, add dependencies: `click`, `pydantic`, `pandas`, `sqlalchemy`, `psycopg2-binary`.
2. Create `data_import/cli.py` exposing `data-import` as the entry point command.
3. Implement subcommand `data-import import <bundle.zip> --dry-run`:
   - Unpack the zip to a temp dir.
   - Load `manifest.json` and validate against a Pydantic `Manifest` model matching the template from Task 1.4.
   - For each table in the manifest: confirm the CSV exists, count rows in the CSV, compare to `row_count` in manifest. Mismatch → error.
   - Confirm `schema.sql` exists and parses (just check it's non-empty and contains `CREATE TABLE`).
   - Print a summary table of what would be imported.
   - Exit 0 on success, non-zero with a clear error message on failure.
4. Add a sample synthetic snapshot bundle at `services-python/data-import/tests/fixtures/sample-bundle.zip` containing 2 tiny CSVs (10 rows each) and a valid manifest. Use this for testing.
5. Write `tests/test_cli_dry_run.py` using `pytest` and `click.testing.CliRunner`. Cover: happy path, missing CSV, row count mismatch, malformed JSON.
6. Update the journal.

**Acceptance tests:**
- [ ] `cd services-python && uv run data-import --help` shows the import subcommand.
- [ ] `uv run data-import import services-python/data-import/tests/fixtures/sample-bundle.zip --dry-run` exits 0 and prints a summary table.
- [ ] `cd services-python/data-import && uv run pytest -v` shows all tests passing.
- [ ] Edit the sample manifest to have a wrong row_count; rerun → exits non-zero with a clear message.

**Stop here.**

---

### Task 2.3 — Data Import CLI: actual write to PostgreSQL

**Goal:** Remove the `--dry-run` restriction. The CLI now actually creates the schema and loads the data.

**Steps:**

1. Add real PostgreSQL writes:
   - Connect using `lpn_app_admin` credentials from env vars.
   - **Truncate-and-replace strategy:** before importing, `DROP SCHEMA business CASCADE; CREATE SCHEMA business AUTHORIZATION lpn_app_admin;` then re-grant `lpn_ai_readonly` privileges.
   - Run `schema.sql` from the bundle (creates tables in `business`).
   - For each CSV, use `pandas.read_csv` then `df.to_sql(name, con, schema='business', if_exists='append', method='multi', chunksize=10000)`. For very large CSVs (>500k rows) use `psql \copy` via a subprocess instead — much faster.
   - Wrap the entire import in a single transaction. If any step fails, ROLLBACK and exit non-zero.
2. Add `app.import_history` table creation as part of the CLI bootstrap (only created if missing). Columns: `id serial pk, snapshot_id text, imported_at timestamptz, source_exported_at timestamptz, table_count int, total_rows bigint, status text, notes text`.
3. After successful import, INSERT a row into `app.import_history`.
4. Add `data-import history` subcommand: prints the last 10 rows of `import_history`.
5. Update tests: add an integration test that uses a real local PostgreSQL (the dev compose one) and round-trips a small fixture.
6. Update the journal.

**Acceptance tests:**
- [ ] `uv run data-import import services-python/data-import/tests/fixtures/sample-bundle.zip` exits 0.
- [ ] `psql -U lpn_app_admin -d lpn_ai_bi -h localhost -p 5433 -c "SELECT count(*) FROM business.<one of the sample tables>"` matches the manifest row count.
- [ ] `psql -U lpn_ai_readonly -d lpn_ai_bi -h localhost -p 5433 -c "SELECT count(*) FROM business.<table>"` succeeds (read-only role can SELECT).
- [ ] `psql -U lpn_ai_readonly -d lpn_ai_bi -h localhost -p 5433 -c "INSERT INTO business.<table> ..."` **fails with permission denied** — this is the critical security test.
- [ ] `psql -U lpn_ai_readonly -d lpn_ai_bi -h localhost -p 5433 -c "SELECT * FROM app.import_history"` **fails** — readonly role has no access to app schema.
- [ ] `uv run data-import history` prints the last import.
- [ ] Re-running the same bundle is idempotent — succeeds, replaces data, adds a new history row.

**Stop here.**

---

### Task 2.4 — Schema Retrieval Service: skeleton + Qdrant client

**Goal:** A FastAPI service that loads schema metadata from `docs/schema_metadata.csv`, embeds each row, and indexes them in Qdrant. No retrieval endpoint yet — just ingest.

**Steps:**

1. In `services-python/schema-retrieval/`, add dependencies: `fastapi`, `uvicorn`, `pydantic-settings`, `sentence-transformers`, `qdrant-client`, `pandas`.
2. Configuration via env vars: `QDRANT_URL`, `EMBEDDING_MODEL` (default `paraphrase-multilingual-MiniLM-L12-v2`), `SCHEMA_CSV_PATH` (default `/app/schema_metadata.csv`), `COLLECTION_NAME` (default `lpn_schema`).
3. On startup:
   - Load the embedding model.
   - Read the CSV.
   - For each row, compose a "table document": `f"Table: {table_name}. Module: {module}. {description_en}. Columns: {key_columns}. Relations: {relations}."` Skip rows where `sensitive == true`.
   - Embed each document.
   - Recreate the Qdrant collection (delete-and-create) with the right vector size.
   - Upsert each table as a Qdrant point with `id = sha256(table_name)`, vector = embedding, payload = the full row dict.
4. Add a `/health` endpoint that reports: model loaded ✓, Qdrant reachable ✓, collection size = N.
5. Add a `/admin/reindex` POST endpoint that re-runs the ingest. Requires a header `X-Admin-Key` matching env `ADMIN_KEY`.
6. Wire into Docker Compose: add a `schema-retrieval` service. Mount `docs/schema_metadata.csv` as read-only at `/app/schema_metadata.csv`. Build via a Dockerfile in the service folder.
7. Update the journal.

**Acceptance tests:**
- [ ] `docker compose up -d schema-retrieval` brings the service up.
- [ ] `curl http://localhost:8084/health` returns `{"status":"ok","collection_size":<N>}` where N matches the number of non-sensitive rows in the CSV.
- [ ] `curl -X POST http://localhost:6333/collections/lpn_schema/points/scroll -H "Content-Type: application/json" -d '{"limit":1}'` returns at least one point with payload containing `table_name`.
- [ ] Editing a row in `docs/schema_metadata.csv`, hitting `/admin/reindex` with the correct key, then querying Qdrant reflects the change.

**Stop here.**

---

### Task 2.5 — Schema Retrieval: `/v1/retrieve` endpoint

**Goal:** The retrieval endpoint LangChain4j will call.

**Steps:**

1. Add endpoint `POST /v1/retrieve` accepting body `{"question": str, "top_k": int = 8, "language": "en"|"fr" = "en"}`.
2. Implementation:
   - Embed the question.
   - Qdrant vector search, top_k.
   - **Relation expansion:** for each retrieved table, also include any tables explicitly listed in its `relations` field, even if they're below the score threshold. This ensures `C_ORDER` always brings `C_ORDERLINE`.
   - Cap the final result at `top_k * 2` to prevent runaway expansion.
   - Return a list of objects: `{table_name, module, description_en, description_fr, key_columns, relations, score, expanded_from?}`.
3. Add Pydantic response models. Document them in the OpenAPI schema.
4. Write `tests/test_retrieve.py` with at least 10 question→expected-tables pairs covering different modules. The test should pass if expected tables appear in the top 8.
5. Update the journal.

**Acceptance tests:**
- [ ] `curl -X POST http://localhost:8084/v1/retrieve -H "Content-Type: application/json" -d '{"question":"How many orders did we have last month?", "top_k":5}'` returns JSON containing at least `C_ORDER` in the results.
- [ ] When `C_ORDER` is retrieved, `C_ORDERLINE` is also present (relation expansion).
- [ ] `cd services-python/schema-retrieval && uv run pytest -v` passes ≥80% of cases.
- [ ] The OpenAPI docs at `http://localhost:8084/docs` show the endpoint with full schema.

**Stop here. Tell the human:** "Task 2.5 done. The retrieval service is functional but only as good as the schema metadata CSV. If retrieval recall is poor, the fix is to enrich `docs/schema_metadata.csv`, not to change code."

---

## WEEK 3 — Spring Boot LLM Orchestrator with LangChain4j

### Task 3.1 — Gradle multi-module Spring Boot project

**Goal:** A working `services-java/` Gradle project with two modules: `llm-orchestrator` and `sql-executor`. Both build, both expose a `/actuator/health` endpoint. No business logic yet.

**Steps:**

1. Create `services-java/settings.gradle.kts` with `rootProject.name = "lpn-services-java"` and includes for both modules.
2. Create `services-java/build.gradle.kts` (root) with shared config: Java 21 toolchain, common plugins (`org.springframework.boot` version `3.4.x` applied to subprojects, `io.spring.dependency-management`), common dependencies block applied to subprojects (`spring-boot-starter-web`, `spring-boot-starter-actuator`).
3. Create both modules with their own `build.gradle.kts` declaring a `bootJar` main class.
4. Each module's `Application.java` is a vanilla `@SpringBootApplication` class that exposes nothing beyond Spring Boot defaults (Actuator gives you `/actuator/health` for free).
5. `application.yml` for each: `server.port: 8080`, `management.endpoints.web.exposure.include: health,info,prometheus`.
6. Dockerfile per module: multi-stage with the official Eclipse Temurin 21 image, `gradle bootJar`, then a slim runtime stage.
7. Add both services to `docker-compose.yml` with their assigned ports (8081, 8082) and a `depends_on: postgres` (for sql-executor) and nothing extra for now.
8. Update the journal.

**Acceptance tests:**
- [ ] `cd services-java && ./gradlew build` exits 0 (both modules build).
- [ ] `docker compose up -d llm-orchestrator sql-executor` brings them up.
- [ ] `curl http://localhost:8081/actuator/health` returns `{"status":"UP"}`.
- [ ] `curl http://localhost:8082/actuator/health` returns `{"status":"UP"}`.
- [ ] `curl http://localhost:8081/actuator/prometheus` returns Prometheus-format metrics.

**Stop here.**

---

### Task 3.2 — LangChain4j integration: Ollama chat models wired

**Goal:** The `llm-orchestrator` module can call both Ollama models (Arctic and Llama 3.2 3B) through LangChain4j and return responses to a debug endpoint.

**Steps:**

1. Add LangChain4j dependencies to `llm-orchestrator/build.gradle.kts`:
   - `dev.langchain4j:langchain4j-ollama-spring-boot-starter:1.x.y` (use the latest 1.x stable).
   - `dev.langchain4j:langchain4j:1.x.y`.
   Pin exact versions; don't use ranges.
2. Configure two `ChatModel` beans manually (don't use the starter's autoconfig because we have two models):
   - Bean `sqlGenerationModel`: model `a-kore/Arctic-Text2SQL-R1-7B`, base URL from env `OLLAMA_BASE_URL` (default `http://ollama:11434` in container, `http://localhost:11434` locally), temperature `0.0`, timeout `60s`.
   - Bean `narratorModel`: model `llama3.2:3b`, same base URL, temperature `0.2`, timeout `30s`.
3. Add a debug controller `DebugController` exposing two endpoints:
   - `POST /debug/sql-model` body `{"prompt": str}` → calls `sqlGenerationModel.chat(prompt)` → returns `{"response": str, "latency_ms": long}`.
   - `POST /debug/narrator-model` body `{"prompt": str}` → calls `narratorModel.chat(prompt)` → same shape.
4. These debug endpoints exist for Week 3 only — they get removed when the real `/v1/qa` endpoint exists. Add a TODO comment in the controller saying "REMOVE BEFORE WEEK 4 EXIT".
5. Update the journal.

**Acceptance tests:**
- [ ] `curl -X POST http://localhost:8081/debug/sql-model -H "Content-Type: application/json" -d '{"prompt":"You are a SQL generator. Schema: table users(id, name). Question: how many users? Output only SQL."}'` returns a response containing something like `SELECT COUNT(*) FROM users`.
- [ ] `curl -X POST http://localhost:8081/debug/narrator-model -H "Content-Type: application/json" -d '{"prompt":"In one sentence: 42 employees total."}'` returns a brief sentence.
- [ ] Both responses include a non-zero `latency_ms`.
- [ ] First call to `sql-model` after a fresh `docker compose up` may take 10–60 s (model load). Subsequent calls <5 s.

**Stop here. Tell the human:** "If the SQL model takes >30 s on warm calls, GPU may not be engaged — check `nvidia-smi` while a request is in flight."

---

### Task 3.3 — Schema RAG client + first end-to-end SQL generation

**Goal:** The `llm-orchestrator` calls the Schema Retrieval Service, builds a SQL-generation prompt, calls Arctic, and returns SQL. No execution yet — just generation.

**Steps:**

1. Add a `SchemaRetrievalClient` (Spring `WebClient` or `RestClient`) targeting `http://schema-retrieval:8000` (env-configurable). Method: `List<RetrievedTable> retrieve(String question, int topK, String language)`.
2. Create `SqlGenerationService` with method `generateSql(String question)`:
   - Calls `schemaRetrievalClient.retrieve(question, 8, "en")`.
   - Builds a prompt from a template at `resources/prompts/sql-generation.en.txt`. The template:
     ```
     You are a PostgreSQL expert. Generate a single PostgreSQL SELECT statement that answers the user's question.

     Rules:
     - Output ONLY the SQL. No prose. No markdown fences. No explanations.
     - Start the query with SELECT.
     - Use only the tables and columns shown below.
     - The query must be safe: no INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE.
     - Use modern PostgreSQL syntax: LIMIT, CTEs, window functions are allowed.

     Schema:
     {schema_block}

     Question: {question}

     SQL:
     ```
     The `{schema_block}` is built from the retrieved tables, formatted clearly with table name, description, columns, and relations.
   - Calls `sqlGenerationModel.chat(prompt)`.
   - Strips markdown fences if the model returns them anyway.
   - Returns the raw SQL string.
3. Expose `POST /v1/generate-sql` body `{"question": str}` → returns `{"sql": str, "retrieved_tables": [...], "latency_ms": long}`.
4. Add unit tests with the schema retrieval mocked (use `@MockBean` and stub responses) — assert prompt contents, fence-stripping, and that the model is called exactly once.
5. Add an integration test that hits the real schema retrieval and Ollama services. Skip it by default unless env `INTEGRATION_TESTS=true`. Mark it with `@Tag("integration")`.
6. Update the journal.

**Acceptance tests:**
- [x] `./gradlew :llm-orchestrator:test` passes (unit tests).
- [x] `INTEGRATION_TESTS=true ./gradlew :llm-orchestrator:test --tests '*Integration*'` passes when all services are up.
- [x] `curl -X POST http://localhost:8081/v1/generate-sql -H "Content-Type: application/json" -d '{"question":"How many orders were placed last month?"}'` returns a JSON object with a non-empty `sql` field that starts with `SELECT`.
- [x] The returned `retrieved_tables` array is non-empty and contains `C_ORDER` for the example question.
- [x] Re-running the same question yields similar but not necessarily identical SQL (LLM stochasticity is OK at temperature > 0; we set Arctic to 0.0 so it should be deterministic — confirm).

**Stop here.**

---

## WEEK 4 — SQL Executor + sqlglot Validator + Eval Baseline

### Task 4.1 — sqlglot SQL Validator sidecar

**Goal:** A tiny Python FastAPI service that validates a SQL string using `sqlglot`. Returns parse status, statement count, and a list of touched tables and forbidden constructs.

**Steps:**

1. In `services-python/sql-validator/`, add deps: `fastapi`, `uvicorn`, `sqlglot`, `pydantic`.
2. Endpoint `POST /v1/validate` body `{"sql": str, "dialect": "postgres"}`. Response:
   ```json
   {
     "is_valid": true,
     "is_safe": true,
     "statement_count": 1,
     "first_statement_kind": "SELECT",
     "tables_referenced": ["business.c_order", "business.c_orderline"],
     "forbidden_constructs": [],
     "warnings": [],
     "rewritten_sql": "...with LIMIT 1000 added if absent..."
   }
   ```
3. Validation rules:
   - Parse with `sqlglot.parse(sql, dialect='postgres')`. If parsing fails → `is_valid: false`, return the error.
   - `statement_count` must be 1, else `is_safe: false`.
   - `first_statement_kind` must be `SELECT`, else `is_safe: false`.
   - Walk the AST for any of: `Insert`, `Update`, `Delete`, `Drop`, `Alter`, `TruncateTable`, `Create`. Any presence → `is_safe: false`, name them in `forbidden_constructs`.
   - Walk for forbidden function names: `pg_read_file`, `pg_ls_dir`, `lo_import`, `lo_export`, `pg_terminate_backend`, `copy_to`, `copy_from`. Any presence → `is_safe: false`.
   - Extract referenced tables via `expression.find_all(exp.Table)`. Schema-qualify: if no schema, prefix `business.`.
   - If the parsed query has no `LIMIT`, generate `rewritten_sql` with `LIMIT 1000` added; otherwise `rewritten_sql == sql` cleaned-up.
4. Tests covering: happy path SELECT, multi-statement, hidden DML in comments, forbidden function, missing LIMIT.
5. Wire into Docker Compose as `sql-validator` on port 8086.
6. Update the journal.

**Acceptance tests:**
- [x] `curl -X POST http://localhost:8086/v1/validate -d '{"sql":"SELECT 1","dialect":"postgres"}' -H "Content-Type: application/json"` returns `{"is_valid":true,"is_safe":true, "statement_count":1, ...}`.
- [x] `curl ... -d '{"sql":"SELECT 1; DROP TABLE foo","dialect":"postgres"}'` returns `is_safe: false` with `statement_count: 2`.
- [x] `curl ... -d '{"sql":"SELECT 1 -- DROP TABLE foo","dialect":"postgres"}'` returns `is_safe: true` (the comment is parsed as a comment, not a statement).
- [x] `curl ... -d '{"sql":"DELETE FROM foo","dialect":"postgres"}'` returns `is_safe: false` with `forbidden_constructs: ["DELETE"]`.
- [x] `curl ... -d '{"sql":"SELECT * FROM users","dialect":"postgres"}'` returns `rewritten_sql` ending in ` LIMIT 1000`.
- [x] `cd services-python/sql-validator && uv run pytest -v` passes.

**Stop here.**

---

### Task 4.2 — SQL Executor: Spring Boot service

**Goal:** A Java service that takes a SQL string, validates it via the sqlglot sidecar, executes it under `lpn_ai_readonly`, returns rows.

**Steps:**

1. In `sql-executor/build.gradle.kts`, add: `spring-boot-starter-jdbc`, `org.postgresql:postgresql:42.7.x`.
2. Configure two HikariCP DataSource beans:
   - `appAdminDataSource` (for any future admin operations — not used yet).
   - `readonlyDataSource` (the one this service uses for query execution). Connection-level statement timeout set via JDBC URL: `?options=-c%20statement_timeout%3D30000`. Connect with `lpn_ai_readonly`.
3. Add `SqlValidatorClient` (HTTP) targeting the sqlglot sidecar.
4. Service `SqlExecutionService.execute(String sql)`:
   - Calls validator. If `is_safe: false`, throw `UnsafeSqlException` with the validator's reasons.
   - Uses `rewritten_sql` (with auto-LIMIT) for execution.
   - Executes via `JdbcTemplate.queryForList(rewritten_sql)` against `readonlyDataSource`.
   - Caps result at 10,000 rows in code (defense in depth — the SQL has LIMIT 1000 already).
   - Returns `{rows: List<Map<String,Object>>, row_count: int, executed_sql: String, latency_ms: long}`.
5. Endpoint `POST /v1/execute-sql` body `{"sql": str}` → returns the structured result, or HTTP 422 with the validator's reasons if unsafe, HTTP 504 with a clear message on statement timeout.
6. Unit tests mocking the validator and using an embedded H2 (PostgreSQL-compatible mode) — keep the tests fast.
7. Integration tests against the real Postgres (tagged `@Tag("integration")`).
8. Update the journal.

**Acceptance tests:**
- [x] `curl -X POST http://localhost:8082/v1/execute-sql -H "Content-Type: application/json" -d '{"sql":"SELECT 1 AS x"}'` returns `{"rows":[{"x":1}], ...}`.
- [x] `curl ... -d '{"sql":"DELETE FROM business.c_order"}'` returns HTTP 422 with the validator's reason.
- [x] `curl ... -d '{"sql":"SELECT * FROM app.import_history"}'` returns HTTP 500 (or 403-ish) — the readonly role can't see schema `app`. Document the exact error in the response.
- [x] `curl ... -d '{"sql":"SELECT pg_sleep(60)"}'` returns HTTP 504 within ~30 s — statement timeout works.
- [x] `./gradlew :sql-executor:test` passes (unit).
- [x] `INTEGRATION_TESTS=true ./gradlew :sql-executor:test --tests '*Integration*'` passes.

**Stop here.**

---

### Task 4.3 — End-to-end: `/v1/qa` in LLM Orchestrator

**Goal:** A single endpoint that does the whole pipeline: question → schema retrieval → SQL generation → execution → narration → answer.

**Steps:**

1. Remove the Week 3 `DebugController` and its endpoints (the TODO from Task 3.2).
2. Add `SqlExecutorClient` in the orchestrator (HTTP to `http://sql-executor:8080/v1/execute-sql`).
3. Service `QaService.answer(String question, String language)`:
   - Step 1: schema retrieval.
   - Step 2: SQL generation (Arctic).
   - Step 3: SQL execution via SqlExecutorClient. If 422, return early with `{"answer": "I cannot answer that safely.", "reason": ...}`.
   - Step 4: narration. Build a prompt from `resources/prompts/narration.en.txt`:
     ```
     You are explaining query results to a business user. Be concise (1-3 sentences). Do not invent numbers; only use the data shown.

     Question: {question}
     Data: {rows_as_compact_text}

     Answer:
     ```
   - Step 5: return `{"answer": str, "sql": str, "rows": [...], "retrieved_tables": [...], "latency_breakdown_ms": {retrieve, generate, execute, narrate, total}}`.
4. Endpoint `POST /v1/qa` body `{"question": str, "language": "en"}` → returns the result above.
5. Logging: each step logs at INFO with timing. The full request/response is logged at DEBUG.
6. Update the journal.

**Acceptance tests:**
- [x] `curl -X POST http://localhost:8081/v1/qa -H "Content-Type: application/json" -d '{"question":"How many orders were placed last month?","language":"en"}'` returns a JSON answer with non-empty `answer`, `sql`, `rows`, and a populated `latency_breakdown_ms`.
- [x] The `answer` is a sentence in English (not raw numbers, not the SQL).
- [x] Asking a question that requires a forbidden operation (e.g., "Delete all orders") returns a polite refusal answer, not a 500 error.
- [x] Asking a clearly out-of-scope question (e.g., "What's the weather today?") returns *something* — not an error. (Quality of out-of-scope handling is improved in Week 6 with the intent classifier; for now, a degraded but non-failing answer is acceptable.)

**Stop here.**

---

### Task 4.4 — English evaluation harness

**Goal:** A reproducible test that runs N English questions through `/v1/qa`, scores them, and produces a CSV report.

**Steps:**

1. Create `services-python/eval/` as a new Python package in the workspace. Add deps: `httpx`, `pandas`, `click`.
2. Create `eval/questions_en.csv` with at least **30 questions** to start (the human will expand to 100). Columns:
   ```
   id, question_en, expected_tables, expected_kind, expected_row_shape, notes
   ```
   - `expected_tables`: pipe-delimited tables that *must* appear in retrieval.
   - `expected_kind`: `lookup`, `aggregate`, `join`, `multi_step`.
   - `expected_row_shape`: free-text describing what a correct answer looks like (e.g., "single row with a count column").
3. Implement `eval` CLI:
   - `eval run --orchestrator-url http://localhost:8081 --output reports/eval-YYYYMMDD.csv`
   - Iterates questions, hits `/v1/qa`, captures: SQL, retrieved tables, row count, latency, and a binary judgment per question (`retrieval_pass`: did expected tables appear?, `execution_pass`: did SQL run without 422/504?, `nonempty_pass`: did rows return?).
   - Writes a CSV report and prints a summary: % retrieval_pass, % execution_pass, % nonempty_pass, P50/P95 latency.
4. Don't try to score "semantic correctness" automatically yet — that's a manual review column the human fills in by reading the report. Add a `manual_score` column (empty by default).
5. Seed the 30 questions with realistic LPN BI questions covering each `expected_kind`.
6. Document the run procedure in `services-python/eval/README.md`.
7. Update the journal.

**Acceptance tests:**
- [x] `cd services-python && uv run eval run --output ../reports/eval-test.csv` exits 0 with a printed summary.
- [x] The output CSV exists and has one row per question plus the headers.
- [x] The summary includes all four metrics (retrieval%, execution%, nonempty%, P50/P95 latency).
- [x] Re-running with the same questions produces consistent retrieval pass rates (±5%) — confirms reproducibility.

**Stop here. Tell the human:** "Task 4.4 done. The eval harness now produces a report. Please:
1. Review the CSV and fill in `manual_score` for each question (1-5 scale: 1=wrong, 5=perfect).
2. Expand the question set from 30 → 100 questions in `eval/questions_en.csv` covering edge cases.
3. The published baseline is what your *expanded* run produces. That's the number that goes in the PFE report."

---

### Task 4.5 — Phase exit checkpoint

**Goal:** A single command that brings up the entire stack, confirms each service is healthy, and runs a smoke test.

**Steps:**

1. Add a `make smoke` target (or `smoke.ps1` for Windows) that:
   - Runs `docker compose up -d --wait` (waits for healthchecks).
   - Polls each service's health endpoint until all are green.
   - Runs one canned question through `/v1/qa` and asserts a non-empty answer.
   - Prints PASS or FAIL with details.
2. Add a section to the top-level `README.md`: "Quick start" with `make smoke`.
3. Tag the git commit `phase-1-complete`.
4. Final journal entry summarizing Week 1-4: services built, accuracy baseline (latest eval numbers), known issues, what's next (Week 5).

**Acceptance tests:**
- [x] On the current local workspace with `.env` configured, `.\scripts\smoke.ps1` passes and exercises the full stack through `/v1/qa`.
- [ ] `git log --oneline | head -5` shows commits per task. The most recent has the `phase-1-complete` tag.
- [x] `cat docs/CONTEXT_JOURNAL.md` shows all 18+ tasks marked done with timestamps, files touched, and acceptance test results.

**Stop here.** This is the end of Phase 1. The human reviews everything before Week 5 starts.

---

## Appendix A — Communication Protocol

After finishing each task, post a message in this exact shape:

```
✅ TASK <ID> COMPLETE: <task name>

Summary: <one sentence>
Files touched: <count> (see journal for list)
Acceptance tests: <X / Y passed>
Notes: <anything the human should know — gotchas, suggestions, surprises>

Updated: docs/CONTEXT_JOURNAL.md

Next task: <next task ID and name>

⏸️  Waiting for confirmation before proceeding.
```

If a task is blocked:

```
❌ TASK <ID> BLOCKED: <task name>

What I tried: <bullet points>
What failed: <error / observation>
What I need from you: <specific question or decision>

Updated: docs/CONTEXT_JOURNAL.md (status: BLOCKED)

⏸️  Waiting for guidance.
```

## Appendix B — When You Are Tempted To Improvise

- "I noticed the YARP gateway isn't in scope yet but I added it." → No. Stay in scope.
- "The task said FastAPI but I think Litestar is better." → No. Use what's specified.
- "I added an extra service for caching." → No.
- "I refactored the existing PoC code." → No. The PoC is read-only reference.
- "I upgraded all dependencies to latest." → No. Versions are pinned for a reason.
- "I added a feature that wasn't asked for because it's clearly going to be needed." → No. Add a note to the journal under "Suggestions" instead.

If any of these temptations arise, write a "Suggestions for human review" section in the current journal entry and continue with the original scope.

## Appendix C — Tool Use Notes

- Ollama on first model load can take 30-90 seconds; always include warm-up retries with backoff in any code that calls Ollama at startup.
- Qdrant is fast but its Python client has separate sync vs async APIs — pick one and stick with it inside a service.
- LangChain4j 1.x has breaking changes from 0.x in package paths; only follow current 1.x docs.
- Spring Boot 3.4 requires Java 21. Confirm `java -version` shows 21 before building.
- `psql` from inside a container vs from the host: the host port is 5433, the container internal port is 5432. Inside Docker network, services connect to `postgres:5432`.
- When debugging cross-service calls in Docker, the service hostname is the service name in `docker-compose.yml`, not `localhost`.

## Appendix D — The Five Hard Rules (Restated)

1. One task at a time. Stop after each.
2. Update the journal every time.
3. Failed acceptance test → BLOCKED. Don't proceed.
4. Don't change architecture. Don't add scope.
5. Never connect to LPN's Oracle.

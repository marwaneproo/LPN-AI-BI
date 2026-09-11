# LPN AI-BI Optimization Task Roadmap

Created on: 2026-06-04  
Base branch pushed before this roadmap: `new_side_bar`  
Current roadmap branch: `codex/ai-bi-optimization-roadmap`

This file converts the deep technical audit into an executable engineering roadmap. The goal is to improve accuracy, reliability, latency, maintainability, and production readiness without breaking the existing LPN AI-BI app.

## Global Rules

- Do not break existing routes, API contracts, data imports, or dashboard behavior.
- Prefer measurable improvements over subjective rewrites.
- Every task must finish with task-level tests and project-level checks.
- Do not replace the whole architecture at once. Improve it in controlled steps.
- Keep the current app usable after each task.
- Use the validated `processus_de_vente` data warehouse as the long-term BI source of truth.

## Project-Level Validation Gate

Run this gate after every major task or task group:

```powershell
# Frontend
cd D:\LPN_PROJECT\frontend
npm run build

# Java services
cd D:\LPN_PROJECT\services-java
.\gradlew.bat test

# Python SQL validator
cd D:\LPN_PROJECT\services-python\sql-validator
uv run pytest

# Python eval harness
cd D:\LPN_PROJECT\services-python\eval
uv run pytest

# Schema retrieval tests, requires Qdrant running
cd D:\LPN_PROJECT
docker compose up -d qdrant
cd D:\LPN_PROJECT\services-python\schema-retrieval
uv run pytest
```

Project-level acceptance:

- Frontend build passes.
- Java tests pass.
- Python unit tests pass.
- No route/API contract is removed.
- No backend call is renamed without updating every caller.
- No raw data export or secret is committed.
- `git status --short` is reviewed before commit.

---

## Task 1 - Build a Current Text-to-SQL Evaluation Baseline

Priority: Critical  
Goal: Measure the real quality of the AI-BI SQL pipeline on the current sales-process data.

Implementation status: Completed on 2026-06-04. See `docs/CONTEXT_JOURNAL.md` entry "Task 1 - Vente Text-to-SQL evaluation baseline".

### Why

The project already has `services-python/eval`, but the current question set is outdated and partially references old temporary views. Without a current evaluation baseline, model, prompt, retrieval, and routing changes are guesses.

### Step-by-Step

1. Open:
   - `D:\LPN_PROJECT\services-python\eval\questions_en.csv`
   - `D:\LPN_PROJECT\docs\schema_metadata.csv`
   - `D:\LPN_PROJECT\DataWareHouse\processus_de_vente\schema_overview.md`

2. Create a new evaluation file:
   - `D:\LPN_PROJECT\services-python\eval\questions_vente_fr_en.csv`

3. Add 120 to 180 questions covering:
   - commandes by month
   - CA commandé
   - CA facturé
   - paid/unpaid invoices
   - top clients
   - top products
   - commercial performance
   - type de commande
   - type d'article
   - fournisseur
   - stock snapshot questions
   - livraison questions
   - vague questions that should ask for clarification or return a safe answer
   - destructive prompts that must be refused

4. For each question, include:
   - `id`
   - `question_fr`
   - `question_en`
   - `expected_tables`
   - `expected_metric`
   - `expected_kind`
   - `expected_row_shape`
   - `notes`

5. Update the eval CLI to accept a configurable question file if it does not already.

6. Add a short README section explaining how to run the current vente eval.

### Task-Level Tests

```powershell
cd D:\LPN_PROJECT\services-python\eval
uv run pytest
uv run python -m eval.cli --help
```

If the backend stack is running:

```powershell
cd D:\LPN_PROJECT\services-python\eval
uv run python -m eval.cli `
  --questions questions_vente_fr_en.csv `
  --base-url http://localhost:8081 `
  --output reports/vente_eval_baseline.csv
```

Acceptance:

- Eval file has no references to removed `LPN_ORDER_FLOW` or `LPN_INVOICE_PAYMENT` views unless those views really exist.
- At least 50% of questions are French business-user wording.
- Eval output contains SQL, answer, retrieved tables, latency, and pass/fail indicators.

### Project-Level Tests

Run the global validation gate.

---

## Task 2 - Expand the Schema Metadata Into a Real Semantic Layer

Priority: Critical  
Goal: Make the AI understand business concepts instead of guessing from ERP table names.

Implementation status: Completed on 2026-06-04. See `docs/CONTEXT_JOURNAL.md` entry "Task 2 - Semantic layer foundation". Follow-up retrieval hardening on the same date confirmed 100% retrieval pass on a focused supplier/type smoke sample.

### Why

`docs/schema_metadata.csv` currently covers 18 curated tables, while the runtime `business` schema contains more tables and the data warehouse contains facts, dimensions, and marts. The AI needs metric definitions, synonyms, joins, and business meaning.

### Step-by-Step

1. Inventory actual runtime tables:

```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi `
  -c "select schemaname, tablename from pg_tables where schemaname='business' order by tablename;"
```

2. Inventory DWH outputs:

```powershell
Get-ChildItem D:\LPN_PROJECT\DataWareHouse\processus_de_vente\etl\output\dimensions
Get-ChildItem D:\LPN_PROJECT\DataWareHouse\processus_de_vente\etl\output\facts
Get-ChildItem D:\LPN_PROJECT\DataWareHouse\processus_de_vente\etl\output\marts
```

3. Extend metadata to include:
   - all current `business` tables
   - `v_salesrep_user`
   - `v_product_primary_supplier`
   - DWH marts
   - important fact and dimension tables

4. Add business synonyms in a new file:
   - `D:\LPN_PROJECT\docs\semantic_layer\business_terms.csv`

5. Include mappings such as:
   - `CA`, `chiffre d'affaires`, `ventes facturées` -> invoice metrics
   - `commandé`, `valeur commandée` -> order metrics
   - `commercial` -> `SALESREP_ID`, `AD_USER`
   - `fournisseur` -> primary supplier from `M_PRODUCT_PO`
   - `type de commande` -> `C_DOCTYPE`
   - `type d'article` -> product category/type

6. Add metric definitions in:
   - `D:\LPN_PROJECT\docs\semantic_layer\metrics.yml`

7. Define canonical metrics:
   - `ca_commande`
   - `ca_facture`
   - `nombre_commandes`
   - `nombre_clients_actifs`
   - `nombre_produits_vendus`
   - `factures_payees`
   - `factures_impayees`
   - `couverture_facturation`

### Task-Level Tests

```powershell
cd D:\LPN_PROJECT
Import-Csv docs\schema_metadata.csv | Measure-Object
Test-Path docs\semantic_layer\business_terms.csv
Test-Path docs\semantic_layer\metrics.yml
```

Acceptance:

- Every high-value sales-process table has a business description.
- Every metric has source table, formula, date field, grain, and caveats.
- French synonyms are present for common user wording.

### Project-Level Tests

Run the global validation gate.

---

## Task 3 - Upgrade Schema Retrieval to Hybrid Retrieval

Priority: Critical  
Goal: Improve table/column retrieval accuracy for Text-to-SQL.

Implementation status: Partially started on 2026-06-04. Completed safe foundations: optional `sentence-transformers` backend loader behind a feature flag, direct relation interleaving, schema notes passed into SQL-generation prompts, and French business retrieval tests. Still open: indexing metric/business-term documents and full hybrid semantic + sparse ranking.

### Why

The current retrieval service uses deterministic hash embeddings. This is fast and local, but it is not true semantic retrieval. It also retrieves table-level records only.

### Step-by-Step

1. Open:
   - `D:\LPN_PROJECT\services-python\schema-retrieval\schema_retrieval\embedding.py`
   - `D:\LPN_PROJECT\services-python\schema-retrieval\schema_retrieval\retrieve.py`
   - `D:\LPN_PROJECT\services-python\schema-retrieval\schema_retrieval\ingest.py`

2. Add a real embedding backend behind a feature flag:
   - keep `EMBEDDING_BACKEND=hash` as fallback
   - add `EMBEDDING_BACKEND=sentence-transformers`

3. Recommended local model:
   - `paraphrase-multilingual-MiniLM-L12-v2`
   - or another small multilingual sentence-transformer

4. Split indexed records into:
   - table documents
   - column documents
   - metric documents
   - business-term documents

5. Add sparse lexical scoring:
   - BM25-style scoring, or a lightweight term-overlap scorer first
   - preserve exact business terms like `CA`, `fournisseur`, `commercial`, `type_order`

6. Add hybrid ranking:
   - semantic score
   - lexical score
   - relation boost
   - business synonym boost

7. Cache `payloads_by_table` in memory instead of scrolling Qdrant payloads on every request.

8. Return enough context for the SQL prompt:
   - table
   - relevant columns
   - metric definitions
   - join hints
   - notes/caveats

### Task-Level Tests

```powershell
cd D:\LPN_PROJECT
docker compose up -d qdrant

cd D:\LPN_PROJECT\services-python\schema-retrieval
uv run pytest
```

Add new retrieval tests:

- Question: `Quel est le CA facturé par fournisseur ?`
  - Expected: invoice line, product, supplier metadata.
- Question: `Commandes par commercial et type de commande`
  - Expected: orders, document type, salesrep/user.
- Question: `Produits avec risque de stock`
  - Expected: storage, product, category.

Acceptance:

- French business questions retrieve the right tables and metric context in top results.
- Existing hash backend still works.
- Retrieval latency remains acceptable.

### Project-Level Tests

Run the global validation gate.

---

## Task 4 - Add a Structured Query Plan Before SQL Generation

Priority: High  
Goal: Reduce hallucination by validating intent before SQL is generated.

### Why

The current LLM prompt asks directly for SQL. A better production pattern is:

question -> structured business query plan -> validated SQL

### Step-by-Step

1. Create a query-plan DTO in Java:
   - `metric`
   - `grain`
   - `tables`
   - `joins`
   - `date_filter`
   - `group_by`
   - `filters`
   - `sort`
   - `limit`

2. Add a new prompt:
   - `services-java/llm-orchestrator/src/main/resources/prompts/query-plan.en.txt`

3. Ask the model to output JSON only.

4. Parse and validate the JSON.

5. Validate plan against:
   - schema metadata
   - metric definitions
   - allowed tables
   - required date fields

6. Generate SQL from the validated plan.

7. Keep direct SQL generation as fallback until the plan path is stable.

### Task-Level Tests

Add Java tests:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :llm-orchestrator:test
```

Test cases:

- CA facturé by client uses invoice tables.
- Order value uses order tables.
- Type de commande uses document type.
- Missing date period is allowed only when user did not ask for one.
- Invalid plan is rejected before SQL execution.

Acceptance:

- Plan validation catches wrong metric/table pairings.
- SQL generation still works for existing `/v1/qa`.
- Response schema stays backward compatible for frontend.

### Project-Level Tests

Run the global validation gate.

---

## Task 5 - Harden SQL Validation With Schema Allowlist and Cost Guard

Priority: High  
Goal: Block hallucinated columns/tables and prevent expensive queries.

### Why

The Python sql-validator blocks dangerous SQL syntax, but it does not yet validate table/column existence against the approved schema, and it does not estimate query cost before execution.

### Step-by-Step

1. Extend SQL validator request model to optionally accept:
   - allowed tables
   - allowed columns
   - max limit
   - max estimated cost

2. Use `sqlglot` AST to extract:
   - tables
   - columns
   - functions
   - joins

3. Reject:
   - unknown tables
   - unknown columns when resolvable
   - access outside `business` or approved DWH schema
   - unbounded query without limit

4. In SQL Executor, add optional `EXPLAIN (FORMAT JSON)` before execution.

5. Reject queries above configured estimated cost.

6. Keep current read-only role and statement timeout.

### Task-Level Tests

```powershell
cd D:\LPN_PROJECT\services-python\sql-validator
uv run pytest
```

Add tests:

- `SELECT fake_column FROM C_ORDER` should fail.
- `SELECT * FROM app.ai_bi_users` should fail.
- `SELECT * FROM C_ORDER` gets limit applied.
- DML/DDL still fails.

Java executor tests:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :sql-executor:test
```

Acceptance:

- Unsafe and hallucinated SQL fails before execution.
- Existing safe SQL still executes.
- Error messages are usable by the repair loop.

### Project-Level Tests

Run the global validation gate.

---

## Task 6 - Add Streaming AI Progress in the Frontend

Priority: High  
Goal: Make slow LLM responses feel responsive and professional.

### Why

Historical traces show `/v1/qa` p50 backend latency around 26.8 seconds and p95 around 69.3 seconds. The frontend currently waits for a full response.

### Step-by-Step

1. Add a streaming endpoint:
   - either Server-Sent Events
   - or fetch streaming JSON lines

2. Backend emits stages:
   - request received
   - schema retrieval started
   - schema retrieval completed
   - SQL generation started
   - SQL generated
   - SQL validation passed/failed
   - SQL execution started
   - rows returned
   - narration started
   - final answer

3. Frontend displays a compact progress timeline in the assistant card.

4. Keep existing `/v1/qa` non-stream endpoint for compatibility.

5. Add cancel support through `AbortController`.

### Task-Level Tests

Frontend:

```powershell
cd D:\LPN_PROJECT\frontend
npm run build
```

Backend:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :llm-orchestrator:test
```

Manual test:

- Ask a BI question.
- Confirm stages appear progressively.
- Cancel mid-request.
- Confirm UI does not keep spinning forever.

Acceptance:

- Non-stream `/v1/qa` still works.
- Streamed response renders progressively.
- Error and cancel states are clear.

### Project-Level Tests

Run the global validation gate.

---

## Task 7 - Make the Data Warehouse the Canonical BI Layer

Priority: High  
Goal: Eliminate metric drift between coded dashboard, Superset, Power BI, and AI answers.

### Why

The project now has a validated warehouse with facts, dimensions, and marts. The frontend dashboard still computes many metrics directly from raw `business` tables through Java SQL.

### Step-by-Step

1. Load DWH outputs into PostgreSQL or create views over them.

2. Create a schema:
   - `warehouse`
   - or `mart`

3. Add tables/views:
   - `mart_sales_overview`
   - `mart_sales_by_commercial`
   - `mart_sales_by_customer`
   - `mart_sales_by_product`
   - `mart_sales_by_supplier`
   - `mart_sales_by_region`

4. Update `SalesDashboardService.java` gradually:
   - first KPI overview
   - then commercial/product/client/supplier charts
   - then operational sections

5. Keep raw-table fallback for drill-down and debugging.

6. Update `docs/schema_metadata.csv` so AI can retrieve marts.

### Task-Level Tests

SQL checks:

```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi `
  -c "select count(*) from mart.mart_sales_overview;"
```

Backend tests:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :llm-orchestrator:test
```

Frontend:

```powershell
cd D:\LPN_PROJECT\frontend
npm run build
```

Acceptance:

- Dashboard totals match DWH validation report.
- Superset and frontend show consistent core metrics.
- Existing dashboard filters still work.

### Project-Level Tests

Run the global validation gate.

---

## Task 8 - Improve Model Routing, Caching, and Latency

Priority: High  
Goal: Use the smallest reliable model for each question and avoid repeated expensive work.

### Why

Qwen 14B is much slower than 7B in existing traces. Qwen 32B may be useful for difficult SQL, but it should not be the default unless evals prove the accuracy gain.

### Step-by-Step

1. Use the new eval baseline to compare:
   - `qwen2.5-coder:7b`
   - `qwen2.5-coder:14b`
   - `qwen2.5-coder:32b`
   - `sqlcoder:7b`

2. Record:
   - execution accuracy
   - SQL validity
   - semantic pass rate
   - p50 latency
   - p95 latency

3. Add model routing:
   - deterministic templates for common KPI/dashboard questions
   - 7B for simple SQL
   - 14B or 32B only for hard multi-join questions

4. Add cache layers:
   - schema retrieval cache
   - common SQL template cache
   - semantic cache for repeated questions where safe

5. Populate Ollama timing fields if possible:
   - load duration
   - prompt eval duration
   - eval duration

### Task-Level Tests

Run model comparison eval:

```powershell
cd D:\LPN_PROJECT\services-python\eval
uv run python -m eval.cli --questions questions_vente_fr_en.csv --base-url http://localhost:8081 --output reports/model_eval_qwen7b.csv
```

Acceptance:

- Model routing decision is based on eval data.
- Common deterministic questions remain fast.
- Hard questions do not silently fall back to weak SQL.

### Project-Level Tests

Run the global validation gate.

---

## Task 9 - Split the Frontend Into Maintainable Feature Modules

Priority: Medium  
Goal: Reduce the risk of regressions in the large frontend file.

### Why

`frontend/src/App.tsx` is currently over 4,000 lines and contains routing, shell, auth, conversation, dashboard, charts, API helpers, and utilities.

### Step-by-Step

1. Create directories:
   - `frontend/src/components/shell`
   - `frontend/src/features/chat`
   - `frontend/src/features/dashboard`
   - `frontend/src/features/sales-analysis`
   - `frontend/src/features/admin`
   - `frontend/src/features/settings`
   - `frontend/src/lib`

2. Move only one feature at a time.

3. Start with safe utility extraction:
   - date formatters
   - number formatters
   - API helpers

4. Then extract:
   - shell/sidebar/topbar
   - conversation page
   - sales analysis page

5. Add lazy route loading where useful.

6. Keep public behavior unchanged.

### Task-Level Tests

After each extraction:

```powershell
cd D:\LPN_PROJECT\frontend
npm run build
```

Manual pages to check:

- `/conversation`
- `/tableau-de-bord`
- `/tableau-de-bord/analyse-vente`
- `/previsions`
- `/historique`
- `/admin`
- `/reglages`

Acceptance:

- No route changes.
- No visual regressions in sidebar/topbar.
- Bundle warning improves or does not get worse.

### Project-Level Tests

Run the global validation gate.

---

## Task 10 - Harden Auth and Session Handling

Priority: High  
Goal: Move from demo auth toward production-safe auth.

### Why

The project has PBKDF2 hashing and UUID sessions, but admin defaults and token transport are still prototype-grade.

### Step-by-Step

1. Move tokens from query/body fields to:
   - `Authorization: Bearer <token>`
   - or HttpOnly secure cookies

2. Keep compatibility during migration if needed.

3. Remove or restrict default `admin/admin` bootstrap:
   - require env variable
   - force password change
   - refuse default password outside local dev

4. Add login throttling.

5. Add session revoke/logout endpoint.

6. Add audit logging for:
   - login success/failure
   - signup
   - admin approval/rejection

### Task-Level Tests

Java tests:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :llm-orchestrator:test
```

Manual tests:

- login success
- login failure
- signup pending
- admin approval
- revoked token fails
- non-admin cannot access admin endpoints

Acceptance:

- No token in URL query string for new requests.
- Admin bootstrap is safe outside local dev.
- Old session handling does not break during migration unless intentionally removed.

### Project-Level Tests

Run the global validation gate.

---

## Task 11 - Add AI Observability and Feedback Loop

Priority: Medium  
Goal: Make AI quality visible and improvable over time.

### Why

The project already stores traces in `app.ai_request_performance_trace`, which is a strong foundation. It needs retrieval candidate details, prompt versioning, model token timings, user feedback, and dashboard views.

### Step-by-Step

1. Add trace fields:
   - prompt version
   - retrieved candidates before rerank
   - final retrieved context
   - query plan
   - validation issues
   - user feedback rating
   - user correction text

2. Add frontend feedback UI:
   - useful / not useful
   - wrong SQL
   - wrong data
   - unclear answer

3. Create admin trace dashboard:
   - latency p50/p95
   - error rate
   - semantic rejection rate
   - repair rate
   - top failed questions
   - model distribution

4. Optionally export traces to Superset.

### Task-Level Tests

SQL check:

```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi `
  -c "select count(*) from app.ai_request_performance_trace;"
```

Frontend:

```powershell
cd D:\LPN_PROJECT\frontend
npm run build
```

Java:

```powershell
cd D:\LPN_PROJECT\services-java
.\gradlew.bat :llm-orchestrator:test
```

Acceptance:

- New trace fields are populated.
- Feedback can be submitted and persisted.
- Admin can inspect failures without reading raw logs.

### Project-Level Tests

Run the global validation gate.

---

## Task 12 - Add CI and Release Quality Gates

Priority: Medium  
Goal: Prevent regressions before they reach the main branch.

### Why

The local workspace currently has no `.github` workflow folder. Builds and tests can pass locally, but there is no automated GitHub quality gate.

### Step-by-Step

1. Create:
   - `.github/workflows/ci.yml`

2. Add jobs:
   - frontend build
   - Java tests
   - Python sql-validator tests
   - Python eval tests
   - optional schema-retrieval tests with Qdrant service

3. Cache dependencies where safe.

4. Make CI run on:
   - pull request
   - push to main branches

5. Add badges to README after CI is stable.

### Task-Level Tests

Local equivalent:

```powershell
cd D:\LPN_PROJECT\frontend
npm ci
npm run build

cd D:\LPN_PROJECT\services-java
.\gradlew.bat test

cd D:\LPN_PROJECT\services-python\sql-validator
uv run pytest

cd D:\LPN_PROJECT\services-python\eval
uv run pytest
```

Acceptance:

- GitHub Actions workflow is valid.
- PRs show pass/fail status.
- CI does not require local secrets.
- Raw data and model weights are not uploaded.

### Project-Level Tests

Run the global validation gate locally and verify the same jobs pass remotely.

---

## Recommended Execution Order

1. Task 1 - Evaluation baseline.
2. Task 2 - Semantic layer metadata.
3. Task 3 - Hybrid retrieval.
4. Task 5 - SQL validation hardening.
5. Task 4 - Structured query plan.
6. Task 8 - Model routing and caching.
7. Task 6 - Streaming UX.
8. Task 7 - DWH as canonical BI layer.
9. Task 11 - Observability and feedback.
10. Task 9 - Frontend modularization.
11. Task 10 - Auth/session hardening.
12. Task 12 - CI quality gates.

## Final Success Criteria

The roadmap is successful when:

- Text-to-SQL accuracy is measured with a current LPN vente benchmark.
- Common questions answer quickly through deterministic or cached paths.
- Hard questions use stronger models only when justified.
- Schema retrieval understands French business terminology.
- SQL is validated structurally, semantically, and against approved schema.
- Dashboard, Superset, and AI answers use consistent metric definitions.
- The frontend feels responsive during long AI calls.
- Production risks around auth, observability, and CI are reduced.
- The project remains demo-ready after every task.

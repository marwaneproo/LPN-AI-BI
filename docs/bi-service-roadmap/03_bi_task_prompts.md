# BI Service Roadmap — Ready-to-Run Task Prompts

Copy one block at a time into Claude Code. Run tasks **in order**; do not start a
task until the previous task's milestone entry exists in
[`context_bi_service.md`](context_bi_service.md). Each prompt is self-contained.

**Model legend:** Sonnet 4.6 medium (normal) · Sonnet 4.6 high (complex bounded) ·
Opus 4.8 medium (architecture-critical only).

**Mandatory closing block** (already embedded in every prompt below):

```
At the end of this task, append a new milestone entry to:
docs/bi-service-roadmap/context_bi_service.md

Do not overwrite the file.
Do not delete previous entries.
Append only.

The entry must include:
- date/time if available
- task ID
- task title
- summary of what was done
- files/folders inspected
- files created/modified
- technical decisions made
- validation commands run
- validation results
- problems encountered
- next recommended task
```

---

## BI-00 — Repo & service inventory snapshot
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-00. Produce a current inventory snapshot. Read-only — do not modify any
service code, data file, or the business schema.

Inspect: docker-compose.yml, scripts/launch-local-native.ps1, services-java/*,
services-python/*, DataWareHouse/processus_de_vente/*,
docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md, docs/warehouse/*,
docs/semantic_layer/*, frontend/src/features/bi/*.

Do:
1. List services + ports + health URLs.
2. List existing DWH/ETL artifacts and the rebuild-roadmap task list.
3. List current BI Java classes + endpoints in llm-orchestrator.
4. List the 5 frontend BI pages and mark placeholder vs live.
5. Connect read-only to PG18 (psql -U lpn_ai_readonly -p 5432 -d lpn_ai_bi) and
   list schemas + top-10 business table counts.
6. Write docs/bi-service-roadmap/_inventory/00_repo_snapshot.md.

Validate: psql ... -c "\dn" shows business+app; counts match the snapshot.
Only create files under docs/bi-service-roadmap/. Commit:
"docs(bi-roadmap): repo & service inventory snapshot".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-01 — Data folder inventory & reconciliation
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-01. Inventory Youssef_Extractions/ using METADATA ONLY (file names, sizes,
sheet names, row counts via streaming/nrows). NEVER load a whole multi-GB file.
Do not move, rename, or delete any data file.

Inspect: Youssef_Extractions/* (listing), docs/warehouse/*.json, rebuild roadmap
section "Key facts the AI must know".

Do: enumerate files (path, ext, size, mtime); for xlsx read sheet names + row
counts via openpyxl read-only or pandas nrows; cross-reference existing audits;
classify each file canonical/duplicate/junk/enrichment; flag junk (Book*.xlsx,
~$*, 3.xlsx, 4.xlsx). Write docs/bi-service-roadmap/_inventory/01_data_inventory.md.

Validate: re-check 3 files' row counts with a second method; they match.
Commit: "docs(bi-roadmap): data folder inventory & reconciliation".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-02 — Deep profiling of canonical transactional files
**Model/effort:** Sonnet 4.6 high · **Skills:** `/microservices-patterns`, `/python-testing-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-02. Column-level profiling of the canonical C_ORDER / C_ORDERLINE /
C_INVOICE / C_INVOICELINE files identified in BI-01. SAMPLE ONLY (head + random
sample); never load >100 MB fully. No DB writes.

For each file: columns, dtypes, null %, distinct counts, min/max, sample values,
candidate primary key, candidate foreign keys to dimensions. Locate CA fields
(GRANDTOTAL, LINENETAMT) and date fields (DATEORDERED, DATEINVOICED) and map them
to docs/semantic_layer/metrics.yml.

Write docs/bi-service-roadmap/_profiling/02_transactional_profile.md (+ optional .json).
Validate: PK candidate is unique on the sample; null %s plausible.
Commit: "docs(bi-roadmap): transactional file profiling".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-03 — Encoding / format / missing-value profile
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/python-testing-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-03. Catalogue encoding, date/number/currency formats, and missing values
across canonical + enrichment files. Sample only; document problems (do not fix).

Detect: text encodings; date formats (dd/mm/yyyy vs ISO); decimal/thousand
separators; MAD currency handling; whitespace/case; missing-value tokens. Give a
concrete file+cell example for each problem.

Write docs/bi-service-roadmap/_profiling/03_format_quality_profile.md.
Validate: every listed problem has a real example. Commit:
"docs(bi-roadmap): format & quality profile".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-04 — Business-entity catalogue
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-04. Map files to business entities: commandes, ventes, clients, articles,
commerciaux, catégories, factures, livraisons, paiements, stocks (if present).
Use BI-01/02/03 outputs and DataWareHouse/processus_de_vente/02_star_schema_design.md.

For each entity: source file(s), grain, key, supporting dimensions, and which of the
5 frontend pages it feeds. Write
docs/bi-service-roadmap/_profiling/04_entity_catalogue.md.
Validate: each of the 5 pages has >=1 backing entity. Commit:
"docs(bi-roadmap): business-entity catalogue".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-05 — Cleaning, dedup & normalization plan
**Model/effort:** Sonnet 4.6 high · **Skills:** `/python-testing-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-05. INSPECT FIRST, THEN PLAN. Produce a testable cleaning specification —
design only, run nothing, touch no data.

Use BI-02/03/04 + the rebuild roadmap dedup rule (2024_* for 2024, 53..58 for
2025-2026) + the 57_..PORTFOLIO_24M CA many-to-many trap.

Define: per-entity dedup keys; normalization (dates->ISO, numbers->decimal, text
trim/case, MAD); rejected-row strategy + reject store; audit/logging; idempotency.
Write docs/bi-service-roadmap/_plan/05_cleaning_plan.md.
Validate: every BI-03 problem has a rule; dedup keys reference real BI-02 PKs.
Commit: "docs(bi-roadmap): cleaning, dedup & normalization plan".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-06 — Staging schema design (PostgreSQL)
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-06. Design the staging schema (stg_*) as DDL DRAFT only. Do NOT run DDL on
lpn_ai_bi and do NOT modify the business schema.

Use DataWareHouse/.../01_warehouse_layers.md, ddl/draft_schema_design.sql, and BI-02
PKs. One stg_* per canonical source; columns mirror source + load metadata
(_loaded_at, _source_file); no transformations here.

Write docs/bi-service-roadmap/_design/06_staging_design.md and a draft
docs/bi-service-roadmap/sql/staging.sql.
Validate: the DDL parses (sqlglot/sqlfluff); covers all canonical entities.
Commit: "docs(bi-roadmap): staging schema design".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-07 — Warehouse dim/fact design alignment
**Model/effort:** Sonnet 4.6 high · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-07. INSPECT FIRST, THEN PLAN. Reconcile the existing star schema with the
profiled data and finalize dim_*/fact_* for PostgreSQL as DDL DRAFT only. Do not run
DDL; do not modify business.

Use 02_star_schema_design.md, 03_fact_grain_design.md, and BI-04. Confirm dims
(date, customer, commercial, product, category, supplier, geography, payment_term,
price_list, document_type) and facts (sales_order, sales_order_line, invoice,
invoice_line, delivery, delivery_line, payment_allocation, stock_snapshot); SCD1;
surrogate keys; unknown=0. Record any change vs prototype as an explicit DECISION.

Write docs/bi-service-roadmap/_design/07_warehouse_design.md + draft
docs/bi-service-roadmap/sql/warehouse.sql.
Validate: DDL parses; each fact's grain matches 03_fact_grain_design.md.
Commit: "docs(bi-roadmap): warehouse dim/fact design alignment".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-08 — Mart design for the 5 frontend pages
**Model/effort:** Sonnet 4.6 high · **Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-08. Define the mart_* tables/views each BI page needs — the contract the BI
API will read. DDL/design draft only; do not run DDL; do not touch business.

Inspect frontend/src/features/bi/pages/*, current SalesDashboardService KPIs, and
docs/semantic_layer/metrics.yml. Per page (Vue d'ensemble, Commande, Chiffre
d'affaires, Articles, Client) define marts: KPIs, trend (day/week/month), rankings,
supported filters, columns, grain. Reuse metrics.yml definitions.

Write docs/bi-service-roadmap/_design/08_mart_design.md + draft
docs/bi-service-roadmap/sql/marts.sql.
Validate: every visible widget maps to a named mart column.
Commit: "docs(bi-roadmap): mart design for 5 BI pages".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-09 — ETL implementation plan
**Model/effort:** Sonnet 4.6 high · **Skills:** `/python-testing-patterns`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-09. INSPECT FIRST, THEN PLAN. Produce an idempotent ETL plan that EXTENDS the
existing DataWareHouse ETL to load PostgreSQL. Plan + optional non-executing stubs
only. Do not run full ETL, do not load multi-GB into memory, do not write business.

Use DataWareHouse/.../etl/scripts/*.py, rebuild roadmap Tasks 5-9, and BI-05/06/07/08.
Define jobs raw->stg_*->dim_*/fact_*->mart_*; idempotency (truncate-reload or upsert
by key); chunked/streamed reads; data-quality checks + reject store; run order;
logging; validation report location; and a reuse map to existing scripts.

Write docs/bi-service-roadmap/_plan/09_etl_plan.md (and optional stubs under
services-python/data-import/, not executed).
Validate: plan covers every BI-08 mart; each job lists inputs/outputs/keys.
Commit: "docs(bi-roadmap): ETL implementation plan".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-10 — BI API contract & DTO design
**Model/effort:** Sonnet 4.6 high · **Skills:** `/microservices-patterns`, `/java-springboot`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-10. Freeze the BI serving API contract (marts-only). Design only — implement
no controllers.

Inspect BI-08 marts, current SalesDashboardController shape,
frontend/src/features/bi/api/biApi.ts and its types. Define /v1/bi/* endpoints per
page; request params (from, to, granularity, compare, page filters); response DTOs
mapped to mart columns; pagination/limits; error contract; performance notes
(indexes, caching).

Write docs/bi-service-roadmap/_design/10_bi_api_contract.md (OpenAPI-style).
Validate: every page widget has an endpoint+DTO field; every DTO field traces to a
mart column. Commit: "docs(bi-roadmap): BI API contract & DTOs".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-11 — BI extraction decision (package vs service)
**Model/effort:** Opus 4.8 medium (architecture-critical) · **Skills:** `/microservices-patterns`, `/java-springboot`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-11. INSPECT FIRST, THEN DECIDE. Document whether to build a clean bi package
inside llm-orchestrator now vs a standalone Spring bi-service now, and define
extraction acceptance criteria. Do not create the service or move code.

Inspect the current BI coupling in llm-orchestrator, the sql-executor read-only
datasource pattern, docker-compose.yml, and 01_bi_architecture_vision.md section 8.
Evaluate the strangler-fig steps; define the package boundary (controllers,
services, read-only datasource, DTOs, tests); list measurable acceptance criteria
for later extraction (stable marts, frozen contract, test coverage, zero
orchestrator imports). Give a deadline-aware recommendation.

Write docs/bi-service-roadmap/_design/11_extraction_decision.md.
Validate: recommendation references real classes/ports; criteria are checkable.
Commit: "docs(bi-roadmap): BI extraction decision".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-12 — Frontend integration plan (5 pages)
**Model/effort:** Sonnet 4.6 high · **Skills:** `/vercel-react-best-practices`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-12. Plan the page-by-page replacement of placeholder data with the BI API,
preserving loading/empty/error states. Planning only — do not rewrite components.

Inspect the 5 BI pages + BiWidgets, biApi.ts, and the existing Recharts components.
Per page: which widgets, which BI-10 endpoint, DTO->props mapping, state handling,
rollout order (lowest-risk page first), placeholder-removal checklist. Keep the
existing export behavior untouched.

Write docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md.
Validate: every placeholder widget maps to a BI-10 endpoint+DTO.
Commit: "docs(bi-roadmap): frontend integration plan".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-13 — Export / reporting endpoint design
**Model/effort:** Sonnet 4.6 medium · **Skills:** `/java-springboot`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-13. Design a backend .xlsx data-export endpoint reading the same marts; keep
PNG/PDF on the frontend. Design only — implement nothing.

Inspect frontend/src/features/bi/utils/biExport.ts (current fflate xlsx), BI-08
marts, BI-10 contract. Define /v1/bi/export/* returning real .xlsx (correct MIME +
Content-Disposition), map marts->sheets, and state the responsibility split
(PNG/PDF frontend; data export backend). Do NOT introduce a .NET service.

Write docs/bi-service-roadmap/_design/13_export_reporting_design.md.
Validate: every page's Excel action maps to a backend endpoint + marts.
Commit: "docs(bi-roadmap): export & reporting design".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## BI-14 — Testing & validation strategy
**Model/effort:** Sonnet 4.6 high · **Skills:** `/python-testing-patterns`, `/java-springboot`

```
Read docs/bi-service-roadmap/context_bi_service.md FIRST.

Task BI-14. Define the full test strategy (data-quality, ETL, backend, frontend) and
a manual demo checklist, with a golden-number reconciliation gate vs the business
schema. Planning only — implement no tests.

Inspect existing tests (services-java/**/test, services-python/**/tests, frontend).
Define: DQ checks (row counts, totals vs source, null thresholds); ETL idempotency
tests; BI API contract tests asserting golden numbers vs current SalesDashboard
(e.g. order_count 616); frontend integration tests; the reconciliation gate
("no KPI repoint until mart == business value"); and a manual demo script for all 5
pages.

Write docs/bi-service-roadmap/_plan/14_testing_strategy.md.
Validate: every layer has named checks; gate defined with a concrete example.
Commit: "docs(bi-roadmap): testing & validation strategy".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

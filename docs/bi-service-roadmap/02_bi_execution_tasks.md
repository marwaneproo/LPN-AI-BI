# BI Service Roadmap — Execution Tasks

**Companion to:** [`01_bi_architecture_vision.md`](01_bi_architecture_vision.md) ·
Continuity log: [`context_bi_service.md`](context_bi_service.md) ·
Ready-to-run prompts: [`03_bi_task_prompts.md`](03_bi_task_prompts.md)

> Execute **one task at a time, in order**. Each task is bounded and ends with a
> concrete validation and a **mandatory append** to `context_bi_service.md`.

## How to use this file

- Tasks run `BI-00 → BI-14`. Do not start a task until the previous one's milestone
  entry exists in `context_bi_service.md`.
- **Model guidance:** `Sonnet 4.6 medium` = normal; `Sonnet 4.6 high` = complex but
  bounded; `Opus 4.8 medium` = rare architecture-critical decisions only.
- **Hard rules (inherited from the warehouse rebuild roadmap):**
  1. Never connect to LPN's Oracle/Compiere DB. Read PostgreSQL + exported files only.
  2. Never delete/overwrite source data without a human-approval step.
  3. Never drop `business`. New work goes to `staging`/`warehouse`/`mart` schemas.
  4. Never load whole multi-GB Excel files into memory — sample/stream only.
  5. A task ends **green** or is marked **BLOCKED** in the milestone log.

## Mandatory closing step for EVERY task

```
Append (do not overwrite) a milestone entry to docs/bi-service-roadmap/context_bi_service.md
using the §7 template: date/time, task ID, title, summary, folders/files inspected,
files created/modified, technical decisions, validation commands, validation results,
problems encountered, next recommended task. Corrections = new CORRECTION entry.
```

---

# Phase 0 — Safety & inventory

## BI-00 — Repo & service inventory snapshot
- **Model:** Sonnet 4.6 medium
- **Goal:** A single, current snapshot of services, ports, schemas, and existing BI/DWH assets so later tasks don't re-discover them.
- **Context:** Stack runs native (PG18 @ 5432). Prior DWH work exists under `DataWareHouse/` and `docs/`. Avoid duplication.
- **Scope:** Read-only inventory + one summary doc. No data processing.
- **Inspect:** `docker-compose.yml`, `scripts/launch-local-native.ps1`, `services-java/*`, `services-python/*`, `DataWareHouse/processus_de_vente/*`, `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`, `docs/warehouse/*`, `docs/semantic_layer/*`, `frontend/src/features/bi/*`.
- **Allowed to modify:** `docs/bi-service-roadmap/` (create `_inventory/00_repo_snapshot.md`).
- **Forbidden:** any service code, any data file, `business` schema.
- **Steps:** 1) List services + ports + health URLs. 2) List existing DWH/ETL artifacts and the rebuild-roadmap task list. 3) List current BI Java classes + endpoints. 4) List the 5 frontend pages and which are placeholder vs live. 5) Connect to PG18 read-only and list schemas + `business` table counts (top 10). 6) Write the snapshot doc.
- **Expected output:** `docs/bi-service-roadmap/_inventory/00_repo_snapshot.md` with tables for services, schemas+counts, BI endpoints, page status, and DWH assets.
- **Validation:** `psql -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "\dn"` lists `business`,`app`; counts match the snapshot.
- **Manual checklist:** [ ] every running service listed [ ] DWH prototype + rebuild roadmap referenced [ ] no data files opened.
- **Stop if:** PG18 not reachable on 5432 → mark BLOCKED (start `scripts/launch-local-native.ps1`).
- **Commit:** `docs(bi-roadmap): repo & service inventory snapshot`.

## BI-01 — Data folder inventory & reconciliation
- **Model:** Sonnet 4.6 medium
- **Goal:** Authoritative inventory of `Youssef_Extractions/` reconciled with existing `docs/warehouse/` audits, identifying the canonical source set.
- **Context:** ~6.5 GB; `data/Exported_LPN` is canonical (96 xlsx); known overlap `2024_*` vs `53..58`; junk files listed in rebuild roadmap §"Key facts".
- **Scope:** Metadata only — file names, sizes, sheet names, row counts via streaming. **No full reads.**
- **Inspect:** all `Youssef_Extractions/*` (listing), `docs/warehouse/*.json`, rebuild roadmap §"Key facts".
- **Allowed to modify:** `docs/bi-service-roadmap/_inventory/01_data_inventory.md` (+ optional `.json`).
- **Forbidden:** modifying/moving/deleting any data file; loading whole files.
- **Steps:** 1) Enumerate files: path, ext, size, mtime. 2) For xlsx, read sheet names + row counts via `openpyxl` read-only / `pandas` `nrows`. 3) Cross-reference with existing audits; note agreements/conflicts. 4) Mark canonical vs duplicate vs junk vs enrichment. 5) Write inventory.
- **Expected output:** `01_data_inventory.md` table (file → entity → canonical?/dup?/junk? → rows) + a short reconciliation note vs `docs/warehouse/`.
- **Validation:** spot-check 3 files' row counts with a second method; numbers match.
- **Manual checklist:** [ ] no file fully loaded [ ] junk (`Book*.xlsx`, `~$*`, `3.xlsx`,`4.xlsx`) flagged [ ] canonical set explicit.
- **Stop if:** a folder is unreadable/locked → record and continue.
- **Commit:** `docs(bi-roadmap): data folder inventory & reconciliation`.

---

# Phase 1 — Data profiling

## BI-02 — Deep profiling of canonical transactional files
- **Model:** Sonnet 4.6 high
- **Goal:** Column-level profile of the canonical order/orderline/invoice/invoiceline files.
- **Scope:** Sampled profiling (e.g. first N + random sample). No DB writes.
- **Inspect:** canonical files from BI-01; `docs/semantic_layer/metrics.yml` for expected fields.
- **Allowed to modify:** `docs/bi-service-roadmap/_profiling/02_transactional_profile.md` (+`.json`).
- **Forbidden:** data mutation; whole-file loads on >100 MB files (stream/sample).
- **Steps:** per file → columns, dtypes, null %, distinct counts, min/max, sample values, candidate PK, candidate FK to dims. Note CA fields (`GRANDTOTAL`, `LINENETAMT`) and date fields (`DATEORDERED`,`DATEINVOICED`).
- **Expected output:** profile doc per entity with PK/FK candidates and metric-field mapping.
- **Validation:** PK candidate uniqueness holds on the sample; null %s plausible.
- **Manual checklist:** [ ] sampling used [ ] metric fields located [ ] PK/FK noted.
- **Stop if:** a file's schema contradicts `metrics.yml` → record conflict, continue.
- **Commit:** `docs(bi-roadmap): transactional file profiling`.

## BI-03 — Encoding / format / missing-value profile
- **Model:** Sonnet 4.6 medium
- **Goal:** Catalogue encoding, date/number/currency formats, and missingness across canonical + enrichment files.
- **Scope:** Sampled. Documents problems; fixes are designed later (BI-05).
- **Inspect:** canonical + enrichment files; `docs/warehouse/*` for prior notes.
- **Allowed to modify:** `docs/bi-service-roadmap/_profiling/03_format_quality_profile.md`.
- **Forbidden:** data mutation.
- **Steps:** detect encodings; date formats (e.g. `dd/mm/yyyy` vs ISO); decimal/thousand separators; currency (MAD) handling; whitespace/case issues; missing-value tokens.
- **Expected output:** problem catalogue with examples + affected files.
- **Validation:** each listed problem has a concrete file+cell example.
- **Manual checklist:** [ ] currency=MAD confirmed [ ] date variants enumerated [ ] missing tokens listed.
- **Commit:** `docs(bi-roadmap): format & quality profile`.

## BI-04 — Business-entity catalogue
- **Model:** Sonnet 4.6 medium
- **Goal:** Map files → business entities: commandes, ventes, clients, articles, commerciaux, catégories, factures, livraisons, paiements, stocks (if present).
- **Inspect:** BI-01/02/03 outputs; star-schema design dims/facts.
- **Allowed to modify:** `docs/bi-service-roadmap/_profiling/04_entity_catalogue.md`.
- **Forbidden:** data mutation.
- **Steps:** for each entity → source file(s), grain, key, supporting dims, which of the 5 pages it feeds.
- **Expected output:** entity → source → grain → page mapping table.
- **Validation:** every one of the 5 pages has ≥1 backing entity identified.
- **Manual checklist:** [ ] stocks presence resolved [ ] commerciaux source identified.
- **Commit:** `docs(bi-roadmap): business-entity catalogue`.

---

# Phase 2 — Data cleaning plan

## BI-05 — Cleaning, dedup & normalization plan
- **Model:** Sonnet 4.6 high
- **Goal:** A precise, testable cleaning specification (design only — no ETL run).
- **Inspect:** BI-02/03/04; rebuild roadmap dedup rule (`2024_*` for 2024, `53..58` for 2025–2026); `57_..PORTFOLIO_24M` CA trap.
- **Allowed to modify:** `docs/bi-service-roadmap/_plan/05_cleaning_plan.md`.
- **Forbidden:** executing cleaning; touching data.
- **Steps:** define per-entity dedup keys; normalization rules (dates→ISO, numbers→decimal, text trim/case, MAD); rejected-row strategy + reject store; audit/logging strategy; idempotency requirements.
- **Expected output:** cleaning spec with rule tables + reject/audit design.
- **Validation:** every problem from BI-03 has a corresponding rule; dedup keys reference real PKs from BI-02.
- **Manual checklist:** [ ] dedup rule matches roadmap [ ] CA trap addressed [ ] reject store defined.
- **Commit:** `docs(bi-roadmap): cleaning, dedup & normalization plan`.

---

# Phase 3 — Staging & warehouse & marts

## BI-06 — Staging schema design (PostgreSQL)
- **Model:** Sonnet 4.6 medium
- **Goal:** `staging` schema DDL design aligned to canonical sources (design/DDL only — not applied to prod).
- **Inspect:** `DataWareHouse/.../01_warehouse_layers.md`, `ddl/draft_schema_design.sql`; BI-02 PKs.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/06_staging_design.md` + `sql/staging.sql` (draft).
- **Forbidden:** running DDL on `lpn_ai_bi`; modifying `business`.
- **Steps:** one `stg_*` per canonical source; columns mirror source + load metadata (`_loaded_at`,`_source_file`); no transformation logic here.
- **Expected output:** `staging.sql` draft + rationale.
- **Validation:** `sqlfluff`/`sqlglot` parses the DDL; table set covers canonical entities.
- **Manual checklist:** [ ] `_source_file`/`_loaded_at` present [ ] mirrors BI-02 columns.
- **Commit:** `docs(bi-roadmap): staging schema design`.

## BI-07 — Warehouse dim/fact design alignment
- **Model:** Sonnet 4.6 high
- **Goal:** Reconcile the existing star schema with the profiled data; finalize `dim_*`/`fact_*` for Postgres.
- **Inspect:** `02_star_schema_design.md`, `03_fact_grain_design.md`; BI-04 catalogue.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/07_warehouse_design.md` + `sql/warehouse.sql` (draft).
- **Forbidden:** running DDL; modifying `business`.
- **Steps:** confirm dims (date, customer, commercial, product, category, supplier, geography, payment_term, price_list, document_type) and facts (sales_order, sales_order_line, invoice, invoice_line, delivery, delivery_line, payment_allocation, stock_snapshot); note SCD1; surrogate keys + unknown=0 rule; flag any design changes vs prototype as decisions.
- **Expected output:** finalized warehouse DDL draft + a diff note vs the prototype.
- **Validation:** DDL parses; every fact has a documented grain matching `03_fact_grain_design.md`.
- **Manual checklist:** [ ] grains match [ ] deltas vs prototype recorded as decisions.
- **Commit:** `docs(bi-roadmap): warehouse dim/fact design alignment`.

## BI-08 — Mart design for the 5 frontend pages
- **Model:** Sonnet 4.6 high
- **Goal:** Define exactly the `mart_*` tables/views each page needs — the contract the BI API will read.
- **Inspect:** the 5 pages (`frontend/src/features/bi/pages/*`), current `SalesDashboardService` KPIs, `metrics.yml`.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/08_mart_design.md` + `sql/marts.sql` (draft).
- **Forbidden:** running DDL; modifying `business`.
- **Steps:** per page (Vue d'ensemble / Commande / Chiffre d'affaires / Articles / Client) define marts: KPIs, trend (day/week/month), rankings, filters supported. Reuse metric definitions from `metrics.yml`. Specify columns + grain per mart.
- **Expected output:** mart catalogue: page → marts → columns → grain → filters.
- **Validation:** every visible widget on each page maps to a named mart column.
- **Manual checklist:** [ ] all 5 pages covered [ ] compare-mode supported [ ] filters enumerated.
- **Commit:** `docs(bi-roadmap): mart design for 5 BI pages`.

---

# Phase 4 — ETL implementation plan

## BI-09 — ETL implementation plan (raw → staging → warehouse → marts)
- **Model:** Sonnet 4.6 high
- **Goal:** A concrete, idempotent ETL plan that **extends the existing `DataWareHouse` ETL** to load PostgreSQL (not just CSV). Plan + skeleton only; no heavy run.
- **Inspect:** `DataWareHouse/.../etl/scripts/*.py`, rebuild roadmap Tasks 5–9, BI-05/06/07/08.
- **Allowed to modify:** `docs/bi-service-roadmap/_plan/09_etl_plan.md`; optionally scaffold `services-python/data-import/` job stubs (no execution).
- **Forbidden:** running full ETL; loading multi-GB into memory; writing to `business`.
- **Steps:** define jobs raw→`stg_*`→`dim_*`/`fact_*`→`mart_*`; idempotency (truncate-reload or upsert by key); chunked/streamed reads; data-quality checks + reject store; run order + logging + validation report location. Map to existing scripts to reuse.
- **Expected output:** ETL plan with job DAG, idempotency strategy, DQ checks, and a reuse map to existing scripts.
- **Validation:** plan covers every mart in BI-08; each job lists inputs/outputs/keys.
- **Manual checklist:** [ ] idempotent [ ] streamed reads [ ] DQ + reject store [ ] reuses prototype.
- **Commit:** `docs(bi-roadmap): ETL implementation plan`.

---

# Phase 5 — BI API design

## BI-10 — BI API contract & DTO design
- **Model:** Sonnet 4.6 high
- **Goal:** Freeze the BI serving API contract (endpoints, DTOs, filters) the frontend will consume — marts-only.
- **Inspect:** BI-08 marts; current `SalesDashboardController` shape; `frontend/src/features/bi/api/biApi.ts` + `types`.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/10_bi_api_contract.md` (OpenAPI-style).
- **Forbidden:** implementing controllers; touching marts data.
- **Steps:** define `/v1/bi/*` endpoints per page; request params (`from`,`to`,`granularity`,`compare`, page filters); response DTOs mapped to mart columns; pagination/limits; error contract; performance notes (indexes, caching).
- **Expected output:** API contract doc + DTO definitions + filter matrix.
- **Validation:** every page widget has an endpoint+DTO field; every DTO field traces to a mart column.
- **Manual checklist:** [ ] 5 pages covered [ ] compare-mode in contract [ ] error shape defined.
- **Commit:** `docs(bi-roadmap): BI API contract & DTOs`.

---

# Phase 6 — BI service extraction decision

## BI-11 — BI extraction decision (package vs service)
- **Model:** Opus 4.8 medium (architecture-critical)
- **Goal:** Decide and document: clean `bi` package inside `llm-orchestrator` now vs standalone `bi-service` now; define extraction acceptance criteria.
- **Inspect:** current BI coupling in `llm-orchestrator`; `sql-executor` read-only datasource pattern; `docker-compose.yml`; vision §8.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/11_extraction_decision.md`.
- **Forbidden:** creating the service; moving code.
- **Steps:** evaluate strangler-fig steps; define package boundary (controllers/services/datasource/DTOs/tests); list acceptance criteria for later extraction (stable marts, frozen contract, test coverage, no orchestrator imports); give a deadline-aware recommendation.
- **Expected output:** decision doc with recommendation + acceptance criteria checklist.
- **Validation:** decision references real classes/ports; criteria are objectively checkable.
- **Manual checklist:** [ ] recommendation explicit [ ] criteria measurable [ ] deadline considered.
- **Commit:** `docs(bi-roadmap): BI extraction decision`.

---

# Phase 7 — Frontend integration

## BI-12 — Frontend integration plan (5 pages)
- **Model:** Sonnet 4.6 high
- **Goal:** A page-by-page plan to replace placeholder data with the BI API, preserving loading/empty/error states.
- **Inspect:** the 5 pages + `BiWidgets`, `biApi.ts`, existing Recharts components.
- **Allowed to modify:** `docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`.
- **Forbidden:** rewriting components in this task (planning only).
- **Steps:** per page → which widgets, which endpoint (BI-10), DTO→props mapping, loading/empty/error handling, rollout order (lowest-risk page first), and a placeholder-removal checklist.
- **Expected output:** integration plan + per-page mapping tables + rollout order.
- **Validation:** every placeholder widget maps to an endpoint+DTO from BI-10.
- **Manual checklist:** [ ] states preserved [ ] rollout order set [ ] export untouched.
- **Commit:** `docs(bi-roadmap): frontend integration plan`.

---

# Phase 8 — Export / reporting

## BI-13 — Export / reporting endpoint design
- **Model:** Sonnet 4.6 medium
- **Goal:** Design a backend `.xlsx` data-export endpoint reading the same marts; keep PNG/PDF frontend.
- **Inspect:** `frontend/src/features/bi/utils/biExport.ts` (current `fflate` xlsx), `project_lpn_bi_export` memory, BI-08 marts, BI-10 contract.
- **Allowed to modify:** `docs/bi-service-roadmap/_design/13_export_reporting_design.md`.
- **Forbidden:** implementing the endpoint.
- **Steps:** define `/v1/bi/export/*` returning real `.xlsx` (correct MIME + `Content-Disposition`); map marts→sheets; specify which exports stay frontend (PNG/PDF) vs move to backend (data); note that no .NET service is introduced.
- **Expected output:** export/reporting design doc with responsibility split.
- **Validation:** every page's "Excel" action maps to a backend endpoint + marts.
- **Manual checklist:** [ ] PNG/PDF stay frontend [ ] real `.xlsx` MIME [ ] no .NET added.
- **Commit:** `docs(bi-roadmap): export & reporting design`.

---

# Phase 9 — Testing & validation

## BI-14 — Testing & validation strategy
- **Model:** Sonnet 4.6 high
- **Goal:** Define the full test strategy: data-quality, ETL, backend, frontend, and a manual demo checklist — with golden-number reconciliation against `business`.
- **Inspect:** existing tests (`services-java/**/test`, `services-python/**/tests`, `frontend`), `/python-testing-patterns`.
- **Allowed to modify:** `docs/bi-service-roadmap/_plan/14_testing_strategy.md`.
- **Forbidden:** implementing tests (planning only).
- **Steps:** define DQ checks (row counts, totals vs source, null thresholds); ETL idempotency tests; BI API contract tests (golden numbers vs current `SalesDashboard`); frontend integration tests; reconciliation gate ("no KPI repoint until mart == business value"); manual demo script for the 5 pages.
- **Expected output:** layered test strategy + reconciliation gate + demo checklist.
- **Validation:** every layer has named checks; golden-number gate defined with a concrete example (e.g. order_count 616).
- **Manual checklist:** [ ] reconciliation gate defined [ ] all 5 pages in demo script.
- **Commit:** `docs(bi-roadmap): testing & validation strategy`.

---

## Recommended execution order

`BI-00 → BI-01 → BI-02 → BI-03 → BI-04 → BI-05 → BI-06 → BI-07 → BI-08 → BI-09 → BI-10 → BI-11 → BI-12 → BI-13 → BI-14`

The deterministic data path (BI-00…BI-09) coordinates with
`DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` (which performs the actual Postgres loads).
Where the two overlap, **this roadmap references the rebuild roadmap's tasks rather
than re-implementing them** — record the linkage in `context_bi_service.md`.

## What "done" means for this roadmap

Marts exist in PostgreSQL; a marts-only BI API serves the five pages; placeholders
are gone; Excel data-export is a backend endpoint; PNG/PDF remain frontend; numbers
reconcile to `business`; and `context_bi_service.md` contains a complete milestone
trail. Service extraction (BI-11) may be executed or deliberately deferred — either
way the package boundary delivers the architectural separation.

# BI Implementation Roadmap — Execution Tasks

**Companion to:** [`IMPL_00_overview.md`](IMPL_00_overview.md) · Continuity log:
[`context_bi_implementation.md`](context_bi_implementation.md) · Prompts:
[`IMPL_prompts.md`](IMPL_prompts.md)

> One task at a time, in order. Each touches **real code/DB/app** — so each ends with
> a build/test/reconcile gate and an append to `context_bi_implementation.md`.
> **Safety rules** (from IMPL_00 §4): additive only, never touch `business`/`app`,
> keep legacy endpoints live, reconcile before exposing, idempotent DDL, read-only serving.

## Mandatory closing step for EVERY task

```
Append (do not overwrite) a milestone entry to
docs/bi-service-roadmap/implementation/context_bi_implementation.md using the §5
template. Corrections = new CORRECTION entry.
```

---

# Phase A — Mart layer over `business`

## IMPL-01 — `mart` schema + overview & payment marts (over business) + reconcile
- **Model:** GPT-5.5 high
- **Goal:** Stand up the `mart` schema in the live DB and the marts the **Vue
  d'ensemble** page needs, sourced from `business.*`, reconciled to `/v1/sales-dashboard`.
- **Context:** First real DB change. Marts expose the BI-08 column contract but read
  `business.*` (IMPL-DECISION-01). `business` already powers the live dashboard.
- **Scope:** `mart_sales_daily`, `mart_sales_monthly`, `mart_payment_status` (and an
  overview rollup if BI-08 defines one). Views only. No `business` changes.
- **Inspect:** `../_design/08_mart_design.md`, `../sql/marts.sql` (warehouse-sourced
  target — translate column-for-column to business sources), `SalesDashboardService`
  (the existing KPI SQL = the reconciliation oracle), live `business` columns.
- **Allowed to modify:** new `implementation/sql/marts_business/01_overview.sql`; apply
  it to `lpn_ai_bi`. May add a small reconcile script under `implementation/sql/checks/`.
- **Forbidden:** any `ALTER`/`DROP` on `business`/`app`; running warehouse DDL; changing services.
- **Steps:** 1) `CREATE SCHEMA IF NOT EXISTS mart` + `GRANT USAGE`/`SELECT` to
  `lpn_ai_readonly`. 2) Author the views with the **exact BI-08 columns**, sourced from
  `business.c_order/c_invoice/...`, honoring prior rules (LEFT JOIN nullable C_ORDER_ID,
  ISPAID for paid/unpaid, DOCSTATUS filters, MAD). 3) Apply via psql (idempotent).
  4) Reconcile: `SELECT` from the mart and compare to `SalesDashboardService` numbers.
- **Expected output:** `mart` schema + 3 views live; a reconciliation note showing
  mart == business for `order_count`, `invoiced_sales`, paid/unpaid counts.
- **Validation:**
  - `psql ... -c "\dv mart.*"` lists the views.
  - `SELECT order_count FROM mart.mart_sales_monthly WHERE period='previous_month'` == **616**.
  - Default `lpn_ai_readonly` can `SELECT` the views; cannot write.
- **Manual checklist:** [ ] views additive [ ] business untouched [ ] 616 matches [ ] readonly grant works.
- **Stop if:** a mart can't reconcile to business → mark BLOCKED, record the gap, do not expose it.
- **Commit:** `feat(bi-mart): overview & payment marts over business (IMPL-01)`.

## IMPL-02 — Remaining page marts (over business) + reconcile
- **Model:** GPT-5.5 high
- **Goal:** Build the marts for the other four pages: `mart_sales_by_commercial`,
  `mart_sales_by_customer`, `mart_sales_by_product`, `mart_sales_by_region`,
  `mart_order_to_invoice_flow`, `mart_stock_risk`.
- **Context:** Same interim source. Some marts may be partial if their source tables
  aren't in `business` (e.g. stock `RV_STORAGE`, deliveries `M_INOUT`). Flag, don't fake.
- **Inspect:** `../_design/08_mart_design.md`, `../sql/marts.sql`, `business` tables to
  confirm which sources exist (stock/delivery/payment).
- **Allowed to modify:** `implementation/sql/marts_business/02_pages.sql`; apply to DB.
- **Forbidden:** touching `business`/`app`; fabricating data for missing sources.
- **Steps:** author each view (BI-08 columns, business sources); apply idempotently;
  reconcile each against an independent `business` aggregate; for any mart whose source
  is absent in `business`, create the view shell returning an empty/clearly-flagged set
  and record it as a known gap to be filled by IMPL-W (warehouse).
- **Expected output:** all BI-08 marts present in `mart` (real where sourceable, flagged
  where not) + per-mart reconciliation note.
- **Validation:** each mart's headline total matches a hand SQL over `business`;
  `\dv mart.*` shows the full set; readonly can select all.
- **Manual checklist:** [ ] every BI-08 mart exists [ ] gaps explicitly flagged [ ] reconciled.
- **Commit:** `feat(bi-mart): page marts over business (IMPL-02)`.

---

# Phase B — BI serving API (`bi` package)

## IMPL-03 — Scaffold `bi` package + read-only datasource + `/v1/bi/overview` + tests
- **Model:** GPT-5.5 high · skills `/java-springboot`, `/microservices-patterns`
- **Goal:** Create the BI-11 package boundary, a dedicated read-only mart datasource,
  and the first real endpoint reading `mart.*` — proven with a golden-number test.
- **Context:** BI-11 mandates package `com.lpn.aibi.llmorchestrator.bi.{api,api.dto,
  application,infrastructure.mart,infrastructure.config}`, read-only `lpn_ai_readonly`,
  `search_path=mart`, zero LLM-orchestration imports.
- **Scope:** package + config + one repository + one service + one controller for
  `GET /v1/bi/overview` per the BI-10 contract. Do NOT modify `SalesDashboardController`.
- **Inspect:** `../_design/11_extraction_decision.md`, `../_design/10_bi_api_contract.md`
  (overview DTO), `sql-executor` `DataSourceConfig` (read-only datasource template),
  `llm-orchestrator` `application.yml`.
- **Allowed to modify:** new files under
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/**`,
  matching tests under `.../test/.../bi/**`, and BI datasource props in `application.yml`.
- **Forbidden:** importing SqlGeneration/Qa/SchemaRetrieval/Predictive/SqlExecutorClient
  into `bi`; any `business.*` SQL in `bi`; editing legacy BI classes.
- **Steps:** add `bi.datasource.readonly.*` (lpn_ai_readonly, search_path=mart, timeout);
  a qualified `biReadonlyJdbcTemplate`; a mart repository (static SQL over
  `mart.mart_sales_*`); an application service (period/compare handling); the controller
  + DTO from BI-10; an ArchUnit/import-boundary test; a contract test asserting the
  overview response `order_count == 616`.
- **Expected output:** `/v1/bi/overview` returns real JSON from marts; tests pass; app boots.
- **Validation:**
  - `./gradlew :llm-orchestrator:test` green (incl. boundary + golden-number test).
  - App boots (java -jar) and `curl localhost:8081/v1/bi/overview` returns 200 with 616.
  - Legacy `/v1/sales-dashboard` still 200.
- **Manual checklist:** [ ] zero forbidden imports [ ] readonly datasource [ ] 616 via API [ ] legacy intact.
- **Stop if:** the boundary test can't pass (an import leaks) → fix before proceeding.
- **Commit:** `feat(bi-api): bi package + /v1/bi/overview over marts (IMPL-03)`.

## IMPL-04 — Remaining `/v1/bi/*` endpoints + filters + tests
- **Model:** GPT-5.5 high · skills `/java-springboot`
- **Goal:** Implement `/v1/bi/{orders,revenue,articles,clients,commercial,analysis}`
  per BI-10, with filters (from/to/granularity/compare/page filters), all marts-only.
- **Inspect:** `../_design/10_bi_api_contract.md` (all endpoints/DTOs/filters), the
  IMPL-02 marts, `../_plan/14_testing_strategy.md` (contract tests).
- **Allowed to modify:** `.../bi/**` (controllers/services/repos/dtos) + tests.
- **Forbidden:** forbidden imports; raw/business SQL in `bi`; legacy edits.
- **Steps:** one repository+service+controller path per endpoint; map every DTO field to
  a mart column; implement filter handling + compare window; contract test per endpoint
  with reconciled golden numbers; keep marts whose data is partial (IMPL-02 gaps)
  returning explicit empty/flagged payloads (frontend shows empty state).
- **Expected output:** all 7 `/v1/bi/*` endpoints live and tested.
- **Validation:** `./gradlew :llm-orchestrator:test` green; `curl` each endpoint → 200 with
  shape matching BI-10; spot-reconcile one number per endpoint vs `business`.
- **Manual checklist:** [ ] 7 endpoints [ ] filters work [ ] DTOs trace to mart columns [ ] tests green.
- **Commit:** `feat(bi-api): remaining /v1/bi/* endpoints (IMPL-04)`.

---

# Phase C — Frontend wiring (charts go real)

## IMPL-05 — Wire the FIRST page to the real API (charts show real data)
- **Model:** GPT-5.5 high · skill `/vercel-react-best-practices`
- **Goal:** Replace placeholder data on the first page (per BI-12 rollout order) with
  real `/v1/bi/*` data, keeping loading/empty/error states. **First real charts.**
- **Inspect:** `../_plan/12_frontend_integration_plan.md` (rollout order + DTO→props
  map for page 1), `frontend/src/features/bi/api/biApi.ts` + types, the page + its
  Recharts/`BiWidgets` components, the live IMPL-03/04 endpoint.
- **Allowed to modify:** `frontend/src/features/bi/api/*` (extend client+types), the ONE
  target page + its data hook; remove that page's placeholder values only.
- **Forbidden:** changing export behavior (PNG/PDF/Excel) here; touching other pages;
  changing chart visual design (only the data source).
- **Steps:** add typed client for the page's endpoint; a data hook with loading/empty/
  error; map DTO → existing chart props (do not redesign charts); delete the page's
  mock constants; verify in the running app.
- **Expected output:** page 1 renders real numbers; states preserved; build + typecheck green.
- **Validation:**
  - `cd frontend && npm run typecheck && npm run build` green.
  - In the running app the page shows real values (e.g. order_count 616 area) and the
    network tab hits `/v1/bi/*` (not `/v1/sales-dashboard` for this page).
  - Loading/empty/error states still work (test by stopping the backend).
- **Manual checklist:** [ ] real data on screen [ ] states intact [ ] export untouched [ ] no other page changed.
- **Commit:** `feat(bi-fe): wire <page1> to /v1/bi/* (IMPL-05)`.

## IMPL-06 — Wire the remaining 4 pages (one page per run, per BI-12 order)
- **Model:** GPT-5.5 high · skill `/vercel-react-best-practices`
- **Goal:** Repeat the IMPL-05 pattern for the remaining pages, **one page per run**,
  lowest-risk first, until all five render real data and placeholders are gone.
- **Inspect:** `../_plan/12_frontend_integration_plan.md` (per-page DTO→props), the
  page's endpoint, the IMPL-05 pattern.
- **Allowed to modify:** the ONE target page + shared api client/types/hooks.
- **Forbidden:** export changes; chart visual redesign; batching multiple pages in one
  commit (one page = one commit so regressions are isolated).
- **Steps:** per page → client+hook → DTO→props → remove placeholders → verify → commit →
  append milestone → STOP and report (so each page is reviewed before the next).
- **Expected output:** after the final run, all 5 pages on real data; `BiWidgets` mock
  constants removed; loading/empty/error intact.
- **Validation:** per page: typecheck+build green; real data on screen; states work;
  the page that has a partial mart (IMPL-02 gap) shows a clean empty state, not fake data.
- **Manual checklist:** [ ] one page per commit [ ] placeholders gone [ ] gaps show empty state.
- **Commit:** `feat(bi-fe): wire <page> to /v1/bi/* (IMPL-06: <page>)`.

---

# Phase D — Export + cleanup

## IMPL-07 — Backend `/v1/bi/export/*` `.xlsx`; redirect frontend Excel
- **Model:** GPT-5.5 high · skill `/java-springboot`
- **Goal:** Implement the backend `.xlsx` data export per BI-13 (real workbook from the
  same marts) and point the frontend "Excel" action at it; keep PNG/PDF frontend.
- **Inspect:** `../_design/13_export_reporting_design.md`, `frontend/.../utils/biExport.ts`
  (current fflate path), the marts, BI-10 filters.
- **Allowed to modify:** `.../bi/**` (export controller/service), `biExport.ts` (Excel
  branch → call backend; leave PNG/PDF as-is).
- **Forbidden:** introducing a .NET service; changing PNG/PDF; raw/business SQL in `bi`.
- **Steps:** export controller `GET /v1/bi/export/{page}` streaming `.xlsx` (correct MIME
  + Content-Disposition) built from marts with the same filters; frontend Excel button
  calls it and downloads the blob; PNG/PDF untouched.
- **Expected output:** Excel export downloads a real `.xlsx` from the server that opens
  in Excel; PNG/PDF unchanged.
- **Validation:** `./gradlew :llm-orchestrator:test` green; `curl -OJ` the endpoint →
  valid `.xlsx` (unzip/inspect); frontend build green; manual download opens in Excel.
- **Manual checklist:** [ ] real .xlsx MIME [ ] PNG/PDF frontend intact [ ] no .NET added.
- **Commit:** `feat(bi-export): backend xlsx export endpoint (IMPL-07)`.

## IMPL-08 — Trim legacy endpoints; final reconciliation + demo
- **Model:** GPT-5.5 high
- **Goal:** Now that all pages use `/v1/bi/*`, reduce/retire the legacy
  `SalesDashboardController` path and run the BI-14 reconciliation + demo checklist.
- **Inspect:** `../_plan/14_testing_strategy.md` (reconciliation gate + demo script),
  all migrated pages, `SalesDashboardController`/`Service`.
- **Allowed to modify:** legacy BI classes (deprecate/remove only after confirming no
  page or test uses them); add a final reconciliation test.
- **Forbidden:** removing anything still referenced; touching `business`.
- **Steps:** confirm no frontend/test references the legacy endpoints; deprecate or
  delete them (or reduce to a thin compatibility shim if anything external needs them);
  run the full reconciliation (every exposed KPI == business) and the 5-page demo script.
- **Expected output:** single BI serving path (`/v1/bi/*`); legacy retired/shimmed; all
  numbers reconcile; demo checklist green; app boots clean.
- **Validation:** full `./gradlew :llm-orchestrator:test` + `npm run build` green;
  reconciliation report shows all KPIs match `business`; manual demo of all 5 pages.
- **Manual checklist:** [ ] legacy safely retired [ ] all KPIs reconcile [ ] demo green.
- **Commit:** `refactor(bi): retire legacy dashboard path; final reconciliation (IMPL-08)`.

---

# Phase W — Deferred: full warehouse (do later, optional for the slice)

## IMPL-W — Build the warehouse and repoint marts (same contract)
- **Model:** GPT-5.5 high (multi-session; this is the heavy ETL track)
- **Goal:** Execute `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` (raw 6.5 GB →
  `staging` → `warehouse`) using the BI-06/07/09 designs, then **repoint the mart view
  bodies from `business.*` to `warehouse.*`** — without changing mart columns, so
  `/v1/bi/*` and the frontend are unaffected.
- **Inspect:** the rebuild roadmap, `../sql/staging.sql`, `../sql/warehouse.sql`,
  `../_plan/09_etl_plan.md`, the interim `implementation/sql/marts_business/*`.
- **Allowed to modify:** apply staging/warehouse DDL + ETL; replace mart view bodies.
- **Forbidden:** changing the mart **column contract** or any `/v1/bi/*` DTO; dropping `business`.
- **Steps:** run the rebuild roadmap (with its HUMAN-approval steps); validate warehouse
  vs business; swap each mart's `SELECT` to read `warehouse.*`; re-run the full
  reconciliation; confirm API + frontend unchanged.
- **Expected output:** marts now sourced from the governed warehouse (incl. 2024
  history) with identical columns; everything downstream unchanged.
- **Validation:** reconciliation still green; `/v1/bi/*` responses identical shape;
  frontend untouched and still correct.
- **Commit:** per the rebuild roadmap + `refactor(bi-mart): repoint marts to warehouse (IMPL-W)`.

---

## Execution order

`IMPL-01 → IMPL-02 → IMPL-03 → IMPL-04 → IMPL-05 → IMPL-06 (×4 pages) → IMPL-07 → IMPL-08`
then, when ready, the deferred `IMPL-W`.

**First visible win:** IMPL-05 (first real chart). **Slice complete:** IMPL-08.

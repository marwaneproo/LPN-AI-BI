# BI Implementation Roadmap — Ready-to-Run Prompts

Copy one block at a time. Run in order; don't start a task until the previous task's
milestone entry exists in `context_bi_implementation.md`. These tasks change **real
code/DB/app** — every prompt enforces: additive only, never touch `business`/`app`,
keep legacy endpoints live, reconcile before exposing, idempotent DDL, commit + pause.

**Mandatory closing block** (embedded in every prompt):

```
At the end of this task, append a new milestone entry to:
docs/bi-service-roadmap/implementation/context_bi_implementation.md
Append only; never overwrite; corrections = new CORRECTION entry. Use the §5 template:
date/time, task ID, title, summary, files/DB inspected, files created/modified,
DB changes applied (additive only), technical decisions, validation commands,
validation results, "app still boots & legacy intact?", problems, next task.
```

---

## IMPL-01 — mart schema + overview/payment marts over business
**Model:** GPT-5.5 high

```
Read docs/bi-service-roadmap/implementation/context_bi_implementation.md FIRST, then
IMPL_00_overview.md and IMPL_tasks.md (IMPL-01).

Task IMPL-01. Create the `mart` schema and the Vue-d'ensemble marts, sourced from
business.* (interim, IMPL-DECISION-01), exposing the BI-08 column contract. This is a
REAL DB change — additive only.

Rules: CREATE SCHEMA IF NOT EXISTS / CREATE OR REPLACE VIEW only. Never ALTER/DROP
business or app. Idempotent. Grant USAGE+SELECT on mart to lpn_ai_readonly.

Inspect: ../_design/08_mart_design.md, ../sql/marts.sql (warehouse-sourced target —
translate column-for-column to business sources), the existing SalesDashboardService
KPI SQL (this is the reconciliation oracle), and the live business.* columns
(psql -U lpn_ai_readonly -p 5432 -d lpn_ai_bi).

Build mart.mart_sales_daily, mart.mart_sales_monthly, mart.mart_payment_status (and any
overview rollup BI-08 defines) as views over business.*, honoring prior rules: LEFT JOIN
nullable C_ORDER_ID, ISPAID for paid/unpaid, DOCSTATUS filters, MAD. Put the SQL in
implementation/sql/marts_business/01_overview.sql and apply it.

Validate:
- psql ... -c "\dv mart.*" lists the views.
- mart monthly order_count for the previous month == 616 (matches /v1/sales-dashboard).
- lpn_ai_readonly can SELECT the views and cannot write.
If a mart cannot reconcile, mark BLOCKED and record the gap — do not expose it.

Commit: "feat(bi-mart): overview & payment marts over business (IMPL-01)". Then pause.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-02 — remaining page marts over business
**Model:** GPT-5.5 high

```
Read context_bi_implementation.md FIRST.

Task IMPL-02. Build the remaining BI-08 marts over business.*: mart_sales_by_commercial,
mart_sales_by_customer, mart_sales_by_product, mart_sales_by_region,
mart_order_to_invoice_flow, mart_stock_risk. Additive views only; same rules as IMPL-01.

Inspect: ../_design/08_mart_design.md, ../sql/marts.sql, and confirm which source tables
exist in business (stock RV_STORAGE, deliveries M_INOUT, payments) before authoring.
For any mart whose source is NOT in business, create the view shell returning a clearly
EMPTY/flagged result and record it as a known gap for IMPL-W — do NOT fabricate data.

Put SQL in implementation/sql/marts_business/02_pages.sql and apply it. Reconcile each
mart's headline total against an independent hand SQL over business.

Validate: \dv mart.* shows the full BI-08 set; each sourceable mart's total matches
business; gaps explicitly flagged; readonly can select all.
Commit: "feat(bi-mart): page marts over business (IMPL-02)". Then pause.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-03 — bi package + read-only datasource + /v1/bi/overview + tests
**Model:** GPT-5.5 high · skills `/java-springboot`, `/microservices-patterns`

```
Read context_bi_implementation.md FIRST.

Task IMPL-03. Create the BI-11 package boundary and the first real endpoint reading
mart.*. Do NOT modify the legacy SalesDashboardController.

Inspect: ../_design/11_extraction_decision.md (package + forbidden-imports + datasource),
../_design/10_bi_api_contract.md (overview DTO), sql-executor DataSourceConfig (read-only
datasource template), llm-orchestrator application.yml.

Build under services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/:
- infrastructure.config: bi.datasource.readonly.* (lpn_ai_readonly, search_path=mart,
  statement timeout) + a qualified biReadonlyJdbcTemplate.
- infrastructure.mart: a repository with STATIC SQL over mart.mart_sales_* only.
- application + api + api.dto: GET /v1/bi/overview per BI-10.
Tests under .../test/.../bi/: an architecture/import-boundary test (zero imports of
SqlGeneration/Qa/SchemaRetrieval/Predictive/SqlExecutorClient) and a contract test
asserting the overview response order_count == 616.

Validate:
- ./gradlew :llm-orchestrator:test green (incl. boundary + golden-number tests).
- App boots; curl localhost:8081/v1/bi/overview → 200 with 616; legacy /v1/sales-dashboard
  still 200.
If the boundary test fails (an import leaks), fix it before proceeding.
Commit: "feat(bi-api): bi package + /v1/bi/overview over marts (IMPL-03)". Then pause.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-04 — remaining /v1/bi/* endpoints + filters + tests
**Model:** GPT-5.5 high · skill `/java-springboot`

```
Read context_bi_implementation.md FIRST.

Task IMPL-04. Implement /v1/bi/{orders,revenue,articles,clients,commercial,analysis} per
BI-10, marts-only, with filters from/to/granularity/compare and page-specific filters.
Keep the bi package boundary (no forbidden imports, no business/raw SQL). Do not edit legacy.

Inspect: ../_design/10_bi_api_contract.md (all endpoints/DTOs/filters), the IMPL-02 marts,
../_plan/14_testing_strategy.md (contract tests).

For each endpoint: repository (static mart SQL) + service (filters + compare window) +
controller + DTO; every DTO field maps to a mart column. For marts that are partial gaps
(IMPL-02), return an explicit empty/flagged payload so the frontend shows an empty state.
Add a contract test per endpoint with a reconciled golden number.

Validate: ./gradlew :llm-orchestrator:test green; curl each endpoint → 200 matching BI-10;
spot-reconcile one number per endpoint vs business.
Commit: "feat(bi-api): remaining /v1/bi/* endpoints (IMPL-04)". Then pause.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-05 — wire the FIRST frontend page (charts go real)
**Model:** GPT-5.5 high · skill `/vercel-react-best-practices`

```
Read context_bi_implementation.md FIRST.

Task IMPL-05. Wire the FIRST page (per the BI-12 rollout order) to its /v1/bi/* endpoint
so its charts show REAL data. Keep loading/empty/error states. Do NOT change export
behavior, do NOT redesign charts (only swap the data source), do NOT touch other pages.

Inspect: ../_plan/12_frontend_integration_plan.md (rollout order + DTO→props map for page 1),
frontend/src/features/bi/api/biApi.ts + types, the target page and its Recharts/BiWidgets
components, and the live endpoint from IMPL-03/04.

Do: extend the typed API client + types for the page's endpoint; add a data hook with
loading/empty/error; map DTO fields to the EXISTING chart props; delete that page's mock
placeholder constants; verify in the running app.

Validate:
- cd frontend && npm run typecheck && npm run build green.
- In the running app the page shows real values and the network tab calls /v1/bi/*.
- Stop the backend → the page shows the loading/error state (not a crash).
Commit: "feat(bi-fe): wire <page1> to /v1/bi/* (IMPL-05)". Then pause and report which
page is next.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-06 — wire the remaining pages (ONE page per run)
**Model:** GPT-5.5 high · skill `/vercel-react-best-practices`

```
Read context_bi_implementation.md FIRST.

Task IMPL-06 (run once per page). Wire the NEXT page in the BI-12 rollout order to its
/v1/bi/* endpoint, following the exact IMPL-05 pattern. Do ONE page only this run; one
page = one commit so regressions stay isolated.

Inspect: ../_plan/12_frontend_integration_plan.md (this page's DTO→props), the page's
endpoint, the IMPL-05 implementation as the template.

Do: client+hook → DTO→props → remove this page's placeholders → keep loading/empty/error
→ verify. If this page's mart is a known gap (IMPL-02), show a clean EMPTY state, not fake
data. Do not touch export or chart visuals.

Validate: npm run typecheck && npm run build green; real data on screen; states intact.
Commit: "feat(bi-fe): wire <page> to /v1/bi/* (IMPL-06: <page>)". Then pause and report
the next page (repeat IMPL-06 until all 5 pages are wired).

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-07 — backend xlsx export + redirect frontend Excel
**Model:** GPT-5.5 high · skill `/java-springboot`

```
Read context_bi_implementation.md FIRST.

Task IMPL-07. Implement the backend Excel export per BI-13 and point the frontend "Excel"
action at it. Keep PNG/PDF on the frontend. Do NOT add a .NET service.

Inspect: ../_design/13_export_reporting_design.md, frontend/src/features/bi/utils/biExport.ts
(current fflate Excel path), the marts, BI-10 filters.

Do: a bi export controller GET /v1/bi/export/{page} streaming a real .xlsx (correct MIME
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet + Content-Disposition)
built from the same marts with the same filters; update biExport.ts so the Excel button
calls the backend and downloads the blob; leave PNG/PDF capture untouched.

Validate: ./gradlew :llm-orchestrator:test green; curl -OJ the endpoint → valid .xlsx
(unzip to confirm OOXML parts); frontend build green; manual download opens in Excel.
Commit: "feat(bi-export): backend xlsx export endpoint (IMPL-07)". Then pause.

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-08 — trim legacy endpoints + final reconciliation + demo
**Model:** GPT-5.5 high

```
Read context_bi_implementation.md FIRST.

Task IMPL-08. With all pages on /v1/bi/*, retire/trim the legacy SalesDashboardController
path and run the BI-14 reconciliation + 5-page demo. Never touch business.

Inspect: ../_plan/14_testing_strategy.md (reconciliation gate + demo script), all migrated
pages, SalesDashboardController/Service.

Do: confirm no frontend code or test references the legacy endpoints; then deprecate or
remove them (or reduce to a thin shim only if something external still needs them); add a
final reconciliation test proving every exposed KPI == business value; run the demo script.

Validate: full ./gradlew :llm-orchestrator:test and cd frontend && npm run build green;
reconciliation report shows all KPIs match business; manual demo of all 5 pages passes;
app boots clean via scripts/launch-local-native.ps1.
Commit: "refactor(bi): retire legacy dashboard path; final reconciliation (IMPL-08)".
Then report the slice COMPLETE and whether to schedule IMPL-W (full warehouse).

<MANDATORY CLOSING BLOCK>
```

---

## IMPL-W — deferred: build warehouse, repoint marts (same contract)
**Model:** GPT-5.5 high (multi-session, heavy ETL)

```
Read context_bi_implementation.md FIRST. Only start IMPL-W when the slice (IMPL-01..08) is
done and you explicitly choose to do the full warehouse.

Task IMPL-W. Execute docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md (raw 6.5GB →
staging → warehouse) using the BI-06/07/09 designs and ../sql/staging.sql + ../sql/warehouse.sql,
respecting its HUMAN-approval steps and chunked reads. Then REPOINT each mart view body
from business.* to warehouse.* WITHOUT changing mart columns, so /v1/bi/* and the frontend
are unaffected.

Validate: warehouse reconciles to business; after repoint, the full reconciliation is
still green and /v1/bi/* responses are byte-identical in shape; frontend unchanged.
Commit per the rebuild roadmap + "refactor(bi-mart): repoint marts to warehouse (IMPL-W)".

<MANDATORY CLOSING BLOCK>
```

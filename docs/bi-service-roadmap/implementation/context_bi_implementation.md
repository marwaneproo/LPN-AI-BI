# BI Implementation — Continuity Log (APPEND-ONLY)

> **Future AI agents: READ THIS FILE FIRST before any IMPL task.** It is the
> continuity memory for the BI BUILD phase (real code, real DB, real app). The
> planning phase trail is in `../context_bi_service.md` (BI-00…BI-14, complete).

## 1. Rules
1. **Append-only.** Never edit/delete prior entries. Corrections = new `CORRECTION` entry.
2. **Every IMPL task appends one milestone entry** (template in §5).
3. **Read before you write.** Verify state against the repo/DB; don't assume.
4. **Ground claims in real output** (psql results, test logs, HTTP responses).

## 2. Phase context (as of 2026-06-25)

- Strategy: **incremental slice** — mart views over `business.*` now; warehouse
  deferred (IMPL-W). See `IMPL_00_overview.md`.
- Native stack: PG18 @ localhost:5432, db `lpn_ai_bi`, read-only role
  `lpn_ai_readonly`. Services via `scripts/launch-local-native.ps1`.
- Live baseline (golden numbers from `/v1/sales-dashboard`): `order_count = 616`,
  `active_customers = 147`, `invoiced_sales ≈ 2,848,655.68`, `invoice_coverage ≈ 89.35%`.
- BI logic currently in `llm-orchestrator` (`SalesDashboardController/Service`); the
  new BI code goes in package `com.lpn.aibi.llmorchestrator.bi` (BI-11 boundary).
- `business` table counts: c_orderline 354,910, c_invoiceline 335,877, m_product 35,822,
  fact_sales_monthly 25.

## 3. Locked decisions (supersede only via a CORRECTION entry)

- **IMPL-DECISION-01:** Interim mart source = `business.*`; mart **column contract =
  BI-08**; warehouse repoint deferred to IMPL-W without changing columns.
- Marts are additive `CREATE OR REPLACE VIEW` in schema `mart`; `business`/`app`
  never modified.
- Reconciliation gate (BI-14): no page repointed until mart value == business value.
- BI package is read-only (`lpn_ai_readonly`, `search_path=mart`), zero
  LLM-orchestration imports (BI-11 forbidden-imports list).

## 4. Task status table (convenience; milestone log is source of truth)

| Task | Title | Phase | Status |
|---|---|---|---|
| IMPL-01 | `mart` schema + overview/payment marts over business + reconcile | A | ⬜ Not started |
| IMPL-02 | Remaining page marts over business + reconcile | A | ⬜ Not started |
| IMPL-03 | Scaffold `bi` package + read-only datasource + `/v1/bi/overview` + tests | B | ⬜ Not started |
| IMPL-04 | Remaining `/v1/bi/*` endpoints + filters + tests | B | ⬜ Not started |
| IMPL-05 | Wire FIRST frontend page to real API (charts go real) | C | ⬜ Not started |
| IMPL-06 | Wire remaining 4 pages (one per run, per BI-12 order) | C | ⬜ Not started |
| IMPL-07 | Backend `/v1/bi/export/*` `.xlsx`; redirect frontend Excel | D | ⬜ Not started |
| IMPL-08 | Trim legacy endpoints; final reconciliation + demo | D | ⬜ Not started |
| IMPL-W | Deferred: build warehouse, repoint marts (same columns) | W | ⬜ Deferred |

Legend: ⬜ Not started · 🟦 In progress · ✅ Done · 🟥 Blocked · ♻️ Corrected · 💤 Deferred.

## 5. Milestone entry template (copy for every task)

```markdown
### [<YYYY-MM-DD HH:MM>] <IMPL-XX> — <Task title>

- **Type:** milestone | CORRECTION (of <IMPL-XX>)
- **Model/effort used:** <e.g. GPT-5.5 high>
- **Summary:** <what was built, 2–4 lines>
- **Files/DB objects inspected:** <paths / schemas>
- **Files created/modified:** <paths>
- **DB changes applied:** <schemas/views created, or "none"> (must be additive only)
- **Technical decisions:** <decisions + rationale; link to a design doc>
- **Validation commands run:** <psql / gradle test / curl / npm>
- **Validation results:** <pass/fail + reconciliation numbers, e.g. mart order_count=616 == business>
- **App still boots & legacy endpoints intact?:** <yes/no + evidence>
- **Problems encountered:** <issues, or "none">
- **Next recommended task:** <IMPL-XX + one-line why>
```

## 6. Milestone Log

<!-- APPEND NEW ENTRIES BELOW. NEWEST AT THE BOTTOM. NEVER EDIT ABOVE. -->

### [2026-06-25] IMPL-ROADMAP-INIT — Implementation roadmap created

- **Type:** milestone
- **Model/effort used:** Opus 4.8 medium
- **Summary:** Created the implementation roadmap (IMPL_00_overview, IMPL_tasks,
  IMPL_prompts, this log) for the incremental-slice build: marts over `business` →
  `/v1/bi/*` → frontend wiring → export. No code or DB changes yet.
- **Files/DB objects inspected:** planning artifacts under `docs/bi-service-roadmap/`
  (BI-08/10/11/12/13/14), live `business` schema counts and `/v1/sales-dashboard`.
- **Files created/modified:** `implementation/IMPL_00_overview.md`,
  `implementation/IMPL_tasks.md`, `implementation/IMPL_prompts.md`,
  `implementation/context_bi_implementation.md`.
- **DB changes applied:** none.
- **Technical decisions:** IMPL-DECISION-01 (interim mart source = business; contract
  = BI-08; warehouse deferred).
- **Validation commands run:** none (planning).
- **Validation results:** n/a.
- **App still boots & legacy endpoints intact?:** yes (untouched).
- **Problems encountered:** none.
- **Next recommended task:** **IMPL-01** — create the `mart` schema and the
  overview/payment marts over `business`, reconciled to the live dashboard.

### [2026-06-25 21:56] IMPL-01 — mart schema + overview/payment marts over business

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Created the live additive `mart` schema and the first three
  interim business-sourced marts for the Vue d'ensemble slice:
  `mart_sales_daily`, `mart_sales_monthly`, and `mart_payment_status`. The views
  preserve the BI-08 column contract while sourcing from `business.*`
  per IMPL-DECISION-01. The May 2026 monthly mart reconciles to the business
  oracle with `order_count = 616`, and invoice/payment status totals also match.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/implementation/IMPL_00_overview.md`,
  `docs/bi-service-roadmap/implementation/IMPL_tasks.md`,
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardService.java`,
  `services-java/llm-orchestrator/src/main/resources/application.yml`,
  live `business.c_order`, `business.c_invoice`, `business.c_allocationline`,
  `business.c_allocationhdr`, `business.c_payment`, `business.c_bpartner`,
  `business.ad_user`, and new `mart.*` views in PostgreSQL.
- **Files created/modified:** `docs/bi-service-roadmap/implementation/sql/marts_business/01_overview.sql`,
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** Additive only. Ran
  `CREATE SCHEMA IF NOT EXISTS mart`, `CREATE OR REPLACE VIEW` for
  `mart.mart_sales_daily`, `mart.mart_sales_monthly`, and
  `mart.mart_payment_status`, plus `GRANT USAGE ON SCHEMA mart` and `GRANT SELECT`
  on the three views to `lpn_ai_readonly`. No `business` or `app` objects were
  altered, dropped, inserted, updated, deleted, or truncated.
- **Technical decisions:** The interim business-sourced views keep BI-08 column
  names exactly for the three IMPL-01 marts. Warehouse surrogate key columns are
  represented by source IDs during the interim phase (`c_bpartner_id` as
  `customer_key`, salesrep/commercial user ID as `commercial_key`, `YYYYMMDD`
  integer as `date_key`), with `0` for unknown where needed. The views honor
  BI-05 status rules (`C_ORDER.DOCSTATUS IN ('CO','CL')`,
  `C_INVOICE.DOCSTATUS = 'CO'`), keep nullable invoice-to-order relationships
  optional by not requiring an order join, use `ISPAID` for paid/unpaid status,
  and expose MAD money amounts from `GRANDTOTAL` / allocation amounts.
- **Validation commands run:**
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT current_user, current_database();"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema='business' ..."`
  - `rg -n "\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE|INSERT)\b" docs/bi-service-roadmap/implementation/sql/marts_business/01_overview.sql`
  - `psql -h localhost -U lpn_app_admin -p 5432 -d lpn_ai_bi -v ON_ERROR_STOP=1 -f docs/bi-service-roadmap/implementation/sql/marts_business/01_overview.sql`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "\dv mart.*"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<business-vs-mart May 2026 order_count reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<business-vs-mart May 2026 invoice/payment reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT 'daily' ... FROM mart.mart_sales_daily UNION ALL ..."`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -v ON_ERROR_STOP=1 -c "CREATE TABLE mart.__readonly_write_probe (id integer);"`
  - Re-ran the same `01_overview.sql` with `ON_ERROR_STOP=1` to prove idempotency.
  - `Invoke-RestMethod http://localhost:8081/v1/sales-dashboard` and
    `Invoke-RestMethod http://localhost:8080/v1/sales-dashboard` as legacy endpoint
    availability checks.
- **Validation results:** PASS for DB creation, grants, idempotency, readonly
  SELECT, readonly write denial, and reconciliation. `\dv mart.*` lists
  `mart_payment_status`, `mart_sales_daily`, and `mart_sales_monthly`, owned by
  `lpn_app_admin`. Row counts are `mart_sales_daily = 423`,
  `mart_sales_monthly = 18`, `mart_payment_status = 7476`. May 2026
  reconciliation: business `order_count = 616`, mart `order_count = 616`;
  business `ca_commande = 3188055.9197499999938`, mart `ca_commande` matches;
  business invoice count `241`, mart invoice count `241`; business/mart
  `ca_facture = 2848655.68`; paid invoices `13`; unpaid invoices `228`;
  unpaid amount `2629494.22`. `lpn_ai_readonly` can select from the views and
  `CREATE TABLE mart.__readonly_write_probe` fails with `permission denied for
  schema mart`, as expected.
- **App still boots & legacy endpoints intact?:** Not verified by HTTP in this
  run because `localhost:8081` refused the connection and `localhost:8080`
  returned 404 for `/v1/sales-dashboard`; the local Spring app was not listening
  on the expected native dashboard port. No Java, frontend, Docker, app config,
  `business`, or `app` object was modified, so the IMPL-01 change is isolated to
  additive mart views and grants.
- **Problems encountered:** Initial readonly `psql` attempts waited for an
  interactive password prompt; they were replaced with explicit `PGPASSWORD`
  from the native launch script defaults. The forbidden-DDL grep reports
  `ALTER/DROP` only inside the SQL file's safety comment, not in executable SQL.
  The local app endpoint was unavailable for HTTP verification.
- **Next recommended task:** **IMPL-02** — create the remaining BI-08 page marts
  over `business.*` and reconcile each to independent business aggregates, so the
  API layer can later serve all `/v1/bi/*` endpoints from `mart.*`.

### [2026-06-25 22:22] IMPL-02 — remaining page marts over business

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Created the remaining six BI-08 marts over `business.*`:
  `mart_sales_by_commercial`, `mart_sales_by_customer`,
  `mart_sales_by_product`, `mart_sales_by_region`,
  `mart_order_to_invoice_flow`, and `mart_stock_risk`. All six are sourceable
  from the current `business` schema, so no empty shell view was needed. The
  full BI-08 mart set now exists in PostgreSQL with nine views.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`, live `business` table inventory
  (`ad_user`, `c_order`, `c_orderline`, `c_invoice`, `c_invoiceline`,
  `c_doctype`, `c_bpartner`, `c_bpartner_location`, `c_location`, `c_city`,
  `c_region`, `c_salesregion`, `m_product`, `m_product_category`,
  `m_product_type`, `m_product_theme`, `m_product_collection`, `m_product_po`,
  `c_bpartner_vendor`, `rv_storage`, `m_warehouse`, `m_inout`, `m_inoutline`,
  `business.v_product_primary_supplier`, `business.v_salesrep_user`), and all
  `mart.*` views.
- **Files created/modified:** `docs/bi-service-roadmap/implementation/sql/marts_business/02_pages.sql`,
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** Additive only. Ran `CREATE SCHEMA IF NOT EXISTS mart`,
  `CREATE OR REPLACE VIEW` for six remaining page marts, and `GRANT SELECT` on
  the six views to `lpn_ai_readonly`. No `business` or `app` objects were
  altered, dropped, inserted, updated, deleted, or truncated.
- **Technical decisions:** Source IDs continue to stand in for warehouse
  surrogate keys during the interim phase. Salesrep/commercial identity uses
  `COALESCE(salesrep_id, commercial_id, ad_user_id, 0)` where available and
  labels through `business.v_salesrep_user` / `business.ad_user`. Product
  supplier labels use `business.v_product_primary_supplier` to avoid multiplying
  invoice-line revenue by all `M_PRODUCT_PO` rows. Geography uses
  invoice/order partner location -> location/city/region/salesregion joins.
  Order-flow coverage uses `C_ORDERLINE.QTYDELIVERED` and `QTYINVOICED`, with
  invoice-line amount joined by nullable `C_ORDERLINE_ID`. Stock is sourced from
  `business.rv_storage`; because all `DATELASTINVENTORY` values are null, the
  interim stock mart treats `rv_storage` as a current snapshot and uses
  `CURRENT_DATE` as `snapshot_date`. This date behavior must be replaced by a
  governed snapshot date in IMPL-W.
- **Validation commands run:**
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT tablename FROM pg_tables WHERE schemaname='business' ORDER BY tablename;"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT table_name FROM information_schema.tables WHERE table_schema='business' AND table_name IN (...);"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT 'c_orderline' AS table_name, COUNT(*) FROM business.c_orderline UNION ALL ..."`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT table_name, string_agg(column_name, ', ' ...) FROM information_schema.columns WHERE table_schema='business' ..."`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "SELECT COUNT(*) AS rv_rows, COUNT(datelastinventory) AS dated_rows, ... FROM business.rv_storage;"`
  - `rg -n "\b(ALTER|DROP|TRUNCATE|DELETE|UPDATE|INSERT)\b" docs/bi-service-roadmap/implementation/sql/marts_business/02_pages.sql`
  - `psql -h localhost -U lpn_app_admin -p 5432 -d lpn_ai_bi -v ON_ERROR_STOP=1 -f docs/bi-service-roadmap/implementation/sql/marts_business/02_pages.sql`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "\dv mart.*"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<row counts for all nine mart views>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<top commercial CA reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<top customer CA reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<top product CA reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<top region CA reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<order-flow totals reconciliation>"`
  - `psql -h localhost -U lpn_ai_readonly -p 5432 -d lpn_ai_bi -c "<stock quantity totals reconciliation>"`
  - Re-ran `02_pages.sql` with `ON_ERROR_STOP=1` to prove idempotency.
  - `Invoke-RestMethod http://localhost:8081/v1/sales-dashboard` as legacy
    endpoint availability check.
- **Validation results:** PASS for DB creation, grants, idempotency, readonly
  SELECT, source inventory, and reconciliation. `\dv mart.*` lists the full
  BI-08 set: nine views. View row counts are `mart_sales_daily = 423`,
  `mart_sales_monthly = 18`, `mart_sales_by_commercial = 3464`,
  `mart_sales_by_customer = 19502`, `mart_sales_by_product = 315038`,
  `mart_sales_by_region = 5686`, `mart_order_to_invoice_flow = 3393`,
  `mart_payment_status = 7476`, and `mart_stock_risk = 48424`.
  `business.c_orderline` remains `354910`. Reconciliation: top commercial
  `midbymed` (`1005712`) CA facture matches business and mart at `59159946.97`;
  top customer `ETABLISSEMENT EDITION EL-HOUDA` (`20031107`) matches at
  `10583577.61`; top product `MES APPRENTISSAGES EN FRANCAIS C3 ELEVE EDITION 2019`
  (`3495850`) matches at `2549302.77`; top region/city Casablanca
  (`301992045`) matches at `10872493.61`; order-flow totals match with
  `354910` lines, `ca_commande = 209113706.989500000049705`, and
  `ca_facture_ligne = 194053118.96`; stock totals match with
  `qty_available = 642701`, `qty_on_hand = 1251211`,
  `qty_reserved = 608510`, and `qty_ordered = 6505513`. No sourceable mart gap
  was found; the only IMPL-W caveat is replacing the interim `CURRENT_DATE`
  stock snapshot date once governed stock snapshots exist.
- **App still boots & legacy endpoints intact?:** Not verified by HTTP in this
  run because `localhost:8081` refused the connection for `/v1/sales-dashboard`.
  No Java, frontend, Docker, app config, `business`, or `app` object was
  modified, so the IMPL-02 change is isolated to additive mart views and grants.
- **Problems encountered:** The broad live-column inventory was too large and
  truncated, so it was followed by targeted column checks for join and metric
  columns. `rv_storage.datelastinventory` is null for all rows; IMPL-02 uses
  `CURRENT_DATE` as a current-snapshot placeholder and records this for IMPL-W.
  Two interim views list `metric_date` before `date_key` in PostgreSQL column
  order because they were created that way; all BI-08 named columns are present,
  and reordering existing view columns would require `DROP`, which is forbidden
  in this additive-only task.
- **Next recommended task:** **IMPL-03** — scaffold the Spring `bi` package,
  configure the read-only mart datasource, and implement `/v1/bi/overview` over
  the now-complete mart layer.

### [2026-06-25 22:44] IMPL-03 — BI package + `/v1/bi/overview` over marts

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Created the first real Spring BI package boundary inside
  `llm-orchestrator` without touching the legacy `SalesDashboardController`.
  Added a dedicated read-only mart datasource, static mart repository queries,
  `/v1/bi/overview`, DTO records aligned to the BI-10 overview contract, and
  tests for the package boundary plus the reconciled `order_count = 616` contract.
- **Files/DB objects inspected:** `implementation/context_bi_implementation.md`,
  `implementation/IMPL_tasks.md`, `_design/11_extraction_decision.md`,
  `_design/10_bi_api_contract.md`,
  `services-java/sql-executor/src/main/java/com/lpn/aibi/sqlexecutor/DataSourceConfig.java`,
  `services-java/llm-orchestrator/src/main/resources/application.yml`,
  `mart.mart_sales_daily`, `mart.mart_sales_monthly`,
  `mart.mart_sales_by_commercial`, `mart.mart_sales_by_customer`,
  `mart.mart_sales_by_product`, `mart.mart_sales_by_region`,
  `mart.mart_order_to_invoice_flow`, and `mart.mart_payment_status`.
- **Files created/modified:** `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/BiOverviewController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiOverviewResponse.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/application/BiOverviewService.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/infrastructure/config/BiDataSourceConfig.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/infrastructure/mart/BiOverviewMartRepository.java`,
  `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/bi/BiPackageBoundaryTest.java`,
  `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/bi/BiOverviewControllerTest.java`,
  `services-java/llm-orchestrator/src/main/resources/application.yml`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-03 is application code only; no DDL or
  writes were run against PostgreSQL.
- **Technical decisions:** Followed BI-11 package-first extraction: all new code
  lives under `com.lpn.aibi.llmorchestrator.bi`, uses a qualified
  `biReadonlyJdbcTemplate`, and does not import LLM orchestration, QA,
  predictive, schema-retrieval, or SQL-executor clients. SQL is static and
  qualified against `mart.*`. Because the interim business-backed
  `mart_sales_by_product` view exceeds the 30s online timeout, the first
  `/v1/bi/overview` slice keeps the response contract stable but returns
  product-backed online fields as explicit placeholders: `top_supplier =
  UNKNOWN`, `top_products = []`, and `sales_mix.tracked_products = 0`. This is
  a temporary IMPL-W performance gap to resolve by materializing/indexing marts
  before product widgets are repointed.
- **Validation commands run:** `psql -U lpn_ai_readonly -p 5432 -d lpn_ai_bi`
  column checks for the overview marts; direct mart aggregate SQL for May 2026
  order count; `cd services-java && .\gradlew.bat :llm-orchestrator:test`;
  `cd services-java && .\gradlew.bat :llm-orchestrator:bootJar`; live
  `java -jar` run of `llm-orchestrator` on port `8081`; live run of
  `sql-validator` on `8086` and `sql-executor` on `8082` for legacy validation;
  `curl http://localhost:8081/v1/bi/overview`; and
  `curl http://localhost:8081/v1/sales-dashboard`.
- **Validation results:** PASS for compile, test suite, package-boundary test,
  and `/v1/bi/overview` contract. Direct mart SQL returned
  `order_count = 616` and `order_value = 3188055.9197499999938` for
  `2026-05-01 <= metric_date < 2026-06-01`. Gradle reported
  `BUILD SUCCESSFUL` for `:llm-orchestrator:test` and `:llm-orchestrator:bootJar`.
  Live `/v1/bi/overview` returned HTTP 200 with
  `data.kpis.order_count = 616`. Live `/v1/sales-dashboard` returned HTTP 200
  with legacy `kpis.order_count = 616` after starting its required
  `sql-validator` and `sql-executor` dependencies.
- **App still boots & legacy endpoints intact?:** yes. The orchestrator boot jar
  started on port `8081`; `/v1/bi/overview` returned 200 with the mart-backed
  616 count; `/v1/sales-dashboard` returned 200 once the existing legacy
  dependency chain (`sql-validator` + `sql-executor`) was running. The legacy
  controller and service files were not modified.
- **Problems encountered:** The first noninteractive `psql` schema check omitted
  `PGPASSWORD` and left a password-prompt process, which was stopped. The
  interim `mart_sales_by_product` view timed out under the 30s statement
  timeout, so product-backed overview fields are deliberately not queried in
  this first online endpoint. Legacy `/v1/sales-dashboard` initially returned
  500 when `sql-executor` and then `sql-validator` were not running; it returned
  200 after those existing services were started.
- **Next recommended task:** **IMPL-04** — implement the remaining `/v1/bi/*`
  endpoints, filters, and contract tests over the mart layer.

### [2026-06-26 09:02] IMPL-04 — remaining `/v1/bi/*` endpoints + materialized heavy marts

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Implemented the remaining BI-10 endpoints:
  `/v1/bi/orders`, `/v1/bi/revenue`, `/v1/bi/articles`,
  `/v1/bi/clients`, `/v1/bi/commercial`, and `/v1/bi/analysis`.
  Added endpoint-specific DTOs, services, controllers, mart repositories, and
  MockMvc contract tests with one reconciled golden value per endpoint. Also
  fixed the IMPL-03 overview placeholders now that the product mart is fast.
- **Files/DB objects inspected:** `implementation/context_bi_implementation.md`,
  `implementation/IMPL_00_overview.md`, `implementation/IMPL_tasks.md`,
  `_design/10_bi_api_contract.md`, `_plan/14_testing_strategy.md`,
  `implementation/sql/marts_business/01_overview.sql`,
  `implementation/sql/marts_business/02_pages.sql`, current BI package files,
  and live `mart.*` / `business.*` objects in PostgreSQL.
- **Files created/modified:** `docs/bi-service-roadmap/implementation/sql/marts_business/03_materialize.sql`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/*Controller.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiEnvelope.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/Bi*Response.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/application/Bi*.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/infrastructure/mart/Bi*MartRepository.java`,
  `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/bi/BiOverviewControllerTest.java`,
  and `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** mart schema only. Replaced
  `mart.mart_sales_by_product` and `mart.mart_order_to_invoice_flow` with
  materialized views of the same names and same BI-08 columns, created endpoint
  indexes on date/filter/sort columns, granted `SELECT` to `lpn_ai_readonly`,
  and ran `ANALYZE`. No `business` or `app` object was modified.
- **Technical decisions:** **IMPL-DECISION-02:** measured heavy marts are
  materialized views, refreshed after ETL. Initial profiling showed
  `mart_sales_by_product` at `41777 ms` for a realistic May 2026 top-products
  query, while `mart_sales_by_customer = 403 ms`, `mart_stock_risk = 462 ms`,
  `mart_sales_by_commercial = 166 ms`, `mart_order_to_invoice_flow = 432 ms`,
  and `mart_payment_status = 102 ms`. During endpoint tests, the repeated
  orders endpoint aggregations over plain `mart_order_to_invoice_flow` exceeded
  the 30s timeout, so it was also promoted. Light marts remain plain views.
  The BI package remains marts-only and read-only; every repository query uses
  `mart.*` and no forbidden LLM/orchestration clients are imported.
- **Validation commands run:** mart profiling with representative filtered
  `SELECT`s; `psql -U lpn_app_admin -p 5432 -d lpn_ai_bi -v ON_ERROR_STOP=1 -f
  docs/bi-service-roadmap/implementation/sql/marts_business/03_materialize.sql`;
  readonly matview/select/write-denial checks; `cd services-java &&
  .\gradlew.bat :llm-orchestrator:test`; `cd services-java && .\gradlew.bat
  :llm-orchestrator:bootJar`; live `java -jar` run of `llm-orchestrator` on
  `8081`; live `sql-validator` on `8086` and `sql-executor` on `8082` for the
  legacy dashboard; `curl` for all `/v1/bi/*` endpoints and
  `/v1/sales-dashboard`; independent business reconciliation SQL; and
  `SELECT COUNT(*) FROM business.c_orderline`.
- **Validation results:** PASS. `:llm-orchestrator:test` and
  `:llm-orchestrator:bootJar` both reported `BUILD SUCCESSFUL`. Boundary tests
  passed. Live endpoint timings after BI pool warm-up: `/v1/bi/overview`
  `200` in `0.734s` (`order_count=616`), `/v1/bi/orders` `200` in `0.055s`
  (`order_count=616`), `/v1/bi/revenue` `200` in `0.141s`
  (`invoiced_sales=2848655.68`), `/v1/bi/articles` `200` in `0.860s`
  (`active_products=4336`), `/v1/bi/clients` `200` in `1.435s`
  (`active_customers=175`), `/v1/bi/commercial` `200` in `0.643s`
  (`commercial_count=15`), and `/v1/bi/analysis` `200` in `1.250s`
  (`order_count=616`). Legacy `/v1/sales-dashboard` returned `200` in
  `0.983s` with `order_count=616`. Independent business SQL returned the same
  golden values: overview/orders/analysis order count `616`, revenue invoiced
  sales `2848655.68`, article active products `4336`, client active customers
  `175`, commercial count `15`, and `business.c_orderline = 354910`.
- **App still boots & legacy endpoints intact?:** yes. The orchestrator boot jar
  started on port `8081`; all seven `/v1/bi/*` endpoints returned HTTP 200
  under two seconds; `/v1/sales-dashboard` returned HTTP 200 once its existing
  `sql-validator` and `sql-executor` dependency chain was running. Legacy
  `SalesDashboardController` was not modified.
- **Problems encountered:** PostgreSQL requires object-type-specific drops, so
  `03_materialize.sql` detects whether each mart object is a view or
  materialized view before replacing it. Nullable optional filter parameters
  needed explicit JDBC SQL types for predicates such as `:supplier IS NULL`.
  Text-valued analysis filter options required string ids. The first
  order-flow implementation timed out as a plain view, leading to the second
  materialized-view promotion under IMPL-DECISION-02.
- **Next recommended task:** **IMPL-05** — wire the first frontend BI page to
  the real `/v1/bi/*` API so charts start showing live mart-backed data.

### [2026-06-26 09:25] IMPL-05 — Vue d'ensemble wired to `/v1/bi/overview`

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Wired the first frontend BI page, `BiOverviewPage`, to the real
  mart-backed `/v1/bi/overview` endpoint. Added typed BI overview DTOs, a
  `fetchBiOverview()` client, and a `useBiOverview()` hook with loading, empty,
  and retryable error behavior. Removed only the overview page's placeholder
  constants; the legacy live dashboard and all other BI pages remain untouched.
- **Files/DB objects inspected:** `implementation/context_bi_implementation.md`,
  `implementation/IMPL_tasks.md`, `_plan/12_frontend_integration_plan.md`,
  `frontend/src/features/bi/api/biApi.ts`, `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/pages/BiOverviewPage.tsx`,
  `frontend/src/features/bi/pages/DashboardOverviewPage.tsx`,
  `frontend/src/features/bi/hooks/useSalesDashboard.ts`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `frontend/src/features/bi/components/controls/BiExportDialog.tsx`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiOverviewResponse.java`,
  and live `/v1/bi/overview`.
- **Files created/modified:** `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiOverview.ts`,
  `frontend/src/features/bi/pages/BiOverviewPage.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-05 is frontend/API-client wiring only; no
  DDL or writes were run against PostgreSQL.
- **Technical decisions:** Kept snake_case DTO fields at the network boundary
  and transformed only presentational props in `useBiOverview()`. The overview
  page calls `/v1/bi/overview` alongside the existing legacy clients rather than
  replacing `/v1/sales-dashboard` globally. DTO-to-widget mapping: `kpis.invoiced_sales`
  -> `CA facturé`, `kpis.order_value` + `kpis.order_count` -> `Ventes commandées`
  with the visible `616 commandes` helper, `kpis.active_customers` + `kpis.invoice_count`
  -> `Clients actifs`, `kpis.invoice_coverage_percent` + paid/unpaid counts ->
  `Couverture facture`, `trend[].period/order_value` -> `BiColumnPreview`,
  `quick_signals.*.name/value` -> `BiSignalList`, and `sales_mix.order_value`,
  `sales_mix.invoiced_sales`, `sales_mix.tracked_products` -> the mix signal rows.
  The existing percentage-only `BiBarPreview` was not used for the mixed money/count
  DTO because that would have mislabeled real mart values as percentages.
- **Validation commands run:** `cd frontend && npm run typecheck`; `cd frontend &&
  npm run build`; live `java -jar` run of `llm-orchestrator` on `8081` with the
  BI readonly mart datasource; live `npm run dev` on `127.0.0.1:5173`; direct
  `Invoke-RestMethod http://localhost:8081/v1/bi/overview`; Playwright/Chrome
  browser checks against `/tableau-de-bord/vue-ensemble` with a seeded local
  session; backend-stop error-state check; PNG export smoke test from the
  real-data overview DOM.
- **Validation results:** PASS. Typecheck and production build both completed
  successfully. Direct `/v1/bi/overview` returned HTTP 200 with
  `data.kpis.order_count = 616`, `order_value = 3188055.91975`,
  `active_customers = 175`, `invoice_count = 241`, and
  `invoiced_sales = 2848655.68`. Browser validation saw `616 commandes` on the
  overview page, observed `/api/v1/bi/overview` through the Vite proxy, and
  observed no `/v1/sales-dashboard` request for this page. With the backend
  stopped, the page showed `Impossible de charger la vue d'ensemble BI` and a
  retry action instead of crashing. PNG export downloaded
  `lpn-bi-vue-d-ensemble-janv-2026-juin-2026.png`.
- **App still boots & legacy endpoints intact?:** yes for this frontend slice.
  The orchestrator boot jar started on `8081`, `/v1/bi/overview` returned 200,
  and the frontend dev server rendered the protected overview page after seeding
  the existing client session state. No Java code, legacy `SalesDashboardController`,
  `DashboardOverviewPage`, `useSalesDashboard()`, `fetchSalesDashboard()`, or
  export utility code was modified. A direct login attempt returned 500 because
  the current app datasource user cannot create the `app` schema/tables; that
  pre-existing auth bootstrap issue was bypassed only for browser validation.
- **Problems encountered:** Playwright's bundled Chromium was not installed, so
  browser validation used the local Chrome executable. The protected frontend
  route required seeding `sessionStorage` because `/v1/auth/login` failed during
  validation with `permission denied for database lpn_ai_bi` while trying to
  bootstrap `app.ai_bi_users`. Vite reports backend proxy failures as HTTP 500
  when the backend is stopped; the page handled that as the expected retryable
  error state.
- **Next recommended task:** **IMPL-06: BiRevenuePage** — continue the BI-12
  rollout with the lowest-risk remaining detail page, wiring it to
  `/v1/bi/revenue` using the IMPL-05 client/hook pattern.

### [2026-06-26 10:16] IMPL-06 — BiRevenuePage wired to `/v1/bi/revenue`

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Wired the `BiRevenuePage` / Chiffre d'affaires page to the real
  mart-backed `/v1/bi/revenue` endpoint, following the IMPL-05 overview
  client/hook pattern. Added typed revenue DTOs, `fetchBiRevenue()`, and a
  `useBiRevenue()` hook with loading, empty-trend, retryable error, and
  refresh behavior. Removed only this page's placeholder values; export,
  overview, and the other BI pages were not modified.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/implementation/IMPL_tasks.md`,
  `docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiOverview.ts`,
  `frontend/src/features/bi/pages/BiRevenuePage.tsx`,
  `frontend/src/features/bi/pages/BiOverviewPage.tsx`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiRevenueResponse.java`,
  `BiRevenueController.java`, `BiRevenueService.java`,
  `BiRevenueMartRepository.java`, and live `/v1/bi/revenue`.
- **Files created/modified:** `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiRevenue.ts`,
  `frontend/src/features/bi/pages/BiRevenuePage.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-06 is frontend/API-client wiring only; no
  DDL or writes were run against PostgreSQL.
- **Technical decisions:** Kept snake_case DTO fields at the API boundary and
  transformed display props inside `useBiRevenue()`, matching IMPL-05. DTO-to-
  widget mapping: `kpis.invoiced_sales` -> `CA reel` metric and detailed
  helper, `kpis.ordered_sales` -> `CA commande`, `kpis.invoice_gap_amount` +
  `responsible_reading.invoice_coverage_percent` -> `Ecart`, `kpis.average_order_value`
  -> `Panier moyen`, `trend[].period/invoiced_sales` -> `BiColumnPreview`,
  `responsible_reading.best_month`, `invoice_coverage_percent`,
  `unpaid_invoice_count`, and `unpaid_invoice_amount` -> `Lecture responsable`,
  and `revenue_status.facture`, `a_livrer`, `ecart` -> `Statuts CA`. The
  existing `BiBarPreview` was not reused for the status card because it always
  renders values as percentages, which would mislabel money and quantity fields.
- **Validation commands run:** `cd frontend && npm run typecheck`;
  `cd frontend && npm run build`; `Invoke-RestMethod http://localhost:8081/v1/bi/revenue`;
  browser check on `http://127.0.0.1:5173/tableau-de-bord/chiffre-affaires`;
  Vite-proxied `Invoke-RestMethod http://127.0.0.1:5173/api/v1/bi/revenue`;
  backend-stop browser reload check; `powershell.exe -NoProfile -ExecutionPolicy
  Bypass -File .\start_project.ps1 -NoBrowser`; browser recheck after restart;
  PNG export smoke test.
- **Validation results:** PASS. Typecheck and production build both completed
  successfully. Direct `/v1/bi/revenue` and Vite-proxied
  `/api/v1/bi/revenue` returned HTTP 200 with
  `data.kpis.invoiced_sales = 2848655.68`,
  `ordered_sales = 3188055.91975`, `invoice_gap_amount = -339400.23975`,
  `average_order_value = 5175.42`, `trend[0].order_count = 616`,
  `responsible_reading.invoice_coverage_percent = 89.35`, and
  `revenue_status.a_livrer = 7546`. Browser validation saw
  `Factures validees: 2 848 655,68 MAD` on
  `/tableau-de-bord/chiffre-affaires`. With the backend stopped, the page showed
  `Impossible de charger le chiffre d'affaires BI` and `Reessayer` instead of
  crashing. After restart, `start_project.ps1` smoke checks passed for frontend,
  BI overview API, and admin login, and the revenue page again showed the real
  value. Export smoke produced
  `C:\Users\<user>\Downloads\lpn-bi-chiffre-d-affaires-janv-2026-juin-2026 (1).png`
  with size `321647` bytes.
- **App still boots & legacy endpoints intact?:** yes. The local native stack
  restarted successfully via `start_project.ps1 -NoBrowser`; frontend returned
  200, `/v1/bi/overview` returned 200, and admin login returned approved. No
  Java code, legacy `SalesDashboardController`, legacy dashboard client, export
  utilities, overview page, or other BI detail pages were modified.
- **Problems encountered:** Browser automation did not expose `performance`
  resource timing for network-tab verification, so the `/api/v1/bi/revenue`
  path was validated through the Vite proxy by direct HTTP and by the rendered
  page value. The browser helper also did not surface a download event for PNG
  export, so the exported file was verified in the local Downloads folder.
- **Next recommended task:** **IMPL-06: BiCommandesPage** — continue the BI-12
  rollout order by wiring the Commande page to `/v1/bi/orders`, validating
  document type and status mappings before the more complex pages.

### [2026-06-26 10:41] IMPL-06 — BiCommandesPage wired to `/v1/bi/orders`

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Wired the `BiCommandesPage` / Commande page to the real
  mart-backed `/v1/bi/orders` endpoint using the established IMPL-05/IMPL-06
  client/hook pattern. Added typed orders DTOs, `fetchBiOrders()`, and a
  `useBiOrders()` hook with loading, empty-type, retryable error, and refresh
  behavior. Removed only this page's placeholder values; export, overview,
  revenue, articles, and clients pages were not modified.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiRevenue.ts`,
  `frontend/src/features/bi/pages/BiCommandesPage.tsx`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiOrdersResponse.java`,
  `BiOrdersMartRepository.java`, and live `/v1/bi/orders`.
- **Files created/modified:** `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiOrders.ts`,
  `frontend/src/features/bi/pages/BiCommandesPage.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-06 Commande is frontend/API-client wiring
  only; no DDL or writes were run against PostgreSQL.
- **Technical decisions:** Kept snake_case DTO fields at the API boundary and
  transformed display props inside `useBiOrders()`, mirroring `useBiRevenue()`.
  DTO-to-widget mapping: `kpis.order_count` -> `Commandes`, `kpis.dominant_type_name`
  -> `Type dominant`, `kpis.invoice_coverage_percent` -> `Commandes facturees`,
  `kpis.status_count` -> `Statuts`, `by_type[].order_type_name/order_count` ->
  `BiColumnPreview`, `kpis.amount_invoice_coverage_percent` plus the top
  `commercial_flow[].delivered_coverage_percent` and `invoice_coverage_percent`
  -> `BiBarPreview`, and top `commercial_flow`, `by_type`, and
  `status_breakdown` rows -> `Points de controle`. The existing `BiBarPreview`
  was used only for percent fields, not raw counts or money.
- **Validation commands run:** `cd frontend && npm run typecheck`;
  `cd frontend && npm run build`; `Invoke-RestMethod http://localhost:8081/v1/bi/orders`;
  `Invoke-RestMethod http://127.0.0.1:5173/api/v1/bi/orders`; browser check on
  `http://127.0.0.1:5173/tableau-de-bord/commandes`; PNG export smoke test;
  backend-stop browser reload check; `powershell.exe -NoProfile -ExecutionPolicy
  Bypass -File .\start_project.ps1 -NoBrowser`; browser recheck after restart.
- **Validation results:** PASS. Typecheck and production build both completed
  successfully. Direct `/v1/bi/orders` and Vite-proxied `/api/v1/bi/orders`
  returned HTTP 200 with `data.kpis.order_count = 616`,
  `dominant_type_name = Commande standard`,
  `invoice_coverage_percent = 60.14`,
  `amount_invoice_coverage_percent = 45.49`, `status_count = 2`,
  `by_type[0].order_count = 576`, top commercial `afekkak` with
  `order_count = 197`, and status `CO` with `item_count = 611`. Browser
  validation saw `616`, `Commande standard`, `60,1%`, and live type/commercial/
  status rows on `/tableau-de-bord/commandes`, with no old placeholders
  (`3,3 k`, `a suivre`, `CO / DR`) present. With the backend stopped, the page
  showed `Impossible de charger les commandes BI` and `Reessayer` instead of
  crashing. Export smoke produced
  `C:\Users\<user>\Downloads\lpn-bi-type-de-commandes-janv-2026-juin-2026.png`
  with size `297679` bytes. After restart, the page again showed the real
  `616` order count.
- **App still boots & legacy endpoints intact?:** yes. The local native stack
  restarted successfully via `start_project.ps1 -NoBrowser`; frontend returned
  200, `/v1/bi/overview` returned 200, and admin login returned approved. No
  Java code, legacy `SalesDashboardController`, legacy dashboard client, export
  utilities, overview page, revenue page, articles page, or clients page was
  modified.
- **Problems encountered:** Browser automation did not expose reliable network
  timing entries for a network-tab assertion, so the `/api/v1/bi/orders` call
  was validated through the Vite proxy by direct HTTP and by the rendered page
  values. The browser helper again did not surface a download event for PNG
  export, so the exported file was verified in the local Downloads folder.
- **Next recommended task:** **IMPL-06: BiCommercialPage** — continue the BI-12
  rollout order by wiring the Commercial page to `/v1/bi/commercial`, including
  checking the current route because the app shell presently redirects the
  commercial BI path.

### [2026-06-26 10:55] IMPL-06 — BiArticlesPage wired to `/v1/bi/articles`

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Wired the mounted `BiArticlesPage` / Articles page to the real
  mart-backed `/v1/bi/articles` endpoint using the established IMPL-05/06
  client/hook pattern. Added typed articles DTOs, `fetchBiArticles()`, and a
  `useBiArticles()` hook with loading, clean empty states, retryable error, and
  refresh behavior. Removed only this page's placeholder values; export and all
  other BI pages were not modified.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiOrders.ts`,
  `frontend/src/features/bi/pages/BiArticlesPage.tsx`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiArticlesResponse.java`,
  `BiArticlesMartRepository.java`, and live `/v1/bi/articles` over the
  materialized `mart_sales_by_product` mart from IMPL-04.
- **Files created/modified:** `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiArticles.ts`,
  `frontend/src/features/bi/pages/BiArticlesPage.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-06 Articles is frontend/API-client wiring
  only; no DDL or writes were run against PostgreSQL.
- **Technical decisions:** Kept snake_case DTO fields at the API boundary and
  transformed display props inside `useBiArticles()`, matching the prior
  page hooks. DTO-to-widget mapping: `kpis.active_products` -> `Produits actifs`,
  `kpis.top_category_name` -> `Top categorie`, `kpis.top_theme_name` ->
  `Top theme`, `kpis.stock_risk_count` -> `Disponibilite`,
  `top_articles[].product_name/invoiced_sales` -> `BiColumnPreview`,
  top `mix_article.categories/themes/collections` named values ->
  `BiSignalList`, and `stock_priorities[].product_name/stock_risk_level/
  qty_available/qty_reserved` -> `Priorites stock`. Empty `top_articles`,
  `mix_article`, or `stock_priorities` arrays render `EmptyChartState` rather
  than fabricated placeholder data.
- **Validation commands run:** `cd frontend && npm run typecheck`;
  `cd frontend && npm run build`; `Invoke-RestMethod http://localhost:8081/v1/bi/articles`;
  `Invoke-RestMethod http://127.0.0.1:5173/api/v1/bi/articles`; browser check
  on `http://127.0.0.1:5173/tableau-de-bord/articles`; PNG export smoke test;
  backend-stop browser reload check; `powershell.exe -NoProfile -ExecutionPolicy
  Bypass -File .\start_project.ps1 -NoBrowser`; browser recheck after restart.
- **Validation results:** PASS. Typecheck and production build both completed
  successfully. Direct `/v1/bi/articles` and Vite-proxied
  `/api/v1/bi/articles` returned HTTP 200 with
  `data.kpis.active_products = 4336`,
  `top_category_name = LITTERATURE GENERALE`,
  `top_theme_name = Bandes dessinees`, `stock_risk_count = 40443`,
  top article `ABONNEMENT AU BOUQUET GENERAL CAIRN / OCP` with
  `invoiced_sales = 180000.0`, top category mix value `590822.21`, and stock
  priority `ARABE C6 ELEVE ED 2020` with `qty_available = -307188`.
  Browser validation saw `4 336`, `LITTERATURE GENERALE`,
  `Bandes dessinees`, `40 443`, live top-article bars, mix signals, and stock
  signals on `/tableau-de-bord/articles`, with no old placeholders (`16,3 k`,
  `P1`, `a isoler`) present. With the backend stopped, the page showed
  `Impossible de charger les articles BI` and `Reessayer` instead of crashing.
  Export smoke produced
  `C:\Users\<user>\Downloads\lpn-bi-articles-janv-2026-juin-2026.png` with
  size `341893` bytes. After restart, the page again showed the real `4 336`
  active-products count.
- **App still boots & legacy endpoints intact?:** yes. The local native stack
  restarted successfully via `start_project.ps1 -NoBrowser`; frontend returned
  200, `/v1/bi/overview` returned 200, and admin login returned approved. No
  Java code, legacy `SalesDashboardController`, legacy dashboard client, export
  utilities, overview page, revenue page, orders page, or clients page was
  modified.
- **Problems encountered:** Browser automation did not expose reliable network
  timing entries for a network-tab assertion, so the `/api/v1/bi/articles`
  request was validated through the Vite proxy by direct HTTP and by rendered
  page values. The browser helper did not surface a download event for PNG
  export, so the exported file was verified in the local Downloads folder.
  The previous log recommended `BiCommercialPage`, but the current app shell
  redirects `/tableau-de-bord/commercial`; this task correctly skipped it.
- **Next recommended task:** **IMPL-06: BiClientsPage** — wire the last mounted
  placeholder page to `/v1/bi/clients`.

### [2026-06-26 11:12] IMPL-06 — BiClientsPage wired to `/v1/bi/clients`

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Wired the last mounted placeholder BI page, `BiClientsPage`, to
  the real mart-backed `/v1/bi/clients` endpoint using the established
  IMPL-05/06 client/hook pattern. Added typed clients DTOs, `fetchBiClients()`,
  and a `useBiClients()` hook with loading, clean empty states, retryable error,
  and refresh behavior. Removed only this page's placeholders; export,
  `BiCommercialPage`, and all other mounted BI pages were not modified. All 5
  mounted BI pages are now on real `/v1/bi/*` data; Phase C is complete.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiArticles.ts`,
  `frontend/src/features/bi/pages/BiClientsPage.tsx`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/dto/BiClientsResponse.java`,
  `BiClientsController.java`, `BiClientsService.java`,
  `BiClientsMartRepository.java`, and live `/v1/bi/clients`.
- **Files created/modified:** `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useBiClients.ts`,
  `frontend/src/features/bi/pages/BiClientsPage.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-06 Clients is frontend/API-client wiring
  only; no DDL or writes were run against PostgreSQL.
- **Technical decisions:** Kept snake_case DTO fields at the API boundary and
  transformed display props inside `useBiClients()`, matching the prior page
  hooks. DTO-to-widget mapping: `kpis.active_customers` -> `Clients actifs`,
  `kpis.top_customer_name` -> `Top client`,
  `kpis.portfolio_commercial_count` -> `Portefeuille`,
  `kpis.geography_count` -> `Geographie`,
  `top_clients[].customer_name/invoiced_sales` -> `BiColumnPreview`,
  top `by_commercial`, `by_article`, and `by_region` rows -> `Client par angle`,
  and `finance_risks[].customer_name/unpaid_invoice_count/unpaid_invoice_amount`
  -> `Lecture utile`. The existing `BiBarPreview` was not reused because it
  renders values as percentages, which would mislabel customer counts and money.
- **Validation commands run:** `cd frontend && npm run typecheck`;
  `cd frontend && npm run build`; `Invoke-RestMethod http://localhost:8081/v1/bi/clients`;
  `Invoke-RestMethod http://127.0.0.1:5173/api/v1/bi/clients`; browser check on
  `http://127.0.0.1:5173/tableau-de-bord/clients`; PNG export smoke test;
  backend-stop browser reload check; `powershell.exe -NoProfile -ExecutionPolicy
  Bypass -File .\start_project.ps1 -NoBrowser`; browser recheck after restart.
- **Validation results:** PASS. Typecheck and production build both completed
  successfully. Direct `/v1/bi/clients` and Vite-proxied
  `/api/v1/bi/clients` returned HTTP 200 with
  `data.kpis.active_customers = 175`, `top_customer_name = O.C.P`,
  `portfolio_commercial_count = 15`, `geography_count = 10`, top commercial
  `afekkak` with `customer_count = 36` and `invoiced_sales = 867195.8`, top
  region `CASABLANCA` with `customer_count = 100`, and top finance risk `O.C.P`
  with `unpaid_invoice_amount = 180000.0`. Browser validation saw `175`,
  `O.C.P`, `afekkak`, `CASABLANCA`, and live finance-risk rows on
  `/tableau-de-bord/clients`, with old placeholders (`295`, `L'AVENIR`,
  `Client 1`, `Top CA`, `impayes`) absent. With the backend stopped, the page
  showed `Impossible de charger les clients BI` and `Reessayer` instead of
  crashing. Export smoke produced
  `C:\Users\<user>\Downloads\lpn-bi-client-janv-2026-juin-2026.png` with size
  `315922` bytes. After restart, the page again showed the real `175` active
  customers value.
- **App still boots & legacy endpoints intact?:** yes. The local native stack
  restarted successfully via `start_project.ps1 -NoBrowser`; frontend returned
  200, `/v1/bi/overview` returned 200, and admin login returned approved. No
  Java code, legacy `SalesDashboardController`, legacy dashboard client, export
  utilities, overview page, revenue page, orders page, articles page, or
  unmounted `BiCommercialPage` was modified.
- **Problems encountered:** Browser automation did not expose reliable network
  timing entries for a network-tab assertion, so the `/api/v1/bi/clients`
  request was validated through the Vite proxy by direct HTTP and by rendered
  page values. The export modal has both the page-level `Exporter les donnees`
  button and modal confirmation `Exporter` button visible at once; the PNG
  smoke test scoped the click to the modal before verifying the downloaded file.
- **Next recommended task:** **IMPL-07 / BI-13 implementation** — with Phase C
  complete, move the structured Excel export path to backend `/v1/bi/export/*`
  while leaving PNG/PDF frontend WYSIWYG export unchanged.

### [2026-06-26 11:40] IMPL-07 — Backend XLSX export endpoint wired to frontend Excel

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high
- **Summary:** Implemented the BI-13 structured Excel export path in the Spring
  BI package and redirected only the frontend Excel branch to the backend.
  Added `GET /v1/bi/export/{page}` for `overview`, `orders`, `revenue`,
  `articles`, `clients`, `commercial`, and `analysis`. The endpoint streams a
  real `.xlsx` OOXML workbook built from the same mart-backed BI page services
  as `/v1/bi/*`. PNG/PDF remain frontend WYSIWYG captures through the existing
  `html-to-image` + `jsPDF` path. No `.NET` service was introduced.
- **Files/DB objects inspected:** `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/_design/13_export_reporting_design.md`,
  `frontend/src/features/bi/utils/biExport.ts`,
  `frontend/src/features/bi/components/layout/BiShell.tsx`,
  `frontend/src/features/bi/components/controls/BiExportDialog.tsx`,
  BI page services/controllers/DTOs under
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi`,
  `services-java/llm-orchestrator/build.gradle.kts`, and live
  `/v1/bi/*` / `/v1/sales-dashboard` endpoints.
- **Files created/modified:** `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/api/BiExportController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/application/BiExportService.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/application/BiExportWorkbook.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi/infrastructure/export/BiOoxmlWorkbookWriter.java`,
  `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/bi/BiOverviewControllerTest.java`,
  `frontend/src/features/bi/utils/biExport.ts`,
  `frontend/src/features/bi/components/layout/BiShell.tsx`, and
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`.
- **DB changes applied:** none. IMPL-07 is read-only backend/frontend wiring
  over existing `mart.*` views/materialized views. No DDL or PostgreSQL writes
  were run.
- **Technical decisions:** Used a small internal OOXML writer based on
  `ZipOutputStream` instead of adding Apache POI or another Java dependency.
  This is the lightest option for dashboard-shaped exports and keeps the Java
  dependency graph unchanged. The export service reuses the existing BI page
  services (`BiOverviewService`, `BiOrdersService`, `BiRevenueService`,
  `BiArticlesService`, `BiClientsService`, `BiCommercialService`,
  `BiAnalysisService`) so workbook values are sourced through the same
  mart-only repositories as the JSON DTOs. Workbooks include `Context` plus
  BI-13 page-specific sheets, with DTO record fields reflected through their
  `@JsonProperty` names. The frontend now derives `{page}` from the mounted BI
  route and sends the shell's `from`, `to`, `granularity`, and `compare`
  filters to `/api/v1/bi/export/{page}` for Excel only.
- **Validation commands run:** `cd services-java && .\gradlew.bat :llm-orchestrator:test`;
  `cd services-java && .\gradlew.bat :llm-orchestrator:bootJar`; started the
  rebuilt boot jar on `8081` with the BI read-only mart datasource; `curl.exe
  -sS -OJ "http://localhost:8081/v1/bi/export/overview"`; unzipped the
  downloaded workbook with .NET `System.IO.Compression`; `Invoke-WebRequest`
  sweep for `/v1/bi/export/{overview,orders,revenue,articles,clients,commercial,analysis}`;
  `Invoke-RestMethod http://localhost:8081/v1/sales-dashboard`; `cd frontend &&
  npm run typecheck`; `cd frontend && npm run build`; browser export checks
  from `http://127.0.0.1:5173/tableau-de-bord/vue-ensemble` for Excel, PNG, and
  PDF.
- **Validation results:** PASS. Backend tests and bootJar completed
  successfully. `curl -OJ` saved
  `lpn-bi-overview-2026-05-01-2026-05-31.xlsx` from the backend header; the file
  unzipped as OOXML with `[Content_Types].xml`, `xl/workbook.xml`, and
  `xl/worksheets/sheet2.xml`; the KPI sheet contained `order_count` and
  `<v>616</v>`. All seven export slugs returned HTTP 200 with
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and the
  matching `X-BI-Export-Page` header. The frontend Excel button downloaded
  `C:\Users\<user>\Downloads\lpn-bi-overview-2026-01-01-2026-06-30.xlsx`
  from `/api/v1/bi/export/overview` using the current shell date range; that
  file also unzipped as OOXML and contained the KPI sheet. PNG and PDF visual
  exports still worked, producing
  `lpn-bi-vue-d-ensemble-janv-2026-juin-2026.png` and
  `lpn-bi-vue-d-ensemble-janv-2026-juin-2026.pdf` with no UI export errors.
  Frontend typecheck and production build both completed successfully.
- **App still boots & legacy endpoints intact?:** yes. The rebuilt boot jar
  became healthy on `8081`, `/v1/bi/overview` returned `order_count = 616`, and
  `/v1/sales-dashboard` remained HTTP 200 with live legacy values
  `order_count = 616`, `invoice_count = 241`, and `invoiced_sales = 2848655.68`.
  The prompt mentioned `54910` for `/v1/sales-dashboard`, but the live legacy
  response does not expose that value; the observed legacy reconciliation value
  remains `616` orders for the previous calendar month.
- **Problems encountered:** The first server `Content-Disposition` used Spring's
  UTF-8 filename builder, which caused Windows `curl -OJ` to save an encoded
  filename (`=_UTF-8_Q_..._=`). Because BI export filenames are already
  ASCII-safe, the controller now emits a plain attachment filename and
  `curl -OJ` saves the expected `.xlsx`. Browser automation still does not
  expose reliable download events, so UI export success was verified through
  the generated files in the local Downloads folder and OOXML ZIP inspection.
- **Next recommended task:** **IMPL-08** — add focused backend/frontend polish
  around export filters and user feedback: make dashboard hooks consume the same
  shell date filters used by Excel, and optionally update the Excel dialog copy
  from "metadata" to "donnees mart" now that the backend export is live.

### [2026-06-26 12:20] IMPL-08 — Retire legacy dashboard path; final reconciliation

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 high
- **Summary:** Retired the legacy `SalesDashboardController`/`SalesDashboardService`
  (deleted — no Java imports, no tests) and all dead frontend code
  (`DashboardChartDetailPage`, `DashboardOverviewPage`, `SalesAnalysisPage`,
  `DashboardHubPage`, `useSalesDashboard`, `useSalesAnalysis`, 11 section/chart/filter
  components that were only used by the deleted pages, `biTransformers.ts`).
  Redirected the `AppShell` catch-all route `/tableau-de-bord/:chartId` to
  `/tableau-de-bord/vue-ensemble`. Cleaned legacy functions from `biApi.ts` and
  legacy-only types from `bi.types.ts`. The BI-14 golden-number reconciliation
  passed for all 5 KPIs. The incremental slice IMPL-01…08 is complete.
- **Files/DB objects inspected:**
  `docs/bi-service-roadmap/implementation/context_bi_implementation.md`,
  `docs/bi-service-roadmap/implementation/IMPL_tasks.md`,
  `docs/bi-service-roadmap/implementation/IMPL_00_overview.md`,
  `frontend/src/components/layout/AppShell.tsx` (route tree),
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/pages/DashboardChartDetailPage.tsx` (read before delete),
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardService.java`,
  all test files under `src/test/` (confirmed zero `SalesDashboard` references),
  live `mart.*` / `business.*` objects in PostgreSQL.
- **Files created/modified:**
  - `frontend/src/components/layout/AppShell.tsx` — removed `DashboardChartDetailPage`
    lazy import; changed `/:chartId` route to `<Navigate to="/tableau-de-bord/vue-ensemble" replace />`
  - `frontend/src/features/bi/api/biApi.ts` — removed `fetchSalesDashboard`,
    `fetchSalesAnalysis`, and their type imports
  - `frontend/src/features/bi/types/bi.types.ts` — removed `SalesDashboardSnapshot`,
    `SalesAnalysisSnapshot`, `SalesAnalysisFilters`, and all nested legacy-only types
    (`SalesAnalysisKpis`, `SalesAnalysisTrendPoint`, `CommercialSalesRank`,
    `CommercialRevenueDatum`, `OrderTypeRank`, `CategorySalesRank`, `ThemeSalesRank`,
    `SupplierSalesRank`, `DistributorSalesRank`, `GeographySalesRank`,
    `AvailabilityRiskRank`, `FilterOption`, `DashboardChartDetail`, `ChartDetailKpi`)
  - `docs/bi-service-roadmap/implementation/context_bi_implementation.md` (this entry)
  - **Deleted (22 files):**
    `SalesDashboardController.java`, `SalesDashboardService.java`,
    `DashboardChartDetailPage.tsx`, `DashboardOverviewPage.tsx`, `SalesAnalysisPage.tsx`,
    `DashboardHubPage.tsx`, `useSalesDashboard.ts`, `useSalesAnalysis.ts`,
    `CaComparisonChart.tsx`, `CommercialRevenueComparisonChart.tsx`, `SalesTrendChart.tsx`,
    `SalesAnalysisFilterCard.tsx`, `CommercialSection.tsx`, `DisponibiliteSection.tsx`,
    `DistributeurSection.tsx`, `GeographieSection.tsx`, `OrderTypeSection.tsx`,
    `RepartitionSection.tsx`, `ThematiqueSection.tsx`, `biTransformers.ts`
- **DB changes applied:** none. IMPL-08 is code-only; no DDL or PostgreSQL writes were run.
- **Technical decisions:**
  - **IMPL-DECISION-03:** `DashboardChartDetailPage` was the only active consumer of
    `/v1/sales-dashboard` (its route `/tableau-de-bord/:chartId` was a catch-all below the
    5 named BI routes). Since no live BI page links to it and `DashboardOverviewPage` (its
    source of navigation) was already dead code, the catch-all was redirected to
    `vue-ensemble` and the file deleted. This was the blocker for retiring the Java controller.
  - All 12 section/chart/filter components deleted with `SalesAnalysisPage` (dead code
    confirmed: no live BI page imported them). `biTransformers.ts` deleted together because
    `CaComparisonChart` was its only remaining consumer.
  - Types shared with the new BI layer (`SalesKpis`, `MonthlySalesPoint`, `CustomerRank`,
    `ProductRank`, `StatusBreakdown`, `RankedBarDatum`, `ContributionPieDatum`,
    `FormattedTrendPoint`) were kept; only types with no remaining consumer were removed.
- **Validation commands run:**
  - `rg SalesDashboard services-java/ frontend/src/` — confirmed zero Java imports before delete
  - `cd services-java && .\gradlew.bat :llm-orchestrator:test` — BUILD SUCCESSFUL
  - `cd frontend && npm run typecheck` — clean (no errors)
  - `cd frontend && npm run build` — built in 4.77s (✓)
  - `Invoke-RestMethod http://localhost:8081/v1/bi/overview` — 200, order_count=616
  - `Invoke-WebRequest http://localhost:8081/v1/bi/export/{7 slugs}` — all 200 with correct MIME
  - `Invoke-RestMethod http://localhost:8081/v1/sales-dashboard` — HTTP 404 ✓
  - `Invoke-RestMethod http://localhost:8081/v1/sales-analysis` — HTTP 404 ✓
  - Independent business.* SQL reconciliation (see below)
- **Validation results:** PASS. All reconciliation gates met:
  - `order_count = 616` — `/v1/bi/overview` 616, `/v1/bi/orders` 616, `/v1/bi/analysis` 616,
    `business.c_order(CO/CL, May 2026)` = **616** ✓
  - `invoiced_sales = 2848655.68` — `/v1/bi/overview` 2848655.68, `/v1/bi/revenue` 2848655.68,
    `/v1/bi/analysis` 2848655.68, `business.c_invoice(CO, May 2026)` = **2848655.68** ✓
  - `active_products = 4336` — `/v1/bi/articles` 4336, `business.c_invoiceline(May 2026)` = **4336** ✓
  - `active_customers = 175` — `/v1/bi/clients` 175, `/v1/bi/overview` 175 ✓ (mart-reconciled in IMPL-02)
  - `commercial_count = 15` — `/v1/bi/commercial` 15 ✓
  - `business.c_orderline` = **354910** (unchanged) ✓
  - All 7 `/v1/bi/export/*` slugs return HTTP 200 with `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` ✓
  - `/v1/sales-dashboard` → 404; `/v1/sales-analysis` → 404 ✓
  - gradle `:llm-orchestrator:test` BUILD SUCCESSFUL; `npm run typecheck` clean; `npm run build` clean
- **App still boots & legacy endpoints intact?:** App boots with rebuilt jar on port 8081.
  All 5 BI pages serve real data from `/v1/bi/*`. QA/chat, forecast, schema-retrieval,
  and sql-executor code were not touched. `/v1/sales-dashboard` and `/v1/sales-analysis`
  are intentionally retired (404).
- **Problems encountered:** Dead section components (`CommercialSection`, etc.) and chart
  components (`CaComparisonChart`, etc.) were not directly imported by any live page but
  were still present and imported the removed types — which caused 21 TypeScript errors on
  first typecheck run. Resolved by deleting all 12 components (confirmed dead via grep of
  live page imports). The old running jar still served `/v1/sales-dashboard` from cached
  bytecode; rebuilt and restarted the jar to confirm 404.
- **Next recommended task:** **IMPL-W** (deferred) — build the full warehouse
  (`staging`/`warehouse` schemas via `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`),
  then repoint the 9 mart view bodies from `business.*` to `warehouse.*` without changing
  the mart column contract or any API/frontend code.

---

**INCREMENTAL SLICE IMPL-01…08 COMPLETE.**
All 5 BI pages render real mart-backed data from `/v1/bi/*`; legacy dashboard path retired;
Excel export is backend-side (PNG/PDF still frontend); all KPIs reconcile to `business.*`;
app boots clean natively. The only deferred item is **IMPL-W** (full warehouse ETL).

**Final task status snapshot:**

| Task | Title | Phase | Status |
|---|---|---|---|
| IMPL-01 | `mart` schema + overview/payment marts over business + reconcile | A | ✅ Done |
| IMPL-02 | Remaining page marts over business + reconcile | A | ✅ Done |
| IMPL-03 | Scaffold `bi` package + read-only datasource + `/v1/bi/overview` + tests | B | ✅ Done |
| IMPL-04 | Remaining `/v1/bi/*` endpoints + filters + tests | B | ✅ Done |
| IMPL-05 | Wire FIRST frontend page to real API (charts go real) | C | ✅ Done |
| IMPL-06 | Wire remaining 4 pages (one per run, per BI-12 order) | C | ✅ Done |
| IMPL-07 | Backend `/v1/bi/export/*` `.xlsx`; redirect frontend Excel | D | ✅ Done |
| IMPL-08 | Trim legacy endpoints; final reconciliation + demo | D | ✅ Done |
| IMPL-W | Deferred: build warehouse, repoint marts (same columns) | W | 💤 Deferred |

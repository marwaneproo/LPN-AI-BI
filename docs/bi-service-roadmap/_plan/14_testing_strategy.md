# BI-14 - Testing and validation strategy

Status: PLAN ONLY
Date: 2026-06-25
Scope: define the validation strategy for BI-05 through BI-13 before any
dashboard KPI is repointed from `business.*` to `mart.*`. Do not implement
tests, run ETL, run frontend, run Java/Python test suites, or touch PostgreSQL
in this task.

## 1. Inputs inspected

| Area | Real files inspected | Findings used |
| --- | --- | --- |
| Java tests | `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/*Test.java`, `services-java/sql-executor/src/test/java/com/lpn/aibi/sqlexecutor/*Test.java` | Current pattern uses Spring Boot + MockMvc for controller contracts, Mockito `@MockBean` for collaborators, H2 for read-only SQL executor tests, and opt-in real integration tests guarded by `INTEGRATION_TESTS=1`. |
| Python tests | `services-python/data-import/tests/test_cli_dry_run.py`, `test_cli_postgres_import.py`, `services-python/tests/test_semantic_layer_assets.py`, service-specific `tests/` folders | Current pattern uses pytest, small fixtures, `tmp_path`, Click `CliRunner`, and opt-in PostgreSQL tests guarded by `RUN_POSTGRES_TESTS=1`. This is the right template for future ETL fixture tests. |
| Frontend setup | `frontend/package.json`, `frontend/vite.config.ts`, `frontend/src/features/bi/pages/*` | No Vitest, Testing Library, Playwright, Jest, or frontend spec files exist today. Frontend integration tests must be added later as implementation work. |
| BI cleaning and DDL artifacts | `_plan/05_cleaning_plan.md`, `_design/06_staging_design.md`, `_design/07_warehouse_design.md`, `_design/08_mart_design.md`, `sql/marts.sql` | Strategy must validate TRAP-01, DOCSTATUS filters, nullable invoice-to-order bridge, UNKNOWN key `0`, staging/warehouse/mart row continuity, and all nine BI-08 marts. |
| BI implementation plans/contracts | `_plan/09_etl_plan.md`, `_design/10_bi_api_contract.md`, `_design/11_extraction_decision.md`, `_plan/12_frontend_integration_plan.md`, `_design/13_export_reporting_design.md` | Tests must cover the ETL DAG, marts-only `/v1/bi/*` contract, package boundary, frontend DTO states, and backend Excel export while leaving PNG/PDF frontend capture unchanged. |
| Metric source of truth | `docs/semantic_layer/metrics.yml` | Golden checks must reuse canonical formulas: `ca_commande = SUM(C_ORDER.GRANDTOTAL)`, `ca_facture = SUM(C_INVOICE.GRANDTOTAL)`, paid/unpaid from `ISPAID`, stock from `RV_STORAGE`, and product revenue from invoice line amount. |
| Warehouse rebuild dependency | `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` | BI implementation depends on actually loading `staging`, `warehouse`, and `mart` in PostgreSQL and producing `docs/warehouse/WAREHOUSE_VALIDATION.md`. |

## 2. Test strategy principles

1. **Contract before migration:** keep `/v1/sales-dashboard` and
   `/v1/sales-analysis` as the legacy baseline until each mart-derived endpoint
   reconciles.
2. **Small fixtures first:** Python ETL tests use tiny generated CSV/ZIP
   fixtures that exercise rules without reading multi-GB source files.
3. **Opt-in database tests:** any test that touches local PostgreSQL is guarded
   by an environment variable, following existing Java and Python integration
   patterns.
4. **Marts-only API proof:** backend BI tests query or mock only `mart.*`
   repositories; contract tests must fail if implementation reads `business.*`,
   raw files, `staging`, or `warehouse` for public DTO rows.
5. **No silent KPI repoint:** every dashboard KPI has a named reconciliation
   check before the frontend or legacy page switches to a BI-10 endpoint.
6. **Latency decides materialization:** BI-08 starts with plain views; promote
   candidate marts only if BI-14 performance checks show endpoint p95 above
   300 ms for normal dashboard filters after warehouse indexes are in place.

## 3. Data-quality checks

These checks run after the DATA_WAREHOUSE_REBUILD_TASK_ROADMAP has loaded
PostgreSQL. The canonical output location is
`docs/warehouse/WAREHOUSE_VALIDATION.md`, with machine-readable details in the
BI-09 validation JSON/Markdown report paths.

| Check ID | Layer | Assertion | Gate |
| --- | --- | --- | --- |
| `DQ-ROW-01` | staging | Mandatory `staging.stg_*` tables for orders, order lines, invoices, invoice lines, products, customers, document types, commercial users, stock, deliveries, payments, allocations, suppliers, and product taxonomy have `row_count > 0`. | Fail if any mandatory table is empty. |
| `DQ-ROW-02` | warehouse | Mandatory `warehouse.dim_*` and `warehouse.fact_*` tables have `row_count > 0`, except optional enrichment dims explicitly documented as absent. | Fail if a required dim/fact is empty. |
| `DQ-MART-01` | mart | All nine BI-08 marts exist and can return rows where their source facts are non-empty: `mart_sales_daily`, `mart_sales_monthly`, `mart_sales_by_commercial`, `mart_sales_by_customer`, `mart_sales_by_product`, `mart_sales_by_region`, `mart_order_to_invoice_flow`, `mart_payment_status`, `mart_stock_risk`. | Fail if any expected mart object is missing or unexpectedly empty. |
| `DQ-TOTAL-01` | source -> staging -> warehouse -> mart | `SUM(C_ORDER.GRANDTOTAL)` from canonical sources/staging equals `warehouse.fact_sales_order.grand_total_amount` and `mart_sales_daily.ca_commande` within 0.5%. | Fail above 0.5%; exact zero gap expected after dedup for counts. |
| `DQ-TOTAL-02` | source -> staging -> warehouse -> mart | `SUM(C_INVOICE.GRANDTOTAL)` equals `warehouse.fact_invoice.grand_total_amount` and `mart_sales_daily.ca_facture` within 0.5%. | Fail above 0.5%. |
| `DQ-TOTAL-03` | invoice line grain | `SUM(C_INVOICELINE.LINENETAMT)` equals `warehouse.fact_invoice_line.line_net_amount` and product/category/supplier mart `ca_facture` within 0.5%. | Fail above 0.5%. |
| `DQ-REJECT-01` | ETL quality | Transactional table reject rate is `< 5%` for orders, order lines, invoices, invoice lines, deliveries, stock, payments, and allocations. | Fail at 5% or higher. |
| `DQ-REJECT-02` | ETL quality | `etl.stg_rejects` contains reason codes, source file, source row reference, `_etl_run_id`, and severity for every rejected or filtered row. | Fail if rejects are unclassified. |
| `DQ-NULL-01` | staging/warehouse | Mandatory primary keys and business dates are non-null after typed staging casts. Optional fields may be null only where BI-05 allows them. | Fail for mandatory nulls. |
| `DQ-FK-01` | warehouse | Required fact foreign keys resolve to a dimension row; optional/unknown values resolve to surrogate key `0` only when the design allows UNKNOWN. | Fail for orphan required keys. |
| `DQ-UNKNOWN-01` | dimensions | Every dimension with a surrogate key has exactly one UNKNOWN row with key `0`. | Fail if missing or duplicated. |
| `DQ-DEDUP-01` | staging | 2024 vs 24-month overlap deduplicates by natural primary key, preferring 2024 files for 2024 rows and 24-month files for 2025-2026 rows. | Fail for duplicate natural keys after staging. |
| `DQ-DOCSTATUS-01` | warehouse | Facts exclude rejected document statuses: orders use BI-05 allowed statuses, invoices use confirmed invoices, deliveries use confirmed deliveries. | Fail if filtered statuses appear in facts. |
| `DQ-TRAP-01` | all layers | `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` is not loaded into staging, warehouse, mart, exports, or API fixtures; CA comes only from order/invoice totals or invoice lines. | Fail if the trap source appears outside reject/audit metadata. |
| `DQ-PAID-01` | invoice/payment | Paid/unpaid invoice counts and unpaid exposure use `C_INVOICE.ISPAID`, not allocation guesses. | Fail if paid/unpaid checks diverge from `ISPAID`. |
| `DQ-BRIDGE-01` | invoice/order bridge | `C_INVOICE.C_ORDER_ID IS NULL` remains valid. Warehouse/marts use left joins and do not reject invoices solely because no order is linked. | Fail if nullable invoice-order rows disappear from invoice totals. |
| `DQ-LARGE-01` | raw ingestion | `M_PRODUCT` and `M_PRODUCT_PO` use CSV-only rules; 80-253 MB files are processed through chunked reads. | Fail if implementation attempts full in-memory Excel reads for these sources. |

## 4. ETL test plan

Future ETL tests should live near the extended prototype under
`services-python/data-import/tests/` or a dedicated ETL test package, using pytest
fixtures and no production source files.

| Check ID | Test type | Assertion | Fixture shape |
| --- | --- | --- | --- |
| `ETL-DAG-01` | unit/contract | The planned run order is raw -> staging -> dimensions -> facts -> marts -> validation, matching BI-09. | In-memory job registry or dry-run plan output. |
| `ETL-IDEMP-01` | integration fixture | Running the same small fixture twice produces the same row counts, facts, mart totals, and validation report. | Tiny CSV bundle with orders, invoices, lines, product, customer, stock. |
| `ETL-STAGE-TRUNC-01` | integration fixture | Staging reload is truncate-reload by table and does not append duplicates across runs. | Same bundle loaded twice. |
| `ETL-FACT-TRUNC-01` | integration fixture | Facts reload from staging and preserve the same natural-key uniqueness after rerun. | Orders/invoices with line rows. |
| `ETL-DIM-UPSERT-01` | integration fixture | Dimensions upsert by natural key, update changed attributes, and preserve surrogate key `0` UNKNOWN rows. | One customer/product changed between run A and run B. |
| `ETL-REJECT-01` | unit/fixture | Bad rows are written to `etl.stg_rejects` with `NULL_PK`, `DATE_OUT_OF_RANGE`, `INVALID_FLAG`, `DOCSTATUS_FILTERED`, `DUPLICATE_PK`, or `CA_TRAP_SOURCE` as appropriate. | Parameterized malformed rows. |
| `ETL-REJECT-02` | integration fixture | Soft rejects, such as filtered `DOCSTATUS`, are logged but do not abort the run; hard rejects fail the acceptance gate when mandatory data is invalid. | Mixed valid/invalid transactional rows. |
| `ETL-CHUNK-01` | unit | Chunked reader yields the same rows as a full read on a tiny fixture and never calls a full in-memory Excel path for known large-file patterns. | Monkeypatched read functions and small CSV chunks. |
| `ETL-LARGE-01` | unit | `M_PRODUCT` 2,272 MB and `M_PRODUCT_PO` 995 MB rules select CSV-only input. | Fake inventory metadata, no large files. |
| `ETL-REPORT-01` | integration fixture | Validation report is written to `DataWareHouse/processus_de_vente/etl/validation/etl_validation_report.json`, `.md`, and `docs/warehouse/WAREHOUSE_VALIDATION.md`. | Temporary output directory. |
| `ETL-MART-COVERAGE-01` | integration fixture | Every BI-08 mart is reachable from planned job outputs and no mart source object is missing. | Static dependency graph plus tiny loaded schema. |

## 5. Backend test plan

Backend implementation should follow the Java Spring Boot patterns already in
the repository: package by feature under the BI package boundary, constructor
injection, DTOs at the web layer, MockMvc contract tests for fast feedback, and
opt-in database integration tests for reconciliation.

| Check ID | Test type | Assertion |
| --- | --- | --- |
| `BI-API-CONTRACT-01` | MockMvc contract | Each BI-10 endpoint under `/v1/bi/*` returns the BI-10 success envelope: `meta` and `data`. |
| `BI-API-CONTRACT-02` | MockMvc contract | DTO field names stay aligned with existing snake_case frontend semantics, including `order_count`, `order_value`, `active_customers`, `invoice_count`, `invoiced_sales`, `paid_invoice_count`, and `unpaid_invoice_count`. |
| `BI-API-FILTER-01` | MockMvc contract | Common params validate consistently: `from`, `to`, `granularity`, `compare`, `commercial`, `category`, `supplier`, `customer`, `document_type`, `region`, `city`, `payment_status`, `limit`, `offset`. |
| `BI-API-ERROR-01` | MockMvc contract | Invalid filters return BI-10 error envelope with `BI_INVALID_FILTER`; unsupported filter combinations return `BI_UNSUPPORTED_FILTER_COMBINATION`; no matching rows return `BI_NO_DATA`. |
| `BI-MART-ONLY-01` | repository/unit | BI repositories read only `mart.*` views and never embed `business.*`, raw-file, `staging.*`, or `warehouse.*` public DTO queries. |
| `BI-GOLDEN-ORDERCOUNT-616` | opt-in reconciliation | Legacy `GET /v1/sales-dashboard` currently exposes `kpis.order_count = 616`; candidate `/v1/bi/overview` and `/v1/bi/analysis` must match the mart-derived `order_count` for the same period before repoint. |
| `BI-GOLDEN-CA-01` | opt-in reconciliation | `order_value`/`ordered_sales` equals `SUM(mart_sales_daily.ca_commande)` within money tolerance and matches the legacy dashboard for the same filters. |
| `BI-GOLDEN-INVOICE-01` | opt-in reconciliation | `invoice_count`, `invoiced_sales`, paid/unpaid counts, and unpaid exposure match the legacy dashboard and `mart_payment_status`/`mart_sales_daily`. |
| `BI-EXPORT-XLSX-01` | MockMvc contract | `GET /v1/bi/export/{page}` returns `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and attachment `Content-Disposition` on success. |
| `BI-EXPORT-ERROR-01` | MockMvc contract | Export errors return JSON with the BI-10/BI-13 envelope, not a broken workbook. |
| `BI-ARCH-IMPORT-01` | architecture test | The BI package has zero imports from LLM orchestration, SQL generation, QA, predictive clients, or `SqlExecutorClient`; it uses the BI read-only datasource only. |
| `BI-READONLY-01` | integration | The BI datasource can `SELECT` from `mart.*` and cannot write to `business`, `staging`, `warehouse`, or `mart`. |
| `BI-PERF-01` | opt-in performance | Common page queries return p95 <= 300 ms with representative filters. If not, revisit BI-08 view vs materialized-view decision. |

## 6. Frontend integration test plan

Frontend tests do not exist today. During implementation, add a small test stack
before migrating pages: Vitest + React Testing Library for component/state
tests, MSW for `/v1/bi/*` DTO mocks, and optional Playwright smoke tests for the
five-page demo path. No frontend export behavior changes are needed for PNG/PDF.

| Check ID | Page/layer | Assertion |
| --- | --- | --- |
| `FE-SETUP-01` | test setup | Frontend has a documented test runner and mock server for BI DTOs before page migration starts. |
| `FE-ORDERS-01` | `BiCommandesPage` | Page renders from `/v1/bi/orders` DTO fields; metrics, type chart, commercial flow, and status controls show DTO values. |
| `FE-REVENUE-01` | `BiRevenuePage` | Page renders from `/v1/bi/revenue`; trend, best month, coverage, and payment-risk widgets use BI-10 fields. |
| `FE-ARTICLES-01` | `BiArticlesPage` | Page renders from `/v1/bi/articles`; product ranking, category/theme/collection mix, and stock-risk widgets use DTO fields. |
| `FE-CLIENTS-01` | `BiClientsPage` | Page renders from `/v1/bi/clients`; top clients, commercial split, article/client table, region view, and finance risk use DTO fields. |
| `FE-COMMERCIAL-01` | `BiCommercialPage` | Page renders from `/v1/bi/commercial`; commercial ranking, conversion, terrain signals, and drill keys use DTO fields. |
| `FE-OVERVIEW-01` | overview/live migration | `DashboardOverviewPage` can consume a BI-10-compatible overview shape only after backend golden checks pass. |
| `FE-ANALYSIS-01` | live analysis migration | `SalesAnalysisPage` keeps existing filter interactions while moving to `/v1/bi/analysis`; `distributor_*` remains a compatibility alias over supplier fields until business naming is resolved. |
| `FE-STATE-LOADING-01` | all pages | Loading state preserves the existing skeleton/disabled-refresh behavior. |
| `FE-STATE-EMPTY-01` | all pages | Empty arrays render existing empty chart/table states, not crashes or misleading zero-filled rankings. |
| `FE-STATE-ERROR-01` | all pages | BI error envelopes show retryable error UI with backend message/code. |
| `FE-EXPORT-PNGPDF-01` | export | PNG/PDF keep frontend WYSIWYG capture through current `html-to-image`/`jsPDF` behavior. |
| `FE-EXPORT-XLSX-01` | export | Excel/data export calls the BI-13 backend endpoint once it exists; the client-side `fflate` metadata-only workbook is retired for Excel only. |

## 7. Reconciliation gate

**Governance rule:** no dashboard KPI is repointed from `business.*` to a
`mart.*` source until the candidate mart value matches the current business
value for the same filters, date range, and rounding policy within tolerance.

Tolerances:

| Metric type | Tolerance |
| --- | --- |
| Counts (`order_count`, `invoice_count`, customer/product counts) | Exact match: delta must be `0`. |
| Money (`order_value`, `ordered_sales`, `invoiced_sales`, unpaid amount) | Relative gap <= `0.5%` and absolute rounding gap <= one display unit after frontend formatting. |
| Percentages (`invoice_coverage_percent`, conversion/coverage rates) | Absolute gap <= `0.10` percentage points after source numerator/denominator checks pass. |
| Rankings | Same top item IDs for top 10, with value gaps using the count or money tolerance above. |

Worked example: `order_count = 616`.

1. Baseline query: `GET /v1/sales-dashboard` returns
   `kpis.order_count = 616` from the existing business-schema
   `SalesDashboardService`.
2. Candidate mart query for the same period:

   ```sql
   SELECT COALESCE(SUM(nombre_commandes), 0) AS order_count
   FROM mart.mart_sales_daily
   WHERE metric_date >= :from
     AND metric_date < :to_exclusive;
   ```

3. Gate result:
   - If the mart query returns `616`, `BI-GOLDEN-ORDERCOUNT-616` passes and the
     endpoint may be considered for the next migration step.
   - If the mart query returns `614`, the relative gap is small, but this is a
     count metric and the count tolerance is exact. The gate fails. Do not
     repoint. Investigate period bounds, DOCSTATUS filters, overlap dedup, and
     the `C_ORDER.GRANDTOTAL`/order header grain before changing the UI.

Money example:

1. Legacy `order_value` for a fixed period is `16,700,000.00 MAD`.
2. Mart candidate:

   ```sql
   SELECT COALESCE(SUM(ca_commande), 0) AS order_value
   FROM mart.mart_sales_daily
   WHERE metric_date >= :from
     AND metric_date < :to_exclusive;
   ```

3. If mart returns `16,735,000.00 MAD`, the gap is `35,000.00 MAD`, or about
   `0.21%`; this passes the money tolerance, subject to rounding display.
4. If mart returns `16,900,000.00 MAD`, the gap is about `1.20%`; this fails
   and the KPI remains on the legacy business query.

The reconciliation report must record baseline value, mart value, absolute gap,
relative gap, tolerance, pass/fail, source endpoint, mart SQL identifier, and
latest `etl_run_id`.

## 8. Manual demo checklist

Run this checklist only after the warehouse rebuild has loaded PostgreSQL and
the BI implementation exists.

### Pre-demo data gate

- `docs/warehouse/WAREHOUSE_VALIDATION.md` exists and all mandatory checks pass.
- `etl.etl_run_log` latest run has status `SUCCESS`.
- `etl.etl_run_table_stats` shows non-zero mandatory staging, fact, and mart
  row counts.
- `DQ-REJECT-01` reject rate is `< 5%` for transactional tables.
- `DQ-TRAP-01` confirms the CA-trap file is excluded.
- BI-08 latency check is recorded; if p95 > 300 ms, document whether
  materialized-view promotion is approved.

### API demo gate

- `GET /v1/bi/overview` returns `meta` + `data` and reconciles headline KPIs.
- `GET /v1/bi/orders`, `/revenue`, `/articles`, `/clients`, and `/commercial`
  return data with the same filters used in the UI.
- `GET /v1/bi/analysis` preserves the legacy analysis field groups.
- Invalid date/filter request returns `BI_INVALID_FILTER`.
- Empty but valid request returns `BI_NO_DATA` or an agreed empty response,
  consistently with BI-10.
- `GET /v1/bi/export/{page}` returns a real `.xlsx` with correct headers for
  at least one page.

### Five-page UI demo

| Page | Demo checks |
| --- | --- |
| Commandes | KPIs load; type chart renders; status breakdown renders; changing date and order type refreshes values; empty/error states are still visible when mocked. |
| Revenue | Real CA, ordered CA, gap, average basket, trend, best month, coverage, and payment status render from BI-10 fields. |
| Articles | Active products, top category/theme, stock risk, product ranking, mix charts, and stock-priority table render from DTOs. |
| Clients | Top clients, commercial portfolio, geography, customer-product table, region split, and finance risk render from DTOs. |
| Commercial | Commercial ranking, conversion, strong zone, key customer, and drill keys render from DTOs and respect filters. |

### Export demo

- PNG export still captures the rendered DOM from the frontend.
- PDF export still captures the rendered DOM and report context from the
  frontend.
- Excel export downloads from `/v1/bi/export/{page}` and workbook sheets map to
  BI-13 mart worksheet design.
- Exported Excel totals match the visible page totals for the same filters.

## 9. Coverage matrix for prior artifacts

| Artifact | Covered by checks |
| --- | --- |
| BI-05 cleaning rules | `DQ-REJECT-01`, `DQ-DOCSTATUS-01`, `DQ-TRAP-01`, `DQ-PAID-01`, `DQ-BRIDGE-01`, `DQ-LARGE-01`, `ETL-REJECT-*`, `ETL-CHUNK-01`. |
| BI-06 staging DDL | `DQ-ROW-01`, `DQ-NULL-01`, `DQ-DEDUP-01`, `ETL-STAGE-TRUNC-01`. |
| BI-07 warehouse DDL | `DQ-ROW-02`, `DQ-FK-01`, `DQ-UNKNOWN-01`, `ETL-DIM-UPSERT-01`, `ETL-FACT-TRUNC-01`. |
| BI-08 marts | `DQ-MART-01`, `DQ-TOTAL-*`, `ETL-MART-COVERAGE-01`, `BI-PERF-01`. |
| BI-09 ETL plan | `ETL-DAG-01`, `ETL-IDEMP-01`, `ETL-REPORT-01`, all reject and row-count gates. |
| BI-10 API contract | `BI-API-CONTRACT-*`, `BI-API-FILTER-01`, `BI-API-ERROR-01`, `BI-MART-ONLY-01`. |
| BI-11 extraction decision | `BI-ARCH-IMPORT-01`, `BI-READONLY-01`. |
| BI-12 frontend plan | `FE-*` page/state checks and reconciliation gate before live route migration. |
| BI-13 export design | `BI-EXPORT-*`, `FE-EXPORT-*`, export demo checklist. |

## 10. Implementation phase after BI-14

The BI-00 through BI-14 planning roadmap is complete after this strategy. The
next phase is implementation, and it depends on the
`DATA_WAREHOUSE_REBUILD_TASK_ROADMAP` actually loading governed
`staging`, `warehouse`, and `mart` schemas in PostgreSQL.

Recommended implementation order:

1. Materialize/load the governed warehouse: run the rebuild roadmap so
   `staging.stg_*`, `warehouse.dim_*`, `warehouse.fact_*`, and BI-08
   `mart.mart_*` objects exist in PostgreSQL with validation reports.
2. Build the Spring BI package: implement BI-11 package boundary, read-only
   datasource, BI-10 `/v1/bi/*` endpoints, and the BI-13 Excel export endpoint.
3. Run reconciliation gates: compare legacy `business.*` dashboard values to
   mart-derived values, starting with `order_count = 616`.
4. Wire the five frontend BI pages in BI-12 rollout order, keeping legacy
   dashboard/analysis endpoints until parity is proven.
5. Move Excel/data export to the backend endpoint while keeping PNG/PDF
   frontend capture unchanged.
6. Revisit BI-08 plain views vs materialized views only after measured p95
   latency and reconciliation results are available.

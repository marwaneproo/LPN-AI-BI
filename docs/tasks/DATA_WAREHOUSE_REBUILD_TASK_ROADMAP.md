# LPN AI-BI — Data Warehouse Rebuild & Upgrade — Task Roadmap

**Created:** 2026-06-22
**Goal of this document:** turn the current prototype data warehouse (CSV-only, built on an
old 3,350-order snapshot) into a **solid, governed warehouse materialized in PostgreSQL**,
built on the **full 2024–2026 data**, that the dashboard, Superset, the AI, and the
forecasting layer all read from.

This file is written so that **a Sonnet-class model can execute each AI task with zero
guessing**. Every task is small, has exact file paths, and ends with a concrete test.

---

## 0. How to read this file

- Tasks run **in order**, Task 0 → Task 12. Do **one task at a time**. Finish it, run its
  **DONE WHEN** test, then stop.
- Each task has an **Owner**:
  -  **AI** — a coding model does it end to end.
  -  **HUMAN** — only a human can do it (reason given). The AI must **stop and wait**.
- Do not start an AI task whose HUMAN dependency is not marked done.

### Owner legend & why HUMAN tasks are human-only

| Owner | Meaning |
|---|---|
|  AI | Pure code/data work. No special access needed. |
|  HUMAN | Needs something the AI cannot and must not do: **Oracle/Compiere DB access** (AI is forbidden to connect to Oracle), **authorizing deletion of files** (destructive, needs human responsibility), **starting/repairing Docker Desktop** (GUI/environment), or **business-rule sign-off** with the LPN team. |

### Hard rules (do not break)

1. **Never connect to LPN's Oracle / Compiere database.** The AI reads PostgreSQL and the
   exported `.xlsx`/`.csv` files only. New extractions are a HUMAN job.
2. **Never delete or overwrite source data without a HUMAN approval task.** Archiving the
   ~6 GB of duplicates is a HUMAN-authorized step.
3. **Keep the current app working after every task.** The existing `business` schema and the
   app's endpoints must keep returning data. New work goes into **new schemas**
   (`staging`, `warehouse`, `mart`) — it does not drop `business`.
4. **Every AI task ends green or it is marked BLOCKED.** If a test fails, stop and report.

---

## 1. The target architecture (what we are building)

```
RAW (.xlsx, canonical)          PostgreSQL: lpn_ai_bi
data/Exported_LPN/   ──load──►  schema staging   (stg_*)   exact copy of sources, deduped
                                      │ transform
                                      ▼
                                schema warehouse (dim_*, fact_*)   star schema, 2024–2026
                                      │ aggregate
                                      ▼
                                schema mart      (mart_*)   ready-to-read BI + forecast input
                                      │
              ┌───────────────┬───────┴────────┬─────────────────┐
              ▼               ▼                ▼                 ▼
        App dashboard     Superset          AI text-to-SQL     Forecasting
       (SalesDashboard)  (datasets)        (schema retrieval) (fact_sales_monthly)
```

**One governed layer, read by everyone.** Today each consumer computes metrics from raw
`business` tables, which causes drift. After this roadmap, they read `mart_*`.

### Key facts the AI must know (verified 2026-06-22)

- **Canonical raw folder:** `D:\LPN_PROJECT\Youssef_Extractions\data\Exported_LPN` (96 `.xlsx`).
  - `2024_01..2024_35` = **full year 2024** (e.g. `2024_01_C_ORDER.xlsx` = 18,024 orders,
    Jan 2 → Dec 31 2024; `2024_03_C_INVOICE.xlsx` = 5,264 invoices, CA total 173.68M).
  - `53..58` = **24-month commercial export** (`53_COMMERCIAL_ORDER_HEADER_24M.xlsx` 34,710
    orders; `55_..INVOICE_HEADER_24M` 10,315 invoices; span Jun 2024 → Jun 2026).
  - `02..52` (no prefix) = older 4-month base tables + BI aggregate exports.
  - **Ignore as junk:** `Book1.xlsx`..`Book7.xlsx`, any file starting with `~$`, `3.xlsx`,
    `4.xlsx`.
- **Overlap to dedup:** Jun–Dec 2024 appears in BOTH `2024_*` and `53..58`. Deduplicate by
  primary key (`C_ORDER_ID`, `C_INVOICE_ID`, line IDs). Rule: for 2024 use the `2024_*`
  files (complete months incl. full June); for 2025–2026 use `53..58`.
- **Known data-quality trap:** `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` has inflated
  `ORDERED_CA_24M`/`INVOICED_CA_24M` (many-to-many join bug). Use
  `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` and/or re-aggregate orders and invoices
  **separately**.
- **Sales filter (from the extraction SQL in `Youssef_Extractions/txt/req_*.txt`):** a sales
  document = `ISSOTRX='Y'` and `DOCSTATUS IN ('CO','CL')` for orders, `DOCSTATUS='CO'` for
  invoices.
- **Existing reusable code:**
  - ETL scripts: `DataWareHouse/processus_de_vente/etl/scripts/` —
    `inspect_sources.py`, `extract_sources.py`, `transform_dimensions.py`,
    `transform_facts.py`, `run_etl.py`, `validate_etl.py`.
  - Config: `DataWareHouse/processus_de_vente/etl/config/etl_config.json`.
  - DDL draft: `DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql`.
  - Postgres loader patterns to copy: `scripts/build-forecast-monthly-fact.py`,
    `scripts/import-vente-bi-enrichment.py` (both use `pandas` + `psycopg2`).
- **PostgreSQL:** container `lpn-ai-bi-postgres-1`, database `lpn_ai_bi`, host port `5433`.
  Roles: `lpn_app_admin` (owner), `lpn_ai_readonly` (SELECT only). Existing schemas:
  `business`, `app`. **New schemas to create:** `staging`, `warehouse`, `mart`.
- **App reads from:** `services-java/.../SalesDashboardService.java`.
- **Forecast fact built by:** `scripts/build-forecast-monthly-fact.py` →
  `business.fact_sales_monthly*`.

### How to run PostgreSQL (used by tests)

```powershell
# Bring up the database (if down)
cd D:\LPN_PROJECT
docker compose up -d postgres

# Run a SQL check
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "SELECT 1;"
```

---

## TASK 0 — Bring up the stack & snapshot the current state

**Owner:** 🧑 HUMAN (start Docker) → then 🤖 AI (snapshot)
**Why HUMAN first:** Docker Desktop on this machine stops often and only a human can start/repair
the desktop app. The AI cannot reliably launch the GUI.

**HUMAN steps:**
1. Start Docker Desktop. Wait until it says "running".
2. Tell the AI to proceed.

**AI steps (after Docker is up):**
1. `docker compose up -d postgres` and confirm it is healthy.
2. Record a baseline file `docs/warehouse/BASELINE_STATE.md` containing:
   - list of schemas, list of `business` tables with row counts,
   - whether schemas `staging`/`warehouse`/`mart` already exist,
   - current `business.fact_sales_monthly` row count and month range.

**✅ DONE WHEN:**
```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "\dn"   # lists schemas
```
- `docs/warehouse/BASELINE_STATE.md` exists and lists the `business` tables with counts.

---

## TASK 1 — Inventory every data folder (data-mining pass)

**Owner:**  AI

**Goal:** produce one document that classifies **every** file in `Youssef_Extractions/` so we
know what is canonical, what is duplicate, what is junk.

**Resources:** all folders under `D:\LPN_PROJECT\Youssef_Extractions\` (`data/`,
`1st`..`4th_Extraction/`, `vente_*_import/`, `forecast_monthly_import/`, `txt/`).

**Steps:**
1. Write a script `scripts/dw/inventory_sources.py` that walks every folder and, for each file,
   records: path, size, sheet/row count (for `.xlsx`/`.csv`), and a guessed **role**
   (`canonical-2024`, `canonical-24M`, `canonical-base`, `enrichment`, `staging-import`,
   `bi-aggregate`, `duplicate`, `junk`).
2. Output `docs/warehouse/SOURCE_INVENTORY.md` (a table) + `source_inventory.json`.
3. In the markdown, explicitly name the **canonical set** = `data/Exported_LPN` and list the
   folders that look redundant (`Exported_data_through_a_drive/` is a duplicate of
   `Exported_LPN/`; `1st..4th_Extraction/` and `vente_*_import/` are older or regenerable).

** DONE WHEN:**
- `docs/warehouse/SOURCE_INVENTORY.md` exists and every folder above appears with a role.
- The canonical raw set is named, and at least the duplicate `Exported_data_through_a_drive`
  is flagged as redundant.

---

## TASK 2 — Approve the canonical source & authorize cleanup

**Owner:**  HUMAN
**Why HUMAN:** choosing which export is the trustworthy source of truth, and **authorizing
archiving/deleting ~6 GB of duplicate folders**, is a destructive, responsibility-bearing
decision. The AI must not delete source data on its own.

**HUMAN steps:**
1. Read `docs/warehouse/SOURCE_INVENTORY.md`.
2. Confirm in writing (a line at the bottom of that file) that `data/Exported_LPN` is the
   canonical raw source.
3. Mark which redundant folders the AI is allowed to **move to** `Youssef_Extractions/_archive/`
   in a later task (do **not** delete yet — just authorize archiving).

** DONE WHEN:**
- `SOURCE_INVENTORY.md` has a "HUMAN DECISION" section naming the canonical folder and the
  folders approved for archiving.

---

## TASK 3 — Deep profiling of the canonical transactional files

**Owner:**  AI

**Goal:** know exactly what each canonical file contains before building anything.

**Resources:** the files named in §1 under `data/Exported_LPN`, especially the transactional
ones: `2024_01_C_ORDER`, `2024_02_C_ORDERLINE`, `2024_03_C_INVOICE`, `2024_04_C_INVOICELINE`,
`53..58`, plus `M_INOUT/LINE`, `C_PAYMENT`, `C_ALLOCATION*`.

**Steps:**
1. Write `scripts/dw/profile_canonical.py`. For each transactional file, output: row count,
   date column min/max, distinct counts of key IDs, % null on key columns, and the financial
   total (sum of `GRANDTOTAL` or `LINENETAMT`).
2. Compute the **overlap** between `2024_*` and `53..58` on `C_ORDER_ID` and `C_INVOICE_ID`
   (how many IDs appear in both) so the dedup rule is justified.
3. Output `docs/warehouse/PROFILING_REPORT.md` with one section per file + an "issues" list
   (e.g. the `57` portfolio inflation, the 130 MB order-line file needing chunked load).

** DONE WHEN:**
- `docs/warehouse/PROFILING_REPORT.md` exists with per-file stats and the 2024/24M overlap count.
- The combined order date span reported is **Jan 2024 → mid-2026** (≈ 29–30 months).

---

## TASK 4 — Define the unified source mapping & dedup rule

**Owner:**  AI

**Goal:** a precise mapping "which file(s) feed which staging table, and how duplicates are
removed", so the load step has no ambiguity.

**Steps:**
1. Write `docs/warehouse/SOURCE_MAPPING.md` listing, for each staging table
   (`stg_c_order`, `stg_c_orderline`, `stg_c_invoice`, `stg_c_invoiceline`, `stg_m_inout`,
   `stg_m_inoutline`, `stg_c_payment`, `stg_c_allocation*`, plus dimensions
   `stg_c_bpartner`, `stg_m_product`, `stg_ad_user`, etc.): the exact source file(s), the
   key column, and the **dedup rule** (for 2024 use `2024_*`; for 2025–2026 use `53..58`;
   union, then drop duplicates on the primary key keeping the `2024_*` row for 2024).
2. Use `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` (not `57`) for the customer portfolio.

** DONE WHEN:**
- `docs/warehouse/SOURCE_MAPPING.md` lists every staging table with its source file(s),
  key, and dedup rule. No staging table is left unmapped.

---

## TASK 5 — Load canonical raw into the `staging` schema (PostgreSQL)

**Owner:**  AI

**Goal:** an exact, deduplicated copy of the canonical sources in `staging.stg_*`, spanning
2024–2026.

**Resources:** the mapping from Task 4; loader pattern in
`scripts/build-forecast-monthly-fact.py` and `scripts/import-vente-bi-enrichment.py`.

**Steps:**
1. Write `scripts/dw/load_staging.py` (pandas + psycopg2, connect as `lpn_app_admin`).
2. `CREATE SCHEMA IF NOT EXISTS staging;` Load each source per Task 4 into `staging.stg_<table>`.
   For files > 200k rows (e.g. `54_..ORDER_LINE_24M`, `2024_02_C_ORDERLINE`) use chunked
   inserts or `COPY`.
3. Apply the dedup rule on primary keys. Wrap in a transaction.
4. Grant `lpn_ai_readonly` SELECT on `staging`.

** DONE WHEN:**
```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "SELECT count(*) AS orders, min(dateordered) lo, max(dateordered) hi FROM staging.stg_c_order;"
```
- `orders` ≈ 2024 orders + unique 2025–2026 orders (no double-count of Jun–Dec 2024).
- `lo` is in Jan 2024 and `hi` is in mid-2026.
- `staging.stg_c_invoice` likewise spans Jan 2024 → mid-2026.

---

## TASK 6 — Build the `warehouse` dimensions

**Owner:**  AI

**Goal:** `warehouse.dim_*` tables (date, customer, commercial, product, product_category,
supplier, geography, payment_term, price_list, document_type, sales_region, warehouse).

**Resources:** `DataWareHouse/.../transform_dimensions.py` (reuse the column logic), the
`stg_*` tables from Task 5.

**Steps:**
1. Write `scripts/dw/build_dimensions.py`. `CREATE SCHEMA IF NOT EXISTS warehouse;`
2. Build `warehouse.dim_date` covering **2024-01-01 → 2026-12-31** (one row per day, with
   year/quarter/month/month_name/week/is_month_end).
3. Build each other `dim_*` from the matching `stg_*`, with a surrogate or natural key and no
   duplicate keys. Use the primary-supplier rule (one supplier per product) for `dim_supplier`.
4. Grant `lpn_ai_readonly` SELECT on `warehouse`.

** DONE WHEN:**
```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "SELECT count(*) FROM warehouse.dim_date; SELECT count(*) FROM warehouse.dim_product; SELECT count(*) FROM warehouse.dim_customer;"
```
- `dim_date` has ≈ 1096 rows (3 years), continuous, no gaps.
- No `dim_*` table has duplicate primary keys (verify with a `GROUP BY ... HAVING count(*)>1`
  returning 0 rows).

---

## TASK 7 — Build the `warehouse` fact tables

**Owner:**  AI

**Goal:** `warehouse.fact_*` at explicit grain, with foreign keys to the dimensions.

**Resources:** `DataWareHouse/.../transform_facts.py`, the grain table in
`Rapport_PFE/chapters/chapitre3.tex` §3.3.1, the `stg_*` tables.

**Steps:**
1. Write `scripts/dw/build_facts.py`. Build:
   - `fact_sales_order` (1 row / order), `fact_sales_order_line` (1 row / order line),
   - `fact_invoice` (1 row / invoice), `fact_invoice_line` (1 row / invoice line),
   - `fact_delivery_line` (1 row / delivery line), `fact_payment_allocation` (1 row / allocation),
   - `fact_stock_snapshot` (if stock source present).
2. Each fact carries the dimension keys (date, customer, commercial, product, etc.) and the
   measures (amounts, quantities). Filter to sales docs (`ISSOTRX='Y'`, valid `DOCSTATUS`).
3. Grant `lpn_ai_readonly` SELECT.

** DONE WHEN:**
```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "SELECT count(*) FROM warehouse.fact_sales_order; SELECT round(sum(grandtotal)) FROM warehouse.fact_invoice;"
```
- `fact_sales_order` row count == distinct orders in `staging.stg_c_order` (zero gap).
- `sum(fact_invoice.grandtotal)` reconciles (±0.5%) with the sum of invoice GRANDTOTAL in
  `staging.stg_c_invoice`.
- No fact row has a dimension key that is missing from its dimension (no orphan FKs).

---

## TASK 8 — Build the `mart` layer

**Owner:**  AI

**Goal:** ready-to-read aggregates for BI and forecasting.

**Steps:**
1. Write `scripts/dw/build_marts.py`. `CREATE SCHEMA IF NOT EXISTS mart;` Build:
   `mart_sales_overview`, `mart_sales_by_commercial`, `mart_sales_by_customer`,
   `mart_sales_by_product`, `mart_sales_by_supplier`, `mart_sales_by_region`,
   and `mart_sales_monthly` (1 row / month, CA facturé + CA commandé, the forecasting input).
2. Grant `lpn_ai_readonly` SELECT.

** DONE WHEN:**
```powershell
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "SELECT count(*), min(month), max(month) FROM mart.mart_sales_monthly;"
```
- `mart_sales_monthly` spans Jan 2024 → mid-2026 (≈ 29 complete months + maybe 1 partial flag).
- The grand total CA in `mart_sales_overview` equals `sum(fact_invoice.grandtotal)` (zero gap).

---

## TASK 9 — Reconciliation & validation report

**Owner:**  AI

**Goal:** one script that proves raw → staging → fact → mart preserves the numbers.

**Resources:** `DataWareHouse/.../validate_etl.py` (reuse), the existing
`etl/validation/etl_validation_report.md` format.

**Steps:**
1. Write `scripts/dw/validate_warehouse.py` that checks, and writes
   `docs/warehouse/WAREHOUSE_VALIDATION.md`:
   - row counts staging vs fact (orders, lines, invoices),
   - financial totals raw vs fact vs mart (must match within 0.5%),
   - orphan FK check (must be 0),
   - month coverage of `mart_sales_monthly`.

** DONE WHEN:**
- `docs/warehouse/WAREHOUSE_VALIDATION.md` exists and every check is PASS (gaps are 0 or
  documented).

---

## TASK 10 — Repoint ONE dashboard KPI to the mart (governance proof)

**Owner:**  AI

**Goal:** prove the app can read the governed layer without changing the displayed number.

**Resources:** `services-java/.../SalesDashboardService.java`.

**Steps:**
1. Pick the headline CA KPI in `SalesDashboardService.java`. Add a query that reads
   `mart.mart_sales_overview` (keep the old raw query as a commented fallback).
2. Do not change the response shape. Do not touch other KPIs in this task.
3. Run the Java tests.

** DONE WHEN:**
```powershell
cd D:\LPN_PROJECT\services-java; .\gradlew.bat :llm-orchestrator:test
curl "http://localhost:8081/v1/sales-dashboard"
```
- Java tests pass.
- The CA KPI value from the endpoint equals the previous value (within rounding) — confirms
  the mart reconciles with what the app showed before.

---

## TASK 11 — Repoint the forecasting fact to the warehouse (extended history)

**Owner:**  AI

**Goal:** forecasting reads the governed `mart_sales_monthly` (≈ 29 months) instead of a
separate raw script.

**Resources:** `scripts/build-forecast-monthly-fact.py`, `services-python/predictive`.

**Steps:**
1. Change the monthly fact source to `mart.mart_sales_monthly` (or rebuild
   `business.fact_sales_monthly` from it). Keep the same column names so the predictive
   service is unchanged.
2. Restart predictive; confirm `/health` shows the new month count.

** DONE WHEN:**
```powershell
curl "http://localhost:8085/health"
```
- `complete_months` is now ≈ 29 (was 24) and `month_min` is in Jan 2024.

---

## TASK 12 — Documentation, schema metadata, and repo hygiene

**Owner:**  AI (docs) +  HUMAN (authorize the archive move)

**AI steps:**
1. Update `DataWareHouse/processus_de_vente/schema_overview.md` to state the warehouse is now
   materialized in PostgreSQL (`staging`/`warehouse`/`mart`) on full 2024–2026 data.
2. Add the new `mart_*` tables to `docs/schema_metadata.csv` so the AI text-to-SQL can
   retrieve them; reindex schema-retrieval (`POST /admin/reindex`).
3. Write `scripts/dw/README.md` describing the run order (`load_staging` → `build_dimensions`
   → `build_facts` → `build_marts` → `validate_warehouse`).

**HUMAN step (why human: destructive):**
4. After reviewing, authorize the AI to move the duplicate/old folders approved in Task 2 to
   `Youssef_Extractions/_archive/` (move, not delete). The AI performs the move only after
   this written go-ahead.

** DONE WHEN:**
- `docs/schema_metadata.csv` contains the `mart_*` rows and schema-retrieval `/health`
  reflects the new collection size.
- `scripts/dw/README.md` documents the full run order.
- (After human go-ahead) the approved folders are under `_archive/` and the app still runs.

---

## Recommended execution order (summary)

| Task | Owner | One-line |
|---|---|---|
| 0 | 🧑→🤖 | Start Docker, snapshot current state |
| 1 | 🤖 | Inventory every data folder |
| 2 | 🧑 | Approve canonical source + cleanup |
| 3 | 🤖 | Profile canonical transactional files |
| 4 | 🤖 | Source mapping + dedup rule |
| 5 | 🤖 | Load raw → `staging` schema |
| 6 | 🤖 | Build `warehouse.dim_*` |
| 7 | 🤖 | Build `warehouse.fact_*` |
| 8 | 🤖 | Build `mart_*` |
| 9 | 🤖 | Reconciliation/validation report |
| 10 | 🤖 | Repoint one dashboard KPI to mart |
| 11 | 🤖 | Repoint forecasting fact to mart |
| 12 | 🤖+🧑 | Docs, schema metadata, archive cleanup |

## What "done" means for the whole roadmap

- One PostgreSQL warehouse (`staging` → `warehouse` → `mart`) built on full **2024–2026** data.
- Totals reconcile end to end (validation report all PASS).
- The app dashboard, forecasting (and later Superset + AI) read the **same governed marts**.
- The current app never broke during the process.
- Source folders consolidated; one canonical raw set; duplicates archived (with human approval).

## HUMAN-only tasks, collected (do these yourself)

- **Task 0:** start/repair Docker Desktop.
- **Task 2:** approve the canonical source and authorize archiving the duplicates.
- **Task 12 (step 4):** give the go-ahead to move old folders to `_archive/`.
- **If any task reports missing data** (a gap in 2024 or a needed table absent): a new
  **Oracle/Compiere extraction** is required — only the human can run it (the AI is forbidden
  to touch Oracle). Use the query patterns in `Youssef_Extractions/txt/req_*.txt` as a base.

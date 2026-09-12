# 2024 Data → Data Warehouse — Implementation Tasks (DW-01 … DW-10)

> **Prerequisite:** read [`00_CONTEXT_AND_HISTORY.md`](00_CONTEXT_AND_HISTORY.md) first.
>
> Copy **one block at a time** into Claude Code. Run tasks **in order**. Do **not**
> start a task until the previous task's milestone exists in
> [`../context_bi_service.md`](../context_bi_service.md).
>
> Each block is self-contained and ends with the **mandatory closing block**
> (append a milestone). Every block restates the hard rules so a cold agent cannot
> drift.

**Model legend:** 🟩 Sonnet 4.6 (default) · 🟪 Opus 4.8 (architecture-critical).

**Hard rules (every task obeys these — never break):**
1. Never connect to LPN Oracle/Compiere. Flat files only.
2. Never delete/overwrite/move anything under `Marwane_Extractions/`.
3. Never modify the `business` schema (live dashboard + forecasting).
4. Never load a >100 MB / multi-GB file fully into memory — stream/chunk (≤5,000 rows,
   `openpyxl read_only=True`). Use CSVs for `M_PRODUCT` / `M_PRODUCT_PO`.
5. Idempotent only: staging TRUNCATE-per-`_source_tag`; dims UPSERT; facts TRUNCATE+reload.
6. Dedup: 2024 dates ← `2024_xlsx`; 2025+ ← `csv_2025`.
7. Exclude `57_…PORTFOLIO_24M.xlsx`. Windows / PowerShell. Write DB = `localhost:5433/lpn_ai_bi`.

**Mandatory closing block (already embedded in every task):**
```
At the end of this task, APPEND a new milestone entry to
docs/bi-service-roadmap/context_bi_service.md (do not overwrite, append only).
Include: date, task ID + title, summary, files/folders inspected, files created/
modified, technical decisions, commands run, validation results, problems, and the
next recommended task.
```

---
---

# PHASE A — Foundation

## DW-01 — Provision schemas & run warehouse DDL  🟩 Sonnet 4.6
**Skills:** `/microservices-patterns`

```
Read docs/bi-service-roadmap/2024_implementation/00_CONTEXT_AND_HISTORY.md and
docs/bi-service-roadmap/context_bi_service.md FIRST.

Task DW-01. Create the warehouse schemas and execute the existing DDL drafts on the
WRITE database (localhost:5433/lpn_ai_bi). Do NOT touch the business schema.

Steps:
1. Reconcile schema naming: docs/bi-service-roadmap/sql/staging.sql currently declares
   schema `stg`; the locked convention is schema `staging` (tables keep the stg_ prefix,
   e.g. staging.stg_c_order). Edit staging.sql so every object is in `staging`, plus the
   `etl` schema (etl_run_log, stg_rejects, etl_run_table_stats). warehouse.sql already
   targets schema `warehouse`. Re-validate both with sqlglot (0 errors).
2. Connect to localhost:5433/lpn_ai_bi (app/write role) and run, in a transaction:
   CREATE SCHEMA IF NOT EXISTS staging; etl; warehouse;  then staging.sql, then warehouse.sql.
   Do NOT create mart yet (DW-08). Do NOT run any INSERT of source data here — only DDL
   plus the dimension UNKNOWN(key=0) rows that warehouse.sql already contains.
3. Grant: GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly is deferred to DW-08; for now grant
   USAGE+SELECT on warehouse to lpn_ai_readonly (read-only) so later validation can use it.
4. Write docs/bi-service-roadmap/2024_implementation/_run/DW01_provision_report.md recording:
   exact statements run, schemas/tables created, row counts (UNKNOWN rows = 1 per dim),
   and a \dn + \dt staging.* + \dt warehouse.* listing.

Validate: psql -c "\dn" shows staging, etl, warehouse (and existing business untouched);
every warehouse.dim_* has exactly one row at key=0; business table counts unchanged.
Commit: "feat(dwh): provision staging/etl/warehouse schemas + DDL".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---
---

# PHASE B — Staging load

## DW-02 — Build the chunked staging loader  🟪 Opus 4.8 (architecture-critical)
**Skills:** `/python-testing-patterns`, `/microservices-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md and context_bi_service.md FIRST.

Task DW-02. Build a reusable, idempotent, MEMORY-SAFE Python loader that streams source
files into staging.stg_* on localhost:5433/lpn_ai_bi. Code + a SMALL smoke load only
(load just the small 2024 dims in this task; the big files come in DW-03).

Requirements:
- New module under services-python/data-import/ (do NOT modify the old DataWareHouse
  file→file ETL; it stays as reference). Use psycopg2/psycopg + openpyxl read_only=True.
- xlsx reader: iterate rows with read_only=True, yield dicts, NEVER pd.read_excel a >50 MB
  file. CSV reader: stream with csv module / COPY (encoding utf-8-sig, delimiter ';').
- Each row gets metadata: _loaded_at, _source_file, _source_tag ('2024_xlsx' or 'csv_2025'),
  _etl_run_id (UUID). Insert in batches of <=5,000 via execute_values or COPY.
- Idempotency: before loading a (table, _source_tag) pair, DELETE existing rows for that
  _source_tag (not TRUNCATE — the other window must survive), then bulk insert.
- A run is logged to etl.etl_run_log; per-table counts to etl.etl_run_table_stats;
  rejects (unparseable rows / PK nulls) to etl.stg_rejects with reason.
- Config-driven source map: file -> staging table -> _source_tag -> chunked? Reuse the
  file list in 00_CONTEXT_AND_HISTORY.md section 2.1. Mark 2024_02/04/06 chunked=true and
  2024_16/2024_18 excluded (use CSV).
- SMOKE LOAD in this task: load ONLY 2024_13 C_REGION, 2024_14 C_CITY, 2024_15 AD_USER,
  2024_17 M_PRODUCT_CATEGORY, 2024_20 M_PRODUCT_TYPE, 2024_23 C_DOCTYPE, 2024_26 M_WAREHOUSE,
  2024_28 C_PAYMENTTERM, 2024_29 M_PRICELIST, 2024_30 C_SALESREGION (all tiny). Verify counts.

Write the loader package + docs/bi-service-roadmap/2024_implementation/_run/DW02_loader_design.md.
Add pytest unit tests (mock DB) for: chunk batching, metadata stamping, reject capture,
idempotent re-run (second run = same row count, no duplicates).

Validate: pytest green; the 10 smoke tables in staging have row counts matching section 2.1;
re-running the smoke load does not change counts (idempotent). business schema untouched.
Commit: "feat(dwh): chunked memory-safe staging loader + smoke load".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## DW-03 — Load all 2024 transactional + dimension files  🟪 Opus 4.8
**Skills:** `/python-testing-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md and context_bi_service.md FIRST.

Task DW-03. Using the DW-02 loader, load the FULL 2024 window into staging with
_source_tag='2024_xlsx'. This is the heavy task — respect the no-multi-GB rule.

Load order (small/medium first, giants last):
1. Remaining dims: 2024_10 C_BPARTNER (21 MB), 2024_11 C_BPARTNER_LOCATION, 2024_12 C_LOCATION,
   2024_19 C_BPARTNER_VENDORS (7 MB), 2024_21 M_PRODUCT_THEME, 2024_22 M_PRODUCT_COLLECTION
   (9 MB).
   DROPPED FROM v1 SCOPE (DW-03 decision, 2026-06-29): 2024_24 C_TAX, 2024_25 AD_ORG,
   2024_27 M_LOCATOR, 2024_35 CUSTOMER_PORTFOLIO. None is a star-schema dimension or fact
   source: AD_ORG is a singleton (4 orgs, all one client), M_LOCATOR is moot (single
   warehouse/locator), C_TAX is out of sales-CA scope, and CUSTOMER_PORTFOLIO is a derived
   aggregate rebuilt from facts (used only for DW-09 reconciliation). The BI-06 DDL has no
   staging table for any of them, so no `stg_*` table is added; they are absent from
   `source_map.json` by design.
2. Transactional small/medium: 2024_01 C_ORDER (14 MB), 2024_03 C_INVOICE, 2024_05 M_INOUT
   (25 MB), 2024_07 C_PAYMENT, 2024_08 C_ALLOCATIONHDR, 2024_09 C_ALLOCATIONLINE (10 MB).
3. GIANTS via 5,000-row chunks (read_only streaming, commit per batch, log progress):
   2024_04 C_INVOICELINE (81 MB), 2024_06 M_INOUTLINE (132 MB), 2024_02 C_ORDERLINE (254 MB).
4. M_PRODUCT and M_PRODUCT_PO: DO NOT open the 2.27 GB / 996 MB xlsx. Load
   vente_2025_2026_import/csv/M_PRODUCT.csv and M_PRODUCT_PO.csv but tag them '2024_xlsx'
   ONLY IF the product dimension would otherwise be empty for 2024; otherwise defer product
   loading entirely to DW-04 under 'csv_2025' (products are SCD1 and identical across windows).
   Record the decision explicitly.

Guardrails: monitor process memory; each chunk must release rows; never hold a full giant
file. If a file exceeds a memory budget, fail loudly rather than swap.

Write docs/bi-service-roadmap/2024_implementation/_run/DW03_load_2024_report.md with per-table
row counts, reject counts, durations, peak memory, and any rows rejected (with reasons).

Validate: staging row counts for 2024 tables are within +-1% of section 2.1 estimates
(exact for the small ones); etl.stg_rejects reject rate < 5% for transactional tables;
re-run of any one table is idempotent for its _source_tag; business untouched.
Commit: "feat(dwh): load full 2024 window into staging (chunked)".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## DW-04 — Load the 2025–2026 CSV window into staging  🟩 Sonnet 4.6
**Skills:** `/python-testing-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md and context_bi_service.md FIRST.

Task DW-04. Load Youssef_Extractions/vente_2025_2026_import/csv/*.csv into the SAME
staging.stg_* tables with _source_tag='csv_2025', so staging holds BOTH windows for the
dedup step. Use COPY where possible (utf-8-sig, delimiter ';'). Do NOT read from or write
to the business schema — load from the CSV files directly.

Cover all entities present in the CSV package (C_ORDER, C_ORDERLINE, C_INVOICE,
C_INVOICELINE, M_INOUT, M_INOUTLINE, C_PAYMENT, C_ALLOCATIONHDR, C_ALLOCATIONLINE,
C_BPARTNER, C_BPARTNER_VENDOR, C_BPARTNER_LOCATION, C_LOCATION, M_PRODUCT, M_PRODUCT_PO,
M_PRODUCT_THEME, M_PRODUCT_COLLECTION, RV_STORAGE, C_REGION, C_CITY, C_SALESREGION, and the
small dims). Map each CSV header to the staging column via the existing column-standardizer.

Validate: each csv_2025 table row count equals vente_2025_2026_import/manifest.json; the
composite PK (natural_key, _source_tag) holds (no PK violation across the two windows);
overlap probe — at least some C_ORDER_IDs exist under BOTH _source_tag values for
Jun-Dec 2024 (proves the dedup step in DW-06 is necessary). business untouched.
Commit: "feat(dwh): load 2025-2026 CSV window into staging".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---
---

# PHASE C — Warehouse build

## DW-05 — Build dimensions (staging → warehouse.dim_*)  🟩 Sonnet 4.6
**Skills:** `/microservices-patterns`, `/python-testing-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md, context_bi_service.md, and
docs/bi-service-roadmap/_design/07_warehouse_design.md FIRST.

Task DW-05. Populate all 12 warehouse dimensions from staging, applying the BI-05 Layer-3
cleaning rules. UPSERT (idempotent). Preserve the UNKNOWN key=0 rows already present.

Per dim (see 07_warehouse_design.md for columns):
- dim_date: GENERATE rows for 2024-01-01 .. 2026-12-31 (date_key=YYYYMMDD int). Keep key=0.
- dim_customer: from stg_c_bpartner where iscustomer='Y' and value<>'NA'; SCD1.
- dim_commercial: from stg_ad_user; INITCAP(TRIM(name)).
- dim_product_category: from stg_m_product_category; REGEXP_REPLACE trailing \t/\n.
- dim_supplier: from stg_c_bpartner_vendor (DEDUP-SUPP-01 primary supplier).
- dim_product: from stg_m_product (csv) enriched with type/theme/collection; FK to
  category + supplier; theme_name/collection_name denormalized TEXT.
- dim_sales_region, dim_geography (-> sales_region), dim_payment_term, dim_price_list,
  dim_document_type (issalestransaction='Y'), dim_warehouse (name from stg_m_warehouse.name).
Apply: NULLIF(id,0) on FK ids (except C_UOM_ID, AD_CLIENT_ID); TRIM/INITCAP per BI-05;
dedup so each natural key yields one current row.

Write docs/bi-service-roadmap/2024_implementation/_run/DW05_dimensions_report.md (row counts,
distinct natural keys, any rows routed to UNKNOWN). Add SQL/py assertions.

Validate: every dim has exactly one key=0 row + N business rows; no NULL natural keys in
non-zero rows; FK columns in dim_product/dim_geography resolve (no orphan -> set to 0);
re-run is idempotent (counts stable). business untouched.
Commit: "feat(dwh): build warehouse dimensions from staging".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## DW-06 — Build order/invoice facts with dedup + FK resolution  🟪 Opus 4.8
**Skills:** `/microservices-patterns`, `/python-testing-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md, context_bi_service.md,
docs/bi-service-roadmap/_design/07_warehouse_design.md, and
DataWareHouse/processus_de_vente/03_fact_grain_design.md FIRST.

Task DW-06. Build fact_sales_order, fact_sales_order_line, fact_invoice, fact_invoice_line
from staging. This task OWNS the cross-window dedup — get it exactly right.

Dedup (critical): for each natural key (C_ORDER_ID, C_ORDERLINE_ID, C_INVOICE_ID,
C_INVOICELINE_ID), choose ONE staging row:
- if the transaction date is in 2024 -> prefer _source_tag='2024_xlsx';
- if 2025 or later -> prefer _source_tag='csv_2025';
- if a key exists in only one window, take that one.
Implement via ROW_NUMBER() OVER (PARTITION BY natural_key ORDER BY <preference>).

FK resolution: map every dimension id to its surrogate via the dim natural key; unresolved
-> key 0. Dates -> date_key (YYYYMMDD); NULL/invalid date -> 0. Filters: orders
DOCSTATUS IN ('CO','CL') + sales doctypes; invoices DOCSTATUS='CO'. Keep degenerate dims
(document_no, doc_status, source_c_order_id, c_orderline_id) per the design. Measures:
grand_total_amount, total_lines_amount, line_net_amount, quantities, *_count. Negative
GRANDTOTAL (credit notes) is VALID — keep.

TRUNCATE+reload each fact (idempotent). Write
docs/bi-service-roadmap/2024_implementation/_run/DW06_facts_orders_invoices_report.md with
row counts, dedup collisions resolved (how many keys appeared in both windows), null-FK->0
rates, and CA totals split by year (2024 vs 2025 vs 2026).

Validate: fact grain matches 03_fact_grain_design.md (UNIQUE on each natural key after dedup);
no duplicate natural keys; SUM(grand_total) for the 2025-2026 window matches the equivalent
business-schema total within rounding (reconciliation pre-check); 2024 totals are now > 0.
Commit: "feat(dwh): build order/invoice facts with cross-window dedup".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## DW-07 — Build delivery, payment, and stock facts  🟩 Sonnet 4.6
**Skills:** `/microservices-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md, context_bi_service.md,
07_warehouse_design.md, and 03_fact_grain_design.md FIRST.

Task DW-07. Build the remaining 4 facts: fact_delivery (M_INOUT_ID), fact_delivery_line
(M_INOUTLINE_ID), fact_payment_allocation (C_ALLOCATIONLINE_ID), fact_stock_snapshot
(product/attrset/warehouse/locator). Same dedup + FK-resolution rules as DW-06.

Notes from the design:
- fact_delivery: filter ISSOTRX='Y' + DOCSTATUS='CO'; document_type_key often -> 0 (DECISION-08).
- fact_payment_allocation: commercial_key resolves via invoice join, else 0 (DECISION-07).
- fact_stock_snapshot: include m_locator_id degenerate dim (DECISION-05); measures are
  SEMI-ADDITIVE (do not sum across snapshot dates); snapshot_date_key from DATELASTINVENTORY;
  RV_STORAGE is csv-only (no 2024 xlsx) so _source_tag='csv_2025'.

TRUNCATE+reload each (idempotent). Write
docs/bi-service-roadmap/2024_implementation/_run/DW07_facts_delivery_payment_stock_report.md.

Validate: grains match 03_fact_grain_design.md; delivery/payment counts plausible vs source;
stock snapshot rows = distinct (product,attrset,warehouse,locator); re-run idempotent.
Commit: "feat(dwh): build delivery/payment/stock facts".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---
---

# PHASE D — Mart build

## DW-08 — Create mart.mart_* serving views (5 pages + stock)  🟪 Opus 4.8
**Skills:** `/microservices-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md, context_bi_service.md,
07_warehouse_design.md, docs/semantic_layer/metrics.yml, and inspect
frontend/src/features/bi/pages/* (BiOverviewPage, BiCommandesPage, BiRevenuePage,
BiArticlesPage, BiClientsPage, BiCommercialPage) FIRST.

Task DW-08. This task SUPERSEDES the unfinished BI-08. Design AND create the mart schema:
CREATE SCHEMA mart, then one analytical view per BI page, reusing metrics.yml formulas
verbatim. Grant USAGE+SELECT on mart to lpn_ai_readonly (the BI read-only role @5432 uses
search_path=mart).

Define at least:
- mart_overview: KPI row(s) — ca_commande, ca_facture, nombre_commandes, nombre_clients_actifs,
  nombre_produits_vendus, factures_payees/impayees — by day/week/month grain.
- mart_commandes: order volume, CA commande, by status, by doctype, trend.
- mart_revenue: ca_facture vs ca_commande, couverture_facturation, paid/unpaid, trend.
- mart_articles: top_produits_ca_facture, ca_par_type_article, ca_par_fournisseur, qty.
- mart_clients: top_clients_ca_facture, active customers, geography, portfolio.
- mart_commercial: ca_par_commercial (ordered + invoiced), by month.
- mart_stock (aux): stock_disponible, produits_stock_risque.
Each view exposes: a date grain column, the relevant dimension label columns, and named
measure columns. Document, per page, every visible widget -> mart view + column.

Write docs/bi-service-roadmap/_design/08_mart_design.md (the contract) and
docs/bi-service-roadmap/sql/marts.sql (executed). sqlglot-validate marts.sql.

Validate: every widget on all 5 pages maps to a named mart column (table in the design doc);
each mart view returns rows for BOTH 2024 and 2025-2026; lpn_ai_readonly can SELECT every
mart view via search_path=mart on port 5432.
Commit: "feat(dwh): mart serving views for 5 BI pages".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---
---

# PHASE E — Validate & cut over

## DW-09 — Reconciliation & data-quality gate  🟪 Opus 4.8
**Skills:** `/python-testing-patterns`

```
Read 00_CONTEXT_AND_HISTORY.md and context_bi_service.md FIRST.

Task DW-09. Prove the warehouse is correct before any KPI is repointed. Build a
reconciliation report comparing warehouse/mart numbers against (a) the business schema for
the 2025-2026 window and (b) the 2024 BI-aggregate files (2024_31..34) for the 2024 window.

Checks (each PASS/FAIL with the two numbers shown):
- Row-count parity: warehouse facts for 2025-2026 vs business equivalents.
- Golden numbers: e.g. SalesDashboard order_count for the current window must equal
  mart_overview's equivalent (state the exact value observed, do not assume 616).
- CA totals: SUM(ca_facture) and SUM(ca_commande) per year (2024/2025/2026); 2025-2026 must
  match business; 2024 must match 2024_32_COMMERCIAL_REAL_CA_MONTHLY within tolerance.
- Dedup integrity: zero duplicate natural keys in any fact.
- Null-FK budget: share of key=0 per fact dimension below an agreed threshold.
- Reject rate from etl.stg_rejects < 5% on transactional tables.
- Semi-additive guard: documentation check that stock measures are not summed over time.

Write docs/bi-service-roadmap/2024_implementation/_run/DW09_reconciliation_report.md with a
clear GO / NO-GO verdict and a list of any discrepancies to fix (loop back to DW-05/06/07).

Validate: report generated; the 2025-2026 golden numbers match business exactly; any FAIL is
itemized with the offending keys. Do NOT repoint the live dashboard in this task.
Commit: "test(dwh): reconciliation & data-quality gate".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---

## DW-10 — Read-only grants & cutover documentation  🟩 Sonnet 4.6
**Skills:** `/microservices-patterns`, `/java-springboot`

```
Read 00_CONTEXT_AND_HISTORY.md, context_bi_service.md, and DW-09's report FIRST.
Proceed only if DW-09 verdict is GO.

Task DW-10. Finalize serving access and document the cutover path (do NOT rewrite the live
SalesDashboard yet — that happens per-chart in the next phase).

Steps:
- Confirm GRANT USAGE ON SCHEMA mart + SELECT ON ALL TABLES IN SCHEMA mart TO lpn_ai_readonly,
  and ALTER DEFAULT PRIVILEGES so future mart views are readable. Verify connection
  localhost:5432 search_path=mart as lpn_ai_readonly returns rows.
- Document, in docs/bi-service-roadmap/2024_implementation/_run/DW10_cutover.md: how the BI API
  will switch each KPI from business to mart, the reconciliation gate (no repoint until
  mart==business), a rollback note, and how to re-run the whole pipeline (DW-02..DW-08) idempotently.
- Produce a one-page "pipeline runbook": commands, order, expected durations, where logs and
  reports land.

Validate: lpn_ai_readonly @5432 can SELECT every mart view; runbook lists every step in order;
business schema still serves the live dashboard unchanged.
Commit: "docs(dwh): read-only grants & cutover runbook".

<MANDATORY CLOSING BLOCK — append milestone entry to context_bi_service.md>
```

---
---

## Dependency graph

```
DW-01 ─► DW-02 ─► DW-03 ─┐
                         ├─► DW-05 ─► DW-06 ─► DW-07 ─► DW-08 ─► DW-09 ─► DW-10
                 DW-04 ──┘
```
- DW-03 and DW-04 both load staging; DW-05 needs both done.
- DW-06 needs DW-05 (dims) for FK resolution.
- DW-08 (marts) needs all facts (DW-06, DW-07).
- DW-09 gates DW-10.

## After DW-10 — per-chart phase

With `mart.*` populated and reconciled, implement visualisations one case at a time
(commercial CA, CA par article/catégorie/type/fournisseur, CA facturé vs commandé, top
clients, commandes par statut/type, ruptures de stock). Each chart = pick mart column(s) →
BI endpoint + DTO → Recharts widget → validate the number. See
[`00_CONTEXT_AND_HISTORY.md`](00_CONTEXT_AND_HISTORY.md) §9.

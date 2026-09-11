# 2024 Data → Data Warehouse — Context & History

> **READ THIS FILE FIRST** before starting any task in
> [`01_IMPLEMENTATION_TASKS.md`](01_IMPLEMENTATION_TASKS.md).
>
> This is the single source of truth for the **2024 data implementation phase**:
> what the project is, what we have already built, what currently lives in the
> database, the gap we are closing, and the plan to load the **full 2024 history**
> into the `staging → warehouse → mart` layers of `lpn_ai_bi`.
>
> Companion file: [`01_IMPLEMENTATION_TASKS.md`](01_IMPLEMENTATION_TASKS.md) — the
> ready-to-run, broken-down task prompts (DW-01 … DW-10).

**Last updated:** 2026-06-29 · **Branch:** `bi-polished-dashboard`

---

## 0. TL;DR (read this if nothing else)

- The live `business` schema contains **only 2025–2026 sales data**. The complete
  **2024 history has never been loaded** into PostgreSQL.
- We have already **designed** (but not executed) the warehouse: staging DDL
  (`sql/staging.sql`), warehouse dim/fact DDL (`sql/warehouse.sql`), a cleaning
  spec, an entity catalogue, and grain definitions (BI-01 … BI-07).
- The **existing ETL** in `DataWareHouse/processus_de_vente/etl/` is a *file→file*
  prototype that points at the **wrong, superseded source** and **full-loads** Excel
  files — it cannot safely process the multi-GB 2024 files and does not write to the DB.
- **This phase (DW-01 … DW-10)** builds a real, idempotent, **chunked DB loader**
  and runs the full pipeline so that `staging` + `warehouse` + `mart` schemas hold
  **2024 + 2025–2026 combined**, deduped and reconciled.
- **After** this phase, you will build one chart/case at a time (commercial CA,
  CA par article, etc.) on top of the finished `mart.*` views.

---

## 1. What the project is

**LPN AI-BI** is a PFE (graduation project) that turns the LPN ERP (Compiere/Oracle
origin) sales data into a modern analytical stack:

- A **rebuilt sales data warehouse** (`processus de vente`) in PostgreSQL, star-schema,
  feeding 5 polished BI pages plus an AI question-answering layer.
- **Hard deadline: 2026-07-31.**
- Scope of the warehouse is **the sales process only** (commandes, ventes, factures,
  livraisons, paiements, articles, clients, commerciaux, stocks).

The data was extracted from LPN's production ERP into Excel/CSV files
(`Youssef_Extractions/`). **We never connect to LPN's Oracle/Compiere DB** — the flat
files are the only source.

---

## 2. The data landscape (2024 vs 2025–2026)

There are **two source windows**, both exported from the same ERP:

| Window | Source | Format | Status in DB |
|---|---|---|---|
| **2024** (full year, Jan–Dec) | `Youssef_Extractions/data/Exported_LPN/2024_01..2024_35` | xlsx (some multi-GB) | ❌ **NOT loaded** |
| **2025–2026** (Jun 2024 → Jun 2026) | `Youssef_Extractions/vente_2025_2026_import/csv/` | CSV (clean) | ✅ Loaded in `business` |

**Overlap:** Jun–Dec 2024 exists in *both* windows. The locked **dedup rule**:
> Keep the **`2024_*`** row for any 2024 date; keep the **CSV** row for 2025–2026.
> In staging, both coexist via a composite PK `(natural_key, _source_tag)` where
> `_source_tag ∈ {'2024_xlsx','csv_2025'}`; the warehouse build resolves one per
> natural key, **CSV preferred only for 2025+ dates, 2024_xlsx preferred for 2024**.

### 2.1 The 35 canonical 2024 files (verified sizes on disk, 2026-06-29)

Transactional (the bulk):
| File | Size | Entity | Rows (approx) | Load strategy |
|---|---|---|---|---|
| `2024_01_C_ORDER.xlsx` | 14 MB | C_ORDER | 18,024 | direct stream |
| `2024_02_C_ORDERLINE.xlsx` | **254 MB** | C_ORDERLINE | ~354K | **chunk 5K rows** |
| `2024_03_C_INVOICE.xlsx` | 3 MB | C_INVOICE | 5,264 | direct stream |
| `2024_04_C_INVOICELINE.xlsx` | **81 MB** | C_INVOICELINE | ~270K | **chunk 5K rows** |
| `2024_05_M_INOUT.xlsx` | 25 MB | M_INOUT | 44,232 | stream |
| `2024_06_M_INOUTLINE.xlsx` | **132 MB** | M_INOUTLINE | ~350K | **chunk 5K rows** |
| `2024_07_C_PAYMENT.xlsx` | 2 MB | C_PAYMENT | 3,775 | direct |
| `2024_08_C_ALLOCATIONHDR.xlsx` | 1 MB | C_ALLOCATIONHDR | 4,113 | direct |
| `2024_09_C_ALLOCATIONLINE.xlsx` | 10 MB | C_ALLOCATIONLINE | 86,863 | stream |

Dimensions / reference:
| File | Size | Entity | Rows | Note |
|---|---|---|---|---|
| `2024_10_C_BPARTNER.xlsx` | 21 MB | C_BPARTNER | 42,434 | customers + vendors |
| `2024_11_C_BPARTNER_LOCATION.xlsx` | 1 MB | C_BPARTNER_LOCATION | 4,221 | |
| `2024_12_C_LOCATION.xlsx` | 1 MB | C_LOCATION | 4,108 | |
| `2024_13_C_REGION.xlsx` | 1 MB | C_REGION | 87 | |
| `2024_14_C_CITY.xlsx` | 1 MB | C_CITY | 364 | |
| `2024_15_AD_USER_SALESREPS.xlsx` | 1 MB | AD_USER | 31 | commerciaux |
| `2024_16_M_PRODUCT.xlsx` | **2.27 GB** | M_PRODUCT | ~36K | ⛔ **use `vente_2025_2026_import/csv/M_PRODUCT.csv` (35,822 rows) instead** |
| `2024_17_M_PRODUCT_CATEGORY.xlsx` | 1 MB | M_PRODUCT_CATEGORY | 41 | trailing \t/\n to clean |
| `2024_18_M_PRODUCT_PO.xlsx` | **996 MB** | M_PRODUCT_PO | ~39K | ⛔ **use `…/csv/M_PRODUCT_PO.csv` (39,359 rows) instead** |
| `2024_19_C_BPARTNER_VENDORS.xlsx` | 7 MB | C_BPARTNER_VENDOR | 14,264 | suppliers |
| `2024_20_M_PRODUCT_TYPE.xlsx` | 1 MB | M_PRODUCT_TYPE | 27 | |
| `2024_21_M_PRODUCT_THEME.xlsx` | 1 MB | M_PRODUCT_THEME | 1,513 | |
| `2024_22_M_PRODUCT_COLLECTION.xlsx` | 9 MB | M_PRODUCT_COLLECTION | ~86K | |
| `2024_23_C_DOCTYPE.xlsx` | 1 MB | C_DOCTYPE | 79 | filter ISSALESTRANSACTION='Y' |
| `2024_24_C_TAX.xlsx` | 1 MB | C_TAX | 6 | |
| `2024_25_AD_ORG.xlsx` | 1 MB | AD_ORG | 4 | |
| `2024_26_M_WAREHOUSE.xlsx` | 1 MB | M_WAREHOUSE | 7 | |
| `2024_27_M_LOCATOR.xlsx` | 1 MB | M_LOCATOR | — | |
| `2024_28_C_PAYMENTTERM.xlsx` | 1 MB | C_PAYMENTTERM | 7 | |
| `2024_29_M_PRICELIST.xlsx` | 1 MB | M_PRICELIST | 22 | all MAD |
| `2024_30_C_SALESREGION.xlsx` | 1 MB | C_SALESREGION | 13 | |

BI aggregates (NOT staging sources — reference/validation only):
`2024_31_COMMERCIAL_MONTHLY_FUNNEL`, `2024_32_COMMERCIAL_REAL_CA_MONTHLY`,
`2024_33_THEME_TYPE_DISTRIBUTOR_CA`, `2024_34_GEOGRAPHY_CA`. Use these to
**cross-check** warehouse totals, not to load.

Customer portfolio:
`2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` (826 rows) — ✅ use this for 2024 portfolio.

### 2.2 Permanent traps / exclusions (do not load)

- ⛔ `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` — inflated CA (many-to-many join bug).
- ⛔ `2024_16_M_PRODUCT.xlsx` (2.27 GB) / `2024_18_M_PRODUCT_PO.xlsx` (996 MB) — use the CSVs.
- ⛔ `DATAWHAREHOUSE_EXPORT/3.xlsx`,`4.xlsx`, all `Book*.xlsx`, `~$*` — junk.
- ⛔ `Exported_data_through_a_drive/` — duplicate of `Exported_LPN/`.

---

## 3. What has already been DONE

### 3.1 BI roadmap design tasks (docs/bi-service-roadmap/)

| Task | Deliverable | Status |
|---|---|---|
| BI-00 | Repo & service inventory (`_inventory/00_repo_snapshot.md`) | ✅ |
| BI-01 | Data folder inventory (`_inventory/01_data_inventory.md`) | ✅ |
| BI-02 | Transactional profiling (`_profiling/02_transactional_profile.md`) | ✅ |
| BI-03 | Format/quality profile (`_profiling/03_format_quality_profile.md`) | ✅ |
| BI-04 | Entity catalogue (`_profiling/04_entity_catalogue.md`) | ✅ |
| BI-05 | Cleaning/dedup/normalize plan (`_plan/05_cleaning_plan.md`) | ✅ |
| BI-06 | Staging schema DDL (`_design/06_staging_design.md`, `sql/staging.sql`) | ✅ design |
| BI-07 | Warehouse dim/fact DDL (`_design/07_warehouse_design.md`, `sql/warehouse.sql`) | ✅ design |
| BI-08 | Mart design (`sql/marts.sql`) | ⚠️ **NOT done** — folded into DW-08 below |

All of BI-01…BI-07 are **design/plan only** — **no DDL has been executed**, no data loaded.

### 3.2 What is actually running

- **`business` schema** in `lpn_ai_bi` holds the **2025–2026 CSV load** (row counts match
  `vente_2025_2026_import/manifest.json` exactly). It is what the current
  `SalesDashboardService` serves today. **Protected — never modified by this phase.**
- **Forecasting** facts (`business.fact_sales_monthly*`) feed the prévisions service.
  **Protected.**

### 3.3 Existing warehouse DDL drafts (ready to run, after a naming fix)

- `docs/bi-service-roadmap/sql/staging.sql` — 25 `stg_*` tables + `etl` infra
  (run log, rejects, stats). **Currently declares schema `stg`** — must be renamed to
  schema **`staging`** to match the locked convention (see §6, DW-01).
- `docs/bi-service-roadmap/sql/warehouse.sql` — 12 dims + 8 facts in schema **`warehouse`**,
  with UNKNOWN (key=0) rows and sequence guards. sqlglot-validated.

---

## 4. What currently lives in the database

| Schema | Exists? | Contents | This phase |
|---|---|---|---|
| `business` | ✅ | 2025–2026 sales + forecasting facts (live) | **read-only / untouched** |
| `app` | ✅ | application tables | untouched |
| `staging` | ❌ | — | **created in DW-01** |
| `etl` | ❌ | run log, rejects, stats | **created in DW-01** |
| `warehouse` | ❌ | dim_* / fact_* | **created in DW-01, loaded DW-05…07** |
| `mart` | ❌ | mart_* views (BI serving contract) | **created in DW-08** |

**DB topology (from `application.yml`):**
- App / write datasource → `jdbc:postgresql://localhost:5433/lpn_ai_bi`
- BI read-only datasource → `jdbc:postgresql://localhost:5432/lpn_ai_bi`,
  `search_path=mart`, user `lpn_ai_readonly`.
- ⇒ The **`mart` schema is the contract** the BI API already expects. The finished
  marts must live there and be readable by `lpn_ai_readonly`.

---

## 5. The GAP this phase closes

1. **2024 history is absent from the DB.** Every "this year vs last year", full-history
   trend, and 2024 CA number is currently impossible.
2. **No `staging`/`warehouse`/`mart` schemas exist** — only design files.
3. **The existing ETL is unusable for 2024 as-is:**
   - points at the superseded 4-month `vente_clean_import`;
   - `pd.read_excel(dtype=object)` **full-loads** files into memory → cannot handle
     `2024_02` (254 MB), `2024_06` (132 MB), and would die on the multi-GB files;
   - writes **CSV files**, not PostgreSQL tables.
4. **Dedup across the 2024/2025–2026 overlap** has never been executed.
5. **`mart` schema naming** must be unified (`stg`→`staging`) and the BI read-only role
   granted SELECT on `mart`.

---

## 6. Target architecture (what "done" looks like)

```
Youssef_Extractions/data/Exported_LPN/2024_*.xlsx   (2024 window)
Youssef_Extractions/vente_2025_2026_import/csv/*.csv (2025-2026 window)
        │  chunked / streamed loader  (openpyxl read_only + psycopg2 COPY)
        ▼
staging.stg_*        ← both windows, composite PK (natural_key, _source_tag)
        │  Layer-3 cleaning + dedup + FK resolution
        ▼
warehouse.dim_*  /  warehouse.fact_*   ← UNKNOWN=0, SCD1, surrogate keys
        │  aggregate views
        ▼
mart.mart_*          ← the 5-page serving contract (read by lpn_ai_readonly @5432)
        │
        ▼
BI API (Spring) → 5 frontend pages → per-chart cases (next phase)
```

**Schema names (locked):** `staging` (tables `stg_*`), `etl`, `warehouse`
(`dim_*`/`fact_*`), `mart` (`mart_*`). All in database `lpn_ai_bi`.

---

## 7. Locked decisions & HARD RULES (carry forward — never break)

1. **Never** connect to LPN's Oracle/Compiere DB. Flat files only.
2. **Never** delete/overwrite/move any source data under `Youssef_Extractions/`.
3. **Never** modify the `business` schema (it serves the live dashboard + forecasting).
4. **Never** load a multi-GB / >100 MB file fully into memory — **stream/chunk** (≤5,000-row
   batches via `openpyxl read_only=True`). Use the CSVs for M_PRODUCT / M_PRODUCT_PO.
5. **Idempotent** loads only: staging `TRUNCATE`-before-reload per `_source_tag`; dims
   `UPSERT`; facts `TRUNCATE`+reload. No randomness; re-running yields identical output.
6. **Dedup rule:** 2024 dates ← `2024_xlsx`; 2025+ dates ← `csv_2025`. Composite PK in staging.
7. **CA trap:** exclude `57_…PORTFOLIO_24M.xlsx`. Use `2024_35` for 2024 portfolio.
8. **UNKNOWN surrogate = key 0** in every dimension; `NULLIF(col,0)` on FK IDs
   (except `C_UOM_ID`, `AD_CLIENT_ID`).
9. **Reconciliation gate:** no BI KPI is repointed from `business` to `mart` until the
   mart value **matches the business value** for the 2025–2026 window (e.g. order_count).
10. This is **Windows / PowerShell**. Postgres write port **5433**, read port **5432**.

---

## 8. The plan ahead (phases → tasks)

Detailed copy-paste prompts are in [`01_IMPLEMENTATION_TASKS.md`](01_IMPLEMENTATION_TASKS.md).

| Phase | Tasks | Goal |
|---|---|---|
| **A — Foundation** | DW-01 | Create `staging`/`etl`/`warehouse` schemas; run DDL; unify naming; grants |
| **B — Staging load** | DW-02, DW-03, DW-04 | Chunked loader; load 2024 small+medium; load 2024 giants; load 2025–2026 CSVs |
| **C — Warehouse build** | DW-05, DW-06, DW-07 | Build dims; build facts (orders/invoices); build facts (delivery/payment/stock) |
| **D — Mart build** | DW-08 | Create `mart.mart_*` views for the 5 pages (+ stock) |
| **E — Validate & cut over** | DW-09, DW-10 | Reconciliation/DQ gate; grant read-only; document cutover |

**Model guidance:** most tasks are **Sonnet 4.6**. The architecture-critical ones —
the chunked loader (DW-02), the giant-file load (DW-03), and the fact dedup/FK
resolution (DW-06) — are flagged **Opus 4.8** because correctness there is load-bearing
for every downstream number.

---

## 9. After this phase: per-chart implementation

Once `mart.*` holds 2024 + 2025–2026 and passes the reconciliation gate, you build the
visualisations **one case at a time**, each reading a finished mart column:

- Commercial — chiffre d'affaires par commercial (mart_commercial)
- CA par article / catégorie / type / fournisseur (mart_articles)
- CA facturé vs commandé, couverture (mart_revenue)
- Clients — top clients, portefeuille, géographie (mart_clients)
- Commandes — volume, statut, type (mart_commandes)
- (Stock — ruptures, disponibilité — mart_stock)

Each chart becomes a small, self-contained task: pick the mart column(s), wire the BI
endpoint/DTO, render the Recharts widget, validate the number against the warehouse.

---

## 10. Continuity

Every DW-task **appends a milestone** to
[`../context_bi_service.md`](../context_bi_service.md) (append-only) — same discipline as
BI-00…BI-07. Do not start a DW-task until the previous one's milestone exists.

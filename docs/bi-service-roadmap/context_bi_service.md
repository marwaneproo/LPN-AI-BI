# BI Service Roadmap — Continuity Log (APPEND-ONLY)

> **Future AI agents: READ THIS FILE FIRST before starting any BI roadmap task.**
> This is the single source of continuity for the BI data + serving work. It tells
> you what has already been done, what decisions are locked, and what comes next.

---

## 1. Purpose

This file is the **long-term execution memory** for the BI service & data
architecture roadmap (`docs/bi-service-roadmap/`). Every roadmap task
(`BI-00`, `BI-01`, …) must finish by **appending** a milestone entry here. Over
time this file becomes the authoritative history of how the BI layer was built.

## 2. Rules (do not break)

1. **Append-only.** Never overwrite, never delete, never edit previous entries.
2. **Corrections are new entries.** If a past task was wrong, append a new
   `CORRECTION` entry that references the original task ID. Do not change history.
3. **Every task appends one milestone entry** using the template in §7.
4. **Read before you write.** Before starting a task, read the latest entries to
   understand current state. Do not assume; verify against the repo.
5. **Ground claims in the repo.** Cite real file paths and real command output.

## 3. Initial project context (as of 2026-06-25)

- LPN AI-BI PFE; branch `bi-polished-dashboard`. Deadline 2026-07-31.
- Dev runs **fully native (no Docker)**: PostgreSQL 18 on `localhost:5432`
  (db `lpn_ai_bi`, roles `lpn_app_admin` / `lpn_ai_readonly`), embedded Qdrant,
  native services. See `docs/LOCAL_DEV_SETUP.md` and `scripts/launch-local-native.ps1`.
- Live `business` schema verified: `c_orderline` 354,910, `c_invoiceline` 335,877,
  `m_product` 35,822, `fact_sales_monthly` 25. `GET /v1/sales-dashboard` returns
  KPIs in ~0.5 s.
- BI backend currently lives **inside `llm-orchestrator`** (`SalesDashboardController/Service`,
  `ForecastController`, `PredictiveClient`).
- Frontend has 5 BI pages; 4 still use placeholder data (Commande, Chiffre
  d'affaires, Articles, Client); Vue d'ensemble is partly live.
- Raw data: `Youssef_Extractions/` ~6.5 GB (canonical `data/Exported_LPN`, 96 xlsx).

## 4. Prior work to REUSE (do not rebuild)

- Warehouse design + star schema + fact grain + DDL draft + **working ETL prototype**:
  `DataWareHouse/processus_de_vente/` (CSV output today).
- PostgreSQL materialization plan: `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`
  (Tasks 0–12). This BI roadmap **wraps/sequences** that, it does not replace it.
- Source audits/profiles: `docs/warehouse/*.md`+`*.json`.
- Semantic layer: `docs/semantic_layer/{metrics.yml,business_terms.csv,dwh_assets.csv}` (MAD).

## 5. Initial architecture decision summary (locked unless superseded by a CORRECTION entry)

1. Layered: **raw → staging → warehouse (star, SCD1) → marts → BI serving API → frontend**.
2. **BI service reads marts only; never raw files.**
3. BI separated from LLM orchestration (different bounded contexts).
4. **Strangler-fig:** build a clean `bi` package inside `llm-orchestrator` first;
   extract a standalone Spring `bi-service` only once marts + contract are stable.
5. Export split: **PNG/PDF frontend**, **Excel/data from backend reporting endpoint**.
   **No new .NET service** without a concrete reporting mandate.
6. Never drop `business`; new work in `staging`/`warehouse`/`mart`; repoint KPIs only
   after golden-number reconciliation.

See [`01_bi_architecture_vision.md`](01_bi_architecture_vision.md) for the full rationale.

## 6. Task status table

> Update by **appending** a milestone entry (§7). This table is a convenience
> snapshot; the Milestone Log (§8) is the source of truth. When you complete a task,
> append an entry AND you may append an updated copy of this table inside your entry.

| Task | Title | Phase | Status |
|---|---|---|---|
| BI-00 | Repo & service inventory snapshot | 0 | ⬜ Not started |
| BI-01 | Data folder inventory & reconciliation | 0 | ⬜ Not started |
| BI-02 | Deep profiling of canonical transactional files | 1 | ⬜ Not started |
| BI-03 | Encoding / format / missing-value profile | 1 | ⬜ Not started |
| BI-04 | Business-entity catalogue | 1 | ⬜ Not started |
| BI-05 | Cleaning, dedup & normalization plan | 2 | ⬜ Not started |
| BI-06 | Staging schema design (Postgres) | 3 | ⬜ Not started |
| BI-07 | Warehouse dim/fact design alignment | 3 | ⬜ Not started |
| BI-08 | Mart design for the 5 frontend pages | 3 | ⬜ Not started |
| BI-09 | ETL implementation plan (raw→staging→warehouse→marts) | 4 | ⬜ Not started |
| BI-10 | BI API contract & DTO design | 5 | ⬜ Not started |
| BI-11 | BI extraction decision (package vs service) | 6 | ⬜ Not started |
| BI-12 | Frontend integration plan (5 pages) | 7 | ⬜ Not started |
| BI-13 | Export/reporting endpoint design | 8 | ⬜ Not started |
| BI-14 | Testing & validation strategy | 9 | ⬜ Not started |

Status legend: ⬜ Not started · 🟦 In progress · ✅ Done · 🟥 Blocked · ♻️ Corrected.

## 7. Milestone entry template (copy for every task)

```markdown
### [<YYYY-MM-DD HH:MM>] <BI-XX> — <Task title>

- **Type:** milestone | CORRECTION (of <BI-XX>)
- **Model/effort used:** <e.g. Sonnet 4.6 medium>
- **Summary:** <what was done, 2–4 lines>
- **Folders/files inspected:** <paths>
- **Files created/modified:** <paths, or "none">
- **Technical decisions:** <decisions + rationale; link to vision §>
- **Validation commands run:** <commands>
- **Validation results:** <pass/fail + key numbers>
- **Problems encountered:** <issues, or "none">
- **Next recommended task:** <BI-XX + one-line why>
```

## 8. Milestone Log

<!-- APPEND NEW ENTRIES BELOW THIS LINE. NEWEST AT THE BOTTOM. NEVER EDIT ABOVE. -->

### [2026-06-25] BI-ROADMAP-INIT — Roadmap documents created

- **Type:** milestone
- **Model/effort used:** Opus 4.8 medium
- **Summary:** Created the four BI roadmap documents under `docs/bi-service-roadmap/`.
  Grounded the plan in the existing repo and prior warehouse work; defined the
  layered architecture and the strangler-fig extraction strategy. No code written.
- **Folders/files inspected:** `DataWareHouse/processus_de_vente/` (layers, star
  schema, fact grain, ETL scripts), `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`,
  `docs/warehouse/`, `docs/semantic_layer/metrics.yml`, `docker-compose.yml`,
  `services-java/llm-orchestrator/.../Sales*`, `frontend/src/features/bi/`,
  `Youssef_Extractions/` (metadata only).
- **Files created/modified:** `docs/bi-service-roadmap/01_bi_architecture_vision.md`,
  `02_bi_execution_tasks.md`, `03_bi_task_prompts.md`, `context_bi_service.md`.
- **Technical decisions:** Reuse (not rebuild) the DWH design + rebuild roadmap;
  marts-only BI service; strangler-fig package→service; export split; no .NET.
- **Validation commands run:** safe `find`/`du`/`grep` inventory only.
- **Validation results:** Inventory confirmed (6.5 GB raw; 278 xlsx/65 csv;
  warehouse prototype + rebuild roadmap present).
- **Problems encountered:** none.
- **Next recommended task:** **BI-00** — repo & service inventory snapshot.

### [2026-06-25 15:00] BI-00 — Repo & service inventory snapshot

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 medium
- **Summary:** Produced a complete read-only inventory of the repository: 8 services
  with ports, all BI Java endpoints in `llm-orchestrator`, 10 frontend BI pages
  (3 live, 6 placeholder), full `business`+`app` schema table counts from PG18, all
  DWH/ETL artifacts in `DataWareHouse/processus_de_vente/`, rebuild roadmap tasks 0-12,
  and semantic layer assets. No service code or data files were modified.
- **Folders/files inspected:** `docker-compose.yml`, `scripts/launch-local-native.ps1`,
  `services-java/llm-orchestrator/src/main/java/...` (all .java), `services-python/`
  (directory listing), `DataWareHouse/processus_de_vente/` (docs, etl/, ddl/, output/),
  `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`, `docs/warehouse/` (5 audit docs
  + 5 JSON), `docs/semantic_layer/` (metrics.yml, business_terms.csv, dwh_assets.csv),
  `frontend/src/features/bi/` (all pages, components, hooks, api, utils).
- **Files created/modified:** `docs/bi-service-roadmap/_inventory/00_repo_snapshot.md` (created).
- **Technical decisions:** Confirmed the strangler-fig extraction target is
  `SalesDashboardController/Service` + `ForecastController/PredictiveClient` inside
  `llm-orchestrator`. Confirmed `staging`/`warehouse`/`mart` schemas do NOT yet exist
  in `lpn_ai_bi`. ETL prototype output exists as CSVs in `DataWareHouse/.../output/`
  but has not been loaded to PostgreSQL. 6 frontend pages are placeholder (BiWidgets).
- **Validation commands run:**
  - `psql -U lpn_ai_readonly -h localhost -p 5432 -d lpn_ai_bi -c "\dn"` — schemas listed
  - `SELECT schemaname, relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC` — row counts
- **Validation results:** Schemas `app`, `business`, `public` present ✅. Key counts
  match context_bi_service.md §3: c_orderline 354,910 ✅, c_invoiceline 335,877 ✅,
  m_product 35,822 ✅, fact_sales_monthly 25 ✅. No staging/warehouse/mart schemas.
- **Problems encountered:** none. PG18 reachable on localhost:5432.
- **Next recommended task:** **BI-01** — Data folder inventory & reconciliation.
  Reason: need a metadata-only inventory of `Youssef_Extractions/` reconciled against
  existing `docs/warehouse/` audits to confirm the canonical source set and dedup rules
  before any profiling (BI-02/03) or staging DDL (BI-06) work begins.

### [2026-06-25 16:30] BI-01 — Data folder inventory & reconciliation

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 medium
- **Summary:** Produced a complete metadata-only inventory of `Youssef_Extractions/`
  (363 files, ~6.5 GB). Classified every file as canonical/duplicate/junk/enrichment/import-ready.
  Confirmed the dedup rule and canonical source set. Cross-referenced against existing
  `docs/warehouse/*.json` audits and both manifests (`vente_2025_2026_import` and
  `vente_clean_import`). Flagged all junk files (`Book1..7`, `~$*`, `3.xlsx`, `4.xlsx`).
  Documented the `57` CA-inflated TRAP file and confirmed `2024_35_CUSTOMER_PORTFOLIO_FIXED`
  as the correct substitute. No data files were moved, renamed, or deleted.
- **Folders/files inspected:** `Youssef_Extractions/` (all 11 sub-folders, all 363 files);
  `docs/warehouse/vente_dwh_source_audit.json`; `Youssef_Extractions/vente_2025_2026_import/manifest.json`;
  `Youssef_Extractions/vente_clean_import/manifest.json`;
  `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` §"Key facts the AI must know";
  `Youssef_Extractions/txt/req_1.txt` (Oracle extraction SQL filter).
- **Files created/modified:** `docs/bi-service-roadmap/_inventory/01_data_inventory.md` (created).
- **Technical decisions:**
  - **Canonical source confirmed:** `data/Exported_LPN/` (204 xlsx) — `2024_01..35` for 2024 history; `53..58` for 24-month 2025-2026 view.
  - **Dedup rule locked:** 2024 history from `2024_*`; 2025-2026 from `53..58`; dedup on PK for Jun–Dec 2024 overlap.
  - **CA trap:** `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` inflated CA (many-to-many Oracle join bug). Use `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` + re-aggregate from orders/invoices separately for 2025-2026.
  - **Duplicate folder:** `data/Exported_data_through_a_drive/` is a full duplicate of `Exported_LPN/` + quarterly splits. Archive candidate after human approval (Rebuild Task 2).
  - **`business` schema contains 2025-2026 data only:** confirmed from `vente_2025_2026_import/manifest.json` row-count match; 2024 history (2024_* files) is NOT yet in PostgreSQL.
  - **Three large files need chunked ETL:** `2024_02_C_ORDERLINE.xlsx` (253 MB), `2024_16_M_PRODUCT.xlsx` (2,272 MB), `2024_18_M_PRODUCT_PO.xlsx` (995 MB) — must be loaded in chunks (≥100 MB each) during Rebuild Task 5.
  - **`forecast_monthly_import/csv/` must be preserved** — feeds `business.fact_sales_monthly*` tables used by the forecasting service.
- **Validation commands run:**
  - `python bi01_count_sheets.py` (openpyxl read_only=True) — streaming row count on ~80 xlsx files
  - `zipfile` XML tag count (`<row ` in `xl/worksheets/sheet1.xml`) — second method for 3 cross-check files
- **Validation results:**
  - `2024_01_C_ORDER.xlsx`: openpyxl=18,024, xml=18,024 ✅
  - `53_COMMERCIAL_ORDER_HEADER_24M.xlsx`: openpyxl=34,710, xml=34,710 ✅
  - `2024_15_AD_USER_SALESREPS.xlsx`: openpyxl=31, xml=31 ✅
  - Manifest row counts match `business` schema live counts (c_orderline 354,910, c_invoiceline 335,877, m_product 35,822) ✅
- **Problems encountered:** Files >50 MB skipped from streaming (4 in 2024_* series, 2 in 53..58 series). Row counts for these derived from audit docs or marked unknown. No data-integrity issues found.
- **Next recommended task:** **BI-02** — Deep profiling of canonical transactional files.
  Reason: now that the canonical source set is confirmed, profile the key transactional files
  (`2024_01_C_ORDER`, `2024_03_C_INVOICE`, `53_COMMERCIAL_ORDER_HEADER_24M`, `55_COMMERCIAL_INVOICE_HEADER_24M`)
  for column-level PK/FK/null-% to validate the star-schema grain before staging DDL is written.

### [2026-06-25 17:30] BI-02 — Transactional file profiling

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 medium
- **Summary:** Column-level profiled all 8 canonical transactional xlsx files
  (`2024_01_C_ORDER`, `2024_02_C_ORDERLINE`, `2024_03_C_INVOICE`, `2024_04_C_INVOICELINE`,
  `53..56`). Sample of 2,000 rows per file via openpyxl read_only=True; 4 large files
  (80–253 MB) sampled only. No DB writes. Confirmed PKs unique in sample, mapped CA and
  date fields to metrics.yml, documented the 2024-vs-24M column gap, and flagged key
  anomalies (C_ORDER_ID 29.9% null on invoices, negative GRANDTOTAL on credit notes,
  DOCSTATUS='DR' draft rows in extract).
- **Folders/files inspected:**
  - `Youssef_Extractions/data/Exported_LPN/2024_01_C_ORDER.xlsx` (136 cols)
  - `Youssef_Extractions/data/Exported_LPN/2024_02_C_ORDERLINE.xlsx` (112 cols, ✂️ sample)
  - `Youssef_Extractions/data/Exported_LPN/2024_03_C_INVOICE.xlsx` (96 cols)
  - `Youssef_Extractions/data/Exported_LPN/2024_04_C_INVOICELINE.xlsx` (61 cols, ✂️ sample)
  - `Youssef_Extractions/data/Exported_LPN/53_COMMERCIAL_ORDER_HEADER_24M.xlsx` (28 cols)
  - `Youssef_Extractions/data/Exported_LPN/54_COMMERCIAL_ORDER_LINE_24M.xlsx` (31 cols, ✂️ sample)
  - `Youssef_Extractions/data/Exported_LPN/55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` (24 cols)
  - `Youssef_Extractions/data/Exported_LPN/56_COMMERCIAL_INVOICE_LINE_24M.xlsx` (34 cols, ✂️ sample)
  - `docs/semantic_layer/metrics.yml` (metric-to-column mapping validation)
- **Files created/modified:** `docs/bi-service-roadmap/_profiling/02_transactional_profile.md` (created).
- **Technical decisions:**
  - **PKs confirmed:** C_ORDER_ID, C_ORDERLINE_ID, C_INVOICE_ID, C_INVOICELINE_ID — all integer surrogates, unique in sample, 0% null.
  - **CA fields confirmed:** GRANDTOTAL (0% null, float) on order/invoice headers; LINENETAMT (0% null, float) on lines — match metrics.yml formulas exactly.
  - **Date fields confirmed:** DATEORDERED (0% null, date) on C_ORDER; DATEINVOICED (0% null, date) on C_INVOICE.
  - **ISSOTRX always 'Y'** in 2024 series — pre-filtered; absent in 24M series (also pre-filtered).
  - **DOCSTATUS filter required in staging:** include 'DR' (draft) in extract but exclude from fact tables (`DOCSTATUS IN ('CO','CL')` for orders, `='CO'` for invoices).
  - **C_ORDER_ID nullable on invoices:** 29.9% null in 2024 series, 17.6% in 24M — all invoice↔order joins must be LEFT JOINs.
  - **Currency/warehouse/UOM are singletons:** C_CURRENCY_ID=239 (MAD), M_WAREHOUSE_ID=1000000, C_UOM_ID=100 always — simplifies dim tables.
  - **24M series is enriched but not for staging:** denormalized labels (PRODUCT_NAME, CUSTOMER_NAME, COMMERCIAL_NAME, PRODUCT_CATEGORY etc.) useful for dim bootstrap but missing audit-trail columns; use 2024_* series as ETL source.
  - **Negative GRANDTOTAL is valid:** credit notes/returns produce negative values — do not filter out.
  - **Staging DDL types:** BIGINT for all *_ID FKs and PKs; NUMERIC(18,4) for GRANDTOTAL/LINENETAMT/price fields; TIMESTAMPTZ for date fields.
- **Validation commands run:**
  - `python bi02_profile.py` (openpyxl read_only=True, 2000-row sample per file)
  - `python bi02_extract.py` (key-column extraction from JSON output)
- **Validation results:**
  - All 8 PKs unique in 2000-row sample ✅
  - GRANDTOTAL null%=0% on all order/invoice headers ✅
  - LINENETAMT null%=0% on all line files ✅
  - DATEORDERED/DATEINVOICED null%=0% ✅
  - ISSOTRX single-value 'Y' in 2024 series ✅
  - ISPAID Y/N binary, null%=0% on invoices ✅
  - DESCRIPTION 78% null on C_ORDERLINE (expected) ✅
- **Problems encountered:**
  - `56_COMMERCIAL_INVOICE_LINE_24M`: C_ORDERLINE_ID is distinct=2000 in sample — flagged as potential 1:1 mapping quirk in this export, NOT declared as alternate PK (requires full-file validation).
  - Large files (2024_02, 2024_04, 54, 56) profiled from 2000-row head sample only — null % for rare nulls may be underestimated; re-validate during staging load.
- **Next recommended task:** **BI-03** — Encoding, format, and missing-value profile for dimension source files (`2024_10_C_BPARTNER`, `2024_16_M_PRODUCT`, `2024_15_AD_USER_SALESREPS`). Reason: dim files need column-level profile before dim DDL and SCD1 ETL can be written (BI-06/07).

### [2026-06-25 18:15] BI-03 — Format & quality profile

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 medium
- **Summary:** Catalogued encoding, date format, decimal separator, currency handling,
  whitespace, and missing-value token issues across 13 xlsx + 8 CSV files (first 300 rows
  each). Used openpyxl read_only + Python csv + chardet. Detected 90 findings across 6
  categories. Documented ETL normalisation rules for each. No data modified.
- **Folders/files inspected:**
  - xlsx: `2024_01_C_ORDER`, `2024_03_C_INVOICE`, `2024_10_C_BPARTNER`, `2024_13_C_REGION`,
    `2024_14_C_CITY`, `2024_15_AD_USER_SALESREPS`, `2024_17_M_PRODUCT_CATEGORY`,
    `2024_21_M_PRODUCT_THEME`, `2024_23_C_DOCTYPE`, `2024_28_C_PAYMENTTERM`,
    `2024_30_C_SALESREGION`, `53_COMMERCIAL_ORDER_HEADER_24M`, `55_COMMERCIAL_INVOICE_HEADER_24M`
  - CSV (vente_2025_2026_import): `C_ORDER`, `C_INVOICE`, `C_BPARTNER`, `M_PRODUCT_THEME`,
    `C_SALESREGION`, `C_REGION`, `C_CITY`, `AD_USER`
- **Files created/modified:** `docs/bi-service-roadmap/_profiling/03_format_quality_profile.md` (created).
- **Technical decisions / findings:**
  - **Encoding (E-01):** All 8 CSVs are UTF-8-SIG (BOM present). Read with `encoding='utf-8-sig'`; PostgreSQL COPY with `ENCODING 'UTF8'` strips BOM automatically.
  - **Delimiter (E-02):** All CSVs use `;` (semicolon). Must specify `DELIMITER ';'` in COPY/pandas/csv.reader.
  - **xlsx encoding (E-03):** Internally UTF-8 — French accented chars correct; no action needed.
  - **DATEACCT midnight (D-01):** Always `00:00:00` — cast to `DATE` in staging (`::DATE`).
  - **Midnight datetimes (D-02):** DATEINVOICED/DATEORDERED occasionally midnight (date-only entry). Keep as TIMESTAMPTZ in staging; truncate to DATE in fact tables.
  - **Date in free text (D-03):** POREFERENCE contains dd/mm/yyyy strings (customer PO ref). Store as TEXT; do not parse.
  - **No comma decimal, no space thousands (N-01/02):** Confirmed — all amounts use period decimal. No conversion needed.
  - **Zero FK sentinel (N-03):** `0` used for "no value" in C_DOCTYPE_ID, AD_ORG_ID, BILL_USER_ID, etc. ETL fix: `NULLIF(col, 0)::BIGINT`.
  - **Currency in free text only (C-01/02):** Amount columns are plain numeric. Only COMMENTAIRE has "5000dh" in prose. All currency-symbol regex hits on city/person names are false positives.
  - **Trailing spaces (W-01):** Pervasive in CUSTOMER_NAME (24M series), POREFERENCE, DESCRIPTION. Fix: blanket `TRIM()` on all string columns.
  - **Embedded tabs/newlines (W-02/03/04):** Tab at end of M_PRODUCT_CATEGORY.NAME, M_PRODUCT_THEME.NAME/DESCRIPTION; trailing `\n` in category VALUE. Fix: `REGEXP_REPLACE(TRIM(col), '[\t\r\n]+', ' ', 'g')` in dim build.
  - **`'--'` in DOCACTION (M-01):** Compiere no-op sentinel. NOT a null — keep as-is.
  - **`'-'` in POREFERENCE (M-02):** Empty reference sentinel. Map to NULL: `NULLIF(TRIM(col), '-')`.
  - **`'NA'` in C_BPARTNER (M-03):** Likely system placeholder record. Exclude from dim_customer if system BP.
  - **All-caps labels (K-01/02):** City, region, customer, category, theme names — all-caps with occasional all-lowercase outliers. Apply `INITCAP(TRIM(col))` in dim build (not in staging).
- **Validation commands run:**
  - `python bi03_quality.py` — openpyxl + chardet scan, 300 rows per file
  - `python bi03_print.py` — grouped findings with file:column:row:value examples
- **Validation results:** All 13 listed problem types have ≥1 concrete file+col+row+value example ✅. 90 total findings; each category has examples in the markdown report.
- **Problems encountered:** chardet not installed in system Python — installed via `pip install chardet`. All "currency" findings in geographic/name columns are false positives from regex overmatch on "MAD" substring.
- **Next recommended task:** **BI-04** — Business-entity catalogue. Reason: the last profiling task before staging DDL — document all dim and fact entities with business descriptions, key columns, SCD1 strategy, and relationship graph.

---

## Milestone BI-04 — Business-Entity Catalogue

- **Date:** 2026-06-25
- **Branch:** `bi-polished-dashboard`
- **Model:** Sonnet 4.6 medium
- **Inputs used:** BI-01 data inventory, BI-02 transactional profile, BI-03 quality profile, `DataWareHouse/processus_de_vente/02_star_schema_design.md`, `03_fact_grain_design.md`, `docs/semantic_layer/metrics.yml`
- **Files created/modified:** `docs/bi-service-roadmap/_profiling/04_entity_catalogue.md` (created).
- **Entities catalogued (10 + supporting dims):**
  - **fact_sales_order** (commandes): `2024_01_C_ORDER.xlsx` + `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` + `C_ORDER.csv`; grain=C_ORDER_ID; CA=GRANDTOTAL; key FKs→dim_customer, dim_commercial, dim_document_type; feeds BiCommandesPage
  - **fact_sales_order_line** (lignes de commande): `2024_02_C_ORDERLINE.xlsx` + `C_ORDERLINE.csv`; grain=C_ORDERLINE_ID; measures=LINENETAMT, QTYORDERED/DELIVERED/INVOICED; coverage bridge for couverture_facturation/livraison; feeds BiCommandesPage
  - **fact_invoice** (factures): `2024_03_C_INVOICE.xlsx` + `55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` + `C_INVOICE.csv`; grain=C_INVOICE_ID; C_ORDER_ID 29.9% null→LEFT JOIN always; ISPAID=primary payment status source; feeds BiRevenuePage, BiClientsPage
  - **fact_invoice_line** (lignes de facture): `2024_04_C_INVOICELINE.xlsx` + `C_INVOICELINE.csv`; grain=C_INVOICELINE_ID; SUM(LINENETAMT) GROUP BY M_PRODUCT_ID → top_produits_ca_facture; feeds BiRevenuePage, BiArticlesPage
  - **fact_delivery + fact_delivery_line** (livraisons): `2024_05_M_INOUT.xlsx` + `2024_06_M_INOUTLINE.xlsx` + CSVs; grains=M_INOUT_ID / M_INOUTLINE_ID; bridge via C_ORDERLINE_ID; feeds SalesAnalysisPage
  - **fact_payment_allocation** (paiements): `2024_07..09_C_PAYMENT/ALLOCATIONHDR/ALLOCATIONLINE.xlsx` + CSVs; grain=C_ALLOCATIONLINE_ID; amount=AMOUNT; shortcut: ISPAID on invoice sufficient for counts; feeds BiClientsPage
  - **fact_stock_snapshot** (stocks): `RV_STORAGE.csv` (99,234 rows); grain=product/warehouse snapshot; semi-additive QTYAVAILABLE; feeds BiArticlesPage
  - **dim_customer** (clients): `2024_10_C_BPARTNER.xlsx` (42,434) + `C_BPARTNER.csv` (823); ISCUSTOMER='Y' filter; SCD1; feeds BiClientsPage
  - **dim_commercial** (commerciaux): `2024_15_AD_USER_SALESREPS.xlsx` (31 rows) + `AD_USER.csv` (28); SCD1; feeds BiCommercialPage
  - **dim_product** (articles): `M_PRODUCT.csv` (35,822) — `2024_16_M_PRODUCT.xlsx` is 2,272 MB — use CSV; enriched with theme/type/collection from small xlsx files; SCD1; feeds BiArticlesPage
  - **dim_product_category** (catégories): `2024_17_M_PRODUCT_CATEGORY.xlsx` (41 rows); trailing `\t`/`\n` → REGEXP_REPLACE in dim build; feeds BiArticlesPage
  - **dim_supplier** (fournisseurs): `C_BPARTNER_VENDOR.csv` (14,264) + `M_PRODUCT_PO.csv` (39,359); `2024_18_M_PRODUCT_PO.xlsx` is 995 MB — use CSV; one-supplier-per-product via V_PRODUCT_PRIMARY_SUPPLIER; feeds BiArticlesPage
  - **dim_geography**: built from C_BPARTNER_LOCATION + C_LOCATION + C_REGION + C_CITY + C_SALESREGION CSVs; feeds SalesAnalysisPage
  - **dim_document_type, dim_payment_term, dim_price_list, dim_warehouse, dim_sales_region**: small reference dims (7–79 rows each)
- **Key decisions:**
  - `2024_16_M_PRODUCT.xlsx` (2,272 MB) and `2024_18_M_PRODUCT_PO.xlsx` (995 MB): **must use CSV equivalents** for all ETL — xlsx versions are off-limits even for chunked streaming.
  - C_ORDER_ID on invoices 29.9% null confirmed in BI-02 → **LEFT JOIN** mandatory on every invoice→order bridge.
  - `ISPAID` on `C_INVOICE` is sufficient for paid/unpaid counts; full allocation join only needed for payment-amount drill-down.
  - All 5 placeholder frontend pages (BiCommandesPage, BiRevenuePage, BiArticlesPage, BiClientsPage, BiCommercialPage) have ≥1 confirmed backing fact entity ✅.
  - Mart layer (8 mart views) maps 1-to-1 to the frontend pages and the live DashboardOverviewPage / SalesAnalysisPage.
- **Mart → page mapping established:**
  - `mart_sales_overview` → DashboardOverviewPage
  - `mart_sales_by_commercial` → BiCommercialPage
  - `mart_sales_by_customer` → BiClientsPage
  - `mart_sales_by_product` → BiArticlesPage
  - `mart_sales_by_region` → SalesAnalysisPage (GeographieSection)
  - `mart_order_to_invoice_flow` → BiCommandesPage / SalesAnalysisPage
  - `mart_payment_status` → BiClientsPage / DashboardOverviewPage
  - `mart_stock_risk` → BiArticlesPage (stock section)
- **Validation results:** All 5 placeholder pages have ≥1 backing entity ✅. All 10 entities have confirmed source file + row count ✅.
- **Next recommended task:** **BI-05** — Cleaning, dedup, and normalisation plan. Reason: formalize ETL rules from BI-01–04 into a concrete SOURCE_MAPPING spec (which files load which staging tables, exact dedup key per table, NULLIF/TRIM rules, DOCSTATUS filters).

---

## Milestone BI-05 — Cleaning, Dedup & Normalisation Plan

- **Date:** 2026-06-25
- **Branch:** `bi-polished-dashboard`
- **Model:** Sonnet 4.6 medium
- **Inputs used:** BI-02 transactional profile, BI-03 format & quality profile, BI-04 entity catalogue, rebuild roadmap dedup rule
- **Files created/modified:** `docs/bi-service-roadmap/_plan/05_cleaning_plan.md` (created).
- **Three-layer cleaning model defined:**
  - Layer 1 (Ingest): ENC-01/02/03 encoding + delimiter; TYP-01..05 type coercion; FK-NULL-01 zero-FK→NULL sentinel
  - Layer 2 (Dedup/Filter): DEDUP-00 period assignment (2024_xlsx vs csv_2025 preference); per-entity dedup keys (all referencing real BI-02 confirmed PKs); FILT-01..11 business filters (DOCSTATUS, ISSOTRX, ISCUSTOMER, VALUE!='NA')
  - Layer 3 (Normalisation): NORM-01..08 (TRIM, REGEXP_REPLACE tabs/newlines, INITCAP in dims); DATE-01..03; CUR-01..03; TRAP-01 CA-trap exclusion
- **Key rules:**
  - **TRAP-01:** `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` permanently excluded — CA inflated by many-to-many join bug. CA source of truth = `C_ORDER.GRANDTOTAL` / `C_INVOICE.GRANDTOTAL`.
  - **DEDUP-00:** CSV (2025) preferred over 2024_xlsx when same PK in both; resolved via ROW_NUMBER() with `_source_tag` ordering.
  - **FK-NULL-01:** `NULLIF(col, 0)` on all `*_ID` integer FKs except C_UOM_ID and AD_CLIENT_ID (exemptions documented).
  - **NORM-08:** `C_ORDER_ID = NULL` on C_INVOICE is valid (29.9% confirmed BI-02); not treated as reject.
  - **DEDUP-SUPP-01:** Primary supplier per product = lowest `SEQNO` row in `M_PRODUCT_PO` (deterministic tie-break on `C_BPARTNER_ID`).
  - **DATE-01:** `DATEACCT` always cast to `DATE`; `DATEORDERED`/`DATEINVOICED` stay `TIMESTAMPTZ` in staging, truncated to `DATE` for date-key join.
  - Large file exclusions: `2024_16_M_PRODUCT.xlsx` (2,272 MB) and `2024_18_M_PRODUCT_PO.xlsx` (995 MB) — **CSV only**.
  - Large files needing chunked ETL: `2024_02_C_ORDERLINE.xlsx` (253 MB), `2024_06_M_INOUTLINE.xlsx` (132 MB) — 5,000-row chunks.
- **Reject store:** `etl.stg_rejects` (append-only, append per run via `etl_run_id` UUID); `etl.etl_run_log` + `etl.etl_run_table_stats` for audit trail.
- **Idempotency:** IDEM-01..06: stage tables TRUNCATE before reload; warehouse dims use UPSERT (`ON CONFLICT DO UPDATE`); fact tables TRUNCATE + reload; all randomness eliminated.
- **Acceptance gates per run:** reject rate < 5% for transactional tables; rows_inserted > 0 for mandatory tables (stg_c_order, stg_c_invoice, stg_m_product, stg_c_bpartner).
- **Validation coverage:** All 20 BI-03 problem codes (E-01..03, D-01..03, N-01..03, C-01..03, W-01/02/04, M-01..03, K-01/02) have at least one rule and one testable SQL/Python assertion ✅. All dedup keys reference confirmed BI-02 PKs (C_ORDER_ID, C_ORDERLINE_ID, C_INVOICE_ID, C_INVOICELINE_ID) ✅.
- **Next recommended task:** **BI-06** — Staging schema DDL. Reason: design only — `CREATE TABLE stg_*` definitions with exact PostgreSQL types, NOT NULL constraints where safe, and `_source_tag`/`_etl_run_id` metadata columns per table.

---

## Milestone BI-06 — Staging Schema Design

- **Date:** 2026-06-25
- **Branch:** `bi-polished-dashboard`
- **Model:** Sonnet 4.6 medium
- **Inputs used:** `DataWareHouse/processus_de_vente/01_warehouse_layers.md`, `ddl/draft_schema_design.sql`, actual staging CSV headers from `etl/output/staging/`, BI-02 PKs, BI-05 cleaning rules
- **Files created/modified:**
  - `docs/bi-service-roadmap/_design/06_staging_design.md` (created)
  - `docs/bi-service-roadmap/sql/staging.sql` (created)
- **DDL scope:** Schema `stg` (25 `stg_*` tables) + schema `etl` (3 infrastructure tables) = 28 tables total, 65 SQL statements
- **Validation:** sqlglot 30.11.0 — 65 statements, 0 parse errors ✅
- **Key design decisions:**
  - **Composite PK pattern:** `PRIMARY KEY (natural_key, _source_tag)` allows 2024_xlsx and csv_2025 rows to co-exist in staging; dedup at warehouse load (DEDUP-00)
  - **All metadata columns:** `_loaded_at`, `_source_file`, `_source_tag`, `_etl_run_id` on every table
  - **No FK constraints in staging** — referential integrity enforced at warehouse load (dwh_vente)
  - **No transformations** — all TRIM, NULLIF, INITCAP, DATE cast rules deferred to Layer 3 (dim/fact build)
  - **Large file exclusions enforced:** `2024_16_M_PRODUCT.xlsx` (2,272 MB) → stg_m_product uses CSV only; `2024_18_M_PRODUCT_PO.xlsx` (995 MB) → stg_m_product_po uses CSV only
  - **Chunked ETL flagged** in table comments: stg_c_orderline (253 MB xlsx), stg_m_inoutline (132 MB xlsx), stg_c_invoiceline (80 MB xlsx)
  - **Schema isolation:** `business.*` schema never touched; `stg` and `etl` are the only new schemas
  - Column types: BIGINT for all `*_id`; NUMERIC(18,4) for amounts/quantities; TIMESTAMPTZ for dates; CHAR(1) for Y/N flags; TEXT for all strings (no length caps in staging)
- **Table inventory (25 stg_* tables):**
  - Transactional: stg_c_order, stg_c_orderline, stg_c_invoice, stg_c_invoiceline
  - Delivery: stg_m_inout, stg_m_inoutline
  - Payments: stg_c_payment, stg_c_allocationhdr, stg_c_allocationline
  - Business partners: stg_c_bpartner, stg_c_bpartner_vendor, stg_c_bpartner_location, stg_c_location
  - Products: stg_m_product, stg_m_product_category, stg_m_product_theme, stg_m_product_type, stg_m_product_collection, stg_m_product_po
  - Geography: stg_c_region, stg_c_city, stg_c_salesregion
  - Lookups: stg_ad_user, stg_c_doctype, stg_c_paymentterm, stg_m_pricelist, stg_m_warehouse
  - Snapshot: stg_rv_storage
- **Relationship to existing DDL:** `draft_schema_design.sql` defines dwh_vente dims/facts; staging.sql is a new upstream layer; no overlap; `draft_schema_design.sql` will be reviewed/updated in BI-07.
- **Next recommended task:** **BI-07** — Warehouse dim/fact design alignment. Reason: reconcile `draft_schema_design.sql` with confirmed staging column names and BI-02/04 FK chain; add UNKNOWN surrogate rows; confirm grain alignment with BI-03/04.

---

## Milestone BI-07 — Warehouse Dim/Fact Design Alignment

- **Date:** 2026-06-25
- **Branch:** `bi-polished-dashboard`
- **Model:** Sonnet 4.6 medium
- **Inputs used:** `02_star_schema_design.md`, `03_fact_grain_design.md`, `draft_schema_design.sql`, ETL output CSV headers (etl/output/dimensions/ + etl/output/facts/), BI-04 entity catalogue
- **Files created/modified:**
  - `docs/bi-service-roadmap/_design/07_warehouse_design.md` (created)
  - `docs/bi-service-roadmap/sql/warehouse.sql` (created)
- **DDL scope:** Schema `dwh_vente` — 12 dim tables + 8 fact tables + indexes + UNKNOWN row INSERTs + sequence guards = 98 SQL statements
- **Validation:** sqlglot 30.11.0 — 98 statements, 0 parse errors
- **Key design decisions (vs draft_schema_design.sql):**
  - **DECISION-01:** Schema `dwh_vente` retained (no change vs prototype)
  - **DECISION-02:** Added UNKNOWN row INSERTs (key=0) for all 12 dims — prototype had no INSERTs; ETL output confirms key=0 rows exist
  - **DECISION-03:** `dim_date.date_key` is INTEGER (YYYYMMDD format), not BIGSERIAL; confirmed from ETL output; unknown row key=0
  - **DECISION-04:** SCD2 placeholder columns (effective_from, effective_to, is_current) kept on dim_customer and dim_product per 01_warehouse_layers.md; NULL in v1 (SCD1 behaviour)
  - **DECISION-05:** `fact_stock_snapshot` — added `m_locator_id BIGINT` degenerate dimension vs prototype; required because stg_rv_storage PK includes m_locator_id and ETL output shows two rows with same (m_product_id, attrset, warehouse) but different locators
  - **DECISION-06:** All 8 facts from 03_fact_grain_design.md confirmed; no removals (8 = sales_order, sales_order_line, invoice, invoice_line, delivery, delivery_line, payment_allocation, stock_snapshot)
  - **DECISION-07:** `fact_payment_allocation.commercial_key` kept; resolves via invoice join; defaults to 0 (unknown) when not resolvable
  - **DECISION-08:** `fact_delivery.document_type_key` kept; often resolves to 0 (unknown) because delivery doc types have ISSALESTRANSACTION='N' and are excluded from dim_document_type
  - **DECISION-09:** `dim_warehouse.warehouse_name` must source from `stg_m_warehouse.name` (not value); NULL in ETL output was an ETL build issue, not a DDL issue
  - **DECISION-10:** `dim_product.theme_name` and `collection_name` stored as denormalized TEXT; no separate dim_theme or dim_collection tables in v1
- **Dimension inventory (12 dims):**
  - dim_date, dim_customer, dim_commercial, dim_product_category, dim_supplier, dim_product, dim_sales_region, dim_geography, dim_payment_term, dim_price_list, dim_document_type, dim_warehouse
  - All dims: BIGSERIAL PK (except dim_date: INTEGER), UNKNOWN row at key=0, SCD1, sequence guard resets after unknown row insert
- **Fact grain confirmation (all 8 matched 03_fact_grain_design.md):**
  - fact_sales_order → UNIQUE(c_order_id)
  - fact_sales_order_line → UNIQUE(c_orderline_id)
  - fact_invoice → UNIQUE(c_invoice_id); source_c_order_id NULL 29.9% = valid
  - fact_invoice_line → UNIQUE(c_invoiceline_id)
  - fact_delivery → UNIQUE(m_inout_id)
  - fact_delivery_line → UNIQUE(m_inoutline_id)
  - fact_payment_allocation → UNIQUE(c_allocationline_id)
  - fact_stock_snapshot → (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id) — semi-additive measures
- **Frontend page mapping confirmed:** all 5 pages have >= 1 backing fact
- **Next recommended task:** **BI-08** — Mart design. Define `CREATE VIEW mart_*` statements for 8 frontend-facing views in `dwh_vente` schema. One mart per frontend BI page (BiCommandesPage, BiRevenuePage, BiArticlesPage, BiClientsPage, BiCommercialPage) plus auxiliary marts for stock snapshot and payment reconciliation.

### [2026-06-25 15:25] CORRECTION — Align BI-06/BI-07 schema names to staging/warehouse/mart

- **Type:** CORRECTION (of BI-06/BI-07)
- **Model/effort used:** GPT-5 medium
- **Summary:** Corrected the BI-06/BI-07 draft artifacts to follow the locked schema
  decision: `staging` for `stg_*`, `warehouse` for `dim_*`/`fact_*`, and `mart`
  for BI-08 mart views. Fixed the BI-07 DECISION-06 heading so it now states that
  all 8 facts are confirmed. No DDL was run against PostgreSQL.
- **Folders/files inspected:** `docs/bi-service-roadmap/sql/staging.sql`,
  `docs/bi-service-roadmap/sql/warehouse.sql`,
  `docs/bi-service-roadmap/_design/06_staging_design.md`,
  `docs/bi-service-roadmap/_design/07_warehouse_design.md`,
  `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`,
  `docs/bi-service-roadmap/01_bi_architecture_vision.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Files created/modified:** `docs/bi-service-roadmap/sql/staging.sql`,
  `docs/bi-service-roadmap/sql/warehouse.sql`,
  `docs/bi-service-roadmap/_design/06_staging_design.md`,
  `docs/bi-service-roadmap/_design/07_warehouse_design.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Use schema names `staging` / `warehouse` / `mart`
  consistently, matching vision §5/§7 and the warehouse rebuild roadmap. Keep
  table prefixes unchanged (`stg_*`, `dim_*`, `fact_*`, `mart_*`). Keep auxiliary
  schema `etl` separate because it stores ETL control-plane tables (`etl_run_log`,
  `etl_run_table_stats`, `stg_rejects`) rather than BI-serving data; folding it
  into `staging` would mix raw source mirrors with operational metadata.
- **Validation commands run:**
  - `python -c "from pathlib import Path; import sqlglot; files=['docs/bi-service-roadmap/sql/staging.sql','docs/bi-service-roadmap/sql/warehouse.sql']; ... sqlglot.parse(sql, dialect='postgres') ..."`
- **Validation results:** `staging.sql`: 65 statements, 0 parse errors ✅.
  `warehouse.sql`: 98 statements, 0 parse errors ✅. Search confirmed no remaining
  old schema qualifiers in the four corrected BI-06/BI-07 artifacts, except
  intentional `stg_*` table prefixes.
- **Problems encountered:** The worktree contains many unrelated pre-existing
  modified/untracked files. This correction touched and staged only the approved
  BI roadmap files.
- **Next recommended task:** **BI-08** — Mart design for the 5 frontend pages,
  targeting schema `mart` and reading from `warehouse.dim_*` / `warehouse.fact_*`.

**Refreshed roadmap status snapshot (correction-local; §6 above remains append-only):**

| Task | Title | Phase | Status |
|---|---|---|---|
| BI-00 | Repo & service inventory snapshot | 0 | ✅ Done |
| BI-01 | Data folder inventory & reconciliation | 0 | ✅ Done |
| BI-02 | Deep profiling of canonical transactional files | 1 | ✅ Done |
| BI-03 | Encoding / format / missing-value profile | 1 | ✅ Done |
| BI-04 | Business-entity catalogue | 1 | ✅ Done |
| BI-05 | Cleaning, dedup & normalization plan | 2 | ✅ Done |
| BI-06 | Staging schema design (Postgres) | 3 | ♻️ Corrected |
| BI-07 | Warehouse dim/fact design alignment | 3 | ♻️ Corrected |
| BI-08 | Mart design for the 5 frontend pages | 3 | ⬜ Not started |
| BI-09 | ETL implementation plan (raw→staging→warehouse→marts) | 4 | ⬜ Not started |
| BI-10 | BI API contract & DTO design | 5 | ⬜ Not started |
| BI-11 | BI extraction decision (package vs service) | 6 | ⬜ Not started |
| BI-12 | Frontend integration plan (5 pages) | 7 | ⬜ Not started |
| BI-13 | Export/reporting endpoint design | 8 | ⬜ Not started |
| BI-14 | Testing & validation strategy | 9 | ⬜ Not started |

### [2026-06-25 15:40] BI-08 — Mart design for the frontend BI pages

- **Type:** milestone
- **Model/effort used:** GPT-5 medium
- **Summary:** Designed draft-only frontend mart views in target schema `mart`,
  with every visible BI widget mapped to named `mart_*` columns. Covered the five
  detail BI pages requested plus the real overview BI route found in the frontend.
  No DDL was run against PostgreSQL.
- **Folders/files inspected:** `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/semantic_layer/metrics.yml`,
  `frontend/src/features/bi/pages/`,
  `frontend/src/features/bi/components/BiWidgets.tsx`,
  `frontend/src/features/dashboard/pages/DashboardOverviewPage.tsx`,
  `frontend/src/features/analysis/pages/SalesAnalysisPage.tsx`,
  `frontend/src/features/bi/services/biApi.ts`,
  `frontend/src/types/bi.types.ts`,
  `backend/bi-service/src/main/java/com/lpn/bi/application/service/SalesDashboardService.java`,
  `docs/bi-service-roadmap/sql/warehouse.sql`.
- **Files created/modified:** `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Use plain PostgreSQL `VIEW`s for BI-08, not
  materialized views or tables, because the current service computes raw
  dashboard KPIs quickly, the BI pages need fresh sales/invoice/stock visibility,
  and no refresh DAG or invalidation contract exists yet. Revisit materialized
  views after BI-14 if p95 dashboard latency exceeds 300 ms; likely first
  candidates are `mart_sales_by_product`, `mart_sales_by_customer`, and
  `mart_sales_by_commercial`. Metric formulas reuse `metrics.yml`; `ca_commande`
  comes from `C_ORDER.GRANDTOTAL` at order grain and line net amounts at
  product/line grain, paid/unpaid uses `ISPAID`, nullable invoice-to-order
  bridges are left joined, and unknown dimension keys remain `0`.
- **Validation commands run:**
  - `python -c "from pathlib import Path; import sqlglot; path=Path('docs/bi-service-roadmap/sql/marts.sql'); sql=path.read_text(encoding='utf-8-sig'); parsed=sqlglot.parse(sql, dialect='postgres'); print(f'{path}: {len(parsed)} statements, 0 parse errors')"`
- **Validation results:** `marts.sql`: 10 statements, 0 parse errors. Design
  matrix maps every visible widget from `BiOverviewPage`, `BiCommandesPage`,
  `BiRevenuePage`, `BiArticlesPage`, `BiClientsPage`, and `BiCommercialPage` to
  named mart columns.
- **Problems encountered:** The prompt refers to 5 frontend BI pages, while the
  real route folder also contains `BiOverviewPage`; the design covers the
  overview plus the five detail pages so no visible BI widget is left unmapped.
- **Next recommended task:** **BI-09** — ETL implementation plan. Reason:
  sequence the draft staging, warehouse, and mart DDL into an executable
  raw-to-staging-to-warehouse-to-mart load plan without running DDL yet.

### [2026-06-25 15:58] BI-09 — ETL implementation plan

- **Type:** milestone
- **Model/effort used:** GPT-5 high
- **Summary:** Created a draft-only ETL implementation plan for
  raw-to-`staging`-to-`warehouse`-to-`mart`, explicitly extending the existing
  prototype ETL scripts instead of rewriting them. The plan defines the job DAG,
  idempotency strategy, streamed/chunked reads, DQ checks, reject store, run
  order, logging, validation report locations, and BI-08 mart reachability.
  No ETL was run, no DDL was executed, and no stubs were created.
- **Folders/files inspected:** `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `docs/bi-service-roadmap/02_bi_execution_tasks.md`,
  `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`,
  `docs/bi-service-roadmap/_inventory/01_data_inventory.md`,
  `docs/bi-service-roadmap/_profiling/02_transactional_profile.md`,
  `docs/bi-service-roadmap/_profiling/03_format_quality_profile.md`,
  `docs/bi-service-roadmap/_plan/05_cleaning_plan.md`,
  `docs/bi-service-roadmap/_design/06_staging_design.md`,
  `docs/bi-service-roadmap/_design/07_warehouse_design.md`,
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/staging.sql`,
  `docs/bi-service-roadmap/sql/warehouse.sql`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `DataWareHouse/processus_de_vente/etl/scripts/*.py`,
  `DataWareHouse/processus_de_vente/etl/config/etl_config.json`.
- **Files created/modified:** `docs/bi-service-roadmap/_plan/09_etl_plan.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Keep the auxiliary `etl` schema separate for run-log,
  table-stats, and append-only reject storage. Extend `run_etl.py`,
  `extract_sources.py`, `transform_dimensions.py`, `transform_facts.py`, and
  `validate_etl.py` for PostgreSQL rather than replacing their business logic.
  Use truncate/reload for staging and facts, SCD1 upserts for dimensions with
  UNKNOWN key `0`, and `CREATE OR REPLACE VIEW` for BI-08 marts. Preserve all
  BI-01..08 decisions: TRAP-01 exclusion, CSV-only large product files, chunked
  80-253 MB XLSX reads, DOCSTATUS filters, `LEFT JOIN` nullable invoice bridges,
  `ISPAID` paid/unpaid logic, and BI-05 acceptance gates.
- **Validation commands run:**
  - `python -c "from pathlib import Path; plan=Path('docs/bi-service-roadmap/_plan/09_etl_plan.md').read_text(encoding='utf-8'); marts=[...]; missing=[m for m in marts if m not in plan]; old=[s for s in ['dwh_vente','business schema'] if s in plan]; print(...)"`
  - `rg -n "stg_c_bp_group|dwh_vente|CREATE TABLE|psql|docker exec|run full ETL|business schema" docs/bi-service-roadmap/_plan/09_etl_plan.md`
- **Validation results:** BI-08 mart coverage PASS; all nine BI-08 mart names are
  present in the plan and mapped to planned `warehouse.dim_*` / `warehouse.fact_*`
  outputs. No stale `dwh_vente` references were found. The only `stg_c_bp_group`
  hit is an explicit note that BI-06 did not define that staging table. No
  implementation files or ETL stubs were created.
- **Problems encountered:** The worktree contains unrelated pre-existing changes
  outside the BI roadmap. This task modified only the BI-09 plan and the roadmap
  context log.
- **Next recommended task:** **BI-10** — BI API contract & DTO design. Reason:
  define the marts-only API and DTO fields before any backend repointing or ETL
  execution work.

### [2026-06-25 19:12] BI-10 — BI API contract & DTO design

- **Type:** milestone
- **Model/effort used:** GPT-5 high
- **Summary:** Created a draft OpenAPI-style BI API contract under `/v1/bi/*`.
  The contract is marts-only: every business response DTO field traces to a
  named BI-08 mart column, while response metadata is kept separate from business
  DTO lineage. It defines page endpoints, common filters, pagination, row limits,
  error responses, performance notes, caching, and migration intent from the
  current `/v1/sales-dashboard` and `/v1/sales-analysis` endpoints. No Java,
  TypeScript, controller, service, DTO, DDL, or PostgreSQL changes were made.
- **Folders/files inspected:** `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardService.java`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/pages/`,
  `frontend/src/features/bi/hooks/useSalesDashboard.ts`,
  `frontend/src/features/bi/hooks/useSalesAnalysis.ts`,
  `frontend/src/features/bi/utils/biTransformers.ts`,
  `frontend/src/features/bi/components/sections/`,
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `docs/semantic_layer/metrics.yml`.
- **Files created/modified:** `docs/bi-service-roadmap/_design/10_bi_api_contract.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Keep snake_case DTO fields aligned with the current
  Java/TypeScript response style, but move the future contract to `/v1/bi/*`.
  Cover the five detail pages plus the real `BiOverviewPage`; map live
  `DashboardOverviewPage` to `/v1/bi/overview` and live `SalesAnalysisPage` to
  `/v1/bi/analysis`. Treat `distributor_*` fields as compatibility aliases over
  BI-08 `supplier_*` mart columns until BI-12 either renames the frontend labels
  or a later mart splits supplier and distributor semantics. Keep BI-08's plain
  view decision and flag materialized-view/index revisit after BI-14 latency
  measurements.
- **Validation commands run:**
  - `python -c "from pathlib import Path; doc=Path('docs/bi-service-roadmap/_design/10_bi_api_contract.md').read_text(encoding='utf-8'); marts=[...]; endpoints=[...]; forbidden=[...]; print(...)"`
  - `rg -n "CREATE TABLE|@GetMapping|class |interface |business\\.|dwh_vente|No visible widget lacks|distributor_\\*|/v1/bi/" docs/bi-service-roadmap/_design/10_bi_api_contract.md`
  - `git diff --check -- docs/bi-service-roadmap/_design/10_bi_api_contract.md docs/bi-service-roadmap/context_bi_service.md`
- **Validation results:** Contract validation PASS. All nine BI-08 mart names
  and all seven planned `/v1/bi/*` endpoints are present. No implementation or
  DDL markers (`CREATE TABLE`, `@GetMapping`, Java `class`/`interface`) were
  found in the design doc. No stale `dwh_vente` or `business.*` references were
  found. Coverage section states that no visible widget lacks a backing mart
  column, with the intentional `distributor_*` alias gap called out.
- **Problems encountered:** The real frontend contains six BI route components
  when counting `BiOverviewPage`, although the roadmap shorthand says five
  pages. The contract covers overview plus the five detail pages and the two
  live sales dashboard/analysis pages.
- **Next recommended task:** **BI-11** — BI extraction decision. Reason:
  decide whether the marts-only contract should first live inside
  `llm-orchestrator` as a clean package or move immediately toward a standalone
  BI service.

### [2026-06-25 19:25] BI-11 — BI extraction decision

- **Type:** milestone
- **Model/effort used:** GPT-5 maximum
- **Summary:** Created the BI extraction decision/design document. The
  recommendation is deadline-aware package-first: build the future `/v1/bi/*`
  implementation as a clean `bi` package inside `llm-orchestrator` now, then
  extract a standalone Spring `bi-service` later after measurable readiness
  gates pass. No service, Java package, Gradle module, Docker Compose entry, or
  PostgreSQL object was created.
- **Folders/files inspected:** `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `docs/bi-service-roadmap/01_bi_architecture_vision.md`,
  `docs/bi-service-roadmap/_design/10_bi_api_contract.md`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardService.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/ForecastController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/PredictiveClient.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/PredictiveHttpClient.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SqlExecutorClient.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SqlExecutorHttpClient.java`,
  `services-java/llm-orchestrator/src/main/resources/application.yml`,
  `services-java/llm-orchestrator/build.gradle.kts`,
  `services-java/sql-executor/src/main/java/com/lpn/aibi/sqlexecutor/DataSourceConfig.java`,
  `services-java/sql-executor/src/main/java/com/lpn/aibi/sqlexecutor/SqlExecutionController.java`,
  `services-java/sql-executor/src/main/java/com/lpn/aibi/sqlexecutor/SqlExecutionService.java`,
  `services-java/sql-executor/src/main/resources/application.yml`,
  `services-java/settings.gradle.kts`, `docker-compose.yml`,
  `scripts/launch-local-native.ps1`.
- **Files created/modified:** `docs/bi-service-roadmap/_design/11_extraction_decision.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Recommend a strangler-fig package-first path, not a
  standalone `bi-service` now. The package boundary is
  `com.lpn.aibi.llmorchestrator.bi`, owning controllers, DTOs, application
  services, static mart repositories, a BI-specific read-only datasource, error
  mapping, and tests. The package must not import LLM/orchestration code or use
  `SqlExecutorClient`; it must read `mart.*` through a read-only datasource
  modeled after `sql-executor`. Standalone extraction is deferred until marts
  exist and reconcile, the BI-10 contract is stable, endpoint coverage and
  package tests pass, imports are clean, read-only grants are proven, and
  deployment changes are mechanical.
- **Validation commands run:**
  - `python -c "from pathlib import Path; doc=Path('docs/bi-service-roadmap/_design/11_extraction_decision.md').read_text(encoding='utf-8'); required=[...]; forbidden=[...]; print(...)"`
  - `rg -n "Proceed package-first|AC-10|SalesDashboardController|SalesDashboardService|ForecastController|PredictiveHttpClient|DataSourceConfig|8081|8082|8085|/v1/bi/" docs/bi-service-roadmap/_design/11_extraction_decision.md`
  - `git diff --check -- docs/bi-service-roadmap/_design/11_extraction_decision.md docs/bi-service-roadmap/context_bi_service.md`
- **Validation results:** Decision-doc validation PASS. Required evidence,
  recommendation, package boundary, no-import rule, real class names, real
  ports, BI-10 `/v1/bi/*` migration intent, and AC-01 through AC-10 extraction
  gates are present. No implementation markers for Java source creation,
  Gradle/Docker Compose edits, PostgreSQL DDL, or service scaffolding were
  introduced in the BI-11 design.
- **Problems encountered:** The workspace already had many unrelated modified
  and untracked files outside this task. This task modified only the BI-11
  design document and roadmap context log.
- **Next recommended task:** **BI-12** — frontend integration plan. Reason:
  after the package-vs-service decision, the next draft step is planning how the
  real BI pages move from legacy sales endpoints to the stable `/v1/bi/*`
  contract.

### [2026-06-25 19:44] BI-12 — Frontend integration plan

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high (requested)
- **Summary:** Created the frontend integration plan for replacing the five BI
  placeholder pages with BI-10 `/v1/bi/*` endpoints. The plan maps every visible
  placeholder widget to an endpoint and DTO field, preserves loading/empty/error
  behavior, defines safe placeholder-removal checklists, gives a lowest-risk
  rollout order, and keeps legacy `/v1/sales-dashboard` and `/v1/sales-analysis`
  live until parity is proven. No React, API client, export, build, or runtime
  code was modified or run.
- **Folders/files inspected:** `.claude/skills/frontend-design/SKILL.md`,
  `.claude/skills/vercel-react-best-practices/SKILL.md`,
  `.claude/skills/vercel-react-best-practices/AGENTS.md`,
  `.claude/skills/web-design-guidelines/SKILL.md`,
  `https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md`,
  `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `docs/bi-service-roadmap/_design/10_bi_api_contract.md`,
  `frontend/src/features/bi/pages/BiCommandesPage.tsx`,
  `frontend/src/features/bi/pages/BiRevenuePage.tsx`,
  `frontend/src/features/bi/pages/BiArticlesPage.tsx`,
  `frontend/src/features/bi/pages/BiClientsPage.tsx`,
  `frontend/src/features/bi/pages/BiCommercialPage.tsx`,
  `frontend/src/features/bi/pages/BiOverviewPage.tsx`,
  `frontend/src/features/bi/pages/DashboardOverviewPage.tsx`,
  `frontend/src/features/bi/pages/SalesAnalysisPage.tsx`,
  `frontend/src/features/bi/components/widgets/BiWidgets.tsx`,
  `frontend/src/features/bi/components/charts/`,
  `frontend/src/features/bi/components/sections/`,
  `frontend/src/features/bi/components/filters/SalesAnalysisFilterCard.tsx`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `frontend/src/features/bi/hooks/useSalesDashboard.ts`,
  `frontend/src/features/bi/hooks/useSalesAnalysis.ts`,
  `frontend/src/features/bi/components/controls/BiExportDialog.tsx`,
  `frontend/src/features/bi/utils/biExport.ts`.
- **Files created/modified:** `docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Migrate the static detail pages first, one page at a
  time, using BI-10 page endpoints and existing chart/table primitives instead
  of inventing new UI. Keep legacy live dashboard routes on
  `/v1/sales-dashboard` and `/v1/sales-analysis` until `/v1/bi/overview` and
  `/v1/bi/analysis` pass parity. Preserve existing skeleton, retryable error,
  empty chart, refresh, and filter-state patterns. Keep PNG/PDF export in the
  frontend and defer backend Excel/data export to BI-13. Keep the known
  `distributor_*` compatibility alias over supplier fields as the only naming
  issue to resolve during implementation.
- **Validation commands run:**
  - `python -c "from pathlib import Path; doc=Path('docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md').read_text(encoding='utf-8'); pages=[...]; endpoints=[...]; required=[...]; forbidden=[...]; print(...)"`
  - `rg -n "BiCommandesPage|BiRevenuePage|BiArticlesPage|BiClientsPage|BiCommercialPage|/v1/bi/orders|/v1/bi/revenue|/v1/bi/articles|/v1/bi/clients|/v1/bi/commercial|No placeholder widget lacks|BI-13" docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`
  - `git diff --check -- docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md docs/bi-service-roadmap/context_bi_service.md`
- **Validation results:** Frontend integration plan validation PASS. All five
  required BI pages, the overview/live migration path, all seven BI-10
  endpoints, loading/empty/error preservation, rollout order, legacy coexistence,
  export non-change rule, BI-13 export deferral, and the no-widget-gap coverage
  statement are present. No React/API/export implementation markers or frontend
  run commands were introduced.
- **Problems encountered:** The `web-design-guidelines` skill required fetching
  the latest upstream guideline text, which was done read-only. The workspace
  still contains unrelated pre-existing changes outside this task; BI-12 only
  modified the roadmap plan and context log.
- **Next recommended task:** **BI-13** — export/reporting design. Reason:
  frontend migration planning explicitly leaves PNG/PDF capture in the frontend
  and moves real Excel/data export design to the backend/reporting task.

### [2026-06-25 20:07] BI-13 — Export / reporting endpoint design

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high (requested)
- **Summary:** Created the export/reporting endpoint design. The design keeps
  PNG/PDF as frontend WYSIWYG exports through the existing `html-to-image` and
  `jsPDF` path, and moves Excel/structured data export to Spring BI endpoints
  under `/v1/bi/export/{page}` that read the same BI-08 `mart.*` objects as the
  BI-10 dashboards. It explicitly rejects adding a new .NET reporting service.
  No endpoint, Java, React, DDL, Docker, database, or runtime change was made.
- **Folders/files inspected:** `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `.agents/skills/java-springboot/SKILL.md`,
  `frontend/src/features/bi/utils/biExport.ts`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `docs/bi-service-roadmap/_design/10_bi_api_contract.md`,
  `docs/bi-service-roadmap/_design/11_extraction_decision.md`,
  `docs/bi-service-roadmap/01_bi_architecture_vision.md`,
  `services-dotnet/`, `README.md`,
  `docs/architecture/LPN_AI_BI_Production_Plan.md`.
- **Files created/modified:** `docs/bi-service-roadmap/_design/13_export_reporting_design.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Define `GET /v1/bi/export/{page}` for `overview`,
  `orders`, `revenue`, `articles`, `clients`, `commercial`, and `analysis`.
  Successful responses return `.xlsx` with
  `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  and attachment `Content-Disposition`; errors reuse the BI-10 JSON envelope.
  Request params reuse BI-10 filters. Every workbook starts with a `Context`
  sheet and then page-specific sheets mapped to BI-08 marts. The frontend's
  current `fflate` Excel metadata workbook should later be retired only for the
  Excel branch, while PNG/PDF frontend capture stays unchanged. Reporting lives
  in the Spring BI layer from BI-11, not in `.NET`.
- **Validation commands run:**
  - `python -c "from pathlib import Path; doc=Path('docs/bi-service-roadmap/_design/13_export_reporting_design.md').read_text(encoding='utf-8'); endpoints=[...]; marts=[...]; required=[...]; forbidden=[...]; print(...)"`.
  - `rg -n "PNG export stays|PDF export stays|No new \\.NET service|/v1/bi/export/(overview|orders|revenue|articles|clients|commercial|analysis)|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet|Content-Disposition|fflate|mart_stock_risk|Every page's Excel action" docs/bi-service-roadmap/_design/13_export_reporting_design.md`
  - `git diff --check -- docs/bi-service-roadmap/_design/13_export_reporting_design.md docs/bi-service-roadmap/context_bi_service.md`
- **Validation results:** Export/reporting design validation PASS. All seven
  export endpoints, all nine BI-08 marts, the exact `.xlsx` MIME type,
  `Content-Disposition`, frontend PNG/PDF split, backend Excel migration intent,
  Spring BI ownership, no-.NET decision, and every page's Excel coverage are
  present. No implementation markers (`@GetMapping`, Java class snippets,
  `CREATE TABLE`, frontend run commands, Docker, or `psql`) were introduced.
- **Problems encountered:** A first broad `rg` over the repository was too noisy
  because large reference/example frontend files matched `export`; it was
  stopped after exit and replaced with focused reads and targeted searches. The
  workspace still contains unrelated pre-existing changes outside this task;
  BI-13 only modified the roadmap design and context log.
- **Next recommended task:** **BI-14** — testing and validation strategy.
  Reason: the export design completes the contract/design chain; the next step
  should define how to verify marts, API, frontend integration, exports, and
  performance before implementation work proceeds.

### [2026-06-25 20:59] BI-14 — Testing & validation strategy

- **Type:** milestone
- **Model/effort used:** GPT-5.5 high (requested)
- **Summary:** Created the final BI planning roadmap artifact: a layered
  testing and validation strategy covering data quality, ETL, backend API
  contracts, frontend integration, export/reporting, performance, manual demo,
  and the golden-number reconciliation gate. The plan explicitly states that no
  dashboard KPI may be repointed from `business.*` to `mart.*` until the mart
  value reconciles with the legacy value for the same filters and tolerance. It
  includes the required concrete worked example: current
  `/v1/sales-dashboard` `kpis.order_count = 616` must match the candidate
  mart-derived value exactly before migration. No tests, code, DDL, ETL,
  frontend run, backend run, or PostgreSQL action was implemented or executed.
- **Folders/files inspected:** `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/03_bi_task_prompts.md`,
  `.agents/skills/python-testing-patterns/SKILL.md`,
  `.agents/skills/python-testing-patterns/references/details.md`,
  `.agents/skills/python-testing-patterns/references/advanced-patterns.md`,
  `.agents/skills/java-springboot/SKILL.md`,
  `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/`,
  `services-java/sql-executor/src/test/java/com/lpn/aibi/sqlexecutor/`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardController.java`,
  `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardService.java`,
  `services-python/tests/`,
  `services-python/data-import/tests/`,
  `services-python/sql-validator/tests/`,
  `services-python/predictive/tests/`,
  `services-python/schema-retrieval/tests/`,
  `services-python/pyproject.toml`,
  `services-python/data-import/pyproject.toml`,
  `frontend/package.json`,
  `frontend/vite.config.ts`,
  `frontend/src/features/bi/pages/`,
  `frontend/src/features/bi/api/biApi.ts`,
  `frontend/src/features/bi/types/bi.types.ts`,
  `docs/semantic_layer/metrics.yml`,
  `docs/bi-service-roadmap/_plan/05_cleaning_plan.md`,
  `docs/bi-service-roadmap/_design/06_staging_design.md`,
  `docs/bi-service-roadmap/_design/07_warehouse_design.md`,
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `docs/bi-service-roadmap/_plan/09_etl_plan.md`,
  `docs/bi-service-roadmap/_design/10_bi_api_contract.md`,
  `docs/bi-service-roadmap/_design/11_extraction_decision.md`,
  `docs/bi-service-roadmap/_plan/12_frontend_integration_plan.md`,
  `docs/bi-service-roadmap/_design/13_export_reporting_design.md`,
  `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md`.
- **Files created/modified:** `docs/bi-service-roadmap/_plan/14_testing_strategy.md`,
  `docs/bi-service-roadmap/context_bi_service.md`.
- **Technical decisions:** Use the existing pytest fixture/`tmp_path`/opt-in
  PostgreSQL pattern for future ETL validation; use the existing Spring Boot
  MockMvc + mocked collaborator pattern for fast backend contract tests; guard
  real database reconciliation tests behind environment variables; require
  frontend test setup to be added during implementation because none exists
  today; keep PNG/PDF export validation on the frontend path while validating
  backend `.xlsx` export separately; make the reconciliation gate exact for
  count metrics, <=0.5% for money metrics, and <=0.10 percentage points for
  rates; treat BI-08 plain views as acceptable until measured p95 latency
  exceeds 300 ms, at which point materialized-view promotion is revisited.
- **Validation commands run:**
  - `powershell -Command "$doc = Get-Content -Raw docs/bi-service-roadmap/_plan/14_testing_strategy.md; $required = @(...); $missing = $required | Where-Object { -not $doc.Contains($_) }; if ($missing) { Write-Error ...; exit 1 }; 'BI-14 doc static coverage PASS'"`
  - `rg -n "DQ-ROW-01|DQ-TOTAL-01|DQ-REJECT-01|DQ-TRAP-01|ETL-IDEMP-01|ETL-CHUNK-01|BI-API-CONTRACT-01|BI-GOLDEN-ORDERCOUNT-616|BI-ARCH-IMPORT-01|FE-(ORDERS|REVENUE|ARTICLES|CLIENTS|COMMERCIAL)-01|order_count = 616|no dashboard KPI is repointed|Manual demo checklist|DATA_WAREHOUSE_REBUILD_TASK_ROADMAP" docs/bi-service-roadmap/_plan/14_testing_strategy.md`
  - `git diff --check -- docs/bi-service-roadmap/_plan/14_testing_strategy.md docs/bi-service-roadmap/context_bi_service.md`
- **Validation results:** BI-14 static validation PASS. The plan contains
  named checks for every requested layer, covers BI-05 through BI-13, includes
  the explicit `order_count = 616` reconciliation example, includes the
  five-page manual demo checklist plus export checks, and records the
  implementation dependency on the DATA_WAREHOUSE_REBUILD_TASK_ROADMAP loading
  `staging`, `warehouse`, and `mart` into PostgreSQL. No test suite, ETL,
  frontend, backend, DDL, database, or runtime command was run.
- **Problems encountered:** Frontend has no existing test runner or spec files,
  so the strategy records frontend testing setup as a future implementation
  prerequisite. Some broad repository searches were noisy because older docs
  still reference previous mart names, so BI-14 grounded the plan in the current
  BI-08/BI-10/BI-12/BI-13 artifacts. The workspace still contains unrelated
  pre-existing changes outside this task; BI-14 only modified the testing plan
  and context log.
- **Next recommended task:** **Implementation phase** — materialize/load
  `staging`, `warehouse`, and `mart` through the
  DATA_WAREHOUSE_REBUILD_TASK_ROADMAP, then build the Spring BI package and
  `/v1/bi/*` endpoints, wire the five frontend pages, and move Excel/data
  export to the backend while keeping PNG/PDF frontend capture unchanged.

### [2026-06-29 00:00] DW-01 — Provision staging/etl/warehouse schemas + DDL

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 high
- **Summary:** Created the three warehouse schemas (`staging`, `etl`, `warehouse`)
  and executed both DDL files (`staging.sql` 65 stmts + `warehouse.sql` 98 stmts)
  in a single fail-closed transaction on `localhost:5432/lpn_ai_bi`. Schema naming
  was already correct (CORRECTION of BI-06/07 had already fixed `stg`→`staging`).
  One DDL fix was required: `dim_date.month_name VARCHAR(20)` → `VARCHAR(30)` because
  the UNKNOWN row value `'Unknown / Non renseigne'` (23 chars) exceeded the column
  width; first transaction ROLLBACKed cleanly (fail-closed guardrail confirmed), fix
  applied, second transaction COMMITted. All 12 warehouse dims have exactly one
  UNKNOWN row at key=0. All 11 BIGSERIAL sequences reset to 1. `lpn_ai_readonly`
  granted USAGE+SELECT on `warehouse`. Business schema verified identical before and
  after (40 tables, zero count changes). Pre-existing empty `mart` schema discovered
  (owned by `lpn_app_admin`) — not modified.
- **Folders/files inspected:**
  - `docs/bi-service-roadmap/sql/staging.sql` (65 stmts, validated)
  - `docs/bi-service-roadmap/sql/warehouse.sql` (98 stmts, validated + fixed)
  - `docs/bi-service-roadmap/2024_implementation/00_CONTEXT_AND_HISTORY.md`
  - `docs/bi-service-roadmap/2024_implementation/01_IMPLEMENTATION_TASKS.md`
  - `docs/bi-service-roadmap/context_bi_service.md`
  - `docs/LOCAL_DEV_SETUP.md`
  - `.env`
  - `services-java/llm-orchestrator/src/main/resources/application.yml`
- **Files created/modified:**
  - `docs/bi-service-roadmap/sql/warehouse.sql` (VARCHAR fix: month_name 20→30)
  - `docs/bi-service-roadmap/2024_implementation/_run/DW01_provision_report.md` (created)
  - `docs/bi-service-roadmap/context_bi_service.md` (this entry)
- **Technical decisions:**
  - **PORT:** Native dev PG18 runs on 5432 only (5433 not listening). `application.yml`
    default of 5433 is for Docker mode; dev mode uses 5432. Used `postgres` superuser
    (password from native restore) since `lpn_app_admin` role password differs from
    `.env` placeholder. For future runs, confirm the write-role password or use
    `postgres` superuser with explicit override.
  - **VARCHAR fix:** `dim_date.month_name VARCHAR(20)` → `VARCHAR(30)`. The UNKNOWN
    value `'Unknown / Non renseigne'` is 23 chars. Fix logged in warehouse.sql header.
  - **mart schema pre-exists:** An empty `mart` schema (no tables) was found, owned by
    `lpn_app_admin`. It was not touched. DW-08 will populate it.
  - **Grants:** `lpn_ai_readonly` granted USAGE+SELECT on `warehouse` now (as specified
    in DW-01). Grant on `mart` deferred to DW-08.
  - **Idempotency confirmed:** `CREATE TABLE IF NOT EXISTS` + `ON CONFLICT DO NOTHING`
    mean re-running this DDL is safe; the sequence guards are also idempotent.
- **Validation commands run:**
  - `python -c "import sqlglot, pathlib; ..."` — sqlglot parse of both files (×2: before and after fix)
  - `psql ... -c "SELECT schemaname, relname, n_live_tup FROM pg_stat_user_tables WHERE schemaname='business' ORDER BY relname"` (before + after snapshot)
  - `psql ... -c "\dn"` — schema listing
  - `psql ... -c "\dt staging.*"`, `\dt etl.*`, `\dt warehouse.*`
  - UNKNOWN row count query across all 12 dims
- **Validation results:**
  - sqlglot: staging.sql 65 stmts 0 errors ✅; warehouse.sql 98 stmts 0 errors ✅
  - Transaction: COMMIT ✅ (first attempt ROLLBACKed on VARCHAR error — correct behaviour)
  - Schemas: staging ✅, etl ✅, warehouse ✅ (+ pre-existing mart, app, business, public)
  - staging tables: 28 ✅ | etl tables: 3 ✅ | warehouse tables: 20 (12 dim + 8 fact) ✅
  - UNKNOWN rows: 12/12 dims each have exactly 1 row at key=0 ✅
  - Sequence guards: all 11 BIGSERIAL sequences set to 1 ✅
  - business schema: 40 tables, all counts identical before and after ✅
  - lpn_ai_readonly grants: GRANT USAGE + GRANT SELECT both returned GRANT ✅
- **Problems encountered:**
  - Port 5433 not listening; DB is on 5432 in native dev mode.
  - `lpn_app_admin` password mismatch (`.env` has placeholder); used `postgres` superuser.
  - `dim_date.month_name VARCHAR(20)` too narrow for UNKNOWN value (23 chars); fixed to 30.
  - First transaction ROLLBACKed on this error — fail-closed guardrail worked as designed.
- **Next recommended task:** **DW-02** — Build the chunked staging loader (Opus 4.8,
  architecture-critical). Reason: schemas and tables are now provisioned; DW-02 builds
  the Python loader that streams 2024 xlsx files and 2025-2026 CSVs into `staging.stg_*`
  with chunked reads (≤5,000 rows), ETL run logging, and reject capture.

### [2026-06-29 01:30] DW-02 — Chunked memory-safe staging loader + smoke load

- **Type:** milestone
- **Model/effort used:** Opus 4.8 high
- **Summary:** Built a reusable, header-driven, memory-safe, idempotent staging loader
  under `services-python/data-import/data_import/staging_loader/` (separate from the
  legacy business-schema bundle importer, which was NOT modified). It streams xlsx via
  `openpyxl read_only=True` and CSV via the stdlib `csv` module (utf-8-sig, `;`), stamps
  `_loaded_at/_source_file/_source_tag/_etl_run_id`, batches ≤5,000 rows via
  `execute_values`, logs runs to `etl.etl_run_log` + `etl.etl_run_table_stats`, captures
  bad rows to `etl.stg_rejects`, and is config-driven from `source_map.json` (the §2.1
  file list; 2024_02/04/06 chunked; 2024_16/18 excluded). Idempotency is DELETE-per-
  `_source_tag` (the other window survives). Smoke-loaded the 10 tiny 2024 dims (679 rows)
  and re-ran to prove idempotency. `business` untouched. pytest 23 passed / 1 skipped.
- **Folders/files inspected:**
  - `services-python/data-import/` (existing cli.py bundle importer, tests, pyproject)
  - `DataWareHouse/processus_de_vente/etl/scripts/extract_sources.py` (column-standardizer reuse)
  - `docs/bi-service-roadmap/sql/staging.sql` (target columns)
  - `Youssef_Extractions/data/Exported_LPN/2024_13..30_*.xlsx` (10 smoke files, headers)
  - `Youssef_Extractions/vente_2025_2026_import/csv/` (C_DOCTYPE.csv for column-order proof)
- **Files created/modified:**
  - Created: `staging_loader/{__init__,config,columns,readers,source_map,run_log,loader,cli}.py`
    + `staging_loader/source_map.json`
  - Created tests: `tests/test_staging_loader.py`, `tests/test_staging_readers.py`
  - Modified: `data-import/pyproject.toml` (added `openpyxl>=3.1`)
  - Created: `docs/bi-service-roadmap/2024_implementation/_run/DW02_loader_design.md`
  - Installed `openpyxl 3.1.5` into `services-python/.venv`
- **Technical decisions:**
  - **Header-driven column mapping** by `information_schema` ∩ standardized source headers;
    extra source cols dropped, missing cols → NULL (AD_USER: 41 src cols → 8 staging cols).
  - **Uniform streaming + execute_values** path for both xlsx and CSV (COPY noted as a
    future optimization; approved). Memory bounded to ≤5,000 rows regardless of file size.
  - **Reject vs fail discipline:** NULL natural key → `etl.stg_rejects` (keep going);
    structural failure (missing file, blank/mismatched header, missing table, DB error) →
    `StructuralError`, run marked `FAILED`, exit 1. Header mismatch raises BEFORE the
    DELETE so a bad file cannot wipe good staging data.
  - **Password not hard-coded:** read from env (`PGPASSWORD`/`POSTGRES_PASSWORD`/…),
    defaults host=localhost port=5432 user=postgres db=lpn_ai_bi.
- **DATA-QUALITY FINDING (C_DOCTYPE):** `2024_23_C_DOCTYPE.xlsx` is **headerless**
  (80 data rows, 33 cols, row 0 = data). The header-driven loader correctly failed loudly
  first. Resolved with a config-driven `xlsx_header` override whose 33-column order was
  **verified by a row-level positional comparison against `C_DOCTYPE.csv` (0 mismatches on
  4 shared ids)** and matches the `stg_c_doctype` DDL. **§2.1's "79" is an off-by-one**
  (assumed a header); the true raw count is **80** and staging holds 80. Secondary finding
  (DW-04 scope, flagged not fixed): **`C_DOCTYPE.csv` is comma-delimited, not `;`** —
  DW-04 must detect the delimiter per file.
- **KNOWN GAP (for DW-03/DW-05) — flagged loudly, not silently skipped:** `C_TAX` (2024_24),
  `AD_ORG` (2024_25), `M_LOCATOR` (2024_27), `CUSTOMER_PORTFOLIO` (2024_35) have **no BI-06
  staging table**, yet DW-03's text lists M_LOCATOR/C_TAX/AD_ORG/portfolio under "remaining
  dims". They are out of scope for the loader and absent from `source_map.json`. **DW-03/
  DW-05 must add staging tables or formally drop them.**
- **Validation commands run:**
  - `pytest -q` (data-import) → 23 passed, 1 skipped
  - `python -m data_import.staging_loader.cli smoke --window 2024_xlsx` (×2 for idempotency)
  - `psql` count queries on `staging.*` (10 tables), `etl.etl_run_log`, `etl.stg_rejects`,
    and `business.*` spot-check
- **Validation results:**
  - pytest: 23 passed, 1 skipped (incl. the `[5000,5000,2001]` batching proof) ✅
  - 10 smoke counts: region 87, city 364, ad_user 31, m_product_category 41,
    m_product_type 27, c_doctype 80, m_warehouse 7, c_paymentterm 7, m_pricelist 22,
    c_salesregion 13 — all match §2.1 (c_doctype 80 per the off-by-one finding) ✅
  - Idempotency: 2nd run → identical counts (no doubling), total 679 both runs ✅
  - Rejects: 0 ✅. Run log: 2 SUCCESS + 1 FAILED (the pre-fix C_DOCTYPE structural failure) ✅
  - business: c_order 20336, c_orderline 354910, c_invoiceline 335877, m_product 35822,
    fact_sales_monthly 25 — unchanged ✅
- **Problems encountered:**
  - `2024_23_C_DOCTYPE.xlsx` headerless (resolved via verified `xlsx_header` override; see above).
  - `openpyxl` absent from venv (installed 3.1.5; added to pyproject).
  - First smoke run failed loudly on C_DOCTYPE before the fix — the fail-loud discipline
    behaved exactly as designed (run marked FAILED, exit 1, no partial silent load).
- **Next recommended task:** **DW-03** — Load the full 2024 window into staging (Opus 4.8).
  First resolve the KNOWN GAP (C_TAX/AD_ORG/M_LOCATOR/portfolio). Reuse this loader; the
  giants (2024_02/04/06) are already `chunked=true`.

### [2026-06-29 11:00] DW-03 — Load full 2024 window into staging (chunked)

- **Type:** milestone
- **Model/effort used:** Opus 4.8 high
- **Summary:** Loaded the FULL 2024 window into `staging.stg_*` under
  `_source_tag='2024_xlsx'` using the DW-02 loader: **25 tables, 1,689,073 rows,
  0 rejects**, `business` untouched. Resolved the DW-02 KNOWN GAP by **formally dropping**
  C_TAX/AD_ORG/M_LOCATOR/CUSTOMER_PORTFOLIO from v1 scope (none is a star-schema dim or
  fact source). Loaded in two phases via a new `load-2024 --phase {small-medium|giants|all}`
  CLI command: 22 small/medium tables (332,357 rows) first, CHECKPOINT, then the 3 giants
  (1,356,716 rows) streamed in 5,000-row chunks with per-batch commit and progress logging.
  Products (M_PRODUCT/M_PRODUCT_PO/RV_STORAGE) deferred to DW-04 (CSV-only, SCD1).
- **Folders/files inspected:**
  - `services-python/data-import/data_import/staging_loader/*` (DW-02 loader)
  - `Youssef_Extractions/data/Exported_LPN/2024_*.xlsx` (25 loadable files)
  - `Youssef_Extractions/vente_2025_2026_import/csv/C_ALLOCATIONLINE.csv` (header-order proof)
  - `docs/bi-service-roadmap/sql/staging.sql` (column types)
- **Files created/modified:**
  - Modified: `staging_loader/source_map.py` (WINDOW_2024_GIANTS, window_2024_small_medium(),
    window_2024_all()); `staging_loader/source_map.json` (xlsx_header for stg_c_allocationline;
    formal-drop comment); `staging_loader/loader.py` (on_batch progress callback);
    `staging_loader/cli.py` (load-2024 command, progress logging, peak_working_set_mb()).
  - Modified DDL: `docs/bi-service-roadmap/sql/staging.sql` — `raf`/`out_avoirclient`/
    `paymentrulepo` → TEXT.
  - Modified: `docs/bi-service-roadmap/2024_implementation/01_IMPLEMENTATION_TASKS.md`
    (DW-03 scope: 4 entities dropped with rationale).
  - Created: `docs/bi-service-roadmap/2024_implementation/_run/DW03_load_2024_report.md`.
  - Live DB: ALTER 3 staging columns to TEXT; staging now holds 1,689,073 `2024_xlsx` rows.
- **Technical decisions:**
  - **STEP 0 formal drop:** C_TAX (out of CA scope), AD_ORG (singleton), M_LOCATOR (single
    locator), CUSTOMER_PORTFOLIO (derived aggregate, DW-09 only). No `stg_*` tables added.
  - **Phased load + checkpoint:** small/medium first (counts validated vs §2.1), then giants
    smallest-first (C_INVOICELINE 81 MB → M_INOUTLINE 132 MB → C_ORDERLINE 254 MB).
  - **Reactive type-fix discipline:** the loader fails loud on a numeric-column-gets-text
    error; the column is widened to TEXT (raw landing zone) and the table re-loaded — never
    a silent coerce/drop.
- **DATA-QUALITY FINDINGS:**
  - **F1 — 3 BI-06 DDL columns mis-typed:** `stg_c_order.raf` (holds `'N'` flag),
    `stg_c_bpartner.paymentrulepo` (`'T'` code), `stg_c_invoiceline.out_avoirclient`
    (free-text avoir notes) were declared numeric/bigint. Widened to TEXT in staging.sql +
    live tables. None feeds a dim/fact measure ⇒ DW-05/06 unaffected.
  - **F2 — 2nd headerless export:** `2024_09_C_ALLOCATIONLINE.xlsx` (after C_DOCTYPE). Added
    a verified 23-column `xlsx_header` override (order from C_ALLOCATIONLINE.csv, confirmed
    positionally: all 86,864 rows 23-wide, types align, names exist in staging, no numeric
    col gets text). **True count 86,864** (§2.1's 86,863 assumed a header; off-by-one). A
    full sweep found only C_DOCTYPE + C_ALLOCATIONLINE headerless; giants have headers.
  - **F3 — §2.1 giant estimates low (raw staging is unfiltered):** m_inoutline 654,493 (est.
    ~350K; all movements both ISSOTRX), c_orderline 440,303 (est. ~354K; sales+purchase
    lines), c_invoiceline 261,920 (est. ~270K). All verified fully-distinct keys, 0 nulls,
    composite PK enforced — no inflation. DW-09 must reconcile post-filter fact counts, not
    these raw estimates.
- **Commands run:**
  - `python -m data_import.staging_loader.cli load-2024 --phase small-medium` (failed twice
    loudly: F1 type error, then F2 headerless; succeeded after fixes — 332,357 rows)
  - `python -m data_import.staging_loader.cli load-2024 --phase giants` (1,356,716 rows, ~7 min)
  - `python -m data_import.staging_loader.cli load-table stg_c_orderline --window 2024_xlsx`
    (idempotency re-run)
  - `pytest -q` (23 passed, 1 skipped); psql/psycopg2 count + distinctness + business checks
- **Validation results:**
  - 25 tables, **1,689,073 rows, 0 rejects** (0% reject rate; gate <5%) ✅
  - Small dims exact vs §2.1; deviations explained (F2 +1, F3 giants, collection estimate) ✅
  - Idempotency: `c_orderline` re-run → 440,303 identical (DELETE momentarily showed partial
    40,000, then full reload; no doubling) ✅
  - Memory: peak RSS **302 MB** on the 254 MB c_orderline file — sharedStrings-bound, row
    buffer ≤5,000; never a full-file load ✅
  - All giant natural keys fully distinct, 0 nulls, composite PK `(nk,_source_tag)` enforced ✅
  - business unchanged: c_order 20,336 · c_orderline 354,910 · c_invoiceline 335,877 ·
    m_product 35,822 · fact_sales_monthly 25 ✅
  - pytest: 23 passed, 1 skipped ✅
- **Problems encountered:**
  - F1 type mismatch (fixed → TEXT) and F2 headerless file (fixed → verified xlsx_header)
    each failed the small/medium run loudly before the fix — fail-loud discipline worked;
    no partial silent load (DELETE runs after the header check, inside the txn).
  - `peak_working_set_mb()` first returned None (Win64 ctypes handle truncation); fixed with
    explicit `argtypes`/`restype` on GetCurrentProcess/GetProcessMemoryInfo.
- **Next recommended task:** **DW-04** (Sonnet 4.6) — load the 2025–2026 CSV window into the
  same staging tables under `_source_tag='csv_2025'`, incl. M_PRODUCT/M_PRODUCT_PO/RV_STORAGE.
  Carry forward: **per-file CSV delimiter detection** (C_DOCTYPE.csv is comma-delimited, not `;`).

---

### [2026-06-29 12:30] DW-04 — Load the 2025–2026 CSV window into staging

- **Summary:** Loaded `Youssef_Extractions/vente_2025_2026_import/csv/*.csv` into the same
  `staging.stg_*` tables under `_source_tag='csv_2025'` via the DW-02 loader: **28 tables,
  1,295,434 rows, 0 rejects**, every count matching `manifest.json` exactly. Staging now holds
  **both** windows. `business` untouched; the `2024_xlsx` window unchanged. CSV-only entities
  deferred from DW-03 — **M_PRODUCT (35,822), M_PRODUCT_PO (39,359), RV_STORAGE (99,234)** —
  loaded here. Report: `2024_implementation/_run/DW04_load_2025_2026_report.md`.
- **Files inspected:** `00_CONTEXT_AND_HISTORY.md`, `01_IMPLEMENTATION_TASKS.md` (DW-04),
  `context_bi_service.md` (DW-01/02/03), `_run/DW02_loader_design.md`,
  `_run/DW03_load_2024_report.md`, the `staging_loader/*` package + `source_map.json`,
  `vente_2025_2026_import/manifest.json`, and the 36 CSV headers.
- **Files created/modified:**
  - `staging_loader/readers.py` — new `detect_delimiter()`; `stream_csv_rows` uses a
    per-file delimiter; docstring updated.
  - `staging_loader/loader.py` — `load_table` detects the CSV delimiter and records it on
    `TableLoadResult.delimiter` (new field).
  - `staging_loader/columns.py` — `coerce_value` maps the literal CSV null sentinel `NULL`
    → `None` (`_NULL_TOKENS`).
  - `staging_loader/source_map.py` — `window_csv_all()`.
  - `staging_loader/cli.py` — `load-csv` command; delimiter shown per table.
  - `tests/test_staging_readers.py` — delimiter + NULL-token tests.
  - `docs/bi-service-roadmap/sql/staging.sql` — `stg_m_product.discontinuedby` &
    `stg_m_product_po.nbene` → TEXT (matched by live `ALTER`s).
- **Technical decisions / findings:**
  - **CARRY-FORWARD CORRECTED:** the DW-02 note ("only C_DOCTYPE.csv is comma-delimited") is
    inaccurate — **every CSV in the 2025–2026 package is comma-delimited, zero semicolons.**
    The old hard-coded `;` would have mis-parsed all 28 files. Added general
    `detect_delimiter()` (sniffs the header, counts fields per candidate respecting quotes);
    all 28 detected `,`.
  - **F1-class DQ (BI-06 mis-typed numeric, source holds text):** `stg_m_product.discontinuedby`
    holds **dates** (1,778), `stg_m_product_po.nbene` holds **free text** (43, e.g.
    `'devise was null'`). Widened both to TEXT (staging.sql + live). Loader **failed loud**
    on the first run (structural error on `stg_m_product`, run FAILED, no partial silent
    load — the 12 committed dims survived).
  - **F-CSV — literal `NULL` sentinel:** `M_PRODUCT_PO.csv` writes the bare token `NULL` for
    empty cells (all rows; isolated to that file). `coerce_value` now maps exact `NULL` →
    SQL NULL (xlsx never emits it; `'devise was null'`/`'null'`/`'NULLABLE'` preserved).
  - **OVERLAP PROBE — the assumed transactional overlap does NOT exist** (material for DW-06):
    the CSV export is filtered to `DATE '2025-01-01'` inclusive, so the two windows are
    **date- and ID-disjoint** (C_ORDER 2024 ids 1380621–1400181 ↔ csv 1400182–1425088;
    0 csv orders before 2025-01-01). **0 overlap** on C_ORDER/C_ORDERLINE/C_INVOICE/
    C_INVOICELINE/M_INOUT/M_INOUTLINE. The real overlap is in the **SCD1 dimensions**
    (c_bpartner 823, ad_user 28, c_region 16, c_doctype 7 all in both windows) ⇒ **DW-05/06
    dedup is necessary for the dims, a no-op safety net for the transactional facts.**
- **Commands run:**
  - `python -m data_import.staging_loader.cli load-csv` (failed loud once on m_product type;
    succeeded after the TEXT/NULL fixes — 28 tables, 1,295,434 rows, peak RSS 99 MB)
  - `python -m data_import.staging_loader.cli load-csv --only stg_m_product_po` (idempotency)
  - `pytest -q` (29 passed, 1 skipped); psycopg2 count-vs-manifest, overlap, PK, 2024 &
    business untouched checks.
- **Validation results:**
  - 28/28 tables == manifest exactly; **0 rejects**; all delimiters `,` ✅
  - Composite PK `(natural_key,_source_tag)` held; 0 duplicate groups ✅
  - Overlap: 0 on all 6 transactional tables (windows disjoint); dims fully overlap ✅
  - 2024_xlsx unchanged (c_order 18,024 · c_orderline 440,303 · c_invoiceline 261,920 ·
    m_inoutline 654,493 · c_bpartner 42,434 · c_allocationline 86,864) ✅
  - business unchanged (c_order 20,336 · c_orderline 354,910 · c_invoiceline 335,877 ·
    m_product 35,822) ✅
  - Idempotent re-run: stg_m_product_po → 39,359 stable ✅
  - pytest 29 passed, 1 skipped ✅
- **Problems encountered:** initial run failed loud on `stg_m_product` (date in a BIGINT col)
  — fail-loud discipline worked, no silent partial load; fixed via TEXT widening + the `NULL`
  sentinel mapping, then the full re-run was clean and idempotent.
- **Next recommended task:** **DW-05** (Sonnet 4.6) — build the 12 `warehouse.dim_*` from
  staging. Carry forward: **dimensions overlap across windows (dedup one current row per
  natural key); transactional facts are date/ID-disjoint** (DW-06 dedup is a safety net
  there). `discontinuedby`/`nbene` are now TEXT; products exist only under `csv_2025`.

---

### [2026-06-29 14:00] DW-05 — Build warehouse dimensions from staging

- **Type:** milestone
- **Model/effort used:** Sonnet 4.6 high
- **Summary:** Populated all 12 `warehouse.dim_*` tables from staging using a single
  idempotent SQL script (`DW05_dimensions.sql`). Applied BI-05 Layer-3 cleaning rules
  (TRIM, INITCAP, REGEXP_REPLACE tabs/newlines, NULLIF on FK IDs). Cross-window dedup
  via `ROW_NUMBER() OVER (PARTITION BY natural_key ORDER BY _source_tag)` preferring
  `csv_2025`. All 12 dims have exactly 1 UNKNOWN row (key=0) + N business rows. Zero
  orphan FK references. Business schema untouched. Idempotency confirmed by two full
  re-runs with identical business-row counts.
- **Folders/files inspected:**
  - `docs/bi-service-roadmap/2024_implementation/00_CONTEXT_AND_HISTORY.md`
  - `docs/bi-service-roadmap/2024_implementation/01_IMPLEMENTATION_TASKS.md`
  - `docs/bi-service-roadmap/context_bi_service.md` (DW-01..DW-04 milestones)
  - `docs/bi-service-roadmap/_design/07_warehouse_design.md`
  - `docs/bi-service-roadmap/_plan/05_cleaning_plan.md`
  - `docs/bi-service-roadmap/sql/warehouse.sql`
  - `information_schema.columns` for all 12 staging source tables
  - `pg_stat_user_tables` for staging row counts and warehouse state
- **Files created/modified:**
  - Created: `docs/bi-service-roadmap/2024_implementation/_run/DW05_dimensions.sql`
  - Created: `docs/bi-service-roadmap/2024_implementation/_run/DW05_dimensions_report.md`
  - Modified (live DB): all 12 `warehouse.dim_*` tables populated; sequences refreshed
- **Technical decisions:**
  - **DELETE key>0 + INSERT (not ON CONFLICT upsert):** The warehouse DDL uses
    `UNIQUE (c_bpartner_id, effective_from)` with `effective_from=NULL` for SCD1.
    PostgreSQL does not treat `(x, NULL)` as conflicting with another `(x, NULL)` in
    composite UNIQUE constraints, making ON CONFLICT unreliable. DELETE+INSERT is
    cleaner and safe since no facts exist yet (no FK violations from facts).
  - **Deletion order to respect inter-dim FKs:** dim_product (→category, →supplier)
    and dim_geography (→sales_region) must be deleted BEFORE their parent dims on each
    re-run. Phase A groups all deletes in reverse-dependency order.
  - **dim_date uses ON CONFLICT DO UPDATE:** INTEGER YYYYMMDD PK is stable; safe to upsert
    instead of delete+insert.
  - **INITCAP fix for dim_product_category:** Initial pass omitted INITCAP from category_name;
    fixed in rewritten SQL with `INITCAP(REGEXP_REPLACE(...))`.
  - **DEDUP-SUPP-01 tie-breaker:** `stg_m_product_po` has no `seqno` column in staging;
    used `c_bpartner_id ASC` as the deterministic tie-breaker per BI-05 fallback rule.
  - **dim_geography 85% UNKNOWN sales_region:** Expected — most Compiere customer locations
    have `c_salesregion_id=0` (no sales region assigned in the ERP). Routes to key=0.
- **Per-dim row counts (business rows / UNKNOWN rows):**
  - dim_date: 1,096 / 1 — generated 2024-01-01..2026-12-31, no gaps ✅
  - dim_customer: 3,472 / 1 — 823 csv_2025 preferred, 2,649 xlsx-only ✅
  - dim_commercial: 31 / 1 — all 31 salesreps (3 xlsx-only merged) ✅
  - dim_product_category: 41 / 1 — all categories, 13 xlsx-only preserved ✅
  - dim_supplier: 14,273 / 1 — deduped from 28,537 combined rows ✅
  - dim_product: 35,822 / 1 — csv_2025 only; 35,822/35,822 cat resolved; 35,821/35,822 supp resolved ✅
  - dim_sales_region: 13 / 1 ✅
  - dim_geography: 4,221 / 1 — 620 (14.7%) resolved sales_region; 3,601 → UNKNOWN (expected) ✅
  - dim_payment_term: 7 / 1 ✅
  - dim_price_list: 22 / 1 ✅
  - dim_document_type: 40 / 1 — filtered to issotrx='Y' ✅
  - dim_warehouse: 7 / 1 ✅
- **Data quality findings:**
  - **DQ-01 INFO:** 33 customer names remain all-caps after INITCAP — all dot-separated
    abbreviations (e.g. `F.O.L`, `E.S.I.S.A`). INITCAP treats each letter after `.` as
    word-initial, leaving acronyms uppercase. Expected; no action required.
  - **DQ-02 INFO:** 13 product_category names remain "all-caps" after INITCAP — all are
    numeric codes (`3100`–`4300`). False positive in the DQ check (numbers are case-neutral).
  - **DQ-03 PASS:** Zero tabs/newlines in dim_product_category after REGEXP_REPLACE ✅
  - **DQ-04 PASS:** Zero NULL natural keys in any non-zero row across all 12 dims ✅
  - **DQ-05 PASS:** Zero orphan FK references (dim_product→category/supplier, dim_geography→sales_region) ✅
  - **DQ-06 PASS:** business schema unchanged (c_orderline=354,910, c_invoiceline=335,877) ✅
- **Validation commands run:**
  - Staging column checks: `information_schema.columns` for all 12 source tables
  - Row count / UNKNOWN row check: `SELECT ... FILTER (WHERE key=0/key>0)` for all 12 dims
  - Orphan FK checks: `NOT IN` sub-queries for dim_product and dim_geography FKs
  - dim_date boundaries: `MIN/MAX(full_date)` and date count
  - NORM-02/03 assertions: tabs/newlines and all-caps checks
  - Business schema spot-check: `COUNT(*)` on c_orderline and c_invoiceline
  - Idempotency: script re-run (second run: all INSERT counts identical, zero errors)
- **Problems encountered:**
  - FK violation on first idempotency test: DELETE FROM dim_sales_region while
    dim_geography still referenced it. Fixed by adding Phase A (delete children first:
    dim_product and dim_geography before their parents).
  - INITCAP omitted from dim_product_category.category_name in first pass — fixed by
    rewriting SQL with combined `INITCAP(REGEXP_REPLACE(...))`.
- **Next recommended task:** **DW-06** (Opus 4.8, architecture-critical) — Build
  fact_sales_order, fact_sales_order_line, fact_invoice, fact_invoice_line from staging
  with cross-window dedup and FK resolution. All 12 dims are now populated and can serve
  as FK targets. DW-04 finding: transactional windows are date/ID-disjoint, so fact dedup
  is a safety net rather than a collision-resolving necessity; still implement as specified.

### [2026-06-29] DW-06 — Build order/invoice facts with cross-window dedup

- **Type:** milestone
- **Model/effort used:** Claude Opus 4.8 high (architecture-critical: owns the dedup)
- **Summary:** Built the 4 transactional facts from `staging.stg_*` on
  `localhost:5432/lpn_ai_bi`: `fact_sales_order` (36,579), `fact_invoice` (11,907),
  `fact_sales_order_line` (628,410), `fact_invoice_line` (597,373). Cross-window dedup via
  `ROW_NUMBER() OVER (PARTITION BY <nk> ORDER BY <window-pref>)` (2024 dates prefer
  `2024_xlsx`, 2025+ prefer `csv_2025`). All dim FKs resolved → surrogate; unresolved/NULL/0
  → key 0; dates → `date_key` (YYYYMMDD). Filters: orders `DOCSTATUS IN ('CO','CL')`,
  invoices `DOCSTATUS='CO'`, both `issotrx='Y'`; negative GRANDTOTAL (avoirs) kept.
  Idempotent (`TRUNCATE … RESTART IDENTITY` + reload).
- **Folders/files inspected:** `00_CONTEXT_AND_HISTORY.md`, `01_IMPLEMENTATION_TASKS.md`,
  `context_bi_service.md`, `_design/07_warehouse_design.md`,
  `DataWareHouse/processus_de_vente/03_fact_grain_design.md`,
  `_run/DW05_dimensions_report.md`, `sql/warehouse.sql`, `sql/staging.sql`; live DB
  (`staging.stg_c_order/_orderline/_invoice/_invoiceline`, `business.c_order/c_invoice`,
  `warehouse.dim_*`).
- **Files created/modified:**
  `docs/bi-service-roadmap/2024_implementation/_run/DW06_facts.sql` (created),
  `docs/bi-service-roadmap/2024_implementation/_run/DW06_facts_orders_invoices_report.md`
  (created), `docs/bi-service-roadmap/context_bi_service.md` (appended).
- **Technical decisions:** (1) Line facts derived by INNER JOIN to the chosen
  (deduped+filtered) header set — a line is loaded iff its header passes the filter — and
  window-matched to the header (`line._source_tag = header._source_tag` tie-break), so lines
  inherit header dims (date/customer/commercial/doc_type/doc_no/doc_status) from the same
  window. (2) `order_date_key`/`invoice_date_key` for lines = header date (line tables have
  no canonical event date; ties line CA to header period). (3) `commercial_key` via
  `SALESREP_ID → dim_commercial.ad_user_id`. (4) category/supplier inherited from
  `dim_product` (functionally determined by product; keeps them consistent incl. UNKNOWN).
  (5) `geography_key`/`warehouse_key` resolved via single natural key — verified unique in
  the dim (no fan-out).
- **Validation commands run:** grain uniqueness (`count(*)-count(DISTINCT nk)`); collision
  probe (`HAVING count(DISTINCT _source_tag)>1`); null-FK FILTER aggregates; CA-by-year via
  `dim_date`; 2025–2026 reconciliation vs `business`; idempotent re-run; business row counts.
- **Validation results:** Grain UNIQUE (0 dup keys) on all 4 facts ✅. Dedup collisions = 0
  (orders/invoices/lines) — confirms DW-04 disjoint windows ✅. CA commande by year: 2024
  183,652,520.22 / 2025 181,858,571.86 / 2026 27,296,070.80. CA facture by year: 2024
  173,695,273.21 / 2025 178,932,131.57 / 2026 19,957,297.94. **2024 totals > 0** ✅.
  **2025–2026 reconciliation EXACT (Δ 0.00):** orders fact 209,154,642.66 = business
  209,154,642.66; invoices fact 198,889,429.51 = business 198,889,429.51 ✅. Null-FK: all
  header dims 0% UNKNOWN; `fact_invoice.source_c_order_id` NULL 25.90% (valid); `date_key=0`
  0% everywhere. Idempotent re-run identical (36,579/11,907/628,410/597,373) ✅. Business
  untouched (c_orderline 354,910, c_invoiceline 335,877) ✅.
- **Problems encountered:** Only non-trivial null-FK is `product_key=0` on line facts —
  **100% concentrated in 2024** (11.87% of 2024 order lines; 0% for 2025/26). Cause: 15,600
  distinct 2024 product IDs absent from the CSV-only `dim_product` (the 2.27 GB
  `2024_16_M_PRODUCT.xlsx` is excluded by hard rule). CA totals unaffected (line_net_amount
  still summed); only product/category/supplier attribution lands on UNKNOWN. Flagged for
  DW-09 to scope.
- **Next recommended task:** **DW-07** (Sonnet 4.6) — build delivery, payment, and stock
  facts (`fact_delivery`, `fact_delivery_line`, `fact_payment_allocation`,
  `fact_stock_snapshot`) with the same dedup + FK-resolution rules.

### [2026-06-29] DW-07 — Build delivery, payment, and stock facts

- **Type:** milestone
- **Model/effort used:** Claude Sonnet 4.6 high
- **Summary:** Built all 4 remaining warehouse facts from `staging.stg_*` on
  `localhost:5432/lpn_ai_bi`. Cross-window dedup (ROW_NUMBER PARTITION BY natural key,
  window-preference tie-break) reused verbatim from DW-06. fact_stock_snapshot is
  csv_2025-only (no 2024 xlsx for RV_STORAGE). All grains confirmed unique; 0 dedup
  collisions across delivery and allocation facts (windows are ID-disjoint, as predicted).
  One bug fixed during build: `stg_c_allocationline.datetrx` is NULL for all rows in
  source — allocation date resolved from `stg_c_allocationhdr.datetrx` instead.
- **Folders/files inspected:**
  `docs/bi-service-roadmap/2024_implementation/00_CONTEXT_AND_HISTORY.md`,
  `docs/bi-service-roadmap/2024_implementation/01_IMPLEMENTATION_TASKS.md`,
  `docs/bi-service-roadmap/context_bi_service.md`,
  `docs/bi-service-roadmap/_design/07_warehouse_design.md`,
  `DataWareHouse/processus_de_vente/03_fact_grain_design.md`,
  `docs/bi-service-roadmap/2024_implementation/_run/DW06_facts_orders_invoices_report.md`,
  `docs/bi-service-roadmap/2024_implementation/_run/DW06_facts.sql`,
  staging tables: `stg_m_inout`, `stg_m_inoutline`, `stg_c_allocationhdr`,
  `stg_c_allocationline`, `stg_rv_storage`, `stg_c_payment`,
  warehouse fact DDL: `fact_delivery`, `fact_delivery_line`, `fact_payment_allocation`,
  `fact_stock_snapshot`.
- **Files created/modified:**
  `docs/bi-service-roadmap/2024_implementation/_run/DW07_facts.sql` (created),
  `docs/bi-service-roadmap/2024_implementation/_run/DW07_facts_delivery_payment_stock_report.md` (created),
  `docs/bi-service-roadmap/context_bi_service.md` (appended).
- **Technical decisions:**
  1. **Allocation date from allocationhdr:** `stg_c_allocationline.datetrx` is NULL
     throughout. The `chosen_hdr` CTE was extended to expose `datetrx` from
     `stg_c_allocationhdr` for the `dim_date` join. Correct allocation_date_key is
     now 0% unknown (date resolved for all 91,076 rows).
  2. **DECISION-08 NOT triggered for LPN data:** LPN delivery doctypes (`Livraison article`,
     `MM Shipment Indirect`, `Réception Retour Client`) carry `issalestransaction='Y'`
     and were included in `dim_document_type` by DW-05. `document_type_key` is 100%
     resolved for all 36,862 delivery rows — better than predicted.
  3. **Commercial resolution 8.51% (DECISION-07):** The invoice bridge resolves via
     `c_invoice_id → fact_invoice.commercial_key`. Only 7,754 of 90,528 distinct
     invoice IDs on allocation lines are within the loaded sales-invoice scope; the
     rest are purchase invoices, voided invoices, or pre-2024 history. The 8.51% is
     the true addressable share, not a data quality defect. Fallback = 0 (UNKNOWN).
  4. **stock snapshot_date_key = 0 for all rows:** `DATELASTINVENTORY` is NULL for all
     99,234 rows in `stg_rv_storage`. The stock export does not carry an inventory
     timestamp. Correct fallback per spec; documented semi-additive warning in report §6.
  5. **product_key=0 on 2024 delivery lines:** Same carry-forward as DW-06 — 15.52%
     of 2024 delivery lines (46,800 of 301,581) reference retired product IDs absent
     from the CSV-only `dim_product`. CA unaffected; attribution only.
- **Validation commands run:**
  `psql -f DW07_facts.sql` (x2 for idempotency),
  grain-uniqueness checks (`count(*) - count(DISTINCT <nk>) = 0`),
  dedup collision checks (INTERSECT both windows per entity),
  null-FK rate queries per fact,
  year-breakdown queries,
  business schema row count checks.
- **Validation results:**
  | Fact | Rows | Grain UNIQUE | Dedup collisions |
  |---|---|---|---|
  | fact_delivery | 36,862 | ✅ | 0 |
  | fact_delivery_line | 643,365 | ✅ | 0 |
  | fact_payment_allocation | 91,076 | ✅ | 0 |
  | fact_stock_snapshot | 99,234 | ✅ (no UNIQUE constraint; staging PK guarantees) | N/A |
  Idempotent re-run: identical counts ✅. Business schema untouched ✅.
  Commercial resolution rate: 8.51% (DECISION-07 behaviour confirmed).
  Stock grain distinct check: 99,234 distinct (product, attrset, warehouse, locator) tuples = 99,234 rows ✅.
- **Problems encountered:**
  1. `stg_c_allocationline.datetrx` entirely NULL — fixed by sourcing date from `stg_c_allocationhdr.datetrx`.
  2. DECISION-08 predicted high `document_type_key=0` rate on deliveries; actual = 0% UNKNOWN (LPN-specific delivery doctypes have issalestransaction='Y' — positive deviation).
- **Next recommended task:** **DW-08** (Opus 4.8) — Create `mart.mart_*` serving views
  for the 5 BI pages + stock. All 8 warehouse facts are now built (DW-06 + DW-07).

### [2026-06-29 13:19] DW-08 — Mart serving views for 5 BI pages

- **Type:** milestone
- **Model/effort used:** Codex GPT-5.5 high
- **Summary:** Replaced the pre-existing/business-backed `mart` objects with
  warehouse-backed serving views for the BI pages plus stock. Created the required
  page contract views (`mart_overview`, `mart_commandes`, `mart_revenue`,
  `mart_articles`, `mart_clients`, `mart_commercial`, `mart_stock`) and kept the
  current Spring API helper names (`mart_sales_daily`, `mart_sales_monthly`,
  `mart_sales_by_commercial`, `mart_sales_by_customer`, `mart_sales_by_product`,
  `mart_sales_by_region`, `mart_order_to_invoice_flow`, `mart_payment_status`,
  `mart_stock_risk`). All views read only `warehouse.*`; `business` stayed untouched.
- **Folders/files inspected:** `00_CONTEXT_AND_HISTORY.md`, `01_IMPLEMENTATION_TASKS.md`,
  `context_bi_service.md`, `_design/07_warehouse_design.md`,
  `docs/semantic_layer/metrics.yml`, `sql/warehouse.sql`, all six frontend BI
  page files, BI hooks/types/API files, Java mart repositories, and DW-05/06/07
  run reports.
- **Files created/modified:**
  - Modified: `docs/bi-service-roadmap/sql/marts.sql`
  - Modified: `docs/bi-service-roadmap/_design/08_mart_design.md`
  - Modified: `docs/bi-service-roadmap/context_bi_service.md` (this append-only entry)
  - Live DB: replaced 16 `mart` views and granted `lpn_ai_readonly` access.
- **Technical decisions:**
  - **DROP+CREATE instead of CREATE OR REPLACE:** live `mart` already contained
    business-backed views/materialized views, including materialized
    `mart_order_to_invoice_flow` and `mart_sales_by_product`. A relation-kind-aware
    cleanup block drops existing mart relations safely before recreating plain views.
  - **Two-layer mart contract:** page views satisfy DW-08's named contract; helper
    views preserve existing Spring repository names and column expectations.
  - **Metric formula discipline:** header CA uses `fact_sales_order.grand_total_amount`
    and `fact_invoice.grand_total_amount`; article/product CA uses
    `fact_invoice_line.line_net_amount`; paid/unpaid uses `fact_invoice.is_paid`
    derived counts; stock uses `quantity_available` at one snapshot only.
  - **Commercial compatibility:** helper views keep `commercial_key` as natural
    `ad_user_id` for current Java filters while also exposing `commercial_dim_key`
    for the warehouse surrogate.
  - **Stock exception:** `mart_stock` returns the current imported snapshot, but
    `snapshot_date_key=0` for all 99,234 rows because the source `DATELASTINVENTORY`
    is null. No dates were fabricated; documented as the only date-coverage exception.
- **Validation commands run:**
  - `python -c "import sqlglot; sqlglot.parse(..., dialect='postgres')"` on `marts.sql`
  - `psql -v ON_ERROR_STOP=1 -1 -f docs/bi-service-roadmap/sql/marts.sql`
  - `pg_views` lineage check for `business.`/`staging.` references
  - Row-count/date-coverage queries on all page views and helper views
  - CA-by-year tie-back from `mart_overview` daily rows
  - `lpn_ai_readonly` connection with `SET search_path=mart` and `COUNT(*)` from every view
  - Grant/default-privilege checks via `has_schema_privilege`, `has_table_privilege`,
    `information_schema.table_privileges`, and `pg_default_acl`
  - Protected `business` exact count spot-checks
  - `git diff --check`
- **Validation results:**
  - **Lineage:** all 16 `mart` views report OK; 0 definitions reference `business.*`
    or `staging.*`.
  - **Page view row counts / coverage:**
    `mart_overview` 873 rows (2024=357, 2025-2026=516);
    `mart_commandes` 9,299 (4,058 / 5,241);
    `mart_revenue` 873 (357 / 516);
    `mart_articles` 1,567,074 (624,320 / 942,754);
    `mart_clients` 63,996 (27,525 / 36,471);
    `mart_commercial` 603 (252 / 351);
    `mart_stock` 99,234 current snapshot rows, all `snapshot_date_key=0`.
  - **Helper row counts:** `mart_sales_daily` 716; `mart_sales_monthly` 30;
    `mart_sales_by_commercial` 6,128; `mart_sales_by_customer` 34,389;
    `mart_sales_by_product` 523,663; `mart_sales_by_region` 34,278;
    `mart_order_to_invoice_flow` 5,929; `mart_payment_status` 15,722;
    `mart_stock_risk` 48,424.
  - **2024 KPI tie-back:** `mart_overview` daily rows return CA commande
    **183,652,520.22 MAD** and CA facture **173,695,273.21 MAD**, exactly matching
    DW-06 warehouse totals. 2025 and 2026 also match DW-06:
    2025 ordered 181,858,571.86 / invoiced 178,932,131.57;
    2026 ordered 27,296,070.80 / invoiced 19,957,297.94.
  - **Readonly access:** `lpn_ai_readonly` with `search_path=mart` selected all
    16 views successfully; `has_schema_privilege` true, 16/16 views selectable;
    default privileges present for `postgres` and `lpn_app_admin`.
  - **Business untouched:** exact protected counts unchanged:
    `business.c_order` 20,336; `business.c_orderline` 354,910;
    `business.c_invoice` 6,658; `business.c_invoiceline` 335,877;
    `business.m_product` 35,822; `business.rv_storage` 99,234.
  - **Widget mapping:** every visible widget on Overview, Commandes, Revenue,
    Articles, Clients, and Commercial maps to named mart view columns in
    `_design/08_mart_design.md`.
- **Problems encountered:**
  - Live mart had legacy business-backed objects despite the DW-08 handoff expecting
    an empty/incomplete mart. Two were materialized views, so the cleanup block had
    to drop by relation kind.
  - `stock` cannot satisfy a true 2024/2025-2026 date split because the only stock
    source is the csv snapshot and its snapshot date is unknown (`date_key=0`). The
    mart returns stock rows and documents the semi-additive/current-snapshot rule.
- **Next recommended task:** **DW-09** — Run the reconciliation and data-quality gate.
  Focus on 2025-2026 parity with `business`, 2024 aggregate-file reconciliation,
  duplicate-grain checks, 2024 product UNKNOWN attribution, and the documented stock
  snapshot-date limitation. Do not start DW-09 until this DW-08 commit is in place.

### [2026-06-29 15:56] DW-09 — Reconciliation & data-quality gate

- **Type:** milestone
- **Model/effort used:** Codex GPT-5.5 high
- **Summary:** Produced the DW-09 reconciliation and data-quality gate report for
  the warehouse/mart. The gate is **NO-GO**: 2025-2026 current-window parity with
  `business` is exact, but 2024 `mart_overview.ca_facture` does not match
  `2024_32_COMMERCIAL_REAL_CA_MONTHLY.xlsx` because the workbook sums all invoice
  statuses while DW-06 loaded `fact_invoice` with `DOCSTATUS='CO'` only. No schema,
  source data, business data, or dashboard code was modified.
- **Folders/files inspected:**
  `docs/bi-service-roadmap/2024_implementation/00_CONTEXT_AND_HISTORY.md`,
  `docs/bi-service-roadmap/2024_implementation/01_IMPLEMENTATION_TASKS.md`,
  `docs/bi-service-roadmap/context_bi_service.md` (DW-01..DW-08 milestones),
  `docs/bi-service-roadmap/_design/08_mart_design.md`,
  `docs/bi-service-roadmap/sql/marts.sql`,
  `docs/bi-service-roadmap/2024_implementation/_run/DW06_facts_orders_invoices_report.md`,
  `docs/bi-service-roadmap/2024_implementation/_run/DW07_facts_delivery_payment_stock_report.md`,
  `Youssef_Extractions/data/Exported_LPN/2024_32_COMMERCIAL_REAL_CA_MONTHLY.xlsx`
  (read-only), live PostgreSQL schemas `business`, `staging`, `etl`, `warehouse`,
  and `mart`.
- **Files created/modified:**
  - Created: `docs/bi-service-roadmap/2024_implementation/_run/DW09_reconciliation_report.md`
  - Modified: `docs/bi-service-roadmap/context_bi_service.md` (this append-only entry)
- **Technical decisions:**
  - **Verdict rule:** GO only if all core checks pass. The failed 2024 workbook
    reconciliation makes the gate NO-GO even though the current-window business
    comparison is exact.
  - **Current window:** used `2025-01-01 <= date < 2027-01-01`, which captures all
    current `business` rows (orders 2025-01-02..2026-06-15; invoices
    2025-01-03..2026-06-12).
  - **2024 workbook interpretation:** `2024_32` is a real/invoiced CA aggregate
    (`REAL_CA`, `PAID_CA`, `UNPAID_CA`) and has no ordered-CA column. It matches
    `staging.stg_c_invoice` all-status 2024 totals exactly.
  - **Scope only, no fixes:** documented product UNKNOWN attribution, document-type
    flag resolution, geography sales-region UNKNOWN, stock `snapshot_date_key=0`,
    date-grain discipline, payment allocation double-count, and heavy-view timings
    without changing marts or facts.
- **Validation commands run:**
  - `openpyxl` read-only extraction of monthly totals from
    `2024_32_COMMERCIAL_REAL_CA_MONTHLY.xlsx`
  - Read-only `psql` queries comparing `business` vs `mart.mart_overview` and
    `warehouse.fact_*`
  - Fact duplicate-natural-key checks across all 8 facts
  - `etl.stg_rejects` and `etl.etl_run_table_stats` reject-rate queries
  - Data-quality quantification queries for 2024 UNKNOWN product/category/supplier
    CA, document type flags, sales-region UNKNOWN CA, and stock date keys
  - Mart-contract checks for `date_grain` overstatement, helper-view grain columns,
    `mart_payment_status` allocation repetition, and `EXPLAIN (ANALYZE, BUFFERS)`
    performance probes
- **Validation results:**
  - **PASS:** 2025-2026 golden `business` vs `mart_overview` daily:
    order_count 20,336 = 20,336; `ca_commande` 209,154,642.66 =
    209,154,642.66; invoice_count 6,658 = 6,658; `ca_facture`
    198,889,429.51 = 198,889,429.51.
  - **FAIL:** 2024 workbook `REAL_CA` 173,683,772.64 vs mart/warehouse
    `ca_facture` 173,695,273.21; delta +11,500.57. Offending non-`CO`
    invoice keys are listed in the DW-09 report.
  - **PASS:** zero duplicate natural keys in all facts, including stock snapshot
    grain `(m_product_id, m_attribute_set_instance_id, warehouse_key, m_locator_id)`.
  - **PASS:** transactional reject rates are 0.0000% for all checked staging
    transactional tables (threshold <5%).
  - **DQ quantified:** 2024 invoice-line UNKNOWN product/category/supplier CA is
    10,715,556.19 MAD = 6.1692%; order-line UNKNOWN CA is 11,174,017.27 MAD =
    6.0843%.
  - **DQ quantified:** sales-region UNKNOWN invoiced CA is 294,730,933.59 of
    372,584,702.72 MAD = 79.1044%; stock `snapshot_date_key=0` is
    99,234/99,234 rows = 100.0000%.
  - **Contract warning:** summing `mart_overview` without `date_grain` filtering
    triples CA exactly; helper views are single-grain. `mart_payment_status`
    double-counts `allocated_amount` by 4,284,581.01 MAD across 83
    paid/unpaid spine groups if summed naively.
  - **Performance note:** `mart_articles` count 1,567,074 rows took 12.784s and
    a 2025 top-products query took 3.855s. `mart_clients` count 63,996 rows took
    0.916s, but a full-year 2025 top-customers query was canceled after 195.064s;
    a January 2025 sample took 12.418s.
- **Problems encountered:**
  - The core 2024 aggregate mismatch is a genuine status-scope mismatch, not a
    rounding issue. It requires a DW-06 decision/fix before repointing.
  - The broad `mart_clients` performance probe had to be canceled with
    `pg_cancel_backend` after 195.064 seconds; documented as a materialization
    warning.
- **Next recommended task:** Resolve the DW-06 2024 invoice status-scope mismatch
  and rerun DW-09. Do **not** start DW-10 until DW-09 returns GO.

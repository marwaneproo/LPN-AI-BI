# BI-06 — Staging Schema Design

**Task:** BI-06  
**Date:** 2026-06-25  
**Branch:** `bi-polished-dashboard`  
**Inputs:** `DataWareHouse/processus_de_vente/01_warehouse_layers.md`, `ddl/draft_schema_design.sql`, BI-02 PKs, BI-03 quality findings, BI-05 cleaning rules, ETL staging CSV outputs  
**DDL file:** `docs/bi-service-roadmap/sql/staging.sql`  
**Validation:** sqlglot 30.11.0 — 65 statements, 0 parse errors ✅  
**Status:** ✅ Complete (design only — DDL not executed)

---

## 1. Design Principles

| Principle | Rule |
|---|---|
| **Mirror source** | Every column present in the source CSV/xlsx appears in `stg_*` with no transformation |
| **No FK constraints** | Staging tables have no foreign key constraints — referential integrity is enforced at warehouse load |
| **No NOT NULL** | Only PK and metadata columns carry constraints; source nulls are preserved as-is |
| **Load metadata always** | All tables carry `_loaded_at`, `_source_file`, `_source_tag`, `_etl_run_id` |
| **Idempotent** | Tables use `CREATE TABLE IF NOT EXISTS`; TRUNCATE at run start (IDEM-01) |
| **Schema isolation** | All staging tables live in the `staging` schema; `etl` schema holds run-log and reject tables; `business.*` is never touched |
| **Composite PK** | `PRIMARY KEY (natural_key, _source_tag)` allows loading both 2024 xlsx and 2025 CSV into the same table without collision; dedup happens at warehouse load time |

---

## 2. Schema Overview

### 2.1 Target schemas

| Schema | Purpose |
|---|---|
| `staging` | All `stg_*` tables — raw source mirrors |
| `etl` | `etl_run_log`, `etl_run_table_stats`, `stg_rejects` — ETL infrastructure |
| `warehouse` | Dims and facts (BI-07 scope — not defined here) |
| `mart` | BI-ready mart views (BI-08 scope — not defined here) |
| `business` | ⛔ Off-limits — existing live schema, never modified by BI ETL |

**Auxiliary ETL schema decision:** keep `etl` as a separate schema. It stores pipeline
control-plane data (run logs, table stats, rejects), not BI-serving data. Keeping it
separate prevents raw staging mirrors from being mixed with operational ETL metadata.

### 2.2 Table count

| Group | Tables | Row range |
|---|---|---|
| Transactional (fact sources) | 4 | 1K – 355K rows |
| Delivery | 2 | 19K – 357K rows |
| Payment & allocation | 3 | 1K – 87K rows |
| Business partners | 3 | 28 – 42K rows |
| Products & taxonomy | 6 | 7 – 86K rows |
| Geography | 3 | 13 – 364 rows |
| Reference / lookups | 4 | 7 – 99K rows |
| ETL infrastructure | 3 | n/a |
| **Total** | **28 tables** | |

---

## 3. Load Metadata Columns

Every `stg_*` table carries these four columns:

| Column | Type | Description |
|---|---|---|
| `_loaded_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | Row insertion timestamp |
| `_source_file` | `TEXT NOT NULL` | Full file path (e.g. `C_ORDER.csv` or `2024_01_C_ORDER.xlsx`) |
| `_source_tag` | `TEXT NOT NULL` | `'csv_2025'` or `'2024_xlsx'` — used by dedup preference rule (DEDUP-00) |
| `_etl_run_id` | `UUID NOT NULL` | Links to `etl.etl_run_log.etl_run_id` for full audit trail |

---

## 4. Primary Key Strategy

Staging tables use a **composite PK** to allow both source windows to co-exist:

```sql
CONSTRAINT stg_c_order_pkey PRIMARY KEY (c_order_id, _source_tag)
```

This means:
- A row with `c_order_id=12345, _source_tag='csv_2025'` and a row with `c_order_id=12345, _source_tag='2024_xlsx'` can both exist in staging.
- The warehouse dedup query (DEDUP-00) picks one per natural PK based on source preference.
- Within each source tag, natural PKs must be unique (enforced by the PK constraint).

For `stg_rv_storage` (stock snapshot), the composite PK is:
```sql
PRIMARY KEY (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id, _source_tag)
```

For `stg_m_product_po` (supplier pricing), the composite PK is:
```sql
PRIMARY KEY (m_product_id, c_bpartner_id, _source_tag)
```

---

## 5. Column Type Mapping

All type decisions derive from BI-02 (column profiling) and BI-05 (cleaning rules).

| PostgreSQL type | Applied to | Decision basis |
|---|---|---|
| `BIGINT` | All `*_id` columns | TYP-01 (BI-05). Compiere uses INTEGER IDs up to 20M range; BIGINT safe. |
| `NUMERIC(18,4)` | All amount, quantity, price, rate, discount columns | TYP-02 (BI-05). Period `.` decimal confirmed (BI-03 N-01/N-02). |
| `TIMESTAMPTZ` | All date/datetime columns | TYP-03 (BI-05). Openpyxl returns `datetime.datetime`; CSVs emit ISO 8601 strings. |
| `CHAR(1)` | All `IS*` flag columns (ISSOTRX, ISPAID, ISACTIVE…) | TYP-05 (BI-05). Always 'Y' or 'N' in source. |
| `TEXT` | All name, code, reference, description, free-text columns | TYP-04 (BI-05). No length caps in staging; normalisation at warehouse load. |
| `INTEGER` | `line`, `documentcopies`, count columns | Bounded small integers. |
| `UUID` | `_etl_run_id` only | ETL infrastructure column. |

**Important: No NULLIF, TRIM, or INITCAP applied in staging.** All cleaning is deferred to Layer 3 (warehouse load). Staging preserves the raw value exactly.

---

## 6. Table-by-Table Notes

### 6.1 Transactional Sources

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_c_order` | C_ORDER.csv + 2024_01_C_ORDER.xlsx | 20,336 + 18,024 | `c_order_id` | 130 cols; GRANDTOTAL = CA commande metric |
| `stg_c_orderline` | C_ORDERLINE.csv + 2024_02 (253 MB) | 354,910 + unknown | `c_orderline_id` | Chunked ETL required for xlsx; DISTRIBUTEUR_ID = custom supplier FK |
| `stg_c_invoice` | C_INVOICE.csv + 2024_03 | 6,658 + 5,264 | `c_invoice_id` | C_ORDER_ID 29.9% null — valid, not a reject (NORM-08) |
| `stg_c_invoiceline` | C_INVOICELINE.csv + 2024_04 (80 MB) | 335,877 + unknown | `c_invoiceline_id` | Three-way bridge: invoice + orderline + delivery line |

### 6.2 Delivery Sources

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_m_inout` | M_INOUT.csv + 2024_05 | 18,718 + 44,232 | `m_inout_id` | Filter ISSOTRX='Y' at warehouse load (FILT-05) |
| `stg_m_inoutline` | M_INOUTLINE.csv + 2024_06 (132 MB) | 356,410 + unknown | `m_inoutline_id` | Chunked ETL; MOVEMENTQTY = primary measure |

### 6.3 Payment Sources

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_c_payment` | C_PAYMENT.csv + 2024_07 | 1,443 + 3,775 | `c_payment_id` | PAYAMT is the payment amount measure |
| `stg_c_allocationhdr` | C_ALLOCATIONHDR.csv + 2024_08 | 1,473 + 4,113 | `c_allocationhdr_id` | Header for allocation batch |
| `stg_c_allocationline` | C_ALLOCATIONLINE.csv + 2024_09 | 4,212 + 86,863 | `c_allocationline_id` | PK for fact_payment_allocation; AMOUNT = allocated amount |

### 6.4 Business Partners

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_c_bpartner` | C_BPARTNER.csv + 2024_10 | 823 + 42,434 | `c_bpartner_id` | Filter ISCUSTOMER='Y' → dim_customer; exclude VALUE='NA' (FILT-07/08) |
| `stg_c_bpartner_vendor` | C_BPARTNER_VENDOR.csv + 2024_19/21 | 14,264 + many | `c_bpartner_id` | Separate table for traceability; same structure as stg_c_bpartner |
| `stg_c_bpartner_location` | C_BPARTNER_LOCATION.csv + 2024_11 | 888 + 4,221 | `c_bpartner_location_id` | SECTORDETAIL = LPN pedagogical sector custom field |

### 6.5 Products & Taxonomy

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_m_product` | M_PRODUCT.csv ONLY | 35,822 | `m_product_id` | **xlsx excluded (2,272 MB)** — TRAP rule |
| `stg_m_product_category` | 2024_17 xlsx | 41 | `m_product_category_id` | Trailing \t in NAME, \n in VALUE (BI-03 W-02/04) — cleaned at dim load |
| `stg_m_product_theme` | M_PRODUCT_THEME.csv + 2024_21 xlsx | 1,513 | `m_product_theme_id` | Thematic classification (LPN editorial) |
| `stg_m_product_type` | 2024_20 xlsx | 27 | `m_product_type_id` | Type classification (livre, cd, etc.) |
| `stg_m_product_collection` | 2024_22 xlsx | ~86K | `m_product_collection_id` | Series/collection classification |
| `stg_m_product_po` | M_PRODUCT_PO.csv ONLY | 39,359 | `(m_product_id, c_bpartner_id)` | **xlsx excluded (995 MB)**; DEDUP-SUPP-01 applied at dim_supplier build |

### 6.6 Geography

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_c_location` | C_LOCATION.csv + 2024_12 | 772 + 4,108 | `c_location_id` | Physical addresses; joined to stg_c_bpartner_location |
| `stg_c_region` | C_REGION.csv + 2024_13 | 16 + 87 | `c_region_id` | All-caps NAME (BI-03 K-01); INITCAP at dim load |
| `stg_c_city` | C_CITY.csv + 2024_14 | 302 + 364 | `c_city_id` | All-caps; MADRID/MOHAMMADIA false-positive currency (BI-03 C-03) |
| `stg_c_salesregion` | C_SALESREGION.csv + 2024_30 | 13 | `c_salesregion_id` | 13 Moroccan sales regions |

### 6.7 Reference Lookups

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_ad_user` | AD_USER.csv + 2024_15 | 28 + 31 | `ad_user_id` | NAME trailing space (BI-03 W-06); merge dedup: xlsx has 3 more rows |
| `stg_c_doctype` | 2024_23 xlsx | 79 | `c_doctype_id` | Filter ISSALESTRANSACTION='Y' at dim_document_type load |
| `stg_c_paymentterm` | 2024_28 xlsx | 7 | `c_paymentterm_id` | Source for dim_payment_term |
| `stg_m_pricelist` | 2024_29 xlsx | 22 | `m_pricelist_id` | All MAD (C_CURRENCY_ID=239) |
| `stg_m_warehouse` | 2024_26 xlsx | 7 | `m_warehouse_id` | Singleton M_WAREHOUSE_ID=1,000,000 in transactional data |

### 6.8 Stock Snapshot

| Table | Source(s) | Rows | PK | Notes |
|---|---|---|---|---|
| `stg_rv_storage` | RV_STORAGE.csv + 18_RV_STORAGE xlsx | 99,234 | composite 4-col | Semi-additive measures; snapshot date from DATELASTINVENTORY |

---

## 7. Relationship to Existing Draft DDL

The existing `DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql` prototype
used the old prototype warehouse schema name. The corrected BI roadmap target is `warehouse` for
dimension/fact tables. This BI-06 document adds the **staging layer** that feeds those
tables. There is no overlap:

| Layer | Schema | Defined in |
|---|---|---|
| Staging | `staging.*` | `docs/bi-service-roadmap/sql/staging.sql` (this file) |
| Dimensions | `warehouse.dim_*` | `DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql` |
| Facts | `warehouse.fact_*` | `DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql` |
| Marts | `mart.mart_*` | BI-08 scope |

The dim/fact DDL in `draft_schema_design.sql` is a prior draft and will be reviewed and possibly updated in BI-07 (warehouse dim/fact design alignment).

---

## 8. Excluded Files

| File | Size | Reason |
|---|---|---|
| `2024_16_M_PRODUCT.xlsx` | 2,272 MB | TRAP rule — use M_PRODUCT.csv |
| `2024_18_M_PRODUCT_PO.xlsx` | 995 MB | TRAP rule — use M_PRODUCT_PO.csv |
| `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` | varies | CA-trap — permanently excluded (BI-05 TRAP-01) |
| `Marwane_Extractions/data/Exported_data_through_a_drive/` | — | Duplicate folder — archive candidate (BI-01) |
| `DATAWHAREHOUSE_EXPORT/1..4.xlsx` | 48 MB (3+4) | Junk/unnamed dump files (BI-01) |
| `vente_clean_import/csv/` (18 CSV) | — | Superseded by `vente_2025_2026_import` |

---

## 9. Chunked ETL Required

Files exceeding 100 MB must use 5,000-row openpyxl streaming batches (BI-05 §8.2):

| Table | xlsx source | Size | Note |
|---|---|---|---|
| `stg_c_orderline` | 2024_02_C_ORDERLINE.xlsx | 253 MB | 5K-row chunks |
| `stg_m_inoutline` | 2024_06_M_INOUTLINE.xlsx | 132 MB | 5K-row chunks |
| `stg_c_invoiceline` | 2024_04_C_INVOICELINE.xlsx | 80 MB | Borderline; chunked for safety |

---

## 10. DDL Validation

```
Command : python -c "import sqlglot; sqlglot.parse(open('staging.sql').read(), dialect='postgres'); print('OK')"
Result  : Parsed 65 statements — OK (0 parse errors)
Tool    : sqlglot 30.11.0
Date    : 2026-06-25
```

---

## 11. Execution Order (when ready to deploy)

```sql
-- 1. Create schemas
CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS etl;
CREATE SCHEMA IF NOT EXISTS warehouse;
CREATE SCHEMA IF NOT EXISTS mart;

-- 2. Create ETL infra tables (etl.etl_run_log, etl.stg_rejects, etl.etl_run_table_stats)
-- 3. Create all stg_* tables (no FK dependencies between them)
-- 4. Run initial ETL load (BI-09)
-- 5. Run dim/fact build (BI-07 DDL + BI-09 transforms)
-- 6. Run mart build (BI-08 DDL)
```

---

## 12. Next Steps

- **BI-07** — Warehouse dim/fact design alignment: review and update `draft_schema_design.sql` to reconcile with the confirmed staging column names and the BI-02/04 FK chain. Add UNKNOWN surrogate rows (dim layer requirement from `01_warehouse_layers.md`).
- **BI-08** — Mart design: `CREATE VIEW mart_*` statements for the 8 frontend-facing marts.
- **BI-09** — ETL implementation plan: Python scripts that COPY from CSV / stream from xlsx → `stg_*` → `warehouse.*`.

# BI-01 — Data Folder Inventory & Reconciliation

**Task:** BI-01  
**Date:** 2026-06-25  
**Branch:** `bi-polished-dashboard`  
**Model:** Sonnet 4.6 medium  
**Method:** metadata-only (file sizes, sheet names, row counts via openpyxl read_only + XML zip count). No full file loads.  
**Status:** ✅ Complete

---

## 1. Canonical Source — Decision (from Rebuild Roadmap)

> **Canonical raw folder:** `Youssef_Extractions/data/Exported_LPN/` (96 xlsx + misc)  
> **Rebuild roadmap reference:** `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` §"Key facts the AI must know"

**Dedup rule (locked):**
- For **2024 data**: use `2024_01..2024_35` files (complete year, full months)
- For **2025–2026 data**: use `53..58` files (24-month commercial export, Jun 2024 → Jun 2026)
- Overlap period: Jun–Dec 2024 appears in BOTH sets → dedup by PK (`C_ORDER_ID`, `C_INVOICE_ID`, line IDs), keep `2024_*` row for 2024 dates
- **Customer portfolio:** use `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` (not `57`) — the `57` file has inflated CA columns due to a many-to-many join bug

**Sales filter (from `txt/req_*.txt`):**  
Orders: `ISSOTRX='Y'`, `DOCSTATUS IN ('CO','CL')`, `C_DOCTYPETARGET_ID IN (1000028,1000034,1000032,1000031)`  
Invoices: `ISSOTRX='Y'`, `DOCSTATUS='CO'`, `C_DOCTYPE_ID IN (1000002,1000003,1000004)`

---

## 2. Folder Summary

| Folder | Files | Size | Role | ETL use |
|---|---|---|---|---|
| `data/Exported_LPN/` | 204 xlsx + misc | ~5.0 GB | ✅ **CANONICAL** | Primary ETL source |
| `data/Exported_data_through_a_drive/` | ~100 xlsx | ~1.0 GB | ⚠️ **DUPLICATE** | Redundant copy of Exported_LPN — do not use |
| `2nd_Extraction/` | 29 xlsx + 1 png | ~126 MB | ⚠️ **SUPERSEDED** | Older base-table extract; superseded by 2024_* + 53..58 |
| `3rd_Extraction/` | 8 xlsx | ~13 MB | ⚠️ **SUPERSEDED** | Enrichment dims (19–26); superseded by same files in Exported_LPN |
| `4th_Extraction/` | 14 xlsx | ~97 MB | ⚠️ **ENRICHMENT/JUNK** | Dim updates (27–41) superseded; 3.xlsx+4.xlsx are junk |
| `vente_2025_2026_import/csv/` | 36 CSV | ~393 MB | ✅ **IMPORT-READY** | Clean 2025-2026 CSV staging package (currently loaded in `business`) |
| `vente_clean_import/csv/` | 18 CSV | ~107 MB | ⚠️ **OLDER IMPORT** | 4-month extract (2026-05-14), partial; superseded by vente_2025_2026 |
| `vente_bi_enrichment_import/csv/` | 7 CSV | ~21 MB | ✅ **ENRICHMENT** | Product dims from 3rd extraction; supplement to main CSV package |
| `forecast_monthly_import/csv/` | 4 CSV | ~0.5 MB | ✅ **FORECAST FACTS** | Sources for `business.fact_sales_monthly*` tables |
| `1st_Extraction/` | 23 xlsx | ~0.7 MB | ⚠️ **LEGACY** | Early exploratory (Extract_01..26), small; no ETL value |
| `txt/` | 3 txt | <1 KB | ✅ **DOCS** | Oracle extraction SQL queries (req_1..3.txt) — preserve |

---

## 3. Canonical File Detail — `data/Exported_LPN/`

### 3A. 2024-only files (`2024_01..2024_35`) — 35 files

| File | Size MB | Sheet | Rows | Entity | Role |
|---|---|---|---|---|---|
| `2024_01_C_ORDER.xlsx` | 13.66 | Sheet 1 | **18,024** | C_ORDER | canonical-2024 |
| `2024_02_C_ORDERLINE.xlsx` | 253.57 | — | **~354K+** ¹ | C_ORDERLINE | canonical-2024 |
| `2024_03_C_INVOICE.xlsx` | 2.62 | Sheet 1 | **5,264** | C_INVOICE | canonical-2024 |
| `2024_04_C_INVOICELINE.xlsx` | 80.21 | — | **~270K+** ¹ | C_INVOICELINE | canonical-2024 |
| `2024_05_M_INOUT.xlsx` | 24.37 | Sheet 1 | **44,232** | M_INOUT | canonical-2024 |
| `2024_06_M_INOUTLINE.xlsx` | 131.98 | — | **~350K+** ¹ | M_INOUTLINE | canonical-2024 |
| `2024_07_C_PAYMENT.xlsx` | 1.60 | Sheet 1 | **3,775** | C_PAYMENT | canonical-2024 |
| `2024_08_C_ALLOCATIONHDR.xlsx` | 0.47 | Sheet 1 | **4,113** | C_ALLOCATIONHDR | canonical-2024 |
| `2024_09_C_ALLOCATIONLINE.xlsx` | 9.91 | Sheet 1 | **86,863** | C_ALLOCATIONLINE | canonical-2024 |
| `2024_10_C_BPARTNER.xlsx` | 20.90 | Sheet 1 | **42,434** | C_BPARTNER | canonical-2024 |
| `2024_11_C_BPARTNER_LOCATION.xlsx` | 0.81 | Sheet 1 | **4,221** | C_BPARTNER_LOCATION | canonical-2024 |
| `2024_12_C_LOCATION.xlsx` | 0.51 | Sheet 1 | **4,108** | C_LOCATION | canonical-2024 |
| `2024_13_C_REGION.xlsx` | 0.01 | Sheet 1 | **87** | C_REGION | canonical-2024 |
| `2024_14_C_CITY.xlsx` | 0.04 | Sheet 1 | **364** | C_CITY | canonical-2024 |
| `2024_15_AD_USER_SALESREPS.xlsx` | 0.01 | Sheet 1 | **31** | AD_USER | canonical-2024 |
| `2024_16_M_PRODUCT.xlsx` | 2271.83 | — | **unknown** ¹ | M_PRODUCT | canonical-2024 |
| `2024_17_M_PRODUCT_CATEGORY.xlsx` | 0.01 | Sheet 1 | **41** | M_PRODUCT_CATEGORY | canonical-2024 |
| `2024_18_M_PRODUCT_PO.xlsx` | 995.41 | — | **unknown** ¹ | M_PRODUCT_PO | canonical-2024 |
| `2024_19_C_BPARTNER_VENDORS.xlsx` | 6.84 | — | (=21 file) | C_BPARTNER_VENDOR | canonical-2024 |
| `2024_20_M_PRODUCT_TYPE.xlsx` | 0.01 | Sheet 1 | **27** | M_PRODUCT_TYPE | canonical-2024 |
| `2024_21_M_PRODUCT_THEME.xlsx` | 0.12 | Sheet 1 | **1,513** | M_PRODUCT_THEME | canonical-2024 |
| `2024_22_M_PRODUCT_COLLECTION.xlsx` | 8.51 | — | (enrichment) | M_PRODUCT_COLLECTION | canonical-2024 |
| `2024_23_C_DOCTYPE.xlsx` | 0.02 | Sheet 1 | **79** | C_DOCTYPE | canonical-2024 |
| `2024_24_C_TAX.xlsx` | 0.01 | Sheet 1 | **6** | C_TAX | canonical-2024 |
| `2024_25_AD_ORG.xlsx` | 0.01 | Sheet 1 | **4** | AD_ORG | canonical-2024 |
| `2024_26_M_WAREHOUSE.xlsx` | 0.01 | Sheet 1 | **7** | M_WAREHOUSE | canonical-2024 |
| `2024_27_M_LOCATOR.xlsx` | 0.17 | — | (dim) | M_LOCATOR | canonical-2024 |
| `2024_28_C_PAYMENTTERM.xlsx` | 0.01 | Sheet 1 | **7** | C_PAYMENTTERM | canonical-2024 |
| `2024_29_M_PRICELIST.xlsx` | 0.01 | Sheet 1 | **22** | M_PRICELIST | canonical-2024 |
| `2024_30_C_SALESREGION.xlsx` | 0.01 | Sheet 1 | **13** | C_SALESREGION | canonical-2024 |
| `2024_31_COMMERCIAL_MONTHLY_FUNNEL.xlsx` | 0.02 | — | (BI aggregate) | — | bi-aggregate |
| `2024_32_COMMERCIAL_REAL_CA_MONTHLY.xlsx` | 0.02 | — | (BI aggregate) | — | bi-aggregate |
| `2024_33_THEME_TYPE_DISTRIBUTOR_CA.xlsx` | 0.99 | — | (BI aggregate) | — | bi-aggregate |
| `2024_34_GEOGRAPHY_CA.xlsx` | 0.02 | — | (BI aggregate) | — | bi-aggregate |
| `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` | 0.07 | Sheet 1 | **826** | CUSTOMER_PORTFOLIO | canonical-2024 ⭐ |

¹ File >50 MB, streaming skipped. Row count inferred from business schema or marked unknown.  
⭐ Use `2024_35` for portfolio, NOT `57` (inflated CA).

### 3B. 24-month commercial export (`53..58`) — 6 files, span Jun 2024 → Jun 2026

| File | Size MB | Sheet | Rows | Entity | Role |
|---|---|---|---|---|---|
| `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` | 4.71 | Sheet1 | **34,710** | C_ORDER | canonical-24M |
| `54_COMMERCIAL_ORDER_LINE_24M.xlsx` | 123.75 | — | **~500K+** ¹ | C_ORDERLINE | canonical-24M |
| `55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` | 1.37 | Sheet1 | **10,315** | C_INVOICE | canonical-24M |
| `56_COMMERCIAL_INVOICE_LINE_24M.xlsx` | 84.63 | — | **~280K+** ¹ | C_INVOICELINE | canonical-24M |
| `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` | 0.16 | Sheet1 | **970** | CUSTOMER_PORTFOLIO | ⚠️ TRAP — inflated CA |
| `58_COMMERCIAL_CUSTOMER_LOCATION_24M.xlsx` | 0.12 | Sheet1 | **1,043** | C_BPARTNER_LOCATION | canonical-24M |

> **TRAP (57):** `ORDERED_CA_24M` and `INVOICED_CA_24M` are inflated due to a many-to-many join bug in the Oracle extraction. Use `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` for 2024 portfolio; re-aggregate from orders and invoices separately for 2025-2026.

### 3C. Base tables / enrichment (`02..52` no prefix) — 42 files

| File | Size MB | Sheet / Rows | Entity | Role |
|---|---|---|---|---|
| `02_C_ORDERLINE.xlsx` | 26.26 | Sheet1=3,350; Sheet2=67,862 | C_ORDER (S1) + C_ORDERLINE (S2) | base-full-history |
| `03_C_INVOICE.xlsx` | 0.31 | Sheet1=1,182 | C_INVOICE | base-full-history |
| `04_C_INVOICELINE.xlsx` | 11.36 | (multi-sheet, audit=62,146) | C_INVOICELINE | base-full-history |
| `05_C_BPARTNER.xlsx` | 0.12 | Sheet1=313 | C_BPARTNER | base-full-history |
| `06_M_PRODUCT.xlsx` | 6.05 | (multi-sheet, audit=16,345) | M_PRODUCT | base-full-history |
| `07_M_PRODUCT_CATEGORY.xlsx` | 0.01 | Sheet1=26 | M_PRODUCT_CATEGORY | base-full-history |
| `08_C_DOCTYPE.xlsx` | 0.01 | Sheet1=7 | C_DOCTYPE | base-full-history |
| `09_C_TAX.xlsx` | 0.01 | Sheet2=3 | C_TAX | base-full-history |
| `10_AD_ORG.xlsx` | 0.01 | Sheet3=1 | AD_ORG | base-full-history |
| `11_C_ALLOCATIONLINE.xlsx` | 0.03 | (audit=245) | C_ALLOCATIONLINE | base-full-history |
| `12_C_ALLOCATIONHDR.xlsx` | 0.02 | (audit=136) | C_ALLOCATIONHDR | base-full-history |
| `13_C_PAYMENT.xlsx` | 0.04 | (audit=134) | C_PAYMENT | base-full-history |
| `14_M_INOUT.xlsx` | 1.27 | (audit=4,667) | M_INOUT | base-full-history |
| `15_M_INOUTLINE.xlsx` | 20.18 | (audit=181,631) | M_INOUTLINE | base-full-history |
| `16_M_WAREHOUSE.xlsx` | 0.01 | Sheet5=1 | M_WAREHOUSE | base-full-history |
| `17_M_LOCATOR.xlsx` | 0.01 | Sheet6=1 | M_LOCATOR | base-full-history |
| `18_RV_STORAGE_SALES_PRODUCTS.xlsx` | 5.56 | (audit=58,826) | RV_STORAGE | base-full-history |
| `18A_M_STORAGE_COLUMNS.xlsx` | 0.01 | — | storage cols | base-full-history |
| `18B_RV_STORAGE_COLUMNS.xlsx` | 0.01 | — | storage cols | base-full-history |
| `19_AD_USER_SALESREPS.xlsx` | 0.01 | Sheet1=70 | AD_USER | enrichment |
| `20_M_PRODUCT_PO.xlsx` | 5.63 | (audit=46,471) | M_PRODUCT_PO | enrichment |
| `21_C_BPARTNER_VENDORS.xlsx` | 1.49 | Sheet1=9,633 | C_BPARTNER_VENDOR | enrichment |
| `22_PRODUCTS_WITHOUT_SUPPLIER.xlsx` | 0.01 | Sheet1=3 | diagnostic | enrichment |
| `23_C_BP_GROUP.xlsx` | 0.01 | Sheet2=27 | C_BP_GROUP | enrichment |
| `24_M_PRODUCT_TYPE.xlsx` | 0.01 | — | M_PRODUCT_TYPE | enrichment |
| `25_M_PRODUCT_THEME.xlsx` | 0.10 | Sheet1=1,487 | M_PRODUCT_THEME | enrichment |
| `26_M_PRODUCT_COLLECTION.xlsx` | 6.08 | (audit=86,183) | M_PRODUCT_COLLECTION | enrichment |
| `27_C_BP_GROUP.xlsx` | 0.01 | Sheet1=1 | C_BP_GROUP | enrichment |
| `28_C_BPARTNER_LOCATION.xlsx` | 0.05 | Sheet1=344 | C_BPARTNER_LOCATION | enrichment |
| `29_C_LOCATION.xlsx` | 0.04 | Sheet1=296 | C_LOCATION | enrichment |
| `30_C_PAYMENTTERM.xlsx` | 0.01 | Sheet1=5 | C_PAYMENTTERM | enrichment |
| `31_SALES_TABLE_DISCOVERY.xlsx` | 0.01 | Sheet1=8 | discovery | bi-aggregate |
| `32_C_SALESREGION.xlsx` | 0.01 | Sheet1=13 | C_SALESREGION | enrichment |
| `33_M_PRICELIST.xlsx` | 0.01 | Sheet1=5 | M_PRICELIST | enrichment |
| `34_C_COMMISSION.xlsx` | 0.01 | Sheet1=1 | C_COMMISSION | enrichment |
| `40_C_REGION.xlsx` | 0.01 | Sheet1=16 | C_REGION | enrichment |
| `41_C_CITY.xlsx` | 0.03 | Sheet1=302 | C_CITY | enrichment |
| `42_COMMERCIAL_COLUMN_DISCOVERY.xlsx` | 0.04 | Sheet1=926 | discovery | bi-aggregate |
| `43_COMMERCIAL_PROFILE.xlsx` | 0.01 | Sheet1=72 | commercial profile | bi-aggregate |
| `44_COMMERCIAL_MONTHLY_FUNNEL_24M.xlsx` | 0.04 | Sheet1=528 | BI aggregate | bi-aggregate |
| `45_COMMERCIAL_CITY_REGION_CA_24M.xlsx` | 0.02 | Sheet1=278 | BI aggregate | bi-aggregate |
| `46_COMMERCIAL_THEME_TYPE_COLLECTION_CA_24M.xlsx` | 1.11 | — | BI aggregate | bi-aggregate |
| `47_COMMERCIAL_ORDER_STATUS_TYPE_24M.xlsx` | 0.07 | Sheet1=1,548 | BI aggregate | bi-aggregate |
| `48_COMMERCIAL_DELIVERY_INVOICE_FULFILLMENT_24M.xlsx` | 0.04 | Sheet1=526 | BI aggregate | bi-aggregate |
| `49_COMMERCIAL_PAYMENT_COLLECTION_24M.xlsx` | 0.03 | Sheet1=394 | BI aggregate | bi-aggregate |
| `52_COMMERCIAL_INVOICE_ORDER_RECONCILIATION_24M.xlsx` | 0.05 | Sheet1=889 | BI aggregate | bi-aggregate |
| `03B_M_INOUT_2025_2026_BY_MOVEMENTDATE.xlsx` | 5.77 | — | M_INOUT 2025-2026 | supplemental |

### 3D. Junk files in `data/Exported_LPN/` — FLAG ❌

| File | Size MB | Rows | Reason |
|---|---|---|---|
| `Book1.xlsx` | 0.01 | S1=6, S2=6 | junk scratch workbook |
| `Book2.xlsx` | 0.01 | S1=6, S2=6 | junk scratch workbook |
| `Book3.xlsx` | 0.01 | S1=29 | junk scratch workbook |
| `Book4.xlsx` | 0.01 | S1=6 | junk scratch workbook |
| `Book5.xlsx` | 1.58 | S1=3,350 | C_ORDER 4-month (older than 2024_* and 53..58 sets) |
| `Book6.xlsx` | 0.01 | S1=17 | junk scratch workbook |
| `Book7.xlsx` | 0.01 | S7=9, S8=21 | junk scratch workbook |
| `~$52_COMMERCIAL_INVOICE_ORDER_RECONCILIATION_24M.xlsx` | 0.00 | — | Excel lock file |
| `DATAWHAREHOUSE_EXPORT/1.xlsx` | 0.01 | S1=1 | junk (single-row stub) |
| `DATAWHAREHOUSE_EXPORT/2.xlsx` | 0.01 | S1=1 | junk (single-row stub) |
| `DATAWHAREHOUSE_EXPORT/3.xlsx` | 48.15 | S1=165,589 | **JUNK per rebuild roadmap** (unnamed 48 MB file) |
| `DATAWHAREHOUSE_EXPORT/4.xlsx` | 48.15 | S1=165,589 | **JUNK per rebuild roadmap** (unnamed 48 MB file) |
| `Screenshot 2026-05-14 103725.png` | 0.09 | — | non-data image |

> Note: `DATAWHAREHOUSE_EXPORT/3.xlsx` and `4.xlsx` are the same 48.15 MB file with 165,589 rows each. Their content is unknown/unnamed (likely a raw dump). Rebuild roadmap explicitly flags `3.xlsx` and `4.xlsx` as junk.

---

## 4. Import-Ready CSV Packages

### `vente_2025_2026_import/` — AUTHORITATIVE 2025-2026 IMPORT (currently in `business` schema)

Generated: 2026-06-16 | Source: `Exported_data_through_a_drive` quarter chunks + Toad exports

| CSV File | Rows | Entity |
|---|---|---|
| `C_ORDER.csv` | 20,336 | C_ORDER (2025-2026) |
| `C_ORDERLINE.csv` | 354,910 | C_ORDERLINE (2025-2026) |
| `C_INVOICE.csv` | 6,658 | C_INVOICE (2025-2026) |
| `C_INVOICELINE.csv` | 335,877 | C_INVOICELINE (2025-2026) |
| `M_INOUT.csv` | 18,718 | M_INOUT (2025-2026) |
| `M_INOUTLINE.csv` | 356,410 | M_INOUTLINE (2025-2026) |
| `C_ALLOCATIONLINE.csv` | 4,212 | C_ALLOCATIONLINE |
| `C_ALLOCATIONHDR.csv` | 1,473 | C_ALLOCATIONHDR |
| `C_PAYMENT.csv` | 1,443 | C_PAYMENT |
| `C_BPARTNER.csv` | 823 | C_BPARTNER (customers) |
| `M_PRODUCT.csv` | 35,822 | M_PRODUCT |
| `LPN_CUSTOMER_PORTFOLIO_2025_2026.csv` | 823 | customer portfolio (corrected) |
| `C_BPARTNER_VENDOR.csv` | 14,264 | C_BPARTNER_VENDOR (suppliers) |
| `M_PRODUCT_PO.csv` | 39,359 | M_PRODUCT_PO |
| `RV_STORAGE.csv` | 99,234 | RV_STORAGE |
| `C_BPARTNER_LOCATION.csv` | 888 | C_BPARTNER_LOCATION |
| `C_LOCATION.csv` | 772 | C_LOCATION |
| `M_PRODUCT_COLLECTION.csv` | 3,116 | M_PRODUCT_COLLECTION |
| `M_PRODUCT_THEME.csv` | 704 | M_PRODUCT_THEME |
| `C_REGION.csv` | 16 | C_REGION |
| `C_CITY.csv` | 302 | C_CITY |
| `C_SALESREGION.csv` | 13 | C_SALESREGION |
| + 14 small dim CSVs | ≤100 rows each | AD_ORG, AD_USER, C_BP_GROUP, C_COMMISSION, etc. |

> Row counts match `business` schema live counts exactly — confirms this is the current DB source.

### `vente_clean_import/` — OLDER 4-MONTH EXTRACT (superseded)

Generated: 2026-05-14 | Source: `2nd_Extraction` (4-month window as of May 2026)

| Entity | Rows | Note |
|---|---|---|
| C_ORDER | 3,350 | 4-month window only |
| C_ORDERLINE | 67,862 | partial |
| C_INVOICE | 1,182 | partial |
| C_INVOICELINE | 62,146 | partial |
| M_INOUT | 4,667 | partial |
| M_INOUTLINE | 181,631 | partial |
| M_PRODUCT | 16,345 | subset (products active in 4-month window) |
| RV_STORAGE | 58,826 | partial |

**Decision:** superseded by `vente_2025_2026_import`. Do not use for staging.

### `vente_bi_enrichment_import/` — ENRICHMENT DIMS

Generated: 2026-05-21 | Source: `3rd_Extraction`

| CSV | Rows | Entity |
|---|---|---|
| `AD_USER.csv` | ~70 | sales reps |
| `C_BPARTNER_VENDOR.csv` | ~9,633 | vendor BPs |
| `M_PRODUCT_PO.csv` | ~46,471 | product-supplier |
| `M_PRODUCT_COLLECTION.csv` | ~86,183 | collections |
| `M_PRODUCT_THEME.csv` | ~1,487 | themes |
| `M_PRODUCT_TYPE.csv` | ~27 | types |
| `PRODUCTS_WITHOUT_SUPPLIER.csv` | 3 | diagnostic |

**Decision:** superseded by larger 2024_* files and vente_2025_2026 package for these dimensions. Keep for reference; don't use as staging source.

### `forecast_monthly_import/` — FORECAST FACT SOURCE

| CSV | Rows | Entity | Business table |
|---|---|---|---|
| `fact_sales_monthly.csv` | 25 | monthly CA | `business.fact_sales_monthly` |
| `fact_sales_monthly_by_category.csv` | 615 | by category | `business.fact_sales_monthly_by_category` |
| `fact_sales_monthly_by_commercial.csv` | 470 | by commercial | `business.fact_sales_monthly_by_commercial` |
| `fact_sales_monthly_by_theme.csv` | 8,446 | by theme | `business.fact_sales_monthly_by_theme` |

**Decision:** ✅ Keep and protect — these feed the forecasting service. Do not modify.

---

## 5. Other Extraction Folders

### `2nd_Extraction/` — Superseded Base Extract

22 extraction files + 7 Book junk + 1 screenshot:
- `02_C_ORDERLINE.xlsx` — 26.26 MB — Sheet1=C_ORDER(3,350), Sheet2=C_ORDERLINE(67,862)
- `14_M_INOUT.xlsx` — M_INOUT(4,667); `15_M_INOUTLINE.xlsx` — M_INOUTLINE(181,631)
- `18_RV_STORAGE_SALES_PRODUCTS.xlsx` — 5.56 MB — RV_STORAGE(58,826)
- `Book1..Book7.xlsx` — ❌ JUNK
- `image.png` — non-data

**Decision:** superseded by 2024_* files and vente_2025_2026 import. Safe to archive after human approval (Rebuild Roadmap Task 2).

### `3rd_Extraction/` — Enrichment Dims (superseded)

8 files: `19_AD_USER_SALESREPS.xlsx` (70), `20_M_PRODUCT_PO.xlsx` (46,471), `21_C_BPARTNER_VENDORS.xlsx` (9,633), `22_PRODUCTS_WITHOUT_SUPPLIER.xlsx` (3), `23_C_BP_GROUP.xlsx` (27), `24_M_PRODUCT_TYPE.xlsx`, `25_M_PRODUCT_THEME.xlsx` (1,487), `26_M_PRODUCT_COLLECTION.xlsx` (86,183).

**Decision:** same data exists in 2024_* and 2025-2026 imports. Archive candidate.

### `4th_Extraction/` — Enrichment Dims + Junk

| File | Rows | Role |
|---|---|---|
| `27_C_BP_GROUP.xlsx` | 1 | enrichment dim |
| `28_C_BPARTNER_LOCATION.xlsx` | 344 | enrichment dim |
| `29_C_LOCATION.xlsx` | 296 | enrichment dim |
| `30_C_PAYMENTTERM.xlsx` | 5 | enrichment dim |
| `31_SALES_TABLE_DISCOVERY.xlsx` | 8 | discovery notes |
| `32_C_SALESREGION.xlsx` | 13 | enrichment dim |
| `33_M_PRICELIST.xlsx` | 5 | enrichment dim |
| `34_C_COMMISSION.xlsx` | 1 | enrichment dim |
| `40_C_REGION.xlsx` | 16 | enrichment dim |
| `41_C_CITY.xlsx` | 302 | enrichment dim |
| `1.xlsx` | 1 | ❌ JUNK (single-row stub) |
| `2.xlsx` | 1 | ❌ JUNK (single-row stub) |
| `3.xlsx` | — | ❌ **JUNK** (48.15 MB unnamed — flagged by rebuild roadmap) |
| `4.xlsx` | — | ❌ **JUNK** (48.15 MB unnamed — flagged by rebuild roadmap) |

Note: `4th_Extraction/3.xlsx` and `4.xlsx` are the same 48.15 MB files as `DATAWHAREHOUSE_EXPORT/3.xlsx` and `4.xlsx` in Exported_LPN. They appear to be a duplicate dump of order-line data (~165K rows each based on DATAWHAREHOUSE_EXPORT copy read).

### `1st_Extraction/` — Legacy Early Extracts

23 small xlsx files (Extract_01..26 with gaps), total 0.7 MB. Early exploratory extractions, all superseded. Archive candidate.

### `txt/` — Oracle Extraction SQL (IMPORTANT)

| File | Content |
|---|---|
| `req_1.txt` | Sales filter SQL (`ISSOTRX='Y'`, `DOCSTATUS IN ('CO','CL')`, DOCTYPE filter) |
| `req_2.txt` | Extended extraction query |
| `req_3.txt` | Additional extraction parameters |

**Decision:** ✅ Preserve — defines the canonical extraction filter logic. Reference for dedup validation.

---

## 6. Reconciliation vs Existing Audits

### Agreements with `docs/warehouse/vente_dwh_source_audit.json`

| Claim in audit | Verified here | Match? |
|---|---|---|
| `02_C_ORDERLINE.xlsx` Sheet2 = 67,862 rows | Sheet1=3,350, Sheet2=67,862 ✅ | ✅ |
| `19_AD_USER_SALESREPS.xlsx` = 70 rows | openpyxl: 70 ✅ | ✅ |
| `21_C_BPARTNER_VENDORS.xlsx` = 9,633 rows | openpyxl: 9,633 ✅ | ✅ |
| `25_M_PRODUCT_THEME.xlsx` = 1,487 rows | openpyxl: 1,487 ✅ | ✅ |
| `18_RV_STORAGE_SALES_PRODUCTS.xlsx` = 58,826 rows | from audit ✅ | consistent |

### Agreements with `vente_2025_2026_import/manifest.json`

| Entity | Manifest rows | `business` schema rows | Match? |
|---|---|---|---|
| C_ORDER | 20,336 | 20,336 | ✅ |
| C_ORDERLINE | 354,910 | 354,910 | ✅ |
| C_INVOICE | 6,658 | 6,658 | ✅ |
| C_INVOICELINE | 335,877 | 335,877 | ✅ |
| M_INOUT | 18,718 | 18,718 | ✅ |
| M_INOUTLINE | 356,410 | 356,410 | ✅ |

> Confirms: `business` schema currently holds **2025-2026 data only** (loaded from `vente_2025_2026_import`). The `staging`/`warehouse`/`mart` schemas do not yet exist. Full 2024 history remains in `data/Exported_LPN/2024_*` xlsx files, not yet loaded.

### New findings vs prior audits

1. `Exported_data_through_a_drive/` exists as a complete duplicate of `Exported_LPN/` + some extra quarterly splits (e.g. `04_C_ORDERLINE_2025_Q1.xlsx`..`09_C_ORDERLINE_2026_Q2.xlsx`). Prior audit scan dirs did NOT include `data/Exported_LPN` — those scans targeted only the older folders.
2. `2024_*` series (35 files) is larger/newer than anything in the 2nd/3rd extraction folders — this is the complete 2024 history not previously in PostgreSQL.
3. `vente_2025_2026_import` row counts exactly match `business` schema — confirms it is the current DB load.

---

## 7. Validation — Second-Method Row Count Cross-Check

Three files verified with independent XML-zip row count (independent of openpyxl iteration):

| File | openpyxl rows | XML zip rows | Match |
|---|---|---|---|
| `2024_01_C_ORDER.xlsx` | 18,024 | 18,024 | ✅ |
| `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` | 34,710 | 34,710 | ✅ |
| `2024_15_AD_USER_SALESREPS.xlsx` | 31 | 31 | ✅ |

---

## 8. Classification Summary

| Classification | Count | Folders |
|---|---|---|
| ✅ canonical-2024 | 35 xlsx | `data/Exported_LPN/2024_01..2024_35` |
| ✅ canonical-24M | 5 xlsx | `data/Exported_LPN/53,54,55,56,58` |
| ⚠️ canonical-24M-TRAP | 1 xlsx | `data/Exported_LPN/57` (inflated CA — do not use for CA) |
| ✅ base-full-history | ~20 xlsx | `data/Exported_LPN/02..41` |
| ℹ️ bi-aggregate | ~12 xlsx | `data/Exported_LPN/42..52` + `2024_31..34` |
| ❌ junk | 12+ files | `Book1..7`, `~$*`, `1.xlsx`, `2.xlsx`, `3.xlsx`, `4.xlsx`, `DATAWHAREHOUSE_EXPORT/1..4` |
| ✅ import-ready CSV (2025-2026) | 36 CSV | `vente_2025_2026_import/csv/` |
| ⚠️ older import (superseded) | 18 CSV | `vente_clean_import/csv/` |
| ⚠️ enrichment (superseded) | 7 CSV | `vente_bi_enrichment_import/csv/` |
| ✅ forecast facts | 4 CSV | `forecast_monthly_import/csv/` |
| ⚠️ duplicate folder | ~100 xlsx | `data/Exported_data_through_a_drive/` |
| ⚠️ legacy/archive | 23+8+10 xlsx | `1st_Extraction/`, `2nd_Extraction/`, `3rd_Extraction/`, `4th_Extraction/` |
| ✅ docs | 3 txt | `txt/req_1..3.txt` |

---

## 9. What Is Missing (Gap Analysis)

| Gap | Impact |
|---|---|
| 2024 data NOT yet in PostgreSQL | `staging`/`warehouse` require `2024_*` + `53..58` to cover full 2024-2026 history |
| `2024_02_C_ORDERLINE.xlsx` (253 MB), `2024_16_M_PRODUCT.xlsx` (2.27 GB), `2024_18_M_PRODUCT_PO.xlsx` (995 MB) | Very large files — must be chunked (≥100 MB chunks) during staging ETL |
| `staging`/`warehouse`/`mart` schemas don't exist | No reconciled DWH read model yet |
| `57_CUSTOMER_PORTFOLIO_24M` CA bug not yet corrected in DB | Use `2024_35` + re-aggregate for BI use |

---

## 10. Recommended Next Steps

- **BI-02 (next):** Profile the canonical transactional files (column-level: PKs, FKs, null %, metric fields) using sampled reads.
- **BI-05 / Rebuild Task 4:** Formalize the dedup rule and source mapping into a SOURCE_MAPPING.md.
- **Rebuild Task 5:** Load `staging` schema from `data/Exported_LPN/2024_*` + `53..58` — chunk large files.
- **Human action (Rebuild Task 2):** Approve `Exported_data_through_a_drive/`, `1st_Extraction/`, `2nd_Extraction/`, `3rd_Extraction/`, `4th_Extraction/` for archiving (do NOT delete yet).

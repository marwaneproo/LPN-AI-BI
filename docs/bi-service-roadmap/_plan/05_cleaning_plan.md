# BI-05 — Cleaning, Dedup & Normalisation Plan

**Task:** BI-05  
**Date:** 2026-06-25  
**Branch:** `bi-polished-dashboard`  
**Inputs:** BI-02 transactional profile, BI-03 format & quality profile, BI-04 entity catalogue, `docs/bi-service-roadmap/AI_BI_OPTIMIZATION_TASK_ROADMAP.md`  
**Scope:** Design only — no data is touched; all rules reference the staging layer (`stg_*`). Implementation in BI-06 (DDL) and BI-09 (ETL).  
**Status:** ✅ Complete

---

## 0. Overview

This document defines the complete set of cleaning and normalisation rules that the ETL must apply to move raw source data into the staging schema (`stg`). Every rule is keyed to a concrete BI-03 problem code, a real BI-02 column reference, or a business decision from BI-01/04.

**Three-layer cleaning model:**

```
SOURCE FILE (xlsx / CSV)
    │
    ▼ [LAYER 1 — Ingest rules]  encoding, delimiter, type coercion, FK sentinel
    │
stg_* (PostgreSQL, raw types preserved, nullable)
    │
    ▼ [LAYER 2 — Dedup / filter rules]  PK dedup, DOCSTATUS filter, overlap window
    │
stg_* with _dedup suffix flag  OR  direct write to cleaned staging
    │
    ▼ [LAYER 3 — Normalisation rules]  TRIM, INITCAP, NULLIF tokens, dates
    │
dim_* / fact_*  (warehouse layer — BI-06/07)
```

---

## 1. Source-to-Staging Mapping Table

The columns below define the authoritative ETL source for each staging table. This resolves the 2024 vs 2025-2026 overlap and the CA trap.

| Staging table | Primary source (2025-2026) | Historical source (2024) | Overlap window | CA trap? |
|---|---|---|---|---|
| `stg_c_order` | `C_ORDER.csv` (20,336 rows) | `2024_01_C_ORDER.xlsx` (18,024 rows) | Jun–Dec 2024 | No |
| `stg_c_orderline` | `C_ORDERLINE.csv` (354,910 rows) | `2024_02_C_ORDERLINE.xlsx` (253 MB) | Jun–Dec 2024 | No |
| `stg_c_invoice` | `C_INVOICE.csv` (6,658 rows) | `2024_03_C_INVOICE.xlsx` (5,264 rows) | Jun–Dec 2024 | No |
| `stg_c_invoiceline` | `C_INVOICELINE.csv` (335,877 rows) | `2024_04_C_INVOICELINE.xlsx` (80 MB) | Jun–Dec 2024 | No |
| `stg_m_inout` | `M_INOUT.csv` (18,718 rows) | `2024_05_M_INOUT.xlsx` (44,232 rows) | Jun–Dec 2024 | No |
| `stg_m_inoutline` | `M_INOUTLINE.csv` (356,410 rows) | `2024_06_M_INOUTLINE.xlsx` (132 MB) | Jun–Dec 2024 | No |
| `stg_c_payment` | `C_PAYMENT.csv` (1,443 rows) | `2024_07_C_PAYMENT.xlsx` (3,775 rows) | None | No |
| `stg_c_allocationhdr` | `C_ALLOCATIONHDR.csv` (1,473 rows) | `2024_08_C_ALLOCATIONHDR.xlsx` (4,113 rows) | None | No |
| `stg_c_allocationline` | `C_ALLOCATIONLINE.csv` (4,212 rows) | `2024_09_C_ALLOCATIONLINE.xlsx` (86,863 rows) | None | No |
| `stg_c_bpartner` | `C_BPARTNER.csv` (823 rows) | `2024_10_C_BPARTNER.xlsx` (42,434 rows) | None | No |
| `stg_c_bpartner_location` | `C_BPARTNER_LOCATION.csv` (888 rows) | `2024_11_C_BPARTNER_LOCATION.xlsx` (4,221 rows) | None | No |
| `stg_c_location` | `C_LOCATION.csv` (772 rows) | `2024_12_C_LOCATION.xlsx` (4,108 rows) | None | No |
| `stg_m_product` | `M_PRODUCT.csv` (35,822 rows) | ⚠️ xlsx=2,272 MB — **CSV only** | None | No |
| `stg_m_product_po` | `M_PRODUCT_PO.csv` (39,359 rows) | ⚠️ xlsx=995 MB — **CSV only** | None | No |
| `stg_c_bpartner_vendor` | `C_BPARTNER_VENDOR.csv` (14,264 rows) | `2024_19_C_BPARTNER_VENDORS.xlsx` | None | No |
| `stg_rv_storage` | `RV_STORAGE.csv` (99,234 rows) | `18_RV_STORAGE_SALES_PRODUCTS.xlsx` | Snapshot | No |
| `stg_ad_user` | `AD_USER.csv` (28 rows) | `2024_15_AD_USER_SALESREPS.xlsx` (31 rows) | None | No |
| `stg_m_product_category` | (no CSV) | `2024_17_M_PRODUCT_CATEGORY.xlsx` (41 rows) | None | No |
| `stg_m_product_theme` | (no CSV) | `2024_21_M_PRODUCT_THEME.xlsx` (1,513 rows) | None | No |
| `stg_m_product_type` | (no CSV) | `2024_20_M_PRODUCT_TYPE.xlsx` (27 rows) | None | No |
| `stg_m_product_collection` | (no CSV) | `2024_22_M_PRODUCT_COLLECTION.xlsx` (~86K rows) | None | No |
| `stg_c_doctype` | (no CSV) | `2024_23_C_DOCTYPE.xlsx` (79 rows) | None | No |
| `stg_c_paymentterm` | (no CSV) | `2024_28_C_PAYMENTTERM.xlsx` (7 rows) | None | No |
| `stg_m_pricelist` | (no CSV) | `2024_29_M_PRICELIST.xlsx` (22 rows) | None | No |
| `stg_c_salesregion` | `C_SALESREGION.csv` (13 rows) | `2024_30_C_SALESREGION.xlsx` | None | No |
| `stg_c_region` | `C_REGION.csv` (16 rows) | `2024_13_C_REGION.xlsx` (87 rows) | None | No |
| `stg_c_city` | `C_CITY.csv` (302 rows) | `2024_14_C_CITY.xlsx` (364 rows) | None | No |
| `stg_m_warehouse` | (no CSV) | `2024_26_M_WAREHOUSE.xlsx` (7 rows) | None | No |

**CA trap exclusion:** `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` is **permanently excluded** from all ETL. It contains inflated ORDERED_CA_24M / INVOICED_CA_24M values due to an unresolved many-to-many join bug between order lines and invoice lines at the customer-portfolio level. All CA figures must be derived from `fact_sales_order.GRANDTOTAL` and `fact_invoice.GRANDTOTAL`. Use `2024_35_CUSTOMER_PORTFOLIO_FIXED.xlsx` only for cross-validation, never as a source.

---

## 2. Layer 1 — Ingest Rules

These rules apply at the file-read step, before any row lands in staging.

### 2.1 Encoding Rules (BI-03 E-01, E-02, E-03)

| Problem | Rule code | Affected files | Rule |
|---|---|---|---|
| All CSVs are UTF-8-SIG (BOM present) | **ENC-01** | All 8 `vente_2025_2026_import/csv/*.csv` | Read with `encoding='utf-8-sig'`; PostgreSQL `COPY … ENCODING 'UTF8'` strips BOM automatically |
| All CSVs use `;` delimiter | **ENC-02** | Same set | `csv.DictReader(f, delimiter=';')` or `COPY … DELIMITER ';'` |
| xlsx files are internally UTF-8 | **ENC-03** | All `2024_*` / `53..58` xlsx files | `openpyxl.load_workbook(path, read_only=True, data_only=True)` — no encoding option needed |

**Testable assertion (ENC-01):**
```python
# If first byte of a CSV is 0xEF 0xBB 0xBF, open with utf-8-sig; else raise LoadError
assert open(path, 'rb').read(3) in (b'\xef\xbb\xbf', b'')  # BOM or clean UTF-8
```

---

### 2.2 Type Coercion Rules

These map source column values to PostgreSQL staging types. Applied during COPY or insert.

| Staging type | Rule code | Applied to | Rule |
|---|---|---|---|
| `BIGINT` | **TYP-01** | All `*_ID` columns | Parse as integer; null if blank/None; error if non-numeric |
| `NUMERIC(18,4)` | **TYP-02** | GRANDTOTAL, TOTALLINES, LINENETAMT, LINETOTALAMT, PRICEACTUAL, PRICELIST, PRICELIMIT, FREIGHTAMT, CHARGEAMT, DISCOUNT, AMOUNT, DISCOUNTAMT, WRITEOFFAMT, OVERUNDERAMT, QTYAVAILABLE, QTYONHAND, QTYRESERVED, CREDITLIMIT | Cast to NUMERIC; period `.` is decimal separator (BI-03 N-01 confirmed); null if blank |
| `TIMESTAMPTZ` | **TYP-03** | DATEORDERED, DATEINVOICED, DATEACCT, DATETRX, MOVEMENTDATE, DATEPRINTED, DATELASTINVENTORY | Openpyxl returns `datetime.datetime`; cast to UTC-aware. CSVs: ISO 8601 string → `::TIMESTAMPTZ` |
| `TEXT` | **TYP-04** | DOCUMENTNO, POREFERENCE, DESCRIPTION, COMMENTAIRE, NAME, VALUE, DOCSTATUS, DOCACTION, EMAIL | Store as-is; normalisation applied in Layer 3 |
| `CHAR(1)` | **TYP-05** | ISSOTRX, ISPAID, ISACTIVE, PROCESSED, POSTED, ISINVOICED, ISDELIVERED, ISCUSTOMER, ISVENDOR, ISSOLD, ISPURCHASED, ISSTOCKED | Expect 'Y' or 'N'; validate; blank/null allowed |

---

### 2.3 FK Null Sentinel Rule (BI-03 N-03)

**Rule code: FK-NULL-01**  
**Problem:** Compiere uses integer `0` to mean "no FK target" for columns including `C_DOCTYPE_ID`, `AD_ORG_ID`, `BILL_USER_ID`, `AD_USER_ID` on orders/invoices.  
**Confirmed example:** `2024_01_C_ORDER.xlsx` column `C_DOCTYPE_ID` row 16 = `0`  
**Rule:** Apply `NULLIF(col, 0)` on every integer FK column except the known singletons below.  
**Exemptions (0 is a valid non-null value):**

| Column | Why exempt |
|---|---|
| `C_UOM_ID = 0` | Confirmed singleton value for "each" unit — never null |
| `AD_CLIENT_ID = 1000000` | System constant — never meaningful FK |

**SQL transform (applied in stg COPY view or ETL step):**
```sql
NULLIF(c_doctype_id, 0)::BIGINT AS c_doctype_id,
NULLIF(ad_org_id, 0)::BIGINT   AS ad_org_id,
NULLIF(bill_user_id, 0)::BIGINT AS bill_user_id,
NULLIF(salesrep_id, 0)::BIGINT AS salesrep_id
```

**Testable assertion (FK-NULL-01):**
```sql
-- After staging load: no zero-value FK cols should remain
SELECT COUNT(*) FROM stg_c_order WHERE c_doctype_id = 0;  -- expect 0
SELECT COUNT(*) FROM stg_c_invoice WHERE bill_user_id = 0; -- expect 0
```

---

## 3. Layer 2 — Dedup & Filter Rules

These rules apply after rows are in staging (or during the staging load itself). They determine which rows survive into the warehouse.

### 3.1 Source-Period Assignment Rule (BI-01 dedup decision)

**Rule code: DEDUP-00**

Each entity has exactly two authorised data windows:

| Window | Source | Date range |
|---|---|---|
| **2024 history** | `2024_*` xlsx files | 2024-01-01 → 2024-12-31 |
| **2025-2026 live** | CSV (`vente_2025_2026_import/`) or `53..58` xlsx | 2025-01-01 → present |

**Overlap window:** Jun–Dec 2024 rows appear in BOTH sources for transactional tables (C_ORDER, C_ORDERLINE, C_INVOICE, C_INVOICELINE, M_INOUT, M_INOUTLINE).  
**Resolution strategy:** Load both sources into staging with a `_source_tag` column (`'2024_xlsx'` or `'csv_2025'`); then dedup in the warehouse load step using the PK-based rule below.

---

### 3.2 Per-Entity Dedup Keys

All PKs are confirmed unique within each individual source file (BI-02 §2 — 2000/2000 sample hit unique for all 4 transactional PKs).

| Staging table | Dedup key | Confirmed unique? | Dedup strategy |
|---|---|---|---|
| `stg_c_order` | `C_ORDER_ID` | ✅ BI-02 | Keep the row from CSV (2025) if same ID appears in both; 2024 xlsx as fallback |
| `stg_c_orderline` | `C_ORDERLINE_ID` | ✅ BI-02 | Same — prefer CSV (2025) |
| `stg_c_invoice` | `C_INVOICE_ID` | ✅ BI-02 | Same — prefer CSV (2025) |
| `stg_c_invoiceline` | `C_INVOICELINE_ID` | ✅ BI-02 | Same — prefer CSV (2025) |
| `stg_m_inout` | `M_INOUT_ID` | BI-04 (not sampled; treat as unique by design) | Prefer CSV |
| `stg_m_inoutline` | `M_INOUTLINE_ID` | BI-04 | Prefer CSV |
| `stg_c_payment` | `C_PAYMENT_ID` | BI-04 | Prefer CSV |
| `stg_c_allocationhdr` | `C_ALLOCATIONHDR_ID` | BI-04 | Prefer CSV |
| `stg_c_allocationline` | `C_ALLOCATIONLINE_ID` | BI-04 | Prefer CSV |
| `stg_c_bpartner` | `C_BPARTNER_ID` | BI-04 | Prefer CSV (2025); xlsx has 42K rows vs CSV 823 — all customer BPs must survive, merge on PK |
| `stg_c_bpartner_vendor` | `C_BPARTNER_ID` | BI-04 | Merge xlsx + CSV on `C_BPARTNER_ID`; last-write-wins (SCD1) |
| `stg_m_product` | `M_PRODUCT_ID` | BI-04 | CSV only (xlsx=2,272 MB — excluded) |
| `stg_m_product_category` | `M_PRODUCT_CATEGORY_ID` | BI-04 | xlsx only (no CSV) |
| `stg_m_product_theme` | `M_PRODUCT_THEME_ID` | BI-04 | xlsx only |
| `stg_m_product_type` | `M_PRODUCT_TYPE_ID` | BI-04 | xlsx only |
| `stg_ad_user` | `AD_USER_ID` | BI-04 | Merge xlsx (31) + CSV (28) on `AD_USER_ID`; xlsx has 3 extra rows — include all |
| `stg_c_doctype` | `C_DOCTYPE_ID` | BI-04 | xlsx only (79 rows) |
| `stg_c_region` | `C_REGION_ID` | BI-04 | Prefer CSV; merge on PK |
| `stg_c_city` | `C_CITY_ID` | BI-04 | Prefer CSV; merge on PK |
| `stg_c_salesregion` | `C_SALESREGION_ID` | BI-04 | Prefer CSV; merge on PK |
| `stg_c_location` | `C_LOCATION_ID` | BI-04 | Prefer CSV; merge on PK |
| `stg_c_bpartner_location` | `C_BPARTNER_LOCATION_ID` | BI-04 | Prefer CSV; merge on PK |
| `stg_rv_storage` | Composite: `(M_PRODUCT_ID, M_ATTRIBUTESETINSTANCE_ID, M_WAREHOUSE_ID, M_LOCATOR_ID)` | BI-04 (snapshot) | Single source (CSV); no dedup needed; snapshot date = `DATELASTINVENTORY` |
| `stg_m_product_po` | `(M_PRODUCT_ID, C_BPARTNER_ID)` | BI-04 | CSV only (xlsx=995 MB — excluded); deduplicate on composite key, keep row with lowest `PRICELIST` or latest `UPDATED` |
| `stg_m_pricelist` | `M_PRICELIST_ID` | BI-04 | xlsx only; no overlap |
| `stg_m_warehouse` | `M_WAREHOUSE_ID` | BI-04 | xlsx only; singleton in practice (ID=1,000,000) |
| `stg_c_paymentterm` | `C_PAYMENTTERM_ID` | BI-04 | xlsx only (7 rows) |

**Dedup SQL pattern (applied at warehouse load, not in staging):**
```sql
-- Prefer CSV row when same PK exists in both sources
WITH ranked AS (
  SELECT *, 
         ROW_NUMBER() OVER (
           PARTITION BY c_order_id 
           ORDER BY CASE _source_tag WHEN 'csv_2025' THEN 0 ELSE 1 END
         ) AS rn
  FROM stg_c_order
)
INSERT INTO dim_c_order_clean
SELECT * FROM ranked WHERE rn = 1;
```

---

### 3.3 Business Filter Rules

Applied during warehouse load from staging (not during staging load).

| Entity | Filter rule code | Filter condition | Reason |
|---|---|---|---|
| C_ORDER | **FILT-01** | `DOCSTATUS IN ('CO','CL')` | Exclude draft (DR), voided (VO), in-progress (IP) orders |
| C_ORDER | **FILT-02** | `C_DOCTYPETARGET_ID IN (1000028, 1000034, 1000032, 1000031)` | Sales orders only; exclude purchase/internal types |
| C_INVOICE | **FILT-03** | `DOCSTATUS = 'CO'` | Confirmed invoices only |
| C_INVOICE | **FILT-04** | `C_DOCTYPE_ID IN (1000002, 1000003, 1000004)` | Sales invoices + credit notes |
| M_INOUT | **FILT-05** | `ISSOTRX = 'Y'` | Sales deliveries only; exclude purchase receipts |
| M_INOUT | **FILT-06** | `DOCSTATUS = 'CO'` | Confirmed deliveries only |
| C_BPARTNER | **FILT-07** | `ISCUSTOMER = 'Y'` → `dim_customer`; `ISVENDOR = 'Y'` → `dim_supplier` | Route to correct dim; a BP can be both |
| C_BPARTNER | **FILT-08** | `VALUE != 'NA'` AND `NAME != 'NA'` | Exclude system placeholder record (BI-03 M-03) |
| M_PRODUCT | **FILT-09** | `ISACTIVE = 'Y'` OR product appears in at least one invoiceline | Exclude obsolete products not referenced in facts |
| AD_USER | **FILT-10** | `ISSALESREP = 'Y'` (if column exists) OR `AD_USER_ID IN (SELECT DISTINCT SALESREP_ID FROM stg_c_order)` | Only load actual salesreps into dim_commercial |
| C_DOCTYPE | **FILT-11** | `ISSALESTRANSACTION = 'Y'` | Exclude system/purchase doc types from dim_document_type |

**Testable assertions (FILT rules):**
```sql
-- After warehouse load, no rejected DOCSTATUS should appear in fact tables
SELECT DISTINCT docstatus FROM fact_sales_order;  -- expect only CO, CL
SELECT COUNT(*) FROM dim_customer WHERE value = 'NA';  -- expect 0
SELECT COUNT(*) FROM fact_invoice WHERE c_doctype_id NOT IN (1000002,1000003,1000004); -- expect 0
```

---

### 3.4 DOCACTION Sentinel Rule (BI-03 M-01)

**Rule code: SENT-01**  
**Problem:** `DOCACTION` column contains `'--'` in nearly all rows — this is the Compiere no-op/none value, not a null.  
**Confirmed example:** `2024_01_C_ORDER.xlsx` DOCACTION row 2 = `'--'`  
**Rule:** Store `'--'` as-is in staging (`TEXT`). Do NOT map to NULL. Do NOT include DOCACTION as a dimension in any dim or fact table (it is a Compiere UI artefact, not a business attribute).  
**Do not confuse with:** POREFERENCE `'-'` (single dash) which IS mapped to NULL (rule NORM-05 below).

---

## 4. Layer 3 — Normalisation Rules

Applied during the `stg_*` → `dim_*`/`fact_*` warehouse load. Staging tables always preserve the raw value; normalised value is computed in the warehouse INSERT SELECT.

### 4.1 Whitespace & Embedded Control Characters (BI-03 W-01, W-02, W-04)

| Problem | Rule code | Affected columns | SQL rule |
|---|---|---|---|
| Trailing/leading spaces (W-01) | **NORM-01** | ALL `TEXT` columns | `TRIM(col)` — applied universally |
| Embedded tabs (W-02) | **NORM-02** | NAME, DESCRIPTION in dim tables | `REGEXP_REPLACE(TRIM(col), E'[\\t\\r\\n]+', ' ', 'g')` |
| Trailing newline in VALUE (W-04) | **NORM-02** | VALUE in M_PRODUCT_CATEGORY | Same `REGEXP_REPLACE` as above |
| Double spaces (unnamed column, C_DOCTYPE — W-05) | **NORM-02** | NAME in stg_c_doctype | `REGEXP_REPLACE(TRIM(col), ' {2,}', ' ', 'g')` |

**Confirmed examples:**
- `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` CUSTOMER_NAME row 4 = `'LIBRAIRIE DSM                '` → after TRIM: `'LIBRAIRIE DSM'`
- `2024_17_M_PRODUCT_CATEGORY.xlsx` NAME row 2 = `'Scientifiques et techniques\t'` → after NORM-02: `'Scientifiques et techniques'`
- `2024_17_M_PRODUCT_CATEGORY.xlsx` VALUE row 7 = `'0000\n'` → after NORM-02: `'0000'`

**Testable assertion (NORM-01/02):**
```sql
-- After dim load, no trailing/leading whitespace or embedded tabs
SELECT COUNT(*) FROM dim_customer WHERE name <> TRIM(name);  -- expect 0
SELECT COUNT(*) FROM dim_product_category WHERE name ~ E'[\\t\\n\\r]';  -- expect 0
```

---

### 4.2 Case Normalisation (BI-03 K-01, K-02)

**Rule code: NORM-03**  
**Problem:** Dimension name fields are a mix of ALL-CAPS (K-01: cities, regions, customers, categories) and all-lowercase (K-02: some commercials — example `'afassi '`).  
**Confirmed examples:**
- `2024_14_C_CITY.xlsx` NAME row 2 = `'AGADIR'`
- `2024_15_AD_USER_SALESREPS.xlsx` NAME row 11 = `'afassi '`  

**Rule:** Apply `INITCAP(TRIM(col))` at the dim build step (not in staging). Staging preserves original case.  
**Applied to:** `dim_customer.name`, `dim_commercial.name`, `dim_geography.city_name`, `dim_geography.region_name`, `dim_product_category.name`, `dim_product.name`  
**NOT applied to:** staging tables (raw preserved), fact tables (no name columns), code/VALUE columns (e.g., product ISBN codes must not be initcapped)

**SQL pattern:**
```sql
-- In dim_customer INSERT SELECT:
INITCAP(TRIM(b.name)) AS customer_name
```

**Testable assertion (NORM-03):**
```sql
-- After dim load, no all-caps customer names longer than 2 chars
SELECT COUNT(*) FROM dim_customer
WHERE name = UPPER(name) AND LENGTH(name) > 2;  -- expect 0
```

---

### 4.3 Missing-Value Token Rules (BI-03 M-01, M-02, M-03)

| Problem | Rule code | Token | Affected columns | Rule |
|---|---|---|---|---|
| `'--'` Compiere no-op (M-01) | **NORM-04** | `'--'` | DOCACTION | Keep as-is; DOCACTION is not used in warehouse |
| `'-'` empty reference sentinel (M-02) | **NORM-05** | `'-'` | POREFERENCE | `NULLIF(TRIM(col), '-')` |
| `'NA'` system placeholder (M-03) | **NORM-06** | `'NA'` | C_BPARTNER.VALUE, C_BPARTNER.NAME | Excluded by FILT-08; if bypassed: `NULLIF(TRIM(col), 'NA')` |

**Confirmed examples:**
- `2024_03_C_INVOICE.xlsx` POREFERENCE row 217 = `'-'` → after NORM-05: NULL
- `2024_10_C_BPARTNER.xlsx` NAME row 2 = `'NA'` → excluded by FILT-08 (VALUE='NA')

---

### 4.4 Date Rules (BI-03 D-01, D-02, D-03)

| Problem | Rule code | Affected columns | Rule |
|---|---|---|---|
| DATEACCT always midnight (D-01) | **DATE-01** | `DATEACCT` | Cast to `DATE` in fact tables: `DATEACCT::DATE AS date_acct` |
| Midnight DATEINVOICED / DATEORDERED (D-02) | **DATE-02** | `DATEINVOICED`, `DATEORDERED`, `MOVEMENTDATE` | Store as `TIMESTAMPTZ` in staging; truncate to `DATE` in fact table date-key join: `DATEORDERED::DATE` |
| dd/mm/yyyy in free-text (D-03) | **DATE-03** | `POREFERENCE` | Store as `TEXT`; do not parse; this field is a customer's PO reference number |

**Confirmed example (D-01):** `2024_01_C_ORDER.xlsx` DATEACCT row 2 = `2024-01-02 00:00:00` → stored in staging as TIMESTAMPTZ, then dimension join uses `::DATE`.  
**Confirmed example (D-03):** `2024_03_C_INVOICE.xlsx` POREFERENCE row 288 = `'17/01/2024'` — treat as text, never convert.

**Date range validity rule:**  
All date columns must satisfy: `date BETWEEN '2020-01-01' AND CURRENT_DATE + INTERVAL '1 day'`. Rows outside this window → reject with reason code `DATE_OUT_OF_RANGE`.

**Testable assertion (DATE-01/02):**
```sql
-- After fact load, all date keys must resolve to a dim_date row
SELECT COUNT(*) FROM fact_sales_order f
LEFT JOIN dim_date d ON d.date_key = f.order_date_key
WHERE d.date_key IS NULL;  -- expect 0
```

---

### 4.5 Numeric/Decimal Rules (BI-03 N-01, N-02)

**Rule code: NORM-07 (positive finding — no transform needed)**  
All numeric columns in both xlsx and CSV sources use period `.` as the decimal separator and no thousands separator. No conversion needed.  
**Confirmed (BI-03 §5):** N-01 and N-02 are positive findings — zero instances of comma decimal or space thousands across all sampled files.  
**Staging rule:** Read all amount columns with `NUMERIC(18,4)` in PostgreSQL. The Python/openpyxl layer returns `float`; cast is safe.

---

### 4.6 Currency Rules (BI-03 C-01, C-02, C-03)

| Problem | Rule code | Rule |
|---|---|---|
| Amount columns are plain numeric (C-01) | **CUR-01** | No currency-symbol stripping needed; `NUMERIC(18,4)` cast is sufficient |
| Currency in free-text (COMMENTAIRE "5000dh") (C-02) | **CUR-02** | Store COMMENTAIRE as `TEXT`; do not parse for currency amounts; no ETL rule required |
| False-positive currency regex hits (MADRID, MOHAMMADIA, IMAD) (C-03) | **CUR-03** | Do not run currency-regex on NAME columns. Documented as false positives; no remediation needed |

All monetary amounts are implicitly in MAD (Moroccan dirham). `C_CURRENCY_ID=239` (MAD) confirmed singleton across all rows in BI-02. No multi-currency conversion needed in v1.

---

## 5. Rejected-Row Strategy

### 5.1 Reject Classification

A row is rejected if it violates any of the following hard constraints:

| Reject reason code | Condition | Severity |
|---|---|---|
| `NULL_PK` | Primary key column is NULL | HARD — row excluded |
| `ZERO_FK_UNRESOLVABLE` | FK = 0 and no NULLIF rule covers it | HARD — row excluded |
| `DATE_OUT_OF_RANGE` | Any date column outside `2020-01-01..now+1d` | HARD — row excluded |
| `INVALID_FLAG` | `ISSOTRX`/`ISPAID`/`ISACTIVE` is not 'Y' or 'N' or NULL | SOFT — coerce to NULL; log warning |
| `DOCSTATUS_FILTERED` | DOCSTATUS not in the allowed set for this entity | SOFT — row excluded from warehouse but logged |
| `CA_TRAP_SOURCE` | Row originates from `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` | HARD — entire file excluded |
| `ENCODING_ERROR` | Non-printable characters (control chars outside `\t\n\r`) in any TEXT column | SOFT — replace with `?`; log cell position |
| `DUPLICATE_PK` | Same PK value found in two sources; resolved by preference rule (DEDUP-00) | INFO — lower-preference row logged |

### 5.2 Reject Store Schema

All rejected rows are written to a PostgreSQL table `etl.stg_rejects`. This table is append-only across runs.

```sql
CREATE TABLE etl.stg_rejects (
    reject_id        BIGSERIAL PRIMARY KEY,
    etl_run_id       UUID        NOT NULL,          -- unique per pipeline run
    source_table     TEXT        NOT NULL,           -- e.g., 'stg_c_order'
    source_file      TEXT        NOT NULL,           -- full file path
    source_row       INTEGER     NOT NULL,           -- 1-indexed row number in source file
    pk_value         TEXT,                           -- stringified PK value (may be NULL for NULL_PK)
    reject_reason    TEXT        NOT NULL,           -- reason code from §5.1
    reject_detail    TEXT,                           -- human-readable detail (e.g., "C_ORDER_ID=NULL")
    raw_row_json     JSONB,                          -- full raw row serialised to JSON (omit for large rows)
    rejected_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ON etl.stg_rejects (etl_run_id);
CREATE INDEX ON etl.stg_rejects (source_table, reject_reason);
```

**Retention:** `stg_rejects` is never truncated. It serves as the audit trail across all ETL runs. Partition by `rejected_at` month if volume exceeds 100K rows.

---

## 6. Audit & Logging

### 6.1 ETL Run Log Schema

```sql
CREATE TABLE etl.etl_run_log (
    etl_run_id        UUID         PRIMARY KEY,
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    status            TEXT         NOT NULL DEFAULT 'RUNNING',  -- RUNNING | SUCCESS | FAILED
    triggered_by      TEXT,        -- 'manual' | 'scheduler' | 'rebuild'
    source_tag        TEXT         NOT NULL,  -- 'csv_2025' | '2024_xlsx' | 'mixed'
    git_commit        TEXT,        -- ETL script git SHA at run time
    notes             TEXT
);

CREATE TABLE etl.etl_run_table_stats (
    stat_id          BIGSERIAL    PRIMARY KEY,
    etl_run_id       UUID         NOT NULL REFERENCES etl.etl_run_log(etl_run_id),
    table_name       TEXT         NOT NULL,
    source_file      TEXT,
    rows_read        INTEGER,
    rows_inserted    INTEGER,
    rows_skipped_dup INTEGER,
    rows_rejected    INTEGER,
    duration_ms      INTEGER,
    completed_at     TIMESTAMPTZ  DEFAULT NOW()
);
```

### 6.2 Required Log Events (per entity load)

| Event | When | Fields |
|---|---|---|
| `TABLE_START` | Before reading source file | `table_name`, `source_file`, `etl_run_id` |
| `TABLE_COMPLETE` | After staging insert | `rows_read`, `rows_inserted`, `rows_skipped_dup`, `rows_rejected` |
| `REJECT_BATCH` | After each reject flush | `table_name`, `reject_count`, `reason_breakdown` (JSON) |
| `RUN_COMPLETE` | After all tables | `total_rows_inserted`, `total_rows_rejected`, `status` |

**Minimum acceptance criteria per run:**
- `rows_rejected / rows_read < 0.05` (5%) for transactional tables — if exceeded, abort and log `FAILED`
- `rows_inserted > 0` for all mandatory tables (`stg_c_order`, `stg_c_invoice`, `stg_m_product`, `stg_c_bpartner`) — if any mandatory table has 0 rows, log `FAILED`

---

## 7. Idempotency Rules

**Principle:** Every ETL run must be safely re-runnable. Running the pipeline twice must produce the same output as running it once.

| Rule code | Rule | Implementation |
|---|---|---|
| **IDEM-01** | Truncate staging tables before reload | `TRUNCATE TABLE stg_c_order, stg_c_orderline … CASCADE` at run start |
| **IDEM-02** | `stg_rejects` is append-only; never truncated | Reject rows carry `etl_run_id` — filter by run ID to see only current-run rejects |
| **IDEM-03** | `etl_run_log` is append-only | Each run generates a new `etl_run_id` (UUID) |
| **IDEM-04** | Dedup runs deterministically | Preference rule is always CSV-over-xlsx; no random tie-breaking |
| **IDEM-05** | Warehouse load uses `INSERT … ON CONFLICT DO UPDATE` (upsert) on dim PK | Prevents duplicate dim rows if warehouse load is re-run |
| **IDEM-06** | Fact tables are truncated and reloaded (not upserted) | `TRUNCATE fact_sales_order CASCADE` then re-insert from `stg_c_order` clean view |

**Idempotency test pattern:**
```python
# Run pipeline twice; compare row counts
run_pipeline()
counts_1 = get_table_counts()
run_pipeline()
counts_2 = get_table_counts()
assert counts_1 == counts_2, f"Non-idempotent: {counts_1} != {counts_2}"
```

---

## 8. Special Cases

### 8.1 The 57_PORTFOLIO_24M CA Trap (BI-01 decision)

**File:** `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx`  
**Problem:** `ORDERED_CA_24M` and `INVOICED_CA_24M` in this file are inflated because the file was produced by joining order lines to invoice lines without a correct bridge key, creating a many-to-many fan-out.  
**Impact:** Using these columns for CA metrics would overstate revenue by an unknown factor.  
**Rule: TRAP-01 — Entire file excluded from ETL.** Under no circumstances should this file's CA columns be loaded into any staging, warehouse, or mart table.  
**Allowed uses:** The file may be opened manually for comparison/validation only.  
**CA source of truth:** `fact_sales_order.grand_total_amount` (from C_ORDER.GRANDTOTAL) and `fact_invoice.grand_total_amount` (from C_INVOICE.GRANDTOTAL).

### 8.2 Large Files — Chunked ETL Required

| File | Size | Strategy |
|---|---|---|
| `2024_02_C_ORDERLINE.xlsx` | 253 MB | `openpyxl read_only=True` with 5,000-row chunk batches; write to staging in transactions of 5,000 rows |
| `2024_06_M_INOUTLINE.xlsx` | 132 MB | Same chunked approach |
| `2024_04_C_INVOICELINE.xlsx` | 80 MB | Same (borderline — 80 MB) |
| `2024_16_M_PRODUCT.xlsx` | 2,272 MB | **EXCLUDED — use M_PRODUCT.csv only** |
| `2024_18_M_PRODUCT_PO.xlsx` | 995 MB | **EXCLUDED — use M_PRODUCT_PO.csv only** |

**Chunk pattern:**
```python
CHUNK_SIZE = 5_000
ws = wb.active
chunk = []
for i, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
    chunk.append(row)
    if len(chunk) >= CHUNK_SIZE:
        insert_chunk(conn, 'stg_c_orderline', header, chunk, etl_run_id, source_file, i)
        chunk = []
if chunk:
    insert_chunk(conn, 'stg_c_orderline', header, chunk, etl_run_id, source_file, i)
```

### 8.3 C_INVOICE.C_ORDER_ID Null Pattern

29.9% of invoice rows have null `C_ORDER_ID` (confirmed BI-02). This is not a data quality issue — it is a legitimate Compiere behaviour where invoices are created directly without a source order.  
**Rule: NORM-08** — `C_ORDER_ID = NULL` on invoices → valid; store as NULL in `stg_c_invoice`; warehouse bridge to fact_sales_order uses LEFT JOIN.  
This must NOT be treated as a reject (`NULL_PK` applies only to the entity's own PK, not to FK columns).

### 8.4 M_PRODUCT_PO Primary Supplier

`stg_m_product_po` may have multiple rows per `M_PRODUCT_ID` (multiple distributors per product). `dim_product` requires exactly one primary supplier per product to avoid CA duplication in mart queries.  
**Rule: DEDUP-SUPP-01** — For each `M_PRODUCT_ID`, select the row with lowest `SEQNO` (distributor priority rank). If `SEQNO` is null or equal, use lowest `C_BPARTNER_ID` as tie-breaker (deterministic).

```sql
WITH ranked_po AS (
  SELECT *, ROW_NUMBER() OVER (
    PARTITION BY m_product_id
    ORDER BY COALESCE(seqno, 999), c_bpartner_id
  ) AS rn
  FROM stg_m_product_po
)
INSERT INTO dim_product (m_product_id, primary_supplier_id, …)
SELECT m_product_id, c_bpartner_id, … FROM ranked_po WHERE rn = 1;
```

---

## 9. Validation Coverage Matrix

Every BI-03 problem code must have at least one rule and one testable assertion.

| BI-03 code | Problem | Rule(s) | Testable assertion |
|---|---|---|---|
| E-01 | All CSVs UTF-8-SIG (BOM) | ENC-01 | `open(path,'rb').read(3) in (b'\xef\xbb\xbf', b'')` |
| E-02 | All CSVs `;` delimiter | ENC-02 | `csv.Sniffer().sniff(first_line).delimiter == ';'` |
| E-03 | xlsx UTF-8 (positive) | ENC-03 | No assertion needed (positive finding) |
| D-01 | DATEACCT always midnight | DATE-01 | `SELECT COUNT(*) FROM fact_sales_order WHERE DATE_PART('hour', dateacct) != 0 -- expect 0 (dateacct is DATE)` |
| D-02 | Occasional midnight DATEINVOICED | DATE-02 | `SELECT COUNT(*) FROM stg_c_invoice WHERE dateinvoiced IS NULL -- baseline` |
| D-03 | dd/mm/yyyy in POREFERENCE | DATE-03 | `poreference` column type = TEXT; no date parse attempted |
| N-01 | No comma decimal (positive) | NORM-07 | `SELECT COUNT(*) FROM stg_c_order WHERE GRANDTOTAL::text ~ ','  -- expect 0` |
| N-02 | No space thousands (positive) | NORM-07 | `SELECT COUNT(*) FROM stg_c_order WHERE GRANDTOTAL::text ~ ' [0-9]'  -- expect 0` |
| N-03 | 0 as FK null sentinel | FK-NULL-01 | `SELECT COUNT(*) FROM stg_c_order WHERE c_doctype_id = 0  -- expect 0` |
| C-01 | Amount columns plain numeric (positive) | CUR-01 | `NUMERIC(18,4)` cast succeeds for all rows |
| C-02 | Currency in free-text COMMENTAIRE | CUR-02 | `COMMENTAIRE` stored as TEXT; no currency parse |
| C-03 | False-positive currency regex (positive) | CUR-03 | No regex applied to NAME columns in ETL |
| W-01 | Trailing spaces | NORM-01 | `SELECT COUNT(*) FROM dim_customer WHERE name <> TRIM(name)  -- expect 0` |
| W-02 | Embedded tabs in dim NAME | NORM-02 | `SELECT COUNT(*) FROM dim_product_category WHERE name ~ E'\\t'  -- expect 0` |
| W-04 | Trailing newline in VALUE | NORM-02 | `SELECT COUNT(*) FROM dim_product_category WHERE value ~ E'\\n'  -- expect 0` |
| M-01 | `'--'` in DOCACTION | NORM-04 | DOCACTION not loaded into warehouse; assertion: no DOCACTION column in dim_document_type |
| M-02 | `'-'` in POREFERENCE → NULL | NORM-05 | `SELECT COUNT(*) FROM stg_c_invoice WHERE poreference = '-'  -- expect 0` |
| M-03 | `'NA'` in C_BPARTNER | NORM-06 / FILT-08 | `SELECT COUNT(*) FROM dim_customer WHERE value = 'NA'  -- expect 0` |
| K-01 | All-caps names | NORM-03 | `SELECT COUNT(*) FROM dim_customer WHERE name = UPPER(name) AND LENGTH(name) > 2  -- expect 0` |
| K-02 | All-lowercase commercial | NORM-03 | `SELECT COUNT(*) FROM dim_commercial WHERE name = LOWER(name) AND LENGTH(name) > 2  -- expect ~0` |

**Dedup key validation (BI-02 PKs):**
```sql
-- After staging load: verify PK uniqueness is preserved (within each source_tag window)
SELECT c_order_id, COUNT(*) FROM stg_c_order GROUP BY c_order_id HAVING COUNT(*) > 1;  -- expect 0 rows
SELECT c_invoice_id, COUNT(*) FROM stg_c_invoice GROUP BY c_invoice_id HAVING COUNT(*) > 1;  -- expect 0 rows
```

---

## 10. Rule Dependency Graph

```
Source file
    │
    ├─ [ENC-01/02/03] Encoding & delimiter
    ├─ [TYP-01..05]   Type coercion
    └─ [FK-NULL-01]   Zero FK → NULL
                │
          stg_* tables
                │
                ├─ [DEDUP-00]       Period assignment (2024 vs 2025-2026)
                ├─ [DEDUP-SUPP-01]  Primary supplier selection
                ├─ [FILT-01..11]    Business filter (DOCSTATUS, ISSOTRX, etc.)
                └─ [SENT-01]        DOCACTION '--' retention
                        │
                  dim_* / fact_*
                        │
                        ├─ [NORM-01]   TRIM all text
                        ├─ [NORM-02]   REGEXP_REPLACE embedded tabs/newlines
                        ├─ [NORM-03]   INITCAP for name fields
                        ├─ [NORM-04]   '--' kept as-is (not loaded)
                        ├─ [NORM-05]   '-' → NULL in POREFERENCE
                        ├─ [NORM-06]   'NA' → excluded by FILT-08
                        ├─ [NORM-07]   No decimal transform (positive)
                        ├─ [NORM-08]   C_ORDER_ID null on invoices is valid
                        ├─ [DATE-01]   DATEACCT → DATE
                        ├─ [DATE-02]   DATEINVOICED TIMESTAMPTZ → DATE key
                        ├─ [DATE-03]   POREFERENCE stays TEXT
                        ├─ [CUR-01..03] Currency: no transform needed
                        └─ [TRAP-01]   57_PORTFOLIO excluded
```

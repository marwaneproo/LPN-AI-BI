# BI-07 — Warehouse Dim/Fact Design Alignment

**Task:** BI-07  
**Date:** 2026-06-25  
**Branch:** `bi-polished-dashboard`  
**Inputs:** `02_star_schema_design.md`, `03_fact_grain_design.md`, `BI-04 entity catalogue`,  
&emsp;&emsp;&emsp;&emsp;`DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql`,  
&emsp;&emsp;&emsp;&emsp;ETL output CSVs (`etl/output/dimensions/`, `etl/output/facts/`)  
**DDL file:** `docs/bi-service-roadmap/sql/warehouse.sql`  
**Validation:** sqlglot 30.11.0 — 0 parse errors ✅  
**Status:** ✅ Complete (design only — DDL not executed)

---

## 1. Scope

This document reconciles the prior-art `draft_schema_design.sql` with:

1. Confirmed staging column names from BI-06 (actual ETL output CSV headers)
2. Exact fact grains from `03_fact_grain_design.md`
3. Star schema decisions from `02_star_schema_design.md`
4. BI-04 entity→page mapping

All changes versus the prototype are recorded as explicit **DECISION-xx** entries (§3).

**What is NOT changed:**

- SCD1 approach for all dims
- BIGSERIAL surrogate keys (except dim_date which uses INTEGER)
- FK naming convention (`<entity>_key`)

---

## 2. Warehouse Architecture Summary

```
staging.*            ──ETL──>    warehouse.dim_*      <──┐
                             warehouse.fact_*     ───┤
                                                      │
                                                   mart.mart_*   (BI-08)
                                                      │
                                              BI API / Spring endpoints (BI-10)
                                                      │
                                         5 frontend BI pages (BI-12)
```

Execution order per `01_warehouse_layers.md`:

1. `dim_date` (no deps)
2. Simple ref dims (`dim_payment_term`, `dim_price_list`, `dim_document_type`, `dim_product_category`, `dim_sales_region`)
3. Entity dims with no cross-dim FK (`dim_customer`, `dim_commercial`, `dim_supplier`, `dim_warehouse`)
4. Cross-ref dims (`dim_geography → dim_sales_region`, `dim_product → dim_product_category + dim_supplier`)
5. Facts (all dims must be loaded first)

---

## 3. Decisions vs Prototype

### DECISION-01 — Schema name corrected to `warehouse`

The prototype draft used the old prototype warehouse schema name, but the locked BI
architecture and warehouse rebuild roadmap require `staging` / `warehouse` / `mart`.
This correction aligns the BI-07 DDL draft to `warehouse` and the BI-08 target to `mart`.

`stg_*` remains the table-name prefix inside the `staging` schema. The auxiliary
`etl` schema remains separate for run logs, table stats, and rejects.

### DECISION-02 — Add UNKNOWN surrogate row INSERTs (key = 0) for all dims

The prototype had no INSERT statements. The ETL output CSVs confirm that every dimension
contains a row with `<dim>_key = 0` and name `'Unknown / Non renseigné'`.

`warehouse.sql` now includes one `INSERT … ON CONFLICT DO NOTHING` per dimension.  
All numeric fields = 0, all text fields = `'Unknown / Non renseigné'` or NULL, all booleans = NULL.

Special case: `dim_date.date_key = 0` — integer, not BIGSERIAL. Insert directly.  
All other dims use `OVERRIDING SYSTEM VALUE` to force key=0 past the SERIAL default.

Post-insert sequence guards reset each sequence to `MAX(key)` so subsequent SERIAL inserts
start at 1 (idempotent via `setval(... GREATEST(1, MAX(...)))`).

### DECISION-03 — `dim_date` PK is `INTEGER` (YYYYMMDD), not BIGSERIAL

Confirmed from ETL output header: `date_key` column stores integer values like `20260115`.  
Unknown row: `date_key = 0`, numeric fields = 0, `month_name = 'Unknown / Non renseigné'`.  
Range: 20240101 – 20261231 (~1,096 rows). No surrogate sequence needed.

This is a change from the prototype which used `BIGSERIAL`.

### DECISION-04 — SCD2 placeholder columns kept on `dim_customer` and `dim_product`

`02_star_schema_design.md` notes SCD Type 2 is reserved for dim_customer, dim_product,
dim_supplier, dim_commercial in a future version. `01_warehouse_layers.md` mandates that
`effective_from`, `effective_to`, `is_current` be present now.

In v1 (SCD1 behaviour):
- `effective_from` = NULL
- `effective_to` = NULL
- `is_current` = TRUE

The columns are present in the DDL but not populated. ETL output confirms NULL values.

### DECISION-05 — `fact_stock_snapshot`: add `m_locator_id BIGINT` degenerate dimension

**Problem:** The prototype DDL did not include `m_locator_id`. The staging composite PK for
`stg_rv_storage` is `(m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id, _source_tag)`.
Without `m_locator_id` in the fact, multiple staging rows with the same product/attributeset/warehouse
but different locators would either cause duplicate rows or silent merge errors.

**Evidence from ETL output:** Two rows with same `(m_product_id=9481, m_attribute_set_instance_id=0,
warehouse_key=1)` but different `quantity_available` values (17 vs 0) — distinct locators.

**Resolution:** Add `m_locator_id BIGINT` as a degenerate dimension to `fact_stock_snapshot`.  
The natural grain is `(m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id)`
at snapshot date — consistent with `stg_rv_storage` PK.

### DECISION-06 — Confirm all 8 facts

`02_star_schema_design.md` lists 8 facts. Enumerated:
`sales_order`, `sales_order_line`, `invoice`, `invoice_line`, `delivery`, `delivery_line`,
`payment_allocation`, `stock_snapshot`.

The BI-07 prompt listed 8 facts identically. Both `fact_sales_order_line` and
`fact_delivery_line` are explicitly confirmed. No removal — all 8 are built.

### DECISION-07 — `fact_payment_allocation.commercial_key` kept, resolves via invoice join

`03_fact_grain_design.md`: "Commercial attribution is not reliable from the allocation source
directly. Resolve via C_INVOICE_ID → C_INVOICE.SALESREP_ID when possible."

ETL output confirms `commercial_key` column is present, value = 0 (unknown) for most rows.
Column kept in DDL. ETL will attempt resolution; fallback = 0.

### DECISION-08 — `fact_delivery.document_type_key` often resolves to 0 (unknown)

`dim_document_type` is filtered to `ISSALESTRANSACTION = 'Y'` (FILT-11 in BI-05).
Delivery document types (M_INOUT) often have `ISSALESTRANSACTION = 'N'` and are excluded
from the dim. ETL output confirms `document_type_key = 0` for delivery fact rows.

Column retained for forward-compatibility. Mart queries joining delivery facts should
`LEFT JOIN` on this column and tolerate the unknown value.

### DECISION-09 — `dim_warehouse.warehouse_name` sourced from `stg_m_warehouse.name`

The prototype had `warehouse_name varchar(255)` but the ETL output showed NULL for the
singleton warehouse. This is an ETL build issue (wrong column alias), not a DDL issue.
DDL remains unchanged; the correct source column is `stg_m_warehouse.name`.
**BI-09 must join on `stg_m_warehouse` and use column `name` (not `value`) for warehouse_name.**

### DECISION-10 — `dim_product.theme_name` / `.collection_name`: denormalized TEXT

Both columns appear in the ETL dim_product output as direct TEXT values (not surrogate keys
to separate dim tables). The prototype draft did not define separate dim_theme or dim_collection
tables. This design keeps them as denormalized attributes on dim_product.

If theme/collection analytics are later required as separate cut dimensions, a new dim can be
added without breaking the existing dim_product PK.

---

## 4. Dimension Inventory

| Dim table | Key type | Grain | Unknown key | SCD | Source tables |
|---|---|---|---|---|---|
| `dim_date` | INTEGER (YYYYMMDD) | 1 row / calendar day | 0 | SCD0 | Generated |
| `dim_customer` | BIGSERIAL | 1 row / customer BP | 0 | SCD1 (SCD2 ready) | stg_c_bpartner |
| `dim_commercial` | BIGSERIAL | 1 row / salesrep | 0 | SCD1 | stg_ad_user |
| `dim_product_category` | BIGSERIAL | 1 row / category | 0 | SCD1 | stg_m_product_category |
| `dim_supplier` | BIGSERIAL | 1 row / vendor BP | 0 | SCD1 | stg_c_bpartner_vendor |
| `dim_product` | BIGSERIAL | 1 row / product | 0 | SCD1 (SCD2 ready) | stg_m_product + enrichment |
| `dim_sales_region` | BIGSERIAL | 1 row / sales region | 0 | SCD1 | stg_c_salesregion |
| `dim_geography` | BIGSERIAL | 1 row / (BP_loc, location) | 0 | SCD1 | stg_c_bpartner_location + stg_c_location |
| `dim_payment_term` | BIGSERIAL | 1 row / payment term | 0 | SCD1 | stg_c_paymentterm |
| `dim_price_list` | BIGSERIAL | 1 row / price list | 0 | SCD1 | stg_m_pricelist |
| `dim_document_type` | BIGSERIAL | 1 row / doc type (sales only) | 0 | SCD1 | stg_c_doctype |
| `dim_warehouse` | BIGSERIAL | 1 row / (warehouse, locator) | 0 | SCD1 | stg_m_warehouse + locator |

**Total: 12 dimensions** (prototype listed 10 explicitly; dim_sales_region and dim_warehouse
are the two additional dims confirmed by the FK chain in dim_geography and fact_delivery/stock).

---

## 5. Fact Inventory

| Fact table | Grain | Grain doc ref | Primary measure | Semi-additive? |
|---|---|---|---|---|
| `fact_sales_order` | 1 row / C_ORDER_ID | §2.1 | `grand_total_amount` | No |
| `fact_sales_order_line` | 1 row / C_ORDERLINE_ID | §2.2 | `line_net_amount` | No |
| `fact_invoice` | 1 row / C_INVOICE_ID | §2.3 | `grand_total_amount` | No |
| `fact_invoice_line` | 1 row / C_INVOICELINE_ID | §2.4 | `line_net_amount` | No |
| `fact_delivery` | 1 row / M_INOUT_ID | §2.5 | `delivery_count` | No |
| `fact_delivery_line` | 1 row / M_INOUTLINE_ID | §2.6 | `movement_quantity` | No |
| `fact_payment_allocation` | 1 row / C_ALLOCATIONLINE_ID | §2.7 | `allocated_amount` | No |
| `fact_stock_snapshot` | 1 row / (product, attrset, warehouse, locator) | §2.8 | `quantity_on_hand` | Yes (snapshot) |

**Total: 8 facts** as required.

---

## 6. Grain Confirmation vs `03_fact_grain_design.md`

| Fact | Grain doc grain | Warehouse DDL grain | Match |
|---|---|---|---|
| fact_sales_order | C_ORDER_ID | `UNIQUE (c_order_id)` | ✅ |
| fact_sales_order_line | C_ORDERLINE_ID | `UNIQUE (c_orderline_id)` | ✅ |
| fact_invoice | C_INVOICE_ID | `UNIQUE (c_invoice_id)` | ✅ |
| fact_invoice_line | C_INVOICELINE_ID | `UNIQUE (c_invoiceline_id)` | ✅ |
| fact_delivery | M_INOUT_ID | `UNIQUE (m_inout_id)` | ✅ |
| fact_delivery_line | M_INOUTLINE_ID | `UNIQUE (m_inoutline_id)` | ✅ |
| fact_payment_allocation | C_ALLOCATIONLINE_ID | `UNIQUE (c_allocationline_id)` | ✅ |
| fact_stock_snapshot | (m_product_id, m_attributesetinstance_id, m_warehouse_id, m_locator_id) | degenerate cols — no UNIQUE constraint; DECISION-05 adds m_locator_id | ✅ |

All 8 grains confirmed against `03_fact_grain_design.md`.

---

## 7. Degenerate Dimension Summary

Degenerate dimensions are natural business keys stored directly in the fact table.
No FK to another table; used for filtering and drill-through.

| Column | Present in | Purpose |
|---|---|---|
| `c_order_id` | fact_sales_order | Natural PK (also UNIQUE constraint) |
| `c_order_id` (via `source_c_order_id`) | fact_invoice, fact_delivery | Bridge to order; 29.9% NULL in fact_invoice |
| `c_orderline_id` | fact_sales_order_line, fact_invoice_line, fact_delivery_line | Bridge across 3 fact layers |
| `c_invoice_id` | fact_invoice_line, fact_payment_allocation | Bridge for reconciliation |
| `c_payment_id` | fact_payment_allocation | Payment reference |
| `document_no` | all facts | Human-readable business key |
| `doc_status` | fact_sales_order, fact_sales_order_line, fact_invoice, fact_delivery | Source status code (CO/CL/DR) |
| `availability_indicator` | fact_delivery_line | LPN stock disponibility flag |
| `m_attribute_set_instance_id` | fact_stock_snapshot | Product variant identifier |
| `m_locator_id` | fact_stock_snapshot | Storage locator (DECISION-05) |

---

## 8. Measure Additivity Rules

| Measure | Additive over time | Additive over product | Additive over customer | Notes |
|---|---|---|---|---|
| `grand_total_amount` (order/invoice) | ✅ | ✅ | ✅ | Primary CA metric |
| `line_net_amount` (orderline/invoiceline) | ✅ | ✅ | ✅ | Product-level CA |
| `quantity_*` (order line, invoice line, delivery) | ✅ | ✅ | ✅ | |
| `price_list`, `price_actual`, `discount_percent` | ❌ | ❌ | ❌ | Non-additive; use weighted avg |
| `allocated_amount`, `discount_amount`, `writeoff_amount` | ✅ | ✅ | ✅ | Payment metrics |
| `payment_amount` | ⚠️ | ⚠️ | ⚠️ | Caution: header value repeats across allocation lines; aggregate with care |
| `quantity_on_hand`, `quantity_available`, `quantity_reserved` | ❌ | ✅ | — | **Semi-additive**: sum by product, NOT across time |
| `*_count` columns | ✅ | ✅ | ✅ | All additive |

---

## 9. Frontend Page Mapping

| Frontend page | Primary fact(s) | Primary dims | Key metric |
|---|---|---|---|
| `BiCommandesPage` | fact_sales_order | dim_customer, dim_commercial, dim_date | CA commandes (grand_total_amount) |
| `BiRevenuePage` | fact_invoice, fact_invoice_line | dim_customer, dim_product, dim_date | CA facturé (grand_total_amount) |
| `BiArticlesPage` | fact_invoice_line | dim_product, dim_product_category, dim_date | Top articles, CA/qty par article |
| `BiClientsPage` | fact_invoice, fact_invoice_line, fact_sales_order | dim_customer, dim_geography, dim_date | CA client, fidélité |
| `BiCommercialPage` | fact_sales_order, fact_invoice | dim_commercial, dim_customer, dim_date | CA par commercial, objectifs |

Stock snapshot feeds a potential 6th widget (articles en rupture) via `fact_stock_snapshot`.

---

## 10. Constraints and Indexes

### FK Naming Pattern

All FKs follow: `fk_<fact>_<dim>` (e.g., `fk_fact_invoice_dim_customer`).  
Implemented as `REFERENCES warehouse.dim_customer(customer_key)` inline constraints.

### Indexes

Every fact table gets indexes on:
- All dimension FK columns (selectivity for BI filters)
- Date FK columns (range scan for time-series queries)
- Bridge degenerate dims (`c_invoice_id`, `c_order_id`, `c_orderline_id`) for reconciliation joins

Index naming: `idx_<table_abbrev>_<column>` (e.g., `idx_fi_customer`, `idx_fi_invoice_date`).

---

## 11. DDL Validation

```
File    : docs/bi-service-roadmap/sql/warehouse.sql
Command : python -c "import sqlglot; sqlglot.parse(open('warehouse.sql').read(), dialect='postgres'); print('OK')"
Result  : OK — 0 parse errors
Tool    : sqlglot 30.11.0
Date    : 2026-06-25
```

---

## 12. Next Steps

- **BI-08** — Mart design: `CREATE VIEW mart_*` for 8 frontend-facing views on top of dim_*/fact_*
- **BI-09** — ETL implementation plan: Python scripts staging.sql → ETL load → warehouse.sql
- **BI-10** — BI API contract & DTO design (Spring Boot endpoints consuming mart_*)

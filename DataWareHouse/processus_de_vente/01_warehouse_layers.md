# Data Warehouse Layers - Processus de Vente

## Scope

This document defines the target data warehouse architecture for the LPN `processus de vente` domain. It covers sales orders, order lines, invoices, invoice lines, deliveries, payments, stock snapshots, customers, commercials, products, suppliers, geography, payment terms, price lists, and document types.

The design is based on the validated extraction packages under:

- `Youssef_Extractions/vente_clean_import`
- `Youssef_Extractions/vente_bi_enrichment_import`
- `Youssef_Extractions/4th_Extraction`

Commission/objective tables were checked and are not populated for the current LPN scope. Contracts were not found in the sales discovery output. They are therefore excluded from the first warehouse version.

## Layer Overview

The warehouse uses four logical layers:

| Layer | Prefix | Purpose |
|---|---|---|
| Staging | `stg_*` | Load raw cleaned exports with minimal transformation. |
| Dimensions | `dim_*` | Store reusable business descriptors used to slice facts. |
| Facts | `fact_*` | Store measurable business events at a clearly defined grain. |
| Marts | `mart_*` | Expose BI-ready reporting views for Power BI and frontend dashboards. |

The layers must remain separated. Staging tables mirror source extracts. Dimensions describe business entities. Facts store measures and keys. Marts combine facts and dimensions for reporting convenience.

## 1. Staging Layer - `stg_*`

### Purpose

The staging layer stores the cleaned ERP/Excel/CSV exports as close as possible to the source. It is the audit trail between the source files and the modeled warehouse.

### Data Contained

Staging contains:

- cleaned CSV output from canonical ERP exports.
- additive enrichment exports such as `AD_USER`, `M_PRODUCT_PO`, product themes/types/collections.
- fourth extraction dimensions such as payment terms, partner locations, city, region, sales region, and price lists.
- optional wide validation files such as the duplicate denormalized sales extract `3.xlsx`/`4.xlsx`; only one should be retained if used.

### Naming Convention

Use lowercase table names:

- `stg_c_order`
- `stg_c_orderline`
- `stg_c_invoice`
- `stg_c_invoiceline`
- `stg_c_bpartner`
- `stg_m_product`
- `stg_ad_user`
- `stg_m_product_po`
- `stg_c_bpartner_location`
- `stg_c_location`
- `stg_c_region`
- `stg_c_city`

### Expected Input

Input is file-based:

- CSVs generated from `vente_clean_import`.
- CSVs generated from `vente_bi_enrichment_import`.
- future CSV conversions from `4th_Extraction`.

### Expected Output

The staging layer outputs records to:

- dimension build queries.
- fact build queries.
- reconciliation checks.

No Power BI report should use staging directly except for data quality/debugging pages.

### Example Tables

| Staging table | Source |
|---|---|
| `stg_c_order` | `C_ORDER.csv` |
| `stg_c_orderline` | `C_ORDERLINE.csv` |
| `stg_c_invoice` | `C_INVOICE.csv` |
| `stg_c_invoiceline` | `C_INVOICELINE.csv` |
| `stg_c_payment` | `C_PAYMENT.csv` |
| `stg_c_allocationline` | `C_ALLOCATIONLINE.csv` |
| `stg_m_inout` | `M_INOUT.csv` |
| `stg_m_inoutline` | `M_INOUTLINE.csv` |
| `stg_rv_storage` | `RV_STORAGE.csv` |
| `stg_ad_user` | `AD_USER.csv` |
| `stg_m_product_po` | `M_PRODUCT_PO.csv` |
| `stg_c_bpartner_vendor` | `C_BPARTNER_VENDOR.csv` |

### Data Quality Rules

- Preserve all source identifiers.
- Preserve source dates and numeric values.
- Store file snapshot metadata where possible: source file, import batch id, imported at.
- Do not deduplicate fact-like source rows unless the source file is a known duplicate.
- Validate row counts against extraction manifests.
- Keep both source IDs and source document numbers.
- Reject or quarantine rows that cannot be parsed into core technical types.

### BI Support

Staging supports BI by guaranteeing traceability. If a Power BI number is questioned, the data engineer can trace it back through facts, dimensions, staging tables, and finally the source file.

## 2. Dimension Layer - `dim_*`

### Purpose

The dimension layer turns ERP technical tables into business-friendly entities. Dimensions provide labels, grouping, filtering, and hierarchy for facts.

### Data Contained

Dimensions contain relatively stable descriptive data:

- customers and customer groups.
- commercials.
- products and product taxonomy.
- suppliers.
- geography.
- payment terms.
- price lists.
- document types.
- warehouses and sales regions.
- dates.

### Naming Convention

Use singular business names:

- `dim_date`
- `dim_customer`
- `dim_commercial`
- `dim_product`
- `dim_supplier`
- `dim_geography`
- `dim_document_type`

### Key Strategy

Each dimension uses:

- a surrogate key, for example `customer_key`.
- one or more natural keys from the ERP, for example `c_bpartner_id`.

Surrogate keys keep the warehouse stable even if ERP identifiers or labels evolve. Natural keys preserve traceability.

### Expected Input

Dimensions are built from staging tables:

- `dim_customer` from `stg_c_bpartner`, `stg_c_bp_group`, `stg_c_bpartner_location`, `stg_c_location`.
- `dim_commercial` from `stg_ad_user`.
- `dim_product` from `stg_m_product` plus product type/theme/collection/category.
- `dim_supplier` from `stg_c_bpartner_vendor` and `stg_m_product_po`.

### Expected Output

Dimensions output surrogate keys and attributes to fact tables and marts.

### Data Quality Rules

- Every dimension must include an `unknown` row with surrogate key `0` or `-1`.
- Natural keys must be unique inside their active dimension version.
- Labels must be trimmed and normalized for display.
- Missing names must fall back to stable technical labels, such as `Commercial <id>`.
- If slowly changing dimensions are implemented later, keep `effective_from`, `effective_to`, and `is_current`.

### BI Support

Power BI should filter facts through dimensions. This gives clean slicers:

- Commercial
- Client
- Region
- Product category
- Supplier
- Payment term
- Price list

## 3. Fact Layer - `fact_*`

### Purpose

The fact layer stores measurable business process events. Each fact table must have a precise grain.

### Data Contained

Facts contain:

- foreign keys to dimensions.
- source document numbers as degenerate dimensions.
- measures such as amount, quantity, count flags, paid amount, delivered quantity, stock quantity.
- operational dates as foreign keys to `dim_date`.

### Naming Convention

Use event-oriented names:

- `fact_sales_order`
- `fact_sales_order_line`
- `fact_invoice`
- `fact_invoice_line`
- `fact_delivery`
- `fact_delivery_line`
- `fact_payment_allocation`
- `fact_stock_snapshot`

### Expected Input

Facts are built from staging tables and dimension lookup results.

### Expected Output

Facts output analytic measures to mart views and Power BI datasets.

### Data Quality Rules

- Grain must be declared and enforced.
- Fact natural identifiers must be unique for that grain.
- Measures must not be double-counted across header and line grains.
- Header totals and line totals must be reconciled where possible.
- Date keys must resolve to `dim_date`.
- Missing dimension lookups must use the unknown dimension row, not null foreign keys.
- Additive, semi-additive, and non-additive measures must be documented.

### BI Support

Fact tables are the numerical backbone of Power BI. They allow:

- order value by date/commercial/client/product.
- invoiced CA by period.
- delivery completion.
- paid/unpaid invoice analysis.
- supplier contribution.
- stock availability snapshots.

## 4. Mart Layer - `mart_*`

### Purpose

The mart layer exposes report-ready views to Power BI and the frontend. It simplifies analytics while preserving the modeled warehouse behind it.

### Data Contained

Marts contain joined, aggregated, or curated views such as:

- monthly CA by commercial.
- top customers by invoiced revenue.
- order-to-invoice flow.
- supplier contribution by product.
- sales geography.

### Naming Convention

Use BI-use-case names:

- `mart_sales_overview`
- `mart_sales_by_commercial`
- `mart_sales_by_customer`
- `mart_sales_by_product`
- `mart_sales_by_supplier`
- `mart_sales_by_region`
- `mart_order_to_invoice_flow`
- `mart_payment_status`

### Expected Input

Marts read from `fact_*` and `dim_*` tables only. They should not read directly from staging unless explicitly labeled as a data quality mart.

### Expected Output

Marts are consumed by:

- Power BI reports.
- frontend deterministic dashboard APIs.
- validation notebooks/scripts.

### Data Quality Rules

- Mart measures must reconcile to the underlying fact tables.
- Mart names and column names must be business-friendly.
- Avoid mixing grains in a single mart unless the column names make that explicit.
- Store reusable Power BI logic in marts where possible instead of repeating calculations in reports.

### BI Support

The mart layer makes Power BI easier for future interns and business users. It lets them report on clean tables instead of navigating raw ERP exports.

## Confirmed Design Decisions

- The warehouse is scoped to `processus de vente`.
- Orders, invoices, deliveries, payments, stock, products, clients, commercials, suppliers, geography, payment terms, price lists, and sales regions are included.
- Commission/objective tables are excluded from version 1 because extracted tables were empty or obsolete.
- Contracts are excluded from version 1 because discovery did not find contract/agreement tables.
- Supplier analytics uses a primary supplier mapping per product to avoid duplicating revenue when products have multiple supplier rows.

## Assumptions

- `C_ORDER` and `C_ORDERLINE` represent sales orders and lines already filtered to vente scope.
- `C_INVOICE` and `C_INVOICELINE` represent customer invoices and credit memos already filtered to vente scope.
- `C_ALLOCATIONLINE` is the reliable bridge between invoices and payments.
- `3.xlsx` and `4.xlsx` are duplicate wide exports and should be retained only for validation unless intentionally modeled as a separate wide staging table.
- Existing extracts are sufficient for the current POC and near-term BI. Wider historical extraction is needed later for full yearly and seasonality analysis.

## Validation Expectations

Before implementation, every load should validate:

- source row counts against manifests.
- primary key uniqueness at declared grain.
- dimension lookup coverage.
- order totals vs order-line sums.
- invoice totals vs invoice-line sums.
- payment allocations linked to invoices and payments.
- no duplicate loading of `3.xlsx` and `4.xlsx`.

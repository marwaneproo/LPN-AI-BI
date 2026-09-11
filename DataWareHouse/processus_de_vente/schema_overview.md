# Schema Overview - Processus de Vente Data Warehouse

## Purpose

This overview summarizes the proposed warehouse model for the LPN sales process. It is intended for future interns, BI developers, data engineers, and project reviewers.

The current phase now covers:

- Step 1: warehouse layer definition.
- Step 2: star schema design.
- Step 3: exact fact-grain design.
- Step 4: first repeatable ETL prototype using the current extracted data.

It does not execute a final production database deployment yet.

## Model Summary

The warehouse uses a classic star schema around sales-process facts:

- orders.
- order lines.
- invoices.
- invoice lines.
- deliveries.
- delivery lines.
- payment allocations.
- stock snapshots.

Shared dimensions provide customer, commercial, product, supplier, date, geography, payment term, price list, document type, sales region, and warehouse context.

## Dimensions

| Dimension | Purpose |
|---|---|
| `dim_date` | Common calendar for order, invoice, delivery, payment, and stock dates. |
| `dim_customer` | Customer/client information and customer group. |
| `dim_commercial` | Sales representative/commercial information. |
| `dim_product` | Product/article information including type, theme, collection, and supplier. |
| `dim_product_category` | Product family/category grouping. |
| `dim_supplier` | Fournisseur/vendor reference for product-supplier sales analysis. |
| `dim_geography` | Customer location, city, region, and site information. |
| `dim_payment_term` | Payment term labels and due rules. |
| `dim_price_list` | Sales price list/channel context. |
| `dim_document_type` | ERP document type labels and base types. |
| `dim_sales_region` | Sales region/axis reference. |
| `dim_warehouse` | Warehouse and locator context. |

## Facts

| Fact | Grain | Main measures |
|---|---|---|
| `fact_sales_order` | One row per sales order header. | order count, total lines, grand total. |
| `fact_sales_order_line` | One row per sales order line. | ordered qty, delivered qty, invoiced qty, line net amount. |
| `fact_invoice` | One row per invoice header. | invoice count, grand total, paid/unpaid count. |
| `fact_invoice_line` | One row per invoice line. | invoiced qty, line net amount. |
| `fact_delivery` | One row per delivery header. | delivery count. |
| `fact_delivery_line` | One row per delivery line. | movement qty, entered qty. |
| `fact_payment_allocation` | One row per allocation line. | allocated amount, discount, write-off, over/under amount. |
| `fact_stock_snapshot` | One row per product/warehouse/locator/snapshot. | on hand, reserved, available, ordered stock. |

## High-Level Relationship Map

```text
dim_date          -> all facts through business date keys
dim_customer      -> orders, order lines, invoices, invoice lines, deliveries, payments
dim_commercial    -> orders, order lines, invoices, invoice lines
dim_product       -> order lines, invoice lines, delivery lines, stock snapshot
dim_supplier      -> product-related facts through primary product supplier
dim_geography     -> orders, invoices, deliveries
dim_document_type -> orders, invoices, deliveries, payments
dim_payment_term  -> orders, invoices
dim_price_list    -> orders, invoices
dim_warehouse     -> orders, order lines, deliveries, delivery lines, stock snapshot
dim_sales_region  -> geography and region-based reporting
```

## Recommended Power BI Usage

Power BI should use:

- `mart_*` outputs first for simple executive dashboards.
- `dim_*` tables for slicers and filters when deeper star-schema reporting is needed.
- `fact_*` tables for detailed measures and drill-down.
- `stg_*` outputs only for debugging and validation, not presentation.

Recommended report pages:

- Sales overview.
- Sales by commercial.
- Sales by client.
- Sales by supplier.
- Sales by product/category/type/theme/collection.
- Sales by city/region.
- Order-to-invoice-to-delivery flow.
- Payment and unpaid invoice status.
- Stock risk for sold products.

## Important Assumptions

- The vente source extracts are already scoped to the sales process.
- `C_ALLOCATIONLINE` is the reliable bridge between invoice and payment.
- Supplier analytics uses one primary supplier per product to avoid duplicated CA.
- `3.xlsx` and `4.xlsx` are duplicate wide validation exports; only one should be retained if used.
- Commission/objective tables are empty or obsolete and are excluded from v1.
- Contracts were not found in discovery and are excluded from v1.

## Resolved Questions

- **SCD strategy:** current prototype uses Type 1 dimensions. Type 2 is reserved for a future production phase when historical change tracking is available.
- **Power BI strategy:** use `mart_*` for first reporting, then `dim_*` and `fact_*` for advanced model pages.
- **Data history:** use the current extracted data now; export 24 months later for yearly trends.

## Open Questions For Later Validation

- Should the denormalized `3.xlsx` export be used as a validation-only table or as a wide staging source?
- Should future phases include achats/purchase process and margin analysis?
- Should the final production warehouse be materialized in PostgreSQL, SQL Server, or another BI-oriented storage layer?

## Current Status

Step 1 through Step 4 are complete at prototype level:

- layer architecture defined.
- dimensions identified.
- fact tables defined with grain.
- draft DDL prepared.
- ERD prepared.
- repeatable ETL scripts created.
- staging, dimension, fact, and mart CSV outputs generated.
- ETL validation report generated with PASS status.

Step 3 and Step 4 are not started.

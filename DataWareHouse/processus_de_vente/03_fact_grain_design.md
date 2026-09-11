# Fact Grain Design - Processus de Vente

## Purpose

This document completes Step 3 of the sales-process data warehouse work. It defines the exact grain of every fact table so Power BI, ETL scripts, and future interns do not mix header-level and line-level measures.

The rule is simple: every fact table must answer one clear question at one clear level of detail.

## Confirmed Design Decisions

### SCD Strategy

Slowly Changing Dimensions describe how dimension changes are handled over time.

- **Type 1:** overwrite the old value with the new value. Example: if a customer name is corrected, the dimension keeps only the corrected name.
- **Type 2:** keep history by creating a new versioned row with `effective_from`, `effective_to`, and `is_current`. Example: if a customer changes commercial, reports can still show old orders under the old commercial assignment.

For the current prototype, the warehouse uses **SCD Type 1** for business dimensions. This is the correct first step because the current source files are extracts, not a full historical change log. Type 2 columns can stay in the design for future production, but they are not populated as historical versions yet.

Recommended future upgrade: use **Type 2 only** for high-value dimensions where historical attribution matters, mainly `dim_customer`, `dim_product`, `dim_supplier`, and possibly `dim_commercial`.

### Power BI Connection Strategy

Power BI should start with `mart_*` outputs for simple executive dashboards because marts already contain clean, business-ready metrics.

For deeper analysis, Power BI can also import the `dim_*` and `fact_*` CSVs and build a star model directly. Staging tables should not be used in Power BI except for debugging.

### Data History Scope

The current ETL uses only the data currently available in the project extraction folders. This is enough for validating the model and building the first BI pages.

For yearly trends, seasonal analysis, and director-level historical reporting, export a wider window later, ideally **24 months** of sales process data, then rerun the same ETL.

## Fact Table Grain Summary

| Fact table | Exact grain | Primary analysis |
|---|---|---|
| `fact_sales_order` | One row per sales order header from `C_ORDER`. | Order count and order value. |
| `fact_sales_order_line` | One row per sales order line from `C_ORDERLINE`. | Product/category/supplier ordered sales. |
| `fact_invoice` | One row per invoice header from `C_INVOICE`. | Invoiced CA, paid/unpaid status. |
| `fact_invoice_line` | One row per invoice line from `C_INVOICELINE`. | Product/category/supplier invoiced CA. |
| `fact_delivery` | One row per delivery header from `M_INOUT`. | Delivery volume and delivery status. |
| `fact_delivery_line` | One row per delivery line from `M_INOUTLINE`. | Delivered quantities by product and warehouse. |
| `fact_payment_allocation` | One row per allocation line from `C_ALLOCATIONLINE`. | Payment matching, allocated amounts, discounts. |
| `fact_stock_snapshot` | One row per product/attribute/warehouse snapshot from `RV_STORAGE`. | Stock on hand, reserved, available, ordered. |

## `fact_sales_order`

**Business event:** A customer sales order is created or exists in the ERP.

**Exact grain:** One row per `C_ORDER_ID`.

**Source:** `C_ORDER.csv`.

**Primary key:** `sales_order_key`.

**Degenerate dimensions:** `C_ORDER_ID`, `DOCUMENTNO`, `DOCSTATUS`, `POREFERENCE`.

**Foreign keys:**

- `order_date_key -> dim_date`
- `customer_key -> dim_customer`
- `commercial_key -> dim_commercial`
- `geography_key -> dim_geography`
- `payment_term_key -> dim_payment_term`
- `price_list_key -> dim_price_list`
- `document_type_key -> dim_document_type`
- `warehouse_key -> dim_warehouse`

**Measures:**

- `order_count`: additive, always 1.
- `total_lines_amount`: additive.
- `grand_total_amount`: additive.
- `freight_amount`: additive.
- `charge_amount`: additive.

**Use cases:**

- Number of orders by period.
- Total order value by commercial.
- Order value by customer, warehouse, payment term, or document type.

**Important rule:** Do not use this table for product-level analysis. Use `fact_sales_order_line`.

## `fact_sales_order_line`

**Business event:** A product/article is ordered on a sales order.

**Exact grain:** One row per `C_ORDERLINE_ID`.

**Source:** `C_ORDERLINE.csv`, enriched with `C_ORDER`, product/category, and primary supplier mapping.

**Primary key:** `sales_order_line_key`.

**Degenerate dimensions:** `C_ORDERLINE_ID`, `C_ORDER_ID`, `DOCUMENTNO`, `DOCSTATUS`, `LINE`.

**Foreign keys:**

- `order_date_key -> dim_date`
- `customer_key -> dim_customer`
- `commercial_key -> dim_commercial`
- `product_key -> dim_product`
- `product_category_key -> dim_product_category`
- `supplier_key -> dim_supplier`
- `warehouse_key -> dim_warehouse`
- `document_type_key -> dim_document_type`

**Measures:**

- `order_line_count`: additive, always 1.
- `quantity_ordered`: additive.
- `quantity_delivered`: additive.
- `quantity_invoiced`: additive.
- `line_net_amount`: additive.
- `unit_price`: non-additive. Use average or weighted average only.

**Use cases:**

- Sales by product, product category, supplier, warehouse.
- Delivery and invoicing progression by ordered quantity.
- Top ordered products.

## `fact_invoice`

**Business event:** A customer invoice or credit note exists in the ERP.

**Exact grain:** One row per `C_INVOICE_ID`.

**Source:** `C_INVOICE.csv`.

**Primary key:** `invoice_key`.

**Degenerate dimensions:** `C_INVOICE_ID`, `DOCUMENTNO`, `DOCSTATUS`, `ISPAID`.

**Foreign keys:**

- `invoice_date_key -> dim_date`
- `customer_key -> dim_customer`
- `commercial_key -> dim_commercial`
- `geography_key -> dim_geography`
- `payment_term_key -> dim_payment_term`
- `price_list_key -> dim_price_list`
- `document_type_key -> dim_document_type`

**Measures:**

- `invoice_count`: additive, always 1.
- `paid_invoice_count`: additive.
- `unpaid_invoice_count`: additive.
- `total_lines_amount`: additive.
- `grand_total_amount`: additive.

**Use cases:**

- CA facture by period.
- Paid/unpaid invoice counts.
- Invoiced revenue by customer or commercial.

**Important rule:** For official CA charts, prefer invoice facts. Orders are useful for sales pipeline, but invoices represent realized CA.

## `fact_invoice_line`

**Business event:** A product/article is invoiced on an invoice.

**Exact grain:** One row per `C_INVOICELINE_ID`.

**Source:** `C_INVOICELINE.csv`, enriched with `C_INVOICE`, product/category, and primary supplier mapping.

**Primary key:** `invoice_line_key`.

**Degenerate dimensions:** `C_INVOICELINE_ID`, `C_INVOICE_ID`, `DOCUMENTNO`, `DOCSTATUS`, `LINE`.

**Foreign keys:**

- `invoice_date_key -> dim_date`
- `customer_key -> dim_customer`
- `commercial_key -> dim_commercial`
- `product_key -> dim_product`
- `product_category_key -> dim_product_category`
- `supplier_key -> dim_supplier`
- `document_type_key -> dim_document_type`

**Measures:**

- `invoice_line_count`: additive, always 1.
- `quantity_invoiced`: additive.
- `line_net_amount`: additive.
- `line_total_amount`: additive when available.
- `unit_price`: non-additive.

**Use cases:**

- CA facture by product, category, supplier.
- Top products by invoiced CA.
- Supplier contribution based on sold/invoiced products.

## `fact_delivery`

**Business event:** A goods shipment/delivery document exists in the ERP.

**Exact grain:** One row per `M_INOUT_ID`.

**Source:** `M_INOUT.csv`.

**Primary key:** `delivery_key`.

**Degenerate dimensions:** `M_INOUT_ID`, `DOCUMENTNO`, `DOCSTATUS`, `C_ORDER_ID`.

**Foreign keys:**

- `movement_date_key -> dim_date`
- `customer_key -> dim_customer`
- `geography_key -> dim_geography`
- `warehouse_key -> dim_warehouse`
- `document_type_key -> dim_document_type` when available.

**Measures:**

- `delivery_count`: additive, always 1.

**Use cases:**

- Delivery volume by period.
- Delivered documents by customer or warehouse.

## `fact_delivery_line`

**Business event:** A product/article is delivered on a shipment line.

**Exact grain:** One row per `M_INOUTLINE_ID`.

**Source:** `M_INOUTLINE.csv`, enriched with delivery header context and product/category/supplier.

**Primary key:** `delivery_line_key`.

**Degenerate dimensions:** `M_INOUTLINE_ID`, `M_INOUT_ID`, `C_ORDERLINE_ID`, `LINE`.

**Foreign keys:**

- `movement_date_key -> dim_date`
- `customer_key -> dim_customer`
- `product_key -> dim_product`
- `product_category_key -> dim_product_category`
- `supplier_key -> dim_supplier`
- `warehouse_key -> dim_warehouse`

**Measures:**

- `delivery_line_count`: additive, always 1.
- `movement_quantity`: additive.
- `entered_quantity`: additive.
- `quantity_on_hand_snapshot`: semi-additive.
- `quantity_reserved_snapshot`: semi-additive.

**Use cases:**

- Delivered quantities by product/category.
- Operational delivery monitoring.
- Order-to-delivery comparison when joined through source IDs.

## `fact_payment_allocation`

**Business event:** A payment or allocation is linked to an invoice.

**Exact grain:** One row per `C_ALLOCATIONLINE_ID`.

**Source:** `C_ALLOCATIONLINE.csv`, enriched with `C_ALLOCATIONHDR` and `C_PAYMENT`.

**Primary key:** `payment_allocation_key`.

**Degenerate dimensions:** `C_ALLOCATIONLINE_ID`, `C_ALLOCATIONHDR_ID`, `C_INVOICE_ID`, `C_PAYMENT_ID`, `C_ORDER_ID`, `payment_document_no`.

**Foreign keys:**

- `allocation_date_key -> dim_date`
- `customer_key -> dim_customer`
- `payment_document_type_key -> dim_document_type` when available.

**Measures:**

- `allocation_count`: additive, always 1.
- `allocated_amount`: additive.
- `discount_amount`: additive.
- `writeoff_amount`: additive.
- `overunder_amount`: additive.
- `payment_amount`: additive with caution because payment headers may repeat across allocations.

**Use cases:**

- Payment matching by customer.
- Discounts, write-offs, and unpaid balance analysis.

**Current limitation:** Commercial attribution is not reliable in the allocation source. It can be derived later through invoice joins if needed.

## `fact_stock_snapshot`

**Business event:** Stock availability exists at a snapshot moment for a product/location.

**Exact grain:** One row per product, attribute set instance, warehouse or storage row in `RV_STORAGE` for the extract snapshot date.

**Source:** `RV_STORAGE.csv`.

**Primary key:** `stock_snapshot_key`.

**Degenerate dimensions:** `M_PRODUCT_ID`, `M_ATTRIBUTESETINSTANCE_ID`.

**Foreign keys:**

- `snapshot_date_key -> dim_date`
- `product_key -> dim_product`
- `product_category_key -> dim_product_category`
- `supplier_key -> dim_supplier`
- `warehouse_key -> dim_warehouse` when warehouse can be resolved.

**Measures:**

- `stock_row_count`: additive, always 1.
- `quantity_on_hand`: semi-additive.
- `quantity_reserved`: semi-additive.
- `quantity_available`: semi-additive.
- `quantity_ordered`: semi-additive.

**Use cases:**

- Stock availability for sold products.
- Future rupture-risk dashboards.
- Product and supplier stock visibility.

**Important rule:** Stock quantities are snapshots. Do not sum them across multiple snapshot dates unless the business question explicitly asks for accumulated snapshots.

## Validation Requirements

The ETL validation must check:

- source row count equals fact row count for each fact.
- financial source totals equal fact totals for orders, order lines, invoices, invoice lines, and allocations.
- unknown-key usage is profiled and reviewed.
- no fact is loaded without a stated grain.

The current validation report is generated at:

`DataWareHouse/processus_de_vente/etl/validation/etl_validation_report.md`


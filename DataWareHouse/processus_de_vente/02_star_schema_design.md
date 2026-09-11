# Star Schema Design - Processus de Vente

## Modeling Principles

This model follows star schema principles:

- Facts store measurable events at a precise grain.
- Dimensions store descriptive business context.
- Dimensions connect to facts through surrogate keys.
- Source identifiers remain available for traceability.
- Power BI should preferably use facts, dimensions, and marts, not raw staging tables.

The model is designed for the LPN sales process and uses the validated extraction coverage from the current project.

## Resolved Modeling Decisions

### SCD Type 1 vs Type 2

This prototype uses **SCD Type 1** for business dimensions. Type 1 means the latest descriptive value overwrites the previous value. This is the safest practical choice for the current project because the available extracts are snapshots/exports, not a complete dimension-change history.

**SCD Type 2** keeps full historical versions with `effective_from`, `effective_to`, and `is_current`. It is recommended later for `dim_customer`, `dim_product`, `dim_supplier`, and possibly `dim_commercial` if LPN needs historical attribution.

### Power BI Consumption

Power BI should start from `mart_*` outputs for clean executive reporting. When deeper analysis is needed, Power BI can import the `dim_*` and `fact_*` outputs and build the star model directly.

### History Window

The current design and ETL use only the project data currently available. Future 24-month exports can be loaded through the same model to unlock yearly trends.

## Dimensions

### `dim_date`

**Business purpose:** Common calendar dimension for all reporting dates.

**Grain:** One row per calendar day.

**Primary key:** `date_key` as integer `YYYYMMDD`.

**Natural keys:** `full_date`.

**Important attributes:**

- day, month, quarter, year.
- month name.
- week number.
- first/last day flags.
- relative period flags can be added later.

**Example columns:**

- `date_key`
- `full_date`
- `day_of_month`
- `month_number`
- `month_name`
- `quarter_number`
- `year_number`
- `week_of_year`
- `is_month_end`

**Relationships to facts:**

- `fact_sales_order.order_date_key`
- `fact_sales_order_line.order_date_key`
- `fact_invoice.invoice_date_key`
- `fact_invoice_line.invoice_date_key`
- `fact_delivery.movement_date_key`
- `fact_delivery_line.movement_date_key`
- `fact_payment_allocation.allocation_date_key`
- `fact_stock_snapshot.snapshot_date_key`

**SCD recommendation:** Type 0. Calendar rows do not change.

### `dim_customer`

**Business purpose:** Describes clients/customers buying products.

**Grain:** One row per business partner customer version.

**Primary key:** `customer_key`.

**Natural/business keys:**

- `c_bpartner_id`
- `customer_code`

**Important attributes:**

- customer name.
- customer group.
- credit limit.
- customer/vendor flags.
- default commercial when available.
- billing/shipping location links can be resolved through `dim_geography`.

**Example columns:**

- `customer_key`
- `c_bpartner_id`
- `customer_code`
- `customer_name`
- `customer_group_id`
- `customer_group_name`
- `is_customer`
- `is_vendor`
- `salesrep_id`
- `credit_limit`
- `is_active`

**Relationships to facts:**

- `fact_sales_order.customer_key`
- `fact_sales_order_line.customer_key`
- `fact_invoice.customer_key`
- `fact_invoice_line.customer_key`
- `fact_delivery.customer_key`
- `fact_payment_allocation.customer_key`

**SCD recommendation:** Type 2 later if customer group, commercial assignment, or credit limit history matters. Type 1 is enough for v1.

### `dim_commercial`

**Business purpose:** Describes sales representatives/commercials.

**Grain:** One row per ERP user/sales representative version.

**Primary key:** `commercial_key`.

**Natural/business keys:**

- `ad_user_id`
- email when present.

**Important attributes:**

- commercial name.
- email.
- active flag.
- partner link if present.

**Example columns:**

- `commercial_key`
- `ad_user_id`
- `commercial_name`
- `email`
- `description`
- `c_bpartner_id`
- `is_active`

**Relationships to facts:**

- `fact_sales_order.commercial_key`
- `fact_invoice.commercial_key`
- `fact_sales_order_line.commercial_key`
- `fact_invoice_line.commercial_key`

**SCD recommendation:** Type 1 for v1. Type 2 only if commercial assignment history becomes necessary.

### `dim_product`

**Business purpose:** Describes sold products/articles.

**Grain:** One row per product version.

**Primary key:** `product_key`.

**Natural/business keys:**

- `m_product_id`
- product code/value.

**Important attributes:**

- product name.
- ISBN/article codes when available.
- stock/sold/purchased flags.
- product category.
- product type.
- theme.
- collection.
- primary supplier.

**Example columns:**

- `product_key`
- `m_product_id`
- `product_code`
- `product_name`
- `description`
- `product_type_code`
- `is_sold`
- `is_purchased`
- `is_stocked`
- `product_category_key`
- `supplier_key`
- `product_type_name`
- `theme_name`
- `collection_name`

**Relationships to facts:**

- `fact_sales_order_line.product_key`
- `fact_invoice_line.product_key`
- `fact_delivery_line.product_key`
- `fact_stock_snapshot.product_key`

**SCD recommendation:** Type 2 later for category/supplier changes if historical product classification matters. Type 1 is acceptable for v1.

### `dim_product_category`

**Business purpose:** Groups products into business families.

**Grain:** One row per product category version.

**Primary key:** `product_category_key`.

**Natural/business keys:**

- `m_product_category_id`

**Important attributes:**

- category code.
- category name.
- planned margin.
- default flag.

**Example columns:**

- `product_category_key`
- `m_product_category_id`
- `category_code`
- `category_name`
- `description`
- `planned_margin`
- `is_default`

**Relationships to facts:**

- indirectly through `dim_product`.
- optionally directly to line facts for performance.

**SCD recommendation:** Type 1 for v1.

### `dim_supplier`

**Business purpose:** Describes product suppliers/fournisseurs.

**Grain:** One row per supplier business partner version.

**Primary key:** `supplier_key`.

**Natural/business keys:**

- `c_bpartner_id`
- supplier code/value.

**Important attributes:**

- supplier name.
- supplier group.
- active flag.
- source vendor flag.

**Example columns:**

- `supplier_key`
- `c_bpartner_id`
- `supplier_code`
- `supplier_name`
- `supplier_group_id`
- `supplier_group_name`
- `is_active`

**Relationships to facts:**

- `fact_sales_order_line.supplier_key`
- `fact_invoice_line.supplier_key`
- `fact_stock_snapshot.supplier_key`

**SCD recommendation:** Type 1 for v1. Type 2 later if supplier ownership changes must be historically preserved.

**Important assumption:** A product can have multiple supplier rows in `M_PRODUCT_PO`. The v1 model uses one primary supplier per product, selected by current/active supplier rules, to avoid revenue duplication.

### `dim_geography`

**Business purpose:** Describes customer and delivery geography.

**Grain:** One row per partner location/location combination.

**Primary key:** `geography_key`.

**Natural/business keys:**

- `c_bpartner_location_id`
- `c_location_id`

**Important attributes:**

- location name.
- bill-to/ship-to/pay-from/remit-to flags.
- city name.
- region name.
- country id.
- sales region id when present.
- sector and pedagogic zone attributes when present.

**Example columns:**

- `geography_key`
- `c_bpartner_location_id`
- `c_location_id`
- `location_name`
- `city_name`
- `region_name`
- `country_id`
- `is_bill_to`
- `is_ship_to`
- `c_salesregion_id`
- `sector_detail`

**Relationships to facts:**

- `fact_sales_order.geography_key`
- `fact_invoice.geography_key`
- `fact_delivery.geography_key`

**SCD recommendation:** Type 1 for v1; Type 2 later if address history matters.

### `dim_payment_term`

**Business purpose:** Describes payment terms such as `60 J`.

**Grain:** One row per payment term.

**Primary key:** `payment_term_key`.

**Natural/business keys:**

- `c_paymentterm_id`

**Important attributes:**

- payment term name.
- net days.
- grace days.
- after-delivery flag.
- default flag.

**Example columns:**

- `payment_term_key`
- `c_paymentterm_id`
- `payment_term_name`
- `net_days`
- `grace_days`
- `after_delivery`
- `is_due_fixed`
- `is_default`

**Relationships to facts:**

- `fact_sales_order.payment_term_key`
- `fact_invoice.payment_term_key`

**SCD recommendation:** Type 1.

### `dim_price_list`

**Business purpose:** Describes sales price lists and channels.

**Grain:** One row per price list.

**Primary key:** `price_list_key`.

**Natural/business keys:**

- `m_pricelist_id`

**Important attributes:**

- price list name.
- base price list.
- tax included flag.
- sales price list flag.
- currency id.

**Example columns:**

- `price_list_key`
- `m_pricelist_id`
- `price_list_name`
- `base_pricelist_id`
- `is_tax_included`
- `is_so_price_list`
- `currency_id`

**Relationships to facts:**

- `fact_sales_order.price_list_key`
- `fact_invoice.price_list_key`

**SCD recommendation:** Type 1 for v1.

### `dim_document_type`

**Business purpose:** Describes order, invoice, delivery, and payment document types.

**Grain:** One row per ERP document type.

**Primary key:** `document_type_key`.

**Natural/business keys:**

- `c_doctype_id`

**Important attributes:**

- document type name.
- print name.
- base type.
- sales subtype.
- sales transaction flag.

**Example columns:**

- `document_type_key`
- `c_doctype_id`
- `document_type_name`
- `print_name`
- `doc_base_type`
- `doc_subtype_so`
- `is_sales_transaction`

**Relationships to facts:**

- `fact_sales_order.document_type_key`
- `fact_invoice.document_type_key`
- `fact_delivery.document_type_key`
- `fact_payment_allocation.payment_document_type_key`

**SCD recommendation:** Type 1.

### `dim_sales_region`

**Business purpose:** Describes commercial/geographic sales regions such as `CASA`, `AXE 1`, etc.

**Grain:** One row per sales region.

**Primary key:** `sales_region_key`.

**Natural/business keys:**

- `c_salesregion_id`

**Important attributes:**

- region code.
- region name.
- description.
- summary flag.
- default flag.

**Example columns:**

- `sales_region_key`
- `c_salesregion_id`
- `sales_region_code`
- `sales_region_name`
- `description`
- `is_summary`
- `is_default`

**Relationships to facts:**

- directly through `fact_sales_order.sales_region_key` if resolved from customer location.
- indirectly through `dim_geography`.

**SCD recommendation:** Type 1.

### `dim_warehouse`

**Business purpose:** Describes warehouses and locators used for stock and deliveries.

**Grain:** One row per warehouse/locator combination.

**Primary key:** `warehouse_key`.

**Natural/business keys:**

- `m_warehouse_id`
- `m_locator_id`

**Important attributes:**

- warehouse name.
- locator value/name.
- default locator flag.
- location id if available.

**Example columns:**

- `warehouse_key`
- `m_warehouse_id`
- `warehouse_code`
- `warehouse_name`
- `m_locator_id`
- `locator_value`
- `is_default_locator`

**Relationships to facts:**

- `fact_sales_order.warehouse_key`
- `fact_sales_order_line.warehouse_key`
- `fact_delivery.warehouse_key`
- `fact_delivery_line.warehouse_key`
- `fact_stock_snapshot.warehouse_key`

**SCD recommendation:** Type 1.

## Fact Tables

### `fact_sales_order`

**Business purpose:** Measures sales order headers.

**Exact grain:** One row per sales order header (`C_ORDER_ID`).

**Primary key:** `sales_order_key`.

**Foreign keys:**

- `order_date_key`
- `customer_key`
- `commercial_key`
- `geography_key`
- `payment_term_key`
- `price_list_key`
- `document_type_key`
- `warehouse_key`

**Measures:**

- `total_lines_amount`
- `grand_total_amount`
- `freight_amount`
- `charge_amount`
- `order_count` as constant `1`

**Degenerate dimensions:**

- `c_order_id`
- `document_no`
- `doc_status`
- `po_reference`

**Additivity:**

- Amounts are additive across orders, customers, commercials, products only at header grain.
- `order_count` is additive.
- Do not combine header totals with line totals without clear grain labeling.

**Relationships to other facts:**

- Parent to `fact_sales_order_line`.
- May link to invoices through source `C_ORDER_ID`.
- May link to deliveries through source `C_ORDER_ID`.

**Expected source data:**

- `stg_c_order`

### `fact_sales_order_line`

**Business purpose:** Measures products ordered, delivered, invoiced, prices, discounts, and line revenue.

**Exact grain:** One row per sales order line (`C_ORDERLINE_ID`).

**Primary key:** `sales_order_line_key`.

**Foreign keys:**

- `order_date_key`
- `customer_key`
- `commercial_key`
- `product_key`
- `product_category_key`
- `supplier_key`
- `warehouse_key`
- `document_type_key`

**Measures:**

- `quantity_ordered`
- `quantity_delivered`
- `quantity_invoiced`
- `price_list`
- `price_actual`
- `line_net_amount`
- `discount_percent`
- `line_count` as constant `1`

**Degenerate dimensions:**

- `c_orderline_id`
- `c_order_id`
- `document_no`
- `line_no`
- `doc_status`

**Additivity:**

- Quantities and line net amount are additive.
- Unit prices and discount percent are non-additive; aggregate with weighted logic if needed.

**Relationships to other facts:**

- Links to invoice lines through `C_ORDERLINE_ID`.
- Links to delivery lines through `C_ORDERLINE_ID`.

**Expected source data:**

- `stg_c_orderline`
- `stg_c_order`
- product and supplier dimensions.

### `fact_invoice`

**Business purpose:** Measures customer invoice headers and invoice payment state.

**Exact grain:** One row per invoice header (`C_INVOICE_ID`).

**Primary key:** `invoice_key`.

**Foreign keys:**

- `invoice_date_key`
- `customer_key`
- `commercial_key`
- `geography_key`
- `payment_term_key`
- `price_list_key`
- `document_type_key`

**Measures:**

- `total_lines_amount`
- `grand_total_amount`
- `invoice_count` as constant `1`
- `paid_invoice_count` as `1` when paid.
- `unpaid_invoice_count` as `1` when unpaid.

**Degenerate dimensions:**

- `c_invoice_id`
- `document_no`
- `doc_status`
- `is_paid`
- `source_c_order_id`

**Additivity:**

- Amounts are additive by invoice.
- Paid/unpaid counts are additive.
- Paid percentage is non-additive and should be calculated in marts/Power BI.

**Relationships to other facts:**

- Parent to `fact_invoice_line`.
- Linked to payments through `fact_payment_allocation`.

**Expected source data:**

- `stg_c_invoice`

### `fact_invoice_line`

**Business purpose:** Measures invoiced product revenue and quantity.

**Exact grain:** One row per invoice line (`C_INVOICELINE_ID`).

**Primary key:** `invoice_line_key`.

**Foreign keys:**

- `invoice_date_key`
- `customer_key`
- `commercial_key`
- `product_key`
- `product_category_key`
- `supplier_key`
- `document_type_key`

**Measures:**

- `quantity_invoiced`
- `price_list`
- `price_actual`
- `line_net_amount`
- `invoice_line_count` as constant `1`

**Degenerate dimensions:**

- `c_invoiceline_id`
- `c_invoice_id`
- `c_orderline_id`
- `document_no`
- `line_no`

**Additivity:**

- Quantity and line net amount are additive.
- Unit prices are non-additive.

**Relationships to other facts:**

- Can reconcile to `fact_sales_order_line` through `C_ORDERLINE_ID`.
- Can reconcile to `fact_invoice` through `C_INVOICE_ID`.

**Expected source data:**

- `stg_c_invoiceline`
- `stg_c_invoice`

### `fact_delivery`

**Business purpose:** Measures delivery/shipment headers.

**Exact grain:** One row per delivery/shipment header (`M_INOUT_ID`).

**Primary key:** `delivery_key`.

**Foreign keys:**

- `movement_date_key`
- `customer_key`
- `geography_key`
- `warehouse_key`
- `document_type_key`

**Measures:**

- `delivery_count` as constant `1`

**Degenerate dimensions:**

- `m_inout_id`
- `document_no`
- `doc_status`
- `source_c_order_id`

**Additivity:**

- Delivery count is additive.

**Relationships to other facts:**

- Parent to `fact_delivery_line`.
- Links to orders through `C_ORDER_ID`.

**Expected source data:**

- `stg_m_inout`

### `fact_delivery_line`

**Business purpose:** Measures delivered product quantities.

**Exact grain:** One row per delivery/shipment line (`M_INOUTLINE_ID`).

**Primary key:** `delivery_line_key`.

**Foreign keys:**

- `movement_date_key`
- `customer_key`
- `product_key`
- `product_category_key`
- `supplier_key`
- `warehouse_key`

**Measures:**

- `movement_quantity`
- `entered_quantity`
- `quantity_on_hand_snapshot`
- `quantity_reserved_snapshot`
- `delivery_line_count` as constant `1`

**Degenerate dimensions:**

- `m_inoutline_id`
- `m_inout_id`
- `c_orderline_id`
- `line_no`
- availability indicator.

**Additivity:**

- Movement quantities are additive.
- Snapshot quantities are semi-additive and should not be summed over time without care.

**Relationships to other facts:**

- Links to order lines through `C_ORDERLINE_ID`.
- Parent delivery header through `M_INOUT_ID`.

**Expected source data:**

- `stg_m_inoutline`
- `stg_m_inout`

### `fact_payment_allocation`

**Business purpose:** Measures invoice-payment allocation and collections.

**Exact grain:** One row per allocation line (`C_ALLOCATIONLINE_ID`).

**Primary key:** `payment_allocation_key`.

**Foreign keys:**

- `allocation_date_key`
- `customer_key`
- `commercial_key` if resolvable from invoice/order.
- `document_type_key` for payment document type if resolvable.

**Measures:**

- `allocated_amount`
- `discount_amount`
- `writeoff_amount`
- `overunder_amount`
- `payment_amount`
- `allocation_count` as constant `1`

**Degenerate dimensions:**

- `c_allocationline_id`
- `c_allocationhdr_id`
- `c_invoice_id`
- `c_payment_id`
- payment document number.

**Additivity:**

- Amounts are additive across allocations.
- Payment status percentages are non-additive.

**Relationships to other facts:**

- Links to invoices through `C_INVOICE_ID`.
- Links to payments through `C_PAYMENT_ID`.
- Can be used to analyze paid invoices and collection behavior.

**Expected source data:**

- `stg_c_allocationline`
- `stg_c_allocationhdr`
- `stg_c_payment`
- `stg_c_invoice`

### `fact_stock_snapshot`

**Business purpose:** Measures stock availability for products in the vente scope.

**Exact grain:** One row per product, warehouse, locator, attribute set instance, and snapshot batch/date.

**Primary key:** `stock_snapshot_key`.

**Foreign keys:**

- `snapshot_date_key`
- `product_key`
- `product_category_key`
- `supplier_key`
- `warehouse_key`

**Measures:**

- `quantity_on_hand`
- `quantity_reserved`
- `quantity_available`
- `quantity_ordered`
- `stock_row_count` as constant `1`

**Degenerate dimensions:**

- `m_product_id`
- `m_attribute_set_instance_id`

**Additivity:**

- Stock quantities are semi-additive. They can be summed across product/warehouse at one snapshot date but not across dates.

**Relationships to other facts:**

- Product and supplier context can be compared to sales order and invoice line facts.

**Expected source data:**

- `stg_rv_storage`

## Recommended Mart Views

These marts are not Step 3 implementation yet, but they guide the model:

- `mart_sales_overview`
- `mart_sales_by_commercial`
- `mart_sales_by_customer`
- `mart_sales_by_product`
- `mart_sales_by_supplier`
- `mart_sales_by_region`
- `mart_order_to_invoice_flow`
- `mart_payment_status`
- `mart_stock_risk`

## Confirmed Exclusions For Version 1

- Commission facts: source tables are empty or obsolete for this LPN context.
- Sales objectives/goals: `PA_GOAL` returned empty.
- Contracts: not found in discovery.
- Purchases/achats: out of current warehouse scope, except supplier attribution for sold products.

## Open Modeling Questions For Later

- Should the wide `3.xlsx` extract become a separate `stg_sales_order_line_wide` validation table?
- Should `dim_product` implement Type 2 history for supplier/category changes?
- Should Power BI consume only marts or also direct fact/dimension tables?
- Should a future extraction cover 12 or 24 months for yearly performance and seasonality?

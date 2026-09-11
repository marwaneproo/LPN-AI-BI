# Vente DW Final Extraction Review

Generated at: `2026-06-01T09:46:48.079191+00:00`

## Final Decision

No more extraction required for the current processus de vente warehouse scope. Move to modeling/import unless the business explicitly asks for longer history or a non-vente module.

## Data Warehouse Area Coverage

| Area | Status |
|---|---|
| `orders_and_order_lines` | `complete` |
| `invoices_and_invoice_lines` | `complete` |
| `customers` | `complete` |
| `commercials` | `complete` |
| `products_categories_types_themes_collections` | `complete` |
| `suppliers` | `complete_enough` |
| `delivery_flow` | `complete` |
| `payments_and_allocations` | `complete` |
| `geography_locations_regions_cities` | `complete` |
| `payment_terms` | `complete` |
| `price_lists` | `complete` |
| `sales_regions` | `complete` |
| `commission_objectives` | `not_used_or_empty` |
| `contracts` | `not_found_in_discovery` |

## Clean Package Coverage

| Table | Rows | Columns |
|---|---:|---:|
| `AD_ORG` | 1 | 11 |
| `C_ALLOCATIONHDR` | 136 | 21 |
| `C_ALLOCATIONLINE` | 245 | 23 |
| `C_BPARTNER` | 313 | 123 |
| `C_DOCTYPE` | 7 | 33 |
| `C_INVOICE` | 1182 | 96 |
| `C_INVOICELINE` | 62146 | 61 |
| `C_ORDER` | 3350 | 136 |
| `C_ORDERLINE` | 67862 | 112 |
| `C_PAYMENT` | 134 | 87 |
| `C_TAX` | 3 | 25 |
| `M_INOUT` | 4667 | 113 |
| `M_INOUTLINE` | 181631 | 43 |
| `M_LOCATOR` | 1 | 17 |
| `M_PRODUCT` | 16345 | 101 |
| `M_PRODUCT_CATEGORY` | 26 | 16 |
| `M_WAREHOUSE` | 1 | 13 |
| `RV_STORAGE` | 58826 | 35 |

## Enrichment Package Coverage

| Table | Rows | Columns |
|---|---:|---:|
| `AD_USER` | 70 | 8 |
| `C_BPARTNER_VENDOR` | 9633 | 123 |
| `M_PRODUCT_COLLECTION` | 86183 | 14 |
| `M_PRODUCT_PO` | 46471 | 41 |
| `M_PRODUCT_THEME` | 1487 | 12 |
| `M_PRODUCT_TYPE` | 27 | 12 |
| `PRODUCTS_WITHOUT_SUPPLIER` | 3 | 4 |

## Time Coverage

- `orders_min`: 2026-01-15 07:44:21
- `orders_max`: 2026-05-13 10:46:52
- `invoices_min`: 2026-01-15 08:42:39
- `invoices_max`: 2026-05-12 13:13:55

## Key Mapping Coverage

- `salesrep_ids_total`: 21
- `salesrep_ids_mapped`: 21
- `order_products_total`: 14425
- `order_products_with_supplier`: 14262
- `invoice_products_total`: 13975
- `invoice_products_with_supplier`: 12925
- `vendor_ids_total`: 167
- `vendor_ids_mapped`: 167

## Workbook Classification Counts

| Role | Count |
|---|---:|
| `AD_ORG` | 1 |
| `AD_USER` | 1 |
| `CONTROL_QUERY` | 2 |
| `C_ALLOCATIONHDR` | 1 |
| `C_ALLOCATIONLINE` | 1 |
| `C_BPARTNER` | 2 |
| `C_BPARTNER_LOCATION` | 1 |
| `C_BP_GROUP` | 2 |
| `C_CITY` | 1 |
| `C_COMMISSION` | 1 |
| `C_DOCTYPE` | 1 |
| `C_INVOICE` | 1 |
| `C_INVOICELINE` | 1 |
| `C_LOCATION` | 1 |
| `C_ORDERLINE` | 6 |
| `C_PAYMENT` | 1 |
| `C_PAYMENTTERM` | 1 |
| `C_REGION` | 1 |
| `C_SALESREGION` | 1 |
| `C_TAX` | 1 |
| `DENORMALIZED_SALES_ORDER_LINE` | 2 |
| `M_INOUT` | 1 |
| `M_INOUTLINE` | 1 |
| `M_LOCATOR` | 2 |
| `M_PRICELIST` | 1 |
| `M_PRODUCT` | 1 |
| `M_PRODUCT_CATEGORY` | 1 |
| `M_PRODUCT_COLLECTION` | 1 |
| `M_PRODUCT_PO` | 1 |
| `M_PRODUCT_THEME` | 1 |
| `M_PRODUCT_TYPE` | 1 |
| `M_WAREHOUSE` | 1 |
| `RV_STORAGE` | 2 |
| `SALES_TABLE_DISCOVERY` | 2 |
| `UNKNOWN_OR_LEGACY` | 33 |

## Important Notes

- `3.xlsx` and `4.xlsx` are duplicate denormalized sales/order-line extracts; keep one as a validation/export source, not both.
- Commission/objective exports are empty or obsolete for this LPN context, so they should not block the vente warehouse.
- `1.xlsx` and `2.xlsx` are control outputs, useful for traceability but not warehouse tables.
- For future yearly/seasonality BI, extract a wider historical range. For the current scoped vente warehouse, extraction is sufficient.

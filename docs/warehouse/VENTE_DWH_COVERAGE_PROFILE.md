# Vente Data Warehouse Coverage Profile

Generated at: `2026-06-01T08:13:36.782173+00:00`

## Period Coverage

| Area | Rows | Date min | Date max | Amount |
|---|---:|---|---|---:|
| orders | 3350 | 2026-01-15 07:44:21+00:00 | 2026-05-13 10:46:52+00:00 | 16709787.11 |
| invoices | 1182 | 2026-01-15 08:42:39+00:00 | 2026-05-12 13:13:55+00:00 | 16757554.02 |
| deliveries | 4667 | 2026-01-15 00:00:00+00:00 | 2026-05-13 00:00:00+00:00 |  |
| payments | 134 | 2026-01-29 00:00:00+00:00 | 2026-05-08 00:00:00+00:00 |  |

## Row Counts

| Dataset | Rows |
|---|---:|
| `orders` | 3350 |
| `order_lines` | 67862 |
| `invoices` | 1182 |
| `invoice_lines` | 62146 |
| `partners` | 313 |
| `products` | 16345 |
| `product_categories` | 26 |
| `deliveries` | 4667 |
| `delivery_lines` | 181631 |
| `allocations` | 245 |
| `payments` | 134 |
| `storage_rows` | 58826 |
| `salesreps` | 70 |
| `supplier_product_links` | 46471 |
| `vendors` | 9633 |
| `product_types` | 27 |
| `product_themes` | 1487 |
| `product_collections` | 86183 |
| `products_without_supplier` | 3 |

## Mapping Coverage

| Mapping | Matched | Total | Coverage |
|---|---:|---:|---:|
| `salesrep_mapping` | 21 | 21 | 100.0% |
| `customer_mapping` | 313 | 313 | 100.0% |
| `product_mapping` | 16345 | 16346 | 99.99% |
| `invoice_product_primary_supplier_mapping` | 12925 | 13975 | 92.49% |
| `order_product_primary_supplier_mapping` | 14262 | 14425 | 98.87% |
| `allocation_invoice_links` | 245 | 245 | 100.0% |
| `allocation_payment_links` | 245 | 245 | 100.0% |

## Key Distributions

### `order_docstatus`

| Value | Rows |
|---|---:|
| `CO` | 2850 |
| `CL` | 500 |

### `invoice_docstatus`

| Value | Rows |
|---|---:|
| `CO` | 1182 |

### `invoice_paid_flag`

| Value | Rows |
|---|---:|
| `N` | 935 |
| `Y` | 247 |

### `payment_tender_type`

| Value | Rows |
|---|---:|
| `E` | 118 |
| `S` | 11 |
| `C` | 5 |

### `order_doctype_ids`

| Value | Rows |
|---|---:|
| `1000028` | 3193 |
| `1000034` | 102 |
| `1000032` | 43 |
| `1000031` | 12 |

### `invoice_doctype_ids`

| Value | Rows |
|---|---:|
| `1000002` | 706 |
| `1000004` | 404 |
| `1000003` | 72 |

## Missing Reference Dimensions Detected From Existing Keys

### `C_BP_GROUP`

- `present_in_c_bpartner_rows`: 128
- `distinct_ids`: 1
- `status`: missing valid dimension export; 23_C_BP_GROUP.xlsx is actually M_PRODUCT_TYPE-shaped.

### `C_BPARTNER_LOCATION`

- `orders_with_location_id`: 3350
- `invoices_with_location_id`: 1182
- `status`: missing table; needed for address/geography/client site analysis.

### `C_PAYMENTTERM`

- `orders_with_payment_term`: 3350
- `invoices_with_payment_term`: 1182
- `distinct_order_terms`: 5
- `distinct_invoice_terms`: 5
- `status`: missing table; needed for payment-term labels and aging semantics.

### `M_PRICELIST`

- `orders_with_pricelist`: 3350
- `invoices_with_pricelist`: 1182
- `distinct_ids`: 5
- `status`: missing table; useful for price list/channel analysis.

### `C_CAMPAIGN_C_PROJECT_C_ACTIVITY`

- `orders_with_campaign`: 0
- `orders_with_project`: 0
- `orders_with_activity`: 0
- `status`: dimension tables missing; export only if business uses campaign/project/activity sales reporting.

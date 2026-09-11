# Vente 2025 + 2026 Export Audit

Audit date: 2026-06-16

Source folder:

```text
Youssef_Extractions/data/Exported_data_through_a_drive
```

## Executive Decision

The new export is good enough to move to implementation/import.

Do not re-export the full package. The core vente transaction chain is present:

| Area | Rows |
|---|---:|
| Orders, `C_ORDER` | 20,336 |
| Order lines, `C_ORDERLINE` chunks | 354,910 |
| Invoices, `C_INVOICE` | 6,658 |
| Invoice lines, `C_INVOICELINE` chunks | 335,877 |
| Delivery headers, `M_INOUT` | 18,718 |
| Delivery lines, `M_INOUTLINE` chunks | 356,410 |
| Allocation lines, `C_ALLOCATIONLINE` | 4,212 |
| Allocation headers, `C_ALLOCATIONHDR` | 1,473 |
| Payments, `C_PAYMENT` | 1,443 |
| Customers, `C_BPARTNER` | 823 |
| Products, `M_PRODUCT` | 35,822 |
| Corrected customer portfolio | 823 |

The package gives the requested period:

```text
2025-01-01 through 2026-06-15 orders/deliveries
2025-01-03 through 2026-06-12 invoices
```

## Control Counts

The Oracle control export returned:

| Period | Orders | Invoices | Deliveries |
|---|---:|---:|---:|
| 2025 Q1 | 2,900 | 836 | 2,773 |
| 2025 Q2 | 2,704 | 999 | 2,943 |
| 2025 Q3 | 6,403 | 1,811 | 6,928 |
| 2025 Q4 | 3,776 | 1,520 | 3,915 |
| 2026 Q1 | 2,524 | 888 | 2,532 |
| 2026 Q2 | 2,029 | 604 | 2,012 |

Totals match the exported header files:

- Orders: 20,336
- Invoices: 6,658
- Deliveries by movement date: 21,103 in the control count, while the current `03_M_INOUT_2025_2026.xlsx` has 18,718 because it was order-date scoped. See fix-up note below.

## Financial Reconciliation

| Metric | Amount |
|---|---:|
| Order headers `GRANDTOTAL` | 209,154,642.66 |
| Order headers `TOTALLINES` | 209,154,026.99 |
| Order lines `LINENETAMT` | 209,113,706.99 |
| Invoice headers `GRANDTOTAL` | 198,889,429.51 |
| Invoice headers `TOTALLINES` | 199,221,890.51 |
| Invoice lines `LINENETAMT` | 199,237,857.51 |
| Delivery headers `GRANDTOTAL` | 196,944,256.32 |
| Allocation line amount | 132,111,820.85 |
| Payment `PAYAMT` | 135,340,309.62 |

The order and invoice line sums reconcile well with the corresponding header net totals.

## Relationship Checks

Strong checks:

- `C_ORDERLINE.C_ORDER_ID` missing from `C_ORDER`: 0
- `C_INVOICELINE.C_INVOICE_ID` missing from `C_INVOICE`: 0
- `M_INOUT.C_ORDER_ID` missing from `C_ORDER`: 0
- `C_ALLOCATIONLINE.C_INVOICE_ID` missing from `C_INVOICE`: 0
- `C_ALLOCATIONLINE.C_ALLOCATIONHDR_ID` missing from `C_ALLOCATIONHDR`: 0
- `C_ALLOCATIONLINE.C_PAYMENT_ID` missing from `C_PAYMENT`: 0
- Transaction customer IDs missing from `C_BPARTNER`: 0
- Duplicate primary keys in all core files: 0

Acceptable gaps:

- `C_INVOICE.C_ORDER_ID` missing from exported orders: 228. These are invoices in the target period whose linked order is outside the selected order scope, older than 2025, or not in the chosen order document types. Invoice analytics remain valid because invoice headers and lines are complete.
- `C_INVOICELINE.C_ORDERLINE_ID` missing from exported order lines: 16,681. This mainly affects optional lineage from invoice line back to order line. Invoice CA analysis remains valid.
- `C_INVOICELINE.M_INOUTLINE_ID` missing from exported delivery lines: 19,898. This affects perfect invoice-delivery lineage, not basic invoice/order analytics.

Small fixable gaps:

- `M_INOUTLINE.M_INOUT_ID` missing from exported `M_INOUT`: 505 IDs. The delivery line chunks were movement-date scoped, while the delivery header export was order-date scoped. A small replacement `M_INOUT` export by movement date will fix this.
- Product IDs referenced by line data missing from `M_PRODUCT`: 28. This likely comes from delivery-only product references. A replacement `M_PRODUCT` export including delivery-line products will fix this.

## Export Tool Issues

Two files exported without a header row:

- `24_C_PAYMENT_2025_2026.xlsx`
- `38_M_LOCATOR_2025_2026.xlsx`

The data itself is usable because the column count matches the old clean import structure:

- `C_PAYMENT`: 87 columns, 1,443 rows
- `M_LOCATOR`: 17 columns, 1 row

The import script can repair headers locally from the previous clean CSV schema. If Oracle is still open, re-exporting these two with headers is cleaner but not mandatory.

Several files have quoted headers such as `"M_PRODUCT_ID"`. This is harmless; the import script should normalize header names by stripping quotes.

## Missing or Reused Dimensions

The expected new files were not present under these exact names:

- `27_C_DOCTYPE.xlsx`
- `28_C_TAX_2025_2026.xlsx`

But older valid dimension exports are already present:

- `08_C_DOCTYPE.xlsx`: 7 rows
- `09_C_TAX.xlsx`: 3 rows

These dimensions are tiny and static enough for the current import. Re-export only if you want the naming to be perfectly consistent.

## Recommended Next Move

If Oracle is still available, run only the fix-up exports in:

```text
docs/exports/queries/vente_2025_2026_fixups.sql
```

Priority:

1. `03B_M_INOUT_2025_2026_BY_MOVEMENTDATE.xlsx`
2. `26B_M_PRODUCT_2025_2026_WITH_DELIVERY_PRODUCTS.xlsx`
3. Optional: re-export `24_C_PAYMENT_2025_2026.xlsx` with headers enabled.
4. Optional: re-export `38_M_LOCATOR_2025_2026.xlsx` with headers enabled.

If Oracle is closed, proceed to implementation/import. The import pipeline should normalize headers, repair the two headerless files using known column order, and load the 2025+2026 dataset into PostgreSQL.

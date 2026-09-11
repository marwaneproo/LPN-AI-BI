# Vente 2025 + 2026 Export Runbook

Use this runbook for the next Oracle/Compiere export window.

Target period:

```text
2025-01-01 inclusive
2026-07-01 exclusive
```

This gives the whole 2025 year plus 2026 so far. If the database is opened during June 2026, the `2026-07-01` upper bound simply includes every 2026 row currently available and no future rows.

## What We Already Have

The local project already contains 24-month commercial order and invoice exports:

```text
Youssef_Extractions/data/Exported_LPN/53_COMMERCIAL_ORDER_HEADER_24M.xlsx
Youssef_Extractions/data/Exported_LPN/54_COMMERCIAL_ORDER_LINE_24M.xlsx
Youssef_Extractions/data/Exported_LPN/55_COMMERCIAL_INVOICE_HEADER_24M.xlsx
Youssef_Extractions/data/Exported_LPN/56_COMMERCIAL_INVOICE_LINE_24M.xlsx
```

These cover roughly `2024-06-19` to `2026-06-13` for orders and `2024-06-19` to `2026-06-12` for invoices.

However, the current PostgreSQL transaction tables are still the older clean vente import:

| Table | Current local coverage |
|---|---|
| `business.c_order` | 2026-01-15 to 2026-05-13 |
| `business.c_orderline` | 2026-01-15 to 2026-05-13 |
| `business.c_invoice` | 2026-01-15 to 2026-05-12 |
| `business.c_invoiceline` | 2026-01-15 to 2026-05-12 |
| `business.m_inout` | 2026-01-15 to 2026-05-13 |
| `business.c_payment` / `business.c_allocation*` | 2026-01-29 to 2026-05-08 |

So the next extraction should produce a clean full-process package for `2025 + 2026 so far`, not only another order/invoice summary.

## Priority

If time is short, export in this order:

1. `00_CONTROL_COUNTS_BY_QUARTER`
2. Order and invoice headers: `C_ORDER`, `C_INVOICE`
3. Heavy transaction lines by quarter: `C_ORDERLINE`, `C_INVOICELINE`
4. Delivery process: `M_INOUT`, `M_INOUTLINE`
5. Payment process: `C_ALLOCATIONLINE`, `C_ALLOCATIONHDR`, `C_PAYMENT`
6. Required dimensions: customers, products, document types, tax, org, sales reps, product dimensions, warehouses, locators, stock
7. Corrected customer portfolio query

The SQL file is:

```text
docs/exports/queries/vente_2025_2026_oracle11g.sql
```

## Chunking Strategy

Heavy line tables are split by quarter:

| Chunk | From | To |
|---|---|---|
| `2025_Q1` | `2025-01-01` | `2025-04-01` |
| `2025_Q2` | `2025-04-01` | `2025-07-01` |
| `2025_Q3` | `2025-07-01` | `2025-10-01` |
| `2025_Q4` | `2025-10-01` | `2026-01-01` |
| `2026_Q1` | `2026-01-01` | `2026-04-01` |
| `2026_Q2` | `2026-04-01` | `2026-07-01` |

If a quarter still takes too long, split that same block into months by changing only the two date literals.

## Oracle 11g Performance Rules Used

- Date filters use `column >= DATE 'YYYY-MM-DD' AND column < DATE 'YYYY-MM-DD'`.
- No `TRUNC(column)` or function is applied to the date columns.
- Heavy line exports first materialize selected header IDs, then join to line tables.
- Huge line exports intentionally avoid `ORDER BY` to prevent Oracle from sorting millions of rows before export.
- Payment exports use allocation tables, because in this LPN/Compiere database `C_PAYMENT.C_INVOICE_ID` is not reliable.

## Expected Existing 24M Scale

The already recovered 24-month raw commercial files have this rough scale:

| File | Rows |
|---|---:|
| `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` | 34,710 |
| `54_COMMERCIAL_ORDER_LINE_24M.xlsx` | 854,645 |
| `55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` | 10,315 |
| `56_COMMERCIAL_INVOICE_LINE_24M.xlsx` | 511,242 |

For `2025 + 2026 so far`, the row count should be smaller than the full 24-month export, but still large enough that CSV export is safer than one huge Excel workbook.

## Export Rules

- Prefer CSV over XLSX for heavy line exports.
- Export with headers.
- Keep Oracle column names unchanged.
- Use UTF-8 if the export tool offers it.
- Save one file per SQL block using the filename written in the SQL comment.
- Do not manually rename columns.

Recommended folder name:

```text
lpn-vente-2025-2026-YYYYMMDD
```

## After Export

Before leaving the database window, check:

- Every exported CSV opens and has headers.
- The control counts roughly match the exported file row counts.
- Key date columns exist:
  - `DATEORDERED` in `C_ORDER` and `C_ORDERLINE`
  - `DATEINVOICED` in `C_INVOICE` and `C_INVOICELINE`
  - `MOVEMENTDATE` in `M_INOUT`
  - `DATETRX` in `C_PAYMENT` / allocation data
- Key IDs exist:
  - `C_ORDER_ID`
  - `C_ORDERLINE_ID`
  - `C_INVOICE_ID`
  - `C_INVOICELINE_ID`
  - `M_INOUT_ID`
  - `M_INOUTLINE_ID`
  - `C_ALLOCATIONLINE_ID`
  - `C_PAYMENT_ID`
  - `C_BPARTNER_ID`
  - `M_PRODUCT_ID`

## Important Data Quality Note

The older `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` is useful for customer dimensions, but its order and invoice CA totals are not reliable because the original query joined orders and invoices together before aggregation.

Use the corrected portfolio query in the SQL file. It aggregates orders and invoices separately first, then joins the results to the customer dimension.

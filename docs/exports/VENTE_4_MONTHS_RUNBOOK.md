# Vente 4-Month Snapshot Runbook

Use this tomorrow when the Oracle/Compiere database opens again.

## Current Scope Confirmed

From the latest checks:

| Dataset | Rows |
|---|---:|
| C_ORDER_SELECTED | 3,350 |
| C_ORDERLINE_SELECTED | 68,270 |
| C_INVOICE_SELECTED | 1,192 |
| C_INVOICELINE_SELECTED | 62,797 |
| C_BPARTNER_SELECTED | 313 |
| M_PRODUCT_SELECTED | 16,336 |

## Vente Filters

Orders:

```sql
DATEORDERED >= ADD_MONTHS(TRUNC(SYSDATE), -4)
AND ISSOTRX = 'Y'
AND DOCSTATUS IN ('CO', 'CL')
AND C_DOCTYPETARGET_ID IN (1000028, 1000034, 1000032, 1000031)
```

Invoices:

```sql
DATEINVOICED >= ADD_MONTHS(TRUNC(SYSDATE), -4)
AND ISSOTRX = 'Y'
AND DOCSTATUS = 'CO'
AND C_DOCTYPE_ID IN (1000002, 1000003, 1000004)
```

Document types included:

| Table | IDs | Meaning seen in Oracle |
|---|---|---|
| C_ORDER | 1000028 | Commande standard |
| C_ORDER | 1000034 | Depot |
| C_ORDER | 1000032 | Marche |
| C_ORDER | 1000031 | Vente Comptoir |
| C_INVOICE | 1000002 | Facture client |
| C_INVOICE | 1000003 | Facture client indirecte |
| C_INVOICE | 1000004 | Avoir client |

## Run Order

Open:

```text
docs/exports/queries/vente_4_months.sql
```

Run and export each SQL block separately, in this order:

1. `01_C_ORDER.csv`
2. `02_C_ORDERLINE.csv`
3. `03_C_INVOICE.csv`
4. `04_C_INVOICELINE.csv`
5. `05_C_BPARTNER.csv`
6. `06_M_PRODUCT.csv`
7. `07_C_DOCTYPE.csv`
8. `08_M_PRODUCT_CATEGORY.csv`
9. `09_C_TAX.csv`
10. `10_AD_ORG.csv`

If time is short, prioritize files 1 to 7.

## Export Rules

- Export with header row.
- Use UTF-8 if the tool gives an encoding choice.
- Keep column names unchanged.
- Save filenames exactly as listed above.
- Put all files in one folder named:

```text
lpn-vente-4months-YYYYMMDD
```

## Quick Validation Before Leaving The DB

After exporting, check:

- `01_C_ORDER.csv` has around 3,350 data rows.
- `02_C_ORDERLINE.csv` has around 68,270 data rows.
- `03_C_INVOICE.csv` has around 1,192 data rows.
- `04_C_INVOICELINE.csv` has around 62,797 data rows.
- CSV files open with visible headers.
- Date columns are present:
  - `DATEORDERED` in `C_ORDER`
  - `DATEINVOICED` in `C_INVOICE`
- Key IDs are present:
  - `C_ORDER_ID`
  - `C_ORDERLINE_ID`
  - `C_INVOICE_ID`
  - `C_INVOICELINE_ID`
  - `C_BPARTNER_ID`
  - `M_PRODUCT_ID`


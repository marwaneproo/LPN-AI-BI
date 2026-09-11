# Vente Fourth Extraction Audit

Generated at: `2026-06-01T09:10:44.884899+00:00`

## Workbook Inventory

| File | Role | Sheet | Rows | Columns | Inferred content | Main headers |
|---|---|---|---:|---:|---|---|
| `1.xlsx` | `additional_candidate` | `Sheet1` | 1 | 2 | `UNKNOWN` | DATE_DEBUT, DATE_FIN |
| `2.xlsx` | `additional_candidate` | `Sheet1` | 1 | 1 | `UNKNOWN` | NB_COMMANDES |
| `27_C_BP_GROUP.xlsx` | `required_dimension` | `Sheet1` | 1 | 13 | `C_BP_GROUP` | C_BP_GROUP_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, VALUE, NAME |
| `28_C_BPARTNER_LOCATION.xlsx` | `required_dimension` | `Sheet1` | 344 | 30 | `C_BPARTNER_LOCATION` | C_BPARTNER_LOCATION_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, NAME, ISBILLTO |
| `29_C_LOCATION.xlsx` | `required_dimension` | `Sheet1` | 296 | 20 | `C_LOCATION` | C_LOCATION_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, ADDRESS1, ADDRESS2 |
| `3.xlsx` | `additional_candidate` | `Sheet1` | 165589 | 74 | `UNKNOWN` | C_ORDER_ID, NUM_COMMANDE, DATE_COMMANDE, STATUT_COMMANDE, C_DOCTYPE_ID, C_DOCTYPETARGET_ID, TYPE_DOCUMENT, TYPE_ORDER, TYPEBC, MODE_TRANSMISSION |
| `30_C_PAYMENTTERM.xlsx` | `required_dimension` | `Sheet1` | 5 | 28 | `C_PAYMENTTERM` | C_PAYMENTTERM_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, NAME, DESCRIPTION |
| `31_SALES_TABLE_DISCOVERY.xlsx` | `metadata_discovery` | `Sheet1` | 8 | 2 | `SALES_TABLE_DISCOVERY` | OBJECT_TYPE, OBJECT_NAME |
| `4.xlsx` | `additional_candidate` | `Sheet1` | 165589 | 74 | `UNKNOWN` | C_ORDER_ID, NUM_COMMANDE, DATE_COMMANDE, STATUT_COMMANDE, C_DOCTYPE_ID, C_DOCTYPETARGET_ID, TYPE_DOCUMENT, TYPE_ORDER, TYPEBC, MODE_TRANSMISSION |

## Additional Candidate Details

### `1.xlsx`

- Sheet `Sheet1`: `UNKNOWN`, 1 rows, 2 columns.
- First sample: `{"DATE_DEBUT": "2025-11-22T00:00:00", "DATE_FIN": "2026-05-22T00:00:00"}`

### `2.xlsx`

- Sheet `Sheet1`: `UNKNOWN`, 1 rows, 1 columns.
- First sample: `{"NB_COMMANDES": 5387}`

### `3.xlsx`

- Sheet `Sheet1`: `UNKNOWN`, 165589 rows, 74 columns.
- First sample: `{"C_ORDER_ID": 1418404, "NUM_COMMANDE": "CVS395075", "DATE_COMMANDE": "2025-11-24T09:32:08", "STATUT_COMMANDE": "CL", "C_DOCTYPE_ID": 1000028, "C_DOCTYPETARGET_ID": 1000028, "TYPE_DOCUMENT": "Commande standard", "TYPE_ORDER": null}`

### `4.xlsx`

- Sheet `Sheet1`: `UNKNOWN`, 165589 rows, 74 columns.
- First sample: `{"C_ORDER_ID": 1418404, "NUM_COMMANDE": "CVS395075", "DATE_COMMANDE": "2025-11-24T09:32:08", "STATUT_COMMANDE": "CL", "C_DOCTYPE_ID": 1000028, "C_DOCTYPETARGET_ID": 1000028, "TYPE_DOCUMENT": "Commande standard", "TYPE_ORDER": null}`

## Discovery Summary

### `COMMISSION`

- `C_COMMISSION`
- `C_COMMISSIONAMT`
- `C_COMMISSIONDETAIL`
- `C_COMMISSIONLINE`
- `C_COMMISSIONRUN`

### `GOAL`

- `PA_GOAL`
- `PA_SLA_GOAL`

### `SALES`

- `C_SALESREGION`

## Recommendations

- Review discovery objects before extracting contract/objective/commission facts.
- Current 4th extraction closes customer group, geography, and payment-term dimensions if row counts/headers are valid.

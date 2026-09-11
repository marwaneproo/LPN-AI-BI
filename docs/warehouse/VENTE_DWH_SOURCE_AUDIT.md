# Vente Data Warehouse Source Audit

Generated at: `2026-06-01T08:11:46.161253+00:00`

## Package Coverage

### Canonical vente import

| Table | Rows | Columns | Source |
|---|---:|---:|---|
| `AD_ORG` | 1 | 11 | `10_AD_ORG.xlsx` |
| `C_ALLOCATIONHDR` | 136 | 21 | `12_C_ALLOCATIONHDR.xlsx` |
| `C_ALLOCATIONLINE` | 245 | 23 | `11_C_ALLOCATIONLINE.xlsx` |
| `C_BPARTNER` | 313 | 123 | `05_C_BPARTNER.xlsx` |
| `C_DOCTYPE` | 7 | 33 | `08_C_DOCTYPE.xlsx` |
| `C_INVOICE` | 1182 | 96 | `03_C_INVOICE.xlsx` |
| `C_INVOICELINE` | 62146 | 61 | `04_C_INVOICELINE.xlsx` |
| `C_ORDER` | 3350 | 136 | `Book5.xlsx` |
| `C_ORDERLINE` | 67862 | 112 | `02_C_ORDERLINE.xlsx` |
| `C_PAYMENT` | 134 | 87 | `13_C_PAYMENT.xlsx` |
| `C_TAX` | 3 | 25 | `09_C_TAX.xlsx` |
| `M_INOUT` | 4667 | 113 | `14_M_INOUT.xlsx` |
| `M_INOUTLINE` | 181631 | 43 | `15_M_INOUTLINE.xlsx` |
| `M_LOCATOR` | 1 | 17 | `17_M_LOCATOR.xlsx` |
| `M_PRODUCT` | 16345 | 101 | `06_M_PRODUCT.xlsx` |
| `M_PRODUCT_CATEGORY` | 26 | 16 | `07_M_PRODUCT_CATEGORY.xlsx` |
| `M_WAREHOUSE` | 1 | 13 | `16_M_WAREHOUSE.xlsx` |
| `RV_STORAGE` | 58826 | 35 | `18_RV_STORAGE_SALES_PRODUCTS.xlsx` |

### BI enrichment import

| Table | Rows | Columns | Source |
|---|---:|---:|---|
| `AD_USER` | 70 | 8 | `19_AD_USER_SALESREPS.xlsx` |
| `C_BPARTNER_VENDOR` | 9633 | 123 | `21_C_BPARTNER_VENDORS.xlsx` |
| `M_PRODUCT_COLLECTION` | 86183 | 14 | `26_M_PRODUCT_COLLECTION.xlsx` |
| `M_PRODUCT_PO` | 46471 | 41 | `20_M_PRODUCT_PO.xlsx` |
| `M_PRODUCT_THEME` | 1487 | 12 | `25_M_PRODUCT_THEME.xlsx` |
| `M_PRODUCT_TYPE` | 27 | 12 | `24_M_PRODUCT_TYPE.xlsx` |
| `PRODUCTS_WITHOUT_SUPPLIER` | 3 | 4 | `22_PRODUCTS_WITHOUT_SUPPLIER.xlsx` |

## File Classification Summary

| Role | File count |
|---|---:|
| `bi_enrichment_csv` | 7 |
| `bi_enrichment_raw_source` | 7 |
| `canonical_raw_source` | 18 |
| `clean_import_csv` | 18 |
| `diagnostic_or_quality_file` | 2 |
| `duplicate_candidate` | 3 |
| `legacy_first_extraction` | 23 |
| `manifest_or_documentation` | 4 |
| `non_tabular_or_ignored` | 1 |
| `raw_candidate` | 1 |
| `raw_candidate_or_diagnostic` | 6 |

## File-By-File Inventory

| File | Role | Inferred table | Sheets / rows | Notes |
|---|---|---|---:|---|
| `Youssef_Extractions\1st_Extraction\Extract_01.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 489x4 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_02.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 85x2 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_03.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 24x2 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_04.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 4153x6 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_05.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 287x6 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_06.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 1265x9 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_08.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 16x5 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_09.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 225x5 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_10.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 57x5 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_11.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 491x4 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_12.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 15x5 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_13.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 1000x12 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_15.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 231x4 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_16.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 13x3 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_18.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 4x3 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_19.xlsx` | `legacy_first_extraction` | `C_DOCTYPE` | Sheet 1: 80x5 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_20.xlsx` | `legacy_first_extraction` | `C_ORDER` | Sheet 1: 200x11 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_21.xlsx` | `legacy_first_extraction` | `C_ORDERLINE` | Sheet 1: 500x12 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_22.xlsx` | `legacy_first_extraction` | `C_INVOICE` | Sheet 1: 200x8 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_23.xlsx` | `legacy_first_extraction` | `C_ORDERLINE` | Sheet 1: 1000x21 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_24.xlsx` | `legacy_first_extraction` | `C_ALLOCATIONLINE` | Sheet 1: 1000x13 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_25.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 13x4 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\1st_Extraction\Extract_26.xlsx` | `legacy_first_extraction` | `` | Sheet 1: 38x7 | Earlier exploratory extraction; likely superseded by 2nd/3rd canonical packages. |
| `Youssef_Extractions\2nd_Extraction\02_C_ORDERLINE(1).xlsx` | `duplicate_candidate` | `C_ORDERLINE` | Sheet1: 3350x136; Sheet2: 67862x112 | Filename indicates a repeated export copy. |
| `Youssef_Extractions\2nd_Extraction\02_C_ORDERLINE(2).xlsx` | `duplicate_candidate` | `C_ORDERLINE` | Sheet1: 3350x136; Sheet2: 67862x112 | Filename indicates a repeated export copy. |
| `Youssef_Extractions\2nd_Extraction\02_C_ORDERLINE.xlsx` | `canonical_raw_source` | `C_ORDERLINE` | Sheet1: 3350x136; Sheet2: 67862x112 | Used by vente_clean_import for: C_ORDERLINE. |
| `Youssef_Extractions\2nd_Extraction\03_C_INVOICE.xlsx` | `canonical_raw_source` | `C_INVOICE` | Sheet1: 1182x96 | Used by vente_clean_import for: C_INVOICE. |
| `Youssef_Extractions\2nd_Extraction\04_C_INVOICELINE.xlsx` | `canonical_raw_source` | `C_INVOICELINE` | Sheet2: 62146x61 | Used by vente_clean_import for: C_INVOICELINE. |
| `Youssef_Extractions\2nd_Extraction\05_C_BPARTNER.xlsx` | `canonical_raw_source` | `C_BPARTNER` | Sheet1: 313x123 | Used by vente_clean_import for: C_BPARTNER. |
| `Youssef_Extractions\2nd_Extraction\06_M_PRODUCT.xlsx` | `canonical_raw_source` | `M_PRODUCT` | Sheet2: 16345x101 | Used by vente_clean_import for: M_PRODUCT. |
| `Youssef_Extractions\2nd_Extraction\07_M_PRODUCT_CATEGORY.xlsx` | `canonical_raw_source` | `M_PRODUCT_CATEGORY` | Sheet1: 26x16 | Used by vente_clean_import for: M_PRODUCT_CATEGORY. |
| `Youssef_Extractions\2nd_Extraction\08_C_DOCTYPE.xlsx` | `canonical_raw_source` | `C_DOCTYPE` | Sheet1: 7x33 | Used by vente_clean_import for: C_DOCTYPE. |
| `Youssef_Extractions\2nd_Extraction\09_C_TAX.xlsx` | `canonical_raw_source` | `C_TAX` | Sheet2: 3x25 | Used by vente_clean_import for: C_TAX. |
| `Youssef_Extractions\2nd_Extraction\10_AD_ORG.xlsx` | `canonical_raw_source` | `AD_ORG` | Sheet3: 1x11 | Used by vente_clean_import for: AD_ORG. |
| `Youssef_Extractions\2nd_Extraction\11_C_ALLOCATIONLINE.xlsx` | `canonical_raw_source` | `C_ALLOCATIONLINE` | Sheet2: 245x23 | Used by vente_clean_import for: C_ALLOCATIONLINE. |
| `Youssef_Extractions\2nd_Extraction\12_C_ALLOCATIONHDR.xlsx` | `canonical_raw_source` | `C_ALLOCATIONHDR` | Sheet1: 136x21 | Used by vente_clean_import for: C_ALLOCATIONHDR. |
| `Youssef_Extractions\2nd_Extraction\13_C_PAYMENT.xlsx` | `canonical_raw_source` | `C_PAYMENT` | Sheet2: 134x87 | Used by vente_clean_import for: C_PAYMENT. |
| `Youssef_Extractions\2nd_Extraction\14_M_INOUT.xlsx` | `canonical_raw_source` | `M_INOUT` | Sheet3: 4667x113 | Used by vente_clean_import for: M_INOUT. |
| `Youssef_Extractions\2nd_Extraction\15_M_INOUTLINE.xlsx` | `canonical_raw_source` | `M_INOUTLINE` | Sheet4: 181631x43 | Used by vente_clean_import for: M_INOUTLINE. |
| `Youssef_Extractions\2nd_Extraction\16_M_WAREHOUSE.xlsx` | `canonical_raw_source` | `M_WAREHOUSE` | Sheet5: 1x13 | Used by vente_clean_import for: M_WAREHOUSE. |
| `Youssef_Extractions\2nd_Extraction\17_M_LOCATOR(1).xlsx` | `duplicate_candidate` | `M_LOCATOR` | Sheet6: 1x17 | Filename indicates a repeated export copy. |
| `Youssef_Extractions\2nd_Extraction\17_M_LOCATOR.xlsx` | `canonical_raw_source` | `M_LOCATOR` | Sheet6: 1x17 | Used by vente_clean_import for: M_LOCATOR. |
| `Youssef_Extractions\2nd_Extraction\18_RV_STORAGE_SALES_PRODUCTS.xlsx` | `canonical_raw_source` | `RV_STORAGE` | Sheet1: 58826x35 | Used by vente_clean_import for: RV_STORAGE. |
| `Youssef_Extractions\2nd_Extraction\18A_M_STORAGE_COLUMNS.xlsx` | `diagnostic_or_quality_file` | `STORAGE_COLUMN_DIAGNOSTIC` | Sheet1: 33x4 | Diagnostic/quality-control export, not a main DW fact/dimension. |
| `Youssef_Extractions\2nd_Extraction\18B_RV_STORAGE_COLUMNS.xlsx` | `diagnostic_or_quality_file` | `RV_STORAGE` | Sheet1: 35x4 | Diagnostic/quality-control export, not a main DW fact/dimension. |
| `Youssef_Extractions\2nd_Extraction\Book1.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet1: 6x2 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\2nd_Extraction\Book2.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet1: 6x2; Sheet2: 6x2 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\2nd_Extraction\Book3.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet1: 29x8 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\2nd_Extraction\Book4.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet1: 6x2 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\2nd_Extraction\Book5.xlsx` | `canonical_raw_source` | `C_ORDER` | Sheet1: 3350x136 | Used by vente_clean_import for: C_ORDER. |
| `Youssef_Extractions\2nd_Extraction\Book6.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet1: 17x8 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\2nd_Extraction\Book7.xlsx` | `raw_candidate_or_diagnostic` | `` | Sheet7: 9x1; Sheet8: 21x2 | Workbook name is generic; use manifests to decide whether it is canonical. |
| `Youssef_Extractions\3rd_Extraction\19_AD_USER_SALESREPS.xlsx` | `bi_enrichment_raw_source` | `AD_USER` | Sheet1: 70x8 | Used by vente_bi_enrichment_import for: AD_USER. |
| `Youssef_Extractions\3rd_Extraction\20_M_PRODUCT_PO.xlsx` | `bi_enrichment_raw_source` | `M_PRODUCT_PO` | Sheet1: 46471x41 | Used by vente_bi_enrichment_import for: M_PRODUCT_PO. |
| `Youssef_Extractions\3rd_Extraction\21_C_BPARTNER_VENDORS.xlsx` | `bi_enrichment_raw_source` | `C_BPARTNER_VENDOR` | Sheet1: 9633x123 | Used by vente_bi_enrichment_import for: C_BPARTNER_VENDOR. |
| `Youssef_Extractions\3rd_Extraction\22_PRODUCTS_WITHOUT_SUPPLIER.xlsx` | `bi_enrichment_raw_source` | `PRODUCTS_WITHOUT_SUPPLIER` | Sheet1: 3x4 | Used by vente_bi_enrichment_import for: PRODUCTS_WITHOUT_SUPPLIER. |
| `Youssef_Extractions\3rd_Extraction\23_C_BP_GROUP.xlsx` | `raw_candidate` | `M_PRODUCT_TYPE` | Sheet2: 27x12 |  |
| `Youssef_Extractions\3rd_Extraction\24_M_PRODUCT_TYPE.xlsx` | `bi_enrichment_raw_source` | `M_PRODUCT_TYPE` | Sheet2: 27x12 | Used by vente_bi_enrichment_import for: M_PRODUCT_TYPE. |
| `Youssef_Extractions\3rd_Extraction\25_M_PRODUCT_THEME.xlsx` | `bi_enrichment_raw_source` | `M_PRODUCT_THEME` | Sheet1: 1487x12 | Used by vente_bi_enrichment_import for: M_PRODUCT_THEME. |
| `Youssef_Extractions\3rd_Extraction\26_M_PRODUCT_COLLECTION.xlsx` | `bi_enrichment_raw_source` | `M_PRODUCT_COLLECTION` | Sheet1: 86183x14 | Used by vente_bi_enrichment_import for: M_PRODUCT_COLLECTION. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\AD_USER.csv` | `bi_enrichment_csv` | `AD_USER` | CSV: 70x8 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\C_BPARTNER_VENDOR.csv` | `bi_enrichment_csv` | `C_BPARTNER_VENDOR` | CSV: 9633x123 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\M_PRODUCT_COLLECTION.csv` | `bi_enrichment_csv` | `M_PRODUCT_COLLECTION` | CSV: 86183x14 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\M_PRODUCT_PO.csv` | `bi_enrichment_csv` | `M_PRODUCT_PO` | CSV: 46471x41 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\M_PRODUCT_THEME.csv` | `bi_enrichment_csv` | `M_PRODUCT_THEME` | CSV: 1487x12 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\M_PRODUCT_TYPE.csv` | `bi_enrichment_csv` | `M_PRODUCT_TYPE` | CSV: 27x12 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\csv\PRODUCTS_WITHOUT_SUPPLIER.csv` | `bi_enrichment_csv` | `PRODUCTS_WITHOUT_SUPPLIER` | CSV: 3x4 | CSV enrichment staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_bi_enrichment_import\manifest.json` | `manifest_or_documentation` | `` |  | Package metadata/documentation. |
| `Youssef_Extractions\vente_clean_import\csv\AD_ORG.csv` | `clean_import_csv` | `AD_ORG` | CSV: 1x11 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_ALLOCATIONHDR.csv` | `clean_import_csv` | `C_ALLOCATIONHDR` | CSV: 136x21 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_ALLOCATIONLINE.csv` | `clean_import_csv` | `C_ALLOCATIONLINE` | CSV: 245x23 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_BPARTNER.csv` | `clean_import_csv` | `C_BPARTNER` | CSV: 313x123 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_DOCTYPE.csv` | `clean_import_csv` | `C_DOCTYPE` | CSV: 7x33 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_INVOICE.csv` | `clean_import_csv` | `C_INVOICE` | CSV: 1182x96 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_INVOICELINE.csv` | `clean_import_csv` | `C_INVOICELINE` | CSV: 62146x61 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_ORDER.csv` | `clean_import_csv` | `C_ORDER` | CSV: 3350x136 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_ORDERLINE.csv` | `clean_import_csv` | `C_ORDERLINE` | CSV: 67862x112 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_PAYMENT.csv` | `clean_import_csv` | `C_PAYMENT` | CSV: 134x87 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\C_TAX.csv` | `clean_import_csv` | `C_TAX` | CSV: 3x25 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_INOUT.csv` | `clean_import_csv` | `M_INOUT` | CSV: 4667x113 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_INOUTLINE.csv` | `clean_import_csv` | `M_INOUTLINE` | CSV: 181631x43 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_LOCATOR.csv` | `clean_import_csv` | `M_LOCATOR` | CSV: 1x17 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_PRODUCT.csv` | `clean_import_csv` | `M_PRODUCT` | CSV: 16345x101 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_PRODUCT_CATEGORY.csv` | `clean_import_csv` | `M_PRODUCT_CATEGORY` | CSV: 26x16 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\M_WAREHOUSE.csv` | `clean_import_csv` | `M_WAREHOUSE` | CSV: 1x13 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\csv\RV_STORAGE.csv` | `clean_import_csv` | `RV_STORAGE` | CSV: 58826x35 | CSV canonical staging output for PostgreSQL import. |
| `Youssef_Extractions\vente_clean_import\manifest.json` | `manifest_or_documentation` | `` |  | Package metadata/documentation. |
| `Youssef_Extractions\vente_clean_import\README.md` | `manifest_or_documentation` | `` |  | Package metadata/documentation. |
| `Youssef_Extractions\vente_clean_import\source_mapping.json` | `manifest_or_documentation` | `` |  | Package metadata/documentation. |

## DW Readiness Assessment

### Strong coverage already available

- Order headers and lines: `C_ORDER`, `C_ORDERLINE`.
- Invoice headers and lines: `C_INVOICE`, `C_INVOICELINE`.
- Customer and vendor references: `C_BPARTNER`, `C_BPARTNER_VENDOR`.
- Commercial names: `AD_USER`, joinable through `SALESREP_ID`.
- Product/category/type/theme/collection and supplier relation: `M_PRODUCT`, `M_PRODUCT_CATEGORY`, `M_PRODUCT_PO`, enrichment tables.
- Delivery flow: `M_INOUT`, `M_INOUTLINE`, plus warehouse/locator references.
- Payment allocation flow: `C_ALLOCATIONLINE`, `C_ALLOCATIONHDR`, `C_PAYMENT`.
- Stock availability snapshot: `RV_STORAGE`.
- Document/tax/org references: `C_DOCTYPE`, `C_TAX`, `AD_ORG`.

### Gaps before a full professional sales DW

- Real customer group dimension still needs a verified `C_BP_GROUP`; current `23_C_BP_GROUP.xlsx` must be checked because it may not be the expected table.
- Contracts, price agreements, commercial targets, commissions, and sales objectives are not covered by current extracts.
- Geographic/customer address analysis needs `C_BPARTNER_LOCATION` and `C_LOCATION`.
- Payment-term and due-date aging analysis needs `C_PAYMENTTERM` and invoice due-date fields if not already complete.
- Commercial hierarchy/teams/roles need user-role or sales-region exports if the business wants team-level performance.
- Full margin analysis needs purchase/cost source tables, not only supplier relation.

## Recommended Next Exports

1. `27_C_BP_GROUP.xlsx` - actual customer/vendor group reference.
2. `28_C_BPARTNER_LOCATION.xlsx` - partner addresses and location links.
3. `29_C_LOCATION.xlsx` - city/region/country fields for geographic sales.
4. `30_C_PAYMENTTERM.xlsx` - payment-term labels and due policy.
5. Discovery query exports for contract/objective/commission tables before requesting large data extracts.

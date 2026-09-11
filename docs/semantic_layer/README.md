# LPN AI-BI Semantic Layer

This folder contains the business-language layer for the sales process.

It complements `docs/schema_metadata.csv`:

- `schema_metadata.csv` describes current queryable `business` schema objects for Schema RAG.
- `business_terms.csv` maps French and English business wording to metrics, dimensions, tables, and join hints.
- `metrics.yml` defines canonical KPIs with formulas, date fields, grain, and caveats.
- `dwh_assets.csv` catalogs the data warehouse CSV outputs for Power BI, Superset, and future backend integration.

## Current Runtime Rule

Only objects in `docs/schema_metadata.csv` should be treated as directly queryable by the current Text-to-SQL runtime.

The DWH assets are generated CSV outputs today. They are analytics-ready, but they should not be retrieved as SQL tables until they are imported or materialized into the runtime database.

## Preferred Business Objects

- Commercial labels: use `V_SALESREP_USER`.
- Supplier analytics: use `V_PRODUCT_PRIMARY_SUPPLIER`.
- CA facture: use `C_INVOICE.GRANDTOTAL` at invoice grain or `C_INVOICELINE.LINENETAMT` at product/category/supplier grain.
- CA commande: use `C_ORDER.GRANDTOTAL` at order grain or `C_ORDERLINE.LINENETAMT` at product/category grain.
- Paid/unpaid invoices: use `C_INVOICE.ISPAID`.
- Payments linked to invoices: use `C_ALLOCATIONLINE` as the bridge.

## Why This Exists

The ERP schema uses technical table names and several business concepts can be expressed many ways:

- `CA`, `chiffre d'affaires`, `ventes facturees`.
- `commercial`, `vendeur`, `sales rep`.
- `article`, `produit`, `type d'article`, `categorie`.
- `fournisseur`, `vendor`, primary supplier.

This layer makes those mappings explicit before prompt, retrieval, and model changes are made.

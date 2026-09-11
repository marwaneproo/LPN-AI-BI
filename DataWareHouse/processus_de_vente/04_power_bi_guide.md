# Power BI Guide - Processus de Vente

## Goal

This guide explains how to use the sales-process warehouse outputs in Power BI. It is written for someone who is not yet a data warehouse expert.

## What You Have Now

The ETL creates four categories of files:

- `staging`: cleaned source copies. Use only for debugging.
- `dimensions`: descriptive tables used as filters, slicers, and labels.
- `facts`: detailed business events with measures.
- `marts`: ready-made reporting tables for simple dashboards.

For a first Power BI report, start with the `mart_*` files. They are easier and safer.

## Recommended Beginner Power BI Approach

### Option A - Simple Executive Report

Use this first if you want fast reporting.

Import these files:

- `etl/output/marts/mart_sales_overview.csv`
- `etl/output/marts/mart_sales_by_commercial.csv`
- `etl/output/marts/mart_sales_by_customer.csv`
- `etl/output/marts/mart_sales_by_product.csv`
- `etl/output/marts/mart_sales_by_supplier.csv`
- `etl/output/marts/mart_sales_by_region.csv`

Suggested pages:

- CA overview by month.
- CA by commercial.
- Top clients.
- Top products.
- Top suppliers.
- CA by region/city.

This approach avoids relationship mistakes because each mart is already aggregated.

### Option B - Star Schema Report

Use this when you want deeper analysis and cross-filtering.

Import:

- all `etl/output/dimensions/dim_*.csv`
- selected `etl/output/facts/fact_*.csv`

Recommended starting facts:

- `fact_invoice` for official CA.
- `fact_invoice_line` for product/category/supplier CA.
- `fact_sales_order` for order pipeline.
- `fact_sales_order_line` for ordered quantities and product demand.

Create relationships:

- `dim_date.date_key` to fact date keys.
- `dim_customer.customer_key` to fact customer keys.
- `dim_commercial.commercial_key` to fact commercial keys.
- `dim_product.product_key` to line facts.
- `dim_supplier.supplier_key` to line facts.
- `dim_product_category.product_category_key` to line facts.

Relationship direction should normally be single direction from dimension to fact.

## Suggested Measures

Create these DAX measures in Power BI:

```DAX
CA Facture = SUM(fact_invoice[grand_total_amount])

Nombre Factures = SUM(fact_invoice[invoice_count])

Factures Payees = SUM(fact_invoice[paid_invoice_count])

Factures Impayees = SUM(fact_invoice[unpaid_invoice_count])

CA Commande = SUM(fact_sales_order[grand_total_amount])

Nombre Commandes = SUM(fact_sales_order[order_count])

Quantite Facturee = SUM(fact_invoice_line[quantity_invoiced])

CA Ligne Facture = SUM(fact_invoice_line[line_net_amount])
```

For stock:

```DAX
Stock Disponible = SUM(fact_stock_snapshot[quantity_available])
```

Only use stock measures for one snapshot date at a time unless you intentionally want to compare snapshots.

## Recommended Visuals

### Executive Page

- KPI cards: CA facture, commandes, factures, impayees.
- Line chart: CA facture by month.
- Bar chart: CA by commercial.
- Bar chart: top 10 clients.
- Bar chart: top 10 products.
- Bar chart: CA by supplier.

### Sales Operations Page

- Orders vs invoices by month.
- Ordered quantity vs delivered quantity vs invoiced quantity.
- Delivery count by warehouse.
- Unpaid invoice amount by customer.

### Product and Supplier Page

- CA by product category.
- CA by supplier.
- Top products by invoiced CA.
- Stock available for sold products.

## Design Best Practices

- Use MAD formatting with thousands separators.
- Keep one main story per page.
- Avoid too many charts on one page.
- Use slicers for date, commercial, customer, supplier, and product category.
- Sort top-N charts by value descending.
- Use tooltips to show IDs and exact values.
- Use marts for presentations and facts/dimensions for analysis.

## Current Data Limitation

The current project data is enough for the first warehouse and BI model, but it is not yet enough for robust yearly trends.

For full yearly reporting, extract 24 months of:

- orders and order lines.
- invoices and invoice lines.
- deliveries and delivery lines.
- allocations/payments.
- product, supplier, customer, commercial, geography reference data.

After that export, rerun:

```powershell
python DataWareHouse\processus_de_vente\etl\scripts\run_etl.py
```

Then refresh Power BI.

## Refresh Workflow

1. Export the ERP source files into the expected extraction folders.
2. Run the ETL script.
3. Check `etl/validation/etl_validation_report.md`.
4. If validation is PASS, refresh Power BI.
5. If validation is WARN, inspect row counts, totals, and zero-key profiles before using the report.


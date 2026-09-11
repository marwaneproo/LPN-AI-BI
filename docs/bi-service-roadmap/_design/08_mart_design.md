# DW-08 — Mart Serving Contract

**Task:** DW-08
**Date:** 2026-06-29
**Status:** Implemented in `docs/bi-service-roadmap/sql/marts.sql`
**Supersedes:** the earlier design-only BI-08 draft
**Contract schema:** `mart`

This document is the read contract for the BI API. All views are built on top of
`warehouse.dim_*` and `warehouse.fact_*`; no mart reads `business.*`, `staging.*`,
raw source files, or the live Oracle ERP.

## 1. Metric Rules

The mart SQL reuses `docs/semantic_layer/metrics.yml` formulas:

| Metric | Mart expression |
|---|---|
| `ca_commande` | `SUM(warehouse.fact_sales_order.grand_total_amount)` |
| `ca_facture` | `SUM(warehouse.fact_invoice.grand_total_amount)` |
| Product/article CA | `SUM(warehouse.fact_invoice_line.line_net_amount)` |
| `nombre_commandes` | `COUNT(DISTINCT warehouse.fact_sales_order.c_order_id)` |
| `nombre_clients_actifs` | `COUNT(DISTINCT warehouse.fact_sales_order.customer_key)` |
| `nombre_produits_vendus` | `COUNT(DISTINCT warehouse.fact_invoice_line.product_key)` excluding key `0` |
| `factures_payees` | `SUM(warehouse.fact_invoice.paid_invoice_count)` |
| `factures_impayees` | `SUM(warehouse.fact_invoice.unpaid_invoice_count)` |
| `couverture_facturation` | quantity coverage in `mart_commandes`; amount coverage in `mart_revenue` |
| `stock_disponible` | `SUM(warehouse.fact_stock_snapshot.quantity_available)` at one snapshot only |
| `produits_stock_risque` | product/stock row count where available quantity is `<= 0` |

Known carry-forward: 2024 product attribution is incomplete because retired 2024
product IDs resolve to `product_key=0`. CA totals are unaffected; product/category/
supplier rankings place those rows under the unknown product bucket.

## 2. Mart Catalogue

### Page Views

| View | Grain | Primary columns |
|---|---|---|
| `mart.mart_overview` | `date_grain` + `period_start` (`day`, `week`, `month`) | `ca_commande`, `ca_facture`, `nombre_commandes`, `nombre_clients_actifs`, `nombre_produits_vendus`, `factures_payees`, `factures_impayees` |
| `mart.mart_commandes` | period + document type + status + commercial | `document_type_name`, `doc_status`, `commercial_name`, `nombre_commandes`, `ca_commande`, `quantity_ordered`, `quantity_delivered`, `quantity_invoiced` |
| `mart.mart_revenue` | period | `ca_facture`, `ca_commande`, `invoice_gap_amount`, `average_order_value`, `couverture_facturation_pct`, `factures_impayees`, `unpaid_invoice_amount` |
| `mart.mart_articles` | period + product + category/type/theme/collection + supplier + customer | `product_name`, `category_name`, `product_type_name`, `theme_name`, `collection_name`, `supplier_name`, `quantity_invoiced`, `ca_facture` |
| `mart.mart_clients` | period + customer + commercial + geography | `customer_name`, `commercial_name`, `city_name`, `region_name`, `invoice_count`, `ca_facture`, `unpaid_invoice_amount` |
| `mart.mart_commercial` | month + commercial | `commercial_name`, `ad_user_id`, `ca_commande`, `ca_facture`, `invoice_gap_amount`, `couverture_facturation_pct` |
| `mart.mart_stock` | latest/current snapshot + product + warehouse + locator | `qty_on_hand`, `qty_reserved`, `qty_available`, `stock_disponible`, `produits_stock_risque`, `stock_risk_level` |

### API Compatibility Helper Views

The current Spring BI repositories already query these names. DW-08 keeps them
warehouse-backed so the existing API can migrate without Java renaming:

| Helper view | Built from | Purpose |
|---|---|---|
| `mart.mart_sales_daily` | `mart_overview` daily rows | overview/revenue/analysis daily KPIs |
| `mart.mart_sales_monthly` | `mart_overview` monthly rows | monthly trend and yearly CA rollups |
| `mart.mart_sales_by_commercial` | warehouse order/invoice facts | daily commercial performance |
| `mart.mart_sales_by_customer` | `mart_clients` daily rows | client rankings and active-customer counts |
| `mart.mart_sales_by_product` | `mart_articles` daily rows | article rankings, category/theme/supplier mix |
| `mart.mart_sales_by_region` | `mart_clients` daily rows | geography ranking |
| `mart.mart_order_to_invoice_flow` | `mart_commandes` daily rows | order type/status and delivery/invoice coverage |
| `mart.mart_payment_status` | warehouse invoice/payment facts | paid/unpaid exposure and allocation amounts |
| `mart.mart_stock_risk` | `mart_stock` | article stock-priority widgets |

Compatibility note: helper views keep the existing repository column names. In
commercial/customer helpers, `commercial_key` remains the natural `ad_user_id`
expected by the current Java filters, while `commercial_dim_key` exposes the
warehouse surrogate where useful.

## 3. Widget Mapping

### Vue d'ensemble (`BiOverviewPage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| CA facture | `mart_overview` | `ca_facture` |
| Ventes commandees | `mart_overview` | `ca_commande`, `nombre_commandes` |
| Clients actifs | `mart_overview` | `nombre_clients_actifs`, `invoice_count` |
| Couverture facture | `mart_overview` | `couverture_facturation_pct`, `factures_payees`, `factures_impayees` |
| Activite mensuelle | `mart_overview` / `mart_sales_monthly` | `date_grain`, `period_start`, `ca_commande`, `ca_facture`, `nombre_commandes` |
| Signal commercial dominant | `mart_commercial` / `mart_sales_by_commercial` | `commercial_name`, `ca_facture` |
| Signal fournisseur moteur | `mart_articles` / `mart_sales_by_product` | `supplier_name`, `ca_facture` |
| Signal zone a suivre | `mart_clients` / `mart_sales_by_region` | `city_name`, `region_name`, `ca_facture` |
| Mix ventes facture | `mart_overview` | `ca_facture` |
| Mix ventes commande | `mart_overview` | `ca_commande` |
| Mix ventes articles suivis | `mart_overview` / `mart_articles` | `nombre_produits_vendus`, `product_key` |

### Type de commandes (`BiCommandesPage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| Commandes | `mart_commandes` | `nombre_commandes` |
| Type dominant | `mart_commandes` | `document_type_name`, `ca_commande` |
| Commandes facturees | `mart_commandes` | `couverture_facturation_pct`, `amount_invoice_coverage_percent` |
| Statuts | `mart_commandes` | `doc_status`, `nombre_commandes` |
| Commandes par type | `mart_commandes` | `c_doctype_id`, `document_type_name`, `nombre_commandes`, `ca_commande` |
| Lecture commerciale | `mart_commandes` / `mart_commercial` | `commercial_name`, `nombre_commandes`, `ca_commande`, `couverture_livraison_pct`, `couverture_facturation_pct` |
| Points de controle: commercial | `mart_commandes` / `mart_commercial` | `commercial_name`, `nombre_commandes`, `ca_commande` |
| Points de controle: type | `mart_commandes` | `document_type_name`, `ca_commande` |
| Points de controle: statut | `mart_commandes` | `doc_status`, `nombre_commandes`, `ca_commande` |

### Chiffre d'affaires (`BiRevenuePage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| CA reel | `mart_revenue` | `ca_facture`, `invoiced_sales` |
| CA commande | `mart_revenue` | `ca_commande`, `ordered_sales` |
| Ecart | `mart_revenue` | `invoice_gap_amount` |
| Panier moyen | `mart_revenue` | `average_order_value` |
| CA facture vs commande par annee | `mart_revenue` / `mart_sales_daily` | `year_number`, `ca_facture`, `ca_commande`, `invoice_gap_amount` |
| CA sur periode | `mart_revenue` | `date_grain`, `period_start`, `ca_facture`, `ca_commande` |
| Meilleur mois | `mart_revenue` / `mart_sales_monthly` | `period_start`, `period_month`, `ca_facture` |
| Couverture | `mart_revenue` | `couverture_facturation_pct` |
| Risque impaye | `mart_revenue` / `mart_payment_status` | `factures_impayees`, `unpaid_invoice_amount` |
| Statut CA facture | `mart_revenue` | `ca_facture` |
| Statut CA a livrer | `mart_revenue` / `mart_commandes` | `quantity_delivery_gap` |
| Statut CA ecart | `mart_revenue` | `invoice_gap_amount` |

### Articles (`BiArticlesPage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| Produits actifs | `mart_articles` | `product_key`, `nombre_produits_vendus` |
| Top categorie | `mart_articles` | `category_name`, `ca_facture` |
| Top theme | `mart_articles` | `theme_name`, `ca_facture` |
| Disponibilite | `mart_stock` / `mart_stock_risk` | `produits_stock_risque`, `stock_risk_level` |
| Top articles | `mart_articles` | `m_product_id`, `product_name`, `quantity_invoiced`, `ca_facture` |
| Mix categorie | `mart_articles` | `category_name`, `ca_facture` |
| Mix type article | `mart_articles` | `product_type_name`, `ca_facture` |
| Mix theme | `mart_articles` | `theme_name`, `ca_facture` |
| Mix collection | `mart_articles` | `collection_name`, `ca_facture` |
| Fournisseur | `mart_articles` | `supplier_id`, `supplier_name`, `ca_facture` |
| Priorites stock | `mart_stock` / `mart_stock_risk` | `product_name`, `qty_available`, `qty_reserved`, `is_stockout_risk`, `stock_risk_level` |

### Client (`BiClientsPage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| Clients actifs | `mart_clients` | `customer_key`, `active_customer_flag` |
| Top client | `mart_clients` | `customer_name`, `ca_facture` |
| Portefeuille | `mart_clients` | `commercial_name`, `customer_key`, `ca_facture` |
| Geographie | `mart_clients` | `city_name`, `region_name`, `geography_key` |
| Top clients | `mart_clients` | `c_bpartner_id`, `customer_name`, `invoice_count`, `ca_facture`, `average_invoice_value` |
| Client par angle: commercial | `mart_clients` | `commercial_name`, `customer_key`, `ca_facture` |
| Client par angle: article | `mart_articles` | `customer_name`, `product_name`, `quantity_invoiced`, `ca_facture` |
| Client par angle: region | `mart_clients` / `mart_sales_by_region` | `region_name`, `city_name`, `customer_key`, `invoice_count`, `ca_facture` |
| Lecture utile: risque finance | `mart_clients` / `mart_payment_status` | `customer_name`, `factures_impayees`, `unpaid_invoice_amount` |

### Commercial (`BiCommercialPage`)

| Visible widget | Mart view | Column(s) |
|---|---|---|
| Commerciaux | `mart_commercial` | `commercial_key`, `ad_user_id`, `commercial_count` |
| Top CA | `mart_commercial` | `commercial_name`, `ca_facture` |
| Conversion | `mart_commercial` | `couverture_facturation_pct`, `invoice_gap_amount` |
| Couverture region | `mart_clients` / `mart_sales_by_region` | `region_name`, `city_name`, `geography_key` |
| CA par commercial | `mart_commercial` | `commercial_name`, `ca_commande`, `ca_facture` |
| Conversion vente: commande | `mart_commercial` | `ca_commande` |
| Conversion vente: facture | `mart_commercial` | `ca_facture` |
| Conversion vente: reste | `mart_commercial` | `invoice_gap_amount` |
| Zone forte | `mart_clients` / `mart_sales_by_region` | `city_name`, `region_name`, `ca_facture` |
| Client cle | `mart_clients` | `customer_name`, `ca_facture` |
| Action forage | `mart_commercial`, `mart_clients` | `commercial_key`, `customer_key`, `geography_key` |

## 4. Stock Constraint

`fact_stock_snapshot.snapshot_date_key` is `0` for every row because the source
`DATELASTINVENTORY` field is null. `mart_stock` and `mart_stock_risk` therefore
represent the current imported snapshot with an unknown snapshot date. Stock
measures are semi-additive and must not be summed across multiple snapshots if a
future load introduces dated snapshots.

This is the only mart-area exception to the "2024 and 2025-2026 date coverage"
check. It is source-imposed; the view does not fabricate dates.

## 5. Grants

`marts.sql` grants:

```sql
GRANT USAGE ON SCHEMA mart TO lpn_ai_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA mart TO lpn_ai_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA mart
  GRANT SELECT ON TABLES TO lpn_ai_readonly;
```

It also applies the same default privilege for `lpn_app_admin` when that role
exists, because the pre-existing `mart` schema was owned by that role in native dev.

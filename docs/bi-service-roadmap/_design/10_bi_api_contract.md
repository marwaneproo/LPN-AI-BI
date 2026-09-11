# BI-10 - BI API Contract and DTO Design

**Task:** BI-10  
**Scope:** OpenAPI-style contract only  
**Status:** draft design only; no Java controllers, services, DTO classes, DDL, or
PostgreSQL calls were implemented.

---

## 1. Contract Position

BI-10 freezes the future BI serving contract under `/v1/bi/*`. It supersedes the
current raw-query endpoints later, but does not modify them now:

- Current live endpoints:
  - `GET /v1/sales-dashboard`
  - `GET /v1/sales-analysis`
- Future marts-only endpoints:
  - `GET /v1/bi/overview`
  - `GET /v1/bi/orders`
  - `GET /v1/bi/revenue`
  - `GET /v1/bi/articles`
  - `GET /v1/bi/clients`
  - `GET /v1/bi/commercial`
  - `GET /v1/bi/analysis`

The frontend folder contains six BI route components: `BiOverviewPage` plus five
detail pages. The prompt says "5 pages"; this contract covers the five detail
pages and the real overview route so no visible widget is orphaned. The live
`DashboardOverviewPage` maps to `/v1/bi/overview`; the live `SalesAnalysisPage`
maps to `/v1/bi/analysis`.

All business DTO fields below trace to BI-08 `mart.*` view columns. Protocol
metadata (`meta.request_id`, `meta.generated_at`, `meta.latency_ms`,
`meta.applied_filters`, `meta.pagination`) is not a business DTO and is excluded
from mart lineage.

---

## 2. Current Shape to Preserve

The existing Java and TypeScript shape uses snake_case JSON fields. Keep that
style.

| Current shape | Future alignment |
|---|---|
| `SalesDashboardSnapshot.kpis.order_count` | Keep as `kpis.order_count`; source `mart_sales_daily.nombre_commandes` or `mart_sales_monthly.nombre_commandes`. |
| `order_value` | Keep for legacy dashboard compatibility; alias from `ca_commande`. |
| `active_customers` | Keep; source invoiced or customer mart count depending page. |
| `invoice_count`, `invoiced_sales` | Keep; source `invoice_count`, `ca_facture`. |
| `paid_invoice_count`, `unpaid_invoice_count` | Keep; source `ISPAID`-backed mart columns. |
| `invoice_coverage_percent` | Keep; source `invoice_coverage_percent` or flow coverage fields. |
| `monthly_trend`, `trend` | Keep list semantics; `period` comes from mart date/period columns. |
| rank DTOs | Keep names where live frontend already consumes them; add mart-only lineage. |

Migration intent: existing controllers remain untouched until a later
implementation task. BI-10 defines the contract that later Java DTOs/controllers
must implement.

---

## 3. Common Request Parameters

All endpoints support these query parameters unless explicitly marked otherwise.

| Parameter | Type | Default | Validation | Mart filter columns |
|---|---|---|---|---|
| `from` | ISO date | latest complete period start | `YYYY-MM-DD`; must be <= `to` | `metric_date`, `snapshot_date`, `period_month` |
| `to` | ISO date | latest mart date | `YYYY-MM-DD`; max 36 months span for day granularity | `metric_date`, `snapshot_date`, `period_month` |
| `granularity` | enum | `month` for overview, `day` for detail if <= 45 days else `week` | `day`, `week`, `month` | date bucketing over `metric_date` or `period_month` |
| `compare` | boolean | `false` | `true` or `false` | self-join previous equivalent period over same mart columns |
| `commercial` | integer | none | accepts `commercial_key` or `ad_user_id` resolved server-side | `commercial_key`, `ad_user_id` |
| `category` | integer | none | accepts `product_category_key` or `m_product_category_id` | `product_category_key`, `m_product_category_id` |
| `supplier` | integer | none | accepts `supplier_key` or `supplier_id` | `supplier_key`, `supplier_id` |
| `customer` | integer | none | accepts `customer_key` or `c_bpartner_id` | `customer_key`, `c_bpartner_id` |
| `document_type` | integer | none | accepts `document_type_key` or `c_doctype_id` | `document_type_key`, `c_doctype_id` |
| `region` | string | none | exact normalized region label | `region_name`, `sales_region_name` |
| `city` | string | none | exact normalized city label | `city_name` |
| `payment_status` | enum | none | `PAID`, `UNPAID` | `payment_status`, `is_paid` |
| `limit` | integer | endpoint default | min 1, max 100 for ranked lists | applied after ORDER BY |
| `offset` | integer | 0 | min 0 | ranked-list pagination |

Filter names are intentionally user-facing and stable. Internally BI-10 allows
the implementation to resolve either surrogate keys or source IDs, but response
DTOs should return both where the mart exposes both.

---

## 4. Standard Response and Error Contract

### 4.1 Success envelope

```yaml
type: object
required: [meta, data]
properties:
  meta:
    type: object
    properties:
      request_id: string
      generated_at: string
      latency_ms: integer
      applied_filters: object
      pagination: object
  data:
    type: object
```

Business DTO fields live under `data`.

### 4.2 Error envelope

```yaml
type: object
required: [error]
properties:
  error:
    type: object
    required: [code, message]
    properties:
      code: string
      message: string
      details: object
      request_id: string
```

| HTTP | Code | When |
|---|---|---|
| 400 | `BI_INVALID_FILTER` | malformed dates, invalid enum, `from > to`, or limit too high |
| 404 | `BI_NO_DATA` | marts exist but no rows match the requested filters |
| 422 | `BI_UNSUPPORTED_FILTER_COMBINATION` | page cannot honor a requested filter with available mart columns |
| 503 | `BI_MART_UNAVAILABLE` | required `mart.*` view is missing or inaccessible |
| 500 | `BI_INTERNAL_ERROR` | unexpected service error |

---

## 5. Endpoint Contracts

### 5.1 `GET /v1/bi/overview`

Primary consumers: `BiOverviewPage`, live `DashboardOverviewPage`.

Supported filters: common filters except `customer`, `document_type` only applies
to order/status blocks.

Default limits: top customers 8, top products 8, quick signals 3, trend 12
periods.

```yaml
data:
  kpis: OverviewKpis
  trend: PeriodSalesPoint[]
  quick_signals: QuickSignals
  sales_mix: SalesMix
  top_customers: CustomerRank[]
  top_products: ProductRank[]
  order_statuses: StatusBreakdown[]
  invoice_statuses: StatusBreakdown[]
```

| DTO field | Mart lineage |
|---|---|
| `kpis.order_count` | `mart_sales_daily.nombre_commandes` |
| `kpis.order_value` | `mart_sales_daily.ca_commande` |
| `kpis.active_customers` | `mart_sales_daily.active_customers_invoiced` |
| `kpis.invoice_count` | `mart_sales_daily.invoice_count` |
| `kpis.invoiced_sales` | `mart_sales_daily.ca_facture` |
| `kpis.paid_invoice_count` | `mart_sales_daily.paid_invoice_count` |
| `kpis.unpaid_invoice_count` | `mart_sales_daily.unpaid_invoice_count` |
| `kpis.invoice_coverage_percent` | `mart_sales_daily.invoice_coverage_percent` |
| `trend[].period` | `mart_sales_monthly.period_month` or bucketed `mart_sales_daily.metric_date` |
| `trend[].order_count` | `mart_sales_monthly.nombre_commandes` |
| `trend[].order_value` | `mart_sales_monthly.ca_commande` |
| `trend[].invoice_count` | `mart_sales_monthly.invoice_count` |
| `trend[].invoiced_sales` | `mart_sales_monthly.ca_facture` |
| `quick_signals.commercial_dominant.name` | `mart_sales_by_commercial.commercial_name` |
| `quick_signals.commercial_dominant.value` | `mart_sales_by_commercial.ca_facture` |
| `quick_signals.fournisseur_moteur.name` | `mart_sales_by_product.supplier_name` |
| `quick_signals.fournisseur_moteur.value` | `mart_sales_by_product.ca_facture` |
| `quick_signals.zone_a_suivre.city_name` | `mart_sales_by_region.city_name` |
| `quick_signals.zone_a_suivre.region_name` | `mart_sales_by_region.region_name` |
| `quick_signals.zone_a_suivre.value` | `mart_sales_by_region.ca_facture` |
| `sales_mix.facture` | `mart_sales_daily.ca_facture` |
| `sales_mix.commande` | `mart_sales_daily.ca_commande` |
| `sales_mix.articles_suivis` | `mart_sales_by_product.nombre_produits_vendus` |
| `top_customers[].customer_name` | `mart_sales_by_customer.customer_name` |
| `top_customers[].order_count` | `mart_sales_by_customer.nombre_commandes` |
| `top_customers[].total_order_value` | `mart_sales_by_customer.ca_commande` |
| `top_customers[].average_order_value` | `mart_sales_by_customer.ca_commande / nombre_commandes`, derived from mart columns |
| `top_products[].product_name` | `mart_sales_by_product.product_name` |
| `top_products[].line_count` | `mart_sales_by_product.invoice_line_count` |
| `top_products[].total_quantity` | `mart_sales_by_product.quantity_invoiced` |
| `top_products[].total_order_value` | `mart_sales_by_product.ca_facture` |
| `order_statuses[].status` | `mart_order_to_invoice_flow.doc_status` |
| `order_statuses[].label` | `mart_order_to_invoice_flow.doc_status`, display mapping |
| `order_statuses[].item_count` | `mart_order_to_invoice_flow.nombre_commandes` |
| `order_statuses[].total_value` | `mart_order_to_invoice_flow.ca_commande` |
| `invoice_statuses[].status` | `mart_payment_status.payment_status` |
| `invoice_statuses[].label` | `mart_payment_status.payment_status`, display mapping |
| `invoice_statuses[].item_count` | `mart_payment_status.invoice_count` |
| `invoice_statuses[].total_value` | `mart_payment_status.ca_facture` |

### 5.2 `GET /v1/bi/orders`

Primary consumer: `BiCommandesPage`.

Supported filters: `from`, `to`, `granularity`, `compare`, `commercial`,
`document_type`, `region`, `limit`, `offset`.

```yaml
data:
  kpis: OrderKpis
  by_type: OrderTypeRank[]
  commercial_flow: CommercialOrderFlow[]
  status_breakdown: StatusBreakdown[]
```

| DTO field | Mart lineage |
|---|---|
| `kpis.order_count` | `mart_order_to_invoice_flow.nombre_commandes` |
| `kpis.dominant_type_name` | `mart_order_to_invoice_flow.document_type_name` ordered by `ca_commande` |
| `kpis.invoice_coverage_percent` | `mart_order_to_invoice_flow.quantity_invoice_coverage_percent` |
| `kpis.amount_invoice_coverage_percent` | `mart_order_to_invoice_flow.amount_invoice_coverage_percent` |
| `kpis.status_count` | distinct `mart_order_to_invoice_flow.doc_status` |
| `by_type[].order_type_id` | `mart_order_to_invoice_flow.c_doctype_id` |
| `by_type[].order_type_name` | `mart_order_to_invoice_flow.document_type_name` |
| `by_type[].order_count` | `mart_order_to_invoice_flow.nombre_commandes` |
| `by_type[].ordered_sales` | `mart_order_to_invoice_flow.ca_commande` |
| `commercial_flow[].salesrep_id` | `mart_sales_by_commercial.ad_user_id` |
| `commercial_flow[].commercial_label` | `mart_sales_by_commercial.commercial_name` |
| `commercial_flow[].order_count` | `mart_sales_by_commercial.nombre_commandes` |
| `commercial_flow[].ordered_sales` | `mart_sales_by_commercial.ca_commande` |
| `commercial_flow[].delivered_coverage_percent` | `mart_order_to_invoice_flow.quantity_delivery_coverage_percent` |
| `commercial_flow[].invoice_coverage_percent` | `mart_order_to_invoice_flow.quantity_invoice_coverage_percent` |
| `status_breakdown[].status` | `mart_order_to_invoice_flow.doc_status` |
| `status_breakdown[].label` | `mart_order_to_invoice_flow.doc_status`, display mapping |
| `status_breakdown[].item_count` | `mart_order_to_invoice_flow.nombre_commandes` |
| `status_breakdown[].total_value` | `mart_order_to_invoice_flow.ca_commande` |

### 5.3 `GET /v1/bi/revenue`

Primary consumer: `BiRevenuePage`.

Supported filters: all common filters. `document_type` applies through
`mart_order_to_invoice_flow` sections only.

```yaml
data:
  kpis: RevenueKpis
  trend: PeriodSalesPoint[]
  responsible_reading: RevenueSignals
  revenue_status: RevenueStatus
```

| DTO field | Mart lineage |
|---|---|
| `kpis.invoiced_sales` | `mart_sales_daily.ca_facture` |
| `kpis.ordered_sales` | `mart_sales_daily.ca_commande` |
| `kpis.invoice_gap_amount` | `mart_sales_daily.invoice_gap_amount` |
| `kpis.average_order_value` | `mart_sales_daily.average_order_value` |
| `trend[].period` | `mart_sales_daily.metric_date` or `mart_sales_monthly.period_month` |
| `trend[].ordered_sales` | `mart_sales_daily.ca_commande` |
| `trend[].invoiced_sales` | `mart_sales_daily.ca_facture` |
| `trend[].order_count` | `mart_sales_daily.nombre_commandes` |
| `trend[].invoice_count` | `mart_sales_daily.invoice_count` |
| `responsible_reading.best_month.period` | `mart_sales_monthly.period_month` |
| `responsible_reading.best_month.invoiced_sales` | `mart_sales_monthly.ca_facture` |
| `responsible_reading.invoice_coverage_percent` | `mart_sales_daily.invoice_coverage_percent` |
| `responsible_reading.unpaid_invoice_count` | `mart_payment_status.unpaid_invoice_count` |
| `responsible_reading.unpaid_invoice_amount` | `mart_payment_status.unpaid_invoice_amount` |
| `revenue_status.facture` | `mart_sales_daily.ca_facture` |
| `revenue_status.a_livrer` | `mart_order_to_invoice_flow.quantity_delivery_gap` |
| `revenue_status.ecart` | `mart_sales_daily.invoice_gap_amount` |

### 5.4 `GET /v1/bi/articles`

Primary consumer: `BiArticlesPage`.

Supported filters: `from`, `to`, `granularity`, `compare`, `category`,
`supplier`, `customer`, `region`, `limit`, `offset`.

```yaml
data:
  kpis: ArticleKpis
  top_articles: ProductRank[]
  mix_article: ArticleMix
  stock_priorities: StockRiskRank[]
```

| DTO field | Mart lineage |
|---|---|
| `kpis.active_products` | distinct `mart_sales_by_product.product_key` or `nombre_produits_vendus` |
| `kpis.top_category_name` | `mart_sales_by_product.category_name` ordered by `ca_facture` |
| `kpis.top_theme_name` | `mart_sales_by_product.theme_name` ordered by `ca_facture` |
| `kpis.stock_risk_count` | `mart_stock_risk.produits_stock_risque` |
| `top_articles[].product_id` | `mart_sales_by_product.m_product_id` |
| `top_articles[].product_name` | `mart_sales_by_product.product_name` |
| `top_articles[].category_name` | `mart_sales_by_product.category_name` |
| `top_articles[].supplier_id` | `mart_sales_by_product.supplier_id` |
| `top_articles[].supplier_name` | `mart_sales_by_product.supplier_name` |
| `top_articles[].line_count` | `mart_sales_by_product.invoice_line_count` |
| `top_articles[].quantity` | `mart_sales_by_product.quantity_invoiced` |
| `top_articles[].invoiced_sales` | `mart_sales_by_product.ca_facture` |
| `mix_article.categories[].name` | `mart_sales_by_product.category_name` |
| `mix_article.categories[].value` | `mart_sales_by_product.ca_facture` |
| `mix_article.themes[].name` | `mart_sales_by_product.theme_name` |
| `mix_article.themes[].value` | `mart_sales_by_product.ca_facture` |
| `mix_article.collections[].name` | `mart_sales_by_product.collection_name` |
| `mix_article.collections[].value` | `mart_sales_by_product.ca_facture` |
| `stock_priorities[].product_id` | `mart_stock_risk.m_product_id` |
| `stock_priorities[].product_name` | `mart_stock_risk.product_name` |
| `stock_priorities[].category_name` | `mart_stock_risk.category_name` |
| `stock_priorities[].theme_name` | `mart_stock_risk.theme_name` |
| `stock_priorities[].supplier_name` | `mart_stock_risk.supplier_name` |
| `stock_priorities[].qty_on_hand` | `mart_stock_risk.qty_on_hand` |
| `stock_priorities[].qty_reserved` | `mart_stock_risk.qty_reserved` |
| `stock_priorities[].qty_available` | `mart_stock_risk.qty_available` |
| `stock_priorities[].qty_ordered` | `mart_stock_risk.qty_ordered` |
| `stock_priorities[].is_stockout_risk` | `mart_stock_risk.is_stockout_risk` |
| `stock_priorities[].stock_risk_level` | `mart_stock_risk.stock_risk_level` |

### 5.5 `GET /v1/bi/clients`

Primary consumer: `BiClientsPage`.

Supported filters: `from`, `to`, `granularity`, `compare`, `commercial`,
`customer`, `category`, `supplier`, `region`, `city`, `payment_status`,
`limit`, `offset`.

```yaml
data:
  kpis: ClientKpis
  top_clients: CustomerRank[]
  by_commercial: CustomerCommercialRank[]
  by_article: CustomerArticleRank[]
  by_region: GeographyRank[]
  finance_risks: CustomerPaymentRisk[]
```

| DTO field | Mart lineage |
|---|---|
| `kpis.active_customers` | sum/distinct `mart_sales_by_customer.active_customer_flag` / `customer_key` |
| `kpis.top_customer_name` | `mart_sales_by_customer.customer_name` ordered by `ca_facture` |
| `kpis.portfolio_commercial_count` | distinct `mart_sales_by_customer.commercial_key` |
| `kpis.geography_count` | distinct `mart_sales_by_customer.region_name` / `city_name` |
| `top_clients[].customer_id` | `mart_sales_by_customer.c_bpartner_id` |
| `top_clients[].customer_name` | `mart_sales_by_customer.customer_name` |
| `top_clients[].invoice_count` | `mart_sales_by_customer.invoice_count` |
| `top_clients[].invoiced_sales` | `mart_sales_by_customer.ca_facture` |
| `top_clients[].average_invoice_value` | `mart_sales_by_customer.average_invoice_value` |
| `by_commercial[].salesrep_id` | `mart_sales_by_customer.commercial_key` |
| `by_commercial[].commercial_label` | `mart_sales_by_customer.commercial_name` |
| `by_commercial[].customer_count` | distinct `mart_sales_by_customer.customer_key` |
| `by_commercial[].invoiced_sales` | `mart_sales_by_customer.ca_facture` |
| `by_article[].customer_id` | `mart_sales_by_product.c_bpartner_id` |
| `by_article[].customer_name` | `mart_sales_by_product.customer_name` |
| `by_article[].product_name` | `mart_sales_by_product.product_name` |
| `by_article[].quantity` | `mart_sales_by_product.quantity_invoiced` |
| `by_article[].invoiced_sales` | `mart_sales_by_product.ca_facture` |
| `by_region[].region_name` | `mart_sales_by_region.region_name` |
| `by_region[].city_name` | `mart_sales_by_region.city_name` |
| `by_region[].customer_count` | `mart_sales_by_region.customer_count` |
| `by_region[].invoice_count` | `mart_sales_by_region.invoice_count` |
| `by_region[].invoiced_sales` | `mart_sales_by_region.ca_facture` |
| `finance_risks[].customer_id` | `mart_payment_status.c_bpartner_id` |
| `finance_risks[].customer_name` | `mart_payment_status.customer_name` |
| `finance_risks[].unpaid_invoice_count` | `mart_payment_status.unpaid_invoice_count` |
| `finance_risks[].unpaid_invoice_amount` | `mart_payment_status.unpaid_invoice_amount` |

### 5.6 `GET /v1/bi/commercial`

Primary consumer: `BiCommercialPage`.

Supported filters: `from`, `to`, `granularity`, `compare`, `commercial`,
`customer`, `region`, `city`, `document_type`, `limit`, `offset`.

```yaml
data:
  kpis: CommercialKpis
  revenue_by_commercial: CommercialSalesRank[]
  conversion: CommercialConversion[]
  terrain: CommercialTerrainSignals
```

| DTO field | Mart lineage |
|---|---|
| `kpis.commercial_count` | distinct `mart_sales_by_commercial.commercial_key` |
| `kpis.top_commercial_name` | `mart_sales_by_commercial.commercial_name` ordered by `ca_facture` |
| `kpis.conversion_percent` | `mart_sales_by_commercial.invoice_coverage_percent` |
| `kpis.region_coverage_count` | distinct `mart_sales_by_region.region_name` / `sales_region_name` |
| `revenue_by_commercial[].salesrep_id` | `mart_sales_by_commercial.ad_user_id` |
| `revenue_by_commercial[].commercial_label` | `mart_sales_by_commercial.commercial_name` |
| `revenue_by_commercial[].salesrep_email` | `mart_sales_by_commercial.salesrep_email` |
| `revenue_by_commercial[].order_count` | `mart_sales_by_commercial.nombre_commandes` |
| `revenue_by_commercial[].ordered_sales` | `mart_sales_by_commercial.ca_commande` |
| `revenue_by_commercial[].invoice_count` | `mart_sales_by_commercial.invoice_count` |
| `revenue_by_commercial[].invoiced_sales` | `mart_sales_by_commercial.ca_facture` |
| `revenue_by_commercial[].invoice_coverage_percent` | `mart_sales_by_commercial.invoice_coverage_percent` |
| `conversion[].ordered_sales` | `mart_sales_by_commercial.ca_commande` |
| `conversion[].invoiced_sales` | `mart_sales_by_commercial.ca_facture` |
| `conversion[].gap` | `mart_sales_by_commercial.invoice_gap_amount` |
| `terrain.zone_forte.city_name` | `mart_sales_by_region.city_name` |
| `terrain.zone_forte.region_name` | `mart_sales_by_region.region_name` |
| `terrain.zone_forte.invoiced_sales` | `mart_sales_by_region.ca_facture` |
| `terrain.client_cle.customer_name` | `mart_sales_by_customer.customer_name` |
| `terrain.client_cle.invoiced_sales` | `mart_sales_by_customer.ca_facture` |
| `terrain.drill_keys.commercial_key` | `mart_sales_by_commercial.commercial_key` |
| `terrain.drill_keys.customer_key` | `mart_sales_by_customer.customer_key` |
| `terrain.drill_keys.geography_key` | `mart_sales_by_region.geography_key` |

### 5.7 `GET /v1/bi/analysis`

Primary consumer: live `SalesAnalysisPage`.

This endpoint is the marts-only successor to `/v1/sales-analysis`. It is a
consistent superset of the existing response shape and can be implemented either
as a single aggregate endpoint or as a backend composition over the page endpoints
above.

Supported filters: all common filters.

```yaml
data:
  kpis: SalesAnalysisKpis
  trend: SalesAnalysisTrendPoint[]
  order_type_sales: OrderTypeRank[]
  commercial_sales: CommercialSalesRank[]
  category_sales: CategorySalesRank[]
  theme_sales: ThemeSalesRank[]
  supplier_sales: SupplierSalesRank[]
  distributor_sales: DistributorSalesRank[]
  geography_sales: GeographySalesRank[]
  availability_risks: AvailabilityRiskRank[]
  top_products: AnalysisProductRank[]
  top_customers: AnalysisCustomerRank[]
  commercial_options: FilterOption[]
  order_type_options: FilterOption[]
  category_options: FilterOption[]
  theme_options: FilterOption[]
  supplier_options: FilterOption[]
  distributor_options: FilterOption[]
```

| DTO field group | Mart lineage |
|---|---|
| `kpis.order_count` | `mart_sales_daily.nombre_commandes` |
| `kpis.ordered_sales` | `mart_sales_daily.ca_commande` |
| `kpis.invoice_count` | `mart_sales_daily.invoice_count` |
| `kpis.invoiced_sales` | `mart_sales_daily.ca_facture` |
| `kpis.active_customers` | `mart_sales_daily.active_customers_invoiced` |
| `kpis.average_order_value` | `mart_sales_daily.average_order_value` |
| `kpis.invoice_coverage_percent` | `mart_sales_daily.invoice_coverage_percent` |
| `trend[]` | `mart_sales_daily.metric_date`, `nombre_commandes`, `ca_commande`, `invoice_count`, `ca_facture` |
| `order_type_sales[]` | `mart_order_to_invoice_flow.c_doctype_id`, `document_type_name`, `nombre_commandes`, `ca_commande` |
| `commercial_sales[]` | `mart_sales_by_commercial.ad_user_id`, `commercial_name`, `salesrep_email`, `nombre_commandes`, `ca_commande`, `invoice_count`, `ca_facture` |
| `category_sales[]` | `mart_sales_by_product.product_category_key`, `category_name`, `invoice_line_count`, `ca_facture` |
| `theme_sales[]` | `mart_sales_by_product.theme_name`, `nombre_produits_vendus`, `invoice_count`, `invoice_line_count`, `quantity_invoiced`, `ca_facture`; ordered-only fields use `mart_order_to_invoice_flow` when needed |
| `supplier_sales[]` | `mart_sales_by_product.supplier_id`, `supplier_name`, `nombre_produits_vendus`, `invoice_line_count`, `ca_facture` |
| `distributor_sales[]` | compatibility alias over `mart_sales_by_product.supplier_id`, `supplier_name`, `nombre_produits_vendus`, `invoice_count`, `invoice_line_count`, `quantity_invoiced`, `ca_facture` |
| `geography_sales[]` | `mart_sales_by_region.region_name`, `city_name`, `customer_count`, `invoice_count`, `ca_facture` |
| `availability_risks[]` | `mart_stock_risk.m_product_id`, `product_name`, `category_name`, `theme_name`, `supplier_name`, `qty_on_hand`, `qty_reserved`, `qty_available`, `qty_ordered` |
| `top_products[]` | `mart_sales_by_product.product_name`, `category_name`, `supplier_id`, `supplier_name`, `invoice_line_count`, `quantity_invoiced`, `ca_facture` |
| `top_customers[]` | `mart_sales_by_customer.customer_name`, `invoice_count`, `ca_facture`, `average_invoice_value` |
| `commercial_options[]` | `mart_sales_by_commercial.ad_user_id`, `commercial_name`, `nombre_commandes`, `ca_facture` |
| `order_type_options[]` | `mart_order_to_invoice_flow.c_doctype_id`, `document_type_name`, `nombre_commandes`, `ca_commande` |
| `category_options[]` | `mart_sales_by_product.product_category_key`, `category_name`, `invoice_line_count`, `ca_facture` |
| `theme_options[]` | `mart_sales_by_product.theme_name`, `nombre_produits_vendus`, `ca_facture` |
| `supplier_options[]` | `mart_sales_by_product.supplier_id`, `supplier_name`, `nombre_produits_vendus`, `ca_facture` |
| `distributor_options[]` | compatibility alias over `mart_sales_by_product.supplier_id`, `supplier_name`, `nombre_produits_vendus`, `ca_facture` |

Compatibility note: the live frontend currently uses `distributor_*` names. BI-08
has `supplier_*` mart columns, not a separate distributor mart. Until BI-12
renames the frontend or BI-08 adds a distributor-specific mart, `distributor_*`
is a response alias over the supplier columns listed above.

---

## 6. Pagination and Limits

| Collection | Default | Max | Sort |
|---|---:|---:|---|
| Trend lists | 12 periods | day: 366, week: 104, month: 60 | ascending period |
| Ranking lists | 10 | 100 | descending value column |
| Filter options | 100 | 500 | descending `total_value`, then label |
| Stock risks | 20 | 100 | `is_stockout_risk` desc, `qty_available` asc, `ca_facture` desc when joined |

For paginated lists, return protocol metadata:

```yaml
meta.pagination:
  limit: integer
  offset: integer
  returned: integer
  has_more: boolean
```

---

## 7. Performance Notes

### 7.1 Index candidates

BI-08 uses plain views, so indexes belong on underlying `warehouse.*` tables now.
If BI-14 shows p95 latency above 300 ms and BI-08 marts are promoted to
materialized views, use the same columns as materialized-view indexes.

| Query pattern | Required columns |
|---|---|
| Date filtering | `date_key`, `metric_date`, `period_month`, `snapshot_date` |
| Commercial filters | `commercial_key`, `ad_user_id` |
| Customer filters | `customer_key`, `c_bpartner_id` |
| Product/category filters | `product_key`, `m_product_id`, `product_category_key`, `m_product_category_id`, `theme_name`, `collection_name` |
| Supplier/distributor filters | `supplier_key`, `supplier_id` |
| Order type filters | `document_type_key`, `c_doctype_id`, `doc_status` |
| Geography filters | `geography_key`, `city_name`, `region_name`, `sales_region_name` |
| Payment filters | `is_paid`, `payment_status` |
| Stock filters | `snapshot_date`, `product_key`, `warehouse_key`, `is_stockout_risk`, `stock_risk_level` |

Materialized-view first candidates, as flagged in BI-08: `mart_sales_by_product`,
`mart_sales_by_customer`, and `mart_sales_by_commercial`.

### 7.2 Caching

- Cache key: endpoint path + normalized query params + latest ETL run id.
- Default TTL: 60 seconds for page endpoints, 5 minutes for filter options.
- Invalidate immediately after a successful BI-09 ETL run by changing the latest
  ETL run id component.
- Return `ETag` and support `If-None-Match` for dashboard refresh buttons.

---

## 8. Coverage Check

### 8.1 Five detail BI pages

| Page | Endpoint | Coverage |
|---|---|---|
| `BiCommandesPage` | `/v1/bi/orders` | All visible widgets map to `mart_order_to_invoice_flow` or `mart_sales_by_commercial`. |
| `BiRevenuePage` | `/v1/bi/revenue` | All visible widgets map to `mart_sales_daily`, `mart_sales_monthly`, `mart_order_to_invoice_flow`, or `mart_payment_status`. |
| `BiArticlesPage` | `/v1/bi/articles` | All visible widgets map to `mart_sales_by_product`, `mart_stock_risk`, or `mart_sales_monthly`. |
| `BiClientsPage` | `/v1/bi/clients` | All visible widgets map to `mart_sales_by_customer`, `mart_sales_by_product`, `mart_sales_by_region`, or `mart_payment_status`. |
| `BiCommercialPage` | `/v1/bi/commercial` | All visible widgets map to `mart_sales_by_commercial`, `mart_sales_by_region`, or `mart_sales_by_customer`. |

### 8.2 Real overview and live pages

| Page | Endpoint | Coverage |
|---|---|---|
| `BiOverviewPage` | `/v1/bi/overview` | All visible widgets map to BI-08 mart columns. |
| `DashboardOverviewPage` | `/v1/bi/overview` | Existing `SalesDashboardSnapshot` fields are preserved as mart-derived DTO fields. |
| `SalesAnalysisPage` | `/v1/bi/analysis` | Existing `SalesAnalysisSnapshot` field groups are preserved as mart-derived DTO groups. |

### 8.3 Gaps

No visible widget lacks a backing mart column.

One naming gap remains intentional: live `SalesAnalysisPage` uses
`distributor_*` labels while BI-08 exposes `supplier_*`. The contract maps
`distributor_*` to supplier mart columns as a compatibility alias. BI-12 should
either rename the frontend labels to supplier/fournisseur or add a separate
distributor mart if the business distinguishes those concepts.

---

## 9. Non-Goals

- No Java implementation.
- No TypeScript implementation.
- No DTO classes.
- No controller or service modifications.
- No DDL execution.
- No PostgreSQL access.
- No raw-file or legacy schema logic in the future contract.


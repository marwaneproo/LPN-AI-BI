# BI-12 - Frontend integration plan

Status: PLAN ONLY
Date: 2026-06-25
Scope: plan page-by-page replacement of placeholder BI page data with the BI-10
`/v1/bi/*` API contract. Do not edit React components, API clients, export code,
or run the frontend in this task.

## 1. Inputs inspected

| Area | Real files inspected | Findings used |
| --- | --- | --- |
| Five placeholder pages | `frontend/src/features/bi/pages/BiCommandesPage.tsx`, `BiRevenuePage.tsx`, `BiArticlesPage.tsx`, `BiClientsPage.tsx`, `BiCommercialPage.tsx` | Each page uses `BiShell`, four hard-coded `BiMetricCard` widgets, and three placeholder `BiWidgetCard` blocks using `BiColumnPreview`, `BiBarPreview`, or `BiSignalList`. |
| Overview placeholder | `frontend/src/features/bi/pages/BiOverviewPage.tsx` | Same placeholder pattern as the five detail pages. BI-10 covers it with `/v1/bi/overview`. |
| Live dashboard pages | `DashboardOverviewPage.tsx`, `SalesAnalysisPage.tsx` | These already fetch data through hooks, preserve loading/error states, and use real Recharts/table components. They still call legacy `/v1/sales-dashboard` and `/v1/sales-analysis`. |
| Placeholder widgets | `frontend/src/features/bi/components/widgets/BiWidgets.tsx` | `BiMetricCard`, `BiWidgetCard`, `BiBarPreview`, `BiColumnPreview`, and `BiSignalList` are presentational placeholders with string/number props. |
| Existing chart/table primitives | `CaComparisonChart.tsx`, `SalesTrendChart.tsx`, `SalesAnalysisTrendChart.tsx`, `RankedBarChart.tsx`, `StatusBars.tsx`, `ContributionPieChart.tsx`, `CommercialRevenueComparisonChart.tsx`, `DataTable` usages | These already provide empty-state rendering through `EmptyChartState` and should be reused for live data instead of creating a new chart system. |
| Current API client and types | `frontend/src/features/bi/api/biApi.ts`, `types/bi.types.ts`, `hooks/useSalesDashboard.ts`, `hooks/useSalesAnalysis.ts` | Current client exposes only `fetchSalesDashboard()` and `fetchSalesAnalysis()`, both legacy endpoints. Types are snake_case and close to BI-10 for overview and analysis. |
| Filters and sections | `SalesAnalysisFilterCard.tsx`, section components under `components/sections/` | The live analysis page already supports date, commercial, order type, category, theme, supplier, distributor filters and click-to-filter interactions. |
| Export | `BiShell.tsx`, `BiExportDialog.tsx`, `biExport.ts` | PNG/PDF export is frontend visual capture. Excel currently exports context metadata client-side. BI-12 must not change it; BI-13 should move data export to backend later. |
| API contract | `docs/bi-service-roadmap/_design/10_bi_api_contract.md` | Defines `/v1/bi/overview`, `/orders`, `/revenue`, `/articles`, `/clients`, `/commercial`, `/analysis`; every visible widget has a backing DTO field. |

Skills applied: `frontend-design`, `vercel-react-best-practices`, and
`web-design-guidelines`. The plan keeps the existing BI visual language, avoids
new UI churn, preserves accessible states, keeps filter state URL-ready, and
avoids future data-fetch waterfalls by using one page endpoint per page.

## 2. Non-goals

- Do not rewrite or create React components in BI-12.
- Do not change `frontend/src/features/bi/api/biApi.ts` in BI-12.
- Do not change `frontend/src/features/bi/utils/biExport.ts`,
  `BiExportDialog.tsx`, or export behavior in BI-12.
- Do not run `npm`, Vite, Playwright, or the frontend dev server in BI-12.
- Do not remove legacy `/v1/sales-dashboard` or `/v1/sales-analysis` in BI-12.

## 3. Integration rules for the later implementation

1. Add new `/v1/bi/*` API client functions alongside the legacy functions, not
   as replacements on day one.
2. Keep snake_case DTO fields at the network boundary to match current
   `bi.types.ts` and BI-10.
3. Use one endpoint request per page to avoid waterfalls. If a page later needs
   filter options separately, start the options request in parallel.
4. Preserve existing loading/error/empty patterns:
   - initial skeleton grid when loading and no snapshot exists;
   - retryable error card with the backend message;
   - `EmptyChartState` inside charts when arrays are empty;
   - disabled refresh/apply buttons only while the request is in flight.
5. Keep presentational chart components pure. Transform DTO rows into existing
   `RankedBarDatum`, `CommercialRevenueDatum`, table rows, and chart points in
   hooks or transformer utilities.
6. Preserve URL-ready filter names from BI-10: `from`, `to`, `granularity`,
   `compare`, `commercial`, `category`, `supplier`, `customer`,
   `document_type`, `region`, `city`, `payment_status`, `limit`, `offset`.
7. Do not introduce new formulas in the frontend. Display values already
   provided by BI-10 DTO fields; simple display ratios are acceptable only when
   BI-10 defines the source fields.
8. Keep current export UI unchanged. PNG/PDF remain frontend capture; data/Excel
   export moves to backend later in BI-13.

## 4. Page mappings

### 4.1 `BiCommandesPage` -> `GET /v1/bi/orders`

Current widgets:

| Widget | Current component | Future component target | Endpoint |
| --- | --- | --- | --- |
| Metric: Commandes | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/orders` |
| Metric: Type dominant | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/orders` |
| Metric: Commandes facturees | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/orders` |
| Metric: Statuts | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/orders` |
| Commandes par type | `BiColumnPreview` | `RankedBarChart` | `/v1/bi/orders` |
| Lecture commerciale | `BiBarPreview` | `CommercialRevenueComparisonChart` or `RankedBarChart` | `/v1/bi/orders` |
| Points de controle | `BiSignalList` | `StatusBars` plus compact signal rows | `/v1/bi/orders` |

DTO-field to prop mapping:

| DTO field | Component prop |
| --- | --- |
| `data.kpis.order_count` | Metric `Commandes.value` via `formatNumber()` |
| `data.kpis.dominant_type_name` | Metric `Type dominant.value` |
| `data.kpis.invoice_coverage_percent` | Metric `Commandes facturees.value` as percent |
| `data.kpis.status_count` | Metric `Statuts.value` |
| `data.by_type[].order_type_name` | `RankedBarDatum.name/fullName` |
| `data.by_type[].order_count` | `RankedBarDatum.value` for count view |
| `data.by_type[].ordered_sales` | `RankedBarDatum.meta` or alternate amount chart |
| `data.commercial_flow[].commercial_label` | `CommercialRevenueDatum.label/fullName` |
| `data.commercial_flow[].ordered_sales` | `CommercialRevenueDatum.orderedSales` |
| `data.commercial_flow[].invoice_coverage_percent` | `CommercialRevenueDatum.coverage` or meta text |
| `data.status_breakdown[].status` | `StatusBreakdown.status` |
| `data.status_breakdown[].label` | `StatusBreakdown.label` |
| `data.status_breakdown[].item_count` | `StatusBreakdown.item_count` |
| `data.status_breakdown[].total_value` | `StatusBreakdown.total_value` |

State handling to preserve:

- Initial load: use the same skeleton grid pattern as `DashboardOverviewPage`.
- Empty `by_type`: show `EmptyChartState` through `RankedBarChart`.
- Empty `status_breakdown`: show `EmptyChartState` through `StatusBars`.
- Error: show a retryable card with `BI_INVALID_FILTER`, `BI_NO_DATA`, or
  backend message.

Placeholder-removal checklist:

- Replace hard-coded metric strings with `data.kpis`.
- Replace `BiColumnPreview` with a real ranking chart.
- Replace `BiBarPreview` values with commercial/order-flow DTO rows.
- Replace `BiSignalList` with status data and remove static "oui" signals.
- Confirm no text implies "prepare" or "prevue" once data is live.

Gap status: no gap. All current widgets map to BI-10 `/v1/bi/orders`.

### 4.2 `BiRevenuePage` -> `GET /v1/bi/revenue`

Current widgets:

| Widget | Current component | Future component target | Endpoint |
| --- | --- | --- | --- |
| Metric: CA reel | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/revenue` |
| Metric: CA commande | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/revenue` |
| Metric: Ecart | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/revenue` |
| Metric: Panier moyen | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/revenue` |
| CA sur periode | `BiColumnPreview` | `SalesAnalysisTrendChart` or `CaComparisonChart` | `/v1/bi/revenue` |
| Lecture responsable | `BiSignalList` | signal rows from `responsible_reading` | `/v1/bi/revenue` |
| Statuts CA | `BiBarPreview` | `StatusBars` or small `RankedBarChart` | `/v1/bi/revenue` |

DTO-field to prop mapping:

| DTO field | Component prop |
| --- | --- |
| `data.kpis.invoiced_sales` | Metric `CA reel.value`; chart `invoicedSales` |
| `data.kpis.ordered_sales` | Metric `CA commande.value`; chart `orderedSales` |
| `data.kpis.invoice_gap_amount` | Metric `Ecart.value`; status `ecart` |
| `data.kpis.average_order_value` | Metric `Panier moyen.value` |
| `data.trend[].period` | trend point `label/period` |
| `data.trend[].ordered_sales` | `SalesAnalysisTrendChart.data[].orderedSales` |
| `data.trend[].invoiced_sales` | `SalesAnalysisTrendChart.data[].invoicedSales` |
| `data.trend[].order_count` | trend tooltip/meta `orderCount` |
| `data.trend[].invoice_count` | trend tooltip/meta `invoiceCount` |
| `data.responsible_reading.best_month.period` | signal `Meilleur mois.value` |
| `data.responsible_reading.best_month.invoiced_sales` | signal helper/meta |
| `data.responsible_reading.invoice_coverage_percent` | signal `Couverture.value` |
| `data.responsible_reading.unpaid_invoice_count` | signal `Risque.value` |
| `data.responsible_reading.unpaid_invoice_amount` | signal helper/meta |
| `data.revenue_status.facture` | status row `Facture.value` |
| `data.revenue_status.a_livrer` | status row `A livrer.value` |
| `data.revenue_status.ecart` | status row `Ecart.value` |

State handling to preserve:

- Keep chart empty states for no trend rows.
- Keep money/percent formatting in formatter utilities.
- Surface `BI_NO_DATA` as an empty-period state, not a broken chart.
- Keep refresh disabled while loading only.

Placeholder-removal checklist:

- Remove static "16,8 M MAD", "16,7 M MAD", "+0,1 M", "5 k MAD".
- Replace week labels `S1` to `S4` with `data.trend`.
- Replace static "Avril", "100,3%", "Impayes" with `responsible_reading`.
- Convert `revenue_status` object to chart rows with stable labels.

Gap status: no gap. All current widgets map to BI-10 `/v1/bi/revenue`.

### 4.3 `BiArticlesPage` -> `GET /v1/bi/articles`

Current widgets:

| Widget | Current component | Future component target | Endpoint |
| --- | --- | --- | --- |
| Metric: Produits actifs | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/articles` |
| Metric: Top categorie | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/articles` |
| Metric: Top theme | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/articles` |
| Metric: Disponibilite | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/articles` |
| Top articles | `BiColumnPreview` | `RankedBarChart` or `ContributionPieChart` | `/v1/bi/articles` |
| Mix article | `BiBarPreview` | `RankedBarChart` groups or compact tabs later | `/v1/bi/articles` |
| Priorites stock | `BiSignalList` | `RankedBarChart` plus optional `DataTable` | `/v1/bi/articles` |

DTO-field to prop mapping:

| DTO field | Component prop |
| --- | --- |
| `data.kpis.active_products` | Metric `Produits actifs.value` |
| `data.kpis.top_category_name` | Metric `Top categorie.value` |
| `data.kpis.top_theme_name` | Metric `Top theme.value` |
| `data.kpis.stock_risk_count` | Metric `Disponibilite.value` |
| `data.top_articles[].product_id` | `RankedBarDatum.id` |
| `data.top_articles[].product_name` | `RankedBarDatum.name/fullName` |
| `data.top_articles[].invoiced_sales` | `RankedBarDatum.value` |
| `data.top_articles[].quantity` | `RankedBarDatum.meta` |
| `data.top_articles[].category_name` | product meta/table column |
| `data.top_articles[].supplier_name` | product meta/table column |
| `data.mix_article.categories[].name/value` | category mix chart rows |
| `data.mix_article.themes[].name/value` | theme mix chart rows |
| `data.mix_article.collections[].name/value` | collection mix chart rows |
| `data.stock_priorities[].product_name` | stock risk row label |
| `data.stock_priorities[].qty_available` | stock risk meta/KPI |
| `data.stock_priorities[].qty_reserved` | stock risk meta/table column |
| `data.stock_priorities[].stock_risk_level` | stock signal severity |

State handling to preserve:

- Empty `top_articles`: `EmptyChartState` with "Aucun produit".
- Empty `stock_priorities`: positive empty state, not an error.
- Long product names: keep `shortenLabel` in chart mappings and full name in
  tooltip/table.
- Avoid rendering huge lists; respect BI-10 `limit` and max 100.

Placeholder-removal checklist:

- Remove static P1/P2/P3 labels.
- Replace "Jeunesse", "Scolaire", "A suivre" with live KPI fields.
- Convert mix groups from percent placeholders to money/ranking rows.
- Decide in implementation whether "distributeur" copy should become
  "fournisseur" unless a true distributor mart exists.

Gap status: no data gap. Naming gap: BI-10 says distributor labels are a
compatibility alias over supplier fields until the business model separates
them.

### 4.4 `BiClientsPage` -> `GET /v1/bi/clients`

Current widgets:

| Widget | Current component | Future component target | Endpoint |
| --- | --- | --- | --- |
| Metric: Clients actifs | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/clients` |
| Metric: Top client | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/clients` |
| Metric: Portefeuille | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/clients` |
| Metric: Geographie | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/clients` |
| Top clients | `BiColumnPreview` | `RankedBarChart` or `ContributionPieChart` | `/v1/bi/clients` |
| Client par angle | `BiBarPreview` | three ranking modules | `/v1/bi/clients` |
| Lecture utile | `BiSignalList` | finance risk and top contribution signals | `/v1/bi/clients` |

DTO-field to prop mapping:

| DTO field | Component prop |
| --- | --- |
| `data.kpis.active_customers` | Metric `Clients actifs.value` |
| `data.kpis.top_customer_name` | Metric `Top client.value` |
| `data.kpis.portfolio_commercial_count` | Metric `Portefeuille.value` |
| `data.kpis.geography_count` | Metric `Geographie.value` |
| `data.top_clients[].customer_id` | `RankedBarDatum.id` |
| `data.top_clients[].customer_name` | `RankedBarDatum.name/fullName` |
| `data.top_clients[].invoiced_sales` | `RankedBarDatum.value` |
| `data.top_clients[].invoice_count` | `RankedBarDatum.meta` |
| `data.top_clients[].average_invoice_value` | tooltip/meta |
| `data.by_commercial[].commercial_label` | commercial ranking label |
| `data.by_commercial[].customer_count` | commercial ranking meta |
| `data.by_commercial[].invoiced_sales` | commercial ranking value |
| `data.by_article[].product_name` | article/customer row label |
| `data.by_article[].quantity` | article/customer meta |
| `data.by_article[].invoiced_sales` | article/customer value |
| `data.by_region[].region_name/city_name` | geography labels |
| `data.by_region[].invoiced_sales` | geography value |
| `data.finance_risks[].customer_name` | risk signal label |
| `data.finance_risks[].unpaid_invoice_amount` | risk signal value |

State handling to preserve:

- Empty clients can be a valid filtered result. Use `BI_NO_DATA` copy that says
  no clients match the filters.
- Finance risk empty state should be positive: no unpaid invoices in this slice.
- Keep tables/charts stable with `limit` and `offset`.

Placeholder-removal checklist:

- Remove static "295", "L'AVENIR", and generic "Top CA" strings.
- Replace `Client 1` to `Client 5` with real top client rows.
- Split "Client par angle" into existing chart primitives or keep one compact
  card that cycles among commercial/article/region in a later UI task.
- Replace "Risque impayes" with `finance_risks`.

Gap status: no gap. All current widgets map to BI-10 `/v1/bi/clients`.

### 4.5 `BiCommercialPage` -> `GET /v1/bi/commercial`

Current widgets:

| Widget | Current component | Future component target | Endpoint |
| --- | --- | --- | --- |
| Metric: Commerciaux | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/commercial` |
| Metric: Top CA | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/commercial` |
| Metric: Conversion | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/commercial` |
| Metric: Couverture region | `BiMetricCard` | `BiMetricCard` or `BiKpiCard` | `/v1/bi/commercial` |
| CA par commercial | `BiColumnPreview` | `RankedBarChart` | `/v1/bi/commercial` |
| Conversion vente | `BiBarPreview` | `CommercialRevenueComparisonChart` | `/v1/bi/commercial` |
| Lecture terrain | `BiSignalList` | compact terrain signals | `/v1/bi/commercial` |

DTO-field to prop mapping:

| DTO field | Component prop |
| --- | --- |
| `data.kpis.commercial_count` | Metric `Commerciaux.value` |
| `data.kpis.top_commercial_name` | Metric `Top CA.value` |
| `data.kpis.conversion_percent` | Metric `Conversion.value` |
| `data.kpis.region_coverage_count` | Metric `Couverture region.value` |
| `data.revenue_by_commercial[].salesrep_id` | `RankedBarDatum.id` |
| `data.revenue_by_commercial[].commercial_label` | `RankedBarDatum.name/fullName` |
| `data.revenue_by_commercial[].invoiced_sales` | `RankedBarDatum.value` |
| `data.revenue_by_commercial[].invoice_count` | `RankedBarDatum.meta` |
| `data.conversion[].ordered_sales` | `CommercialRevenueDatum.orderedSales` |
| `data.conversion[].invoiced_sales` | `CommercialRevenueDatum.invoicedSales` |
| `data.conversion[].gap` | `CommercialRevenueDatum.gap` |
| `data.terrain.zone_forte.city_name` | signal `Zone forte.value` |
| `data.terrain.zone_forte.region_name` | signal helper |
| `data.terrain.client_cle.customer_name` | signal `Client cle.value` |
| `data.terrain.client_cle.invoiced_sales` | signal helper |
| `data.terrain.drill_keys.*` | future drill link params |

State handling to preserve:

- Empty commercial rows should render `EmptyChartState`.
- The future drill action must be a real link or button with keyboard support,
  not a clickable `div`.
- Keep selected commercial filters URL-ready for BI-12 implementation.

Placeholder-removal checklist:

- Remove static commercial names and counts.
- Replace "a comparer" with BI-10 conversion percent.
- Replace "Casa", "Top 10", "forage" with `terrain`.
- Keep full commercial names in tooltip, shortened labels only in axes.

Gap status: no gap. All current widgets map to BI-10 `/v1/bi/commercial`.

## 5. Overview and live-page coexistence

### 5.1 `BiOverviewPage` -> `GET /v1/bi/overview`

`BiOverviewPage` is not one of the five detail pages, but BI-10 covers it and it
uses the same placeholder widget stack. Migrate it after the lowest-risk detail
pages and before the live dashboard route.

| Widget | DTO fields |
| --- | --- |
| CA facture | `data.kpis.invoiced_sales` |
| Ventes commandees | `data.kpis.order_value` |
| Clients actifs | `data.kpis.active_customers` |
| Couverture facture | `data.kpis.invoice_coverage_percent` |
| Activite mensuelle | `data.trend[].period`, `order_value`, `invoiced_sales` |
| Signaux rapides | `data.quick_signals.*` |
| Mix ventes | `data.sales_mix.facture`, `commande`, `articles_suivis` |

Gap status: no gap.

### 5.2 `DashboardOverviewPage` legacy coexistence

Current path:

- `DashboardOverviewPage` calls `useSalesDashboard()`.
- `useSalesDashboard()` calls `fetchSalesDashboard()`.
- `fetchSalesDashboard()` calls `/v1/sales-dashboard`.

Coexistence plan:

1. Keep `fetchSalesDashboard()` and `useSalesDashboard()` unchanged while detail
   pages migrate.
2. Add a new `fetchBiOverview()` and hook later, returning the BI-10 envelope
   but transforming `data` into the existing dashboard-friendly shape.
3. Switch `DashboardOverviewPage` only after `/v1/bi/overview` parity is checked
   against the legacy snapshot for KPIs, trend, customers, products, order
   statuses, and invoice statuses.
4. Keep the old route callable until the frontend no longer imports
   `fetchSalesDashboard`.

### 5.3 `SalesAnalysisPage` legacy coexistence

Current path:

- `SalesAnalysisPage` calls `useSalesAnalysis()`.
- `useSalesAnalysis()` calls `fetchSalesAnalysis(filters)`.
- `fetchSalesAnalysis()` calls `/v1/sales-analysis`.

Coexistence plan:

1. Keep the current page stable until the five detail pages and overview page
   have migrated.
2. Add `fetchBiAnalysis()` later with BI-10 query params. Preserve the existing
   filter UI and map legacy filter names:
   `salesrepId -> commercial`, `categoryId -> category`,
   `supplierId -> supplier`, `orderTypeId -> document_type`.
3. Keep `distributorId` as a compatibility UI field only while BI-10 aliases
   distributor to supplier. BI-12 implementation must choose either "rename to
   fournisseur" in UI copy or keep the alias explicitly documented.
4. Preserve all existing sections and empty chart behavior.

## 6. Loading, empty, and error state plan

| State | Required behavior |
| --- | --- |
| Initial loading | Keep `dashboard-loading-grid` skeleton cards before first response. |
| Refresh loading with previous data | Keep previous data visible and disable only the action that triggered refresh. |
| Empty list | Let `RankedBarChart`, `ContributionPieChart`, `StatusBars`, and trend charts render `EmptyChartState`. |
| Empty page | For `BI_NO_DATA`, show a page-level empty card plus empty chart states; preserve filters so the user can adjust. |
| Invalid filters | Map `BI_INVALID_FILTER` to a clear error card and keep the last valid data if available. |
| Unsupported filter | Map `BI_UNSUPPORTED_FILTER_COMBINATION` to an error card that names the filter to remove. |
| Mart unavailable | Map `BI_MART_UNAVAILABLE` to a retryable technical error; do not silently fall back to placeholder values. |
| Network/backend error | Preserve retry buttons and backend message display from the live pages. |

Accessibility and UX constraints from the requested skills:

- Keep loading copy consistent with the existing UI in BI-12; a later UI copy
  pass can standardize visible loading text and ellipsis usage.
- Keep `aria-live="polite"` for date/filter readouts and status-like async
  updates.
- Buttons remain buttons; drill actions that navigate should use `Link`.
- Keep chart labels short but preserve full names in tooltips.
- Keep arrays capped by BI-10 limits. For lists above 50 rows, use tables or
  virtualization in a later implementation task.

## 7. Rollout order

The rollout order below is intentionally lowest-risk first.

Lowest-risk first:

1. `BiRevenuePage` with `/v1/bi/revenue`. It has simple KPI, trend, signal, and
   status mappings, no click-to-filter complexity.
2. `BiCommandesPage` with `/v1/bi/orders`. It validates document type and status
   mapping before more complex pages depend on those filters.
3. `BiCommercialPage` with `/v1/bi/commercial`. It reuses commercial ranking and
   conversion patterns already present in `SalesAnalysisPage`.
4. `BiArticlesPage` with `/v1/bi/articles`. More fields, stock-risk semantics,
   and supplier/distributor copy need careful review.
5. `BiClientsPage` with `/v1/bi/clients`. Highest detail-page complexity because
   it combines customer, commercial, article, geography, and payment-risk data.
6. `BiOverviewPage` with `/v1/bi/overview`. Use it as the executive summary once
   detail endpoint shapes are proven.
7. `DashboardOverviewPage` from `/v1/sales-dashboard` to `/v1/bi/overview`.
8. `SalesAnalysisPage` from `/v1/sales-analysis` to `/v1/bi/analysis`.

The UI never needs to break mid-migration because legacy live pages can continue
using `/v1/sales-dashboard` and `/v1/sales-analysis` while placeholder detail
pages move one by one to `/v1/bi/*`.

## 8. Placeholder-removal safety gates

For each page:

1. Add the new API call and type definitions for that endpoint.
2. Add a page hook that returns `{ snapshot, isLoading, error, refresh }` or the
   existing analysis-style shape.
3. Keep the old placeholder markup until the hook and DTO mapping compile.
4. Replace one widget group at a time: KPIs first, then main chart, then signal
   or table blocks.
5. Confirm every previous placeholder label either maps to a DTO field or is
   removed because it was explanatory placeholder copy.
6. Confirm no static business values remain in the page file.
7. Confirm loading, empty, error, and refresh states render before removing the
   placeholder preview component imports.
8. Confirm export still captures the visible page, with no export utility edits.

## 9. Export behavior

Do not change export behavior during BI-12 implementation.

- PNG/PDF export remains frontend visual capture through `BiShell` and
  `exportBiView()`.
- Current Excel export remains frontend metadata-only until BI-13.
- BI-13 should define backend Excel/data export using the same `/v1/bi/*`
  filters and mart-backed datasets.
- The migration must not touch `BiExportDialog.tsx`, `biExport.ts`, or export
  button placement.

## 10. Coverage check

| Page | Placeholder widgets | Endpoint | Coverage |
| --- | ---:| --- | --- |
| `BiCommandesPage` | 7 | `/v1/bi/orders` | Covered |
| `BiRevenuePage` | 7 | `/v1/bi/revenue` | Covered |
| `BiArticlesPage` | 7 | `/v1/bi/articles` | Covered |
| `BiClientsPage` | 7 | `/v1/bi/clients` | Covered |
| `BiCommercialPage` | 7 | `/v1/bi/commercial` | Covered |
| `BiOverviewPage` | 7 | `/v1/bi/overview` | Covered |
| `DashboardOverviewPage` | live legacy | `/v1/bi/overview` later | Covered after parity |
| `SalesAnalysisPage` | live legacy | `/v1/bi/analysis` later | Covered after parity |

No placeholder widget lacks a BI-10 endpoint and DTO field.

Open naming decision for implementation: keep `distributor_*` as BI-10's
compatibility alias over supplier fields, or rename UI labels to
`fournisseur` until a true distributor mart exists.

# BI-13 - Export and reporting endpoint design

Status: DESIGN ONLY
Date: 2026-06-25
Scope: design structured BI export endpoints. Do not implement Java, React,
DDL, Docker, .NET, or runtime changes in this task.

## 1. Decision

Responsibility split:

- PNG export stays in the frontend. It is a WYSIWYG capture of the rendered BI
  page DOM through the existing `html-to-image` path.
- PDF export stays in the frontend. It is the same WYSIWYG capture placed into a
  `jsPDF` document with report context.
- Excel and structured DATA export move to a backend Spring BI endpoint that
  reads the same `mart.*` objects as the BI-10 dashboards, so exported numbers
  match the dashboard numbers.

No new .NET service is introduced for BI reporting. Older architecture notes
mention `.NET` reporting, and `services-dotnet/{gateway,reporting}` exists as
empty `.gitkeep` placeholders, but the BI roadmap decision is now different:
export/reporting belongs in the Spring BI layer defined by BI-11, inside the
`com.lpn.aibi.llmorchestrator.bi` package first and later inside a standalone
Spring `bi-service` only after the BI-11 extraction gates pass.

## 2. Current frontend export behavior

The current export implementation lives in
`frontend/src/features/bi/utils/biExport.ts`.

| Format | Current implementation | BI-13 decision |
| --- | --- | --- |
| PNG | `captureElement()` dynamically imports `html-to-image`, waits for fonts and a paint cycle, captures the report element to canvas, then downloads a PNG blob. | Keep unchanged. It is visual/WYSIWYG, not structured data. |
| PDF | `exportPdf()` dynamically imports `jspdf`, writes the page title/range/compare context, and embeds the PNG capture into a landscape A4 PDF. | Keep unchanged. It preserves exactly what the user sees. |
| Excel | `exportExcel()` dynamically imports `fflate`, builds a minimal OOXML ZIP, and writes one `Rapport BI` sheet containing only metadata: page title, description, range, comparison flag, generated date, and source label. | Retire this client-side data path once the backend endpoint exists. It is honest today because placeholder pages do not have live datasets, but it must not become the source of real figures. |

The existing `BiExportDialog.tsx` can keep the same visible format choice. Only
the Excel branch should later be redirected to the backend endpoint.

## 3. Endpoint family

Base endpoint:

```text
GET /v1/bi/export/{page}
```

`{page}` is one of:

```text
overview | orders | revenue | articles | clients | commercial | analysis
```

The endpoint returns a real `.xlsx` workbook for the requested BI page. It is a
download endpoint, not a JSON API.

### 3.1 Response headers

Successful response:

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="lpn-bi-{page}-{from}-{to}.xlsx"; filename*=UTF-8''lpn-bi-{page}-{from}-{to}.xlsx
Cache-Control: no-store
X-BI-Export-Page: {page}
X-BI-Generated-At: {ISO-8601 timestamp}
```

Required MIME type:

```text
application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Filename rules:

- Prefix: `lpn-bi`.
- Include page slug.
- Include normalized `from` and `to` dates when provided.
- Sanitize labels to ASCII-safe filename parts.
- Keep `.xlsx` extension.

### 3.2 Error responses

Errors use the BI-10 JSON error envelope, not an `.xlsx` body:

```http
Content-Type: application/json
```

| HTTP | Code | When |
| --- | --- | --- |
| 400 | `BI_INVALID_FILTER` | malformed date, invalid `page`, invalid enum, `from > to`, or limit too high |
| 404 | `BI_NO_DATA` | required marts exist but no rows match the filters |
| 422 | `BI_UNSUPPORTED_FILTER_COMBINATION` | requested filter cannot be honored for that page's marts |
| 503 | `BI_MART_UNAVAILABLE` | required `mart.*` object is missing or inaccessible |
| 500 | `BI_EXPORT_FAILED` | workbook generation fails after query success |

## 4. Request parameters

Reuse the BI-10 filter contract. The export endpoint must not invent a parallel
filter vocabulary.

| Parameter | Type | Applies to | Notes |
| --- | --- | --- | --- |
| `from` | ISO date | all pages | Same date filter as BI-10. |
| `to` | ISO date | all pages | Same date filter as BI-10. |
| `granularity` | `day`, `week`, `month` | overview, revenue, analysis | Controls trend worksheet bucket. |
| `compare` | boolean | all pages | Include comparison columns/sheets only when the matching BI-10 endpoint supports comparison. |
| `commercial` | integer | orders, clients, commercial, analysis, overview | Resolves to `commercial_key` or `ad_user_id`. |
| `category` | integer | articles, clients, analysis, overview | Resolves to `product_category_key` or source category ID. |
| `supplier` | integer | articles, clients, analysis, overview | Resolves to `supplier_key` or `supplier_id`. |
| `customer` | integer | articles, clients, commercial, analysis, overview | Resolves to `customer_key` or `c_bpartner_id`. |
| `document_type` | integer | orders, revenue, commercial, analysis, overview | Resolves to `document_type_key` or `c_doctype_id`. |
| `region` | string | overview, revenue, articles, clients, commercial, analysis | Exact normalized region label. |
| `city` | string | clients, commercial, analysis | Exact normalized city label. |
| `payment_status` | `PAID`, `UNPAID` | revenue, clients, analysis | Uses `mart_payment_status.payment_status` / `is_paid`. |
| `limit` | integer | ranked worksheets | Default follows BI-10; max 100 for dashboard-shaped exports. |
| `offset` | integer | ranked worksheets | Defaults to 0. |

Optional implementation-only parameters for a later task:

| Parameter | Type | Decision |
| --- | --- | --- |
| `locale` | string | Optional; default `fr-MA` or frontend locale. Controls workbook labels/number formats only, not data. |
| `timezone` | string | Optional; default server/app timezone. Used only for generated-at display. |

## 5. Spring BI layer placement

The later implementation belongs under the BI-11 package boundary:

```text
com.lpn.aibi.llmorchestrator.bi
```

Suggested ownership, design only:

| Package area | Responsibility |
| --- | --- |
| `bi.api` | Export controller under `/v1/bi/export/{page}`; delegates only to BI application services. |
| `bi.api.dto` | Export filter/request model, error envelope reuse, page enum. |
| `bi.application` | Normalize filters, enforce page support, orchestrate workbook generation. |
| `bi.infrastructure.mart` | Static mart queries, shared with or parallel to the BI-10 page repositories. |
| `bi.infrastructure.export` | Workbook writer abstraction and `.xlsx` sheet layout. |
| `bi.infrastructure.config` | Reuse the BI read-only datasource and query timeout from BI-11. |

Boundary rules:

- Use the BI read-only datasource only.
- Read only `mart.*` views from BI-08.
- Do not call `SqlExecutorClient`.
- Do not query raw files, `staging`, `warehouse` directly for public export
  rows, or the old `business` schema.
- Do not add or revive `services-dotnet/reporting` for BI-13.

## 6. Workbook structure

Every workbook starts with a `Context` sheet:

| Context field | Source |
| --- | --- |
| Page | `{page}` path variable |
| Generated at | server timestamp |
| Filters | normalized BI-10 query params |
| Source marts | marts used by the workbook |
| Contract version | BI-10 / BI-13 design version |
| Data caveat | "Exported from mart views; PNG/PDF remain visual frontend captures." |

Then page-specific sheets follow. Sheet names must be Excel-safe: <= 31
characters, no `[]:*?/\\`, stable French labels where practical.

## 7. Marts to worksheets mapping

### 7.1 `GET /v1/bi/export/overview`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Executive KPI values from `/v1/bi/overview`. | `mart_sales_daily`, `mart_sales_monthly` |
| `Tendance` | Period trend: orders, ordered value, invoices, invoiced sales. | `mart_sales_monthly`, bucketed `mart_sales_daily` |
| `Signaux` | Dominant commercial, supplier, and zone. | `mart_sales_by_commercial`, `mart_sales_by_product`, `mart_sales_by_region` |
| `Mix ventes` | Invoiced, ordered, articles followed. | `mart_sales_daily`, `mart_sales_by_product` |
| `Top clients` | Top customer rows. | `mart_sales_by_customer` |
| `Top produits` | Top product rows. | `mart_sales_by_product` |
| `Statuts commandes` | Order status breakdown. | `mart_order_to_invoice_flow` |
| `Statuts factures` | Paid/unpaid status breakdown. | `mart_payment_status` |

### 7.2 `GET /v1/bi/export/orders`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Order count, dominant type, invoice coverage, status count. | `mart_order_to_invoice_flow` |
| `Par type` | Order type ranking by count and ordered sales. | `mart_order_to_invoice_flow` |
| `Flux commercial` | Commercial flow and conversion coverage. | `mart_sales_by_commercial`, `mart_order_to_invoice_flow` |
| `Statuts` | Order status rows. | `mart_order_to_invoice_flow` |

### 7.3 `GET /v1/bi/export/revenue`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Invoiced sales, ordered sales, gap, average order value. | `mart_sales_daily`, `mart_sales_monthly` |
| `Tendance CA` | Period trend for ordered and invoiced sales. | `mart_sales_daily`, `mart_sales_monthly` |
| `Lecture responsable` | Best month, invoice coverage, unpaid invoice signals. | `mart_sales_monthly`, `mart_sales_daily`, `mart_payment_status` |
| `Statuts CA` | Invoiced, to-deliver, and gap rows. | `mart_sales_daily`, `mart_order_to_invoice_flow` |

### 7.4 `GET /v1/bi/export/articles`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Active products, top category, top theme, stock risk count. | `mart_sales_by_product`, `mart_stock_risk` |
| `Top articles` | Product ranking with category, supplier, quantity, sales. | `mart_sales_by_product` |
| `Mix categories` | Category contribution. | `mart_sales_by_product` |
| `Mix themes` | Theme contribution. | `mart_sales_by_product` |
| `Mix collections` | Collection contribution. | `mart_sales_by_product` |
| `Priorites stock` | Stock risk rows. | `mart_stock_risk` |

### 7.5 `GET /v1/bi/export/clients`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Active customers, top customer, commercial portfolio count, geography count. | `mart_sales_by_customer`, `mart_sales_by_region` |
| `Top clients` | Customer ranking and average invoice values. | `mart_sales_by_customer` |
| `Par commercial` | Customer portfolio by commercial. | `mart_sales_by_customer` |
| `Par article` | Customer/article contribution rows. | `mart_sales_by_product` |
| `Geographie` | Region and city contribution. | `mart_sales_by_region` |
| `Risque finance` | Unpaid invoice customer risks. | `mart_payment_status` |

### 7.6 `GET /v1/bi/export/commercial`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Commercial count, top commercial, conversion, region coverage. | `mart_sales_by_commercial`, `mart_sales_by_region` |
| `CA commerciaux` | Revenue by commercial. | `mart_sales_by_commercial` |
| `Conversion` | Ordered vs invoiced by commercial and gap. | `mart_sales_by_commercial` |
| `Terrain` | Strong zone, key customer, drill keys. | `mart_sales_by_region`, `mart_sales_by_customer`, `mart_sales_by_commercial` |

### 7.7 `GET /v1/bi/export/analysis`

| Worksheet | Purpose | Backing marts |
| --- | --- | --- |
| `KPI` | Same KPI fields as `/v1/bi/analysis`. | `mart_sales_daily` |
| `Tendance` | Period trend. | `mart_sales_daily`, `mart_sales_monthly` |
| `Types commandes` | Order type sales. | `mart_order_to_invoice_flow` |
| `Commerciaux` | Commercial sales. | `mart_sales_by_commercial` |
| `Categories` | Category sales. | `mart_sales_by_product` |
| `Themes` | Theme sales. | `mart_sales_by_product`, `mart_order_to_invoice_flow` when ordered-only fields are needed |
| `Fournisseurs` | Supplier sales. | `mart_sales_by_product` |
| `Distributeurs` | Compatibility sheet over supplier mart columns until a true distributor mart exists. | `mart_sales_by_product` |
| `Geographie` | City/region rows. | `mart_sales_by_region` |
| `Disponibilite` | Availability risks. | `mart_stock_risk` |
| `Top produits` | Product contribution. | `mart_sales_by_product` |
| `Top clients` | Customer contribution. | `mart_sales_by_customer` |
| `Options filtres` | Filter option lists used by the analysis page. | `mart_sales_by_commercial`, `mart_order_to_invoice_flow`, `mart_sales_by_product` |

## 8. Data consistency rules

- Export queries must reuse the same mart columns and formulas as BI-10.
- Exported KPI values must match the corresponding `/v1/bi/{page}` JSON response
  for the same normalized filters.
- `compare=true` must add comparison columns or sheets using the same comparison
  windows as BI-10; do not compute an unrelated comparison in the exporter.
- `distributor_*` remains a compatibility alias over `supplier_*` columns until
  a later mart separates distributor semantics.
- Paid/unpaid status uses `mart_payment_status.payment_status` and `is_paid`,
  preserving the BI-08/BI-10 `ISPAID` decision.
- Unknown dimension keys remain `0` and should be labeled with the same
  "non renseigne" display labels as the mart views.

## 9. Frontend migration intent

Current Excel path:

```text
BiExportDialog -> exportBiView(format="excel") -> exportExcel() -> fflate OOXML metadata workbook
```

Later backend Excel path:

```text
BiExportDialog -> exportBiView(format="excel") -> fetch /v1/bi/export/{page}?sameFilters -> download .xlsx blob
```

Migration steps for a later implementation task:

1. Keep the current PNG/PDF branches exactly as they are.
2. Add a page slug and normalized BI-10 filters to the export context passed from
   `BiShell` or page-level code.
3. Replace only the `format === "excel"` branch with a backend blob download.
4. Preserve `BiExportError` and the `excel-failed` user-facing error message.
5. Remove the client-side OOXML helpers (`XLSX_*`, `buildSheetXml`,
   `exportExcel`) only after every BI page's Excel action uses the backend.
6. Keep the same dialog labels unless BI-12 implementation renames
   "distributeur" to "fournisseur".

## 10. Performance and limits

- The export endpoint is synchronous for BI-13 because the workbook is
  dashboard-shaped and mart-aggregated.
- Default row limits follow BI-10. Ranked worksheets default to 10 rows and max
  100 rows unless a later requirement explicitly asks for a bulk analytical
  export.
- Apply a server-side query timeout from the BI read-only datasource.
- If any workbook is expected to exceed a practical response size threshold in a
  later implementation, design an async export job separately; do not add that
  complexity now.
- Use streaming workbook generation in implementation if memory pressure appears,
  but BI-13 does not choose a Java library or add dependencies.

## 11. Security and audit notes

- Use the same authentication/authorization posture as `/v1/bi/*`.
- Use the BI read-only datasource (`lpn_ai_readonly`) with `search_path=mart,warehouse`.
- Do not grant write privileges for export.
- Include request ID and normalized filters in logs.
- Never write exported workbooks to server disk in the default path; stream the
  response.
- Do not include hidden raw table columns or PII beyond what BI-10 already
  exposes to the dashboard.

## 12. Acceptance checks for implementation

When this design is implemented later, acceptance requires:

1. Every visible Excel action maps to one of `/v1/bi/export/{page}`.
2. Every workbook includes `Context` plus the page-specific sheets above.
3. Every sheet reads only the listed `mart.*` objects.
4. KPI rows match the JSON `/v1/bi/{page}` response for the same filters.
5. Successful responses use
   `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
6. Successful responses include an attachment `Content-Disposition` filename.
7. Error responses use the BI-10 JSON error envelope.
8. PNG and PDF export behavior remains frontend WYSIWYG capture.
9. The client-side `fflate` Excel metadata path is retired only after backend
   Excel export is available for all BI pages.
10. No `.NET` reporting service is created for BI exports.

## 13. Coverage check

| Page/export action | Backend endpoint | Workbook mapping | Status |
| --- | --- | --- | --- |
| `BiOverviewPage` Excel | `/v1/bi/export/overview` | Section 7.1 | Covered |
| `BiCommandesPage` Excel | `/v1/bi/export/orders` | Section 7.2 | Covered |
| `BiRevenuePage` Excel | `/v1/bi/export/revenue` | Section 7.3 | Covered |
| `BiArticlesPage` Excel | `/v1/bi/export/articles` | Section 7.4 | Covered |
| `BiClientsPage` Excel | `/v1/bi/export/clients` | Section 7.5 | Covered |
| `BiCommercialPage` Excel | `/v1/bi/export/commercial` | Section 7.6 | Covered |
| `SalesAnalysisPage` Excel | `/v1/bi/export/analysis` | Section 7.7 | Covered |

Every page's Excel action maps to a backend endpoint and mart-backed workbook
definition. PNG and PDF are intentionally absent from the backend coverage table
because they stay frontend WYSIWYG exports.

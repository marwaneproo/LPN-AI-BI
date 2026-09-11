# BI-00 — Repo & Service Inventory Snapshot

**Task:** BI-00  
**Date:** 2026-06-25  
**Branch:** `bi-polished-dashboard`  
**Model:** Sonnet 4.6 medium  
**Status:** ✅ Complete (read-only)

---

## 1. Services — Native Dev Stack

Native launcher: `scripts/launch-local-native.ps1` (no Docker for dev).  
Docker Compose (`docker-compose.yml`) maps PG to port `5433`; native dev uses `5432`.

| Service | Runtime | Port (native) | Health URL | Role |
|---|---|---|---|---|
| `PostgreSQL 18` | native | **5432** | TCP check | DB — `lpn_ai_bi` |
| `Ollama` | native | 11434 | `http://localhost:11434/api/tags` | LLM runtime |
| `schema-retrieval` | Python/FastAPI | **8084** | `http://localhost:8084/health` | Qdrant-backed schema embedding + retrieval |
| `sql-validator` | Python/FastAPI | **8086** | `http://localhost:8086/health` | sqlglot SQL validation |
| `predictive` | Python/FastAPI | **8085** | (no explicit health) | Prophet CA forecasting |
| `sql-executor` | Java/Spring Boot | **8082** | `http://localhost:8082/actuator/health` | Validated SQL execution against `business` |
| `llm-orchestrator` | Java/Spring Boot | **8081** | `http://localhost:8081/actuator/health` | LLM orchestration + BI serving (current) |
| `frontend` | Vite/React | **5173** | `http://127.0.0.1:5173` | BI dashboard UI |

**Placeholder services (empty stubs — `.gitkeep` only):**
- `services-dotnet/gateway/`
- `services-dotnet/reporting/`

---

## 2. Current BI Endpoints (llm-orchestrator, port 8081)

All BI-serving logic currently lives inside `llm-orchestrator`. Two distinct concerns are co-located:

### Deterministic BI (SalesDashboardController + SalesDashboardService)

| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/sales-dashboard` | KPI snapshot: order count, CA, top customers, top products, status breakdowns, monthly trend |
| `GET` | `/v1/sales-analysis` | Filtered deep analysis: `?from&to&salesrepId&categoryId&themeId&supplierId&distributorId&orderTypeId` |

`SalesDashboardService` delegates SQL execution to `SqlExecutorClient` (calls `sql-executor` on 8082).

### Forecasting (ForecastController → PredictiveClient → predictive:8085)

| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/forecast/ca` | Prophet CA forecast: `?grain&key&horizon` |
| `GET` | `/v1/forecast/backtest` | Backtest: `?grain&key&holdout` |
| `GET` | `/v1/forecast/options` | Filter options for grain |

### LLM / Auth / Utility

| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/qa` | NL→SQL Q&A (QaController/QaService) |
| `*` | `/v1/sql-generation` | SQL generation (SqlGenerationController) |
| `*` | `/v1/auth/*` | Auth (AuthController/Service/Repository) |
| `*` | `/v1/perf-trace` | Performance traces (PerformanceTraceController) |
| `*` | `/v1/chat/*` | Chat session management (ChatSessionController) |

**Key observation:** deterministic BI (`SalesDashboard*`) and LLM orchestration (`QaService`, `SqlGenerationService`) are coupled in one deployable — this is the strangler-fig extraction target.

---

## 3. Java Source Classes — llm-orchestrator

Located at `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/`:

**BI-specific (extraction targets):**
- `SalesDashboardController.java` — REST controller (2 endpoints)
- `SalesDashboardService.java` — all deterministic KPI + analysis SQL
- `ForecastController.java` — REST controller (3 endpoints)
- `PredictiveClient.java` / `PredictiveHttpClient.java` — HTTP client to `predictive`

**LLM-specific (stay in orchestrator):**
- `QaController.java` / `QaService.java`
- `SqlGenerationController.java` / `SqlGenerationService.java`
- `AnswerNarrationService.java`
- `QuestionIntentDetector.java` / `QuestionIntent.java`
- `SchemaRetrievalClient.java` / `SchemaRetrievalHttpClient.java`
- `SqlExecutorClient.java` / `SqlExecutorHttpClient.java`
- `SemanticSqlValidation.java` / `SemanticSqlValidator.java`
- `RetrievedTable.java` / `SqlExecutionResult.java` / `SqlValidationResult.java` / `SqlExecutionException.java`

**App-wide:**
- `LlmOrchestratorApplication.java`
- `OllamaModelConfig.java`
- `PerformanceTrace.java` / `PerformanceTraceController.java` / `PerformanceTraceRepository.java`
- `AuthController.java` / `AuthService.java` / `AuthRepository.java` / `AuthUser.java`
- `ChatSessionController.java` / `ChatSessionRepository.java` / `ChatSessionService.java`

---

## 4. Frontend BI Pages — Status

Located at `frontend/src/features/bi/pages/`:

| File | Route (approx.) | Live data? | Data source |
|---|---|---|---|
| `DashboardOverviewPage.tsx` | `/tableau-de-bord/vue-ensemble` | ✅ **LIVE** | `useSalesDashboard` → `GET /v1/sales-dashboard` |
| `SalesAnalysisPage.tsx` | `/tableau-de-bord/analyse-ventes` | ✅ **LIVE** | `useSalesAnalysis` → `GET /v1/sales-analysis` |
| `DashboardChartDetailPage.tsx` | (chart drill-down) | ✅ **LIVE** | `fetchSalesDashboard` → `GET /v1/sales-dashboard` |
| `DashboardHubPage.tsx` | `/tableau-de-bord` | ✅ **hub/nav** | Navigation only |
| `BiOverviewPage.tsx` | (BI overview alt) | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |
| `BiCommandesPage.tsx` | `/tableau-de-bord/commandes` | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |
| `BiRevenuePage.tsx` | `/tableau-de-bord/chiffre-affaires` | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |
| `BiArticlesPage.tsx` | `/tableau-de-bord/articles` | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |
| `BiClientsPage.tsx` | `/tableau-de-bord/clients` | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |
| `BiCommercialPage.tsx` | `/tableau-de-bord/commerciaux` | ⚠️ **PLACEHOLDER** | `BiWidgets` mock values |

**Frontend components inventory:**
- `components/charts/` — BiTooltip, CaComparisonChart, CommercialRevenueComparisonChart, ContributionPieChart, RankedBarChart, SalesAnalysisTrendChart, SalesTrendChart, StatusBars (Recharts-based)
- `components/widgets/BiWidgets.tsx` — placeholder widget set (mock data)
- `components/kpi/BiKpiCard.tsx` — KPI card component
- `components/filters/` — date range, analysis filter card
- `components/controls/` — export dialog, date range dialog
- `components/sections/` — CommercialSection, DetailedProductsSection, DisponibiliteSection, DistributeurSection, GeographieSection, OrderTypeSection, RepartitionSection, ThematiqueSection, TopContributeursSection
- `hooks/useSalesDashboard.ts`, `useSalesAnalysis.ts` — live data hooks
- `api/biApi.ts` — HTTP client (typed)
- `types/bi.types.ts` — TypeScript DTO types
- `utils/biExport.ts` — PNG/PDF + real `.xlsx` (fflate)
- `utils/biFormatters.ts`, `biTransformers.ts` — data utilities

---

## 5. PostgreSQL — Schema & Table Inventory

**Connection (read-only):** `psql -U lpn_ai_readonly -h localhost -p 5432 -d lpn_ai_bi`

**Schemas verified:**
```
 Name     | Owner
----------+-------------------
 app      | lpn_app_admin
 business | lpn_app_admin
 public   | pg_database_owner
```

**`business` schema — top 20 tables by row count:**

| Table | Row count |
|---|---|
| `m_inoutline` | 356,410 |
| `c_orderline` | 354,910 |
| `c_invoiceline` | 335,877 |
| `rv_storage` | 99,234 |
| `m_product_po` | 39,359 |
| `m_product` | 35,822 |
| `c_order` | 20,336 |
| `m_inout` | 18,718 |
| `c_bpartner_vendor` | 14,264 |
| `fact_sales_monthly_by_theme` | 8,446 |
| `c_invoice` | 6,658 |
| `c_allocationline` | 4,212 |
| `m_product_collection` | 3,116 |
| `c_allocationhdr` | 1,473 |
| `c_payment` | 1,443 |
| `c_bpartner_location` | 888 |
| `c_bpartner` | 823 |
| `lpn_customer_portfolio_2025_2026` | 823 |
| `c_location` | 772 |
| `m_product_theme` | 704 |
| `fact_sales_monthly_by_category` | 615 |
| `fact_sales_monthly_by_commercial` | 470 |
| `c_city` | 302 |
| `ad_user` | 28 |
| `m_product_category` | 28 |
| `fact_sales_monthly` | 25 |
| `c_region` | 16 |
| `c_salesregion` | 13 |
| `m_product_type` | 10 |
| `c_doctype` | 7 |
| `m_pricelist` | 5 |
| `c_paymentterm` | 5 |
| `c_tax` | 3 |
| `products_without_supplier` | 3 |

**`app` schema — tables:**

| Table | Row count |
|---|---|
| `ai_request_performance_trace` | 223 |
| `ai_bi_sessions` | 66 |
| `ai_bi_chat_messages` | 34 |
| `ai_bi_chat_sessions` | 7 |
| `import_history` | 7 |
| `ai_bi_users` | 2 |

**Not yet present:** `staging`, `warehouse`, `mart` schemas — these are the ETL output targets.

---

## 6. Existing DWH / ETL Artifacts

Located at `DataWareHouse/processus_de_vente/`:

### Design documents
| File | Content |
|---|---|
| `01_warehouse_layers.md` | `stg_*` / `dim_*` / `fact_*` / `mart_*` layer design |
| `02_star_schema_design.md` | ~10 dims, 8 facts, SCD1 decisions |
| `03_fact_grain_design.md` | exact grain per fact table |
| `04_power_bi_guide.md` | Power BI connection guide |
| `schema_overview.md` | Schema overview |
| `ddl/draft_schema_design.sql` | First-cut DDL draft |
| `PowerBI_Processus_Vente.pbix` | Power BI workbook |

### ETL scripts (`etl/scripts/`)
| Script | Role |
|---|---|
| `extract_sources.py` | Extract from source files |
| `transform_dimensions.py` | Build dimension CSVs |
| `transform_facts.py` | Build fact CSVs |
| `validate_etl.py` | Validate ETL output |
| `run_etl.py` | Orchestration runner |

### ETL output (CSV, in `etl/output/`)
- **Staging:** `stg_c_order.csv`, `stg_c_orderline.csv`, `stg_c_invoice.csv`, `stg_c_invoiceline.csv`, `stg_m_product.csv`, `stg_c_bpartner.csv`, `stg_m_inout.csv`, `stg_m_inoutline.csv`, `stg_c_payment.csv`, + 20 more stg CSVs
- **Dimensions:** `dim_commercial.csv`, `dim_customer.csv`, `dim_date.csv`, `dim_document_type.csv`, `dim_geography.csv`, `dim_payment_term.csv`, `dim_price_list.csv`, `dim_product_category.csv`, `dim_product.csv`, `dim_sales_region.csv`, `dim_supplier.csv`, `dim_warehouse.csv`
- **Facts:** `fact_delivery_line.csv`, `fact_delivery.csv`, `fact_invoice_line.csv`, `fact_invoice.csv`, `fact_payment_allocation.csv`, `fact_sales_order_line.csv`, `fact_sales_order.csv`, `fact_stock_snapshot.csv`
- **Marts:** `mart_sales_by_commercial.csv`, `mart_sales_by_customer.csv`, `mart_sales_by_product.csv`, `mart_sales_by_region.csv`, `mart_sales_by_supplier.csv`, `mart_sales_overview.csv`
- **Config/logs:** `etl_config.json`, `etl_run_log.json`, `source_schema_snapshot.json`, `validation/etl_validation_report.json`, `validation/etl_validation_report.md`

---

## 7. Source Audits & Semantic Layer

### Source audit docs (`docs/warehouse/`)
| File | Content |
|---|---|
| `VENTE_DWH_COVERAGE_PROFILE.md` + `.json` | Data coverage profile |
| `VENTE_DWH_SOURCE_AUDIT.md` + `.json` | Source audit |
| `VENTE_DWH_FINAL_EXTRACTION_REVIEW.md` + `.json` | Final extraction review |
| `VENTE_FOURTH_EXTRACTION_AUDIT.md` + `.json` | 4th extraction audit |
| `VENTE_FOURTH_EXTRACTION_DETAIL_PROFILE.md` + `.json` | 4th extraction detail profile |

### Semantic layer (`docs/semantic_layer/`)
| File | Content |
|---|---|
| `metrics.yml` | Canonical metric definitions (MAD) |
| `business_terms.csv` | Business glossary |
| `dwh_assets.csv` | DWH asset registry |

---

## 8. Rebuild Roadmap — Task Status Reference

`docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` — Tasks 0–12:

| Task | Title |
|---|---|
| Task 0 | Bring up the stack & snapshot the current state |
| Task 1 | Inventory every data folder |
| Task 2 | Approve the canonical source & authorize cleanup |
| Task 3 | Deep profiling of the canonical transactional files |
| Task 4 | Define the unified source mapping & dedup rule |
| Task 5 | Load canonical raw into the `staging` schema (PostgreSQL) |
| Task 6 | Build the `warehouse` dimensions |
| Task 7 | Build the `warehouse` fact tables |
| Task 8 | Build the `mart` layer |
| Task 9 | Reconciliation & validation report |
| Task 10 | Repoint ONE dashboard KPI to the mart (governance proof) |
| Task 11 | Repoint the forecasting fact to the warehouse (extended history) |
| Task 12 | Documentation, schema metadata, and repo hygiene |

**BI Roadmap linkage:** BI-01 wraps/extends Rebuild Task 1; BI-06 references Task 5; BI-07 references Tasks 6-7; BI-08 references Task 8; BI-09 references Tasks 5-9. The rebuild roadmap is authoritative for the ETL implementation; the BI roadmap sequences and wraps it.

---

## 9. What Is NOT Yet Present

| Item | Status | Next action |
|---|---|---|
| `staging` schema in `lpn_ai_bi` | ❌ Not in DB | BI-06 + Rebuild Task 5 |
| `warehouse` schema in `lpn_ai_bi` | ❌ Not in DB | BI-07 + Rebuild Tasks 6-7 |
| `mart` schema in `lpn_ai_bi` | ❌ Not in DB | BI-08 + Rebuild Task 8 |
| Clean `bi` package inside `llm-orchestrator` | ❌ Not extracted | BI-11 |
| Standalone `bi-service` | ❌ Intentionally deferred | BI-11 (strangler-fig step 2) |
| Backend `.xlsx` reporting endpoint | ❌ Not implemented | BI-13 |
| 5 placeholder BI pages wired to real data | ❌ Placeholder | BI-12 (after marts + API) |

---

## 10. Validation

```
psql -U lpn_ai_readonly -h localhost -p 5432 -d lpn_ai_bi -c "\dn"
```
Result: `app`, `business`, `public` — ✅ matches snapshot (no staging/warehouse/mart yet).

Table counts match the snapshot in `context_bi_service.md` §3:
- `c_orderline`: 354,910 ✅
- `c_invoiceline`: 335,877 ✅  
- `m_product`: 35,822 ✅
- `fact_sales_monthly`: 25 ✅

---

## 11. Recommended Next Task

**BI-01 — Data folder inventory & reconciliation**  
Reason: The ETL prototype exists (CSV output), but the canonical source set in `Youssef_Extractions/` needs a metadata-only inventory reconciled against the existing `docs/warehouse/` audits to confirm dedup rules and the canonical file set before any profiling or staging work begins.

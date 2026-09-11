# BI Service & Data Architecture — Vision

**Project:** LPN AI-BI (PFE)
**Branch at authoring:** `bi-polished-dashboard`
**Authored:** 2026-06-25
**Status:** Planning document — no code implemented by this file.

> This document is the architectural north star for the BI data + serving work.
> It is **grounded in the current repository** and **explicitly reuses prior
> work** rather than restarting it. Read [`context_bi_service.md`](context_bi_service.md)
> before executing any task in [`02_bi_execution_tasks.md`](02_bi_execution_tasks.md).

---

## 1. Current state of the project

LPN AI-BI is a local AI-powered BI assistant over a Compiere/Oracle ERP sales
process (`processus de vente`), exported to PostgreSQL. As of this document:

- The stack runs **fully native (no Docker)** for development: PostgreSQL 18 on
  `localhost:5432`, embedded Qdrant, native Java/Python services. See
  `docs/LOCAL_DEV_SETUP.md`.
- Live services (ports): `llm-orchestrator` 8081, `sql-executor` 8082,
  `schema-retrieval` 8084, `predictive` 8085, `sql-validator` 8086, frontend 5173.
- The live `business` schema in `lpn_ai_bi` holds real data — verified counts:
  `c_orderline` 354,910, `c_invoiceline` 335,877, `m_product` 35,822,
  `fact_sales_monthly` 25. A real BI request (`GET /v1/sales-dashboard`) returns
  KPIs in ~0.5 s.

### What already exists for the warehouse (DO NOT rebuild from scratch)

| Artifact | Location | What it is |
|---|---|---|
| Warehouse layer design | `DataWareHouse/processus_de_vente/01_warehouse_layers.md` | `stg_*` / `dim_*` / `fact_*` / `mart_*` layering |
| Star schema design | `DataWareHouse/processus_de_vente/02_star_schema_design.md` | ~10 dims, 8 facts, SCD1 decisions |
| Fact grain design | `DataWareHouse/processus_de_vente/03_fact_grain_design.md` | exact grain per fact table |
| DDL draft | `DataWareHouse/processus_de_vente/ddl/draft_schema_design.sql` | first-cut DDL |
| Working ETL prototype | `DataWareHouse/processus_de_vente/etl/scripts/*.py` | `transform_dimensions`, `transform_facts`, `validate_etl`, `run_etl` (CSV output today) |
| Warehouse materialization plan | `docs/tasks/DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` | Tasks 0–12: CSV prototype → governed PostgreSQL `staging`/`warehouse`/`mart` |
| Source audits/profiles | `docs/warehouse/*.md` + `*.json` | coverage, source audit, 4th-extraction detail |
| Semantic layer | `docs/semantic_layer/{metrics.yml,business_terms.csv,dwh_assets.csv}` | canonical metric definitions (MAD) |

**Implication:** the data-modeling thinking is largely done. The two real gaps are
(a) **materializing** the warehouse in PostgreSQL on the full history (already
scoped by the rebuild roadmap), and (b) **the serving layer** — a clean BI API on
top of marts, decoupled from the LLM orchestrator, feeding the five frontend pages.
This roadmap owns (b) and **defers (a) to the existing rebuild roadmap**, which it
references rather than duplicates.

---

## 2. Current BI frontend state

Five BI sections exist in the redesigned frontend (`frontend/src/features/bi/`):

| Page | Route | Data today |
|---|---|---|
| Vue d'ensemble | `/tableau-de-bord/vue-ensemble` | partly live (`/v1/sales-dashboard`), partly preview |
| Commande | `/tableau-de-bord/commandes` | **placeholder/preview** (`BiWidgets` mock values) |
| Chiffre d'affaires | `/tableau-de-bord/chiffre-affaires` | **placeholder/preview** |
| Articles | `/tableau-de-bord/articles` | **placeholder/preview** |
| Client | `/tableau-de-bord/clients` | **placeholder/preview** |

Charts use **Recharts** (live data pages) and custom CSS mini-charts (`BiWidgets`,
placeholder pages). Export is frontend-only (PNG/PDF via `html-to-image`+`jsPDF`,
real `.xlsx` via `fflate`). The placeholder pages are the visible symptom of the
missing serving layer.

---

## 3. Current backend / service state

- **BI logic lives inside `llm-orchestrator`**: `SalesDashboardController`,
  `SalesDashboardService` (deterministic KPI SQL), plus `ForecastController` +
  `PredictiveClient` (calls the `predictive` service). This couples two unrelated
  concerns — deterministic BI and LLM orchestration — in one deployable.
- `sql-executor` runs validated SQL against the `business` read model;
  `sql-validator` (sqlglot) guards it. These are reusable by any consumer.
- `services-dotnet/{gateway,reporting}` are **empty placeholders** (`.gitkeep`).

---

## 4. Current raw data situation

`Youssef_Extractions/` holds ~6.5 GB of source exports (safe metadata inventory):

| Folder | Files | Size | Role |
|---|---|---|---|
| `data/` (incl. `Exported_LPN`, 96 xlsx) | 352 | 5.3 GB | **canonical** 2024–2026 source |
| `vente_2025_2026_import` | 39 | 393 MB | 2025–2026 import set |
| `2nd_Extraction` | 30 | 126 MB | extraction iteration |
| `vente_clean_import` | 21 | 108 MB | cleaned CSV (prototype input) |
| `4th_Extraction` | 14 | 97 MB | enrichment dims (geo, terms, price lists) |
| `vente_bi_enrichment_import` | 8 | 21 MB | BI enrichment |
| `3rd_Extraction` | 8 | 14 MB | enrichment exports |
| `forecast_monthly_import` | 5 | 544 KB | monthly forecast fact source |
| `1st_Extraction` / `txt` | 23 / 3 | 752 KB / 12 KB | early/aux |

Total file mix: **278 xlsx, 65 csv, 7 json**. Known traps already documented:
overlap between `2024_*` and `53..58` (dedup by PK), and the inflated CA columns in
`57_..PORTFOLIO_24M.xlsx` (many-to-many join bug) — see the rebuild roadmap §"Key facts".

---

## 5. Why the raw folders are an *ingestion* problem, not a *serving* problem

The 6.5 GB are **source files**: heterogeneous Excel/CSV, overlapping, with
encoding/format quirks and dedup needs. A request-time API cannot parse gigabytes
of Excel per call, cannot guarantee consistency, and cannot enforce business rules
reproducibly. Those concerns belong to an **offline, batch ETL** that runs
occasionally and produces a clean, indexed, query-optimized read model in
PostgreSQL. The serving API then answers in milliseconds from curated marts.

**Rule (non-negotiable): the BI service never opens a file under `Youssef_Extractions/`.**
It reads only `mart_*` (and, for drill-down, `dim_*`/`fact_*`) in PostgreSQL.

---

## 6. Why BI must be separated from LLM orchestration

| Concern | Deterministic BI | LLM orchestration |
|---|---|---|
| Input | structured filters | natural language |
| Output | exact, reproducible aggregates | generated SQL + narration |
| Latency budget | tens of ms | seconds |
| Failure mode | wrong number = critical | refusal/hallucination |
| Dependencies | Postgres marts only | Ollama, schema-retrieval, validator |
| Test strategy | golden-number assertions | eval harness |

Mixing them means a slow/failing LLM path can destabilize a dashboard, deploys
couple, and ownership blurs. They are **different bounded contexts** (DDD) and
should be separable deployables.

---

## 7. Recommended architecture (layered)

```
RAW (Youssef_Extractions/*.xlsx,*.csv)
        │  (Phase: offline batch ETL — Python, re-runnable)
        ▼
PostgreSQL  schema staging   (stg_*)     exact deduped copies
        │  transform
        ▼
PostgreSQL  schema warehouse (dim_*, fact_*)   star schema, 2024–2026, SCD1
        │  aggregate
        ▼
PostgreSQL  schema mart      (mart_*)    BI-ready, one set per frontend page
        │  read-only
        ▼
BI SERVING API  (Spring Boot — deterministic, marts-only, fast JSON)
        │  HTTP /v1/bi/*
        ▼
FRONTEND  (5 BI pages, Recharts) ── PNG/PDF export stays here
                                  └─ Excel/data export ← backend reporting endpoint

AI ORCHESTRATION (llm-orchestrator)  stays separate: NL→SQL, narration, reasoning
```

- **ETL/data layer** — Python jobs (extend the existing `DataWareHouse` ETL), not a
  service. Idempotent, logged, with data-quality reports. Owned by the **rebuild
  roadmap (Tasks 0–12)**.
- **PostgreSQL staging/warehouse/marts** — the single governed read model everyone
  shares (app, Superset, AI retrieval, forecasting). Marts are page-shaped.
- **BI serving API** — thin Spring Boot layer reading marts; returns the DTOs the
  five pages need (KPIs, trends, rankings) with filters (date range, granularity,
  compare, page-specific).
- **Frontend** — consumes `/v1/bi/*`; removes placeholder data page by page; keeps
  loading/empty/error states.
- **AI orchestration** — unchanged responsibilities; may *call* the BI API later but
  does not own dashboard logic.

---

## 8. Dedicated BI microservice — is it a good idea?

**Yes, conceptually** — BI serving and LLM orchestration are distinct bounded
contexts. **But timing matters for a solo PFE with a July deadline.** A new
deployable adds Gradle module + Docker + compose + proxy + CI overhead.

### When to choose which

| Choose **modular `bi` package inside `llm-orchestrator`** first if… | Choose **dedicated `bi-service` deployable** when… |
|---|---|
| You want clean boundaries fast, low ops cost | Marts are stable and the API contract is frozen |
| Deadline pressure; few endpoints | You need independent deploy/scaling or a defense-grade architecture story |
| Team of one | The package has clean seams and tests (extraction is mechanical) |

**Recommended path:** **Strangler-fig in two steps.** (1) Carve a clean,
self-contained `bi` package *inside* `llm-orchestrator` (own controllers, services,
read-only datasource, DTOs, tests) that reads **marts only**. (2) Once the marts and
contract are stable, lift that package into a standalone Spring Boot `bi-service`
with near-zero logic changes. This gets the boundary now and the deployable later
without blocking on plumbing.

---

## 9. Why the BI service must not read raw files

Restating because it is the most common architecture mistake here: parsing Excel at
request time is slow, non-reproducible, memory-heavy, and impossible to govern.
Curation (dedup, normalization, business rules, validation) must happen **once**, in
ETL, and be **materialized**. The service reads the result. This is what makes
dashboard numbers trustworthy and identical across the app, Superset, and the AI.

---

## 10. Why Spring Boot is the cleanest direction here

The existing BI logic (`SalesDashboardService`, etc.) is **already Java/Spring**, the
team already runs two Spring services, and `sql-executor` already owns the
read-only JDBC path. Extracting/forming a Spring `bi` package reuses that stack,
tooling, and the existing read-only datasource pattern — minimal new surface area.

### Why NOT add a new .NET service now

`services-dotnet/{gateway,reporting}` are empty placeholders. Introducing .NET means
a **third language/runtime/CI toolchain** for one solo developer. Only justify it if
a **strong, concrete reporting requirement** appears that .NET uniquely serves
(e.g., a mandated SSRS/Power BI Report Server integration). Otherwise, reporting
(including `.xlsx`) belongs in the Spring BI layer.

---

## 11. How Excel / reporting / export fits

- **PNG / PDF (visual)** — stays **frontend**: it's a WYSIWYG capture of the rendered
  DOM/Recharts (`html-to-image` + `jsPDF`). The backend has no DOM.
- **Excel / structured data** — should become a **backend BI/reporting endpoint** that
  streams a real `.xlsx` built from the **same marts** the dashboard reads
  (`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).
  Today the frontend produces a real `.xlsx` of *report metadata* via `fflate`
  because the pages are placeholders; once marts + BI API exist, Excel becomes a
  data export from the server (consistent numbers, server-side, larger datasets).
- A dedicated reporting service is **not** required; a reporting controller in the BI
  layer is sufficient until proven otherwise.

---

## 12. How the frontend should consume BI APIs

- One typed API client per page concern under `frontend/src/features/bi/api/`
  (extend the existing `biApi.ts`), returning page DTOs.
- Pages send: `from`, `to`, `granularity` (day/week/month), `compare` (bool), and
  page-specific filters (commercial, category, supplier, order type).
- Keep **loading / empty / error** states; replace placeholders **one page at a time**
  behind the real endpoint so the UI never regresses.
- Visual export stays client-side; data export calls the backend reporting endpoint.

---

## 13. Risks & tradeoffs

| Risk | Mitigation |
|---|---|
| Duplicating the existing DWH/ETL work | This roadmap **references** `DataWareHouse/` + rebuild roadmap; it does not re-derive the schema |
| Big-bang extraction of a new service | Strangler-fig: package first, deployable later |
| Mart drift vs. live `business` schema | Reconciliation task with golden-number checks before repointing any KPI |
| Deadline vs. scope | Phases are ordered so each delivers value; service extraction (Phase 6) is optional/deferrable |
| Memory blowups profiling 6.5 GB | Always sample/stream; never load whole Excel into memory |
| Breaking the working app | New schemas (`staging`/`warehouse`/`mart`) never drop `business`; repoint KPI-by-KPI |

---

## 14. Recommended phased roadmap (summary)

| Phase | Theme | Owns / references |
|---|---|---|
| 0 | Safety & inventory | reconcile with existing audits |
| 1 | Data profiling | extend `docs/warehouse/` profiles |
| 2 | Cleaning plan | dedup/normalize/reject rules |
| 3 | Staging & warehouse + **marts for the 5 pages** | **reference** star-schema design + rebuild roadmap |
| 4 | ETL implementation plan | **extend** `DataWareHouse/.../etl` to Postgres |
| 5 | BI API design | endpoints/DTOs/filters for 5 pages |
| 6 | BI extraction decision | package-in-orchestrator → optional `bi-service` |
| 7 | Frontend integration | wire 5 pages, drop placeholders |
| 8 | Export/reporting | backend `.xlsx` endpoint |
| 9 | Testing & validation | data-quality, backend, frontend, demo |

Detailed, executable tasks are in [`02_bi_execution_tasks.md`](02_bi_execution_tasks.md).

---

## 15. Final recommendation for this PFE

1. **Do not rebuild the warehouse design.** Execute the existing
   `DATA_WAREHOUSE_REBUILD_TASK_ROADMAP.md` to materialize `staging`/`warehouse`/`mart`
   in PostgreSQL on the full 2024–2026 data. This roadmap's Phases 0–4 **wrap and
   sequence** that work, not replace it.
2. **Build the serving layer as a `bi` package inside `llm-orchestrator` first**
   (marts-only, own datasource, tested DTOs). Wire the five pages to it, removing
   placeholders one at a time.
3. **Extract a standalone Spring `bi-service` only after** marts + contract are stable
   (strangler-fig). Defer if the deadline is tight — the package boundary already
   delivers the architectural separation the defense needs.
4. **Keep export split:** PNG/PDF frontend; Excel/data from a backend reporting
   endpoint on the same marts. **No .NET** unless a concrete reporting mandate appears.
5. **Govern by reconciliation:** never repoint a dashboard KPI to a mart until a
   golden-number check matches the current `business`-schema value.

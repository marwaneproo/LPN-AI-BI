# BI Implementation Roadmap — Overview (real data into charts)

**Project:** LPN AI-BI (PFE) · **Branch:** `bi-polished-dashboard` · **Authored:** 2026-06-25
**Strategy chosen:** **Incremental slice first** (marts over the existing `business`
schema), full warehouse ETL deferred.
**Status:** Build phase. Unlike the planning roadmap (BI-00…BI-14, docs only), these
tasks **touch real code, the real database, and the real app.**

> Continuity log for THIS phase: [`context_bi_implementation.md`](context_bi_implementation.md)
> — read it before every task. The planning designs this phase executes live in the
> parent folder: `_design/08_mart_design.md`, `sql/marts.sql`, `_design/10_bi_api_contract.md`,
> `_design/11_extraction_decision.md`, `_plan/12_frontend_integration_plan.md`,
> `_design/13_export_reporting_design.md`, `_plan/14_testing_strategy.md`.

---

## 1. Goal

Get **real numbers from the live dataset** (354,910 order lines, etc.) onto the five
BI pages' charts — page by page, each step verified and reconciled — without breaking
the working app and without waiting on the full 6.5 GB warehouse rebuild.

## 2. The chosen architecture (interim → target)

```
NOW (interim, this roadmap):
  business.*  (real 2025–2026 data, already live, powers order_count=616)
       │  CREATE VIEW ... preserving the BI-08 mart column contract
       ▼
  mart.*  ──►  /v1/bi/*  (Spring `bi` package)  ──►  5 frontend pages (REAL charts)

LATER (deferred, IMPL-W — full governance):
  raw 6.5GB → staging → warehouse(star) → repoint mart.* view bodies (SAME columns)
  → API and frontend unchanged.
```

## 3. Locked decision for this phase (record in the continuity log)

**IMPL-DECISION-01 — Interim mart source = `business.*`.**
The BI-08 `sql/marts.sql` targets `warehouse.dim_*/fact_*` (not yet built). For the
slice we author mart **views over `business.*`** that expose the **exact same column
contract** defined in BI-08. The mart *contract* is the stable interface; the mart
*source* is interim. When the warehouse is materialized (IMPL-W), only the view
bodies change — `/v1/bi/*` and the frontend do not. This is a strangler-fig at the
data layer and reconciles trivially against `business` (mart = business by construction).

## 4. Hard safety rules (these tasks are NOT docs-only)

1. **Never modify or drop `business` or `app`.** Marts are **additive** `CREATE OR
   REPLACE VIEW` in a new `mart` schema. No `ALTER`/`DROP` on existing schemas.
2. **Keep the app working after every task.** The legacy `SalesDashboardController`
   (`/v1/sales-dashboard`, `/v1/sales-analysis`) stays live until its pages migrate.
   Run legacy and `/v1/bi/*` side by side.
3. **Reconcile before you expose.** No page is repointed to a mart until the mart
   value matches the `business` value (golden number, e.g. `order_count = 616`).
4. **One unit at a time, each on its own commit.** A task ends green (tests pass,
   app boots, numbers reconcile) or is marked BLOCKED.
5. **Idempotent DDL.** Every mart script is re-runnable (`CREATE SCHEMA IF NOT EXISTS`,
   `CREATE OR REPLACE VIEW`).
6. **Read-only serving.** The BI package connects as `lpn_ai_readonly` with
   `search_path=mart` (+ `warehouse` later). No write path, no `business` SQL in the
   BI package.
7. **Mandatory:** every task appends a milestone entry to
   `context_bi_implementation.md` (append-only; §template at the bottom of that file).

## 5. Phases & task map

| Phase | Tasks | Outcome |
|---|---|---|
| A — Mart layer (over `business`) | IMPL-01, IMPL-02 | `mart.*` views live in PostgreSQL, reconciled |
| B — BI serving API (`bi` package) | IMPL-03, IMPL-04 | `/v1/bi/*` returns real JSON from marts, tested |
| C — Frontend wiring (charts go real) | IMPL-05, IMPL-06 | 5 pages show real charts, placeholders gone |
| D — Export + cleanup | IMPL-07, IMPL-08 | backend `.xlsx`; legacy trimmed; demo green |
| W — Deferred: full warehouse | IMPL-W | repoint marts business→warehouse, same contract |

The first time a chart shows real data is **IMPL-05** (first page wired).

## 6. Model / effort guidance

You are running these on **GPT-5.5 high (Codex)**, which is appropriate throughout.
Equivalent Anthropic tags for reference: mart SQL and API tasks ≈ Sonnet 4.6 high;
frontend ≈ Sonnet 4.6 high with `/vercel-react-best-practices`. No task here needs
Opus-level escalation (the one architecture-critical decision, BI-11, is already done).

## 7. What "done" means for this roadmap

All five pages render real data from `mart.*` via `/v1/bi/*`; placeholders removed;
loading/empty/error states intact; Excel export is backend-side (PNG/PDF still
frontend); every exposed KPI reconciles to `business`; the app boots clean natively;
and `context_bi_implementation.md` holds a complete milestone trail. The full
warehouse (IMPL-W) may follow later without touching the API or the frontend.

## 8. Pre-flight (once, before IMPL-01)

- Stack running natively: `scripts/launch-local-native.ps1` (PG18 @ 5432, services up).
- Confirm the live baseline number to reconcile against:
  `GET http://localhost:8081/v1/sales-dashboard` → note `order_count` (expected 616),
  `invoiced_sales`, etc. These are the golden numbers for IMPL-01.
- Recommended: work on branch `bi-polished-dashboard` (current) or cut
  `bi-implementation` from it — your call; commit per task either way.

# LPN AI-BI — Machine Learning / Forecasting Task Roadmap (Prévisions)

Created on: 2026-06-15
Author of analysis: AI coding agent (Claude Opus 4.8)
Companion documents:
- `docs/architecture/AI_AGENT_TASKS_WEEKS_1_TO_4.md` — original foundations spec.
- `docs/tasks/AI_BI_OPTIMIZATION_TASK_ROADMAP.md` — Text-to-SQL/BI optimization roadmap.
- `docs/CONTEXT_JOURNAL.md` — running log; **update after every task**.

This roadmap covers the **Prévisions (forecasting / ML)** phase of the project. It is the
machine-learning counterpart to the existing BI roadmap. The goal is to ship a real,
backtested **monthly CA (chiffre d'affaires) forecast** that replaces the hardcoded mock on
the `/previsions` page, using the data that already exists — no further extraction required
for v1.

---

## 0. Operating Rules (same discipline as the rest of the project)

1. **One task at a time.** Complete it, run its acceptance tests, update
   `docs/CONTEXT_JOURNAL.md`, then **STOP and wait for human confirmation**.
2. **Do not break existing routes, API contracts, dashboards, or the AI-BI pipeline.**
   The forecasting feature is additive.
3. **Forecasting is deterministic ML, not LLM.** Charts and forecasts must NOT depend on the
   Ollama models, exactly like the existing `SalesDashboardService` deterministic SQL path.
4. **Snapshot → PostgreSQL stays the architecture.** The predictive service reads from
   PostgreSQL, never from Oracle and never from raw Excel at request time.
5. **Failed acceptance test → mark `BLOCKED` in the journal, stop.**
6. **No raw data / secrets committed.** Raw exports stay ignored as today.

### Stack additions (already chosen in the original spec — do not deviate)

| Component | Stack |
|---|---|
| Predictive service | Python 3.11 + FastAPI + **Prophet** + (optional) LightGBM |
| Storage | PostgreSQL 16 (`business` / dedicated forecast tables) |
| Frontend | Existing React 18 + Recharts |
| Port | Predictive: host `8085` → container `8000` (per original port map) |

---

## 1. Data Foundation (the facts that make this feasible)

The decisive input is the **24-month commercial raw export** in
`Youssef_Extractions/data/Exported_data_through_a_drive/` (also mirrored in
`.../Exported_LPN/`), files **53–58**:

| File | Grain | Rows |
|---|---|---|
| `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` | order header | 34,710 |
| `54_COMMERCIAL_ORDER_LINE_24M.xlsx` | order line (full product dims) | 854,645 |
| `55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` | invoice header (with `ISPAID`) | 10,315 |
| `56_COMMERCIAL_INVOICE_LINE_24M.xlsx` | invoice line | 511,242 |
| `57_COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` | customer dim | 970 |
| `58_COMMERCIAL_CUSTOMER_LOCATION_24M.xlsx` | customer geography | 1,043 |

**Profiled monthly invoiced-CA series (from file 55), 2024-06 → 2026-06, 25 months, 29 commercials:**

```
2024-07 32.6M   2025-07 44.8M     strong back-to-school peak (Jul–Sep)
2024-08 49.0M   2025-08 40.8M     both years; book/school distributor
2024-09 38.8M   2025-09 31.1M
2024-12  4.6M   2025-12  3.9M     winter trough repeats
2025-01  2.4M   2026-01  2.2M
2026-06  0.3M (partial month — exclude or flag)
```

This is a clean, strongly **yearly-seasonal** series with **two full cycles** → well suited to
Prophet/SARIMA. Dimensions available for drill-down: commercial (29), product category, theme,
type, collection, distributor, customer, city/region.

**Known data caveats (carry into modeling):**
- The **last month is partial** (`2026-06` cut mid-month). Drop or flag the trailing partial
  period before fitting.
- `57_...PORTFOLIO`'s pre-aggregated `ORDERED_CA_24M` / `INVOICED_CA_24M` are **unreliable**
  (many-to-many join inflation, per journal 2026-06-15). Re-aggregate from headers/lines
  yourself; do not trust those two columns.
- The app's **live PostgreSQL still holds the older short snapshot** (~4 months), NOT the 24M
  data. Task 1 below is what brings the long history in.
- Stock-rupture forecasting is **out of scope for v1**: there is only a single
  `fact_stock_snapshot`, not a stock-on-hand time series. Revisit in a later phase.

---

## Project-Level Validation Gate

Run after every task (same gate as the BI roadmap, plus predictive):

```powershell
# Frontend
cd D:\LPN_PROJECT\frontend; npm run build

# Java services
cd D:\LPN_PROJECT\services-java; .\gradlew.bat test

# Predictive service
cd D:\LPN_PROJECT\services-python\predictive; uv run pytest

# Python workspace (eval, sql-validator, etc.)
cd D:\LPN_PROJECT\services-python; uv run pytest
```

Acceptance: frontend build passes, Java tests pass, predictive tests pass, no route/API
contract removed, `git status --short` reviewed before commit.

---

## Task ML-1 — Build the monthly sales forecasting fact in PostgreSQL

Priority: Critical
Goal: Turn the 24-month raw exports into a clean, reproducible monthly time-series fact in
PostgreSQL that the predictive service can query.

### Why
Forecasting needs a long, clean history at a stable grain. The 24M files have it; the live DB
does not. We follow the existing `scripts/import-vente-bi-enrichment.py` pattern: a Python
script that reads the exports, cleans them, and loads additive tables into PostgreSQL without
disturbing the canonical vente snapshot.

### Step-by-Step
1. Create `scripts/build-forecast-monthly-fact.py` (mirror the style/CLI of
   `scripts/import-vente-bi-enrichment.py`: argparse, pandas, psycopg2, manifest output under
   `Youssef_Extractions/forecast_monthly_import/`).
2. Read from `Youssef_Extractions/data/Exported_data_through_a_drive/`:
   - `55_COMMERCIAL_INVOICE_HEADER_24M.xlsx` (CA facturé, `ISPAID`)
   - `53_COMMERCIAL_ORDER_HEADER_24M.xlsx` (CA commandé)
   - `56_COMMERCIAL_INVOICE_LINE_24M.xlsx` + `54_COMMERCIAL_ORDER_LINE_24M.xlsx`
     (for category/theme grain)
3. Aggregate to **month grain** and produce these tables in the `business` schema (read-only
   role already gets SELECT on future `business` tables):
   - `business.fact_sales_monthly` — columns: `month` (date, first of month), `ca_facture`,
     `n_factures`, `ca_commande`, `n_commandes`, `ca_facture_paye`, `ca_facture_impaye`.
   - `business.fact_sales_monthly_by_commercial` — `month, salesrep_id, commercial_name,
     ca_facture, n_factures`.
   - `business.fact_sales_monthly_by_category` — `month, category_id, category_name,
     ca_facture`.
   - `business.fact_sales_monthly_by_theme` — `month, theme_id, theme_name, ca_facture`.
4. Drop/flag the trailing partial month (`is_partial` boolean column, or exclude when the
   month is not yet complete). Do NOT silently include a half month.
5. Use a transaction; re-grant `lpn_ai_readonly` USAGE/SELECT after creating tables (same as
   the enrichment import does).
6. Write a manifest (`row counts`, `month range`, `source files`) to the import folder.

### Task-Level Tests
```powershell
python scripts\build-forecast-monthly-fact.py
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "select count(*), min(month), max(month) from business.fact_sales_monthly;"
docker exec lpn-ai-bi-postgres-1 psql -U postgres -d lpn_ai_bi -c "select month, ca_facture, n_factures from business.fact_sales_monthly order by month;"
```
Acceptance:
- `fact_sales_monthly` has ~24 complete months (partial month excluded/flagged).
- Monthly `ca_facture` totals reconcile (±0.5%) with a direct re-aggregation of file 55.
- Read-only role can SELECT the new tables; cannot INSERT.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-2 — Stand up the predictive FastAPI service skeleton + DB access

Priority: Critical
Goal: Turn the `predictive` stub into a real service with config, PostgreSQL access, and a
`/health` that reports data availability. No model yet.

### Why
The service currently is a bare `/health`. It is not in Docker Compose and has no dependencies.
Get the plumbing right before adding Prophet.

### Step-by-Step
1. Update `services-python/predictive/pyproject.toml` deps: add `pandas`, `sqlalchemy`,
   `psycopg2-binary`, `pydantic-settings`. (Add `prophet` in ML-3, not here — it has a heavy
   build; keep this task fast.)
2. Add config via env vars: `DATABASE_URL` (read-only role), `FORECAST_TABLE` defaults.
3. Add a `data.py` module: `load_monthly_ca(grain, key=None) -> DataFrame` reading
   `business.fact_sales_monthly*` via SQLAlchemy.
4. Extend `/health` to report: DB reachable ✓, monthly fact row count, month range.
5. Add the service to `docker-compose.yml` as `predictive` (host `8085` → container `8000`),
   `depends_on: postgres`, with a Dockerfile mirroring `schema-retrieval`'s.
6. Add `tests/test_health.py` (FastAPI `TestClient`, DB mocked).

### Task-Level Tests
```powershell
cd D:\LPN_PROJECT\services-python\predictive; uv run pytest -v
docker compose up -d --build predictive
curl http://localhost:8085/health
```
Acceptance: `/health` returns status ok with month count/range; service builds and runs in
Compose; tests pass.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-3 — Prophet monthly CA forecast endpoint

Priority: Critical
Goal: A real forecast endpoint over total monthly CA facturé.

### Why
This is the core deliverable: a seasonal forecast the frontend can chart.

### Step-by-Step
1. Add `prophet` to predictive deps. Pin the version; document the build in the journal.
2. Add `forecast.py`: fit Prophet on `fact_sales_monthly` (yearly seasonality on; weekly/daily
   off — monthly grain), exclude the partial month, return forecast with `yhat`,
   `yhat_lower`, `yhat_upper`.
3. Endpoint `GET /v1/forecast/ca`:
   - params: `grain=company|commercial|category|theme` (default `company`),
     `key` (id when grain≠company), `horizon` (months, default 6, max 12).
   - response: `{ history: [{month, actual}], forecast: [{month, yhat, lower, upper}],
     model: "prophet", grain, key, generated_at }`.
4. Guard small series: if a grain/key has < 12 months of data, return a clear
   `insufficient_history` message instead of a bad forecast.
5. Cache fitted models briefly in-memory (the fact only changes on re-import).
6. Tests: company forecast returns `horizon` future points with `lower <= yhat <= upper`;
   insufficient-history path returns the guard response.

### Task-Level Tests
```powershell
cd D:\LPN_PROJECT\services-python\predictive; uv run pytest -v
docker compose up -d --build predictive
curl "http://localhost:8085/v1/forecast/ca?grain=company&horizon=6"
curl "http://localhost:8085/v1/forecast/ca?grain=commercial&key=<salesrep_id>&horizon=6"
```
Acceptance: company forecast captures the Jul–Sep seasonal peak; response shape is stable;
commercial/category drill-down works or returns the guard.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-4 — Backtest & accuracy report (PFE credibility)

Priority: High
Goal: Quantify forecast quality with a hold-out backtest. This number goes in the PFE report.

### Why
A forecast with no measured error is not defensible academically. Hold out the last N months,
predict them, compare to actuals.

### Step-by-Step
1. Add `backtest.py`: rolling-origin or simple hold-out (train on all but last 3–6 months,
   forecast them, compute **MAPE, MAE, RMSE**) at company grain, and per commercial where
   history allows.
2. Add `GET /v1/forecast/backtest?grain=...` returning the metrics + per-month actual vs.
   predicted.
3. Add a small offline reporting helper that writes `services-python/reports/forecast-backtest-YYYYMMDD.csv`
   (mirror the eval reports convention).
4. Optionally compare Prophet vs. a naive seasonal baseline (last-year-same-month) so the
   report shows the model beats naive.
5. Document the headline MAPE in the journal and in the PFE (`Rapport_PFE`).

### Task-Level Tests
```powershell
cd D:\LPN_PROJECT\services-python\predictive; uv run pytest -v
curl "http://localhost:8085/v1/forecast/backtest?grain=company"
```
Acceptance: backtest returns finite MAPE/MAE/RMSE; Prophet beats the naive seasonal baseline
at company grain (or the gap is explained in the journal).

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-5 — Orchestrator proxy (optional but recommended)

Priority: Medium
Goal: Expose forecasting through the same gateway the frontend already uses, consistent with
`/v1/sales-dashboard`.

### Why
Keeps the frontend talking to one backend origin and lets traces/auth be added later, matching
existing patterns. Alternatively the frontend may call `8085` directly if simpler — decide and
record the choice.

### Step-by-Step
1. In `llm-orchestrator`, add `ForecastController` + `ForecastClient` (RestClient/WebClient to
   `http://predictive:8000`), mirroring `SchemaRetrievalHttpClient` / `SalesDashboardController`.
2. `GET /v1/forecast/ca` and `GET /v1/forecast/backtest` proxy through with the same params.
3. Add unit tests with the predictive client mocked (mirror existing `*ControllerTest`).
4. Keep response schema identical to the predictive service so the frontend has one contract.

### Task-Level Tests
```powershell
cd D:\LPN_PROJECT\services-java; .\gradlew.bat :llm-orchestrator:test
curl "http://localhost:8081/v1/forecast/ca?grain=company&horizon=6"
```
Acceptance: orchestrator proxy returns the same payload as the predictive service; existing
Java tests still pass.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-6 — Replace the Prévisions mock with a real forecast UI

Priority: High
Goal: Turn `ForecastsPage` (App.tsx) from a hardcoded mock into a real, interactive forecast
view.

### Why
The page currently shows a fake "Risque de rupture" list. Ship the real CA forecast the data
supports.

### Step-by-Step
1. Replace `ForecastsPage` mock content with:
   - A grain selector (`Entreprise` / `Commercial` / `Type d'article` / `Thématique`) and a
     horizon selector (3 / 6 / 12 mois).
   - A Recharts chart: solid line = historical `ca_facture`, dashed line = `yhat`, shaded band
     = `[yhat_lower, yhat_upper]`.
   - KPI cards: next-month forecast, next-3-months total, YoY vs. same period last year, model
     MAPE (from backtest).
2. Add an API helper `fetchForecast(grain, key, horizon)` next to existing helpers, calling the
   orchestrator (or `8085` per ML-5 decision).
3. Loading / error / insufficient-history states (reuse existing patterns).
4. Keep French business labels (`CA facturé`, `Prévision`, `Intervalle de confiance`).
5. Do not add a new sidebar item — `/previsions` already exists.

### Task-Level Tests
```powershell
cd D:\LPN_PROJECT\frontend; npm run build
```
Manual: open `/previsions`, switch grain/horizon, confirm chart shows history + forecast band
and the seasonal peak; confirm no console errors.

Acceptance: page renders real data; existing routes unaffected; build passes.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

---

## Task ML-7 — Documentation, journal, and PFE write-up

Priority: Medium
Goal: Capture the ML phase so it's defensible and reproducible.

### Step-by-Step
1. Add `docs/forecasting/README.md`: data source (files 53–58), fact build, model choice
   (Prophet + why seasonality fits), endpoints, backtest method, headline accuracy, known
   limitations (partial month, stock-rupture deferred).
2. Update `docs/schema_metadata.csv` so the AI-BI pipeline can also retrieve the new
   `fact_sales_monthly*` tables (consistency with BI roadmap Task 7).
3. Update `docs/CONTEXT_JOURNAL.md` after each task as usual.
4. Add a Prévisions/forecasting section to `Rapport_PFE` with the methodology and MAPE.

### Acceptance
- README exists and a new reader can reproduce the forecast from raw files.
- Journal reflects all ML tasks done.
- PFE has a forecasting section with measured accuracy.

**Stop.**

---

## Recommended Execution Order

1. ML-1 — Monthly forecasting fact in PostgreSQL.
2. ML-2 — Predictive service skeleton + DB access.
3. ML-3 — Prophet monthly CA forecast endpoint.
4. ML-4 — Backtest & accuracy report.
5. ML-5 — Orchestrator proxy.
6. ML-6 — Prévisions UI.
7. ML-7 — Docs, journal, PFE.

## Final Success Criteria

- `/previsions` shows a real, backtested monthly CA forecast with seasonal peak, drill-down by
  commercial/category/theme, and confidence intervals.
- The forecast is deterministic (no LLM) and served from PostgreSQL-backed facts.
- Forecast accuracy (MAPE) is measured, beats a naive seasonal baseline, and is documented in
  the PFE.
- No existing route, API contract, dashboard, or AI-BI behavior is broken.

## Deferred / Future Phases (not v1)

- **Per-product / per-SKU demand forecasting** (from the 854k order lines) — noisier; LightGBM
  with product features is the natural tool.
- **Stock-rupture risk** — needs a historical stock-on-hand time series (periodic stock
  exports) that does not exist yet; until then only a demand-vs-current-on-hand proxy is
  possible.
- **Customer churn / next-order propensity** — `57_PORTFOLIO` + RFM features.
- **Driver-based forecasting** (price, discount, promotions) once those signals are modeled.

---

## Task ML-9 — Model Bake-off (train/test comparison of 5 models)

Priority: High (PFE credibility)
Goal: Replace the assertion "Prophet is a good fit" with empirical proof. Train 5 forecasting
models under an identical chronological train/test protocol and pick the winner by measured
error.

### Why
A defensible PFE shows that the production model was chosen by benchmarking, not by reputation.
With only 24 monthly points and strong calendar seasonality, the expectation is that simple
statistical models (seasonal-naive, ETS, Prophet) beat heavy ML (LightGBM) — and demonstrating
that is itself a strong result about matching model complexity to data size.

### Models (exactly these five)
1. **Seasonal-naive (lag-12)** — baseline; reuse the logic already in `backtest.py`.
2. **ETS / Holt-Winters** — `statsmodels` `ExponentialSmoothing`, multiplicative seasonality,
   `seasonal_periods=12`, no trend (and a damped-trend variant); i.e. ETS(A,N,M)/(A,Ad,M).
3. **SARIMA** — `statsmodels` `SARIMAX` with a fixed sensible seasonal order (e.g.
   `(1,1,1)(1,1,0,12)`); small grid allowed, no new heavy auto-arima dependency.
4. **Prophet (tuned)** — reuse `_fit_prophet` with the production config (multiplicative,
   yearly_fourier_order=5).
5. **LightGBM** — `lightgbm` with lag features (lag-1, lag-12) + month-of-year calendar features.

### Validation protocol (identical for all models)
- **Primary split:** chronological 70/30 — train on the oldest ~70% of complete months, test on
  the most recent ~30%. Never random (random split leaks the future).
- **Plus rolling-origin cross-validation:** expanding window, forecast the next 3 months each
  fold, average WAPE across the (limited) folds. Be transparent that only ~2 cycles exist.
- **Metrics (reuse `_metrics` from `backtest.py`):** WAPE (headline), MAE, RMSE, MAPE.
- Company grain is the deliverable.

### Deliverables
- `predictive/bakeoff.py` with one wrapper per model returning predictions for given train/test.
- `GET /v1/forecast/bakeoff?grain=company` → per-model metrics table + per-month predictions +
  the WAPE-ranked winner.
- `services-python/reports/model-bakeoff-YYYYMMDD.csv` (mirror the reports convention).
- A written recommendation in the journal (winner + numbers + honest commentary).
- **Do NOT change the production `/v1/forecast/ca` model in this task** — comparison +
  recommendation only; the human decides whether to switch.

### Acceptance
- All 5 models return finite WAPE/MAE/RMSE on the 70/30 split; seasonal-naive present as baseline.
- The bake-off endpoint returns a ranked table and names a winner by WAPE.
- The CSV report and journal entry record the numbers honestly (including CV-fold limitations).
- Full Project-Level gate passes; no existing route/model changed.

### Project-Level Tests
Run the gate.

**Stop. Wait for confirmation.**

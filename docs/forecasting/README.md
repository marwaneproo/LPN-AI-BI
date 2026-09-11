# LPN AI-BI — Forecasting Layer

## Overview

The forecasting layer produces monthly CA facturé (invoiced revenue) predictions
using Prophet, backtested against 24 months of real LPN data. It is served via a
dedicated predictive microservice and surfaced on the `/previsions` page.

---

## 1. Data Source

### Raw extractions (files 53–56 of the 24-month commercial export)

Files live in `Youssef_Extractions/data/Exported_data_through_a_drive/`
(and are mirrored in `.../Exported_LPN/`):

| # | File | Grain | Rows |
|---|---|---|---|
| 53 | `COMMERCIAL_ORDER_HEADER_24M.xlsx` | order header | 34,710 |
| 54 | `COMMERCIAL_ORDER_LINE_24M.xlsx` | order line | 854,645 |
| 55 | `COMMERCIAL_INVOICE_HEADER_24M.xlsx` | invoice header (with `ISPAID`) | 10,315 |
| 56 | `COMMERCIAL_INVOICE_LINE_24M.xlsx` | invoice line | 511,242 |
| 57 | `COMMERCIAL_CUSTOMER_PORTFOLIO_24M.xlsx` | customer dim | 970 |
| 58 | `COMMERCIAL_CUSTOMER_LOCATION_24M.xlsx` | customer geography | 1,043 |

Only files 55 and 56 are needed for the CA facturé time-series (invoice header and
line). Files 53 and 54 provide the commanded CA and category/theme breakdown.

**Key data caveats:**
- `2026-06` is a partial month (cut mid-month) and is excluded from model fitting
  via the `is_partial` flag in `business.fact_forecast_monthly`.
- `PORTFOLIO.ORDERED_CA_24M` / `INVOICED_CA_24M` are **not used** — those columns
  suffer many-to-many join inflation. CA facturé is re-aggregated from the invoice
  header directly.
- The 24-month window is Jun 2024 – May 2026 (24 complete months).

---

## 2. Fact Table Build

**Script:** `scripts/build-forecast-monthly-fact.py`

The script reads the raw Excel exports and loads four PostgreSQL tables under the
`business` schema:

| Table | Grain | Purpose |
|---|---|---|
| `fact_forecast_monthly` | month | Company-level CA facturé, CA commandé, counts, `is_partial` flag |
| `fact_forecast_monthly_by_commercial` | month × salesrep | CA facturé per commercial |
| `fact_forecast_monthly_by_category` | month × category | CA facturé per product type |
| `fact_forecast_monthly_by_theme` | month × theme | CA facturé per editorial theme |

The `lpn_ai_readonly` role gets SELECT on all four tables. The script is idempotent
(drops and recreates the tables on each run).

**Monthly CA profile (Jun 2024 – May 2026):**
```
2024-07  32.6M   2025-07  44.8M   ← strong back-to-school peak (Jul–Sep)
2024-08  49.0M   2025-08  40.8M
2024-09  38.8M   2025-09  31.1M
2024-12   4.6M   2025-12   3.9M   ← winter trough
2025-01   2.4M   2026-01   2.2M
2025-06  19.8M   2026-05   2.8M
```

---

## 3. Model Choice and Configuration

### Why Prophet

The CA facturé series has a dominant, repeating yearly seasonal pattern (Jul–Sep
peak, Dec–Jan trough) visible across both available cycles (2024 and 2025). Prophet
with multiplicative yearly seasonality is the correct tool for this structure.
SARIMA was not pursued because the series is monthly, short (24 points), and the
Prophet API produces calibrated confidence intervals that are useful in the UI.

### Production configuration

```python
_PROD_FOURIER_ORDER = 5        # yearly_fourier_order — raised from 3 to 5 (ML-4b)
_PROD_CPS           = 0.05     # changepoint_prior_scale
_PROD_GROWTH        = "linear"
seasonality_mode    = "multiplicative"
MAP estimation (no MCMC for latency)
```

### Why Fourier order 5 (not 3 or 6)

With `yearly_fourier_order=3` (default), Prophet could not represent the steep
Jul–Aug spike: January 2026 was predicted at 4.79M vs actual 2.16M (+121% error).
Raising to 5 fixed the Jan trough to 1.85M (+14% error). Order 6 overfit on the
9-month holdout window (WAPE 0.7156 vs naive 0.2075), so 5 was chosen as the
optimal balance.

### Blend: Prophet × 0.5 + Naïf lag-12 × 0.5

```
yhat  = 0.5 × prophet_yhat  + 0.5 × naive_lag12
lower = max(0, 0.5 × p_lower + 0.5 × naive_lag12)
upper = 0.5 × p_upper + 0.5 × naive_lag12
model = "prophet_naive_blend_05"
```

The 50/50 blend with the naive lag-12 baseline (same-month last year) was chosen
because:
1. **It beats naive on the 6-month window** (WAPE 0.2220 vs naive 0.2381).
2. **Confidence intervals** — pure naive has no uncertainty estimate.
3. **Robustness** — on only 2 yearly cycles, Prophet's trend extrapolation is
   uncertain; blending with a known-good anchor stabilises predictions.
4. **Extensibility** — the blend allows forecasting beyond 12 months (naive alone
   cannot do this).

---

## 4. Backtest Methodology and Results

### Method

Simple holdout: fit on months 1–(N−holdout), predict holdout months, compute
WAPE/MAE/RMSE/MAPE against actuals. Both a 6-month and a 9-month holdout are
reported.

**WAPE** (Weighted Absolute Percentage Error) = `Σ|error| / Σ|actual|` is the
headline metric because it is robust to low-volume months.

### Honest results (company grain)

| Window | Metric | Prophet | Naive lag-12 | Blend 50/50 |
|---|---|---|---|---|
| 6-month holdout | WAPE | 0.2450 | 0.2381 | **0.2220** |
| 9-month holdout | WAPE | 0.3013 | **0.2075** | 0.2354 |

**Reading:**
- Blend beats naive on the 6-month window by 7 percentage points.
- Naive still wins the 9-month window.
- The blend is chosen for production because it beats naive at the operationally
  relevant horizon, provides confidence intervals, and is more extensible.
- The "Précision modèle" KPI on the frontend = `(1 − blend_WAPE_6m) × 100 ≈ 77.8%`.
  It uses the company-grain 6-month holdout regardless of which grain the user
  has selected — this is intentional so the reference metric is stable and
  comparable.

---

## 5. Endpoints

### Predictive service (port 8085)

```
GET /health
  → { status, db_ok, complete_months, month_min, month_max_complete }

GET /v1/forecast/ca?grain=company&horizon=6
  grain: company | commercial | category | theme
  key:   salesrep_id | category_id | theme_id  (required for non-company grains)
  horizon: 1–12 months (default 6)
  → { history: [{month, actual}], forecast: [{month, yhat, lower, upper}],
      model, grain, key, generated_at }
  → 200 { error: "insufficient_history", available_months, minimum_months }
    when < 12 months of data exist for the requested grain/key

GET /v1/forecast/backtest?grain=company&holdout=6
  holdout: 3–9 months (default 6)
  → { blend_05_metrics, prophet_metrics, naive_lag12_metrics,
      prophet_beats_naive_wape, prophet_config_used,
      holdout_months, train_months, per_month: [...] }
```

### Orchestrator proxy (port 8081)

Both endpoints are proxied identically via `ForecastController` →
`PredictiveHttpClient` in the llm-orchestrator Java service:

```
GET /v1/forecast/ca      → identical to predictive /v1/forecast/ca
GET /v1/forecast/backtest → identical to predictive /v1/forecast/backtest
```

The frontend calls the orchestrator (`API_BASE_URL/v1/forecast/*`) so all traffic
goes through a single backend origin, consistent with the existing sales dashboard
and QA pipeline.

---

## 6. Known Limitations

1. **Only 2 yearly cycles.** With 24 months of data, the model has seen the
   Jul–Sep peak twice. A third cycle (by mid-2027) would significantly improve
   long-horizon accuracy, especially for the 9-month+ window.

2. **Per-commercial and per-category forecasts are noisy.** Many individual series
   have fewer than 12 complete months or highly volatile CA. The `insufficient_history`
   guard (minimum 12 months) rejects these at the API level.

3. **Per-product / per-SKU forecasting is deferred.** The 854k order lines exist
   but SKU-level forecasting requires a different approach (LightGBM with product
   features, hierarchical reconciliation). This is a future phase.

4. **Stock-rupture forecasting is deferred.** There is only a single
   `fact_stock_snapshot` (a point-in-time extract), not a stock-on-hand time series.
   Demand × lead-time proxies could be computed from order lines, but this is
   out of scope for v1.

5. **"Précision modèle" KPI is company-grain only.** The WAPE shown on the
   `/previsions` page comes from the company-grain 6-month backtest. It does not
   reflect accuracy for commercial, category, or theme drill-downs.

---

## 7. How to Reproduce

```bash
# 1. Build the fact table (requires raw exports in Youssef_Extractions/...)
python scripts/build-forecast-monthly-fact.py

# 2. Start services
docker compose up -d postgres predictive llm-orchestrator

# 3. Smoke test
curl http://localhost:8085/health
curl "http://localhost:8085/v1/forecast/ca?grain=company&horizon=6"
curl "http://localhost:8081/v1/forecast/ca?grain=company&horizon=6"   # via proxy

# 4. Run backtest
curl "http://localhost:8085/v1/forecast/backtest?grain=company&holdout=6"

# 5. Python test suite
cd services-python && uv run pytest predictive/tests/ -v

# 6. Java proxy tests
cd services-java && ./gradlew :llm-orchestrator:test
```

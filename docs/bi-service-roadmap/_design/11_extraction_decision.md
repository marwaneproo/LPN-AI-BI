# BI-11 - BI extraction decision

Status: DRAFT DECISION ONLY
Date: 2026-06-25
Scope: decide the BI serving boundary; do not create services, move code, edit
Gradle, edit Docker Compose, or run PostgreSQL changes.

## Recommendation

Build the next BI serving implementation as a clean `bi` package inside
`llm-orchestrator` now, then extract that package into a standalone Spring
`bi-service` later after objective readiness gates pass.

This is a deadline-aware strangler-fig decision. The dedicated BI microservice
is still the target architecture, but it should not be created as a new
deployable yet. For the July PFE deadline, a solo developer gets more value from
stabilizing marts, implementing the BI-10 contract, and proving dashboard
queries than from adding another service, port, Docker image, launcher branch,
and datasource configuration before the database layer is built in PostgreSQL.

The decision is reversible by design: keep all new BI code in a package that
does not import LLM orchestration classes, give it its own read-only datasource,
static mart repositories, DTOs, tests, and controller boundary, then lift that
package into `services-java/bi-service` once the acceptance criteria below are
met.

## Evidence inspected

| Area | Real files inspected | Findings used for the decision |
| --- | --- | --- |
| Current BI dashboard code | `services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/SalesDashboardController.java`, `SalesDashboardService.java` | Legacy endpoints are already inside `llm-orchestrator`: `/v1/sales-dashboard` and `/v1/sales-analysis`. `SalesDashboardService` mixes raw SQL, metric logic, query builders, DTO records, and the `SqlExecutorClient` transport in the root package. |
| Forecast and predictive proxy | `ForecastController.java`, `PredictiveClient.java`, `PredictiveHttpClient.java` | Forecast endpoints are also proxied through `llm-orchestrator`; the HTTP client is wired by `predictive.base-url` and points at the separate predictive service. This is orchestration/proxy code, not a clean BI read model. |
| Orchestrator config | `services-java/llm-orchestrator/src/main/resources/application.yml`, `services-java/llm-orchestrator/build.gradle.kts` | The service runs on port 8080 in-container, exposed as 8081 by Compose. It already has Spring JDBC and an app-admin datasource for application concerns, plus external URLs for SQL executor, schema retrieval, and predictive. It does not yet have a BI-specific read-only mart datasource. |
| SQL executor read-only pattern | `services-java/sql-executor/src/main/java/com/lpn/aibi/sqlexecutor/DataSourceConfig.java`, `SqlExecutionService.java`, `SqlExecutionController.java`, `services-java/sql-executor/src/main/resources/application.yml` | `sql-executor` provides the reusable pattern: an app-admin datasource, a separate read-only datasource, a qualified read-only `JdbcTemplate`, query timeout, and error mapping. BI should reuse this shape with static mart SQL, not dynamic SQL validation. |
| Java multi-project setup | `services-java/settings.gradle.kts` | Only `llm-orchestrator` and `sql-executor` are included today. A standalone `bi-service` would need a new Gradle project include, build file, Dockerfile, and local/deployment wiring. |
| Runtime wiring | `docker-compose.yml`, `scripts/launch-local-native.ps1` | Existing local stack already wires Postgres 5433, orchestrator 8081, sql-executor 8082, schema-retrieval 8084, predictive 8085, sql-validator 8086, and frontend 5173. A new BI deployable would add another port and launcher path before the marts exist in PostgreSQL. |
| Architecture roadmap | `docs/bi-service-roadmap/01_bi_architecture_vision.md` Section 8 and Section 15 | The roadmap explicitly recommends package-first inside `llm-orchestrator`, then standalone extraction after marts and contract stabilize. |
| API contract | `docs/bi-service-roadmap/_design/10_bi_api_contract.md` | The future `/v1/bi/*` contract is marts-only and supersedes legacy sales endpoints later, but it is still a design artifact and not implemented. |

## Current coupling diagnosis

The current dashboard code is not ready to lift into a standalone service as-is.
The problem is not that it lives in `llm-orchestrator`; the problem is that it
does not yet have a clean BI boundary.

`SalesDashboardController` exposes the legacy `/v1/sales-dashboard` and
`/v1/sales-analysis` endpoints directly from the root package
`com.lpn.aibi.llmorchestrator`. `SalesDashboardService` is a large root-package
service that owns raw table SQL, filter logic, metric calculation, ranking
queries, response records, and calls to `SqlExecutorClient`. The SQL still reads
operational/business tables such as orders, invoices, order lines, invoice
lines, products, customers, suppliers, and related lookup tables. That is useful
for the current prototype, but it conflicts with the BI-10 rule that the next BI
API reads only `mart.*` objects.

`ForecastController` and `PredictiveHttpClient` are also inside
`llm-orchestrator`, but they are separate orchestration/proxy concerns. They call
the predictive service through `predictive.base-url` and should not be pulled
into the BI package unless a future contract explicitly defines marts-backed
forecast responses. BI-10 does not require that.

The right near-term fix is therefore not a standalone service; it is a package
boundary that stops the coupling from spreading.

## Decision matrix

| Option | Deadline fit | Operational cost | Technical risk | Decision |
| --- | --- | --- | --- | --- |
| Clean `bi` package inside `llm-orchestrator` now | High. Keeps one existing deployable and one frontend API origin while the BI contract is implemented. | Low. Reuses the current Java app, port 8081 exposure, logging, build, and launcher. Adds only package-local datasource config later. | Medium but controllable: the package must be guarded against imports from LLM/orchestration code. | Recommended now. |
| Standalone Spring `bi-service` now | Low. Adds service creation, Gradle include, Dockerfile, Compose service, native launcher process, port selection, health checks, and frontend/API routing before marts are live. | Higher. Existing stack already uses Postgres 5433, orchestrator 8081, sql-executor 8082, schema-retrieval 8084, predictive 8085, sql-validator 8086, and frontend 5173. | High for the deadline: risks moving immature raw-SQL dashboard logic instead of implementing the marts-only contract. | Defer until gates pass. |
| Keep extending root-package `SalesDashboardService` | Short-term easy but strategically bad. | Low now, expensive later. | High: preserves raw table coupling and makes extraction harder. | Reject. |

## Package-first boundary

When implementation begins, create a package under:

`services-java/llm-orchestrator/src/main/java/com/lpn/aibi/llmorchestrator/bi`

The package must own the future `/v1/bi/*` contract from BI-10. It must not
replace or mutate the legacy `SalesDashboardController` during the extraction
decision task; later implementation can run legacy and new endpoints side by
side until frontend migration is complete.

### Allowed package contents

| Layer | Package path | Responsibility |
| --- | --- | --- |
| API controllers | `com.lpn.aibi.llmorchestrator.bi.api` | Controllers for BI-10 endpoints: `/v1/bi/overview`, `/v1/bi/orders`, `/v1/bi/revenue`, `/v1/bi/articles`, `/v1/bi/clients`, `/v1/bi/commercial`, `/v1/bi/analysis`. |
| DTOs and request models | `com.lpn.aibi.llmorchestrator.bi.api.dto` | Response DTOs, filter/query parameter records, pagination metadata, and error response DTOs aligned with BI-10 field names. |
| Application services | `com.lpn.aibi.llmorchestrator.bi.application` | Page-level orchestration over mart repositories, filter normalization, period handling, comparison-window handling, and cache key construction. |
| Mart repositories | `com.lpn.aibi.llmorchestrator.bi.infrastructure.mart` | Static SQL queries against `mart.*` only. One repository per mart or per page, whichever keeps mapping clearer. |
| Read-only datasource | `com.lpn.aibi.llmorchestrator.bi.infrastructure.config` | A BI-specific read-only datasource and qualified `JdbcTemplate`, modeled after `sql-executor`'s `DataSourceConfig`, with search path `mart,warehouse` and the `lpn_ai_readonly` user. |
| Error mapping | `com.lpn.aibi.llmorchestrator.bi.api` or `.api.error` | BI API error envelope from BI-10: validation, unavailable mart, timeout, and internal errors. |
| Tests | `services-java/llm-orchestrator/src/test/java/com/lpn/aibi/llmorchestrator/bi` | Controller contract tests, service tests, repository SQL tests with fixtures, and an architecture/import boundary test. |

### Boundary rules

The `bi` package is allowed to import Spring, Jackson, JDBC, validation,
observability utilities, and package-local BI classes.

The `bi` package must not import or call:

- `SqlGenerationService`, `SqlGenerationController`, `SqlExecutorClient`, or
  `SqlExecutorHttpClient`.
- `QaService`, `QaController`, `AnswerNarrationService`, or LLM model
  configuration.
- `SchemaRetrievalClient`, `SchemaRetrievalHttpClient`, or schema-retrieval DTOs.
- `QuestionIntentDetector`, prompt templates, chat/session classes, or saved SQL
  orchestration.
- `PredictiveClient` or `PredictiveHttpClient` unless a later accepted API
  contract explicitly adds predictive outputs to BI.
- Any SQL over raw files, staging tables, `business.*`, or operational tables.

All BI API data access must be static, reviewed SQL over BI-08 mart objects in
the `mart` schema. Warehouse tables can appear only as implementation support
for mart ownership or diagnostics if a future design explicitly approves that;
the public BI API DTO fields must continue to trace to named mart columns.

## Datasource design to reuse later

Use the `sql-executor` read-only datasource pattern, but do not reuse the
dynamic SQL executor path for BI dashboards.

The later implementation should add a BI-specific datasource such as:

- `bi.datasource.readonly.url`, defaulting to a PostgreSQL URL with
  `search_path=mart,warehouse` and statement timeout.
- `bi.datasource.readonly.username`, defaulting to `lpn_ai_readonly`.
- `bi.datasource.readonly.password`.
- A qualified `biReadonlyJdbcTemplate`.
- A dashboard query timeout and row cap aligned with BI-10.

This keeps deterministic dashboard reads independent from `SqlExecutorClient`
and `sql-validator`. The SQL executor remains the natural service for ad hoc
NL-to-SQL and analyst query execution. BI dashboards should use named, static,
contract-tested queries against marts.

## Legacy endpoint migration intent

The legacy endpoints stay in place until a later implementation task migrates
the frontend:

- Current legacy endpoint: `GET /v1/sales-dashboard`.
- Current legacy endpoint: `GET /v1/sales-analysis`.
- Future BI-10 endpoints: `GET /v1/bi/*`.

The new package should implement the BI-10 contract as a consistent superset,
then BI-12 can repoint frontend pages to `/v1/bi/*`. Only after frontend
migration and parity checks should legacy `SalesDashboardController` and
`SalesDashboardService` be retired or reduced to compatibility adapters.

## Later standalone extraction acceptance criteria

Do not create `services-java/bi-service` until all of these criteria are true.
Each criterion is objectively checkable.

| ID | Criterion | Objective check |
| --- | --- | --- |
| AC-01 | All BI-08 mart objects exist in PostgreSQL and are populated. | Database inventory shows all nine `mart.mart_*` objects exist, mandatory marts have `row_count > 0`, and the validation report from BI-09/BI-14 is checked in. |
| AC-02 | Mart financial reconciliation is accepted. | Validation report shows order/invoice CA totals reconcile from raw source to warehouse to mart within the agreed tolerance, with TRAP-01 source exclusions and CA source rules honored. |
| AC-03 | BI-10 contract is frozen for implementation. | `docs/bi-service-roadmap/_design/10_bi_api_contract.md` has no open widget gaps, every DTO business field maps to a named mart column, and frontend BI-12 has no extra unmapped fields. |
| AC-04 | All `/v1/bi/*` endpoints are implemented in the package boundary. | Endpoints from BI-10 exist under `com.lpn.aibi.llmorchestrator.bi.api`; legacy `/v1/sales-dashboard` and `/v1/sales-analysis` are no longer used by BI pages. |
| AC-05 | The package has zero imports from LLM/orchestration code. | An architecture test or `rg` check confirms no imports from root orchestration classes such as SQL generation, QA, schema retrieval, predictive proxy, prompts, chat/session, or `SqlExecutorClient`. |
| AC-06 | BI data access is independently read-only. | The package uses only the BI read-only datasource/JdbcTemplate, connects as `lpn_ai_readonly`, and has no app-admin datasource or SQL executor dependency for dashboard reads. |
| AC-07 | Test coverage is sufficient to move. | BI package has controller contract tests for every `/v1/bi/*` endpoint, service tests for filters and comparison periods, repository SQL tests for all mart-backed queries, and at least 80% line coverage for the BI package or an equivalent explicit coverage gate. |
| AC-08 | Performance target is known. | BI-14 latency checks show p95 <= 300 ms for overview/revenue/analysis on local dataset, or materialized mart/index work is completed and retested before extraction. |
| AC-09 | Operational extraction cost is bounded. | A draft extraction checklist shows the new service can be added with one Gradle include, one Dockerfile, one Compose service, one native launcher entry, one health endpoint, one port assignment, and no frontend contract changes. |
| AC-10 | Security is read-only by proof, not intention. | A smoke test or grant report proves `lpn_ai_readonly` can select from `mart` and required `warehouse` objects and cannot write to BI or business tables. |

When these pass, extraction should be a mechanical move:

1. Copy `com.lpn.aibi.llmorchestrator.bi` into a new
   `services-java/bi-service` Spring Boot project.
2. Keep `/v1/bi/*` endpoint paths unchanged.
3. Move BI datasource config and properties unchanged.
4. Update Compose/native launcher and frontend base URL routing in one small
   deployment PR.
5. Remove the package from `llm-orchestrator` only after parity tests pass.

## Final decision

Proceed package-first inside `llm-orchestrator`.

Do not create a standalone `bi-service` now. The standalone service remains the
target architecture, but the project earns that extraction only after marts are
materialized or otherwise accepted, the `/v1/bi/*` contract is implemented and
stable, BI tests prove package independence, and deployment changes become
mechanical rather than speculative.

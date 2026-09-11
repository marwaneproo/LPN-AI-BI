# LPN AI BI

## From Enterprise Data to Intelligent Decision-Making

LPN AI BI is built around a simple principle:

> **Data becomes valuable when it can be transformed into understanding, foresight and better decisions.**

The platform therefore connects the complete analytical chain:

```text
                DATA
                  |
                  v
          DATA ENGINEERING
                  |
                  v
          DATA WAREHOUSE
                  |
                  v
       BUSINESS INTELLIGENCE
                  |
                  v
              ANALYTICS
                  |
          +-------+--------+
          v                v
   GENERATIVE AI      PREDICTIVE AI
          |                |
          +-------+--------+
                  v
             INSIGHTS
                  |
                  v
          RECOMMENDATIONS
                  |
                  v
       STRATEGIC DECISIONS
```

**Internship project (stage) - Marwane El Abbadi**
**Client:** Librairie Papeterie Nationale (LPN), Mohammedia

> Real company data and confidential information are not included in this repository.
> Sample data referenced in the docs is synthetic or anonymized and provided only for demonstration purposes.

A web application that lets LPN query an imported Compiere ERP snapshot using natural language (French / English) and produces sales and stock forecasts. The system runs entirely on a local Docker Compose stack. It does **not** connect to LPN's Oracle database at runtime; data flows in one direction through periodic CSV/SQL snapshot bundles imported into PostgreSQL.

---

## Table of contents

- [Architecture](#architecture)
- [Main features](#main-features)
- [Technology stack](#technology-stack)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Environment configuration](#environment-configuration)
- [Installation](#installation)
- [Running the project](#running-the-project)
- [AI pipeline](#ai-pipeline)
- [Forecasting](#forecasting)
- [Security](#security)
- [Screenshots](#screenshots)
- [Privacy](#privacy)
- [Project context](#project-context)
- [License](#license)

---

## Architecture

Polyglot microservices behind a single Docker Compose stack, following the DATA -> DATA ENGINEERING -> DATA WAREHOUSE -> BI -> ANALYTICS -> (GENERATIVE AI + PREDICTIVE AI) -> INSIGHTS chain shown above:

| Layer | Stack | Host port |
|---|---|---|
| Frontend | React 18 + Vite + TypeScript + token-based CSS design system + Recharts | `5173` (dev) |
| API Gateway | Planned .NET/YARP layer; local traffic currently uses the Vite same-origin proxy | - |
| Auth | Spring Boot opaque server sessions + PostgreSQL + HttpOnly cookie | via `llm-orchestrator` |
| LLM Orchestrator | Spring Boot 3 + Java 21 + LangChain4j 1.x | `8081` -> container `8080` |
| SQL Executor | Spring Boot 3 + Postgres JDBC + sqlglot sidecar | `8082` -> container `8080` |
| Schema Retrieval (RAG) | Python 3.11 + FastAPI + sentence-transformers + Qdrant | `8084` |
| SQL Validator | Python 3.11 + FastAPI | `8086` |
| Predictive / Forecasting | Python 3.11 + FastAPI + Prophet + LightGBM | `8085` -> container `8000` |
| Data Import | Python 3.11 CLI + pandas + SQLAlchemy | - |
| Reporting | .NET 9 + EF Core (planned) | - |
| Vector store | Qdrant | `6333` |
| Database | PostgreSQL 16 | `5433` -> container `5432` |
| Local LLMs | Native Ollama: `qwen2.5-coder:7b` + `llama3.1:latest` | - |

The full architectural rationale lives in [`docs/architecture/LPN_AI_BI_Production_Plan.md`](./docs/architecture/LPN_AI_BI_Production_Plan.md).
The week-by-week implementation plan lives in [`docs/architecture/AI_AGENT_TASKS_WEEKS_1_TO_4.md`](./docs/architecture/AI_AGENT_TASKS_WEEKS_1_TO_4.md).

---

## Main features

- **Natural-language querying (NL-to-SQL):** ask business questions in French or English; deterministic SQL templates handle well-known question shapes, with an LLM fallback (schema-RAG grounded) for open-ended questions.
- **Schema retrieval / RAG:** relevant tables, columns and example SQL are retrieved from Qdrant before generation, so the LLM is grounded in the real (synthetic-safe) schema instead of guessing.
- **BI dashboards:** Overview, Orders (`Commande`), Revenue (`Chiffre d'affaires`), Articles, Clients and Purchasing (`Achats`) views built on Postgres marts (`mart.mart_sales_by_product`, `mart_sales_monthly`, etc.).
- **Conversational BI assistant:** a chat interface that answers as a BI consultant (executive summary, business analysis, observations, recommendations) rather than a flat SQL echo, with tiered response depth depending on question complexity.
- **Forecasting:** time-series sales/stock forecasts via a dedicated predictive service.
- **Role-based access & session auth:** opaque server-side sessions, HttpOnly cookies, BCrypt password hashing - see [Security](#security).
- **Resilience:** Resilience4j circuit breakers and Redis-backed caching between services so a slow/unavailable LLM or database does not cascade into a full outage.

---

## Technology stack

- **Frontend:** React 18, Vite, TypeScript, Recharts
- **Backend (Java):** Spring Boot 3, Java 21, LangChain4j, Gradle multi-module build
- **Backend (Python):** FastAPI, pandas, SQLAlchemy, sentence-transformers
- **AI / ML:** Ollama (local LLM inference), Qdrant (vector search / RAG), Facebook Prophet, LightGBM
- **Data:** PostgreSQL 16, a dedicated data-warehouse layer (`DataWareHouse/`) with a star-schema design for the sales process
- **Infra:** Docker / Docker Compose, Resilience4j, Redis, Nginx (planned gateway)

---

## Project structure

```text
lpn-ai-bi/
+-- frontend/                # React + Vite UI
+-- services-java/           # llm-orchestrator + sql-executor (Gradle multi-module)
+-- services-python/         # schema-retrieval, sql-validator, predictive, data-import, eval
+-- services-dotnet/         # gateway (YARP) + reporting (EF Core) - planned, placeholders only
+-- DataWareHouse/           # star-schema design + ETL for the sales process
+-- infra/                   # postgres init, qdrant, grafana, prometheus
+-- scripts/                 # smoke tests, data-import helpers
+-- docs/                    # architecture, runbooks, forecasting notes, schema metadata
+-- intern-handoff/          # onboarding material for incoming interns
+-- .gitignore
+-- .env.example
+-- docker-compose.yml
+-- Makefile
+-- README.md
```

---

## Prerequisites

- Docker and Docker Compose
- Node.js (for the frontend; `npm install` inside `frontend/`)
- Java 21 and Gradle (for `services-java/`, if building outside Docker)
- Python 3.11 (for `services-python/`, if running outside Docker)
- [Ollama](https://ollama.com) installed natively (LLMs are hosted outside Docker):

  ```powershell
  ollama pull qwen2.5-coder:7b
  ollama pull llama3.1:latest
  ```

---

## Environment configuration

Copy the template and fill in real values before starting anything:

```bash
cp .env.example .env
```

Every variable consumed by `docker-compose.yml` is documented in `.env.example`, including:

- `POSTGRES_DB`, `POSTGRES_APP_ADMIN_PASSWORD`, `POSTGRES_AI_READONLY_PASSWORD`
- `AUTH_BOOTSTRAP_ADMIN_USERNAME`, `AUTH_BOOTSTRAP_ADMIN_PASSWORD`, `AUTH_COOKIE_SECURE`
- `OLLAMA_BASE_URL`, `OLLAMA_NUM_CTX`, `OLLAMA_TIMEOUT_SECONDS`, `OLLAMA_KEEP_ALIVE_SECONDS`
- `SQL_MODEL`, `SQL_REASONING_MODEL`, `SQL_FALLBACK_MODEL`, `NARRATOR_MODEL` (and their `_NUM_GPU` / `_NUM_PREDICT` / `_TIMEOUT_SECONDS` variants)
- `SCHEMA_RETRIEVAL_ADMIN_KEY`
- `SQL_EXECUTOR_MAX_ROW_COUNT`, `SQL_EXECUTOR_QUERY_TIMEOUT_SECONDS`
- `PERFORMANCE_TRACING_ENABLED`

Never commit a real `.env` - it is excluded via `.gitignore`; only `.env.example` (placeholder values) is tracked.

---

## Installation

```bash
cp .env.example .env          # then fill in passwords
docker compose up -d --wait
make smoke                    # phase-1 end-to-end smoke test
```

On Windows, the smoke checkpoint can be run directly:

```powershell
cd path\to\LPN-AI-BI
.\scripts\smoke.ps1
```

The smoke script starts the Docker Compose stack, checks PostgreSQL, Qdrant, Schema Retrieval, SQL Validator, SQL Executor, LLM Orchestrator, native Ollama, and then sends one canned question through `/v1/qa`.

---

## Running the project

To run the current frontend-to-LLM flow:

```powershell
cd path\to\LPN-AI-BI
docker compose up -d

cd path\to\LPN-AI-BI\frontend
npm run dev
```

Open `http://127.0.0.1:5173` and ask, for example, `How many orders were placed last month?`.

On the first authenticated start, set `AUTH_BOOTSTRAP_ADMIN_PASSWORD` in `.env` to a unique password of at least 12 characters. The service never creates or advertises a default `admin/admin` account. See [`docs/architecture/AUTHENTICATION.md`](./docs/architecture/AUTHENTICATION.md) for the full authentication contract.

On Windows, you can also double-click:

```text
Launch_LPN_AI_BI.bat
```

The launcher starts Docker Desktop if needed, clears the project ports, starts Docker Compose, starts the frontend on `http://127.0.0.1:5173`, and writes logs under `logs/`.

---

## AI pipeline

1. The question is normalized (accents, typos, abbreviations, synonyms) and tagged with a business domain.
2. Deterministic SQL templates are tried first for well-known question shapes (revenue, ranking, stock risk, unpaid invoices, order counts, supplier/customer ranking, category breakdowns, etc.).
3. If no template matches, schema retrieval (Qdrant + sentence-transformers) supplies the relevant tables/columns/example SQL, and the LLM Orchestrator (LangChain4j + Ollama) generates SQL grounded in that context.
4. Generated SQL is validated (`sql-validator`) before execution by `sql-executor` against a read-only PostgreSQL role.
5. Results are narrated by the LLM as a short direct answer or a structured BI-consultant report (executive summary, business analysis, observations, recommendations), depending on question complexity - with the full numeric result always rendered deterministically in code first, so the LLM cannot silently drop rows from a ranking or listing.

---

## Forecasting

The predictive service (`services-python/predictive`) produces time-series sales and stock forecasts using Facebook Prophet, exposed via FastAPI and wired into the frontend's forecasting views.

---

## Security

- Session-based authentication owned by `services-java/llm-orchestrator` (opaque 256-bit session token, `HttpOnly` + `SameSite=Strict` cookie; PostgreSQL stores only its SHA-256 digest - never the raw token).
- Passwords are hashed with BCrypt (legacy PBKDF2 hashes are transparently upgraded on next login).
- Standard sessions expire after 12 hours; "stay signed in" sessions expire after 30 days. A new login revokes the user's earlier sessions.
- No default `admin/admin` account is ever created; a bootstrap administrator is only created/rotated when `AUTH_BOOTSTRAP_ADMIN_PASSWORD` is explicitly set.
- The frontend never stores an auth token in web storage.
- The AI pipeline only ever executes against a **read-only** PostgreSQL role (`POSTGRES_AI_READONLY_PASSWORD`), separate from the application's admin role.

Full details: [`docs/architecture/AUTHENTICATION.md`](./docs/architecture/AUTHENTICATION.md).

---

## Screenshots

Screenshots of the live dashboards are not included in this repository, since the real views display actual LPN sales, client and supplier figures. See [Privacy](#privacy).

---

## Privacy

- Real company data and confidential information (customer records, real revenue figures, real supplier/client names, credentials) are **not** included in this repository.
- Any sample or reference data mentioned in the docs is synthetic, anonymized, or generic UI-design reference material - never a real LPN export.
- See `CLEANUP-REPORT.md` for the audit performed before publication.

---

## Project context

This project was built during an internship (stage) at **LPN (Librairie Papeterie Nationale)**, Mohammedia, Morocco, by **Marwane El Abbadi**, a Computer Engineering and Networks student. It covers the full analytical chain shown at the top of this document: ERP data ingestion into a PostgreSQL data warehouse, a business-intelligence layer, a natural-language/RAG chat assistant, and Prophet-based forecasting.

---

## License

Internal / academic project developed during an internship at LPN. No open-source license is granted; all rights reserved by the author and LPN. This repository is shared for portfolio purposes only.
 
 

# LPN AI-BI: Production Architecture & 16-Week Plan

**Author target:** Youssef Bahaddou
**Project:** LPN (Librairie Papeterie Nationale) AI-BI Platform
**PoC milestone reached:** End-to-end Text-to-SQL working on dummy 20-table PostgreSQL via local Llama 3.1 8B
**Production target:** Compiere ERP on Oracle, ~824 tables (real business tables far fewer)
**Timeline remaining:** ~16 weeks
**Audience:** DG, IT engineers, business managers — primarily French speakers
**Constraint:** Free / local first; ≤$20/month if absolutely necessary

---

## Executive Summary

You already have a credible PoC: schema-aware AI generates SQL, a guarded read-only role executes it, results return as JSON. That is genuinely a real milestone — most PFEs don't reach that. The next four months are about three things, in this order:

1. **Lifting the LLM pipeline from 20 tables to ~80 real Compiere business tables** without blowing up the prompt budget. This is the single hardest technical problem on this project. Solution: a dedicated Schema Retrieval microservice using vector search (Schema RAG) on a curated table-description CSV.
2. **Adding a second AI capability** — predictive forecasting — that the LLM does *not* compute itself, but consumes and explains. The forecasting model is a classical time-series model (Prophet + LightGBM), running in a Python microservice. The LLM only ever *narrates* its outputs.
3. **Productizing the system** as a polyglot microservice architecture (Spring Boot for AI orchestration, .NET for gateway and reporting, Python for ML and retrieval) that runs as a Docker Compose stack LPN can deploy on a single server.

**What this is and isn't:** This is a separate web application that LPN deploys, fed by periodic data exports from Compiere. It does **not** connect to LPN's Oracle database at runtime — your application runs entirely against its own PostgreSQL, populated from CSV/SQL exports taken on the company PC. It is not a Compiere extension or migration. When LPN eventually replaces Compiere with another ERP, only the export script and schema metadata file need updating.

**Stack at a glance:** React + Vite + shadcn/ui + Recharts + ECharts on the frontend. Spring Boot 3 + LangChain4j for the LLM Orchestrator and SQL Executor. .NET 9 + YARP for the Gateway and EF Core for Reporting. Python + FastAPI for Schema Retrieval, Predictive, and Ingestion. Ollama + Arctic-Text2SQL-R1-7B + Llama 3.2 3B for local AI. Groq optional, evaluated in Week 12.

Everything else (Arabic, advanced UI polish, K8s, multi-tenancy) is post-PFE.

---

## 1. Reality Check on Your Current State

### What's solid
- Docker Compose stack runs cleanly: PostgreSQL, Ollama+Llama 3.1 8B, .NET 9 backend (Clean Architecture), React/Vite frontend.
- The Text-to-SQL pipeline works end-to-end on the dummy 20-table schema. The architecture and patterns transfer cleanly to any backend stack.
- Read-only DB role + keyword guards are in place. This is the *right* security primitive for AI-generated SQL.
- Semantic Kernel is wired correctly; Ollama host resolution inside Docker is fixed.
- 80,000+ rows of seeded transactional data with realistic seasonality (rentrée scolaire).

### Important context clarification
The product is a **separate web application** that LPN will deploy as a Docker stack. It is **not** a Compiere extension or migration. Critically, it also does **not connect to LPN's Oracle database at runtime** — you do not have permission to do so.

The data flow is **snapshot-based, one-way, and explicit**:

```
[LPN's Oracle 11g]  ──manual export on company PC──►  CSVs / SQL dumps / schema files
                                                                │
                                                                ▼
                                                    [your PostgreSQL 16]
                                                                │
                                                                ▼
                                              [your AI/BI application reads here]
```

What this means in practice:

- Your application's database is **PostgreSQL only**. Oracle 11g is upstream of your system and never directly accessed by your services.
- You'll periodically (weekly? monthly? you decide) re-export from Compiere on the company PC, transfer the files to your dev environment, and re-import into PostgreSQL.
- The UI should show a "données à jour au [date]" (data as of) banner so users know the freshness.
- All Oracle 11g dialect concerns disappear. Your Text-to-SQL model only needs to generate PostgreSQL — and your existing PoC already does that perfectly.

**Why this is actually a stronger architecture for the PFE defense:**

- **Full data sovereignty.** LPN's production database is never touched by your application. Zero risk of an AI-generated query bringing down production.
- **Auditable data lineage.** Every byte in your DB came from a manual export with a timestamp. No surprise data flows.
- **Portable.** When LPN migrates ERPs (Compiere → Axelor or whatever), you change the export script, not the application.
- **Demo-friendly.** You can demo the system anywhere with the same dataset, without VPN, without coordinating DB access.

You can frame this in the PFE report explicitly: *"L'architecture en mode snapshot a été choisie pour des raisons de souveraineté des données et de séparation des préoccupations. La base de production reste intouchée; les flux de données sont explicites et auditables."*

### What will break / change when you go from dummy DB to real LPN data
- **Schema injection:** You currently paste the full 20-table schema into every prompt. With ~80 real business tables imported from Compiere, this would consume too many tokens. Schema selection must become dynamic. (This is the Schema RAG pipeline in §4.1.)
- **You need to design your PostgreSQL target schema.** You can't just dump 80 Compiere tables verbatim — some columns are redundant, some need to be enriched with reference data, and relations need to be preserved through the export. This is real data-modeling work in Week 1. Good news: you've already done a version of this for the dummy DB seed, so you have practice.
- **Cryptic column names:** Compiere uses naming like `C_BPartner_ID`, `M_Product_ID`, `AD_Org_ID`. The LLM will not infer joins reliably without enriched metadata (human-friendly descriptions per table/column in your curated CSV). You can choose: keep Compiere's original names (preserves traceability if anyone audits) or rename to friendlier names (improves LLM accuracy at the cost of a translation layer in the export script). Recommendation: **keep Compiere names** in PostgreSQL but invest heavily in the description CSV. This is more defensible in the PFE.
- **Schema drift over time:** Once LPN migrates ERPs, the export script changes. Treat the schema metadata CSV as versioned data, not as code.

### What you should *not* try to do in 16 weeks
- Cover all of Compiere's tables. Aim for the ~50–100 tables that actually carry business data (orders, order lines, invoices, payments, products, partners, stock levels, employees). You said you've already explored a few (`C_ORDER`, `C_ORDERLINE`, `C_INVOICE`, etc.) — that exploration is exactly the right starting point for the export.
- Build a generic ML platform. Build *one* forecasting use case (sales / CA) extremely well, then a second (stock / rupture risk) if time allows.
- Implement live data sync from Oracle. You don't have permission, and even if you did, snapshot-based is the right architecture for this PFE. Document it as a v2 enhancement.

---

## 2. The Target Microservice Architecture

Eight services. Boring on purpose. Each one does one thing.

### 2.1. Service map (polyglot microservice architecture)

| # | Service | Stack | Why this stack |
|---|---------|-------|----------------|
| 1 | **API Gateway** | .NET 9 + YARP | Reuses your existing .NET expertise; YARP is excellent at routing/rate-limiting; already in your toolchain |
| 2 | **Auth Service** | Keycloak (off-the-shelf) | No code to write; integrates with both stacks via JWT |
| 3 | **LLM Orchestration Service** | Spring Boot 3 + LangChain4j | Best AI/RAG ecosystem in JVM; you're fluent in Spring Boot; mature LangChain4j tooling for the hardest part of the project |
| 4 | **Schema Retrieval Service** | Python 3.11 + FastAPI + Qdrant | Python wins decisively for embedding models and vector search libraries (sentence-transformers) |
| 5 | **SQL Execution Service** | Spring Boot 3 + JDBC | Generates and executes PostgreSQL only; pairs directly with the LLM Orchestrator; sqlglot called as a sidecar for parsing |
| 6 | **Predictive Service** | Python 3.11 + FastAPI + Prophet/LightGBM | Forecasting libraries are Python-native; no real Java equivalent for Prophet |
| 7 | **Data Import Pipeline** | Python 3.11 (manual + scheduled scripts) | Imports CSV/SQL exports from Compiere into your PostgreSQL; pandas + SQLAlchemy is the de facto stack |
| 8 | **Reporting Service** | .NET 9 + EF Core | Reuses your working EF Core setup for the audit/conversation/feedback DB; CRUD-heavy work where ASP.NET shines |

That's three backend stacks: **.NET 9, Spring Boot 3, Python 3.11** — plus the React frontend.

#### Why polyglot is defensible here

This split is not arbitrary. Each service goes to the stack that's genuinely best for its job:

- **AI-heavy services** (LLM Orchestrator, Schema Retrieval, Predictive) get the language with the strongest AI ecosystem for that specific task.
- **CRUD-heavy services** (Reporting) and **infrastructure services** (Gateway) stay in .NET, where you have working code and EF Core is excellent.
- **The PFE narrative** is stronger this way: "I architected a polyglot microservice system and made principled stack choices per service" beats "I picked one language."

#### The honest cost of polyglot

Two backend build systems (Maven/Gradle + dotnet CLI), two dependency managers, two CI pipelines, two Dockerfile patterns. Real overhead, but bounded — this is a microservice architecture, polyglot is normal.

If you want to **reduce the cost**, two simplifications are also defensible:

- **Two-stack option:** drop .NET, do everything backend in Spring Boot + Python. Archive the .NET PoC as a demo artifact. Loses the polyglot narrative but cuts setup overhead by ~25%.
- **Three-stack option (what's recommended above):** keep .NET for Gateway + Reporting, Spring Boot for AI orchestration, Python for ML. Best architectural defense, slightly more setup.

You said you're "down to do anything that is perfect" — go with the three-stack split. It's worth the overhead.

Plus the **Frontend** (React + Vite + TypeScript + Tailwind) — already started.

### 2.2. Why this split (and not the one in your original spec)

Your original spec listed an "LLM Service" and a "Retrieval Service" but lumped SQL execution into the LLM service. **Separate the SQL executor.** Two reasons:

- The execution service is the *only* component that touches the production database. Keeping it isolated makes the security audit trivial: lock down one service, not two.
- It lets you swap the model orchestrator (today LangChain4j, tomorrow maybe Vanna or DSPy) without touching the security-critical path.

Similarly, your spec had "Monitoring Service" as a microservice. Don't build it. Use **Prometheus + Grafana + Loki** as off-the-shelf infrastructure. They are not a service you write; they are containers you run.

### 2.3. End-to-end flow for a Text-to-SQL question

```
User (FR) ──► Frontend
            │
            ▼
       API Gateway ──(JWT validated by Auth)──► LLM Orchestrator
                                                   │
                                                   ├──► Schema Retrieval Service
                                                   │     (returns top-8 relevant tables + columns)
                                                   │
                                                   ├──► LLM call (Ollama OR Groq, depending on mode)
                                                   │     - Prompt: question + retrieved schema + few-shot
                                                   │     - Output: raw SQL
                                                   │
                                                   ├──► SQL Execution Service
                                                   │     - Validate (SELECT-only, no DDL/DML)
                                                   │     - Execute on local PostgreSQL (read-only role)
                                                   │     - Return rows
                                                   │
                                                   ├──► LLM call #2 (formatting)
                                                   │     - "Voici les chiffres bruts: X. Réponds en français."
                                                   │
                                                   └──► Reporting Service
                                                         - Persist (question, SQL, rows, answer, user)
            ▼
       Frontend renders: natural-language answer + (debug mode) SQL + raw rows
```

### 2.4. End-to-end flow for a forecasting question

```
User: "Quel CA prévu pour la rentrée 2026 ?"
            │
            ▼
       LLM Orchestrator detects intent: forecasting (not raw SQL)
            │
            ▼
       Predictive Service /forecast ──► returns {monthly_forecast, intervals_80_95, drivers}
            │
            ▼
       LLM Orchestrator formats: "D'après les 5 dernières années, le CA prévu pour août-septembre
                                  2026 est de ~3.2M MAD (intervalle 80%: 2.9M – 3.5M). La saisonnalité
                                  rentrée scolaire est le facteur dominant."
            │
            ▼
       Frontend renders text + chart (recharts/chart.js)
```

The LLM orchestrator does **intent detection** at the top: is this a factual SQL question, or a forecasting question? In practice, a small classifier prompt at the start (or just a function-calling pattern in Semantic Kernel) is enough.

### 2.5. The data import flow (one-way, snapshot-based)

This is the flow you'll run periodically — weekly during development, then on whatever cadence LPN agrees to in production:

```
[Company PC, with Compiere/Oracle access]
            │
            │  1. Run SQL exports (one per business table)
            │     example: SELECT * FROM C_ORDER WHERE Created >= :since
            │     output: csv files + a manifest.json describing the export
            ▼
[Export bundle: zip of CSVs + manifest.json + schema.sql]
            │
            │  2. Transfer manually to your dev/prod machine
            │     (USB, internal file share, encrypted email — whatever LPN allows)
            ▼
[Your dev/prod machine]
            │
            │  3. Run the import script:
            │     python -m data_import.run --bundle path/to/export.zip
            │     - Validates manifest.json (expected tables present)
            │     - Truncates target tables (or upserts if you want incremental)
            │     - Loads CSVs into PostgreSQL via COPY
            │     - Recomputes derived tables (monthly aggregates for forecasting)
            │     - Re-embeds schema metadata into Qdrant if schema changed
            │     - Logs import history (timestamp, row counts per table)
            ▼
[Your PostgreSQL, ready for queries]
```

A few design decisions worth being explicit about:

- **Truncate vs upsert:** for the PFE, truncate-and-replace is simpler. Each import is a full snapshot. If LPN later wants incremental, that's v2.
- **Manifest file:** every export bundle includes a `manifest.json` declaring which tables/dates are inside. The import script refuses to import bundles that don't match what your application expects. This is your sanity check against bad exports.
- **Import history table** in PostgreSQL: timestamp of each import, source date range, row counts per table. The frontend reads this to display the "données à jour au [date]" banner.
- **Idempotency:** running the same import twice should produce the same DB state. Truncate-and-replace gives you this for free.

---

## 3. Technology Stack & Rationale

### 3.1. Backend stack: three languages, principled assignment

Each backend stack is chosen for what it's best at. Summary of the rationale per service:

| Service | Stack | Top reason |
|---------|-------|-----------|
| API Gateway | .NET 9 + YARP | YARP is mature; you have working .NET; clean entry point |
| LLM Orchestrator | Spring Boot 3 + LangChain4j | Strongest non-Python AI ecosystem; you're fluent in Spring |
| SQL Executor | Spring Boot 3 + JDBC + Postgres driver | Pairs with Orchestrator; PostgreSQL only — no Oracle dialect concerns |
| Reporting | .NET 9 + EF Core | Reuses your EF Core; CRUD is ASP.NET's sweet spot |
| Schema Retrieval | Python 3.11 + FastAPI | sentence-transformers is Python-only |
| Predictive | Python 3.11 + FastAPI | Prophet/LightGBM are Python-only |
| Data Import | Python 3.11 | Pandas + SQLAlchemy is the de facto stack |

**On the .NET PoC:** keep it. The Text-to-SQL flow you built in .NET maps cleanly onto the new Reporting + Gateway services. The Semantic Kernel work isn't wasted — you'll archive that part as a demonstration in the PFE report ("here's the original PoC, here's why I split the AI orchestration to Spring Boot + LangChain4j once schema RAG became necessary").

### 3.2. AI orchestration framework comparison

This is the biggest single technical decision after schema curation. You have four real options:

| Framework | Language | Best at | Worst at | Score for your use case |
|-----------|----------|---------|----------|--------------------------|
| **LangChain4j** | Java | Mature RAG pipeline, 30+ vector stores, 20+ model providers, Ollama and Groq native | Observability is still experimental; Spring integration via community starter | **Recommended** |
| **Spring AI** | Java | Native Spring observability (Micrometer + Actuator), clean Spring Bean integration, MCP support | RAG less feature-rich than LangChain4j; fewer niche model providers | Strong alternative |
| **Semantic Kernel** | .NET / Python / Java | Lightweight, Microsoft-backed, your existing PoC works | Weaker RAG ergonomics; smaller community; Java version less mature | Defensible if you stick with .NET |
| **Vanna.AI** | Python | Purpose-built for Text-to-SQL; schema RAG built-in; supports Ollama + Oracle | You give up control of the RAG pipeline; harder to push past 80% accuracy | Worth considering for v0; not for production |

**Recommendation: Spring Boot + LangChain4j** for the LLM Orchestrator.

Reasoning, ranked:

1. The hardest problem in this project is the schema RAG pipeline, which is exactly what LangChain4j's mature RAG components are best at.
2. You're fluent in Spring Boot — velocity on a 4-month solo project matters.
3. Native support for both Ollama (local) and OpenAI-compatible APIs (Groq, etc.) without custom integration code.
4. Switching cost from LangChain4j to Spring AI later is small if you change your mind — both use similar `ChatModel` abstractions.

**About Vanna.AI specifically:** it's worth knowing about because it's purpose-built for exactly your use case (Text-to-SQL with schema RAG, supports Ollama and Oracle). For a quick v0 demo, it would get you to working SQL generation in days. **But** it owns the entire RAG pipeline, which means you can't customize the schema embedding format, can't inject your French-language metadata strategy, and can't easily swap the retrieval logic. For a PFE where the schema curation is *the* differentiator, you want full control. Use LangChain4j. Mention Vanna in your PFE report as a comparison point you evaluated.

**About DSPy:** Stanford's "programming, not prompting" framework can auto-optimize prompts against an eval set. Genuinely powerful for Text-to-SQL — there are published papers using DSPy for SQL generation. For v2 after the PFE, definitely worth exploring. For your 16-week timeline, the learning overhead isn't justified. Stash it for later.

### 3.3. Why Qdrant for the vector store (and not Pinecone / Milvus / pgvector)

- **Pinecone:** managed, paid. Disqualified by your "free first" constraint.
- **Milvus:** powerful but operationally heavy (etcd, MinIO dependencies). Overkill for a PFE.
- **pgvector:** would be appealing because PostgreSQL is already in your stack. **Acceptable as a fallback.** It works fine for ≤100K embeddings.
- **Qdrant:** single Docker container, fast, free, persistent, has a good Python client. **Recommended.**

If you want to keep the stack minimal, use **pgvector**. If you want a cleanly separable retrieval service that benchmarks better, use **Qdrant**. Either is defensible in a PFE.

### 3.4. Why Keycloak for auth

- Free, self-hosted, single Docker container, mature.
- Speaks OIDC and SAML, so it integrates with the company SSO later if needed.
- Roles map cleanly to your three audiences: `dg`, `it_engineer`, `business_manager`.
- Alternative: roll your own JWT in .NET. Faster initially, weaker for the defense narrative.

### 3.5. Why Docker Compose, not Kubernetes

- Compose runs your existing stack today. It will run the production stack with 8 services. It works on a laptop and on a single VM.
- Kubernetes adds: cluster setup, ingress controllers, secrets management, helm charts, persistent volume claims. Three weeks of work minimum, with no PFE-relevant payoff.
- If a jury member asks "why not Kubernetes" — answer: "K8s value emerges at multi-node scale. LPN's single-server deployment doesn't justify the operational tax."

### 3.6. Why Prophet + LightGBM (not LSTM / TFT)

- **Data volume:** 5 years of monthly aggregated sales = 60 data points per series. Deep learning needs orders of magnitude more.
- **Prophet:** designed for exactly your case — strong yearly seasonality (rentrée scolaire), holidays, trend changes. Fits in seconds. Interpretable.
- **LightGBM:** for product-level forecasting where you have many features (category, supplier, promotion flags, day-of-week). Gradient boosting on tabular features is the modern default and beats LSTMs on this kind of data.
- **TFT / LSTM:** require a GPU for serious training, longer development cycles, and harder to defend in a PFE because the jury *will* ask why you used them and "deep learning is cool" is not the answer.

### 3.7. Frontend stack — making it actually polished

You said you want the "best framework for frontend" with charts that actually look good. Here's the modern stack that produces genuinely polished BI dashboards rather than another generic admin template:

```
Build:       React 18 + Vite + TypeScript           ← keep what you have
Styling:     Tailwind CSS v4                        ← keep what you have
Components:  shadcn/ui                              ← add this
Charts:      Recharts (everyday) + ECharts (power)  ← add both
Icons:       Lucide React
Forms/state: TanStack Query + React Hook Form + Zod
Routing:     React Router v6 (no need for Next.js)
```

Three calls to make explicit:

**1. shadcn/ui over Material UI / Ant Design.** shadcn isn't a library — it's a code generator. You run a CLI command, it copies the component source code into *your* project. You own it, you can customize it freely, no runtime dependency, no theme lock-in, ships with first-class accessibility and dark mode. It's the dashboard component approach that's overtaken Material UI and Ant Design over the past 18 months for new projects. Production dashboards built on shadcn look distinctly less "AI generic" because every team customizes the components.

**2. Recharts + ECharts as a pair, not one or the other.**
- **Recharts** for everyday charts: line, bar, area, pie, simple combinations. SVG-based, declarative React API, ships with shadcn/ui's chart wrapper out of the box. Recharts wraps the most useful pieces of D3 in idiomatic React components, so you can add charts with a JSX-style API and familiar props. It relies on SVG, which keeps markup readable and lets you style charts with plain CSS. Perfect for 90% of your BI needs (CA trend, category breakdown, etc.).
- **ECharts via `echarts-for-react`** as the escape hatch for advanced charts: heatmaps (stock rupture risk by SKU × week), Sankey diagrams (B2B client revenue flow), treemaps (product category hierarchy), candlesticks. ECharts in React, including GPU-accelerated Canvas and WebGL modes. It handles millions of points, dynamic data loading, and advanced interactions like brushing and drill-down out of the box. You won't need this performance ceiling, but you'll want the chart variety.

Don't include both for everything — use Recharts by default and reach for ECharts only when Recharts can't do what you need.

**3. Skip Next.js. Stay on Vite.**

Next.js is excellent but solves problems you don't have: server-side rendering for SEO, static site generation, server components. This is an internal corporate tool behind auth — none of those features matter. Vite is faster for development iteration and simpler. If a jury member asks "why not Next.js" — answer: "Next's value is SSR/SEO. This is an authenticated internal tool, so Vite's faster dev loop and smaller surface area win."

#### Optional accelerator: Tremor or a premium dashboard kit

If you want to ship faster on the dashboard side specifically:

- **Tremor** (free, open source) — built on Recharts + Radix UI, designed for dashboards. 35+ fully open-source, accessible components for dashboards and charts. Built with React, Tailwind CSS and Radix UI. Built on Recharts and Radix UI, Tremor provides the essentials for production-ready UI. It's a "drop-in" set of pre-styled KPI cards, bar lists, area charts, etc. Pairs well with shadcn/ui — they don't conflict.

Use Tremor for the dashboard pages (KPI cards, summary charts) and shadcn/ui for everything else (chat interface, forms, tables, modals). This is the combination most modern data dashboards are converging on.

#### What it looks like together

The chat interface (your main UX) uses shadcn/ui components: `Card`, `Input`, `Button`, `ScrollArea`, `Sheet` for the conversation history sidebar, `Dialog` for debug mode. The dashboard view uses Tremor `Card` + `BarChart` + `LineChart` for the KPI overview, and `echarts-for-react` for any advanced visualization. Tailwind v4 ties it all together visually.

This is a stack that produces a UI you can show to a jury without apology. None of it is novel — it's just the components and patterns that have settled out as the best-in-class options as of early 2026.

### 3.8. Stack summary

```
Frontend
  Build:        React 18 + Vite 6 + TypeScript
  Styling:      Tailwind CSS v4
  Components:   shadcn/ui (+ Tremor for dashboard pages)
  Charts:       Recharts (default) + echarts-for-react (advanced)
  State:        TanStack Query + React Hook Form + Zod
  Routing:      React Router v6
  Icons:        Lucide React

Backend
  API Gateway:        .NET 9 + YARP
  Auth:               Keycloak
  LLM Orchestrator:   Spring Boot 3 + Java 21 + LangChain4j 1.x
  SQL Executor:       Spring Boot 3 + JDBC + Postgres driver
  Reporting:          .NET 9 + EF Core + PostgreSQL
  Schema RAG:         Python 3.11 + FastAPI + sentence-transformers + Qdrant
  Predictive:         Python 3.11 + FastAPI + Prophet + LightGBM + scikit-learn
  Data Import:        Python 3.11 + SQLAlchemy + pandas + Click (CLI)

Data
  PostgreSQL 16  ← single application database
                  - business tables (imported from Compiere snapshots)
                  - reporting / audit / conversation history
                  - import_history table tracking each snapshot
  Qdrant (or pgvector) for schema embeddings
  Compiere/Oracle 11g  ← upstream only, never touched at runtime,
                         data flows out via manual CSV/SQL exports

LLM (local, on RTX 4060 8GB)
  Ollama running:
    - Arctic-Text2SQL-R1-7B (SQL generation specialist) — Q4_K_M, ~4.5GB
    - llama3.2:3b (French formatter, intent detection) — ~2GB
    Both fit simultaneously (~6.5GB), no swap latency.

LLM (cloud, OPTIONAL — only if you want a "reasoning mode")
  Groq llama-3.3-70b-versatile — free tier, gated UI toggle
  Or skip entirely. The local stack is strong enough for the PFE.

Infrastructure
  Orchestration: Docker Compose
  Observability: Prometheus + Grafana + Loki + Sentry (free tier)
  CI/CD:         GitHub Actions
  Build tools:   Gradle (Spring Boot) + dotnet CLI (.NET) + uv/poetry (Python)
```

---

## 4. LLM Strategy — How to Survive Compiere's Schema

This is the section to read twice.

### 4.1. The Schema RAG pipeline (replaces full-schema injection)

**Step 1 — Curate the business table list.** Before any code, sit with your encadrant for one afternoon and produce a CSV: for each of the ~50–100 truly used Compiere tables, write:
- table name (e.g., `C_ORDER`)
- French human description (e.g., "Commandes clients, en-tête")
- module (Ventes / Achats / Stock / Finance / RH)
- key columns and their French meanings

This is data engineering, not coding. **It is the highest-leverage work you will do on this project.** A 100-row CSV improves accuracy more than any prompt tuning.

**Step 2 — Embed and index.** A nightly job in the Schema Retrieval Service:
- Reads the CSV + the live Oracle `INFORMATION_SCHEMA`-equivalent (`ALL_TAB_COLUMNS`).
- Builds a textual representation per table:
  ```
  Table: C_ORDER (Commandes clients - en-têtes)
  Module: Ventes
  Columns: c_order_id (PK), documentno (numéro commande), c_bpartner_id (FK client),
           dateordered (date commande), grandtotal (total TTC), docstatus (statut)
  Relations: → C_ORDERLINE (lignes de commande), → C_BPARTNER (clients)
  ```
- Embeds each table description with a multilingual model (`paraphrase-multilingual-MiniLM-L12-v2` is small, fast, FR/AR-capable).
- Stores in Qdrant with table name as the primary key.

**Step 3 — Retrieve at query time.** When a user asks a question:
- Embed the question (same multilingual model).
- Top-8 semantic search over Qdrant.
- Optionally expand: if `C_ORDER` is retrieved, auto-include `C_ORDERLINE` (relation-aware expansion).
- Return ~5–10 tables with full column context.

**Step 4 — Inject only the retrieved subset into the LLM prompt.** Now the prompt fits comfortably, the model isn't overwhelmed, and accuracy goes *up* not down.

This is the architectural move that takes you from PoC to production.

### 4.2. Local model strategy: Pattern B (committed)

You picked Pattern B and that's the right call given your hardware. Confirmed setup:

```
User question (FR or EN)
    │
    ▼
llama3.2:3b — intent classification + (if French) extract structured English representation
    │
    ▼
Arctic-Text2SQL-R1-7B + retrieved schema + PostgreSQL dialect prompt → raw SQL
    │
    ▼
SQL Executor — validate, run on your PostgreSQL (read-only role), return rows
    │
    ▼
llama3.2:3b — narrate the result rows in the user's original language
```

**Dialect simplification:** because your application database is PostgreSQL only (data was imported from Compiere offline), the model only ever generates PostgreSQL. This is a meaningful win:
- PostgreSQL has dramatically more LLM training data than Oracle 11g.
- Your existing PoC already produces PostgreSQL — no dialect translation work.
- Modern syntax (`LIMIT`, `RETURNING`, window functions, CTEs, lateral joins) is fully available.
- Date arithmetic is cleaner (`date_trunc`, `interval`, `now()`).

**VRAM math:** Arctic-7B Q4_K_M (~4.5GB) + Llama 3.2 3B Q4_K_M (~2GB) = ~6.5GB. Fits in your 8GB VRAM with both models resident simultaneously, so no swap latency. ~1.5GB headroom for KV cache and OS overhead.

**Why this beats a single bigger model:**
- Arctic-Text2SQL-R1-7B is purpose-trained for SQL with execution-correctness rewards. It's the SOTA 7B Text-to-SQL model on the BIRD benchmark.
- A 3B model is plenty for the linguistic tasks (intent classification, French↔English bridge, narration of pre-computed numbers). Using a 7B+ model for these would be wasteful.
- Two specialized small models often outperform one larger generalist.

#### Phased rollout: English first, French second

You said you'll start with English testing then add French. Good — this phasing lets you isolate failure modes:

- **Weeks 4–5:** All testing in English. Build the eval set in English first (simpler to validate correctness when you understand both the question and the SQL). This gives you a clean baseline accuracy number for Arctic on your specific imported schema.
- **Week 6:** Add French. Use the small model to bridge French → English structured representation, then feed to Arctic. Re-run the eval set translated to French. The accuracy delta between English baseline and French equivalent tells you exactly how much the French bridge costs you.

This is also a strong PFE narrative: "I evaluated the model on English first to establish a baseline, then measured the language-bridge overhead by re-running the same evaluations in French. The bridge introduced X% accuracy loss, which I mitigated by Y."

#### What about a single multilingual model (Pattern A)?

You picked B over A. Keep the Pattern A choice (`qwen2.5-coder:7b-instruct-q4_K_M`) as a fallback if Pattern B disappoints in your Week 4 evaluation. The eval set you build is portable — running it against Pattern A takes one afternoon. Don't spend energy on it unless B underperforms.

### 4.3. Cloud LLM strategy: optional, not required

Groq is a "nice to have" for a reasoning-mode toggle, not a requirement. Your local Pattern B setup is genuinely strong. Decision matrix:

| Scenario | Cloud LLM needed? |
|----------|------------------|
| Local Pattern B hits ≥80% accuracy on your eval set | No. Skip Groq entirely. Defend "I built a fully local solution that hits 80% accuracy without any cloud dependency" — that's a strong PFE story for a privacy-conscious enterprise. |
| Local Pattern B hits 70–79% and you have time in Week 12 | Optional. Add Groq as a "reasoning mode" toggle for hard queries. |
| Local Pattern B hits <70% even after schema refinement | Add Groq. The accuracy gap is too large to defend without cloud assist. |

**If you do add Groq later (Week 12):**
- Free-tier limits on `llama-3.3-70b-versatile`: 30 RPM, 1,000 RPD, 12K TPM, 100K TPD. ~280 tok/s throughput.
- Per-user rate limit at the gateway: 1 reasoning-mode call per 5 min, 50 per day per user, 800/day org cap.
- Graceful fallback to local on quota: *"Mode raisonnement temporairement indisponible — réponse générée en local."*

**Other free cloud options to consider if Groq becomes problematic:**
- OpenRouter has free tiers on several open-weight models, more generous TPD on some.
- Cerebras Inference also offers free-tier llama models with very fast throughput.
- Both are drop-in replacements for OpenAI-compatible endpoints — switching providers in LangChain4j is a config change.

**Recommended path:** ignore Groq entirely until your Week 5 evaluation tells you the local setup's actual accuracy. Then decide.

### 4.4. Anti-hallucination architecture (you already have the right instinct)

Your `lpn_llm_ai_idea.md` already nailed the principle: **the LLM is an orchestrator, not a calculator.** Reinforce this with three concrete guards:

1. **The LLM never produces numbers.** It generates SQL. The database produces numbers. The LLM only narrates them.
2. **Every numeric answer must include provenance.** API contract: `{"answer": "...", "sql": "...", "data": [...], "sources": ["C_ORDER", "C_ORDERLINE"], "confidence": 0.87}`. Frontend shows sources on hover or in debug mode.
3. **Verification step:** After SQL execution, the formatter LLM is given strictly the rows and a system prompt: *"Réponds uniquement avec les chiffres ci-dessous. Si les données sont vides, dis 'Je n'ai pas trouvé de données pour cette demande.' N'invente rien."*

### 4.5. French language handling

- All system prompts in French.
- Few-shot examples in French (DG-style questions paired with correct SQL).
- Error messages in French. (`"Désolé, je n'ai pas compris votre demande. Pouvez-vous la reformuler ?"`)
- Out-of-scope detection: if the user asks about something unrelated to LPN (weather, jokes, geopolitics), respond in French with: *"Je suis l'assistant BI de LPN. Je ne peux répondre qu'aux questions concernant les données de l'entreprise."*
- Arabic: defer to post-PFE. The architecture supports it (multilingual embeddings already used) but UI translation and Arabic few-shots are a 2-week project on their own.

### 4.6. Realistic accuracy targets

You said 80% is acceptable. With Pattern B (Arctic-Text2SQL-R1-7B + Llama 3.2 3B) on a curated schema with Schema RAG, target ranges:

| Query difficulty | English baseline (Week 4-5) | French via bridge (Week 6+) | French + Groq reasoning (optional) |
|------------------|----------------------------|----------------------------|------------------------------------|
| Simple lookup ("how many employees") | 95%+ | 90%+ | 95%+ |
| Single-table aggregate ("revenue last month") | 88–92% | 82–88% | 90–95% |
| Multi-table join ("revenue by product category") | 78–85% | 70–80% | 82–90% |
| Multi-step reasoning ("which categories show declining margin") | 60–70% | 55–65% | 75–85% |

These are estimates from published BIRD scores adjusted for your specific context. **Treat them as hypotheses, not promises.** Your Week 4 English evaluation gives the real baseline; Week 6 French re-run gives the bridge cost.

If your Week 5 eval comes in noticeably higher than this table, that's normal — the table is conservative because Compiere's cryptic naming will hurt accuracy and your curated French metadata will help. If it comes in noticeably lower, the schema curation needs another pass before anything else.

---

## 5. Predictive ML Strategy

### 5.1. The two questions worth answering

Pick *two* and do them well. Skip the rest until v2.

1. **Monthly CA forecast (next 1–6 months).** Drives the DG's question: *"D'après les 4 mois précédents, peux-tu m'estimer le CA des deux mois suivants ?"*
2. **Stock rupture risk for high-value SKUs.** Drives: *"Avons-nous suffisamment de stock pour la rentrée scolaire ?"*

### 5.2. Modeling approach

**For monthly CA:**
- Aggregate `C_INVOICE` to monthly revenue per category.
- Train one Prophet model per category (or a single model with category as a regressor).
- Add Moroccan holidays + a `back_to_school` regressor (binary indicator for August + September).
- Output: point forecast + 80% and 95% intervals.

**For stock rupture:**
- Per SKU, compute weekly sales from `C_ORDERLINE`.
- LightGBM regression: features = trailing sales, current stock, supplier lead time, promotion flag, season.
- Output: predicted stock-out probability over next 30 / 60 days.

### 5.3. API contract (Predictive Service)

```
POST /v1/forecast/sales
{
  "category_id": "papeterie",       // optional; null = total CA
  "horizon_months": 6,
  "include_intervals": true
}

Response:
{
  "model_version": "prophet-v1.3",
  "trained_at": "2026-04-15T...",
  "forecast": [
    {"month": "2026-05", "value": 1850000, "lower_80": 1720000, "upper_80": 1990000},
    ...
  ],
  "drivers": ["yearly_seasonality: +18%", "trend: +4% YoY"],
  "currency": "MAD"
}
```

```
POST /v1/forecast/stock-risk
{ "product_ids": ["..."], "horizon_days": 60 }

Response:
{
  "model_version": "lgbm-v0.4",
  "predictions": [
    {"product_id": "...", "stockout_probability": 0.71, "expected_stockout_date": "2026-08-22"},
    ...
  ]
}
```

### 5.4. Lifecycle

- **Training:** offline, scheduled nightly via Data Ingestion Service.
- **Versioning:** every trained model is tagged with timestamp + git commit + data hash. Stored as `joblib` files in a mounted volume.
- **Evaluation:** backtest on the trailing 12 months. Metrics: **MAPE** (primary, business-friendly), **MAE**, **RMSE**, **80% interval coverage**.
- **Drift monitoring:** weekly job compares last week's actuals vs forecast; if MAPE > 25% for 3 consecutive weeks, flag in Grafana.
- **Promotion gate:** new model replaces old one only if backtest MAPE improves by ≥1 percentage point.

### 5.5. Why no LLM in the forecasting loop

The LLM never produces forecast numbers. It only:
- Detects forecasting intent.
- Calls the Predictive Service.
- Narrates the response in French.

This is a deliberate separation. Mixing them is how you lose accuracy and confidence calibration in the same step.

---

## 6. Security, Governance & Multilingual

### 6.1. Database access (the most critical part)

- **No direct connection to LPN's Oracle.** This is a hard constraint imposed by LPN, not just a preference. The architecture enforces it: there is no JDBC driver for Oracle anywhere in your service code. This is a feature, not a limitation.
- **Single application database: PostgreSQL 16.** Two logical schemas inside it:
  - `business`: imported from Compiere snapshots, holds the ~80 business tables.
  - `app`: your application's own data — conversation history, audit log, feedback, import history, model metadata.
- **Two PostgreSQL roles:**
  - `lpn_app_admin`: used by Reporting Service and Data Import Pipeline. Full access to `app` schema; INSERT/UPDATE/DELETE/TRUNCATE on `business` schema (needed for imports).
  - `lpn_ai_readonly`: used **only** by the SQL Executor when running AI-generated SQL. SELECT-only on the `business` schema, no access to `app` schema. Even if the LLM generates `DROP TABLE`, the role can't execute it.
- **Connection-level kill switch.** Statement timeout: 30 seconds. Result row cap: 10,000. Both enforced at connection-string level *and* re-enforced in the SQL Executor.
- **Import-time integrity:** the Data Import Pipeline runs as `lpn_app_admin`. It validates manifest.json before any write. It uses transactions per table — if an import fails midway, the previous snapshot remains intact.

### 6.2. SQL safety guards (extending what you have)

You already block `INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE` keywords. Add:

- **Parser-based validation, not just regex.** Use `sqlglot` (Python) called as a sidecar from the SQL Executor. This catches comments hiding malicious payloads (`SELECT 1; -- DROP TABLE …`) and other regex-evading tricks.
- **Statement count check:** must be exactly one statement.
- **Forbidden function blocklist:** `pg_read_file`, `pg_ls_dir`, `lo_import`, `lo_export`, `pg_terminate_backend`, anything in the `pg_catalog` write-side, `COPY` to/from filesystem.
- **Schema confinement:** the `lpn_ai_readonly` role only has access to the `business` schema. Even if the LLM tried to query `app.audit_log`, the role would refuse.
- **Row-limit injection:** if the SQL has no `LIMIT`, the executor adds `LIMIT 1000` before running.

### 6.3. Audit logging

Every AI query produces a row in an audit table:

```
audit_id | user_id | timestamp | question_fr | retrieved_tables | llm_model |
generated_sql | execution_status | row_count | answer_text | was_reasoning_mode
```

This is the table that protects you in any future security review. It is also the dataset you'll use to fine-tune prompts and evaluate accuracy over time.

### 6.4. Authorization model

- `viewer` (everyone): can ask questions, can see answers.
- `analyst` (IT engineers): can see SQL + raw data (debug mode in UI).
- `admin` (you, encadrant): can manage schema metadata, model versions, see audit log.
- `dg`: viewer + access to reasoning mode.

Implemented in Keycloak as roles, enforced at the API Gateway.

### 6.5. PII / sensitive field handling

- HR-related tables (salaries, personal addresses): explicitly excluded from the curated business table list. The LLM literally cannot see them in retrieval.
- B2C customer email/phone: masked at execution time (`'****@****.ma'`) unless user role is admin.

### 6.6. Error messages (per your spec)

- Internal errors → French: *"Une erreur interne est survenue. Veuillez réessayer ou reformuler votre demande."*
- Out-of-scope → *"Je suis l'assistant BI de LPN, je ne peux répondre qu'à des questions sur les données de l'entreprise."*
- Empty result → *"Je n'ai trouvé aucune donnée correspondant à votre requête."*
- Debug mode (analyst+) → expand to show SQL, retrieved tables, model used, latency.

---

## 7. The 16-Week Roadmap

Each week assumes ~25–30 hours of focused work alongside other internship duties.

### Phase 1 — Foundations & Data (Weeks 1–4)

**Week 1 — Compiere export plan + curated schema metadata + polyglot scaffolding.** Three tracks in parallel:
- *Export track (priority):* on the company PC, identify the ~50–100 real business tables in Compiere. For each, write a SQL export query (`SELECT * FROM C_ORDER WHERE ...` etc.). Save as a reusable script. Run a first export to CSV + a `schema.sql` capturing the structure. This becomes your "snapshot bundle v1" — even if it's incomplete, you have real LPN data to work with.
- *Schema metadata track:* produce `schema_metadata.csv` (table name, English description, business module, key columns with descriptions, key relations). Start in English; add French in Week 6. This is the file that drives everything downstream.
- *Scaffolding track:* set up the polyglot repo structure. One mono-repo, top-level dirs: `services-java/` (Gradle multi-module Spring Boot for `llm-orchestrator` and `sql-executor`), `services-dotnet/` (existing .NET solution refactored to host `gateway` and `reporting`), `services-python/` (uv- or poetry-managed for `schema-retrieval`, `predictive`, `data-import`). Single `docker-compose.yml` ties everything together. PostgreSQL container with two schemas: `business` and `app`.

**Week 2 — Data Import Pipeline + Schema Retrieval Service.**
- *Import track:* Python CLI tool (`data-import`). Takes a snapshot bundle (zip of CSVs + manifest.json + schema.sql), validates the manifest, runs `schema.sql` to (re)create tables in the `business` schema, COPYs the CSVs in, records the import in `app.import_history`. Idempotent: rerunning replaces. Test: import your Week 1 snapshot end-to-end.
- *Schema RAG track:* Python + FastAPI + Qdrant. Embed your `schema_metadata.csv` + the live PostgreSQL schema introspected from `information_schema`. Endpoint: `POST /v1/retrieve` returning top-K tables. Unit test with 30–50 example questions in English. Use `paraphrase-multilingual-MiniLM-L12-v2` for embeddings — multilingual now means no swap when you add French in Week 6.

**Week 3 — Spring Boot LLM Orchestrator with LangChain4j (English-only).** Re-implement the working .NET pipeline in Spring Boot:
- `OllamaChatModel` configured for two model endpoints: `arctic-text2sql-r1-7b` (SQL generation) and `llama3.2:3b` (intent + narration).
- `@AiService` interfaces for `SqlGenerationService` and `AnswerNarrationService`.
- HTTP client to Schema Retrieval Service for top-K table fetch.
- **PostgreSQL dialect** in the prompt template — modern syntax allowed (`LIMIT`, CTEs, window functions, `date_trunc`, `interval`).
- All in English. Run against your imported snapshot in PostgreSQL.

**Week 4 — SQL Executor v2 + English evaluation baseline.** Two tracks:
- *Executor track:* Spring Boot SQL Executor with Postgres JDBC only. Add `sqlglot` validation as a small Python sidecar (called over HTTP from the executor — sqlglot's PostgreSQL dialect support is the best available). Add row/timeout limits (default 30s timeout, 10K row cap). Use the `lpn_ai_readonly` role exclusively for execution.
- *Evaluation track:* build a 100-question English evaluation set covering all the question types LPN cares about (sales aggregates, joins, multi-step reasoning, status checks). Run end-to-end against your imported PostgreSQL snapshot. Record: SQL execution rate, semantic accuracy, P95 latency. **This is your published accuracy baseline for the PFE report.** If accuracy is below 70%, the schema metadata needs another pass *before* moving to French.

### Phase 2 — Microservices, Auth, and French (Weeks 5–7)

**Week 5 — Gateway (.NET 9 + YARP) + Keycloak + Reporting Service (.NET 9).** Bring up the .NET-side services:
- YARP gateway with route definitions for all backend services.
- Keycloak in Docker Compose. Define the four roles (`viewer`, `analyst`, `dg`, `admin`).
- JWT validation in YARP using Keycloak's public keys.
- Reporting Service (.NET 9 + EF Core): conversation history table, audit log table, feedback endpoint. This reuses your existing EF Core knowledge.

**Week 6 — French language bridge + French metadata.** Two tracks:
- *Bridge track:* extend the LLM Orchestrator's `IntentService` to detect language and bridge French → structured English representation before SQL generation. The narration step uses Llama 3.2 3B in the user's original language.
- *Metadata track:* add French descriptions to all entries in `schema_metadata.csv`. Re-embed in Qdrant. Now the retrieval works for both languages.
- *Re-run the eval:* translate the 100-question English eval set to French. Run end-to-end. Document the accuracy delta — this is your "language bridge cost" number for the PFE report.

**Week 7 — Frontend overhaul (the polished UI).**
- Set up the new component stack: install shadcn/ui, configure Tailwind v4 theme, add Recharts and `echarts-for-react`, install Lucide React, TanStack Query, React Hook Form + Zod.
- Build the chat interface: shadcn `Card` + `Input` + `Button` + `ScrollArea`, with conversation history in a `Sheet` sidebar. Reasoning-mode toggle (gated by role). Debug-mode toggle (gated by role) showing SQL, retrieved tables, model used, latency.
- Build the answer rendering: text answer first, expandable "Sources" section showing tables, expandable "SQL" section in debug mode.
- French-first copy throughout the UI; English fallback strings wired but not exposed in the UI yet.

### Phase 3 — Predictive ML (Weeks 8–11)

**Week 8 — Forecasting feature engineering.** In your PostgreSQL `business` schema, add derived tables for forecasting: monthly revenue per category, weekly sales per SKU, current stock levels. Implement these as either materialized views (refreshed after each import) or as Python scripts in the Data Import Pipeline that run after every snapshot load. Document which approach you chose and why.

**Week 9 — Predictive Service v1: CA forecast.** Prophet model per category. Endpoint `POST /v1/forecast/sales`. Backtest on 2021–2025. Target MAPE: ≤15% on aggregated monthly CA.

**Week 10 — Predictive Service v2: stock rupture.** LightGBM model. Endpoint `POST /v1/forecast/stock-risk`. Evaluate on a holdout window.

**Week 11 — LLM intent routing.** In the LLM Orchestrator, add an intent classifier (small system prompt or LangChain4j tool-calling) that routes between SQL pipeline and Predictive Service. Add narration prompts: *"Voici les chiffres de prévision: [X]. Réponds en français en expliquant la tendance."*

### Phase 4 — Optional cloud LLM, observability, charts (Weeks 12–14)

**Week 12 — Decision point: Groq or polish.** Look at your Week 11 evaluation accuracy. Then choose:
- *If accuracy ≥80% on French eval:* skip Groq. Spend the week on additional schema metadata refinement and harder prompt engineering for the failing 20%. The "fully local" PFE story is stronger.
- *If accuracy 70–79%:* add Groq as an optional reasoning-mode toggle. LangChain4j supports OpenAI-compatible endpoints; Groq plugs in as a `ChatModel` config. Implement per-user rate limiting (1/5min, 50/day per user, 800/day org cap). Graceful fallback to local on quota.
- *If accuracy <70%:* something is broken in the schema RAG or prompts. Don't add Groq yet — fix the underlying issue first.

**Week 13 — Observability stack.** Prometheus scraping all services (Spring Boot Actuator + Micrometer for the JVM services, ASP.NET prometheus-net for .NET services, `prometheus-client` for the Python services). Grafana dashboards: request rate, latency P95, LLM token usage, accuracy proxy (% of queries where the user did not retry, % thumbs-up). Loki for centralized logs. Sentry free tier for backend errors.

**Week 14 — Charts & BI dashboards in frontend.** Now the polish week:
- Build the `Forecasts` view: Tremor `Card` + Recharts `LineChart` for CA monthly forecast with confidence intervals, `BarList` for top-risk SKUs.
- Build the `Insights` view: ECharts heatmap for stock rupture risk by SKU × week, ECharts treemap for product category revenue contribution.
- Wire the Predictive Service endpoints to the frontend via TanStack Query (caching, retries, loading states).
- Polish: dark/light mode toggle (shadcn handles this natively), responsive layout, French copy throughout.

### Phase 5 — Hardening & Defense (Weeks 15–16)

**Week 15 — Evaluation suite + bug fixing.** Build a benchmark set of 100 representative questions in French, with expected answers. Run weekly. Document accuracy per query type. Fix top 10 failing patterns.

**Week 16 — PFE deliverables.** Architecture diagram (drawio/excalidraw). Final report draft. Slide deck. Demo script. Rehearse the defense. Tag a stable Docker Compose release.

### Buffer / cuts if you fall behind

If by Week 8 you're behind:
- **Cut:** Stock rupture model (Week 10). Keep CA forecast only.
- **Cut:** Fancy Grafana dashboards. Just keep Prometheus metrics + basic dashboard.
- **Cut:** Groq integration entirely. The local Pattern B is enough.
- **Keep at all cost:** Schema RAG, audit logging, Keycloak, polished frontend, French support. These are what differentiates the project at the defense.

---

## 8. Risks & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Compiere export queries return wrong / incomplete data | High | High | Validate row counts against Compiere UI before trusting an export. For each table, sample 5 rows in Compiere's UI and 5 from the exported CSV, confirm match. Document the validation procedure. |
| Snapshot data becomes stale during demo | Medium | Low | UI shows "données à jour au [date]" banner. Re-export weekly during your active development. For the PFE demo, run a fresh export the week before. |
| Schema RAG retrieval misses relevant tables | Medium | High | Build the curated CSV with care. Add manual aliases ("commande" → C_ORDER). Evaluate retrieval recall on a held-out question set before integrating. |
| Arctic-Text2SQL-R1-7B underperforms on Compiere's cryptic columns | Medium | High | Schema metadata enrichment (column descriptions in the CSV) is the fix. Few-shot examples with real Compiere queries also help significantly. Pattern A (Qwen2.5-Coder) as fallback. |
| French accuracy significantly lower than English | Medium | Medium | Use the small bridge model (Llama 3.2 3B) to translate to structured English representation. Document the gap. Consider fine-tuning the bridge model on French DG queries if the gap exceeds 10%. |
| 8GB VRAM tight with Pattern B | Low | Medium | Both models in Q4_K_M sum to ~6.5GB. Confirmed safe. If it gets tight, drop the formatter to Q3_K_M (~1.5GB) at slight quality cost. |
| Polyglot complexity slows you down | Medium | Medium | Each stack is in a separate folder with its own README, build script, and Dockerfile. Weekly time-box of 1 day for build/CI maintenance — if it takes more, simplify. |
| Docker memory pressure on 16GB RAM laptop | Medium | Medium | Limit each container's memory in compose. Predictive ~300MB. Schema retrieval ~500MB. Postgres ~500MB. .NET services ~400MB each. Spring Boot services ~600MB each. Ollama ~7GB (incl. model). Total ~10–11GB → comfortable. Close other apps when running ML training. |
| Solo dev burnout | Medium | High | Stick to the roadmap. Don't gold-plate. Cut features, not weeks. |
| LPN ERP migration during your internship | Low | Critical | Out of your control. The architecture isolates schema metadata as data — only the CSV needs replacing on migration, not the code. |

---

## 9. Immediate Next Steps (This Week)

To unblock momentum, before any new code:

1. **Today (30 min):** Pull the Pattern B models into Ollama:
   ```
   docker exec -it lpn-ollama ollama pull a-kore/Arctic-Text2SQL-R1-7B
   docker exec -it lpn-ollama ollama pull llama3.2:3b
   ```
   ~6.5 GB total. Worth pulling now while you have time. Optional: also pull `qwen2.5-coder:7b-instruct` as a Pattern A fallback (~4.5 GB more), so you can switch quickly if Pattern B disappoints.

2. **Today:** On the company PC, confirm what export tools you have access to in Compiere. Can you `SELECT * FROM ANY_TABLE` and save to CSV? Can you export the schema definition (`CREATE TABLE ...`) for the tables you care about? Document what works. The richer your export tooling, the easier Week 1 will be.

3. **Tomorrow:** Open a new Git branch `feature/architecture-v2`. Sketch the polyglot folder structure:
   ```
   /
   ├── services-java/          (Gradle multi-module)
   │   ├── llm-orchestrator/
   │   └── sql-executor/
   ├── services-dotnet/        (existing .NET solution, refactored)
   │   ├── gateway/
   │   └── reporting/
   ├── services-python/        (uv or poetry workspaces)
   │   ├── schema-retrieval/
   │   ├── predictive/
   │   └── data-ingestion/
   ├── frontend/               (existing React/Vite)
   ├── infra/
   │   ├── docker-compose.yml
   │   └── grafana/
   └── docs/
       └── schema_metadata.csv  ← the most important file
   ```

4. **Wednesday-Thursday:** Start `schema_metadata.csv` in English. Populate the first 20 business tables you've already explored (`C_ORDER`, `C_ORDERLINE`, `C_INVOICE`, `C_INVOICELINE`, `C_BPARTNER`, `M_PRODUCT`, etc.). For each: table name, English description, business module, key columns with descriptions, key relations. Get to 50 tables by end of week.

5. **Friday:** Empty Spring Boot 3 + Java 21 multi-module project committed and building (no logic, just `./gradlew build` succeeds). Confirm `langchain4j-spring-boot-starter` and `langchain4j-ollama-spring-boot-starter` are pinned in version catalog.

6. **Next Monday:** Start Week 1 of the roadmap proper.

That's it. The rest follows from these things. The most important deliverable this week is the schema CSV, not any code.

---

## 10. Appendix — Full API Contracts

### LLM Orchestrator

```
POST /v1/qa
Headers: Authorization: Bearer <jwt>
Body:
{
  "user_id": "...",
  "question": "Quel est le CA de mars 2026 ?",
  "mode": "auto" | "local" | "reasoning",
  "context_filters": {
    "client_id": "...",        // optional
    "date_range": ["2026-03-01", "2026-03-31"]
  },
  "debug": false
}

Response:
{
  "answer_fr": "Le chiffre d'affaires de mars 2026 est de 2.4M MAD, en hausse de 8% vs mars 2025.",
  "intent": "sql_query" | "forecast" | "out_of_scope",
  "confidence": 0.87,
  "sources": ["C_INVOICE", "C_INVOICELINE"],
  "model_used": "llama3.1-8b-local" | "llama-3.3-70b-groq",
  "latency_ms": 2840,
  "debug": {                              // only if debug=true and role allows
    "retrieved_tables": [...],
    "generated_sql": "SELECT ...",
    "raw_data": [...]
  }
}
```

### Schema Retrieval Service

```
POST /v1/retrieve
{ "question_fr": "...", "top_k": 8 }

Response:
{
  "tables": [
    {
      "name": "C_ORDER",
      "description_fr": "Commandes clients - en-têtes",
      "module": "Ventes",
      "columns": [...],
      "score": 0.91
    },
    ...
  ]
}
```

### Predictive Service

```
POST /v1/forecast/sales      (defined in §5.3)
POST /v1/forecast/stock-risk (defined in §5.3)
GET  /v1/models              # list versions
GET  /v1/models/{id}/metrics # backtest metrics
POST /v1/models/train        # admin only
```

### Reporting Service

```
GET  /v1/conversations?user_id=...&from=...&to=...
GET  /v1/conversations/{id}
GET  /v1/audit?from=...&to=...     # admin/IT only
POST /v1/feedback                  # user thumbs up/down on an answer
```

---

## Closing Note

The single sentence to remember: **a curated schema CSV plus a small Schema Retrieval service is what turns your 20-table PoC into an 824-table production system.** Everything else in this document is scaffolding around that one decision. Get Week 1 right and the rest is execution.

Good luck. The PoC you've already built is real engineering — don't let anyone tell you otherwise during the defense.

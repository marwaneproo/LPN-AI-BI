# AI-BI STARTER — Prompts prêts à copier + modèle recommandé par tâche

> **Mode d'emploi (Youssef).**
> 1. Fais l'ÉTAPE 0 de `01_INTERN_PROJECT_TASKS.md` (dossier `D:\AI_BI_STARTER`, copie des
>    3 fichiers, `git init`).
> 2. Pour chaque tâche : ouvre une **nouvelle session** dans `D:\AI_BI_STARTER` avec le
>    **modèle indiqué**, colle **[PRÉAMBULE COMMUN] + [PROMPT DE LA TÂCHE]**, laisse
>    travailler, vérifie les critères d'acceptation, committe, passe à la suivante.
> 3. La session ne connaît RIEN du projet parent — c'est voulu : tout le contexte nécessaire
>    est dans les fichiers copiés à l'étape 0. Ne colle jamais de données LPN réelles.
>
> **Grille des modèles.** Boilerplate/scaffold/docs → *Sonnet 4.6 (effort medium)* : rapide,
> fiable, économique. Code sensible ou UI riche → *Sonnet 4.6 (effort high)*. Pipelines
> complexes / intégration multi-services → *Codex GPT-5.5 (high)* (ou Sonnet 4.6 high si
> indisponible). Si tu as *Sonnet 5*, il remplace avantageusement Sonnet 4.6 partout.

---

## PRÉAMBULE COMMUN — à coller au début de CHAQUE prompt

```
You are a senior full-stack engineer building a teaching-grade microservices project for two
interns. You are working inside the repository folder AI_BI_STARTER (current directory).

MANDATORY FIRST STEP: read docs/PROJECT_BRIEF.md entirely (for the very first task it is
still at the repo root as 00_INTERN_PROJECT_CONTEXT.md). It is the single source of truth:
architecture, the 3 services and their exact responsibilities, ports, database schema,
technology decisions. Never contradict it, never invent beyond it.

HARD RULES:
1. Scope: touch ONLY the files listed in the task. One task = one coherent, committable unit.
2. Stack is fixed: Python 3.11 + FastAPI for all 3 services; React 18 + TypeScript + Vite +
   Recharts for the frontend; PostgreSQL 16 + pgvector; Ollama on the host. No extra
   frameworks, no ORM (plain SQL via psycopg), no message broker, no Kubernetes.
3. Security is the point of the exercise: only data-access-service ever connects to
   PostgreSQL at runtime, always with the read-only role; SQL validation is mandatory and
   tested; no secrets hardcoded (env vars + .env.example updated with every new variable).
4. All user-facing strings (frontend labels, API error messages) in FRENCH. Code,
   comments and identifiers in English.
5. Code must be readable by juniors: small files, explicit names, short docstrings on every
   module, type hints everywhere. Prefer boring and clear over clever.
6. Every service gets: pyproject.toml, Dockerfile (slim, non-root user), tests/ with pytest,
   GET /health. Every task ends with its tests passing.
7. Synthetic data only. This repository must never contain real business data.
8. FINAL REPORT required: files created/modified, how to run, test results (paste the pytest
   summary), anything intentionally left for a later task.
```

---

## TÂCHE 0 — Squelette + base + seed
**Modèle : Sonnet 4.6 — effort medium** *(scaffolding + SQL, aucune subtilité algorithmique)*

```
TASK: Bootstrap the repository exactly as specified in section "TÂCHE 0" of
01_INTERN_PROJECT_TASKS.md (read that section now — it lists the full tree, the Postgres
docker-compose service, the 11-table schema, roles/grants, pgvector, and the synthetic seed
generator).

Key requirements to honor precisely:
- Copy 00_INTERN_PROJECT_CONTEXT.md to docs/PROJECT_BRIEF.md (verbatim). Create
  docs/ARCHITECTURE.md with the mermaid diagram translated from the brief's ASCII diagram.
- db/init/01_roles_schemas.sql: schemas business + meta, extension vector, roles app_admin
  (password from env) and app_readonly (SELECT on business only — verify with a negative
  GRANT test in comments).
- db/init/02_schema.sql: the 11 tables from the brief §4, with FKs and the indexes a BI
  workload needs (dates, FKs).
- db/seed/generate_seed.py: deterministic (fixed random seed), writes db/seed/03_seed.sql;
  ~24 months (2024-07..2026-06) with a visible back-to-school seasonality (Aug-Oct peak,
  roughly 3x the low season); volumes from the brief (~400 products, ~120 clients,
  ~4000 orders, ~12000 order lines, ~3500 invoices). Order/invoice amounts must be
  consistent with their lines. Run it and commit the generated 03_seed.sql too.
- db/schema_metadata.csv: one row per table with description_fr, description_en,
  key_columns, relations — write real, useful descriptions (they feed the RAG later).
- docker-compose.yml with only the db service (pgvector/pgvector:pg16, host port 5442,
  healthcheck, init + seed mounted in /docker-entrypoint-initdb.d).
- README.md quickstart + .gitignore + .env.example.

VERIFY before reporting: docker compose up -d; wait healthy; then inside the container run
psql checks: 11 tables present, orders count ~4000, app_readonly can SELECT on business but
gets "permission denied" on CREATE TABLE, extension vector installed. Paste the outputs.
Suggested commit: feat: repo skeleton, postgres+pgvector, synthetic seed
```

---

## TÂCHE 1 — `data-access-service`
**Modèle : Codex GPT-5.5 — high** *(code sensible : c'est le verrou de sécurité de toute la
plateforme ; alternative : Sonnet 4.6 high)*

```
TASK: Build services/data-access-service exactly as specified in section "TÂCHE 1" of
01_INTERN_PROJECT_TASKS.md (read it now). This service is the ONLY runtime gateway to
PostgreSQL and its validator is the security backbone of the whole platform.

Design constraints:
- FastAPI, port 8003. POST /v1/query {sql, max_rows?} -> {columns, rows, row_count,
  duration_ms, truncated}. GET /health -> {status, db_ok}.
- app/validator.py built on sqlglot (postgres dialect): exactly one statement; must parse;
  top-level node must be SELECT (WITH...SELECT allowed); reject any write/DDL/DCL token
  anywhere (INSERT, UPDATE, DELETE, DROP, CREATE, ALTER, TRUNCATE, GRANT, REVOKE, COPY,
  CALL, DO, SET, EXECUTE); reject multiple statements even inside comments/strings tricks;
  French error messages naming the violated rule.
- app/executor.py: psycopg connection with the READ-ONLY DSN from env, statement_timeout
  15000 ms set per session, fetch capped at max_rows (default 5000, hard max 5000) with
  truncated flag, JSON-safe conversion (date/datetime -> ISO string, Decimal -> float,
  None kept).
- Config via pydantic-settings; update .env.example.
- Dockerfile slim non-root; pyproject.toml; README.md of the service.

TESTS (pytest, >=10, no live DB needed for the validator part): happy SELECT; each rejected
keyword; "SELECT 1; DROP TABLE x" rejected; "SELECT 1 -- ; DROP" accepted (comment is
harmless) — think carefully about which of these are genuinely dangerous; WITH...SELECT
accepted; truncation logic unit-tested with a fake cursor. Add one integration test hitting
the real db, marked with @pytest.mark.integration and skipped by default.

VERIFY: uvicorn locally against the running db from TÂCHE 0; curl one happy query and one
rejected UPDATE; paste both responses and the pytest summary.
Suggested commit: feat(data-access): validated read-only SQL gateway
```

---

## TÂCHE 2 — `bi-service`
**Modèle : Sonnet 4.6 — effort medium** *(endpoints + SQL de KPI, pattern répétitif)*

```
TASK: Build services/bi-service exactly as specified in section "TÂCHE 2" of
01_INTERN_PROJECT_TASKS.md (read it now).

Design constraints:
- FastAPI, port 8001. It NEVER connects to PostgreSQL: every endpoint sends its SQL to
  data-access-service (URL from env) via httpx and reshapes the result.
- Endpoints (all GET, optional from/to ISO-date query params applied as WHERE filters):
  /v1/bi/overview, /v1/bi/ca-mensuel, /v1/bi/top-produits?limit=10,
  /v1/bi/top-clients?limit=10, /v1/bi/ventes-par-categorie, /v1/bi/stock-faible?seuil=20.
  Response shapes exactly as listed in the task file (French field names as given).
- All SQL lives in app/queries.py as named, commented constants (parameterized safely —
  build WHERE clauses with validated ISO dates only, never raw string interpolation of
  user input).
- KPI definitions: ca = sum of paid+unpaid invoice totals; clients_actifs = distinct
  clients with >=1 order in period; panier_moyen = ca_commandes / nb_commandes;
  taux_paiement = paid invoices / all invoices (%). Document each in a docstring.
- Pydantic response models so the Swagger /docs is self-explanatory. GET /health checks
  data-access reachability.

TESTS: pytest with the data-access client mocked (respx or monkeypatch) for every endpoint,
plus date-validation rejection tests (bad date -> 422). One optional integration test.
VERIFY: with db + data-access running, curl /v1/bi/overview and /v1/bi/ca-mensuel; the
numbers must be plausible against the seed (~24 monthly rows). Paste outputs + pytest summary.
Suggested commit: feat(bi): KPI endpoints backed by data-access
```

---

## TÂCHE 3 — `assistant-service`
**Modèle : Codex GPT-5.5 — high** *(la tâche la plus complexe : RAG pgvector + Ollama +
garde-fous ; alternative : Sonnet 4.6 high)*

```
TASK: Build services/assistant-service exactly as specified in section "TÂCHE 3" of
01_INTERN_PROJECT_TASKS.md (read it now, carefully — it defines the full pipeline, the
indexing CLI exception, and the degraded mode).

Design constraints:
- FastAPI, port 8002. POST /v1/chat {question} -> {answer, sql, rows, columns, latency_ms,
  degraded}. GET /health reports both dependencies (data-access, Ollama).
- app/embeddings.py: try sentence-transformers all-MiniLM-L6-v2; if unavailable at runtime,
  fall back to a deterministic hashing embedder (document the trade-off). Both produce the
  same dimension (384) so the pgvector column is stable.
- Indexing is ADMIN-time, not runtime: python -m app.index_schema reads
  db/schema_metadata.csv, embeds description_fr + description_en + key_columns, and
  upserts into meta.schema_embeddings using the ADMIN DSN (env). Idempotent (per-table
  primary key upsert). Runtime NEVER writes.
- app/retrieval.py: embed the question, retrieve top-4 tables by cosine distance (<=>)
  — this SELECT goes through data-access-service like any other read.
- app/llm.py: minimal Ollama HTTP client (POST /api/generate), models and timeouts from
  env (defaults qwen2.5-coder:1.5b for SQL, llama3.2:3b for narration), low temperature
  for SQL.
- app/pipeline.py: retrieve -> SQL prompt (rules: PostgreSQL dialect, SELECT only, schema
  business, ONLY the provided tables, no explanations — SQL between ```sql fences) ->
  extract SQL -> local guards (starts with SELECT/WITH; every referenced table is in the
  retrieved set — parse with sqlglot) -> execute via data-access -> narration prompt
  (short French answer, formatted numbers, mention the period if any) -> response.
  Destructive/off-topic questions must be refused BEFORE any LLM call when detectable
  (keyword screen), and always by the guards after generation.
- Prompts as plain-text files in app/prompts/ (sql_generation.txt, narration.txt).
- Degraded mode: Ollama unreachable -> HTTP 503 with a clear French message; data-access
  down -> 502 French message. Never a stack trace to the client.

TESTS (pytest, Ollama and data-access mocked, >=8): happy path; destructive question
refused with no data-access call; generated SQL referencing an unknown table rejected;
fenced-SQL extraction robustness (with/without fences, leading chatter); Ollama down ->
503; indexing CLI idempotence (against a fake connection).
VERIFY live if Ollama is installed: index the schema, ask "Quel est le chiffre d'affaires
total de 2025 ?" and paste the full JSON response. Otherwise paste the mocked test summary
and the /health output showing ollama_ok=false handled gracefully.
Suggested commit: feat(assistant): RAG + NL-to-SQL + narration pipeline
```

---

## TÂCHE 4 — Frontend : squelette
**Modèle : Sonnet 4.6 — effort medium** *(scaffold Vite standard)*

```
TASK: Build the frontend skeleton exactly as specified in section "TÂCHE 4" of
01_INTERN_PROJECT_TASKS.md (read it now).

Constraints: Vite react-ts template in frontend/; react-router-dom with /assistant and
/tableau-de-bord (/ redirects to /assistant); a common shell with a French sidebar
("Assistant", "Tableau de bord") and a header showing the app name "AI-BI Starter";
light clean theme via CSS variables (system font stack, one accent color #2f7ff0, cards
with subtle borders — no UI framework, no Tailwind); src/lib/api.ts exposing typed
fetch helpers reading VITE_BI_URL and VITE_ASSISTANT_URL from env (.env.example);
clean placeholder content on both pages; frontend/README.md (dev, build, env).

VERIFY: npm install && npm run build must pass with zero TypeScript errors; npm run dev
serves both routes. Paste the build output.
Suggested commit: feat(frontend): app shell with assistant + dashboard routes
```

---

## TÂCHE 5 — Frontend : tableau de bord BI
**Modèle : Sonnet 4.6 — effort high** *(qualité visuelle des graphiques + états UI)*

```
TASK: Build the /tableau-de-bord page exactly as specified in section "TÂCHE 5" of
01_INTERN_PROJECT_TASKS.md (read it now), on top of the existing shell and api client.

Constraints: KPI card row (CA total, Commandes, Clients actifs, Panier moyen, Taux de
paiement) fed by /v1/bi/overview; Recharts BarChart for "CA mensuel" (French month labels);
horizontal ranked bars for "Top produits" and "Top clients"; PieChart "Ventes par
catégorie" (group beyond 6 slices into "Autres"); table "Stock faible". Amounts formatted
with Intl.NumberFormat('fr-MA') + " MAD"; a simple period filter (year selector defaulting
to the latest full year, applied as from/to on every call); per-widget loading skeletons,
a French error state with a retry button, and an empty state. Keep components small:
src/features/bi/ with one file per widget and a useBiData hook per endpoint.

VERIFY: with db + data-access + bi-service running, the page shows real seeded values
(CA mensuel must show ~24 bars with a visible seasonal peak). npm run build passes.
Paste a summary of the rendered values for one KPI to prove live wiring.
Suggested commit: feat(frontend): BI dashboard wired to bi-service
```

---

## TÂCHE 6 — Frontend : assistant conversationnel
**Modèle : Sonnet 4.6 — effort high** *(UX du chat + gestion fine des erreurs)*

```
TASK: Build the /assistant page exactly as specified in section "TÂCHE 6" of
01_INTERN_PROJECT_TASKS.md (read it now).

Constraints: conversation thread (user/assistant bubbles); textarea with Enter-to-send
(Shift+Enter = newline); "L'assistant réfléchit…" indicator while awaiting /v1/chat;
each answer renders: the French narrated text, a collapsible <details> block "Voir le SQL"
with the SQL in a <pre>, and a results table capped at 50 displayed rows ("… N lignes au
total"); HTTP 503 -> pedagogical French banner explaining that Ollama must be started
(with the two ollama pull commands); history persisted in localStorage with a "Nouvelle
conversation" button; on first load, 4 clickable example questions in French (use
realistic ones for the seeded schema). Keep it in src/features/assistant/.

VERIFY: live demo against the running stack — send "Quel est le chiffre d'affaires total
de 2025 ?" and confirm narration + SQL + table render (or, without Ollama, confirm the 503
banner). npm run build passes. Paste what you observed.
Suggested commit: feat(frontend): conversational assistant page
```

---

## TÂCHE 7 — Intégration + smoke test de bout en bout
**Modèle : Codex GPT-5.5 — high** *(multi-services, réseau compose, débogage réel)*

```
TASK: Finalize the integration exactly as specified in section "TÂCHE 7" of
01_INTERN_PROJECT_TASKS.md (read it now).

Constraints: extend docker-compose.yml with the 3 services built from their Dockerfiles
(internal compose-network URLs for inter-service env vars; db healthcheck gating with
depends_on: condition: service_healthy; Ollama stays on the host — use
host.docker.internal:11434 and add extra_hosts: host-gateway for Linux compatibility).
Write scripts/smoke_test.py (stdlib + httpx only): checks the 3 /health endpoints, 3 BI
endpoints for non-empty coherent data, then sends 10 French questions to /v1/chat —
8 legitimate (CA by year/month, top products, top clients, counts, stock) and 2 destructive
("Supprime toutes les commandes", "Mets à jour les prix à zéro") that MUST be refused;
prints a PASS/FAIL line per case and exits non-zero on failure. Update the root README:
full Docker startup, native startup (3 uvicorns + npm run dev), troubleshooting section
(Ollama missing, port conflicts, reseeding the db by dropping the volume).

VERIFY: run docker compose up -d --build then python scripts/smoke_test.py; iterate until
>=8/10 legitimate PASS and 2/2 refusals PASS (if Ollama is unavailable on this machine,
the assistant cases may report SKIPPED-degraded — say so explicitly). Paste the smoke test
output in full.
Suggested commit: feat: full compose integration + e2e smoke test
```

---

## TÂCHE 8 — Docs stagiaires + backlog
**Modèle : Sonnet 4.6 — effort medium** *(rédaction française soignée)*

```
TASK: Write the intern-facing documentation exactly as specified in section "TÂCHE 8" of
01_INTERN_PROJECT_TASKS.md (read it now). Everything in FRENCH.

Constraints: docs/ONBOARDING.md — machine prerequisites (Docker, Node 20+, Python 3.11,
Ollama with qwen2.5-coder:1.5b + llama3.2:3b, sized for ~4 GB VRAM laptops), step-by-step
install, a guided tour of the repo (one paragraph per service explaining its role and its
key files), and a fully guided "première modification" exercise: add a "CA par commercial"
KPI end-to-end (SQL in queries.py -> new bi-service endpoint -> new dashboard widget),
with the exact code snippets. docs/BACKLOG.md — the phase-2 items from the task file,
each with: objectif, pistes techniques, définition de terminé, difficulté (facile/moyen/
difficile). docs/ARCHITECTURE.md — finalize: mermaid diagram, ports table, request/response
example for each API endpoint, and the security rules (read-only chain, validation,
what interns must NEVER change without asking).

VERIFY: follow your own ONBOARDING.md from a clean state mentally and fix any gap; every
command in the docs must be copy-paste correct for Windows PowerShell AND bash where they
differ. Report the final repo tree.
Suggested commit: docs: intern onboarding + backlog
```

---

## Après la TÂCHE 8 (manuel — Youssef)

1. Relire le dépôt une dernière fois (surtout : aucun secret, aucune donnée réelle).
2. `git push` final, puis sur GitHub : repo **privé**, inviter Marwane + binôme
   (Settings → Collaborators), protéger `main` si tu veux des PR.
3. Leur envoyer : le lien du repo + `docs/ONBOARDING.md` comme point de départ + rappel
   des modèles Ollama à télécharger avant la première exécution.
4. Première réunion : leur faire dérouler le smoke test, puis leur assigner la
   « première modification » de l'ONBOARDING comme exercice d'échauffement, avant
   d'attaquer le BACKLOG selon les priorités de M. Mounir.

# AI-BI STARTER — Plan de construction (tâches séquencées)

> **Rôle de ce fichier.** La liste ordonnée des tâches qui font passer d'un dossier vide à un
> projet complet prêt à pousser sur GitHub. Chaque tâche est exécutée par un modèle d'IA dans
> une **nouvelle session qui ne connaît rien du projet parent** — les prompts exacts (et le
> modèle recommandé pour chacun) sont dans `02_INTERN_PROJECT_PROMPTS.md`.
> Règle d'or : **une tâche = une session = un commit**. On vérifie, on committe, on passe à la
> suivante.

---

## ÉTAPE 0 (manuelle — Youssef, 5 minutes)

1. Créer le dossier du nouveau projet : `D:\AI_BI_STARTER` (nom libre, éviter les espaces).
2. Y copier les 3 fichiers de `D:\LPN_PROJECT\intern-handoff\` tels quels.
3. Dans le dossier : `git init -b main`.
4. Après la TÂCHE 0 : créer le repo GitHub **privé** (ex. `ai-bi-starter`), `git remote add origin …`,
   `git push -u origin main`, puis inviter les stagiaires en collaborateurs.
5. Les tâches suivantes se poussent au fil de l'eau (un commit par tâche).

**Prérequis machine (une fois)** : Docker Desktop, Node 20+, Python 3.11 + `uv` (ou venv+pip),
Ollama installé avec `ollama pull qwen2.5-coder:1.5b` et `ollama pull llama3.2:3b`.

---

## TÂCHE 0 — Squelette du dépôt, base de données et données synthétiques

**Objectif.** Le socle : arborescence, docker-compose (PostgreSQL+pgvector seul pour l'instant),
schéma SQL, générateur de données synthétiques, documentation d'entrée.

**Livrables.**
- Arborescence :
  ```
  ai-bi-starter/
    README.md                  ← quickstart complet (Docker, Ollama, ordres de démarrage)
    .gitignore                 ← Python, Node, .env, __pycache__, dist, .venv
    .env.example               ← toutes les variables (ports, DSN, modèles Ollama)
    docker-compose.yml         ← service db uniquement (les 3 services s'ajouteront en T7)
    db/
      init/01_roles_schemas.sql   ← rôles app_admin / app_readonly, schémas business + meta,
                                    extension pgvector, grants (readonly = SELECT sur business)
      init/02_schema.sql          ← les 11 tables du brief (§4) + index utiles
      seed/generate_seed.py       ← génère db/seed/03_seed.sql (déterministe, seed aléatoire fixe)
      schema_metadata.csv         ← 1 ligne/table : table_name, description_fr, description_en,
                                    key_columns, relations
    docs/
      PROJECT_BRIEF.md         ← copie conforme de 00_INTERN_PROJECT_CONTEXT.md
      ARCHITECTURE.md          ← le diagramme du brief (mermaid) + décisions techniques
    services/                  ← vide (3 sous-dossiers .gitkeep)
    frontend/                  ← vide (.gitkeep)
  ```
- PostgreSQL : image `pgvector/pgvector:pg16`, port hôte **5442**, volume nommé, healthcheck ;
  les fichiers de `db/init/` + le seed généré montés dans `/docker-entrypoint-initdb.d/`.
- Données synthétiques (~24 mois, saisonnalité rentrée scolaire, volumes du brief §4).
  Le générateur écrit du SQL pur (INSERT) pour rester lisible et rejouable.

**Acceptation.** `docker compose up -d` puis (via psql dans le conteneur) :
les 11 tables existent ; `SELECT count(*) FROM business.orders` ≈ 4 000 ;
`app_readonly` peut lire `business` mais `CREATE TABLE` lui est refusé ;
`SELECT extversion FROM pg_extension WHERE extname='vector'` répond. Commit :
`feat: repo skeleton, postgres+pgvector, synthetic seed`.

---

## TÂCHE 1 — `data-access-service` (la porte unique vers la base)

**Objectif.** FastAPI (port 8003) : exécution contrôlée de SQL en lecture seule.

**Livrables.** `services/data-access-service/` : app FastAPI (`app/main.py`, `app/config.py`,
`app/validator.py`, `app/executor.py`), `pyproject.toml`, `Dockerfile`, `tests/`.
- `POST /v1/query` `{sql: str, max_rows?: int}` → `{columns: [...], rows: [[...]], row_count, duration_ms}`.
- Validation **avant** exécution (module `validator.py`, basé sqlglot dialecte postgres) :
  une seule instruction ; SELECT (ou WITH…SELECT) uniquement ; refus de tout mot-clé
  d'écriture/DDL ; refus des `;` multiples ; erreurs → HTTP 400 avec raison en français.
- Exécution : connexion `app_readonly` (DSN via env), `statement_timeout` 15 s,
  plafond 5 000 lignes (tronque + `truncated: true`), types JSON-sérialisables (dates ISO, Decimal→float).
- `GET /health` → `{status, db_ok}`.

**Acceptation.** Tests pytest (≥ 10) : SELECT valide passe ; UPDATE/DELETE/DROP/INSERT/
CREATE/ALTER/GRANT/multi-instructions/commentaire piégé `; DROP` → 400 ; troncature à
max_rows ; types sérialisés. Test d'intégration (marqué, optionnel) contre la vraie db.
Commit : `feat(data-access): validated read-only SQL gateway`.

---

## TÂCHE 2 — `bi-service` (les indicateurs)

**Objectif.** FastAPI (port 8001) : endpoints KPI prêts pour le frontend. Aucune connexion
directe à la base : chaque endpoint appelle `data-access-service`.

**Livrables.** `services/bi-service/` (même structure). Endpoints (tous `GET`, paramètres
optionnels `from`/`to` en dates ISO) :
- `/v1/bi/overview` → `{ca_total, nb_commandes, nb_clients_actifs, panier_moyen,
  taux_paiement}` sur la période.
- `/v1/bi/ca-mensuel` → `[{mois: "2025-09", ca, nb_commandes}]`.
- `/v1/bi/top-produits?limit=10` → `[{product, category, quantite, ca}]`.
- `/v1/bi/top-clients?limit=10` → `[{client, ville, type, ca, nb_commandes}]`.
- `/v1/bi/ventes-par-categorie` → `[{category, ca, part_pourcent}]`.
- `/v1/bi/stock-faible?seuil=20` → `[{product, quantity_on_hand, quantity_reserved}]`.
- `GET /health` (vérifie aussi la joignabilité de data-access).
Le SQL de chaque KPI vit dans `app/queries.py` (constantes lisibles, commentées).

**Acceptation.** Tests pytest avec client data-access **mocké** (réponses simulées) ;
test d'intégration optionnel bout-en-bout. Toutes les réponses documentées dans le
`/docs` Swagger auto. Commit : `feat(bi): KPI endpoints backed by data-access`.

---

## TÂCHE 3 — `assistant-service` (le traitement IA : RAG + SQL + narration)

**Objectif.** FastAPI (port 8002) : le pipeline complet question → réponse narrée.

**Livrables.** `services/assistant-service/` :
- `app/embeddings.py` — embedding **local et léger** des descriptions de
  `db/schema_metadata.csv` (sentence-transformers `all-MiniLM-L6-v2` si dispo, sinon repli
  hachage déterministe comme le parent) ; stockage dans `meta.schema_embeddings`
  (pgvector) via data-access ? **Non** — exception documentée : l'écriture des embeddings
  est un acte d'administration, faite par un script CLI `python -m app.index_schema`
  avec le DSN admin, PAS par le service au runtime. Au runtime le service lit les
  embeddings en SELECT via data-access.
- `app/retrieval.py` — embarque la question, similarité cosinus (`<=>` pgvector),
  top-4 tables + leurs descriptions.
- `app/llm.py` — client Ollama HTTP (base URL, modèles et timeouts via env ;
  défauts : `qwen2.5-coder:1.5b` pour le SQL, `llama3.2:3b` pour la narration).
- `app/pipeline.py` — orchestration : retrieve → prompt SQL (règles : « uniquement SELECT,
  schéma business, tables fournies, dialecte PostgreSQL ») → extraction du SQL de la
  réponse → garde-fous locaux (regex SELECT-only + tables ∈ retrieved) → `POST data-access
  /v1/query` → prompt narration (réponse courte en français, chiffres formatés) →
  `{answer, sql, rows, columns, latency_ms, degraded}`.
- `POST /v1/chat` `{question: str}` ; `GET /health` (statuts db-via-data-access + Ollama).
- **Mode dégradé** : si Ollama injoignable → HTTP 503 avec message clair français
  (« Le serveur de modèles n'est pas démarré… ») ; jamais de crash.
- Prompts dans `app/prompts/` en fichiers texte séparés (comme le parent).

**Acceptation.** Tests pytest avec Ollama **mocké** (≥ 8) : pipeline heureux ; question
destructive → refus sans appel à data-access ; SQL généré référencant une table inconnue →
rejet ; Ollama down → 503. Script `python -m app.index_schema` idempotent (ré-exécution =
mêmes lignes). Commit : `feat(assistant): RAG + NL-to-SQL + narration pipeline`.

---

## TÂCHE 4 — Frontend : squelette (React + TS + Vite)

**Objectif.** L'app à deux périmètres, navigable, stylée, sans données réelles encore.

**Livrables.** `frontend/` : Vite React-TS ; `react-router-dom` avec `/assistant` et
`/tableau-de-bord` (redirect `/` → `/assistant`) ; coquille commune (barre latérale
française : « Assistant », « Tableau de bord ») ; thème clair simple (CSS variables,
police système) ; client API `src/lib/api.ts` (base URLs via `.env` :
`VITE_BI_URL=http://localhost:8001`, `VITE_ASSISTANT_URL=http://localhost:8002`) ;
pages placeholder propres ; `README.md` du frontend.

**Acceptation.** `npm run dev` affiche les 2 pages ; `npm run build` passe sans erreur TS.
Commit : `feat(frontend): app shell with assistant + dashboard routes`.

---

## TÂCHE 5 — Frontend : le tableau de bord BI

**Objectif.** Page `/tableau-de-bord` complète, branchée sur `bi-service`.

**Livrables.** Rangée de cartes KPI (CA, commandes, clients actifs, panier moyen, taux de
paiement) ; graphique barres « CA mensuel » ; barres horizontales « Top produits » et
« Top clients » ; secteurs « Ventes par catégorie » ; tableau « Stock faible ». Recharts,
libellés FR, montants formatés (`fr-MA`, séparateur d'espace + « MAD »), états
chargement/erreur/vide soignés, filtre de période simple (sélecteur année ou plage de dates).

**Acceptation.** Avec la stack docker démarrée, la page affiche les vraies valeurs du seed ;
`npm run build` OK. Commit : `feat(frontend): BI dashboard wired to bi-service`.

---

## TÂCHE 6 — Frontend : l'assistant conversationnel

**Objectif.** Page `/assistant` complète, branchée sur `assistant-service`.

**Livrables.** Zone de conversation (bulles question/réponse) ; envoi sur Entrée ;
indicateur « réflexion… » pendant l'appel ; réponse = texte narré + bloc **SQL repliable**
(`<details>`) + tableau de résultats (max 50 lignes affichées) ; gestion du 503 Ollama
avec message pédagogique ; historique conservé en `localStorage` (bouton « Nouvelle
conversation ») ; 4 questions d'exemple cliquables au premier chargement.

**Acceptation.** Démo réelle : « Quel est le chiffre d'affaires total de 2025 ? » affiche
narration + SQL + résultat. `npm run build` OK.
Commit : `feat(frontend): conversational assistant page`.

---

## TÂCHE 7 — Intégration complète, compose final, test de bout en bout

**Objectif.** Tout démarre ensemble ; le parcours complet est vérifié et documenté.

**Livrables.**
- `docker-compose.yml` final : db + les 3 services (build depuis leurs Dockerfiles,
  env DSN/URLs internes réseau compose, healthchecks, `depends_on` conditionnels).
  Ollama reste sur l'hôte (`host.docker.internal:11434` avec `extra_hosts` si besoin).
- `scripts/smoke_test.py` : vérifie les 3 `/health`, 3 endpoints BI (valeurs non vides),
  puis 10 questions français sur `/v1/chat` (dont 2 destructives qui DOIVENT être
  refusées) ; imprime un rapport PASS/FAIL par cas.
- README racine mis à jour : démarrage Docker complet, démarrage natif (uvicorn ×3 +
  npm), section dépannage (Ollama absent, port occupé, seed à rejouer).

**Acceptation.** `docker compose up -d --build` → smoke test ≥ 8/10 questions PASS et
2/2 refus PASS. Commit : `feat: full compose integration + e2e smoke test`.

---

## TÂCHE 8 — Documentation stagiaires et backlog

**Objectif.** Que les stagiaires soient autonomes dès le premier `git pull`.

**Livrables.**
- `docs/ONBOARDING.md` (FR) : prérequis machine (dont modèles Ollama adaptés à 4 Go de
  VRAM), installation pas-à-pas, tour du code service par service, « votre première
  modification » guidée (ajouter un KPI de bout en bout : SQL → bi-service → carte frontend).
- `docs/BACKLOG.md` (FR) : phase 2 priorisée — authentification simple ; filtres de
  période avancés ; export Excel/PDF ; meilleur modèle d'embedding ; historique de chat
  persistant (nouvelle table) ; re-skin pharmacie (checklist des renommages) ;
  module prévision (description du concept Prophet du parent, sans code).
- `docs/ARCHITECTURE.md` enrichi : diagramme mermaid final, tableau des ports,
  contrat de chaque API (exemples requête/réponse), règles de sécurité.

**Acceptation.** Relecture : un développeur qui n'a jamais vu le projet peut l'installer
et faire la « première modification » sans aide. Commit : `docs: intern onboarding + backlog`.

---

## Récapitulatif des commits attendus

| # | Commit | Contenu |
|---|---|---|
| 0 | `feat: repo skeleton, postgres+pgvector, synthetic seed` | socle + db |
| 1 | `feat(data-access): validated read-only SQL gateway` | service 3 |
| 2 | `feat(bi): KPI endpoints backed by data-access` | service 1 |
| 3 | `feat(assistant): RAG + NL-to-SQL + narration pipeline` | service 2 |
| 4 | `feat(frontend): app shell with assistant + dashboard routes` | frontend socle |
| 5 | `feat(frontend): BI dashboard wired to bi-service` | périmètre BI |
| 6 | `feat(frontend): conversational assistant page` | périmètre chat |
| 7 | `feat: full compose integration + e2e smoke test` | intégration |
| 8 | `docs: intern onboarding + backlog` | remise aux stagiaires |

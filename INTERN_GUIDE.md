git# Guide de démarrage — LPN AI-BI (branche `for_interns`)

> Bienvenue ! Ce guide vous amène **pas à pas** d'un PC vide jusqu'à la
> plateforme qui tourne en local. Lisez-le en entier une fois avant de
> commencer. En cas de blocage : demandez à Youssef, ne devinez pas.

---

## 1. Ce que vous allez faire tourner

LPN AI-BI est une plateforme locale (aucune donnée ne sort de la machine) :

| Composant | Techno | Port |
|---|---|---|
| **frontend** | React + TypeScript (Vite) | 5173 |
| **llm-orchestrator** | Java 21 / Spring Boot — auth, chat IA, **tous les endpoints BI** | 8081 |
| **sql-executor** | Java 21 / Spring Boot — exécution SQL lecture seule | 8082 |
| **schema-retrieval** | Python / FastAPI — recherche de schéma (Qdrant embarqué) | 8084 |
| **predictive** | Python / FastAPI — prévisions CA (Prophet) | 8085 |
| **sql-validator** | Python / FastAPI — validation des requêtes générées | 8086 |
| **PostgreSQL** | base `lpn_ai_bi` (entrepôt + app) | 5432 |
| **Ollama** | LLM locaux (génération SQL + narration) | 11434 |

Trois usages : l'**assistant conversationnel** (français → SQL → réponse),
les **tableaux de bord BI** (`/tableau-de-bord/*`) et les **prévisions**.

---

## 2. Prérequis (à installer une seule fois)

1. **Git**.
2. **JDK 21** (Temurin/Adoptium recommandé). Vérifiez : `java -version`.
3. **Node.js 20+** (le projet est développé avec Node 22). Vérifiez : `node --version`.
4. **PostgreSQL 16 ou 18** sur le port **5432**, avec les outils en ligne de
   commande (`psql`, `pg_restore`) dans le `PATH`.
5. **uv** (gestionnaire Python) : <https://docs.astral.sh/uv/> — les services
   Python se lancent avec.
6. **Ollama** : <https://ollama.com>, puis téléchargez les modèles :
   ```powershell
   ollama pull qwen2.5-coder:7b
   ollama pull qwen2.5-coder:14b
   ollama pull llama3.1:latest
   ```
   (Machine modeste ? Commencez sans le 14b — seul le mode « raisonnement » l'utilise.)

---

## 3. Récupérer le projet

```powershell
git clone https://github.com/YoussefBahaddou/lpn-ai.git
cd lpn-ai
git checkout for_interns
```

---

## 4. Configurer l'environnement

```powershell
Copy-Item .env.example .env
```

Puis ouvrez `.env` et renseignez **obligatoirement** :

- `AUTH_BOOTSTRAP_ADMIN_PASSWORD=` → choisissez un mot de passe (12+ caractères).
  C'est le mot de passe du compte `admin` créé au premier démarrage.
- Laissez les mots de passe `POSTGRES_*` tels quels pour commencer — ils doivent
  simplement **correspondre aux rôles créés à l'étape 5**.

> Le fichier `.env` est ignoré par git : il ne doit **jamais** être commité.

---

## 5. Charger la base de données

Le dépôt ne contient **aucune donnée** (règle absolue). Demandez à Youssef le
dump PostgreSQL (`lpn_ai_bi.dump` ou `full_dump.sql`), puis :

```powershell
# variante A — dump complet (roles + base) :
psql -h localhost -p 5432 -U postgres -f full_dump.sql

# variante B — dump de la seule base lpn_ai_bi :
psql -h localhost -p 5432 -U postgres -c "CREATE DATABASE lpn_ai_bi;"
pg_restore -h localhost -p 5432 -U postgres -d lpn_ai_bi lpn_ai_bi.dump
```

Vérifiez ensuite que les deux rôles applicatifs existent et que leurs mots de
passe correspondent au `.env` :

```powershell
psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -c "\du"
# attendu : lpn_app_admin  et  lpn_ai_readonly
```

S'ils manquent, le script `infra/postgres/init/00-roles-and-schemas.sql` les crée.

Test rapide que l'entrepôt est là :

```powershell
psql -h localhost -p 5432 -U postgres -d lpn_ai_bi -c "SELECT COUNT(*) FROM mart.mart_sales_daily;"
```

---

## 6. Démarrer la plateforme

Un seul point d'entrée, à la racine :

```powershell
.\start_project.bat
```

Le lanceur : nettoie les anciens processus, démarre les 6 services dans le bon
ordre, attend leurs health-checks, écrit les logs dans `logs/` et les pid dans
`logs/_runtime/`, puis ouvre le navigateur. **Pour redémarrer proprement, il
suffit de le relancer** (il arrête d'abord ce qui tourne).

Premier démarrage : comptez plusieurs minutes (Gradle télécharge ses dépendances).
Frontend seul (si besoin) : `cd frontend && npm install && npm run dev`.

---

## 7. Se connecter et vérifier

1. Ouvrez <http://127.0.0.1:5173>.
2. Connectez-vous : utilisateur **`admin`**, mot de passe = votre
   `AUTH_BOOTSTRAP_ADMIN_PASSWORD`.
3. Vérifiez les trois piliers :
   - **BI** → les 5 pages (Vue d'ensemble, Commande, Chiffre d'affaires,
     Articles, Client) affichent des chiffres, le sélecteur de période marche.
   - **Conversation** → posez « Quel est le CA facturé de mai 2026 ? » et
     attendez la réponse (les premiers appels Ollama sont lents).
   - **Prévisions** → la page charge une courbe.

Santé backend en direct : `curl http://localhost:8081/actuator/health`.

---

## 8. Lancer les tests

```powershell
# Backend BI (19+ tests d'intégration contre la base locale — la base doit tourner)
cd services-java
.\gradlew.bat :llm-orchestrator:test --tests "*BiOverviewControllerTest*"

# Frontend (typage + build)
cd ..\frontend
npx tsc --noEmit
npm run build
```

Les deux doivent être verts **avant et après** chacune de vos modifications.

---

## 9. Carte du dépôt

| Dossier | Contenu |
|---|---|
| `frontend/src/features/bi/` | Pages, hooks, graphiques et types du tableau de bord |
| `frontend/src/styles/theme.css` | **Tout** le style (thèmes clair/sombre) |
| `services-java/llm-orchestrator/` | Auth + chat + endpoints `/v1/bi/*` (api / application / infrastructure.mart) |
| `services-java/sql-executor/` | Exécution SQL lecture seule |
| `services-python/` | schema-retrieval, sql-validator, predictive |
| `docs/LOCAL_DEV_SETUP.md` | Setup natif détaillé (référence de ce guide) |
| `docs/bi-service-roadmap/sql/` | DDL de l'entrepôt (staging / warehouse / mart) — source de vérité |
| `intern-handoff/` | Contexte, tâches et prompts prévus pour vous |
| `scripts/` | Lanceurs PowerShell |

---

## 10. Les règles de la maison

1. **Aucune donnée réelle LPN ne sort de votre machine** : jamais de dump, de
   CSV exporté ou de capture contenant des données dans un commit, un mail ou
   un chat externe.
2. `.env`, `backups/`, `logs/` sont ignorés par git — **ne forcez jamais** leur ajout.
3. Le SQL généré par l'IA s'exécute **uniquement** via le rôle lecture seule
   (`lpn_ai_readonly`) — ne donnez jamais le rôle admin à un chemin IA.
4. Travaillez sur des branches `feature/…` créées depuis `for_interns`, petites
   PR relues par Youssef.
5. UI en **français**, montants en **KDH** (milliers de dirhams) / **MAD** —
   utilisez les helpers existants (`utils/formatters.ts`), n'inventez pas de
   nouveau formatage.

Bon démarrage ! 🚀

# AI-BI STARTER — Contexte et vision du projet stagiaires

> **Rôle de ce fichier.** C'est le document de référence du nouveau projet confié aux stagiaires
> (Marwane + binôme, encadrés par M. Mounir). Il sera copié dans le nouveau dépôt sous
> `docs/PROJECT_BRIEF.md` et servira de source de vérité à la fois (a) aux modèles d'IA qui
> construiront le squelette du projet (voir `01_INTERN_PROJECT_TASKS.md`) et (b) aux stagiaires
> qui le continueront. Toute décision d'architecture non couverte ici se tranche avec Youssef
> ou M. Mounir — on n'invente pas.

---

## 1. D'où vient ce projet (le parent)

Le projet parent est **LPN AI-BI** : la plateforme PFE de Youssef Bahaddou construite pour la
Librairie Papeterie Nationale. C'est un système **réel et fonctionnel** composé de microservices
(Java Spring Boot + Python FastAPI), d'un frontend React, d'un entrepôt de données PostgreSQL
et de modèles LLM **exécutés localement via Ollama** (aucune donnée ne quitte l'entreprise).
Il offre trois usages : un **assistant conversationnel** (question en français → SQL généré,
validé, exécuté en lecture seule → réponse narrée), des **tableaux de bord BI** et une
**prévision du chiffre d'affaires**.

Ce nouveau projet est une **version découpée et simplifiée** de cette plateforme, pensée pour
être reprise, comprise et étendue par deux stagiaires — puis adaptée selon les exigences de
M. Mounir (par exemple re-thématisée vers la **gestion de pharmacie**, domaine de leur
mini-projet d'entretien).

**Règle absolue : ce dépôt ne contient AUCUNE donnée réelle de la LPN.** Toutes les données
sont synthétiques, générées par script. Le schéma métier est un commerce générique
(produits / clients / commandes / factures / stock) volontairement neutre pour pouvoir être
re-skinné en pharmacie sans changer l'architecture.

## 2. Ce qu'on construit (le périmètre exact)

Une plateforme **AI-BI locale** avec **3 backends + 1 frontend + 1 base de données** :

```
                    ┌──────────────────────────────┐
                    │   FRONTEND — React + TS      │
                    │  2 périmètres seulement :    │
                    │  • Assistant (chat)          │
                    │  • Tableau de bord (BI)      │
                    └──────┬───────────────┬───────┘
                           │ REST          │ REST
              ┌────────────▼─────┐  ┌──────▼────────────┐
              │ assistant-service│  │    bi-service     │
              │ (traitement)     │  │ (KPI, agrégats)   │
              │ FastAPI :8002    │  │ FastAPI :8001     │
              │ RAG + Ollama     │  │                   │
              └───────┬──────────┘  └──────┬────────────┘
                      │ REST (SQL validé)  │ REST (SQL des KPI)
              ┌───────▼────────────────────▼───────┐
              │        data-access-service         │
              │  FastAPI :8003 — SEUL service qui  │
              │  parle à la base. Lecture seule,   │
              │  validation sqlglot, timeout,      │
              │  plafond de lignes.                │
              └───────────────┬────────────────────┘
                              │ psycopg (SELECT only)
                  ┌───────────▼────────────┐        ┌──────────────┐
                  │ PostgreSQL 16+pgvector │        │ Ollama :11434│
                  │ :5442 — schéma métier  │        │ (hôte local) │
                  │ + embeddings du schéma │        └──────▲───────┘
                  └────────────────────────┘               │
                        assistant-service ─────────────────┘
                        (génération SQL + narration)
```

- **`bi-service` (port 8001)** — expose des indicateurs prêts à afficher :
  vue d'ensemble (CA, commandes, clients actifs), CA mensuel, top produits, top clients,
  état du stock. Il ne calcule rien côté Python : chaque endpoint envoie son SQL au
  `data-access-service` et met en forme le résultat.
- **`assistant-service` (port 8002 — le service de « traitement »)** — le pipeline IA :
  1. reçoit une question en français/anglais ;
  2. **RAG** : retrouve les tables pertinentes par recherche vectorielle (descriptions du
     schéma embarquées dans **pgvector**) ;
  3. construit le prompt et demande le SQL à **Ollama** (modèle local) ;
  4. contrôles de sécurité locaux (SELECT uniquement, tables connues) ;
  5. fait exécuter le SQL par `data-access-service` ;
  6. fait **narrer** le résultat par Ollama et renvoie `{answer, sql, rows}`.
- **`data-access-service` (port 8003)** — l'unique porte vers PostgreSQL :
  `POST /v1/query` avec validation **sqlglot** (SELECT uniquement, une seule instruction),
  rôle SQL en **lecture seule**, timeout 15 s, plafond 5 000 lignes. Toute violation → 400
  explicite. C'est la reproduction du principe « defense in depth » du projet parent.
- **Frontend (React 18 + TypeScript + Vite, port 5173)** — deux pages :
  `/assistant` (conversation : réponse narrée + SQL repliable + tableau de résultats) et
  `/tableau-de-bord` (cartes KPI + graphiques Recharts). Libellés en **français**.
  Pas de module prévision (c'est du backlog phase 2).
- **PostgreSQL 16 + pgvector (Docker, port hôte 5442)** — 2 schémas :
  `business` (tables métier + données synthétiques ~24 mois) et `meta`
  (table `schema_embeddings` pour le RAG). Deux rôles : `app_admin` (DDL/seed) et
  `app_readonly` (SELECT sur `business` uniquement — utilisé par data-access).

## 3. Choix techniques imposés (et pourquoi)

| Choix | Décision | Raison |
|---|---|---|
| Langage des 3 backends | **Python 3.11 + FastAPI** | Un seul écosystème à maîtriser ; outillage LLM/RAG le plus simple ; PC stagiaires modestes. |
| Vector store | **pgvector dans PostgreSQL** | Un conteneur de moins qu'un Qdrant séparé (RAM limitée) ; même principe RAG que le parent. |
| LLM | **Ollama local**, modèles ≤ 3B : `qwen2.5-coder:1.5b` ou `:3b` (SQL), `llama3.2:3b` (narration) | Les PC des stagiaires ont ~4 Go de VRAM ; ces modèles y tiennent. Modèles configurables par variables d'env. |
| Frontend | **React 18 + TypeScript + Vite + Recharts** | Les stagiaires connaissent React ; même stack que le parent. |
| Communication | **REST/JSON uniquement**, pas de message broker | Simplicité ; c'est le modèle du parent. |
| Données | **100 % synthétiques** générées par script Python (seed) | Confidentialité LPN ; volume maîtrisé (~15 000 lignes). |
| Orchestration | **docker-compose** (db + 3 services) ; frontend en `npm run dev` ; Ollama sur l'hôte | Reproductible et léger. |
| Sécurité minimale | lecture seule partout côté requêtes ; validation SQL centralisée | Le point le plus important à préserver du parent. |

## 4. Le schéma métier (base `business`, données synthétiques)

11 tables, ~24 mois d'historique (2024-07 → 2026-06), saisonnalité visible (pic de rentrée) :

- `categories(category_id, name)` — ~8 catégories.
- `suppliers(supplier_id, name, city)` — ~15 fournisseurs.
- `products(product_id, name, category_id, supplier_id, unit_price, cost_price)` — ~400 produits.
- `clients(client_id, name, city, client_type)` — ~120 clients (types : librairie, école, particulier).
- `sales_reps(sales_rep_id, full_name, email)` — ~8 commerciaux.
- `orders(order_id, client_id, sales_rep_id, order_date, status)` — ~4 000 commandes.
- `order_lines(order_line_id, order_id, product_id, quantity, unit_price, line_total)` — ~12 000 lignes.
- `invoices(invoice_id, order_id, invoice_date, total_amount, is_paid)` — ~3 500 factures.
- `invoice_lines(invoice_line_id, invoice_id, product_id, quantity, line_total)`.
- `payments(payment_id, invoice_id, payment_date, amount)`.
- `stock(product_id, quantity_on_hand, quantity_reserved, last_updated)` — 1 ligne/produit.

Chaque table a une description FR/EN dans `db/schema_metadata.csv` → c'est ce fichier que
l'assistant embarque dans pgvector pour le RAG (comme le `schema_metadata.csv` du parent).

**Re-skin pharmacie (backlog)** : renommer produits→médicaments, catégories→classes
thérapeutiques, clients→patients/pharmacies… l'architecture ne change pas.

## 5. Ce que les stagiaires feront ensuite (backlog indicatif, phase 2)

Le squelette livré doit marcher de bout en bout. Ensuite, à eux (selon M. Mounir) :
authentification + rôles ; nouveaux KPI et filtres de période ; export PDF/Excel ;
amélioration du RAG (meilleur modèle d'embedding) ; historique de conversation persistant ;
re-skin pharmacie ; et plus tard un module de **prévision** (le parent utilise Prophet).

## 6. Définition de « terminé » pour le squelette

1. `docker compose up` démarre db + 3 services ; `npm run dev` démarre le frontend.
2. `/tableau-de-bord` affiche des KPI et graphiques réels issus du seed.
3. `/assistant` répond correctement à au moins 10 questions français type
   (« Quel est le chiffre d'affaires de mars 2026 ? », « Top 5 des produits les plus vendus ? »)
   avec le SQL visible — et **refuse** toute demande destructive (UPDATE/DELETE).
4. Tous les tests unitaires passent ; chaque service a un `/health`.
5. Aucun secret en dur ; aucun fichier de données LPN ; README d'installation complet
   (y compris téléchargement des modèles Ollama).

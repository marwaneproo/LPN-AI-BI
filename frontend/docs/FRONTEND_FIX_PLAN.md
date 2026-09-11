# Frontend Fix Plan

## Objectif

Ce document liste exactement ce que je corrigerais dans le frontend avant de le considérer comme une vraie base produit pour LPN AI-BI.

Le but n'est pas de refaire le design pour le plaisir. Le but est de transformer le frontend actuel, qui ressemble surtout a une maquette compilee/mockee, en frontend maintenable, versionne, coherent avec le backend reel, et pret a accompagner le pipeline BI/RAG/SQL du projet.

## Diagnostic actuel

Le frontend a une bonne direction produit:

- interface sobre, dense et professionnelle;
- design system coherent avec un outil BI interne;
- experience conversationnelle proche Claude/Notion;
- panneaux `Sources`, `SQL`, `Donnees`;
- dashboards BI visuellement pertinents;
- theming dark/light et densite comfortable/compact;
- composants attendus: conversation, dashboard, historique, reglages, admin.

Mais il y a un probleme majeur:

- le code source React/Vite n'est pas present dans le repo;
- le dossier `frontend/src` n'existe pas;
- `frontend/package.json` n'existe pas;
- `frontend/vite.config.ts` n'existe pas;
- seul le build compile `frontend/dist` est present;
- `frontend/README.md` est encore un placeholder;
- les ecrans semblent utiliser des donnees mockees, pas le backend reel.

Donc mon verdict est:

> Le design est une bonne direction, mais l'implementation actuelle n'est pas encore notre vrai frontend produit.

## Principe de correction

Je ne chercherais pas a patcher directement `frontend/dist`, car c'est un output compile. Ce serait fragile et non maintenable.

La vraie correction doit etre:

1. restaurer ou reconstruire le code source frontend;
2. garder les bonnes idees du design system;
3. connecter progressivement le frontend aux endpoints backend reels;
4. supprimer les mocks au bon moment, pas avant que les APIs existent;
5. aligner l'UX avec l'etat reel du projet.

## Ce que je vais ajouter

### 1. Recréer une vraie base frontend source

Ajouter ou restaurer:

- `frontend/package.json`
- `frontend/index.html`
- `frontend/vite.config.ts`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.node.json`
- `frontend/src/main.tsx`
- `frontend/src/App.tsx`
- `frontend/src/styles/theme.css`
- `frontend/src/vite-env.d.ts`

But:

- pouvoir lancer `npm install`, `npm run dev`, `npm run build`;
- pouvoir modifier le frontend proprement;
- ne plus dependre uniquement de `frontend/dist`.

### 2. Recréer la structure frontend propre

Ajouter une structure claire:

```text
frontend/src/
  components/
    ui/
    shell/
    conversation/
    dashboard/
    admin/
  pages/
  providers/
  lib/
    api/
    mocks/
    format/
    config/
  styles/
```

But:

- garder le code lisible;
- separer UI, pages, mocks et API;
- faciliter l'integration backend.

### 3. Ajouter un vrai client API

Ajouter:

- `frontend/src/lib/api/client.ts`
- `frontend/src/lib/api/sqlGeneration.ts`
- plus tard: `validator.ts`, `qa.ts`, `dashboard.ts`, selon les endpoints disponibles.

Premier endpoint reel a connecter:

```http
POST http://localhost:8081/v1/generate-sql
```

Payload:

```json
{
  "question": "How many orders were placed last month?"
}
```

Reponse actuelle:

```json
{
  "sql": "...",
  "retrieved_tables": [...],
  "latency_ms": 17973
}
```

But:

- la conversation doit utiliser notre vrai Schema RAG;
- le panneau `Sources` doit afficher `retrieved_tables`;
- le panneau `SQL` doit afficher le SQL genere par Arctic;
- la latence doit venir du backend.

### 4. Ajouter un mode mock / real contrôlé par `.env`

Ajouter:

```env
VITE_API_BASE_URL=http://localhost:8081
VITE_USE_MOCKS=false
```

Comportement voulu:

- `VITE_USE_MOCKS=true`: frontend demo avec donnees fake;
- `VITE_USE_MOCKS=false`: frontend connecte au backend local.

But:

- garder les demos faciles;
- ne pas bloquer le developpement quand un service backend est down;
- eviter de melanger mock et real sans controle.

### 5. Aligner la conversation avec l'etat reel du backend

Aujourd'hui le frontend simule:

```text
retrieve -> generate -> execute -> narrate
```

Mais notre backend actuel fait seulement:

```text
question -> schema retrieval -> SQL generation
```

Je corrigerais l'UX comme suit:

Pour maintenant:

```text
Récupération du schéma
Génération SQL
SQL prêt à valider
```

Et pas encore:

```text
Exécution
Formulation finale
```

Ces deux etapes doivent apparaitre plus tard, apres:

- Task 4.1: SQL validator sidecar;
- Task 4.2/4.x: SQL executor;
- endpoint final `/v1/qa`.

But:

- ne pas mentir a l'utilisateur;
- montrer exactement ou on en est dans le pipeline;
- eviter une UI qui promet deja une reponse BI finale alors que le backend genere seulement du SQL.

### 6. Adapter les panneaux de réponse

Pour l'etat actuel du projet, la reponse conversationnelle devrait afficher:

- question utilisateur;
- etape de progression;
- SQL genere;
- tables recuperees par Schema RAG;
- latence;
- avertissement discret: "SQL non execute pour le moment".

Les chips devraient etre:

- `Sources`
- `SQL`
- `Validation` en disabled ou "Bientot"
- `Donnees` en disabled ou absent pour l'instant

Quand l'execution sera implementee, on reactivera:

- `Donnees`
- `Reponse`
- `Graphique`, si pertinent.

### 7. Corriger les noms de tables et schemas

Le build actuel contient parfois:

```sql
business.c_order
```

Mais notre Schema RAG actuel retourne:

```text
C_ORDER
C_INVOICE
C_BPARTNER
C_ORDERLINE
M_PRODUCT
```

Je corrigerais l'affichage pour utiliser les valeurs reelles retournees par le backend:

- `table_name`
- `module`
- `description_en`
- `description_fr`
- `key_columns`
- `relations`
- `score`

But:

- ne pas inventer un schema SQL qui n'est pas encore celui du backend;
- eviter la confusion entre schema Postgres futur (`business.*`) et metadata Compiere brute (`C_ORDER`).

### 8. Nettoyer les données fake trop spécifiques

Le build actuel invente des chiffres:

- chiffre d'affaires;
- impayes;
- stock;
- produits;
- factures;
- risques de rupture;
- Prophet;
- LightGBM.

Je garderais ces donnees uniquement dans `mocks/`, jamais comme comportement par defaut en mode real.

En mode real, tant que les endpoints dashboard n'existent pas:

- dashboard affiche un empty state propre;
- previsions affiche "non disponible";
- admin affiche "aucun audit reel disponible";
- conversation reste fonctionnelle sur `generate-sql`.

### 9. Refaire le README frontend

Remplacer le placeholder par un vrai README:

- comment installer;
- comment lancer;
- variables `.env`;
- mode mock vs real;
- routes disponibles;
- limites actuelles;
- commandes de test/build.

Commandes attendues:

```powershell
cd D:\LPN_PROJECT\frontend
npm install
npm run dev
npm run build
npm run typecheck
```

### 10. Ajouter des tests frontend minimum

Ajouter au minimum:

- test de rendu de la page conversation;
- test du client API `generateSql`;
- test du mode mock/real;
- test d'affichage des sources;
- test d'etat erreur backend down;
- test d'etat timeout.

Possibles outils:

- Vitest;
- React Testing Library;
- Playwright plus tard pour smoke UI.

## Ce que je vais modifier

### 1. Conversation

Modifier la conversation pour qu'elle devienne le centre reel du produit.

Etat cible court terme:

- utilisateur pose une question;
- appel reel a `/v1/generate-sql`;
- affichage du SQL;
- affichage des tables retrouvees;
- affichage de la latence;
- pas d'execution SQL tant que le validator/executor n'existent pas.

Etat cible moyen terme:

- question;
- schema retrieval;
- SQL generation;
- SQL validation;
- SQL execution read-only;
- answer narration;
- table preview;
- possible chart.

### 2. Dashboard

Modifier le dashboard pour qu'il n'ait pas l'air "fini" alors qu'il est mocke.

Court terme:

- garder le layout;
- afficher un empty state si pas d'API dashboard;
- afficher clairement "donnees mockees" seulement si `VITE_USE_MOCKS=true`.

Plus tard:

- connecter aux endpoints d'agregats reels;
- utiliser les donnees importees depuis Postgres;
- ne pas inventer Prophet/LightGBM tant que ces services n'existent pas.

### 3. Admin

Modifier l'admin pour correspondre a ce que le backend peut tracer.

Court terme:

- audit mock seulement en mode mock;
- en mode real, afficher "audit non disponible";
- preparer le shape futur: question, retrieved tables, generated SQL, validation status, execution status, latency.

### 4. Login

Modifier le login pour etre clair:

- garder stub temporaire;
- ne pas pretendre que l'auth est finale;
- preparer Keycloak/OIDC plus tard;
- ne pas hardcoder l'utilisateur partout dans la logique metier.

### 5. Design tokens

Verifier que le CSS respecte le design system:

- limiter `rounded-3xl`;
- eviter gradients decoratifs;
- garder l'accent bleu tres rare;
- garder les cards sobres;
- verifier le contraste dark/light;
- verifier les etats disabled/focus/hover.

## Ce que je vais supprimer

Je ne supprimerais rien sans validation humaine, mais voici ce que je proposerais de retirer ou de remplacer:

### 1. Ne pas versionner `frontend/dist`

Idealement, `frontend/dist` ne devrait pas etre la source de verite.

Je proposerais:

- garder `dist` ignore par Git;
- versionner `src`;
- generer `dist` seulement avec `npm run build`.

Mais je ne supprimerais pas `dist` tant que le vrai source n'est pas restaure.

### 2. Supprimer les mocks du chemin real

Pas supprimer les fichiers mock completement.

Mais supprimer leur utilisation par defaut quand:

```env
VITE_USE_MOCKS=false
```

### 3. Supprimer les promesses UI non supportees

Tant que le backend ne le supporte pas, je retirerais ou desactiverais:

- execution SQL affichee comme si elle existait;
- narration finale comme si elle venait du backend;
- dashboards reels;
- previsions Prophet/LightGBM;
- audit reel.

Ces elements peuvent rester en mock demo, mais clairement marques.

## Ce que je ne changerais pas

Je garderais:

- la direction visuelle generale;
- le dark theme par defaut;
- la navigation: Conversation, Tableau de bord, Previsions, Historique, Reglages, Admin;
- le style Linear/Vercel/Claude;
- les chips `Sources`, `SQL`, `Donnees`;
- la densite d'information;
- l'approche typographique;
- le design system comme base officielle.

## Ordre d'implementation recommande

### Phase 1 - Recuperation ou reconstruction source

1. Verifier si Claude a le dossier `frontend/src` ailleurs.
2. Si oui, le restaurer dans le repo.
3. Si non, reconstruire le frontend source a partir du `dist` et du design system.
4. Ajouter `package.json`, Vite, TypeScript, scripts.
5. Verifier `npm run build`.

### Phase 2 - API réelle pour SQL generation

1. Ajouter `VITE_API_BASE_URL`.
2. Ajouter client `generateSql`.
3. Connecter la page conversation a `/v1/generate-sql`.
4. Afficher `sql`, `retrieved_tables`, `latency_ms`.
5. Gerer erreurs: backend down, timeout, 500, 422.

### Phase 3 - Ajuster l'UX au backend actuel

1. Remplacer faux flow `execute/narrate` par flow actuel.
2. Desactiver `Donnees` tant que SQL executor absent.
3. Afficher "SQL non execute".
4. Garder mocks uniquement en mode mock.

### Phase 4 - Qualite frontend

1. Typecheck.
2. Build.
3. Tests unitaires minimum.
4. Smoke test navigateur.
5. Verifier responsive desktop/tablet.
6. Verifier dark/light.

### Phase 5 - Future integration

Apres Task 4.1 et SQL executor:

1. Ajouter validation SQL dans l'UI.
2. Ajouter execution SQL read-only.
3. Ajouter preview table.
4. Ajouter narration finale.
5. Ajouter audit log reel.

## Definition of Done

Je considererais le frontend "bon pour continuer" quand:

- `frontend/src` existe et est versionne;
- `npm install` marche;
- `npm run dev` marche;
- `npm run build` marche;
- la conversation appelle vraiment `/v1/generate-sql`;
- les sources viennent vraiment du Schema RAG;
- le SQL affiche vient vraiment d'Arctic/Ollama;
- les mocks sont controles par `.env`;
- le README explique comment tester;
- le design reste sobre et coherent avec `DESIGN_SYSTEM.md`;
- l'UI ne pretend pas executer ou narrer tant que le backend ne le fait pas.

## Conclusion

Je ne repartirais pas de zero visuellement.

Je garderais la direction de Claude:

- sobre;
- dense;
- moderne;
- BI interne;
- conversation centree sur SQL/sources/donnees.

Mais je referais ou restaurerais proprement l'implementation source, puis je connecterais d'abord la conversation au backend reel.

La priorite absolue est:

> transformer cette maquette compilee en frontend source maintenable et connecte au vrai pipeline Schema RAG -> SQL generation.

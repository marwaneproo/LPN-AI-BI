# LPN AI-BI Evaluation Harness

This package runs reproducible question sets through the live `llm-orchestrator` `/v1/qa` endpoint and writes CSV reports for accuracy review.

The current recommended baseline is:

- `questions_vente_fr_en.csv`: 128 bilingual French and English sales-process questions for the current LPN vente scope.
- `questions_en.csv`: older 30-question smoke set kept for backward compatibility.

## Prerequisites

Start the stack first:

```powershell
cd D:\LPN_PROJECT
.\Launch_LPN_AI_BI.bat
```

For the current Excel-derived snapshot, ensure the temporary data is imported:

```powershell
cd D:\LPN_PROJECT
powershell -ExecutionPolicy Bypass -File scripts\import-youssef-xlsx-snapshot.ps1
```

## Run

From the Python workspace:

```powershell
cd D:\LPN_PROJECT\services-python
uv run eval run `
  --questions eval/questions_vente_fr_en.csv `
  --language fr `
  --output ../reports/vente-eval-test.csv
```

Optional arguments:

```powershell
uv run eval run `
  --questions eval/questions_vente_fr_en.csv `
  --base-url http://localhost:8081 `
  --language fr `
  --output ../reports/vente-eval-20260604.csv
```

Use `--language en` to run the English wording of the same benchmark.

The direct module help also works:

```powershell
cd D:\LPN_PROJECT\services-python\eval
uv run python -m eval.cli --help
```

## Vente Baseline Coverage

`questions_vente_fr_en.csv` covers:

- commandes by period status document type client commercial and product category.
- CA commande and CA facture trends.
- paid and unpaid invoices.
- top clients and top products.
- commercial performance.
- type de commande and type d'article.
- supplier-oriented questions that expose whether supplier metadata is available.
- delivery and order-to-delivery questions.
- stock snapshot and rupture-risk questions.
- vague questions that should trigger clarification.
- destructive or sensitive prompts that must be refused without SQL.

## Report Columns

The generated report includes:

- `language` and `question_used`: the exact wording sent to the orchestrator.
- `question_fr` and `question_en`: the bilingual benchmark wording.
- `expected_metric`, `expected_kind`, and `expected_row_shape`: the intended business behavior.
- `retrieval_pass`: whether every expected table appeared in retrieved tables.
- `execution_pass`: whether `/v1/qa` returned `SUCCESS`.
- `nonempty_pass`: whether at least one row was returned.
- `kind_pass`: a stricter check based on the expected question kind.
- `latency_ms`: backend latency reported by `/v1/qa`.
- `sql`, `answer`, and `retrieved_tables` for human review.
- `manual_score`: intentionally blank. Youssef fills it manually on a 1-5 scale:
  - `1`: wrong
  - `2`: mostly wrong
  - `3`: partially useful
  - `4`: correct with minor issue
  - `5`: perfect

## Interpreting the Baseline

This benchmark is not expected to score perfectly at first. It is designed to show current strengths and gaps before changing prompts models retrieval or validation.

Important interpretation notes:

- Supplier questions may fail until supplier metadata and joins are added to the semantic layer.
- Commercial name questions may be limited while the runtime schema mainly exposes `SALESREP_ID`.
- Clarification and safe-refusal rows should avoid generating SQL.
- Always keep the generated CSV report with the model version prompt version and date in the filename.

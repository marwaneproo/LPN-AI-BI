from __future__ import annotations

import re
from typing import Any

from qdrant_client import QdrantClient

from schema_retrieval.config import Settings
from schema_retrieval.embedding import HashEmbeddingModel
from schema_retrieval.qdrant import get_qdrant_client


RELATION_TARGET_RE = re.compile(r"->\s*([A-Z][A-Z0-9_]+)")
RELATION_TABLE_RE = re.compile(r"\b([A-Z][A-Z0-9_]+)\b")
WORD_RE = re.compile(r"[A-Za-z0-9_]+")
LEXICAL_WEIGHT = 0.08
BUSINESS_TERM_WEIGHT = 0.35


def retrieve_schema(
    question: str,
    top_k: int,
    settings: Settings,
    embedding_model: HashEmbeddingModel,
) -> list[dict[str, Any]]:
    query_vector = embedding_model.encode([question])[0]
    client = get_qdrant_client(settings)
    candidate_limit = max(top_k * 4, top_k + 10)
    response = client.query_points(
        collection_name=settings.collection_name,
        query=query_vector,
        limit=candidate_limit,
        with_payload=True,
    )

    retrieved = [
        _result_from_payload(point.payload or {}, score=float(point.score))
        for point in response.points
    ]
    retrieved = _rerank_by_lexical_overlap(question, retrieved)[:top_k]
    return _expand_relations(
        retrieved=retrieved,
        payloads_by_table=_payloads_by_table(client, settings.collection_name),
        limit=top_k * 2,
    )


def _expand_relations(
    retrieved: list[dict[str, Any]],
    payloads_by_table: dict[str, dict[str, Any]],
    limit: int,
) -> list[dict[str, Any]]:
    results_by_table: dict[str, dict[str, Any]] = {}
    ordered_results: list[dict[str, Any]] = []

    for item in retrieved:
        source_table = str(item["table_name"])
        if source_table not in results_by_table:
            results_by_table[source_table] = item
            ordered_results.append(item)
        if len(ordered_results) >= limit:
            return ordered_results

        for related_table in _prioritize_related_tables(
            source_table,
            _related_tables(str(item.get("relations", ""))),
        ):
            if len(ordered_results) >= limit:
                return ordered_results
            if related_table in results_by_table or related_table not in payloads_by_table:
                continue

            expanded = _result_from_payload(
                payloads_by_table[related_table],
                score=0.0,
                expanded_from=source_table,
            )
            results_by_table[related_table] = expanded
            ordered_results.append(expanded)

    return ordered_results


def _payloads_by_table(client: QdrantClient, collection_name: str) -> dict[str, dict[str, Any]]:
    payloads: dict[str, dict[str, Any]] = {}
    next_page_offset: str | None = None

    while True:
        points, next_page_offset = client.scroll(
            collection_name=collection_name,
            limit=100,
            offset=next_page_offset,
            with_payload=True,
            with_vectors=False,
        )
        for point in points:
            payload = point.payload or {}
            table_name = str(payload.get("table_name", ""))
            if table_name:
                payloads[table_name] = payload
        if next_page_offset is None:
            return payloads


def _result_from_payload(
    payload: dict[str, Any],
    score: float,
    expanded_from: str | None = None,
) -> dict[str, Any]:
    result = {
        "table_name": str(payload.get("table_name", "")),
        "module": str(payload.get("module", "")),
        "description_en": str(payload.get("description_en", "")),
        "description_fr": str(payload.get("description_fr", "")),
        "key_columns": str(payload.get("key_columns", "")),
        "relations": str(payload.get("relations", "")),
        "notes": str(payload.get("notes", "")),
        "score": score,
    }
    if expanded_from is not None:
        result["expanded_from"] = expanded_from
    return result


def _related_tables(relations: str) -> list[str]:
    seen: set[str] = set()
    related: list[str] = []
    for table_name in [
        *RELATION_TARGET_RE.findall(relations),
        *RELATION_TABLE_RE.findall(relations),
    ]:
        if table_name in seen:
            continue
        seen.add(table_name)
        related.append(table_name)
    return related


def _rerank_by_lexical_overlap(
    question: str,
    results: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    query_terms = _terms(question)
    if not query_terms:
        return results

    order_by_table = {str(result["table_name"]): index for index, result in enumerate(results)}
    ranked: list[dict[str, Any]] = []
    for index, result in enumerate(results):
        document = " ".join(
            [
                str(result.get("table_name", "")),
                str(result.get("module", "")),
                str(result.get("description_en", "")),
                str(result.get("description_fr", "")),
                str(result.get("key_columns", "")),
                str(result.get("relations", "")),
                str(result.get("notes", "")),
            ]
        )
        table_terms = _terms(document)
        lexical_score = len(query_terms & table_terms) * LEXICAL_WEIGHT
        business_score = _business_term_boost(query_terms, str(result["table_name"]))
        boosted = {
            **result,
            "score": float(result.get("score", 0.0)) + lexical_score + business_score,
        }
        ranked.append(boosted)

    return sorted(
        ranked,
        key=lambda result: (
            float(result.get("score", 0.0)),
            -order_by_table[str(result["table_name"])],
        ),
        reverse=True,
    )


def _terms(text: str) -> set[str]:
    raw_terms = {term.lower() for term in WORD_RE.findall(text)}
    normalized = set(raw_terms)
    for term in raw_terms:
        if term.endswith("ies") and len(term) > 4:
            normalized.add(f"{term[:-3]}y")
        if term.endswith("s") and len(term) > 3:
            normalized.add(term[:-1])
    return normalized


def _business_term_boost(query_terms: set[str], table_name: str) -> float:
    boost = 0.0
    if query_terms & {"order", "orders", "commande", "commandes"}:
        if table_name == "C_ORDER":
            boost += BUSINESS_TERM_WEIGHT
        if table_name == "C_ORDERLINE" and query_terms & {"line", "lines", "ligne", "lignes", "product", "produit", "produits"}:
            boost += BUSINESS_TERM_WEIGHT

    if query_terms & {"invoice", "invoices", "facture", "factures", "facturees", "facturee"}:
        if table_name == "C_INVOICE":
            boost += BUSINESS_TERM_WEIGHT
        if table_name == "C_INVOICELINE" and query_terms & {"ca", "revenue", "amount", "montant", "product", "produit", "supplier", "fournisseur"}:
            boost += BUSINESS_TERM_WEIGHT

    if query_terms & {"supplier", "suppliers", "fournisseur", "fournisseurs", "vendor", "vendors"}:
        if table_name == "V_PRODUCT_PRIMARY_SUPPLIER":
            boost += BUSINESS_TERM_WEIGHT
        if table_name in {"M_PRODUCT_PO", "C_BPARTNER_VENDOR"}:
            boost += BUSINESS_TERM_WEIGHT / 2

    if query_terms & {"forecast", "forecasting", "prevision", "previsions", "mensuel", "monthly"}:
        if table_name.startswith("FACT_SALES_MONTHLY"):
            boost += BUSINESS_TERM_WEIGHT

    return boost


def _prioritize_related_tables(source_table: str, related_tables: list[str]) -> list[str]:
    line_table = f"{source_table}LINE"
    return sorted(
        related_tables,
        key=lambda table_name: 0 if table_name == line_table else 1,
    )

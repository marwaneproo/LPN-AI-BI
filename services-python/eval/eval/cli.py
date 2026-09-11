from __future__ import annotations

import math
from datetime import datetime
from pathlib import Path
from typing import Any

import click
import httpx
import pandas as pd


DEFAULT_QUESTIONS_PATH = Path(__file__).resolve().parents[1] / "questions_en.csv"
DEFAULT_ORCHESTRATOR_URL = "http://localhost:8081"
REQUEST_TIMEOUT_SECONDS = 300


@click.group()
def cli() -> None:
    """Run reproducible QA evaluations against the LPN AI-BI orchestrator."""


@cli.command(name="run")
@click.option(
    "--questions",
    "questions_path",
    type=click.Path(exists=True, dir_okay=False, path_type=Path),
    default=DEFAULT_QUESTIONS_PATH,
    show_default=True,
    help="CSV file containing evaluation questions.",
)
@click.option(
    "--orchestrator-url",
    "--base-url",
    default=DEFAULT_ORCHESTRATOR_URL,
    show_default=True,
    help="Base URL for the llm-orchestrator service.",
)
@click.option(
    "--language",
    type=click.Choice(["en", "fr"], case_sensitive=False),
    default="en",
    show_default=True,
    help="Question language to send to the orchestrator.",
)
@click.option(
    "--output",
    "output_path",
    type=click.Path(dir_okay=False, path_type=Path),
    default=None,
    help="CSV report path. Defaults to ../reports/eval-YYYYMMDD-HHMMSS.csv.",
)
def run_eval(
    questions_path: Path,
    orchestrator_url: str,
    language: str,
    output_path: Path | None,
) -> None:
    """Run the question set through /v1/qa and write a CSV report."""

    questions = _load_questions(questions_path)
    if output_path is None:
        output_path = Path("..") / "reports" / f"eval-{datetime.now():%Y%m%d-%H%M%S}.csv"

    output_path.parent.mkdir(parents=True, exist_ok=True)
    base_url = orchestrator_url.rstrip("/")
    rows: list[dict[str, Any]] = []

    with httpx.Client(base_url=base_url, timeout=REQUEST_TIMEOUT_SECONDS) as client:
        for index, question in questions.iterrows():
            question_id = str(question["id"])
            question_text = _question_text(question, language)
            click.echo(f"[{index + 1:02d}/{len(questions):02d}] {question_id}: {question_text}")
            rows.append(_evaluate_question(client, question, language))

    report = pd.DataFrame(rows)
    report.to_csv(output_path, index=False)

    summary = _summary(report)
    click.echo()
    click.echo(f"Report written: {output_path}")
    click.echo(f"Questions: {len(report)}")
    click.echo(f"Retrieval pass: {summary['retrieval_pass_pct']:.1f}%")
    click.echo(f"Execution pass: {summary['execution_pass_pct']:.1f}%")
    click.echo(f"Nonempty pass: {summary['nonempty_pass_pct']:.1f}%")
    click.echo(f"P50 latency: {summary['p50_latency_ms']:.0f} ms")
    click.echo(f"P95 latency: {summary['p95_latency_ms']:.0f} ms")


def _load_questions(path: Path) -> pd.DataFrame:
    questions = pd.read_csv(path).fillna("")
    required_columns = {
        "id",
        "question_en",
        "expected_tables",
        "expected_kind",
        "expected_row_shape",
        "notes",
    }
    missing_columns = required_columns - set(questions.columns)
    if missing_columns:
        raise click.ClickException(
            f"{path} is missing required columns: {', '.join(sorted(missing_columns))}"
        )
    if questions.empty:
        raise click.ClickException(f"{path} does not contain any questions")
    for optional_column in ("question_fr", "expected_metric"):
        if optional_column not in questions.columns:
            questions[optional_column] = ""
    return questions


def _evaluate_question(client: httpx.Client, question: pd.Series, language: str = "en") -> dict[str, Any]:
    expected_tables = _split_tables(str(question["expected_tables"]))
    expected_kind = str(question["expected_kind"])
    started_at = datetime.now()
    error = ""
    payload: dict[str, Any] = {}
    status_code: int | None = None
    question_text = _question_text(question, language)

    try:
        response = client.post(
            "/v1/qa",
            json={
                "question": question_text,
                "language": language,
                "client_request_id": f"eval-{question['id']}-{started_at:%Y%m%d%H%M%S%f}",
            },
        )
        status_code = response.status_code
        response.raise_for_status()
        payload = response.json()
    except Exception as exception:
        error = str(exception)

    retrieved_tables = [
        str(table.get("table_name", ""))
        for table in payload.get("retrieved_tables", [])
        if isinstance(table, dict)
    ]
    retrieved_table_set = set(retrieved_tables)
    retrieved_missing = sorted(expected_tables - retrieved_table_set)
    execution_status = str(payload.get("execution_status", "HTTP_ERROR" if error else "UNKNOWN"))
    row_count = _int_or_zero(payload.get("row_count", 0))
    latency_ms = _int_or_zero(payload.get("latency_ms", 0))

    sql = str(payload.get("sql", "") or "")
    answer = str(payload.get("answer", "") or "")
    execution_pass = _execution_pass(status_code, execution_status, sql, expected_kind)
    retrieval_pass = not retrieved_missing
    nonempty_pass = row_count > 0
    kind_pass = _kind_pass(expected_kind, execution_pass, row_count, sql, answer)

    return {
        "id": question["id"],
        "language": language,
        "question_used": question_text,
        "question_fr": question.get("question_fr", ""),
        "question_en": question["question_en"],
        "expected_tables": question["expected_tables"],
        "expected_metric": question.get("expected_metric", ""),
        "expected_kind": expected_kind,
        "expected_row_shape": question["expected_row_shape"],
        "retrieved_tables": "|".join(retrieved_tables),
        "retrieved_missing": "|".join(retrieved_missing),
        "retrieval_pass": retrieval_pass,
        "execution_status": execution_status,
        "execution_pass": execution_pass,
        "nonempty_pass": nonempty_pass,
        "kind_pass": kind_pass,
        "row_count": row_count,
        "latency_ms": latency_ms,
        "sql": sql,
        "answer": answer,
        "model_used": payload.get("model_used", ""),
        "fallback_used": payload.get("fallback_used", ""),
        "http_status": status_code or "",
        "error": payload.get("error", "") or error,
        "manual_score": "",
        "notes": question["notes"],
    }


def _split_tables(value: str) -> set[str]:
    return {table.strip() for table in value.split("|") if table.strip()}


def _question_text(question: pd.Series, language: str) -> str:
    preferred = str(question.get(f"question_{language}", "") or "").strip()
    if preferred:
        return preferred
    return str(question["question_en"]).strip()


def _execution_pass(
    status_code: int | None,
    execution_status: str,
    sql: str,
    expected_kind: str,
) -> bool:
    if status_code != 200:
        return False
    if expected_kind in {"clarification", "safe_refusal"}:
        return execution_status != "SUCCESS" or not sql.strip()
    return execution_status == "SUCCESS"


def _kind_pass(
    expected_kind: str,
    execution_pass: bool,
    row_count: int,
    sql: str,
    answer: str,
) -> bool:
    if expected_kind in {"aggregate", "lookup", "ranking", "join", "trend", "distribution", "multi_step"}:
        return execution_pass and row_count > 0 and bool(sql.strip())
    if expected_kind == "clarification":
        return bool(answer.strip()) and not sql.strip()
    if expected_kind == "safe_refusal":
        return not sql.strip()
    return execution_pass


def _int_or_zero(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _summary(report: pd.DataFrame) -> dict[str, float]:
    latencies = [float(value) for value in report["latency_ms"].tolist() if float(value) > 0]
    return {
        "retrieval_pass_pct": _percent(report["retrieval_pass"]),
        "execution_pass_pct": _percent(report["execution_pass"]),
        "nonempty_pass_pct": _percent(report["nonempty_pass"]),
        "p50_latency_ms": _percentile(latencies, 50),
        "p95_latency_ms": _percentile(latencies, 95),
    }


def _percent(values: pd.Series) -> float:
    if values.empty:
        return 0.0
    return float(values.astype(bool).mean() * 100)


def _percentile(values: list[float], percentile: int) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (percentile / 100) * (len(ordered) - 1)
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[int(rank)]
    weight = rank - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


if __name__ == "__main__":
    cli()

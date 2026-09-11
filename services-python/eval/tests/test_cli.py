from __future__ import annotations

from pathlib import Path

import httpx
import pandas as pd
from click.testing import CliRunner

from eval import cli as eval_cli


def test_split_tables_ignores_empty_values() -> None:
    assert eval_cli._split_tables("C_ORDER| M_PRODUCT |") == {"C_ORDER", "M_PRODUCT"}


def test_question_text_prefers_requested_language() -> None:
    question = pd.Series(
        {
            "question_fr": "Combien de commandes ?",
            "question_en": "How many orders?",
        }
    )

    assert eval_cli._question_text(question, "fr") == "Combien de commandes ?"
    assert eval_cli._question_text(question, "en") == "How many orders?"


def test_safe_refusal_does_not_pass_when_sql_is_generated() -> None:
    assert eval_cli._execution_pass(200, "SUCCESS", "DROP TABLE C_ORDER", "safe_refusal") is False
    assert eval_cli._kind_pass("safe_refusal", False, 0, "", "I cannot do that.") is True


def test_summary_reports_pass_percentages_and_latency_percentiles() -> None:
    report = pd.DataFrame(
        [
            {"retrieval_pass": True, "execution_pass": True, "nonempty_pass": True, "latency_ms": 100},
            {"retrieval_pass": False, "execution_pass": True, "nonempty_pass": False, "latency_ms": 300},
        ]
    )

    summary = eval_cli._summary(report)

    assert summary["retrieval_pass_pct"] == 50.0
    assert summary["execution_pass_pct"] == 100.0
    assert summary["nonempty_pass_pct"] == 50.0
    assert summary["p50_latency_ms"] == 200.0
    assert summary["p95_latency_ms"] == 290.0


def test_run_writes_csv_report_with_automatic_judgments(
    monkeypatch,
    tmp_path: Path,
) -> None:
    questions_path = tmp_path / "questions.csv"
    output_path = tmp_path / "report.csv"
    questions_path.write_text(
        "\n".join(
            [
                "id,question_en,expected_tables,expected_kind,expected_row_shape,notes",
                "Q001,How many orders?,C_ORDER,aggregate,single count,smoke",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/v1/qa"
        return httpx.Response(
            200,
            json={
                "answer": "There are 200 orders.",
                "sql": "SELECT COUNT(*) FROM C_ORDER",
                "rows": [{"count": 200}],
                "row_count": 1,
                "retrieved_tables": [{"table_name": "C_ORDER"}],
                "latency_ms": 1234,
                "model_used": "test-model",
                "fallback_used": False,
                "execution_status": "SUCCESS",
            },
        )

    original_client = httpx.Client
    monkeypatch.setattr(
        eval_cli.httpx,
        "Client",
        lambda *args, **kwargs: original_client(
            transport=httpx.MockTransport(handler),
            *args,
            **kwargs,
        ),
    )

    result = CliRunner().invoke(
        eval_cli.cli,
        [
            "run",
            "--questions",
            str(questions_path),
            "--base-url",
            "http://testserver",
            "--language",
            "en",
            "--output",
            str(output_path),
        ],
    )

    assert result.exit_code == 0, result.output
    assert "Retrieval pass: 100.0%" in result.output
    assert "Execution pass: 100.0%" in result.output
    assert "Nonempty pass: 100.0%" in result.output
    assert "P50 latency: 1234 ms" in result.output
    assert "P95 latency: 1234 ms" in result.output

    report = pd.read_csv(output_path)
    assert len(report) == 1
    assert bool(report.loc[0, "retrieval_pass"])
    assert bool(report.loc[0, "execution_pass"])
    assert bool(report.loc[0, "nonempty_pass"])
    assert bool(report.loc[0, "kind_pass"])
    assert report.loc[0, "question_used"] == "How many orders?"
    assert pd.isna(report.loc[0, "manual_score"])


def test_vente_question_file_is_current_and_bilingual() -> None:
    questions_path = Path(__file__).resolve().parents[1] / "questions_vente_fr_en.csv"
    questions = pd.read_csv(questions_path).fillna("")
    required_columns = {
        "id",
        "question_fr",
        "question_en",
        "expected_tables",
        "expected_metric",
        "expected_kind",
        "expected_row_shape",
        "notes",
    }

    assert required_columns <= set(questions.columns)
    assert len(questions) >= 120
    assert questions["id"].is_unique
    assert questions["question_fr"].str.strip().astype(bool).mean() >= 0.5
    assert not questions["expected_tables"].str.contains(
        "LPN_ORDER_FLOW|LPN_INVOICE_PAYMENT",
        regex=True,
    ).any()
